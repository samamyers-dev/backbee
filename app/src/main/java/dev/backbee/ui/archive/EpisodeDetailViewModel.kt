package dev.backbee.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.backbee.data.db.EpisodeRow
import dev.backbee.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EpisodeDetailUiState(
    val row: EpisodeRow? = null,
    val description: String? = null,
    val noteDraft: String = "",
)

class EpisodeDetailViewModel(
    private val container: AppContainer,
    private val episodeId: Long,
) : ViewModel() {

    private val playback = container.playbackRepository

    private val _noteDraft = MutableStateFlow<String?>(null)
    private val _description = MutableStateFlow<String?>(null)

    val state: StateFlow<EpisodeDetailUiState> =
        combine(playback.observeRow(episodeId), _noteDraft, _description) { row, draft, description ->
            EpisodeDetailUiState(
                row = row,
                description = description,
                // The stored note seeds the field once; after that the draft is
                // whatever is being typed, so a background write cannot yank
                // text out from under the cursor.
                noteDraft = draft ?: row?.note.orEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EpisodeDetailUiState())

    init {
        viewModelScope.launch { _description.value = playback.description(episodeId) }
    }

    fun setNoteDraft(value: String) {
        _noteDraft.value = value
    }

    fun saveNote() {
        val note = _noteDraft.value ?: return
        viewModelScope.launch { playback.setNote(episodeId, note) }
    }

    fun toggleStar() {
        val current = state.value.row?.isStarred ?: false
        viewModelScope.launch { playback.setStarred(episodeId, !current) }
    }

    fun setPlayed(played: Boolean) {
        viewModelScope.launch {
            if (played) {
                playback.markPlayed(episodeId, state.value.row?.durationSeconds ?: 0)
            } else {
                playback.markUnplayed(episodeId)
            }
            nudgeDownloads()
        }
    }

    fun downloadNow() {
        viewModelScope.launch {
            container.downloadRepository.enqueue(episodeId)
            nudgeDownloads()
        }
    }

    fun removeDownload() {
        viewModelScope.launch { container.downloadRepository.removeDownload(episodeId) }
    }

    private suspend fun nudgeDownloads() {
        container.workScheduler.requestDownloadAhead(container.settingsStore.current().wifiOnlyDownloads)
    }
}
