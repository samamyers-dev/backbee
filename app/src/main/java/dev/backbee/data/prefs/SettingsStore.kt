package dev.backbee.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.backbee.core.playback.ResumeTier
import dev.backbee.core.playback.SmartResume
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "backbee_settings")

/** Everything on the global Settings screen. Per-show values live on the show row. */
data class Settings(
    val downloadAhead: Int = 10,
    val storageCapBytes: Long = 8L * 1024 * 1024 * 1024,
    val wifiOnlyDownloads: Boolean = true,
    val deletePlayedAfterHours: Int = 24,
    val resumeTiers: List<ResumeTier> = SmartResume.DEFAULT_TIERS,
    /**
     * Off by default and deliberately so: connecting to the car should leave the
     * app ready to play, not start talking on its own.
     */
    val autoPlayOnBluetooth: Boolean = false,
    val skipForwardSeconds: Int = 30,
    val skipBackSeconds: Int = 10,
    /** SAF tree URI of the Syncthing-watched folder the nightly checkpoint writes to. */
    val backupFolderUri: String? = null,
    val backupEnabled: Boolean = true,
) {
    val deletePlayedAfterMillis: Long get() = deletePlayedAfterHours * 3_600_000L
}

class SettingsStore(private val context: Context) {

    private object Keys {
        val downloadAhead = intPreferencesKey("download_ahead")
        val storageCapBytes = longPreferencesKey("storage_cap_bytes")
        val wifiOnly = booleanPreferencesKey("wifi_only_downloads")
        val deletePlayedAfterHours = intPreferencesKey("delete_played_after_hours")
        val resumeTiers = stringPreferencesKey("resume_tiers")
        val autoPlayOnBluetooth = booleanPreferencesKey("auto_play_on_bluetooth")
        val skipForward = intPreferencesKey("skip_forward_seconds")
        val skipBack = intPreferencesKey("skip_back_seconds")
        val backupFolderUri = stringPreferencesKey("backup_folder_uri")
        val backupEnabled = booleanPreferencesKey("backup_enabled")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        val defaults = Settings()
        Settings(
            downloadAhead = prefs[Keys.downloadAhead] ?: defaults.downloadAhead,
            storageCapBytes = prefs[Keys.storageCapBytes] ?: defaults.storageCapBytes,
            wifiOnlyDownloads = prefs[Keys.wifiOnly] ?: defaults.wifiOnlyDownloads,
            deletePlayedAfterHours = prefs[Keys.deletePlayedAfterHours] ?: defaults.deletePlayedAfterHours,
            resumeTiers = SmartResume.decodeTiers(prefs[Keys.resumeTiers]),
            autoPlayOnBluetooth = prefs[Keys.autoPlayOnBluetooth] ?: defaults.autoPlayOnBluetooth,
            skipForwardSeconds = prefs[Keys.skipForward] ?: defaults.skipForwardSeconds,
            skipBackSeconds = prefs[Keys.skipBack] ?: defaults.skipBackSeconds,
            backupFolderUri = prefs[Keys.backupFolderUri],
            backupEnabled = prefs[Keys.backupEnabled] ?: defaults.backupEnabled,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setDownloadAhead(value: Int) = edit { it[Keys.downloadAhead] = value.coerceIn(0, 100) }

    suspend fun setStorageCapBytes(value: Long) =
        edit { it[Keys.storageCapBytes] = value.coerceAtLeast(256L * 1024 * 1024) }

    suspend fun setWifiOnlyDownloads(value: Boolean) = edit { it[Keys.wifiOnly] = value }

    suspend fun setDeletePlayedAfterHours(value: Int) =
        edit { it[Keys.deletePlayedAfterHours] = value.coerceIn(0, 24 * 30) }

    suspend fun setResumeTiers(tiers: List<ResumeTier>) =
        edit { it[Keys.resumeTiers] = SmartResume.encodeTiers(tiers) }

    suspend fun setAutoPlayOnBluetooth(value: Boolean) = edit { it[Keys.autoPlayOnBluetooth] = value }

    suspend fun setSkipForwardSeconds(value: Int) = edit { it[Keys.skipForward] = value.coerceIn(5, 300) }

    suspend fun setSkipBackSeconds(value: Int) = edit { it[Keys.skipBack] = value.coerceIn(5, 300) }

    suspend fun setBackupFolderUri(value: String?) = edit { prefs ->
        if (value == null) prefs.remove(Keys.backupFolderUri) else prefs[Keys.backupFolderUri] = value
    }

    suspend fun setBackupEnabled(value: Boolean) = edit { it[Keys.backupEnabled] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
