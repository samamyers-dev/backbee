package dev.backbee.playback

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import dev.backbee.data.repo.PlaybackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Position durability.
 *
 * Losing playback position is the one unforgivable failure, so this errs hard
 * towards writing too often. A flush is a single-row UPDATE of three columns; it
 * is cheaper than the risk of skipping one.
 *
 * The moment that actually eats positions in podcast apps is shutting off the
 * car: Bluetooth drops, the process is backgrounded, and whatever was only in
 * memory is gone. That is why route changes are a mandatory flush point rather
 * than something we hope the pause callback covers.
 */
class PositionWriter(
    /** Drives the five-second ticker; lives and dies with the service. */
    private val scope: CoroutineScope,
    private val repository: PlaybackRepository,
    /**
     * Runs the database writes. Defaults to [scope], but the service passes the
     * application scope so the final flush in onDestroy cannot be cancelled by
     * the service's own teardown a few lines later - which is the one write
     * that matters most when the OS kills a paused service.
     */
    private val writeScope: CoroutineScope = scope,
    private val onEpisodeFinished: suspend (episodeId: Long) -> Unit,
    private val onFlushed: () -> Unit = {},
    /**
     * Called whenever the player's current item changes. The download planner
     * reads this to know which file it must not reclaim, so it has to be written
     * down rather than merely known in memory.
     */
    private val onLoadedEpisodeChanged: suspend (episodeId: Long?) -> Unit = {},
) : Player.Listener {

    private var player: Player? = null
    private var ticker: Job? = null

    fun attach(player: Player) {
        this.player = player
        player.addListener(this)
        recordLoadedEpisode()
    }

    fun detach() {
        ticker?.cancel()
        ticker = null
        player?.removeListener(this)
        player = null
    }

    // -- Player.Listener ----------------------------------------------------

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            startTicker()
        } else {
            stopTicker()
            // Covers pause, audio-focus loss, and buffering stalls alike.
            flush("playback stopped")
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_ENDED -> {
                val player = player ?: return
                val episodeId = MediaItems.episodeIdOf(player.currentMediaItem) ?: return
                val duration = (player.duration.takeIf { it > 0 } ?: 0L) / 1000
                finish(episodeId, duration)
            }

            Player.STATE_READY -> recordMeasuredDuration()
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
            // The episode we just left ran to its end - that is auto-advance, and
            // the outgoing episode is finished.
            val episodeId = MediaItems.episodeIdOf(oldPosition.mediaItem)
            if (episodeId != null) finish(episodeId, oldPosition.positionMs / 1000)
        } else {
            val outgoing = oldPosition.mediaItem
            val incoming = newPosition.mediaItem
            if (outgoing != null && outgoing.mediaId != incoming?.mediaId) {
                // A manual switch: Next, a tap in the archive, mark-and-next.
                // The player already holds the incoming episode, so a plain
                // flush would record the wrong one. Save the outgoing episode
                // where it was left; the incoming one gets its first tick soon.
                MediaItems.episodeIdOf(outgoing)?.let { id ->
                    save(id, oldPosition.positionMs.coerceAtLeast(0) / 1000, "switched away")
                }
            } else {
                // A seek. Persist immediately so a crash right after does not undo it.
                flush("seek")
            }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        recordMeasuredDuration()
        recordLoadedEpisode()
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        Log.w(TAG, "Player error; flushing position before anything else", error)
        flush("player error")
    }

    // -- Flushing -----------------------------------------------------------

    /**
     * Write the current position now. Safe to call from anywhere, including
     * broadcast receivers and service teardown.
     */
    fun flush(reason: String) {
        val player = player ?: return
        val episodeId = MediaItems.episodeIdOf(player.currentMediaItem) ?: return
        save(episodeId, player.currentPosition.coerceAtLeast(0) / 1000, reason)
    }

    private fun save(episodeId: Long, positionSeconds: Long, reason: String) {
        writeScope.launch {
            runCatching { repository.savePosition(episodeId, positionSeconds) }
                .onSuccess { onFlushed() }
                .onFailure { Log.e(TAG, "Failed to persist position for $episodeId", it) }
        }
        Log.d(TAG, "save($reason) episode=$episodeId at=${positionSeconds}s")
    }

    private fun finish(episodeId: Long, positionSeconds: Long) {
        writeScope.launch {
            runCatching {
                repository.markPlayed(episodeId, positionSeconds)
                onEpisodeFinished(episodeId)
            }.onFailure { Log.e(TAG, "Failed to mark $episodeId played", it) }
        }
    }

    private fun recordLoadedEpisode() {
        val episodeId = player?.currentMediaItem?.let { MediaItems.episodeIdOf(it) }
        writeScope.launch {
            runCatching { onLoadedEpisodeChanged(episodeId) }
                .onFailure { Log.w(TAG, "Failed to record the loaded episode", it) }
        }
    }

    private fun recordMeasuredDuration() {
        val player = player ?: return
        val episodeId = MediaItems.episodeIdOf(player.currentMediaItem) ?: return
        val duration = player.duration
        if (duration <= 0) return
        // Feeds often omit <itunes:duration>. Once the decoder knows the real
        // length, keep it so "hours left" stops undercounting.
        writeScope.launch {
            runCatching { repository.recordMeasuredDuration(episodeId, duration / 1000) }
        }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flush("tick")
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    companion object {
        private const val TAG = "PositionWriter"
        private const val FLUSH_INTERVAL_MS = 5_000L
    }
}
