package dev.backbee.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.backbee.core.playback.ResumeTier
import dev.backbee.data.db.ShowEntity
import dev.backbee.data.prefs.Settings
import dev.backbee.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: Settings = Settings(),
    val activeShow: ShowEntity? = null,
    val bytesOnDisk: Long = 0,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        container.settingsStore.settings,
        container.showRepository.observeActiveShow(),
        container.downloadRepository.observeBytesOnDisk(),
    ) { settings, show, bytes ->
        SettingsUiState(settings, show, bytes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    // -- Per-show -----------------------------------------------------------

    fun setShowSpeed(speed: Float) = updateShow { it.copy(speed = speed.coerceIn(0.5f, 4f)) }

    fun setSkipIntro(seconds: Int) = updateShow { it.copy(skipIntroSeconds = seconds.coerceIn(0, 600)) }

    fun setSkipOutro(seconds: Int) = updateShow { it.copy(skipOutroSeconds = seconds.coerceIn(0, 600)) }

    private fun updateShow(transform: (ShowEntity) -> ShowEntity) {
        val show = state.value.activeShow ?: return
        viewModelScope.launch { container.showRepository.updateShow(transform(show)) }
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

    fun backupNow() = container.workScheduler.backupNow()

    fun refreshFeedsNow() = container.workScheduler.refreshNow()

    private suspend fun rescheduleDownloads() {
        container.workScheduler.requestDownloadAhead(container.settingsStore.current().wifiOnlyDownloads)
    }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
