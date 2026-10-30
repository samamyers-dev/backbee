package dev.backbee.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.backbee.core.playback.ListeningDirection
import dev.backbee.core.playback.ResumeTier
import dev.backbee.data.db.ShowEntity
import dev.backbee.data.prefs.DiagnosticsSnapshot
import dev.backbee.data.prefs.Settings
import dev.backbee.di.AppContainer
import dev.backbee.playback.PlayerConnection
import dev.backbee.ui.theme.BackbeePalette
import dev.backbee.work.BackupPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: Settings = Settings(),
    val activeShow: ShowEntity? = null,
    val bytesOnDisk: Long = 0,
    val diagnostics: DiagnosticsSnapshot = DiagnosticsSnapshot(),
)

/** The restore flow: a file has been read and checked, and is waiting on a decision. */
data class RestoreUiState(
    val inspecting: Boolean = false,
    val preview: BackupPreview? = null,
    val restoring: Boolean = false,
    val error: String? = null,
)

class SettingsViewModel(
    private val container: AppContainer,
    private val player: PlayerConnection,
) : ViewModel() {

    private val _restore = MutableStateFlow(RestoreUiState())
    val restore: StateFlow<RestoreUiState> = _restore.asStateFlow()

    val state: StateFlow<SettingsUiState> = combine(
        container.settingsStore.settings,
        container.showRepository.observeActiveShow(),
        container.downloadRepository.observeBytesOnDisk(),
        container.diagnostics.snapshot,
    ) { settings, show, bytes, diagnostics ->
        SettingsUiState(settings, show, bytes, diagnostics)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    // -- Per-show -----------------------------------------------------------

    fun setShowSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 4f)
        // Applies to audio already in flight, exactly as the speed key on Now
        // does; a setting that only takes effect on the next episode is a bug.
        val show = state.value.activeShow ?: return
        if (player.state.value.showId == show.id) player.setSpeed(clamped)
        viewModelScope.launch { container.showRepository.setSpeed(show.id, clamped) }
    }

    fun setSkipIntro(seconds: Int) {
        val show = state.value.activeShow ?: return
        val clamped = seconds.coerceIn(0, 600)
        viewModelScope.launch { container.showRepository.setSkipIntroSeconds(show.id, clamped) }
    }

    fun setSkipOutro(seconds: Int) {
        val show = state.value.activeShow ?: return
        val clamped = seconds.coerceIn(0, 600)
        viewModelScope.launch { container.showRepository.setSkipOutroSeconds(show.id, clamped) }
    }

    /**
     * Flips the listening direction. Resume is derived from the show row, so it
     * moves to the first unplayed episode from the new end on its own; saved
     * positions and played flags are not touched. The download window does have
     * to be re-planned, because "ahead" just changed ends.
     */
    fun setDirection(direction: ListeningDirection) {
        val show = state.value.activeShow ?: return
        viewModelScope.launch {
            container.showRepository.setDirection(show.id, direction)
            rescheduleDownloads()
        }
    }

    // -- Global -------------------------------------------------------------

    fun setDownloadAhead(count: Int) = edit {
        container.settingsStore.setDownloadAhead(count)
        rescheduleDownloads()
    }

    fun setStorageCapGb(gb: Int) = edit {
        container.settingsStore.setStorageCapBytes(gb.toLong() * 1024 * 1024 * 1024)
        rescheduleDownloads()
    }

    fun setWifiOnly(value: Boolean) = edit {
        container.settingsStore.setWifiOnlyDownloads(value)
        // Constraints are baked into the enqueued work, so this only takes
        // effect once the jobs are re-registered.
        container.workScheduler.scheduleDailyRefresh(value)
        container.workScheduler.requestDownloadAhead(value)
    }

    fun setDeletePlayedAfterHours(hours: Int) = edit {
        container.settingsStore.setDeletePlayedAfterHours(hours)
        rescheduleDownloads()
    }

    fun setAutoPlayOnBluetooth(value: Boolean) = edit {
        container.settingsStore.setAutoPlayOnBluetooth(value)
    }

    fun setSkipForwardSeconds(seconds: Int) = edit {
        container.settingsStore.setSkipForwardSeconds(seconds)
    }

    fun setSkipBackSeconds(seconds: Int) = edit {
        container.settingsStore.setSkipBackSeconds(seconds)
    }

    fun setResumeTiers(tiers: List<ResumeTier>) = edit {
        container.settingsStore.setResumeTiers(tiers)
    }

    fun setBackupFolder(uri: String?) = edit {
        container.settingsStore.setBackupFolderUri(uri)
        if (uri != null) container.workScheduler.scheduleNightlyBackup()
    }

    fun setBackupEnabled(value: Boolean) = edit {
        container.settingsStore.setBackupEnabled(value)
    }

    fun setColorPalette(palette: BackbeePalette) = edit {
        container.settingsStore.setColorPalette(palette)
    }

    fun backupNow() = container.workScheduler.backupNow()

    // -- Restore ------------------------------------------------------------

    /** Reads and checks the chosen file. Nothing changes until [confirmRestore]. */
    fun inspectBackup(uri: Uri) {
        _restore.value = RestoreUiState(inspecting = true)
        viewModelScope.launch {
            container.backupRestorer.inspect(uri).fold(
                onSuccess = { _restore.value = RestoreUiState(preview = it) },
                onFailure = { _restore.value = RestoreUiState(error = it.message ?: "Could not read that file.") },
            )
        }
    }

    fun cancelRestore() {
        container.backupRestorer.discardStaged()
        _restore.value = RestoreUiState()
    }

    /**
     * Swaps the database and restarts the app. Playback stops first so the
     * player is not mid-write when the file changes underneath it.
     */
    fun confirmRestore() {
        if (_restore.value.preview == null) return
        _restore.value = _restore.value.copy(restoring = true, error = null)
        player.stopAndClear()
        viewModelScope.launch {
            container.backupRestorer.restoreStaged(container.database).onFailure {
                _restore.value = _restore.value.copy(
                    restoring = false,
                    error = it.message ?: "Restore failed. Nothing was changed.",
                )
            }
        }
    }

    fun refreshFeedsNow() = container.workScheduler.refreshNow()

    private suspend fun rescheduleDownloads() {
        container.workScheduler.requestDownloadAhead(container.settingsStore.current().wifiOnlyDownloads)
    }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
