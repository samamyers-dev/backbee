package dev.backbee.data.repo

import dev.backbee.data.db.BackbeeDatabase
import dev.backbee.data.db.EpisodeRow
import dev.backbee.data.db.PositionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Positions, stars and notes.
 *
 * Position is the one piece of state the app is not allowed to lose, so writes
 * here are narrow, idempotent, and safe to call far more often than necessary.
 */
class PlaybackRepository(private val db: BackbeeDatabase) {

    private val episodeDao = db.episodeDao()
    private val positionDao = db.positionDao()
    private val markDao = db.markDao()
    private val showDao = db.showDao()

    fun observeArchive(showId: Long): Flow<List<EpisodeRow>> = episodeDao.observeArchive(showId)

    fun observeRow(episodeId: Long): Flow<EpisodeRow?> = episodeDao.observeRow(episodeId)

    fun observeUpNext(showId: Long, afterOrderIndex: Int, limit: Int = 3): Flow<List<EpisodeRow>> =
        episodeDao.observeUpNext(showId, afterOrderIndex, limit)

    fun observeStarred(showId: Long): Flow<List<EpisodeRow>> = episodeDao.observeStarred(showId)

    fun search(showId: Long, query: String): Flow<List<EpisodeRow>> =
        episodeDao.search(showId, query, SEARCH_RESULT_LIMIT)

    suspend fun getRow(episodeId: Long): EpisodeRow? = episodeDao.getRow(episodeId)

    /** Where Resume points: the earliest episode not yet finished. */
    suspend fun resumeTarget(showId: Long): EpisodeRow? = episodeDao.nextUnplayed(showId)

    fun observeResumeTarget(showId: Long): Flow<EpisodeRow?> = episodeDao.observeNextUnplayed(showId)

    suspend fun yearMarks(showId: Long) = episodeDao.yearMarks(showId)

    fun observeDownloadedAhead(showId: Long, fromOrderIndex: Int): Flow<Int> =
        episodeDao.observeDownloadedAhead(showId, fromOrderIndex)

    suspend fun episodeCount(showId: Long): Int = episodeDao.count(showId)

    suspend fun queueWindow(showId: Long, fromOrderIndex: Int, limit: Int): List<EpisodeRow> =
        episodeDao.fromOrderIndex(showId, fromOrderIndex, limit)

    suspend fun starredEpisodes(showId: Long, limit: Int = 100): List<EpisodeRow> =
        episodeDao.starred(showId, limit)

    suspend fun savePosition(episodeId: Long, positionSeconds: Long, at: Long = System.currentTimeMillis()) {
        positionDao.savePosition(episodeId, positionSeconds.coerceAtLeast(0), at)
    }

    suspend fun position(episodeId: Long) = positionDao.get(episodeId)

    /**
     * Marks an episode finished and reports whether that completed the archive.
     * The caller decides what to do about it; this only records the fact.
     */
    suspend fun markPlayed(
        episodeId: Long,
        positionSeconds: Long,
        at: Long = System.currentTimeMillis(),
    ): Boolean {
        positionDao.markPlayed(episodeId, positionSeconds.coerceAtLeast(0), at)
        val row = episodeDao.getRow(episodeId) ?: return false
        val played = positionDao.playedCount(row.showId)
        val total = episodeDao.count(row.showId)
        val finished = total > 0 && played >= total
        if (finished) showDao.setCompletedAt(row.showId, at)
        return finished
    }

    // -- Bulk marking -------------------------------------------------------

    /** What a range looks like before anything is done to it. */
    data class RangeSummary(val total: Int, val played: Int) {
        val unplayed: Int get() = total - played
        val isEmpty: Boolean get() = total == 0
    }

    /**
     * Everything needed to put a bulk mark back exactly as it was.
     *
     * [episodeIds] are only the rows that actually changed, and [previous] is
     * their prior state - absent from the list where no position row existed at
     * all. Undoing therefore cannot disturb an episode that was already in the
     * target state before the bulk mark ran.
     */
    data class BulkPlayedChange(
        val showId: Long,
        val episodeIds: List<Long>,
        val previous: List<PositionEntity>,
        val played: Boolean,
    ) {
        val count: Int get() = episodeIds.size
    }

    suspend fun summarizeRange(showId: Long, fromOrderIndex: Int, toOrderIndex: Int): RangeSummary {
        if (fromOrderIndex > toOrderIndex) return RangeSummary(0, 0)
        return RangeSummary(
            total = episodeDao.countInRange(showId, fromOrderIndex, toOrderIndex),
            played = positionDao.playedCountInRange(showId, fromOrderIndex, toOrderIndex),
        )
    }

