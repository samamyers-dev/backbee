package dev.backbee.core.playback

/**
 * "3 DAYS AGO", "8 MONTHS AGO".
 *
 * The shelf and the resume screen are about *how long it has been*, not about
 * dates. Someone who paused a show eight months ago does not need 14 March; they
 * need to feel the gap.
 */
object RelativeTime {

    private const val MINUTE = 60L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR
    private const val WEEK = 7 * DAY
    private const val MONTH = 30 * DAY
    private const val YEAR = 365 * DAY

    /** Coarse, uppercase, and never more precise than it can justify. */
    fun ago(millisAgo: Long): String {
        val seconds = (millisAgo / 1000).coerceAtLeast(0)
        return when {
            seconds < MINUTE -> "JUST NOW"
            seconds < HOUR -> plural(seconds / MINUTE, "MINUTE")
            seconds < DAY -> plural(seconds / HOUR, "HOUR")
            seconds < WEEK -> plural(seconds / DAY, "DAY")
            seconds < MONTH -> plural(seconds / WEEK, "WEEK")
            seconds < YEAR -> plural(seconds / MONTH, "MONTH")
            else -> plural(seconds / YEAR, "YEAR")
        }
    }

    /** Convenience for a stored timestamp rather than an elapsed duration. */
    fun since(timestampMillis: Long, nowMillis: Long): String = ago(nowMillis - timestampMillis)

    private fun plural(count: Long, unit: String): String {
        val n = count.coerceAtLeast(1)
        return if (n == 1L) "1 $unit AGO" else "$n ${unit}S AGO"
    }
}
