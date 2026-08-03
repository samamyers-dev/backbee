package dev.backbee.core.feed

import java.net.URI

/** Anything that can turn a feed URL into XML. Kept as an interface so the core module stays free of HTTP. */
fun interface FeedFetcher {
    suspend fun fetch(url: String): String
}

data class PagedArchive(
    /** The first page's channel-level metadata. Later pages repeat it and are ignored. */
    val head: ParsedFeed,
    /** Deduplicated by guid, ordered oldest-first. */
    val episodes: List<ParsedEpisode>,
    val episodesInFirstPage: Int,
    val pagesFollowed: Int,
    /** True when we stopped because of [ArchiveFetcher.maxPages], not because the feed ran out. */
    val stoppedAtPageLimit: Boolean,
)

/**
 * Walks a feed and every `rel="next"` page behind it.
 *
 * The whole point of the app is playing a 1,200-episode backlog in order, and a
 * large share of feeds only put the most recent ~300 items on page one. Paging
 * is therefore the normal path, not an edge case.
 */
class ArchiveFetcher(
    private val fetcher: FeedFetcher,
    private val maxPages: Int = 50,
) {
    suspend fun fetchArchive(feedUrl: String): PagedArchive {
        var currentUrl: String? = feedUrl
        var head: ParsedFeed? = null
        var firstPageCount = 0
        var pages = 0
        val seenGuids = mutableSetOf<String>()
        val seenUrls = mutableSetOf<String>()
        val collected = mutableListOf<ParsedEpisode>()

        while (currentUrl != null && pages < maxPages) {
            // A feed that points its "next" back at a page we already read would
            // otherwise spin until maxPages; stop the moment we recognise a URL.
            if (!seenUrls.add(currentUrl)) break

            val page = FeedParser.parse(fetcher.fetch(currentUrl))
            pages++
            if (head == null) {
                head = page
                firstPageCount = page.items.size
            }
            for (item in page.items) {
                if (seenGuids.add(item.guid)) collected += item
            }

            val next = page.nextPageUrl ?: break
            currentUrl = resolve(currentUrl, next)
        }

        val resolvedHead = head ?: ParsedFeed(null, null, null, null, null, null, emptyList())
        val stoppedAtLimit = pages >= maxPages && currentUrl != null

        return PagedArchive(
            head = resolvedHead,
            episodes = orderOldestFirst(collected),
            episodesInFirstPage = firstPageCount,
            pagesFollowed = pages,
            stoppedAtPageLimit = stoppedAtLimit,
        )
    }

    private fun resolve(base: String, href: String): String? {
        val ref = href.trim()
        if (ref.isEmpty()) return null
        return runCatching {
            if (ref.startsWith("?")) {
                // java.net.URI predates RFC 3986 and resolves a query-only
                // reference by discarding the base path, turning
                // "…/feed?page=2" + "?page=3" into "…/?page=3". Paged feeds use
                // exactly that form, so handle it directly.
                base.substringBefore('#').substringBefore('?') + ref
            } else {
                URI(base).resolve(ref).toString()
            }
        }.getOrElse { ref.takeIf { it.startsWith("http") } }
    }

    companion object {
        /**
         * Feeds are published newest-first. Sorting by pubDate ascending gives the
         * archive order; items with no usable date keep their document order
         * reversed, which puts them where the publisher implied they belong
         * instead of dumping them all at one end.
         */
        fun orderOldestFirst(items: List<ParsedEpisode>): List<ParsedEpisode> {
            val documentOrder = items.withIndex().associate { (i, item) -> item.guid to i }
            return items.sortedWith(
                compareBy<ParsedEpisode> { it.pubDateMillis ?: Long.MAX_VALUE }
                    .thenByDescending { documentOrder[it.guid] ?: 0 }
            )
        }
    }
}
