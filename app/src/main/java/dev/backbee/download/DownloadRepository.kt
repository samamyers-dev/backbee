package dev.backbee.download

import android.util.Log
import dev.backbee.core.download.DownloadConfig
import dev.backbee.core.download.DownloadPlan
import dev.backbee.core.download.DownloadPlanner
import dev.backbee.core.download.EpisodeSlot
import dev.backbee.data.db.BackbeeDatabase
import dev.backbee.data.db.DownloadEntity
import dev.backbee.data.db.DownloadState
import dev.backbee.data.db.LocalFileRow
import dev.backbee.data.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow

/**
 * The download-ahead engine's state, separate from the worker that does the I/O.
 *
 * Planning is a pure function in :core; everything context-dependent - what is
 * on disk, what the user configured, how big things are - is gathered here.
 */
class DownloadRepository(
    private val db: BackbeeDatabase,
    private val files: EpisodeFiles,
    private val settings: SettingsStore,
) {
    private val downloadDao = db.downloadDao()
    private val episodeDao = db.episodeDao()

    fun observeLocalFiles(): Flow<List<LocalFileRow>> = downloadDao.observeLocalFiles()

    fun observeBytesOnDisk(): Flow<Long> = downloadDao.observeBytesOnDisk()

    suspend fun state(episodeId: Long): DownloadEntity? = downloadDao.get(episodeId)

    /** Builds the plan for a show without acting on it. The worker carries it out. */
    suspend fun planFor(showId: Long, currentOrderIndex: Int): DownloadPlan {
        val config = settings.current().let {
            DownloadConfig(
                downloadAhead = it.downloadAhead,
                deletePlayedAfterMillis = it.deletePlayedAfterMillis,
                storageCapBytes = it.storageCapBytes,
            )
        }

        val slots = downloadDao.planningRows(showId).map { row ->
            EpisodeSlot(
                episodeId = row.episodeId,
                orderIndex = row.orderIndex,
                played = row.played == true,
                playedAtMillis = row.playedAt,
                downloaded = row.downloadState == DownloadState.DONE && row.filePath != null,
                bytes = estimateBytes(row.bytesTotal, row.enclosureBytes, row.durationSeconds),
                hasEnclosure = !row.enclosureUrl.isNullOrBlank(),
            )
        }

        return DownloadPlanner(config).plan(slots, currentOrderIndex, System.currentTimeMillis())
    }

    /**
     * Feeds routinely omit `<enclosure length>`. Guessing from duration at a
     * typical spoken-word bitrate keeps the storage cap roughly honest instead
     * of treating unknown-size episodes as free.
     */
    private fun estimateBytes(bytesTotal: Long?, enclosureBytes: Long?, durationSeconds: Long?): Long {
        bytesTotal?.takeIf { it > 0 }?.let { return it }
        enclosureBytes?.takeIf { it > 0 }?.let { return it }
        val seconds = durationSeconds?.takeIf { it > 0 } ?: return FALLBACK_EPISODE_BYTES
        return seconds * ASSUMED_BYTES_PER_SECOND
    }

    suspend fun enqueue(episodeId: Long) {
        val existing = downloadDao.get(episodeId)
        if (existing?.state == DownloadState.DONE) return
        downloadDao.upsert(
            DownloadEntity(
                episodeId = episodeId,
                state = DownloadState.QUEUED,
                filePath = existing?.filePath,
                bytesTotal = existing?.bytesTotal ?: 0,
                bytesDone = existing?.bytesDone ?: 0,
                updatedAt = System.currentTimeMillis(),
                failureCount = existing?.failureCount ?: 0,
            )
        )
    }

    suspend fun markRunning(episodeId: Long) =
        downloadDao.setState(episodeId, DownloadState.RUNNING, System.currentTimeMillis())

    suspend fun markProgress(episodeId: Long, bytesDone: Long, bytesTotal: Long) =
        downloadDao.setProgress(episodeId, bytesDone, bytesTotal, System.currentTimeMillis())

    suspend fun markDone(episodeId: Long, path: String, bytes: Long) {
        downloadDao.upsert(
            DownloadEntity(
                episodeId = episodeId,
                state = DownloadState.DONE,
                filePath = path,
                bytesTotal = bytes,
                bytesDone = bytes,
                updatedAt = System.currentTimeMillis(),
                failureCount = 0,
            )
        )
    }

    suspend fun markFailed(episodeId: Long) {
        val existing = downloadDao.get(episodeId)
        downloadDao.upsert(
            DownloadEntity(
                episodeId = episodeId,
                state = DownloadState.FAILED,
                filePath = null,
                bytesTotal = existing?.bytesTotal ?: 0,
                bytesDone = 0,
                updatedAt = System.currentTimeMillis(),
                failureCount = (existing?.failureCount ?: 0) + 1,
            )
        )
    }

    /** Removes the audio and the row. The episode itself, and its position, stay. */
    suspend fun removeDownload(episodeId: Long) {
        val existing = downloadDao.get(episodeId)
        files.delete(existing?.filePath)
        downloadDao.delete(episodeId)
    }

    suspend fun targetFileFor(episodeId: Long): java.io.File? {
        val row = episodeDao.getRow(episodeId) ?: return null
        val url = row.enclosureUrl ?: return null
        return files.fileFor(row.showId, episodeId, url)
    }

    /**
     * Drops audio the database no longer references. File and row can drift
     * apart if the process dies between writing one and committing the other;
     * this runs with the nightly job to reconcile them.
     */
    suspend fun sweepOrphans() {
        val known = downloadDao.allFilePaths().toSet()
        val removed = files.deleteOrphans(known)
        if (removed > 0) Log.i(TAG, "Removed $removed orphaned audio file(s)")
    }

    companion object {
        private const val TAG = "DownloadRepository"

        /** ~64 kbps, a common spoken-word encode. */
        private const val ASSUMED_BYTES_PER_SECOND = 8_000L
        private const val FALLBACK_EPISODE_BYTES = 40L * 1024 * 1024
    }
}
