package dev.backbee.core.feed

import java.io.InputStream
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

/**
 * RSS 2.0 / iTunes / Atom-paging podcast feed parser.
 *
 * Built on SAX rather than StAX or XmlPullParser because SAX is the one XML API
 * present on both the JVM and Android, which lets this parser live in a plain
 * Kotlin module and be unit-tested without an emulator.
 *
 * The parser is deliberately forgiving. Podcast feeds in the wild are full of
 * unescaped ampersands, missing durations, and three different date formats; a
 * strict parser would reject archives the app is supposed to be able to play.
 */
object FeedParser {

    fun parse(xml: String): ParsedFeed = parse(xml.byteInputStream(Charsets.UTF_8))

    fun parse(input: InputStream): ParsedFeed {
        val handler = FeedHandler()
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            // Feeds are fetched from the open internet. Refuse doctypes and
            // external entities outright so a hostile feed cannot read local
            // files or hang the parser on an entity expansion bomb.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val reader = factory.newSAXParser().xmlReader
        reader.contentHandler = handler
        // A resolver that returns an empty document is the belt to the feature
        // flags' braces: some parsers ignore the flags but always honour this.
        reader.entityResolver = object : EntityResolver {
            override fun resolveEntity(publicId: String?, systemId: String?) = InputSource("".reader())
        }
        reader.parse(InputSource(input))
        return handler.build()
    }

    private const val ATOM_NS = "http://www.w3.org/2005/Atom"
    private const val ITUNES_NS = "http://www.itunes.com/dtds/podcast-1.0.dtd"
    private const val PODCAST_NS = "https://podcastindex.org/namespace/1.0"

    private class FeedHandler : DefaultHandler() {
        private val text = StringBuilder()
        private var inItem = false
        private var inImage = false

        private var channelTitle: String? = null
        private var channelDescription: String? = null
        private var channelArtwork: String? = null
        private var channelLink: String? = null
        private var channelLastBuild: String? = null
        private var nextPageUrl: String? = null

        private val items = mutableListOf<ParsedEpisode>()

        private var itemGuid: String? = null
        private var itemTitle: String? = null
        private var itemPubDate: String? = null
        private var itemDuration: String? = null
        private var itemEnclosureUrl: String? = null
        private var itemEnclosureBytes: Long? = null
        private var itemDescription: String? = null
        private var itemSummary: String? = null
        private var itemEpisode: String? = null
        private var itemSeason: String? = null

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            text.setLength(0)
            val name = localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty()

