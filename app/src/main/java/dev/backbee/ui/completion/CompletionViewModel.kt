package dev.backbee.ui.completion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.backbee.core.stats.CompletionStats
import dev.backbee.data.db.EpisodeRow
import dev.backbee.data.db.ShowEntity
import dev.backbee.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CompletionUiState(
    val loading: Boolean = true,
    val show: ShowEntity? = null,
    val stats: CompletionStats? = null,
    val starred: List<EpisodeRow> = emptyList(),
    val otherShows: List<ShowEntity> = emptyList(),
)

/**
 * Finishing a 1,200-episode archive is a year of someone's listening. The recap
 * is the app acknowledging that before pointing at the next book on the shelf.
 */
class CompletionViewModel(
    private val container: AppContainer,
    private val showId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(CompletionUiState())
    val state: StateFlow<CompletionUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val show = container.showRepository.getShow(showId)
        val totals = container.showRepository.listeningTotals(showId)
        val starred = container.playbackRepository.starredEpisodes(showId)
        val others = container.database.showDao().getAll().filter { it.id != showId && it.completedAt == null }

        _state.value = CompletionUiState(
            loading = false,
            show = show,
            starred = starred,
            otherShows = others,
            stats = if (show == null || totals == null) null else CompletionStats(
                showTitle = show.title,
                episodeCount = totals.episodeCount,
                listenedSeconds = totals.listenedSeconds,
                firstPlayedAtMillis = totals.firstPlayedAt,
                lastPlayedAtMillis = totals.lastPlayedAt,
                starredCount = totals.starredCount,
                averageSpeed = show.speed,
            ),
        )
    }

    fun activateShow(nextShowId: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            container.showRepository.makeActive(nextShowId)
            container.workScheduler.requestDownloadAhead(
                container.settingsStore.current().wifiOnlyDownloads
            )
            onDone()
        }
    }
}
