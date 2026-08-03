package dev.backbee.ui.shelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.backbee.core.feed.ArchiveProbe
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
) {
    val isComplete: Boolean get() = show.completedAt != null

    /** "Paused at ep 89 of 412", or a completion badge, or nothing yet. */
    fun bookmarkLine(): String {
        val progress = progress ?: return "Not loaded yet"
        if (progress.totalEpisodes == 0) return "No episodes"
        if (isComplete) return "Finished · ${progress.totalEpisodes} episodes"
        if (progress.playedEpisodes == 0) return "Not started · ${progress.totalEpisodes} episodes"
        return "Paused at ep ${progress.playedEpisodes + 1} of ${progress.totalEpisodes}"
    }
}

data class AddShowState(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<DirectoryResult> = emptyList(),
    val adding: Boolean = false,
    val message: String? = null,
    /** Phase 0's verdict for the show just added, surfaced rather than buried in a log. */
    val lastProbe: ArchiveProbe.Report? = null,
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
                    pairs.map { (show, progress) -> ShelfEntry(show, progress) }
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
        _addState.value = _addState.value.copy(adding = true, message = null, lastProbe = null)

        viewModelScope.launch {
            try {
                val result = shows.addShow(trimmed)
                _addState.value = _addState.value.copy(
                    adding = false,
                    query = "",
                    results = emptyList(),
                    lastProbe = result.probe,
                    message = summarise(result.probe, result.added, result.recoveredFromDirectory),
                )
                container.workScheduler.requestDownloadAhead(
                    container.settingsStore.current().wifiOnlyDownloads
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _addState.value = _addState.value.copy(adding = false, message = "Could not add: ${e.message}")
            }
        }
    }

    private fun summarise(probe: ArchiveProbe.Report, added: Int, recovered: Boolean): String = buildString {
        append("Added $added episode(s). ")
        when (probe.verdict) {
            ArchiveProbe.Verdict.COMPLETE ->
                append("The feed appears to hold the full archive.")

            ArchiveProbe.Verdict.COMPLETE_VIA_PAGING ->
                append("Full archive reached across ${probe.pagesFollowed} pages.")

            ArchiveProbe.Verdict.LIKELY_TRUNCATED -> if (recovered) {
                append("The feed was truncated; the rest came from Podcast Index.")
            } else {
                append("Warning: this feed looks truncated. ${probe.findings.firstOrNull().orEmpty()}")
            }

            ArchiveProbe.Verdict.INCONCLUSIVE ->
                append("Could not confirm the archive is complete - worth checking by hand.")
        }
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
        _addState.value = _addState.value.copy(message = null, lastProbe = null)
    }
}
