package dev.backbee.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.backbee.data.db.EpisodeRow
import dev.backbee.data.db.ShowEntity
import dev.backbee.di.AppContainer
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** A year label and the list position it starts at, for the fast-scroll scrubber. */
data class YearMarker(val year: Int, val listIndex: Int)

data class ArchiveUiState(
    val show: ShowEntity? = null,
    val rows: List<EpisodeRow> = emptyList(),
    val years: List<YearMarker> = emptyList(),
    val query: String = "",
    val searching: Boolean = false,
    /** Where the bookmark sits, so the list can open there. */
    val resumeIndex: Int? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveViewModel(private val container: AppContainer) : ViewModel() {

    private val playback = container.playbackRepository

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val activeShow = container.showRepository.observeActiveShow()

    private val rows = combine(activeShow, _query) { show, query -> show to query }
        .flatMapLatest { (show, query) ->
            when {
                show == null -> flowOf(emptyList())
                query.isBlank() -> playback.observeArchive(show.id)
                else -> playback.search(show.id, query.trim())
            }
        }

    val state: StateFlow<ArchiveUiState> = combine(activeShow, rows, _query) { show, rows, query ->
        ArchiveUiState(
            show = show,
            rows = rows,
            years = if (query.isBlank()) yearMarkers(rows) else emptyList(),
            query = query,
            searching = query.isNotBlank(),
            resumeIndex = rows.indexOfFirst { !it.isPlayed }.takeIf { it >= 0 },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArchiveUiState())

    fun setQuery(value: String) {
        _query.value = value
    }

    /**
     * Jump-to-episode-number. Accepts the publisher's own numbering when the feed
     * has it and falls back to position in the archive, which is what the rest of
     * the UI shows when it does not.
     */
    fun indexForEpisodeNumber(number: Int): Int? {
        val rows = state.value.rows
        val byNumber = rows.indexOfFirst { it.episodeNumber == number }
        if (byNumber >= 0) return byNumber
        return rows.indexOfFirst { it.orderIndex == number - 1 }.takeIf { it >= 0 }
    }

    private fun yearMarkers(rows: List<EpisodeRow>): List<YearMarker> {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        val markers = mutableListOf<YearMarker>()
        var lastYear = Int.MIN_VALUE
        rows.forEachIndexed { index, row ->
            val millis = row.pubDate ?: return@forEachIndexed
            calendar.timeInMillis = millis
            val year = calendar.get(Calendar.YEAR)
            if (year != lastYear) {
                markers += YearMarker(year, index)
                lastYear = year
            }
        }
        return markers
    }
}
