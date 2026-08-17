package dev.backbee.core.playback

/**
 * The playback speeds worth having on a one-tap key.
 *
 * A single key that cycles beats a slider or a dialog for something adjusted
 * mid-drive: every press is a known, bounded change, and the wrap back to 1x
 * means no state to remember. Shared by every screen with a speed key so they
 * cannot drift apart.
 */
object PlaybackSpeeds {

    val STEPS = listOf(1.0f, 1.2f, 1.4f, 1.6f, 1.8f, 2.0f)

    /**
     * The next step up from [current], wrapping past the top back to the first.
     * A speed between steps (set elsewhere) snaps up to the next real step.
     */
    fun next(current: Float): Float {
        val index = STEPS.indexOfFirst { it > current + TOLERANCE }
        return if (index == -1) STEPS.first() else STEPS[index]
    }

    /** Floats arrive back from the player slightly off; treat near-equal as equal. */
    private const val TOLERANCE = 0.01f
}
