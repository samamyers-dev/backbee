package dev.backbee.core.download

/**
 * What the planner needs to know about one episode. Deliberately a flat snapshot
 * so the planner is a pure function of state and can be tested exhaustively.
 */
data class EpisodeSlot(
    val episodeId: Long,
    /** Position in the archive, oldest-first. */
    val orderIndex: Int,
    val played: Boolean,
    /** Epoch millis the episode was marked played, if it was. */
    val playedAtMillis: Long? = null,
    val downloaded: Boolean = false,
    /** On-disk size when downloaded, else the enclosure's advertised size, else an estimate. */
    val bytes: Long = 0,
    val hasEnclosure: Boolean = true,
    /** Exempt from the played-cleanup sweep and from cap eviction. */
    val keepAfterPlaying: Boolean = false,
)

data class DownloadConfig(
    /** Keep this many unplayed episodes ahead of the current one on disk. */
    val downloadAhead: Int = 10,
    /** Played episodes are deleted this long after being finished. */
    val deletePlayedAfterMillis: Long = 24 * 60 * 60 * 1000L,
    /** Hard ceiling on bytes held by episode audio. */
    val storageCapBytes: Long = 8L * 1024 * 1024 * 1024,
)

data class DownloadPlan(
    /** Episode ids to fetch, nearest-first. Order is the download queue order. */
    val toDownload: List<Long>,
    /** Episode ids whose audio should be removed from disk. */
    val toDelete: List<Long>,
    /** Human-readable justification per episode id, for the debug screen and logs. */
    val reasons: Map<Long, String>,
    /** Bytes that will be held once this plan is carried out. */
    val projectedBytes: Long,
    /** True when the storage cap stopped us short of [DownloadConfig.downloadAhead]. */
    val capLimited: Boolean,
)

/**
 * Decides what to fetch and what to throw away.
 *
 * The rule the user experiences is "the next N episodes are always ready and I
 * never think about storage". Everything here serves that: fill forward from the
 * current position, drop what is behind, and when the cap bites, give up the
 * furthest-ahead episode rather than the one about to play.
 */
class DownloadPlanner(private val config: DownloadConfig) {

    fun plan(
        episodes: List<EpisodeSlot>,
        currentOrderIndex: Int,
        nowMillis: Long,
    ): DownloadPlan {
        val ordered = episodes.sortedBy { it.orderIndex }
        val reasons = mutableMapOf<Long, String>()
        val toDelete = linkedSetOf<Long>()

        // The window is the current episode plus the next N unplayed ones. Played
        // episodes inside the range do not consume a slot: skipping five you have
        // already heard should not shrink how far ahead you are buffered.
        val window = ordered
            .filter { it.orderIndex >= currentOrderIndex && !it.played && it.hasEnclosure }
            .take(config.downloadAhead + 1)
        val windowIds = window.map { it.episodeId }.toSet()

        // Decide what to reclaim. Played and unplayed are separate cases: a
        // just-finished episode is deliberately kept for the delete-after window
        // so a mis-tap or a "wait, what did they say" can still get back to it,
        // even though it now sits behind the current position.
        for (slot in ordered) {
            if (!slot.downloaded || slot.episodeId in toDelete) continue

            // An episode explicitly marked "keep" is never reclaimed, however
            // long ago it was played and however tight the cap gets. That is the
            // entire point of the flag.
            if (slot.keepAfterPlaying) continue

            if (slot.played) {
                val playedAt = slot.playedAtMillis
                if (playedAt == null || nowMillis - playedAt >= config.deletePlayedAfterMillis) {
                    toDelete += slot.episodeId
                    reasons[slot.episodeId] = "played, past the ${config.deletePlayedAfterMillis / 3_600_000}h keep window"
                }
            } else if (slot.episodeId !in windowIds) {
                toDelete += slot.episodeId
                reasons[slot.episodeId] = if (slot.orderIndex < currentOrderIndex) {
                    "behind the current position"
                } else {
                    "beyond the ${config.downloadAhead}-episode look-ahead"
                }
            }
        }

        // Bytes we will still be holding: everything downloaded and kept.
        var heldBytes = ordered
            .filter { it.downloaded && it.episodeId !in toDelete }
            .sumOf { it.bytes }

        // 3. Fill the window, nearest-first, until the cap says stop.
        val toDownload = mutableListOf<Long>()
        var capLimited = false
        for (slot in window) {
            if (slot.downloaded && slot.episodeId !in toDelete) continue
            val size = slot.bytes
            if (heldBytes + size > config.storageCapBytes) {
                // Try to make room by dropping already-downloaded episodes that
                // sit further ahead than this one. Never evict to make room for
                // something more distant than what we would be evicting.
                val reclaimable = ordered
                    .filter {
                        it.downloaded && !it.keepAfterPlaying &&
                            it.episodeId !in toDelete && it.orderIndex > slot.orderIndex
                    }
                    .sortedByDescending { it.orderIndex }
                for (victim in reclaimable) {
                    if (heldBytes + size <= config.storageCapBytes) break
                    toDelete += victim.episodeId
                    reasons[victim.episodeId] = "evicted for the storage cap"
                    heldBytes -= victim.bytes
                }
                if (heldBytes + size > config.storageCapBytes) {
                    capLimited = true
                    break
                }
            }
            toDownload += slot.episodeId
            reasons[slot.episodeId] = "look-ahead slot ${slot.orderIndex - currentOrderIndex}"
            heldBytes += size
        }

        return DownloadPlan(
            toDownload = toDownload,
            toDelete = toDelete.toList(),
            reasons = reasons,
            projectedBytes = heldBytes,
            capLimited = capLimited,
        )
    }
}
