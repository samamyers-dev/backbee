package dev.backbee.data.repo

import android.util.Log
import androidx.room.withTransaction
import dev.backbee.core.feed.ArchiveFetcher
import dev.backbee.core.feed.ArchiveProbe
import dev.backbee.core.feed.PagedArchive
import dev.backbee.core.feed.ParsedEpisode
import dev.backbee.data.db.BackbeeDatabase
import dev.backbee.data.db.EpisodeEntity
import dev.backbee.data.db.ShowEntity
import dev.backbee.data.net.PodcastDirectory
import kotlinx.coroutines.flow.Flow

data class IngestResult(
    val showId: Long,
    val added: Int,
    val updated: Int,
    /** True when order_index was rebuilt from publication date rather than appended to. */
    val reindexed: Boolean,
    val probe: ArchiveProbe.Report,
    /** Set when Podcast Index supplied episodes the publisher's own feed did not. */
    val recoveredFromDirectory: Boolean = false,
)

/**
 * Owns the archive: getting it in, keeping it in order, and tracking which show
 * is the one currently being read.
 */
class ShowRepository(
    private val db: BackbeeDatabase,
    private val archiveFetcher: ArchiveFetcher,
    private val directory: PodcastDirectory,
) {
    private val showDao = db.showDao()
    private val episodeDao = db.episodeDao()
    private val positionDao = db.positionDao()

    fun observeShows(): Flow<List<ShowEntity>> = showDao.observeAll()

    fun observeActiveShow(): Flow<ShowEntity?> = showDao.observeActive()

    suspend fun activeShow(): ShowEntity? = showDao.getActive()

    fun observeProgress(showId: Long) = showDao.observeProgress(showId)

    suspend fun listeningTotals(showId: Long) = showDao.listeningTotals(showId)

    suspend fun makeActive(showId: Long) = showDao.makeActive(showId)

    suspend fun getShow(showId: Long) = showDao.getById(showId)

    suspend fun updateShow(show: ShowEntity) = showDao.update(show)

    suspend fun deleteShow(showId: Long) = showDao.delete(showId)

    suspend fun setCompletedAt(showId: Long, at: Long?) = showDao.setCompletedAt(showId, at)

    suspend fun searchDirectory(term: String) = directory.search(term)

    /**
     * Adds a feed and pulls its archive. The first show added becomes the active
     * one; after that, promotion from the Shelf is always explicit.
     */
    suspend fun addShow(feedUrl: String, makeActive: Boolean? = null): IngestResult {
        showDao.getByRssUrl(feedUrl)?.let { return refresh(it.id) }

        val archive = archiveFetcher.fetchArchive(feedUrl)
        val now = System.currentTimeMillis()
        val showId = showDao.insert(
            ShowEntity(
                rssUrl = feedUrl,
                title = archive.head.title?.takeIf { it.isNotBlank() } ?: feedUrl,
                artworkUrl = archive.head.artworkUrl,
                addedAt = now,
            )
        )

        val shouldActivate = makeActive ?: showDao.getAll().none { it.isActive }
        if (shouldActivate) showDao.makeActive(showId)

        return ingest(showId, feedUrl, archive, now)
    }

    /** The quiet daily check. Cheap when nothing changed. */
    suspend fun refresh(showId: Long): IngestResult {
        val show = showDao.getById(showId) ?: error("No show with id $showId")
        val archive = archiveFetcher.fetchArchive(show.rssUrl)
        val now = System.currentTimeMillis()

        // Titles and artwork drift over a decade-long run. Keep those current;
        // never touch the user's own per-show speed or skip settings.
        val title = archive.head.title?.takeIf { it.isNotBlank() } ?: show.title
        val artwork = archive.head.artworkUrl ?: show.artworkUrl
        if (title != show.title || artwork != show.artworkUrl) {
            showDao.update(show.copy(title = title, artworkUrl = artwork))
        }

        return ingest(showId, show.rssUrl, archive, now)
    }

    /** Phase 0 on demand, without writing anything. Used by the add-show flow. */
    suspend fun probeArchive(feedUrl: String): ArchiveProbe.Report {
        val archive = archiveFetcher.fetchArchive(feedUrl)
        val expected = runCatching { directory.episodeCountForFeed(feedUrl) }.getOrNull()
        return ArchiveProbe.evaluate(feedUrl, archive, expected)
    }

    private suspend fun ingest(
        showId: Long,
        feedUrl: String,
        archive: PagedArchive,
        now: Long,
    ): IngestResult {
        val expectedTotal = runCatching { directory.episodeCountForFeed(feedUrl) }.getOrNull()
        var probe = ArchiveProbe.evaluate(feedUrl, archive, expectedTotal)
        var episodes = archive.episodes
        var recovered = false

        // Phase 0's escape hatch, wired in rather than left as advice: when the
        // publisher's feed is only a window onto the archive and Podcast Index
        // holds more of it, take theirs.
        if (!probe.isUsable && directory.podcastIndexAvailable) {
            val fromDirectory = runCatching { directory.episodesForFeed(feedUrl) }
                .onFailure { Log.w(TAG, "Podcast Index lookup failed for $feedUrl", it) }
                .getOrDefault(emptyList())

            if (fromDirectory.size > episodes.size) {
                val merged = mergeByGuid(episodes, fromDirectory)
                probe = probe.copy(
                    episodesAfterPaging = merged.size,
                    findings = probe.findings +
                        "Recovered ${merged.size} episodes via Podcast Index; the feed itself offered ${episodes.size}.",
                )
                episodes = merged
                recovered = true
            }
        }

        val counts = writeEpisodes(showId, episodes, now)
        return IngestResult(
            showId = showId,
            added = counts.added,
            updated = counts.updated,
            reindexed = counts.reindexed,
            probe = probe,
            recoveredFromDirectory = recovered,
        )
    }

    /**
     * Directory data is broader but the publisher's own feed is more accurate,
     * so feed entries win on conflict and directory entries fill the gaps.
     */
    private fun mergeByGuid(fromFeed: List<ParsedEpisode>, fromDirectory: List<ParsedEpisode>): List<ParsedEpisode> {
        val byGuid = LinkedHashMap<String, ParsedEpisode>()
        for (episode in fromDirectory) byGuid[episode.guid] = episode
        for (episode in fromFeed) byGuid[episode.guid] = episode
        return ArchiveFetcher.orderOldestFirst(byGuid.values.toList())
    }

    private data class WriteCounts(val added: Int, val updated: Int, val reindexed: Boolean)

    private suspend fun writeEpisodes(
        showId: Long,
        episodes: List<ParsedEpisode>,
        now: Long,
    ): WriteCounts = db.withTransaction {
        val existing = episodeDao.identityMap(showId).associateBy { it.guid }

        // Renumbering an archive somebody is 300 episodes into would move their
        // bookmark, so it is only safe while nothing has been played. Until then
        // - which covers recovering a truncated first import - the order is
        // rebuilt from publication date. After that, new episodes append to the
        // end, which is what "a still-running show" should do anyway.
        val hasProgress = positionDao.playedCount(showId) > 0
        val reindex = !hasProgress

        var nextOrderIndex = if (reindex) 0 else episodeDao.maxOrderIndex(showId) + 1
        val toInsert = mutableListOf<EpisodeEntity>()
        var updated = 0

        for (parsed in episodes) {
            val known = existing[parsed.guid]
            if (known == null) {
                toInsert += EpisodeEntity(
                    showId = showId,
                    guid = parsed.guid,
                    orderIndex = nextOrderIndex++,
                    title = parsed.title,
                    pubDate = parsed.pubDateMillis,
                    durationSeconds = parsed.durationSeconds,
                    enclosureUrl = parsed.enclosureUrl,
                    description = parsed.description,
                    episodeNumber = parsed.episodeNumber,
                    enclosureBytes = parsed.enclosureBytes,
                )
            } else {
                episodeDao.updateMetadata(
                    id = known.id,
                    title = parsed.title,
                    pubDate = parsed.pubDateMillis,
                    durationSeconds = parsed.durationSeconds,
                    enclosureUrl = parsed.enclosureUrl,
                    description = parsed.description,
                    episodeNumber = parsed.episodeNumber,
                    enclosureBytes = parsed.enclosureBytes,
                )
                if (reindex) episodeDao.updateOrderIndex(known.id, nextOrderIndex++)
                updated++
            }
        }

        episodeDao.insertAll(toInsert)
        showDao.setLastRefreshedAt(showId, now)

        WriteCounts(added = toInsert.size, updated = updated, reindexed = reindex && existing.isNotEmpty())
    }

    companion object {
        private const val TAG = "ShowRepository"
    }
}