    /** Highest order index in the show, for "everything after this one". */
    suspend fun lastOrderIndex(showId: Long): Int = episodeDao.maxOrderIndex(showId)

    /**
     * Marks a whole stretch of the archive played or unplayed and returns the
     * receipt needed to undo it. Null when the range is empty or already in the
     * requested state - there is nothing to undo and nothing to report.
     */
    suspend fun markRange(
        showId: Long,
        fromOrderIndex: Int,
        toOrderIndex: Int,
        played: Boolean,
        at: Long = System.currentTimeMillis(),
    ): BulkPlayedChange? {
        if (fromOrderIndex > toOrderIndex) return null
        val ids = positionDao.idsNeedingPlayedChange(showId, fromOrderIndex, toOrderIndex, played)
        if (ids.isEmpty()) return null

        val previous = ids.chunked(SQL_VARIABLE_LIMIT).flatMap { positionDao.positionsFor(it) }
        ids.chunked(SQL_VARIABLE_LIMIT).forEach { positionDao.applyPlayedIn(it, played, at) }
        refreshCompletion(showId, at)

        return BulkPlayedChange(showId, ids, previous, played)
    }

    suspend fun undoBulk(change: BulkPlayedChange, at: Long = System.currentTimeMillis()) {
        val byId = change.previous.associateBy { it.episodeId }
        change.episodeIds.chunked(SQL_VARIABLE_LIMIT).forEach { chunk ->
            positionDao.restorePositions(chunk, chunk.mapNotNull(byId::get))
        }
        refreshCompletion(change.showId, at)
    }

    /**
     * Keeps the show's completion flag honest after a bulk change. Only writes
     * when the answer actually flips, so undoing a change that did not complete
     * the archive cannot invent or destroy a completion date.
     */
    private suspend fun refreshCompletion(showId: Long, at: Long) {
        val show = showDao.getById(showId) ?: return
        val total = episodeDao.count(showId)
        val complete = total > 0 && positionDao.playedCount(showId) >= total
        when {
            complete && show.completedAt == null -> showDao.setCompletedAt(showId, at)
            !complete && show.completedAt != null -> showDao.setCompletedAt(showId, null)
        }
    }

    suspend fun markUnplayed(episodeId: Long, at: Long = System.currentTimeMillis()) {
        positionDao.markUnplayed(episodeId, at)
        val row = episodeDao.getRow(episodeId) ?: return
        // Un-finishing any episode re-opens the archive.
        showDao.setCompletedAt(row.showId, null)
    }

    suspend fun setStarred(episodeId: Long, starred: Boolean, at: Long = System.currentTimeMillis()) =
        markDao.setStarred(episodeId, starred, at)

    suspend fun setNote(episodeId: Long, note: String?, at: Long = System.currentTimeMillis()) =
        markDao.setNote(episodeId, note?.takeIf { it.isNotBlank() }, at)

    suspend fun setKeepAfterPlaying(episodeId: Long, keep: Boolean, at: Long = System.currentTimeMillis()) =
        markDao.setKeepAfterPlaying(episodeId, keep, at)

    suspend fun description(episodeId: Long): String? = episodeDao.description(episodeId)

    suspend fun findByEpisodeNumber(showId: Long, number: Int): Long? =
        episodeDao.findByEpisodeNumber(showId, number) ?: episodeDao.findByOrderIndex(showId, number - 1)

    suspend fun firstOrderIndexOnOrAfter(showId: Long, millis: Long): Int? =
        episodeDao.firstOrderIndexOnOrAfter(showId, millis)

    /** Fills in a duration the feed omitted, once the player has measured it. */
    suspend fun recordMeasuredDuration(episodeId: Long, durationSeconds: Long) {
        if (durationSeconds > 0) episodeDao.fillMissingDuration(episodeId, durationSeconds)
    }

    private companion object {
        /** Enough to scroll through; a search that matches more needs better words. */
        const val SEARCH_RESULT_LIMIT = 300

        /**
         * SQLite's bound-parameter ceiling is 999 on the versions shipped with
         * the older API levels this app still supports, and a bulk mark over a
         * 1,500-episode archive would sail straight past it. Room does not
         * chunk `IN (:ids)` for you.
         */
        const val SQL_VARIABLE_LIMIT = 400
    }
}
