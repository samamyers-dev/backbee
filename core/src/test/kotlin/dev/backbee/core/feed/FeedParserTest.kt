package dev.backbee.core.feed

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FeedParserTest {

    @Test
    fun `parses channel metadata and items`() {
        val feed = FeedParser.parse(Fixtures.SIMPLE_FEED)

        assertThat(feed.title).isEqualTo("The Long Archive")
        assertThat(feed.artworkUrl).isEqualTo("https://cdn.example/art.jpg")
        assertThat(feed.items).hasSize(3)

        val first = feed.items.first()
        assertThat(first.guid).isEqualTo("ep-003")
        assertThat(first.title).isEqualTo("Episode Three")
        assertThat(first.enclosureUrl).isEqualTo("https://cdn.example/3.mp3")
        assertThat(first.enclosureBytes).isEqualTo(30_000_000L)
        assertThat(first.durationSeconds).isEqualTo(3661L)
        assertThat(first.episodeNumber).isEqualTo(3)
    }

    @Test
    fun `falls back to rss image url when itunes image is absent`() {
        val feed = FeedParser.parse(Fixtures.RSS_IMAGE_ONLY)
        assertThat(feed.artworkUrl).isEqualTo("https://cdn.example/rss-art.png")
    }

    @Test
    fun `guid falls back to enclosure url then to title and date`() {
        val feed = FeedParser.parse(Fixtures.MISSING_GUIDS)

        assertThat(feed.items[0].guid).isEqualTo("https://cdn.example/a.mp3")
        assertThat(feed.items[1].guid).startsWith("No Guid No Enclosure@")
        assertThat(feed.items.map { it.guid }.toSet()).hasSize(2)
    }

    @Test
    fun `reads atom next page link at channel level only`() {
        val feed = FeedParser.parse(Fixtures.PAGE_ONE)
        assertThat(feed.nextPageUrl).isEqualTo("https://example.com/feed?page=2")
    }

    @Test
    fun `tolerates missing duration and missing enclosure`() {
        val feed = FeedParser.parse(Fixtures.SPARSE_ITEM)
        val item = feed.items.single()

        assertThat(item.durationSeconds).isNull()
        assertThat(item.enclosureUrl).isNull()
        assertThat(item.title).isEqualTo("Bare Bones")
    }

    @Test
    fun `parses the three duration spellings publishers actually use`() {
        assertThat(FeedParser.parseDuration("01:01:01")).isEqualTo(3661L)
        assertThat(FeedParser.parseDuration("42:30")).isEqualTo(2550L)
        assertThat(FeedParser.parseDuration("3612")).isEqualTo(3612L)
        assertThat(FeedParser.parseDuration("3612.48")).isEqualTo(3612L)
        assertThat(FeedParser.parseDuration("")).isNull()
        assertThat(FeedParser.parseDuration("not a duration")).isNull()
        assertThat(FeedParser.parseDuration("1:2:3:4")).isNull()
    }

    @Test
    fun `parses the date formats found across a decade-long archive`() {
        val rfc1123 = FeedParser.parseDate("Tue, 03 Jun 2008 11:05:30 GMT")
        val iso = FeedParser.parseDate("2008-06-03T11:05:30Z")
        assertThat(rfc1123).isNotNull()
        assertThat(rfc1123).isEqualTo(iso)

        assertThat(FeedParser.parseDate("Tue, 3 Jun 2008 11:05:30 -0400")).isNotNull()
        assertThat(FeedParser.parseDate("garbage")).isNull()
        assertThat(FeedParser.parseDate("")).isNull()
    }

    @Test
    fun `entities and cdata survive parsing`() {
        val feed = FeedParser.parse(Fixtures.ENTITIES)
        assertThat(feed.items.single().title).isEqualTo("Q&A: \"the big one\"")
        assertThat(feed.items.single().description).contains("<p>Rich text</p>")
    }

    @Test
    fun `external entities are not resolved`() {
        // A feed that tries to pull in /etc/passwd must not parse into content.
        // Either the parser rejects the doctype outright or it yields nothing;
        // both are acceptable, silently inlining the file is not.
        val result = runCatching { FeedParser.parse(Fixtures.XXE_ATTEMPT) }
        val title = result.getOrNull()?.items?.singleOrNull()?.title
        assertThat(title == null || !title.contains("root:")).isTrue()
    }
}
