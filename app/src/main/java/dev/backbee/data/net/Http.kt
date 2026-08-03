package dev.backbee.data.net

import dev.backbee.core.feed.FeedFetcher
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object Http {
    const val USER_AGENT = "backbee/1.0 (personal archive player)"

    fun client(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.MINUTES)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()
}

/** [FeedFetcher] backed by OkHttp, so :core stays free of any HTTP dependency. */
class HttpFeedFetcher(private val client: OkHttpClient) : FeedFetcher {

    override suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            // Some hosts serve a short feed, or a 403, to clients they do not
            // recognise. A plain honest agent string avoids both.
            .header("User-Agent", Http.USER_AGENT)
            .header("Accept", "application/rss+xml, application/xml;q=0.9, */*;q=0.8")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw FeedException("HTTP ${response.code} fetching $url")
            }
            response.body?.string() ?: throw FeedException("Empty body fetching $url")
        }
    }
}

class FeedException(message: String, cause: Throwable? = null) : Exception(message, cause)
