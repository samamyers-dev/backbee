package dev.backbee.data.net

import dev.backbee.core.feed.ParsedEpisode
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class DirectoryResult(
    val title: String,
    val feedUrl: String,
    val artworkUrl: String?,
    val author: String?,
    /** Episode count as the directory sees it. This is the ground truth Phase 0 compares against. */
    val episodeCount: Int?,
)

/**
 * Add-by-search, and the second opinion on how many episodes a show really has.
 *
 * iTunes needs no credentials, which makes it the default. Podcast Index needs a
 * key pair but can hand back the *entire* episode list for a feed, which is the
 * escape hatch when a publisher's own feed only exposes a recent window.
 */
class PodcastDirectory(
    private val client: OkHttpClient,
    private val podcastIndexKey: String? = null,
    private val podcastIndexSecret: String? = null,
) {

    val podcastIndexAvailable: Boolean
        get() = !podcastIndexKey.isNullOrBlank() && !podcastIndexSecret.isNullOrBlank()

    suspend fun search(term: String, limit: Int = 25): List<DirectoryResult> = withContext(Dispatchers.IO) {
        if (term.isBlank()) return@withContext emptyList()
        val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("term", term)
            .addQueryParameter("entity", "podcast")
            .addQueryParameter("limit", limit.toString())
            .build()

        val body = get(Request.Builder().url(url).header("User-Agent", Http.USER_AGENT).build())
        val results = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList()

        buildList {
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val feedUrl = item.optString("feedUrl").takeIf { it.isNotBlank() } ?: continue
                add(
                    DirectoryResult(
                        title = item.optString("collectionName").ifBlank { feedUrl },
                        feedUrl = feedUrl,
                        artworkUrl = item.optString("artworkUrl600").takeIf { it.isNotBlank() }
                            ?: item.optString("artworkUrl100").takeIf { it.isNotBlank() },
                        author = item.optString("artistName").takeIf { it.isNotBlank() },
                        episodeCount = item.optInt("trackCount", -1).takeIf { it > 0 },
                    )
                )
            }
        }
    }

    /**
     * How many episodes Podcast Index believes the feed has. Used as
     * [dev.backbee.core.feed.ArchiveProbe]'s `expectedTotal`; null when we have
     * no credentials or the lookup fails, which the probe treats as "unknown"
     * rather than as agreement.
     */
    suspend fun episodeCountForFeed(feedUrl: String): Int? = withContext(Dispatchers.IO) {
        if (!podcastIndexAvailable) return@withContext null
        runCatching {
            val url = "https://api.podcastindex.org/api/1.0/podcasts/byfeedurl".toHttpUrl().newBuilder()
                .addQueryParameter("url", feedUrl)
                .build()
            val body = get(podcastIndexRequest(url.toString()))
            JSONObject(body).optJSONObject("feed")?.optInt("episodeCount", -1)?.takeIf { it > 0 }
        }.getOrNull()
    }

    /**
     * The full episode list from Podcast Index, for archives the publisher's own
     * feed will not serve. `max` is deliberately generous: the whole point is the
     * 1,500-episode back catalogue.
     */
    suspend fun episodesForFeed(feedUrl: String, max: Int = 10_000): List<ParsedEpisode> = withContext(Dispatchers.IO) {
        if (!podcastIndexAvailable) return@withContext emptyList()
        val url = "https://api.podcastindex.org/api/1.0/episodes/byfeedurl".toHttpUrl().newBuilder()
            .addQueryParameter("url", feedUrl)
            .addQueryParameter("max", max.toString())
            .build()

        val body = get(podcastIndexRequest(url.toString()))
        val items = JSONObject(body).optJSONArray("items") ?: return@withContext emptyList()

        buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val guid = item.optString("guid").takeIf { it.isNotBlank() }
                    ?: item.optString("enclosureUrl").takeIf { it.isNotBlank() }
                    ?: continue
                add(
                    ParsedEpisode(
                        guid = guid,
                        title = item.optString("title").ifBlank { "Untitled episode" },
                        // Podcast Index publishes seconds; the rest of the app works in millis.
                        pubDateMillis = item.optLong("datePublished", 0L).takeIf { it > 0 }?.times(1000),
                        durationSeconds = item.optLong("duration", 0L).takeIf { it > 0 },
                        enclosureUrl = item.optString("enclosureUrl").takeIf { it.isNotBlank() },
                        enclosureBytes = item.optLong("enclosureLength", 0L).takeIf { it > 0 },
                        description = item.optString("description").takeIf { it.isNotBlank() },
                        episodeNumber = item.optInt("episode", -1).takeIf { it > 0 },
                        seasonNumber = item.optInt("season", -1).takeIf { it > 0 },
                    )
                )
            }
        }
    }

    private fun podcastIndexRequest(url: String): Request {
        val key = podcastIndexKey.orEmpty()
        val secret = podcastIndexSecret.orEmpty()
        val date = (System.currentTimeMillis() / 1000).toString()
        val digest = MessageDigest.getInstance("SHA-1")
            .digest((key + secret + date).toByteArray())
            .joinToString("") { "%02x".format(it) }

        return Request.Builder()
            .url(url)
            .header("User-Agent", Http.USER_AGENT)
            .header("X-Auth-Key", key)
            .header("X-Auth-Date", date)
            .header("Authorization", digest)
            .build()
    }

    private fun get(request: Request): String =
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw FeedException("HTTP ${response.code} for ${request.url}")
            response.body?.string() ?: throw FeedException("Empty body for ${request.url}")
        }
}
