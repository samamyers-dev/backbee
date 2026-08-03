package dev.backbee.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.backbee.data.db.EpisodeRow
import dev.backbee.data.repo.PlaybackRepository
import dev.backbee.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which side of this episode a bulk mark applies to. This episode is never included. */
enum class BulkScope { BEFORE, AFTER }

data class BulkUiState(
    /** Open confirmation: the range has been counted but nothing has been written. */
    val scope: BulkScope? = null,
    val summary: PlaybackRepository.RangeSummary? = null,
    val working: Boolean = false,
    /** The last applied change, kept only so it can be undone. */
    val done: PlaybackRepository.BulkPlayedChange? = null,
)

data class EpisodeDetailUiState(
    val row: EpisodeRow? = null,
    val description: String? = null,
    val noteDraft: String = "",
    val bulk: BulkUiState = BulkUiState(),
)

class EpisodeDetailViewModel(
    private val container: AppContainer,
    private val episodeId: Long,
) : ViewModel() {

    private val playback = container.playbackRepository

    private val _noteDraft = MutableStateFlow<String?>(null)
    private val _description = MutableStateFlow<String?>(null)

    private val _bulk = MutableStateFlow(BulkUiState())

    val state: StateFlow<EpisodeDetailUiState> =
        combine(playback.observeRow(episodeId), _noteDraft, _description, _bulk) { row, draft, description, bulk ->
            EpisodeDetailUiState(
                row = row,
                description = description,
                // The stored note seeds the field once; after that the draft is
                // whatever is being typed, so a background write cannot yank
                // text out from under the cursor.
                noteDraft = draft ?: row?.note.orEmpty(),
                bulk = bulk,
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

    fun setKeepAfterPlaying(keep: Boolean) {
        viewModelScope.launch { playback.setKeepAfterPlaying(episodeId, keep) }
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

    // -- Bulk marking -------------------------------------------------------

    /**
     * Counts the range and opens the confirmation. Nothing is written yet: a
     * mis-tap here would otherwise rewrite hundreds of rows, and the count is
     * the only way to know in advance how big the mistake would be.
     */
    fun openBulk(scope: BulkScope) {
        val row = state.value.row ?: return
        viewModelScope.launch {
            val range = rangeFor(row, scope) ?: run {
                _bulk.value = BulkUiState(scope = scope, summary = PlaybackRepository.RangeSummary(0, 0))
                return@launch
            }
            _bulk.value = BulkUiState(
                scope = scope,
                summary = playback.summarizeRange(row.showId, range.first, range.second),
            )
        }
    }

    fun dismissBulk() {
        _bulk.value = BulkUiState()
    }

    fun applyBulk(played: Boolean) {
        val row = state.value.row ?: return
        val scope = _bulk.value.scope ?: return
        viewModelScope.launch {
            _bulk.value = _bulk.value.copy(working = true)
            val range = rangeFor(row, scope)
            val change = range?.let { playback.markRange(row.showId, it.first, it.second, played) }
            // A null change means every episode in the range was already in the
            // requested state. Closing silently is right - there is nothing to
            // report and nothing to undo.
            _bulk.value = if (change == null) BulkUiState() else BulkUiState(done = change)
            if (change != null) nudgeDownloads()
        }
    }

    fun undoBulk() {
        val change = _bulk.value.done ?: return
        viewModelScope.launch {
            _bulk.value = _bulk.value.copy(working = true)
            playback.undoBulk(change)
            _bulk.value = BulkUiState()
            nudgeDownloads()
        }
    }

    /** Inclusive order-index bounds, never including this episode itself. */
    private suspend fun rangeFor(row: EpisodeRow, scope: BulkScope): Pair<Int, Int>? = when (scope) {
        BulkScope.BEFORE -> if (row.orderIndex <= 0) null else 0 to (row.orderIndex - 1)
        BulkScope.AFTER -> {
            val last = playback.lastOrderIndex(row.showId)
            if (row.orderIndex >= last) null else (row.orderIndex + 1) to last
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
