package dev.backbee.work

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import dev.backbee.data.db.BackbeeDatabase
import dev.backbee.ui.MainActivity
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a backup file looks like before anything is done with it. */
data class BackupPreview(
    val name: String,
    val shows: Int,
    val playedEpisodes: Int,
    val sizeBytes: Long,
)

/**
 * The other half of the durability story: putting a nightly backup back.
 *
 * [BackupWorker] writes `backbee.db.<date>.bak`; this reads one, checks it is
 * a healthy backbee database, swaps it in under the app, and restarts the
 * process so every open connection, ViewModel and player starts from the
 * restored file rather than a closed one. Losing the phone costs at most a day
 * of position only if the copy can be read back - without this it is a promise
 * that needs adb to keep.
 */
class BackupRestorer(private val context: Context) {

    /**
     * Copies the chosen file to the cache and validates it. Nothing on the
     * device changes. The preview it returns is what the confirmation shows,
     * so a wrong file is caught by its numbers rather than after the swap.
     */
    suspend fun inspect(uri: Uri): Result<BackupPreview> = withContext(Dispatchers.IO) {
        runCatching {
            val staging = stage(uri)
            val name = DocumentFile.fromSingleUri(context, uri)?.name ?: "backup"
            openReadOnly(staging).use { db ->
                check(db.integrityOk()) { "The file is damaged (integrity check failed)." }
                check(db.hasTable("room_master_table") && db.hasTable("shows") && db.hasTable("positions")) {
                    "That is not a backbee backup."
                }
                val version = db.userVersion()
                check(version <= BackbeeDatabase.VERSION) {
                    "That backup was written by a newer backbee (database version $version). Update the app first."
                }
                BackupPreview(
                    name = name,
                    shows = db.count("SELECT COUNT(*) FROM shows"),
                    playedEpisodes = db.count("SELECT COUNT(*) FROM positions WHERE played = 1"),
                    sizeBytes = staging.length(),
                )
            }
        }.onFailure { Log.w(TAG, "Backup rejected", it) }
    }

    /**
     * Replaces the live database with the staged copy and restarts the app.
     * Only returns on failure; on success the process ends.
     *
     * The caller must have stopped playback and closed [BackbeeDatabase] - the
     * file is swapped underneath whatever still holds a handle, and the restart
     * is what makes that safe.
     */
    suspend fun restoreStaged(database: BackbeeDatabase): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val staging = stagingFile()
            check(staging.isFile) { "The backup was not prepared. Choose it again." }
            openReadOnly(staging).use { check(it.integrityOk()) { "The staged copy is damaged." } }

            database.close()

            val live = BackbeeDatabase.fileFor(context)
            live.parentFile?.mkdirs()
            // The write-ahead log and shared-memory files belong to the old
            // database; left behind they would be replayed onto the new one.
            File(live.path + "-wal").delete()
            File(live.path + "-shm").delete()
            File(live.path + "-journal").delete()
            staging.copyTo(live, overwrite = true)
            staging.delete()
            Log.i(TAG, "Restored database from backup (${live.length() / 1024} KB); restarting")

            restart()
        }.onFailure { Log.e(TAG, "Restore failed", it) }
    }

    fun discardStaged() {
        stagingFile().delete()
    }

    private fun stage(uri: Uri): File {
        val staging = stagingFile()
        staging.delete()
        context.contentResolver.openInputStream(uri)?.use { input ->
            staging.outputStream().use { input.copyTo(it) }
        } ?: throw IOException("Could not open the chosen file.")
        check(staging.length() > 0) { "The chosen file is empty." }
        return staging
    }

    private fun stagingFile(): File = File(context.cacheDir, "restore-staging.db")

    private fun openReadOnly(file: File): SQLiteDatabase =
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

    private fun SQLiteDatabase.integrityOk(): Boolean =
        rawQuery("PRAGMA integrity_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }

    private fun SQLiteDatabase.hasTable(name: String): Boolean =
        rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name))
            .use { it.moveToFirst() }

    private fun SQLiteDatabase.userVersion(): Int =
        rawQuery("PRAGMA user_version", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun SQLiteDatabase.count(sql: String): Int =
        rawQuery(sql, null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    /**
     * A cold start on the restored file. The launch intent is handed to the
     * system before the process exits, so the system brings the app straight
     * back up - the same trick every "restart to apply" flow uses.
     */
    private fun restart() {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    private companion object {
        const val TAG = "BackupRestorer"
    }
}
