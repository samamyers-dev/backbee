package dev.backbee.core.stats

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The numbers behind the completion screen. Finishing a 1,200-episode archive is
 * a year of someone's listening; the recap should be able to say so concretely.
 */
data class CompletionStats(
    val showTitle: String,
    val episodeCount: Int,
    /** Audio consumed at 1x, in seconds. */
    val listenedSeconds: Long,
    /** When the first episode of this show was played. */
    val firstPlayedAtMillis: Long?,
    /** When the last one was. */
    val lastPlayedAtMillis: Long?,
    val starredCount: Int,
    val averageSpeed: Float,
) {
    /** Hours of audio, at 1x. What was listened to, not how long it took. */
    val listenedHours: Double get() = listenedSeconds / 3600.0

    /** Hours actually spent, accounting for speed. */
    val wallClockHours: Double
        get() = if (averageSpeed <= 0f) listenedHours else listenedSeconds / averageSpeed.toDouble() / 3600.0

    val elapsedDays: Long?
        get() {
            val start = firstPlayedAtMillis ?: return null
            val end = lastPlayedAtMillis ?: return null
            return Duration.ofMillis((end - start).coerceAtLeast(0)).toDays().coerceAtLeast(1)
        }

    /** Episodes per week over the run. Null when there is no usable date range. */
    val episodesPerWeek: Double?
        get() = elapsedDays?.let { days -> episodeCount * 7.0 / days }

    fun dateRange(zone: ZoneId = ZoneId.systemDefault()): String? {
        val start = firstPlayedAtMillis ?: return null
        val end = lastPlayedAtMillis ?: return null
        val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US).withZone(zone)
        return "${fmt.format(Instant.ofEpochMilli(start))} – ${fmt.format(Instant.ofEpochMilli(end))}"
    }

    fun paceSummary(): String {
        val perWeek = episodesPerWeek ?: return "$episodeCount episodes"
        return String.format(Locale.US, "%,d episodes · %.1f per week", episodeCount, perWeek)
    }

    fun hoursSummary(): String =
        String.format(Locale.US, "%,d hours of audio in %,d hours of listening", listenedHours.roundToInt(), wallClockHours.roundToInt())
}
