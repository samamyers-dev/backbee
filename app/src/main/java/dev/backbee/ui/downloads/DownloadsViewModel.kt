package dev.backbee.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.backbee.data.db.DownloadState
import dev.backbee.data.db.EpisodeRow
import dev.backbee.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DownloadsUiState(
    val capBytes: Long = 0,
    val usedBytes: Long = 0,
    val downloadAhead: Int = 10,
    val inProgress: List<EpisodeRow> = emptyList(),
    val onDevice: List<EpisodeRow> = emptyList(),
    val failed: List<EpisodeRow> = emptyList(),
    val queued: List<EpisodeRow> = emptyList(),
    val playedReclaimableBytes: Long = 0,
) {
    val capReached: Boolean get() = capBytes > 0 && usedBytes >= capBytes
    val fractionUsed: Float get() = if (capBytes <= 0) 0f else (usedBytes.toFloat() / capBytes)
}

/**
 * The download engine made visible.
 *
 * Normally this screen should never be needed - the whole point is that
 * downloads happen silently. It exists for the two moments when they cannot:
 * the storage cap is full, or the network went away mid-queue.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModel(private val container: AppContainer) : ViewModel() {

    private val rows = container.showRepository.observeActiveShow().flatMapLatest { show ->
        if (show == null) flowOf(emptyList()) else container.playbackRepository.observeArchive(show.id)
    }

    val state: StateFlow<DownloadsUiState> = combine(
        rows,
        container.settingsStore.settings,
        container.downloadRepository.observeBytesOnDisk(),
    ) { all, settings, usedBytes ->
        DownloadsUiState(
            capBytes = settings.storageCapBytes,
            usedBytes = usedBytes,
            downloadAhead = settings.downloadAhead,
            inProgress = all.filter { it.downloadState == DownloadState.RUNNING },
            queued = all.filter { it.downloadState == DownloadState.QUEUED },
            failed = all.filter { it.downloadState == DownloadState.FAILED },
            onDevice = all.filter { it.isDownloaded },
            playedReclaimableBytes = all
                .filter { it.isDownloaded && it.isPlayed && !it.isKept }
                .sumOf { it.bytesDone ?: 0 },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    fun raiseCap() {
        viewModelScope.launch {
            val current = container.settingsStore.current().storageCapBytes
            container.settingsStore.setStorageCapBytes(current + GB)
            container.workScheduler.requestDownloadAhead(container.settingsStore.current().wifiOnlyDownloads)
        }
    }

    /** Reclaims every played episode now, ignoring the delete-after window. */
    fun purgePlayed() {
        viewModelScope.launch {
            state.value.onDevice
                .filter { it.isPlayed && !it.isKept }
                .forEach { container.downloadRepository.removeDownload(it.id) }
            container.workScheduler.requestDownloadAhead(container.settingsStore.current().wifiOnlyDownloads)
        }
    }

    fun retryFailed() {
        viewModelScope.launch {
            state.value.failed.forEach { container.downloadRepository.enqueue(it.id) }
            container.workScheduler.requestDownloadAhead(container.settingsStore.current().wifiOnlyDownloads)
        }
    }

    private companion object {
        const val GB = 1024L * 1024 * 1024
    }
}
