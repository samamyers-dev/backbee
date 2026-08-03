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

/** A year label and the list position it starts at, for the fast-scroll rail. */
data class YearMarker(val year: Int, val listIndex: Int)

data class ArchiveUiState(
    val show: ShowEntity? = null,
    val rows: List<EpisodeRow> = emptyList(),
    val years: List<YearMarker> = emptyList(),
    val query: String = "",
    val searching: Boolean = false,
    /**
     * List position of the earliest unfinished episode, so the list can open
     * there. Null while searching: the index would be into the filtered rows,
     * and jumping to "the first unplayed search hit" is not the same place.
     */
    val resumeIndex: Int? = null,
    val totalEpisodes: Int = 0,
    val playedCount: Int = 0,
    val downloadedCount: Int = 0,
    val starredCount: Int = 0,
    /** Episode id -> the band to draw above it, e.g. "2021 · EPISODES 231-286". */
    private val yearBands: Map<Long, String> = emptyMap(),
) {
    fun yearBandFor(episodeId: Long): String? = yearBands[episodeId]
}

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveViewModel(private val container: AppContainer) : ViewModel() {

    private val playback = container.playbackRepository

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val activeShow = container.showRepository.observeActiveShow()

    private val allRows = activeShow.flatMapLatest { show ->
        if (show == null) flowOf(emptyList()) else playback.observeArchive(show.id)
    }

    private val visibleRows = combine(activeShow, _query) { show, q -> show to q }
        .flatMapLatest { (show, q) ->
            when {
                show == null -> flowOf(emptyList())
                q.isBlank() -> playback.observeArchive(show.id)
                else -> playback.search(show.id, q.trim())
            }
        }

    val state: StateFlow<ArchiveUiState> =
        combine(activeShow, allRows, visibleRows, _query) { show, all, rows, q ->
            val searching = q.isNotBlank()
            ArchiveUiState(
                show = show,
                rows = rows,
                years = if (searching) emptyList() else yearMarkers(rows),
                query = q,
                searching = searching,
                resumeIndex = if (searching) null
                else rows.indexOfFirst { !it.isPlayed }.takeIf { it >= 0 },
                // Counters always describe the whole archive, never the current
                // filter - "PLAYED 246" means 246 of the show, not of the search.
                totalEpisodes = all.size,
                playedCount = all.count { it.isPlayed },
                downloadedCount = all.count { it.isDownloaded },
                starredCount = all.count { it.isStarred },
                yearBands = if (searching) emptyMap() else yearBands(rows),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArchiveUiState())

    fun setQuery(value: String) {
        _query.value = value
    }

    /**
     * Jump-to-episode-number. Prefers the publisher's own numbering when the
     * feed has it and falls back to archive position, which is what the rest of
     * the UI shows when it does not.
     */
    fun indexForEpisodeNumber(number: Int): Int? {
        val rows = state.value.rows
        val byNumber = rows.indexOfFirst { it.episodeNumber == number }
        if (byNumber >= 0) return byNumber
        return rows.indexOfFirst { it.orderIndex == number - 1 }.takeIf { it >= 0 }
    }

    private fun yearOf(millis: Long): Int {
        calendar.timeInMillis = millis
        return calendar.get(Calendar.YEAR)
    }

    private val calendar = Calendar.getInstance(TimeZone.getDefault())

    private fun yearMarkers(rows: List<EpisodeRow>): List<YearMarker> {
        val markers = mutableListOf<YearMarker>()
        var last = Int.MIN_VALUE
        rows.forEachIndexed { index, row ->
            val year = yearOf(row.pubDate ?: return@forEachIndexed)
            if (year != last) {
                markers += YearMarker(year, index)
                last = year
            }
        }
        return markers
    }

    /** `2021 · EPISODES 231-286` above the first row of each year. */
    private fun yearBands(rows: List<EpisodeRow>): Map<Long, String> {
        val bands = mutableMapOf<Long, String>()
        var last = Int.MIN_VALUE
        rows.forEachIndexed { index, row ->
            val year = yearOf(row.pubDate ?: return@forEachIndexed)
            if (year != last) {
                val firstNumber = row.episodeNumber ?: (row.orderIndex + 1)
                val lastOfYear = rows.drop(index).lastOrNull {
                    it.pubDate != null && yearOf(it.pubDate) == year
                }
                val lastNumber = lastOfYear?.let { it.episodeNumber ?: (it.orderIndex + 1) } ?: firstNumber
                bands[row.id] = "$year · EPISODES $firstNumber-$lastNumber"
                last = year
            }
        }
        return bands
    }
}
