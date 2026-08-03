package dev.backbee.work

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.backbee.BackbeeApp
import dev.backbee.data.db.BackbeeDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The entire durability story: a nightly consistent snapshot of the database
 * dropped into a Syncthing-watched folder, which carries it to the home server.
 *
 * Phone loss costs at most one day of position. That is the whole design - no
 * accounts, no sync protocol, no server to run.
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val container = (applicationContext as BackbeeApp).container
        val settings = container.settingsStore.current()

        if (!settings.backupEnabled) return@withContext Result.success()
        val folderUri = settings.backupFolderUri?.let(Uri::parse) ?: run {
            Log.i(TAG, "No backup folder configured; skipping")
            return@withContext Result.success()
        }

        val staging = File(applicationContext.cacheDir, "backup-staging.db")
        staging.delete()

        try {
            // VACUUM INTO writes a consistent snapshot without stopping playback
            // or blocking position writes for its duration. SQLite will not bind
            // a parameter as the target, so the path goes in as a literal - it is
            // ours, inside cacheDir, and the quote doubling keeps it well-formed
            // whatever the device names its directories.
            val literal = staging.absolutePath.replace("'", "''")
            container.database.openHelper.writableDatabase.execSQL("VACUUM INTO '$literal'")

            val folder = DocumentFile.fromTreeUri(applicationContext, folderUri)
            if (folder == null || !folder.canWrite()) {
                Log.w(TAG, "Backup folder is not writable; the tree permission may have been revoked")
                return@withContext Result.failure()
            }

            val name = "${BackbeeDatabase.NAME}.${stamp()}.bak"
            // Replace rather than accumulate a same-named file.
            folder.findFile(name)?.delete()
            val target = folder.createFile("application/octet-stream", name)
                ?: return@withContext Result.failure()

            applicationContext.contentResolver.openOutputStream(target.uri)?.use { output ->
                staging.inputStream().use { it.copyTo(output) }
            } ?: return@withContext Result.failure()

            pruneOldBackups(folder)
            Log.i(TAG, "Wrote checkpoint $name (${staging.length() / 1024} KB)")
            container.diagnostics.recordCheckpoint(System.currentTimeMillis(), "OK")
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Backup failed", e)
            container.diagnostics.recordCheckpoint(System.currentTimeMillis(), "FAILED: ${e.message}")
            Result.retry()
        } finally {
            staging.delete()
            // Reconciling stray audio is cheap and this is the one job that
            // reliably runs while nobody is listening.
            runCatching { container.downloadRepository.sweepOrphans() }
        }
    }

    /** Keeps a rolling fortnight. Syncthing keeps its own versions beyond that. */
    private fun pruneOldBackups(folder: DocumentFile) {
        val backups = folder.listFiles()
            .filter { it.name?.startsWith(BackbeeDatabase.NAME) == true && it.name?.endsWith(".bak") == true }
            .sortedByDescending { it.name }
        backups.drop(KEEP_BACKUPS).forEach { it.delete() }
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis()))

    companion object {
        private const val TAG = "Backup"
        private const val KEEP_BACKUPS = 14
        const val UNIQUE_NAME = "nightly-backup"
    }
}
