package dev.backbee.work

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.backbee.BackbeeApp
import dev.backbee.data.db.BackbeeDatabase
import dev.backbee.di.AppContainer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The entire durability story: a nightly consistent snapshot of the database
 * dropped into a folder the user chose - ideally one that something (Syncthing,
 * a cloud drive's folder sync, an SD card) carries off the phone.
 *
 * Phone loss costs at most one day of position. That is the whole design - no
 * accounts, no sync protocol, no server to run. Android's own cloud backup
 * covers the database too (see backup_rules.xml); this is the copy the user
 * can see and keep.
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val container = (applicationContext as BackbeeApp).container
        try {
            backup(container)
        } finally {
            // Reconciling the download table with the disk is cheap, and this is
            // the one job that reliably runs while nobody is listening. It runs
            // whether or not a backup folder was ever chosen.
            runCatching { container.downloadRepository.reconcileWithDisk() }
                .onFailure { Log.w(TAG, "Download reconciliation failed", it) }
        }
    }

    private suspend fun backup(container: AppContainer): Result {
        val settings = container.settingsStore.current()

        if (!settings.backupEnabled) return Result.success()
        val folderUri = settings.backupFolderUri?.let(Uri::parse) ?: run {
            Log.i(TAG, "No backup folder configured; skipping")
            return Result.success()
        }

        val staging = File(applicationContext.cacheDir, "backup-staging.db")
        staging.delete()

        return try {
            snapshotDatabase(container.database, staging)

            val folder = DocumentFile.fromTreeUri(applicationContext, folderUri)
            if (folder == null || !folder.canWrite()) {
                Log.w(TAG, "Backup folder is not writable; the tree permission may have been revoked")
                // Say so on the settings readout: a folder that quietly stopped
                // accepting writes is exactly the failure the readout exists for.
                container.diagnostics.recordCheckpoint(
                    System.currentTimeMillis(),
                    "FAILED: FOLDER NOT WRITABLE - CHOOSE IT AGAIN",
                )
                return Result.failure()
            }

            val name = "${BackbeeDatabase.NAME}.${stamp()}.bak"
            // Replace rather than accumulate a same-named file.
            folder.findFile(name)?.delete()
            val target = folder.createFile("application/octet-stream", name)
                ?: return Result.failure()

            applicationContext.contentResolver.openOutputStream(target.uri)?.use { output ->
                staging.inputStream().use { it.copyTo(output) }
            } ?: return Result.failure()

            pruneOldBackups(folder)
            Log.i(TAG, "Wrote backup $name (${staging.length() / 1024} KB)")
            container.diagnostics.recordCheckpoint(System.currentTimeMillis(), "OK")
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Backup failed", e)
            container.diagnostics.recordCheckpoint(System.currentTimeMillis(), "FAILED: ${e.message}")
            Result.retry()
        } finally {
            staging.delete()
        }
    }

    /**
     * A consistent copy of the database while playback carries on.
     *
     * `VACUUM INTO` is the clean way: it writes a fresh, compact snapshot without
     * stopping position writes for its duration. It needs SQLite 3.27, which
     * Android 11 is the first release guaranteed to carry. Older devices
     * checkpoint the write-ahead log and copy the main file while holding the
     * write lock instead: nothing can write during the copy, so the file is
     * consistent, and at worst it lacks the few seconds of playback since the
     * checkpoint.
     */
    private fun snapshotDatabase(database: BackbeeDatabase, target: File) {
        val db = database.openHelper.writableDatabase
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // SQLite will not bind a parameter as the target, so the path goes
            // in as a literal - it is ours, inside cacheDir, and the quote
            // doubling keeps it well-formed whatever the device names its
            // directories.
            val literal = target.absolutePath.replace("'", "''")
            db.execSQL("VACUUM INTO '$literal'")
            return
        }

        db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        database.runInTransaction {
            BackbeeDatabase.fileFor(applicationContext).copyTo(target, overwrite = true)
        }
    }

    /** Keeps a rolling fortnight; a syncing folder keeps its own versions beyond that. */
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
