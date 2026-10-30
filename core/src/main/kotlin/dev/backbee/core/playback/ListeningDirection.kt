package dev.backbee.core.playback

/**
 * Which end of an archive a show is listened from.
 *
 * The archive is always stored oldest-first: `order_index` is the spine and is
 * never renumbered when this changes. Direction only decides which way the queue
 * walks along that spine, so flipping it cannot move a bookmark or lose a played
 * flag - it just changes which unplayed episode counts as "next".
 */
enum class ListeningDirection {
    OLDEST_FIRST,
    NEWEST_FIRST;

    val isNewestFirst: Boolean get() = this == NEWEST_FIRST

    val label: String get() = if (isNewestFirst) "Newest first" else "Oldest first"

    /**
     * A synthetic order index just before the first episode in listening order,
     * for queries that need "from the top of the queue".
     */
    val startEdge: Int get() = if (isNewestFirst) Int.MAX_VALUE else -1
}
