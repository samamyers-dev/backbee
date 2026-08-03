package dev.backbee.core.playback

/**
 * One rewind rule: "if you were away for at most [pauseAtMostSeconds], rewind [rewindSeconds]".
 * The last tier in a list should use [Long.MAX_VALUE] as its bound to catch everything else.
 */
data class ResumeTier(val pauseAtMostSeconds: Long, val rewindSeconds: Int)

/**
 * Picking up where you left off, minus enough to re-establish context.
 *
 * A ten-second gap needs no rewind; coming back to a documentary a week later
 * needs a minute to remember who is talking. The tiers make that scale.
 */
object SmartResume {

    val DEFAULT_TIERS: List<ResumeTier> = listOf(
        ResumeTier(pauseAtMostSeconds = 10 * 60, rewindSeconds = 0),
        ResumeTier(pauseAtMostSeconds = 60 * 60, rewindSeconds = 10),
        ResumeTier(pauseAtMostSeconds = 24 * 60 * 60, rewindSeconds = 30),
        ResumeTier(pauseAtMostSeconds = Long.MAX_VALUE, rewindSeconds = 60),
    )

    fun rewindSeconds(pausedForSeconds: Long, tiers: List<ResumeTier> = DEFAULT_TIERS): Int {
        if (tiers.isEmpty()) return 0
        val elapsed = pausedForSeconds.coerceAtLeast(0)
        val ordered = tiers.sortedBy { it.pauseAtMostSeconds }
        return ordered.firstOrNull { elapsed <= it.pauseAtMostSeconds }?.rewindSeconds
            ?: ordered.last().rewindSeconds
    }

    /**
     * Where playback should actually start. Never returns a negative position and
     * never rewinds past the beginning of the episode.
     */
    fun resumePositionSeconds(
        savedPositionSeconds: Long,
        pausedForSeconds: Long,
        tiers: List<ResumeTier> = DEFAULT_TIERS,
    ): Long {
        val saved = savedPositionSeconds.coerceAtLeast(0)
        return (saved - rewindSeconds(pausedForSeconds, tiers)).coerceAtLeast(0)
    }

    /**
     * Serialised form for settings storage: `600:0,3600:10,86400:30,*:60`.
     * `*` stands in for the catch-all bound so the stored value stays readable.
     */
    fun encodeTiers(tiers: List<ResumeTier>): String = tiers.joinToString(",") { tier ->
        val bound = if (tier.pauseAtMostSeconds == Long.MAX_VALUE) "*" else tier.pauseAtMostSeconds.toString()
        "$bound:${tier.rewindSeconds}"
    }

    /** Returns [DEFAULT_TIERS] rather than throwing if the stored value is unusable. */
    fun decodeTiers(encoded: String?): List<ResumeTier> {
        if (encoded.isNullOrBlank()) return DEFAULT_TIERS
        val parsed = encoded.split(',').mapNotNull { entry ->
            val (boundRaw, rewindRaw) = entry.trim().split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            val bound = if (boundRaw == "*") Long.MAX_VALUE else boundRaw.toLongOrNull() ?: return@mapNotNull null
            val rewind = rewindRaw.toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
            ResumeTier(bound, rewind)
        }
        return parsed.takeIf { it.isNotEmpty() }?.sortedBy { it.pauseAtMostSeconds } ?: DEFAULT_TIERS
    }
}
