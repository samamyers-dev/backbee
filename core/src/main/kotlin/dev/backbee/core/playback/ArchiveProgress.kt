package dev.backbee.core.playback

import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The one-line status on the Now screen:
 * `Ep 312 of 1,247 · 25% · ~340 hrs left at 1.6×`
 *
 * The percentage counts episodes, matching the two numbers next to it, so the
 * line never reads as though it contradicts itself. [percentByTime] is available
 * separately for anywhere a duration-weighted figure is more honest.
 */
data class ArchiveProgress(
    /** 1-based position of the active episode within the archive. */
    val currentPosition: Int,
    val totalEpisodes: Int,
    /** Unplayed audio still ahead, at 1x, in seconds. */
    val remainingSeconds: Long,
    /** Total audio in the archive at 1x, in seconds. Zero when unknown. */
    val totalSeconds: Long = 0,
    val speed: Float = 1.0f,
) {
    val completedEpisodes: Int get() = (currentPosition - 1).coerceIn(0, totalEpisodes)

    val percentByEpisodes: Int
        get() = if (totalEpisodes <= 0) 0 else (completedEpisodes * 100.0 / totalEpisodes).roundToInt().coerceIn(0, 100)

    val percentByTime: Int
        get() = if (totalSeconds <= 0) percentByEpisodes
        else (((totalSeconds - remainingSeconds).coerceAtLeast(0) * 100.0) / totalSeconds).roundToInt().coerceIn(0, 100)

    /** Wall-clock hours left, accounting for playback speed. */
    val remainingHoursAtSpeed: Double
        get() {
            val effectiveSpeed = if (speed <= 0f) 1.0 else speed.toDouble()
            return remainingSeconds.coerceAtLeast(0) / effectiveSpeed / 3600.0
        }

    fun summary(): String = buildString {
        append("Ep ").append(group(currentPosition))
        append(" of ").append(group(totalEpisodes))
        append(" · ").append(percentByEpisodes).append('%')
        if (remainingSeconds > 0) {
            append(" · ~").append(group(remainingHoursAtSpeed.roundToLong()))
            append(" hrs left at ").append(formatSpeed(speed)).append('×')
        }
    }

    companion object {
        private fun group(value: Number): String = String.format(Locale.US, "%,d", value.toLong())

        /** `1.6×`, `2×`, `1.25×` - trailing zeros dropped so the strip stays short. */
        fun formatSpeed(speed: Float): String {
            val rounded = (speed * 100).roundToInt() / 100.0
            return if (rounded % 1.0 == 0.0) rounded.toInt().toString()
            else rounded.toString().trimEnd('0').trimEnd('.')
        }

        /** `4h 12m`, `12m 30s`, `48s` - used in episode rows and the completion screen. */
        fun formatDuration(seconds: Long): String {
            val total = seconds.coerceAtLeast(0)
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return when {
                h > 0 -> "${h}h ${m}m"
                m > 0 -> "${m}m ${s}s"
                else -> "${s}s"
            }
        }

        /** `-12:34` style remaining-time readout for the player. */
        fun formatClock(seconds: Long): String {
            val total = seconds.coerceAtLeast(0)
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
            else String.format(Locale.US, "%d:%02d", m, s)
        }
    }
}
