package dev.backbee.ui.shelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.backbee.core.feed.ArchiveProbe
import dev.backbee.core.playback.RelativeTime
import dev.backbee.data.db.ShowEntity
import dev.backbee.data.db.ShowProgress
import dev.backbee.data.net.DirectoryResult
import dev.backbee.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A show plus the bookmark line the shelf shows under it. */
data class ShelfEntry(
    val show: ShowEntity,
    val progress: ShowProgress?,
    /** When this show was last played, for the "8 MONTHS AGO" half of the line. */
    val lastTouchedAt: Long? = null,
) {
    val isComplete: Boolean get() = show.completedAt != null

    /**
     * "PAUSED AT EP 89 / 412 · 8 MONTHS AGO". The elapsed time matters as much
     * as the position: it is what tells you how cold the show has gone.
     */
    fun bookmarkLine(nowMillis: Long = System.currentTimeMillis()): String {
        val progress = progress ?: return "NOT LOADED YET"
        if (progress.totalEpisodes == 0) return "NO EPISODES"

        val age = lastTouchedAt?.let { " · " + RelativeTime.since(it, nowMillis) }.orEmpty()
        return when {
            isComplete -> "COMPLETED · ${progress.totalEpisodes} EPS"
            progress.playedEpisodes == 0 -> "NOT STARTED · ${progress.totalEpisodes} EPS"
            else -> "PAUSED AT EP ${progress.playedEpisodes + 1} / ${progress.totalEpisodes}$age"
        }
    }
}

data class AddShowState(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<DirectoryResult> = emptyList(),
    val adding: Boolean = false,
    val message: String? = null,
    /** Phase 0's verdict, rendered as readout lines rather than buried in a log. */
    val probeLines: List<String> = emptyList(),
    val probeUsable: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ShelfViewModel(private val container: AppContainer) : ViewModel() {

    private val shows = container.showRepository

    private val _addState = MutableStateFlow(AddShowState())
    val addState: StateFlow<AddShowState> = _addState.asStateFlow()

    val entries: StateFlow<List<ShelfEntry>> = shows.observeShows()
        .flatMapLatest { list ->
            if (list.isEmpty()) {
                flowOf(emptyList())
            } else {
                // One progress flow per show, recombined - the shelf is a handful
                // of rows, so this stays cheap.
                combine(list.map { show -> shows.observeProgress(show.id).map { show to it } }) { pairs ->
                    pairs.map { (show, progress) ->
                        ShelfEntry(show, progress, show.completedAt ?: show.lastRefreshedAt)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        _addState.value = _addState.value.copy(query = value)
    }

    fun search() {
        val query = _addState.value.query.trim()
        if (query.isEmpty()) return
        _addState.value = _addState.value.copy(searching = true, message = null)
        viewModelScope.launch {
            try {
                val results = shows.searchDirectory(query)
                _addState.value = _addState.value.copy(
                    searching = false,
                    results = results,
                    message = if (results.isEmpty()) "Nothing found for \"$query\"" else null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _addState.value = _addState.value.copy(
                    searching = false,
                    message = "Search failed: ${e.message}",
                )
            }
        }
    }

    /**
     * Adds by feed URL. The Phase 0 verdict comes back with it, because "this
     * feed only exposes the last 300 of 1,247 episodes" is something to find out
     * now rather than 300 episodes in.
     */
    fun addByUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        _addState.value = _addState.value.copy(adding = true, message = null, probeLines = emptyList())

        viewModelScope.launch {
            try {
                val result = shows.addShow(trimmed)
                _addState.value = _addState.value.copy(
                    adding = false,
                    query = "",
                    results = emptyList(),
                    probeLines = probeReadout(result.probe, result.added, result.recoveredFromDirectory),
                    probeUsable = result.probe.isUsable || result.recoveredFromDirectory,
                )
                container.workScheduler.requestDownloadAhead(
                    container.settingsStore.current().wifiOnlyDownloads
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _addState.value = _addState.value.copy(
                    adding = false,
                    probeLines = listOf("COULD NOT ADD: ${e.message}"),
                    probeUsable = false,
                )
            }
        }
    }

    /** The Phase 0 report as the terminal readout the spec asks for. */
    private fun probeReadout(probe: ArchiveProbe.Report, added: Int, recovered: Boolean): List<String> =
        buildList {
            add("FEED OK // ${probe.episodesAfterPaging} ITEMS DECLARED")
            add("ADDED $added EPISODE(S) TO THE ARCHIVE")
            add(
                if (probe.pagesFollowed > 1) "PAGED FEED: rel=next FOLLOWED x${probe.pagesFollowed}"
                else "PAGED FEED: rel=next ABSENT"
            )
            probe.expectedTotal?.let { add("DIRECTORY CROSS-CHECK: $it") }
            if (probe.episodesWithoutEnclosure > 0) {
                add("UNPLAYABLE (NO ENCLOSURE): ${probe.episodesWithoutEnclosure}")
            }
            add(
                when {
                    recovered -> "FEED WAS TRUNCATED. RECOVERED VIA PODCAST INDEX."
                    probe.verdict == ArchiveProbe.Verdict.COMPLETE ->
                        "FULL ARCHIVE AVAILABLE. NO IMPORT NEEDED."
                    probe.verdict == ArchiveProbe.Verdict.COMPLETE_VIA_PAGING ->
                        "FULL ARCHIVE AVAILABLE VIA PAGING."
                    probe.verdict == ArchiveProbe.Verdict.LIKELY_TRUNCATED ->
                        "WARNING: ARCHIVE LOOKS TRUNCATED."
                    else -> "INCONCLUSIVE. VERIFY BEFORE STARTING THIS SHOW."
                }
            )
            probe.findings.firstOrNull()?.let { add(it.uppercase()) }
        }

    fun makeActive(showId: Long) {
        viewModelScope.launch {
            shows.makeActive(showId)
            container.workScheduler.requestDownloadAhead(
                container.settingsStore.current().wifiOnlyDownloads
            )
        }
    }

    fun removeShow(showId: Long) {
        viewModelScope.launch {
            container.episodeFiles.deleteShow(showId)
            shows.deleteShow(showId)
        }
    }

    fun dismissMessage() {
        _addState.value = _addState.value.copy(message = null, probeLines = emptyList())
    }
}
