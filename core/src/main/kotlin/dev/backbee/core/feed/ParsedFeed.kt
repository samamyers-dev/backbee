package dev.backbee.core.feed

/**
 * A feed document as parsed from a single HTTP response.
 *
 * [nextPageUrl] carries `<atom:link rel="next">` when the publisher paginates.
 * Following it is the difference between seeing 300 episodes and seeing 1,247,
 * so it is a first-class field rather than something the caller has to dig for.
 */
data class ParsedFeed(
    val title: String?,
    val description: String?,
    val artworkUrl: String?,
    val link: String?,
    val nextPageUrl: String?,
    val lastBuildDate: String?,
    val items: List<ParsedEpisode>,
)

data class ParsedEpisode(
    /** `<guid>`, falling back to the enclosure URL, falling back to title+date. Never blank. */
    val guid: String,
    val title: String,
    /** Epoch millis. Null when the publisher omitted or mangled `<pubDate>`. */
    val pubDateMillis: Long?,
    val durationSeconds: Long?,
    val enclosureUrl: String?,
    val enclosureBytes: Long?,
    val description: String?,
    val episodeNumber: Int?,
    val seasonNumber: Int?,
)