            when {
                name == "item" && uri != ATOM_NS -> {
                    inItem = true
                    resetItem()
                }

                name == "entry" && uri == ATOM_NS -> {
                    inItem = true
                    resetItem()
                }

                // Must be tested before the bare <image> branch below, otherwise
                // an <itunes:image href="..."/> is mistaken for the RSS container
                // element and its href is never read.
                name == "image" && uri == ITUNES_NS -> {
                    val href = attributes.value("href")?.trim()
                    if (!inItem && !href.isNullOrEmpty() && channelArtwork == null) channelArtwork = href
                }

                name == "image" -> inImage = true

                name == "link" && uri == ATOM_NS -> {
                    // Paged feeds advertise continuation here. Take the first
                    // rel="next" we see at channel level and ignore per-item ones.
                    if (!inItem && attributes.value("rel") == "next") {
                        nextPageUrl = attributes.value("href")?.trim()?.takeIf { it.isNotEmpty() }
                    }
                }

                name == "enclosure" && inItem -> {
                    itemEnclosureUrl = attributes.value("url")?.trim()
                    itemEnclosureBytes = attributes.value("length")?.trim()?.toLongOrNull()
                }

                // podcast:alternateEnclosure and friends are ignored in v1; the
                // canonical <enclosure> is what the download engine fetches.
                uri == PODCAST_NS -> Unit
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            text.appendRange(ch, start, start + length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val name = localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty()
            val value = text.toString().trim()
            text.setLength(0)

            if (inItem) {
                when {
                    name == "item" || (name == "entry" && uri == ATOM_NS) -> {
                        items += buildItem()
                        inItem = false
                    }

                    name == "guid" || (name == "id" && uri == ATOM_NS) -> itemGuid = value
                    name == "title" -> itemTitle = value
                    name == "pubDate" || name == "published" || name == "updated" ->
                        if (itemPubDate.isNullOrEmpty()) itemPubDate = value

                    name == "duration" && uri == ITUNES_NS -> itemDuration = value
                    name == "episode" && uri == ITUNES_NS -> itemEpisode = value
                    name == "season" && uri == ITUNES_NS -> itemSeason = value
                    name == "description" || name == "encoded" -> itemDescription = value
                    name == "summary" && uri == ITUNES_NS -> itemSummary = value
                }
                return
            }

            when {
                name == "image" && uri != ITUNES_NS -> inImage = false
                name == "title" && channelTitle == null -> channelTitle = value
                name == "description" && channelDescription == null -> channelDescription = value
                name == "lastBuildDate" -> channelLastBuild = value
                // <image><url> is the RSS 2.0 fallback when itunes:image is absent.
                name == "url" && inImage && channelArtwork == null -> channelArtwork = value.takeIf { it.isNotEmpty() }
                name == "link" && uri != ATOM_NS && channelLink == null -> channelLink = value.takeIf { it.isNotEmpty() }
            }
        }

        private fun resetItem() {
            itemGuid = null
            itemTitle = null
            itemPubDate = null
            itemDuration = null
            itemEnclosureUrl = null
            itemEnclosureBytes = null
            itemDescription = null
            itemSummary = null
            itemEpisode = null
            itemSeason = null
        }

        private fun buildItem(): ParsedEpisode {
            val title = itemTitle?.takeIf { it.isNotEmpty() } ?: "Untitled episode"
            val pubDate = itemPubDate?.let(::parseDate)
            // A stable identity is what makes re-ingest idempotent, so fall
            // through as far as we have to rather than ever emitting a blank.
            val guid = itemGuid?.takeIf { it.isNotEmpty() }
                ?: itemEnclosureUrl?.takeIf { it.isNotEmpty() }
                ?: "$title@${pubDate ?: 0L}"

            return ParsedEpisode(
                guid = guid,
                title = title,
                pubDateMillis = pubDate,
                durationSeconds = itemDuration?.let(::parseDuration),
                enclosureUrl = itemEnclosureUrl?.takeIf { it.isNotEmpty() },
                enclosureBytes = itemEnclosureBytes?.takeIf { it > 0 },
                description = (itemDescription ?: itemSummary)?.takeIf { it.isNotEmpty() },
                episodeNumber = itemEpisode?.toIntOrNull(),
                seasonNumber = itemSeason?.toIntOrNull(),
            )
        }

        fun build(): ParsedFeed = ParsedFeed(
            title = channelTitle,
            description = channelDescription,
            artworkUrl = channelArtwork,
            link = channelLink,
            nextPageUrl = nextPageUrl,
            lastBuildDate = channelLastBuild,
            items = items.toList(),
        )
    }

    private val DATE_FORMATS = listOf(
        DateTimeFormatter.RFC_1123_DATE_TIME,
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        DateTimeFormatter.ISO_ZONED_DATE_TIME,
        // Two-digit hour without zone padding, and the "GMT"/"UT" spellings that
        // RFC_1123_DATE_TIME rejects, both show up in long-running archives.
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss zzz", Locale.US),
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.US),
        DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss Z", Locale.US),
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm zzz", Locale.US),
    )

    /** Returns epoch millis, or null if none of the known shapes match. */
    fun parseDate(raw: String): Long? {
        val cleaned = raw.trim().takeIf { it.isNotEmpty() } ?: return null
        for (format in DATE_FORMATS) {
            try {
                return ZonedDateTime.parse(cleaned, format).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                // try the next shape
            }
        }
        return runCatching { Instant.parse(cleaned).toEpochMilli() }.getOrNull()
    }

    /**
     * `<itunes:duration>` is specified as HH:MM:SS but publishers also emit
     * MM:SS and bare seconds. All three appear inside single archives.
     */
    fun parseDuration(raw: String): Long? {
        val cleaned = raw.trim().takeIf { it.isNotEmpty() } ?: return null
        if (!cleaned.contains(':')) {
            // Bare seconds, sometimes fractional ("3612.48").
            return cleaned.toDoubleOrNull()?.takeIf { it >= 0 }?.toLong()
        }
        val parts = cleaned.split(':')
        if (parts.size > 3) return null
        var total = 0L
        for (part in parts) {
            val n = part.trim().toLongOrNull() ?: return null
            if (n < 0) return null
            total = total * 60 + n
        }
        return total
    }

    private fun Attributes.value(name: String): String? =
        getValue(name) ?: getValue("", name)
}
