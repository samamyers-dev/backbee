package dev.backbee.ui.now

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.backbee.core.playback.ArchiveProgress
import dev.backbee.data.db.EpisodeRow
import dev.backbee.data.db.ShowEntity
import dev.backbee.di.AppContainer
import dev.backbee.playback.PlayerConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NowUiState(
    val loading: Boolean = true,
    val show: ShowEntity? = null,
    val resumeTarget: EpisodeRow? = null,
    val progress: ArchiveProgress? = null,
    val upNext: List<EpisodeRow> = emptyList(),
    /** True once every episode is played; the Now screen hands over to the recap. */
    val archiveComplete: Boolean = false,
) {
    val hasShow: Boolean get() = show != null
}

@OptIn(ExperimentalCoroutinesApi::class)
class NowViewModel(
    private val container: AppContainer,
    private val player: PlayerConnection,
) : ViewModel() {

    private val shows = container.showRepository
    private val playback = container.playbackRepository

    private val activeShow = shows.observeActiveShow()

    // Follows the database rather than the player, so the Now screen stays right
    // when an episode finishes in the car with the phone screen off.
    private val resumeTarget = activeShow.flatMapLatest { show ->
        if (show == null) flowOf(null) else playback.observeResumeTarget(show.id)
    }

    private val upNext = combine(activeShow, resumeTarget) { show, target -> show to target }
        .flatMapLatest { (show, target) ->
            if (show == null) flowOf(emptyList())
            else playback.observeUpNext(show.id, target?.orderIndex ?: -1, UP_NEXT_COUNT)
        }

    private val progress = activeShow.flatMapLatest { show ->
        if (show == null) flowOf(null) else shows.observeProgress(show.id)
    }

    val state: StateFlow<NowUiState> = combine(
        activeShow,
        resumeTarget,
        upNext,
        progress,
    ) { show, target, next, showProgress ->
        NowUiState(
            loading = false,
            show = show,
            resumeTarget = target,
            upNext = next,
            archiveComplete = show != null && showProgress != null &&
                showProgress.totalEpisodes > 0 && showProgress.playedEpisodes >= showProgress.totalEpisodes,
            progress = showProgress?.let {
                ArchiveProgress(
                    // The strip reads "Ep 312 of 1,247", so the current episode
                    // counts as the one being worked on, not one already done.
                    currentPosition = (target?.orderIndex?.plus(1)) ?: it.totalEpisodes,
                    totalEpisodes = it.totalEpisodes,
                    remainingSeconds = it.remainingSeconds,
                    totalSeconds = it.totalSeconds,
                    speed = show?.speed ?: 1f,
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowUiState())

    /** Persists the show's speed and applies it to playback already in flight. */
    fun setSpeed(speed: Float) {
        val show = state.value.show ?: return
        player.setSpeed(speed)
        viewModelScope.launch { shows.updateShow(show.copy(speed = speed)) }
    }

    fun refreshNow() {
        container.workScheduler.refreshNow()
    }

    companion object {
        private const val UP_NEXT_COUNT = 3
    }
}
