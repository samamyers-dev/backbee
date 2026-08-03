package dev.backbee.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/**
 * Makes the skip increments settable at runtime.
 *
 * ExoPlayer fixes `seekForwardIncrementMs` at construction, but the skip amounts
 * are user settings and the headset, steering-wheel and notification buttons all
 * route through `seekForward()`/`seekBack()`. Wrapping the player is the only
 * place a change can take effect everywhere at once.
 */
class SkipForwardingPlayer(
    player: Player,
    @Volatile var forwardMs: Long = 30_000,
    @Volatile var backMs: Long = 10_000,
) : ForwardingPlayer(player) {

    override fun getSeekForwardIncrement(): Long = forwardMs

    override fun getSeekBackIncrement(): Long = backMs

    override fun seekForward() {
        seekRelative(forwardMs)
    }

    override fun seekBack() {
        seekRelative(-backMs)
    }

    private fun seekRelative(deltaMs: Long) {
        val episodeLength = duration
        val target = currentPosition + deltaMs
        val clamped = when {
            target < 0 -> 0
            episodeLength > 0 && target > episodeLength -> episodeLength
            else -> target
        }
        seekTo(clamped)
    }
}
