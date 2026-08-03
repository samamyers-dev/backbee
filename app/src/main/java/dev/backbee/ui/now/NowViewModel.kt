package dev.backbee.ui.now

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.backbee.core.playback.ArchiveProgress
import dev.backbee.core.playback.SmartResume
import dev.backbee.data.db.EpisodeRow
import dev.backbee.data.db.ShowEntity
import dev.backbee.data.db.ShowProgress
import dev.backbee.di.AppContainer
import dev.backbee.playback.PlayerConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NowUiState(
    val loading: Boolean = true,
    val show: ShowEntity? = null,
    /** The bookmark: the earliest episode not yet finished. */
    val resumeTarget: EpisodeRow? = null,
    /** Whatever the player actually has loaded, which need not be the bookmark. */
    val nowPlaying: EpisodeRow? = null,
    val progress: ArchiveProgress? = null,
    val upNext: List<EpisodeRow> = emptyList(),
    /** Year label paired with its 0f..1f position along the archive spine. */
    val yearMarks: List<Pair<Int, Float>> = emptyList(),
    /** How many unplayed episodes ahead are already on disk. */
    val downloadedAhead: Int = 0,
    /** "SAVED 26:41 → RESUME FROM 26:11 · TIER −30S", or empty when there is nothing to say. */
    val resumeReadout: String = "",
    val archiveComplete: Boolean = false,
) {
    val hasShow: Boolean get() = show != null

    /**
     * What this screen is about. Playing something from the archive makes Now
     * follow it - otherwise picking episode 500 leaves the screen still talking
     * about episode 1, which is the same as the screen being wrong.
     */
    val current: EpisodeRow? get() = nowPlaying ?: resumeTarget

    /**
     * The player is somewhere other than the bookmark. Worth saying out loud:
     * the bookmark has not moved, and auto-advance will carry on from here, not
     * from there.
     */
    val isDetour: Boolean
        get() = nowPlaying != null && resumeTarget != null && nowPlaying.id != resumeTarget.id
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

    /**
     * The episode the player has loaded, as a full archive row.
     *
     * Keyed off the id alone: the connection republishes its state once a second
     * while the screen is on, and re-running this query on every tick would be a
     * query per second for a value that changes once an hour.
     */
    private val nowPlaying = combine(
        activeShow,
        player.state.map { it.episodeId }.distinctUntilChanged(),
    ) { show, episodeId -> show to episodeId }
        .flatMapLatest { (show, episodeId) ->
            if (show == null || episodeId == null) flowOf(null)
            // Right after a shelf switch the player can still hold an episode
            // from the previous show. Now belongs to the active show.
            else playback.observeRow(episodeId).map { row -> row?.takeIf { it.showId == show.id } }
        }

    /** Whatever Now is currently about, playing or bookmarked. */
    private val current = combine(nowPlaying, resumeTarget) { playing, bookmark -> playing ?: bookmark }

    private val currentOrderIndex = current.map { it?.orderIndex ?: -1 }.distinctUntilChanged()

    // Up next follows the current episode rather than the bookmark, because it
    // is a prediction of what auto-advance will do, and auto-advance continues
    // from wherever the player actually is.
    private val upNext = combine(activeShow, currentOrderIndex) { show, index -> show to index }
        .flatMapLatest { (show, index) ->
            if (show == null) flowOf(emptyList())
            else playback.observeUpNext(show.id, index, UP_NEXT_COUNT)
        }

    private val progress = activeShow.flatMapLatest { show ->
        if (show == null) flowOf(null) else shows.observeProgress(show.id)
    }

    private val downloadedAhead = combine(activeShow, currentOrderIndex) { show, index -> show to index }
        .flatMapLatest { (show, index) ->
            if (show == null) flowOf(0)
            else playback.observeDownloadedAhead(show.id, index.coerceAtLeast(0))
        }

    // Year marks change only when the archive is re-ingested, so they are loaded
    // once per show rather than recomputed on every position write.
    private val yearMarks = MutableStateFlow<List<Pair<Int, Float>>>(emptyList())

    private val resumeReadout = MutableStateFlow("")

    init {
        viewModelScope.launch {
            activeShow.map { it?.id }.distinctUntilChanged().collect { showId ->
                yearMarks.value = if (showId == null) emptyList() else loadYearMarks(showId)
            }
        }
        viewModelScope.launch {
            current.collect { target -> resumeReadout.value = describeResume(target) }
        }
    }

    val state: StateFlow<NowUiState> = combine(
        combine(activeShow, resumeTarget, nowPlaying, upNext, progress) { show, target, playing, next, p ->
            Core(show, target, playing, next, p)
        },
        yearMarks,
        downloadedAhead,
        resumeReadout,
    ) { core, marks, ahead, readout ->
        val (show, target, playing, next, showProgress) = core
        NowUiState(
            loading = false,
            show = show,
            resumeTarget = target,
            nowPlaying = playing,
            upNext = next,
            yearMarks = marks,
            downloadedAhead = ahead,
            resumeReadout = readout,
            archiveComplete = show != null && showProgress != null &&
                showProgress.totalEpisodes > 0 && showProgress.playedEpisodes >= showProgress.totalEpisodes,
            progress = showProgress?.let {
                ArchiveProgress(
                    // The strip reads "Ep 312 of 1,247", so the current episode
                    // counts as the one being worked on, not one already done.
                    currentPosition = ((playing ?: target)?.orderIndex?.plus(1)) ?: it.totalEpisodes,
                    totalEpisodes = it.totalEpisodes,
                    remainingSeconds = it.remainingSeconds,
                    totalSeconds = it.totalSeconds,
                    speed = show?.speed ?: 1f,
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowUiState())

    private suspend fun loadYearMarks(showId: Long): List<Pair<Int, Float>> {
        val total = playback.episodeCount(showId).takeIf { it > 0 } ?: return emptyList()
        return playback.yearMarks(showId).map { it.year to (it.orderIndex.toFloat() / total) }
    }

    /**
     * States the rewind before it happens. Smart resume silently moving the
     * position backwards is exactly the kind of thing that feels like a bug when
     * it is unexplained.
     */
    private suspend fun describeResume(target: EpisodeRow?): String {
        val saved = target?.positionSeconds ?: return ""
        if (saved <= 0) return ""
        val updatedAt = playback.position(target.id)?.updatedAt ?: return ""

        val awaySeconds = ((System.currentTimeMillis() - updatedAt) / 1000).coerceAtLeast(0)
        val tiers = container.settingsStore.current().resumeTiers
        val rewind = SmartResume.rewindSeconds(awaySeconds, tiers)
        if (rewind <= 0) return ""

        val from = SmartResume.resumePositionSeconds(saved, awaySeconds, tiers)
        return "SAVED ${ArchiveProgress.formatClock(saved)} → " +
            "FROM ${ArchiveProgress.formatClock(from)} · −${rewind}S"
    }

    /** Persists the show's speed and applies it to playback already in flight. */
    fun setSpeed(speed: Float) {
        val show = state.value.show ?: return
        player.setSpeed(speed)
        viewModelScope.launch { shows.updateShow(show.copy(speed = speed)) }
    }

    fun refreshNow() {
        container.workScheduler.refreshNow()
    }

    /** More flows than combine's typed arities allow, so they travel as one value. */
    private data class Core(
        val show: ShowEntity?,
        val resumeTarget: EpisodeRow?,
        val nowPlaying: EpisodeRow?,
        val upNext: List<EpisodeRow>,
        val progress: ShowProgress?,
    )

    companion object {
        private const val UP_NEXT_COUNT = 3
    }
}
