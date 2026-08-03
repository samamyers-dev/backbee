package dev.backbee.core.feed

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ArchiveFetcherTest {

    private fun fetcherOf(vararg pages: Pair<String, String>): FeedFetcher {
        val map = pages.toMap()
        return FeedFetcher { url -> map[url] ?: error("unexpected fetch of $url") }
    }

    @Test
    fun `follows next links across pages and dedupes overlapping items`() = runTest {
        val fetcher = fetcherOf(
            "https://example.com/feed" to Fixtures.PAGE_ONE,
            "https://example.com/feed?page=2" to Fixtures.PAGE_TWO,
            "https://example.com/feed?page=3" to Fixtures.PAGE_THREE,
        )

        val archive = ArchiveFetcher(fetcher).fetchArchive("https://example.com/feed")

        assertThat(archive.pagesFollowed).isEqualTo(3)
        assertThat(archive.episodesInFirstPage).isEqualTo(2)
        // p1a, p1b, p2a, p3a - p1b appears on two pages but only once here.
        assertThat(archive.episodes.map { it.guid }).containsExactly("p3a", "p2a", "p1b", "p1a").inOrder()
        assertThat(archive.stoppedAtPageLimit).isFalse()
    }

    @Test
    fun `resolves relative next links against the current page url`() = runTest {
        // PAGE_TWO's next link is the relative "?page=3"; resolving it against
        // the page-two URL is what keeps the walk on the right host and path.
        val fetcher = fetcherOf(
            "https://example.com/feed" to Fixtures.PAGE_ONE,
            "https://example.com/feed?page=2" to Fixtures.PAGE_TWO,
            "https://example.com/feed?page=3" to Fixtures.PAGE_THREE,
        )

        val archive = ArchiveFetcher(fetcher).fetchArchive("https://example.com/feed")
        assertThat(archive.episodes.map { it.guid }).contains("p3a")
    }

    @Test
    fun `stops instead of looping when next points back at a page already read`() = runTest {
        val fetcher = fetcherOf("https://example.com/feed" to Fixtures.PAGE_LOOPING)

        val archive = ArchiveFetcher(fetcher).fetchArchive("https://example.com/feed")

        assertThat(archive.pagesFollowed).isEqualTo(1)
        assertThat(archive.episodes).hasSize(1)
    }

    @Test
    fun `reports when the page limit cut the walk short`() = runTest {
        // Every page advertises another one, so only maxPages stops us.
        val fetcher = FeedFetcher { url ->
            val page = url.substringAfter("page=", "1").toInt()
            Fixtures.generated(count = 2, nextPage = "https://example.com/feed?page=${page + 1}", firstNumber = page * 10)
        }

        val archive = ArchiveFetcher(fetcher, maxPages = 3).fetchArchive("https://example.com/feed?page=1")

        assertThat(archive.pagesFollowed).isEqualTo(3)
        assertThat(archive.stoppedAtPageLimit).isTrue()
    }

    @Test
    fun `orders oldest first which is the archive playback order`() = runTest {
        val fetcher = fetcherOf("https://example.com/feed" to Fixtures.SIMPLE_FEED)

        val archive = ArchiveFetcher(fetcher).fetchArchive("https://example.com/feed")

        assertThat(archive.episodes.map { it.title })
            .containsExactly("Episode One", "Episode Two", "Episode Three").inOrder()
    }

    @Test
    fun `undated items keep their implied position rather than piling up at one end`() {
        val dated = { guid: String, millis: Long? ->
            ParsedEpisode(guid, guid, millis, null, null, null, null, null, null)
        }
        // Document order is newest-first: c (undated), b, a.
        val ordered = ArchiveFetcher.orderOldestFirst(
            listOf(dated("c", null), dated("b", 2000), dated("a", 1000))
        )

        assertThat(ordered.map { it.guid }).containsExactly("a", "b", "c").inOrder()
    }
}
