package dev.backbee.core.feed

/** Hand-written feed snippets covering the shapes real archives throw at the parser. */
object Fixtures {

    val SIMPLE_FEED = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"
             xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"
             xmlns:atom="http://www.w3.org/2005/Atom">
          <channel>
            <title>The Long Archive</title>
            <description>A very long show.</description>
            <link>https://example.com</link>
            <itunes:image href="https://cdn.example/art.jpg"/>
            <item>
              <title>Episode Three</title>
              <guid isPermaLink="false">ep-003</guid>
              <pubDate>Wed, 03 Jan 2024 10:00:00 GMT</pubDate>
              <itunes:duration>01:01:01</itunes:duration>
              <itunes:episode>3</itunes:episode>
              <enclosure url="https://cdn.example/3.mp3" length="30000000" type="audio/mpeg"/>
            </item>
            <item>
              <title>Episode Two</title>
              <guid isPermaLink="false">ep-002</guid>
              <pubDate>Tue, 02 Jan 2024 10:00:00 GMT</pubDate>
              <itunes:duration>42:30</itunes:duration>
              <itunes:episode>2</itunes:episode>
              <enclosure url="https://cdn.example/2.mp3" length="20000000" type="audio/mpeg"/>
            </item>
            <item>
              <title>Episode One</title>
              <guid isPermaLink="false">ep-001</guid>
              <pubDate>Mon, 01 Jan 2024 10:00:00 GMT</pubDate>
              <itunes:duration>1800</itunes:duration>
              <itunes:episode>1</itunes:episode>
              <enclosure url="https://cdn.example/1.mp3" length="10000000" type="audio/mpeg"/>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    val RSS_IMAGE_ONLY = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <title>No iTunes Art</title>
            <image>
              <url>https://cdn.example/rss-art.png</url>
              <title>No iTunes Art</title>
            </image>
            <item><title>Only</title><guid>x</guid></item>
          </channel>
        </rss>
    """.trimIndent()

    val MISSING_GUIDS = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <title>Sloppy</title>
            <item>
              <title>No Guid</title>
              <pubDate>Mon, 01 Jan 2024 10:00:00 GMT</pubDate>
              <enclosure url="https://cdn.example/a.mp3" length="1" type="audio/mpeg"/>
            </item>
            <item>
              <title>No Guid No Enclosure</title>
              <pubDate>Tue, 02 Jan 2024 10:00:00 GMT</pubDate>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    val PAGE_ONE = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
          <channel>
            <title>Paged Show</title>
            <atom:link rel="self" href="https://example.com/feed"/>
            <atom:link rel="next" href="https://example.com/feed?page=2"/>
            <item><title>P1 A</title><guid>p1a</guid>
              <pubDate>Mon, 08 Jan 2024 10:00:00 GMT</pubDate>
              <enclosure url="https://cdn.example/p1a.mp3" length="1" type="audio/mpeg"/></item>
            <item><title>P1 B</title><guid>p1b</guid>
              <pubDate>Sun, 07 Jan 2024 10:00:00 GMT</pubDate>
              <enclosure url="https://cdn.example/p1b.mp3" length="1" type="audio/mpeg"/></item>
          </channel>
        </rss>
    """.trimIndent()

    val PAGE_TWO = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
          <channel>
            <title>Paged Show</title>
            <atom:link rel="next" href="?page=3"/>
            <item><title>P2 A</title><guid>p2a</guid>
              <pubDate>Sat, 06 Jan 2024 10:00:00 GMT</pubDate>
              <enclosure url="https://cdn.example/p2a.mp3" length="1" type="audio/mpeg"/></item>
            <item><title>P1 B duplicate</title><guid>p1b</guid>
              <pubDate>Sun, 07 Jan 2024 10:00:00 GMT</pubDate>
              <enclosure url="https://cdn.example/p1b.mp3" length="1" type="audio/mpeg"/></item>
          </channel>
        </rss>
    """.trimIndent()

    val PAGE_THREE = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
          <channel>
            <title>Paged Show</title>
            <item><title>P3 A</title><guid>p3a</guid>
              <pubDate>Fri, 05 Jan 2024 10:00:00 GMT</pubDate>
              <enclosure url="https://cdn.example/p3a.mp3" length="1" type="audio/mpeg"/></item>
          </channel>
        </rss>
    """.trimIndent()

    /** Page whose "next" points back at page one - the loop the fetcher must not fall into. */
    val PAGE_LOOPING = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
          <channel>
            <title>Looping Show</title>
            <atom:link rel="next" href="https://example.com/feed"/>
            <item><title>L A</title><guid>la</guid>
              <pubDate>Mon, 08 Jan 2024 10:00:00 GMT</pubDate>
              <enclosure url="https://cdn.example/la.mp3" length="1" type="audio/mpeg"/></item>
          </channel>
        </rss>
    """.trimIndent()

    val SPARSE_ITEM = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <title>Sparse</title>
            <item><title>Bare Bones</title><guid>bare</guid></item>
          </channel>
        </rss>
    """.trimIndent()

    val ENTITIES = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
          <channel>
            <title>Entities</title>
            <item>
              <title>Q&amp;A: &quot;the big one&quot;</title>
              <guid>ent</guid>
              <content:encoded><![CDATA[<p>Rich text</p>]]></content:encoded>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    val XXE_ATTEMPT = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE rss [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
        <rss version="2.0">
          <channel>
            <title>Hostile</title>
            <item><title>&xxe;</title><guid>x</guid></item>
          </channel>
        </rss>
    """.trimIndent()

    /**
     * A feed with [count] items, newest-first, episode-numbered 1..count.
     * [firstNumber] lets a test simulate a window that starts partway in, and
     * [skip] punches holes for the gap-detection tests.
     */
    fun generated(
        count: Int,
        numbered: Boolean = true,
        nextPage: String? = null,
        firstNumber: Int = 1,
        skip: Set<Int> = emptySet(),
    ): String {
        val numbers = (firstNumber until firstNumber + count).filterNot { it in skip }
        val items = numbers.reversed().joinToString("\n") { n ->
            val episodeTag = if (numbered) "<itunes:episode>$n</itunes:episode>" else ""
            "<item>" +
                "<title>Episode $n</title>" +
                "<guid>gen-$n</guid>" +
                "<pubDate>${rfc(n)}</pubDate>" +
                "<itunes:duration>1800</itunes:duration>" +
                episodeTag +
                "<enclosure url=\"https://cdn.example/$n.mp3\" length=\"10000000\" type=\"audio/mpeg\"/>" +
                "</item>"
        }
        val next = nextPage?.let { "<atom:link rel=\"next\" href=\"$it\"/>" }.orEmpty()
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<rss version=\"2.0\" " +
            "xmlns:itunes=\"http://www.itunes.com/dtds/podcast-1.0.dtd\" " +
            "xmlns:atom=\"http://www.w3.org/2005/Atom\">" +
            "<channel><title>Generated Show</title>$next$items</channel></rss>"
    }

    /** Monotonically increasing pubDates: episode n is n days after the epoch date. */
    private fun rfc(n: Int): String {
        val base = java.time.ZonedDateTime.parse("2000-01-01T10:00:00Z").plusDays((n - 1).toLong())
        return java.time.format.DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.US)
            .format(base)
    }
}
