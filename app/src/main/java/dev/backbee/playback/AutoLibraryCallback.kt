package dev.backbee.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.backbee.data.repo.PlaybackRepository
import dev.backbee.data.repo.ShowRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future

/**
 * What Android Auto shows.
 *
 * Three entries, because a car screen at arm's length is not a place to browse
 * 1,247 episodes: Resume (where you left off), Next up (the following few), and
 * Starred. Everything else lives on the phone.
 *
 * Note for a sideloaded build: Auto only lists media apps installed from Play by
 * default. Enable Developer settings -> "Unknown sources" in the Android Auto
 * app or none of this will appear.
 */
class AutoLibraryCallback(
    private val scope: CoroutineScope,
    private val shows: ShowRepository,
    private val playback: PlaybackRepository,
    private val coordinator: () -> PlaybackCoordinator?,
) : MediaLibrarySession.Callback {

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        // Android 13+'s "recently played" row on the lock screen and in Quick
        // Settings asks for a recent root and expects a playable item directly
        // under it, not folders. Give it its own root so it finds one.
        if (params?.isRecent == true) {
            val recentParams = LibraryParams.Builder().setRecent(true).build()
            return Futures.immediateFuture(
                LibraryResult.ofItem(MediaItems.browsableItem(RECENT, "backbee"), recentParams)
            )
        }
        return Futures.immediateFuture(
            LibraryResult.ofItem(MediaItems.browsableItem(ROOT, "backbee"), params)
        )
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
        val show = shows.activeShow()
            ?: return@future LibraryResult.ofItemList(ImmutableList.of<MediaItem>(), params)

        val items: List<MediaItem> = when (parentId) {
            ROOT -> listOf(
                MediaItems.browsableItem(RESUME, "Resume", show.title),
                MediaItems.browsableItem(UP_NEXT, "Next up", show.title),
                MediaItems.browsableItem(STARRED, "Starred", show.title),
            )

            RESUME, RECENT -> listOfNotNull(
                playback.resumeTarget(show.id)?.let { MediaItems.forEpisode(it, show) }
            )

            UP_NEXT -> {
                // The same list auto-advance will actually play: the resume
                // target, then the unplayed episodes after it.
                val target = playback.resumeTarget(show.id)
                val after = playback.unplayedAfter(show.id, target?.orderIndex ?: -1, AUTO_LIST_LIMIT - 1)
                (listOfNotNull(target) + after).map { MediaItems.forEpisode(it, show) }
            }

            STARRED -> playback.starredEpisodes(show.id, AUTO_LIST_LIMIT)
                .map { MediaItems.forEpisode(it, show) }

            else -> emptyList()
        }

        LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
        val episodeId = mediaId.toLongOrNull()
            ?: return@future LibraryResult.ofError<MediaItem>(SessionResult.RESULT_ERROR_BAD_VALUE)
        val row = playback.getRow(episodeId)
            ?: return@future LibraryResult.ofError<MediaItem>(SessionResult.RESULT_ERROR_BAD_VALUE)
        val show = shows.getShow(row.showId)
        LibraryResult.ofItem(MediaItems.forEpisode(row, show), null)
    }

    // -- Search ---------------------------------------------------------------
    //
    // "Play the episode about X on backbee" from the steering wheel. Searches
    // the active show's titles and descriptions, the same query the Archive
    // screen's search box runs.

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> = scope.future {
        val count = searchResults(query).size
        session.notifySearchResultChanged(browser, query, count, params)
        LibraryResult.ofVoid()
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
        val results = searchResults(query)
        val pageItems = if (pageSize <= 0) {
            results
        } else {
            val from = (page * pageSize).coerceIn(0, results.size)
            val to = (from + pageSize).coerceAtMost(results.size)
            results.subList(from, to)
        }
        LibraryResult.ofItemList(ImmutableList.copyOf(pageItems), params)
    }

    private suspend fun searchResults(query: String): List<MediaItem> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()
        val show = shows.activeShow() ?: return emptyList()
        return playback.search(show.id, term).first()
            .take(AUTO_LIST_LIMIT)
            .map { MediaItems.forEpisode(it, show) }
    }

    // -- Playing --------------------------------------------------------------

    /**
     * Auto hands back a [MediaItem] carrying only the media id it was given, so
     * the URI has to be filled in here before the player can do anything with it.
     */
    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> = scope.future {
        val resolved = mediaItems.mapNotNull { item ->
            if (item.localConfiguration != null) return@mapNotNull item
            val episodeId = item.mediaId.toLongOrNull() ?: return@mapNotNull null
            val row = playback.getRow(episodeId) ?: return@mapNotNull null
            MediaItems.forEpisode(row, shows.getShow(row.showId))
        }
        resolved.toMutableList()
    }

    /**
     * Every request to start playing lands here: Auto, the widget, and the app's
     * own Play button, which all send a bare media id.
     *
     * The plan is *returned* rather than pushed onto the player, because the
     * session applies whatever this future resolves to - setting the queue here
     * as well would have the session immediately overwrite it. Routing through
     * the coordinator is what keeps smart resume, per-show speed and the rolling
     * window identical in the car and on the phone.
     */
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
        val coordinator = coordinator()
        val requestedId = mediaItems.getOrNull(startIndex.coerceAtLeast(0))?.mediaId
            ?: mediaItems.firstOrNull()?.mediaId

        val plan = when {
            coordinator == null -> null
            // RESUME as a media id means "wherever you left off", which is
            // what a browsable "Resume" node resolves to when played directly.
            requestedId == null || requestedId == RESUME -> coordinator.planResume()
            else -> {
                // A specific episode plays that episode or nothing. Quietly
                // playing the resume target instead, because the tapped one
                // has no audio, would be the wrong episode without a word.
                val episodeId = requestedId.toLongOrNull()
                if (episodeId != null) coordinator.planEpisode(episodeId) else coordinator.planResume()
            }
        }

        plan.toItemsWithStartPosition()
    }

    /**
     * The headset or Bluetooth play button while the process is dead, and the
     * system's "recently played" resume card. The service has been started with
     * an empty player and asks what it should be playing: where you left off.
     */
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
        coordinator()?.planResume().toItemsWithStartPosition()
    }

    /**
     * No plan means nothing playable: the archive is finished, or the requested
     * episode has no audio. Handing the caller's bare ids back to the session
     * would put items without a URI into the player, which crashes it; an empty
     * list leaves it idle, which is what "nothing to play" should look like.
     */
    private fun QueuePlan?.toItemsWithStartPosition(): MediaSession.MediaItemsWithStartPosition =
        if (this == null) {
            MediaSession.MediaItemsWithStartPosition(emptyList(), C.INDEX_UNSET, C.TIME_UNSET)
        } else {
            MediaSession.MediaItemsWithStartPosition(items, startIndex, startPositionMs)
        }

    companion object {
        const val ROOT = "root"
        const val RECENT = "recent"
        const val RESUME = "resume"
        const val UP_NEXT = "up_next"
        const val STARRED = "starred"

        private const val AUTO_LIST_LIMIT = 25
    }
}
