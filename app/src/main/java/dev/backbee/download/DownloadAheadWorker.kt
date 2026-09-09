package dev.backbee.download

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.backbee.BackbeeApp
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response

/**
 * The silent half of "zero decisions while listening": keeps the next N episodes
 * on disk, clears what has been heard, and never asks anything of the user.
 *
 * Runs under WorkManager so the OS decides when it is polite to use the network,
 * and so an interrupted run resumes rather than restarts.
 */
class DownloadAheadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val container = (applicationContext as BackbeeApp).container
        val show = container.showRepository.activeShow() ?: return@withContext Result.success()

        // Before planning around the table, make sure the table is telling the
        // truth about the disk. A DONE row whose file is gone would otherwise
        // never be fetched again.
        runCatching { container.downloadRepository.reconcileWithDisk() }
            .onFailure { Log.w(TAG, "Reconciliation failed", it) }

        val resumeTarget = container.playbackRepository.resumeTarget(show.id)
        val currentOrderIndex = resumeTarget?.orderIndex ?: 0

        val loadedEpisodeId = container.playbackStateStore.currentLoadedEpisodeId()

        val plan = container.downloadRepository.planFor(show.id, currentOrderIndex, loadedEpisodeId)
        Log.i(
            TAG,
            "show=${show.id} at=$currentOrderIndex loaded=$loadedEpisodeId fetch=${plan.toDownload.size} " +
                "drop=${plan.toDelete.size} projected=${plan.projectedBytes / (1024 * 1024)}MB" +
                if (plan.capLimited) " (storage cap reached)" else ""
        )

        // Reclaim first: freeing space before fetching is what lets the cap be a
        // real ceiling rather than a threshold we bounce off.
        for (episodeId in plan.toDelete) {
            container.downloadRepository.removeDownload(episodeId)
        }

        var failures = 0
        for (episodeId in plan.toDownload) {
            ensureActive()
            val ok = runCatching { download(container, episodeId) }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "Download failed for episode $episodeId", error)
                }
                .getOrDefault(false)
            if (!ok) failures++
        }

        // A failure here is nearly always a flaky network. Retrying lets
        // WorkManager back off rather than us spinning.
        if (failures > 0) Result.retry() else Result.success()
    }

    private suspend fun download(container: dev.backbee.di.AppContainer, episodeId: Long): Boolean {
        val row = container.playbackRepository.getRow(episodeId) ?: return false
        val url = row.enclosureUrl ?: return false

        val target = container.episodeFiles.fileFor(row.showId, episodeId, url)
        val partial = container.episodeFiles.partialFor(target)

        // enqueue first: markRunning is an UPDATE, so without a row to update the
        // archive list would show no download state at all until the file landed.
        container.downloadRepository.enqueue(episodeId)
        container.downloadRepository.markRunning(episodeId)

        val alreadyHave = if (partial.exists()) partial.length() else 0L
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", dev.backbee.data.net.Http.USER_AGENT)
            .apply {
                // Resume rather than re-fetch. On a 600-episode backlog over
                // patchy mobile data this is the difference between finishing
                // and never finishing.
                if (alreadyHave > 0) header("Range", "bytes=$alreadyHave-")
            }
            .build()

        var expectedTotal = 0L
        container.mediaHttpClient.newCall(request).execute().use { response ->
            if (response.code == 416) {
                // The server says our partial already reaches past the end:
                // either it is complete and the process died before the rename,
                // or the file changed under us. Both are settled by starting
                // over, and neither is settled by sending the same Range again.
                partial.delete()
                container.downloadRepository.markFailed(episodeId)
                return false
            }
            if (!response.isSuccessful) {
                container.downloadRepository.markFailed(episodeId)
                return false
            }
            val contentType = response.header("Content-Type").orEmpty()
            if (contentType.startsWith("text/html", ignoreCase = true)) {
                // A 200 with an HTML body is a login page or a "file moved"
                // notice, not audio. Saved as an .mp3 it is something the player
                // cannot decode, and the failure would surface an hour later.
                partial.delete()
                container.downloadRepository.markFailed(episodeId)
                return false
            }

            val resuming = response.code == 206 && alreadyHave > 0
            if (!resuming && alreadyHave > 0) {
                // Server ignored the Range header; start over rather than
                // producing a file with a duplicated prefix.
                partial.delete()
            }

            val startingAt = if (resuming) alreadyHave else 0L
            val total = contentLength(response, startingAt)
            expectedTotal = total

            writeBody(response, partial, append = resuming) { written ->
                container.downloadRepository.markProgress(episodeId, startingAt + written, total)
            }
        }

        // Only a file of the announced size becomes a download. Short means the
        // body was cut off: keep the partial so the next attempt resumes it.
        // Long means the partial and the response disagree about the file: no
        // amount of resuming fixes that, so start over.
        if (expectedTotal > 0 && partial.length() != expectedTotal) {
            Log.w(TAG, "Episode $episodeId: got ${partial.length()} of $expectedTotal bytes")
            if (partial.length() > expectedTotal) partial.delete()
            container.downloadRepository.markFailed(episodeId)
            return false
        }

        if (!partial.renameTo(target)) {
            // Cross-device rename can fail; fall back to a copy so a download is
            // never lost just because of where the file happened to land.
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }

        container.downloadRepository.markDone(episodeId, target.absolutePath, target.length())
        return true
    }

    private fun contentLength(response: Response, startingAt: Long): Long {
        val body = response.body?.contentLength() ?: -1L
        return if (body > 0) startingAt + body else 0L
    }

    private suspend fun writeBody(
        response: Response,
        target: File,
        append: Boolean,
        onProgress: suspend (Long) -> Unit,
    ) {
        val source = response.body?.byteStream() ?: throw IOException("Empty body")
        target.parentFile?.mkdirs()

        source.use { input ->
            java.io.FileOutputStream(target, append).use { output ->
                val buffer = ByteArray(64 * 1024)
                var written = 0L
                var sinceReport = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    written += read
                    sinceReport += read
                    // Reporting every chunk would be one DB write per 64 KB.
                    if (sinceReport >= PROGRESS_REPORT_BYTES) {
                        onProgress(written)
                        sinceReport = 0
                    }
                }
                output.fd.sync()
                onProgress(written)
            }
        }
    }

    companion object {
        private const val TAG = "DownloadAhead"
        private const val PROGRESS_REPORT_BYTES = 2L * 1024 * 1024
        const val UNIQUE_NAME = "download-ahead"
    }
}
