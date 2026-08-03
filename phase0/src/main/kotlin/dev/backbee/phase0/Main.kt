package dev.backbee.phase0

import dev.backbee.core.feed.ArchiveFetcher
import dev.backbee.core.feed.ArchiveProbe
import dev.backbee.core.feed.FeedFetcher
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Phase 0 verification, runnable before any app code is trusted.
 *
 *   ./gradlew :phase0:run --args="https://example.com/feed.xml"
 *   ./gradlew :phase0:run --args="--expect 1247 https://example.com/feed.xml"
 *
 * Exit code 0 means the archive looks complete, 1 means it does not, 2 means the
 * probe could not tell. Non-zero is a deliberate signal: a show that fails here
 * needs a Podcast Index import step before it is worth starting.
 */
private const val USAGE = """
Usage: phase0 [options] <feed-url> [<feed-url> ...]

Options:
  --expect <n>      Episode count from a directory (Podcast Index / iTunes), used
                    as ground truth for the completeness check.
  --max-pages <n>   How many rel="next" pages to follow (default 50).
  --verbose         Print every episode the walk produced.

Exit codes: 0 complete, 1 likely truncated, 2 inconclusive, 3 fetch error.
"""

fun main(args: Array<String>) {
    if (args.isEmpty() || args.contains("--help") || args.contains("-h")) {
        println(USAGE.trim())
        exitProcess(if (args.isEmpty()) 3 else 0)
    }

    var expected: Int? = null
    var maxPages = 50
    var verbose = false
    val urls = mutableListOf<String>()

    var i = 0
    while (i < args.size) {
        when (val arg = args[i]) {
            "--expect" -> expected = args.getOrNull(++i)?.toIntOrNull()
                ?: fail("--expect needs a number")

            "--max-pages" -> maxPages = args.getOrNull(++i)?.toIntOrNull()
                ?: fail("--max-pages needs a number")

            "--verbose" -> verbose = true
            else -> {
                if (arg.startsWith("--")) fail("unknown option $arg")
                urls += arg
            }
        }
        i++
    }

    if (urls.isEmpty()) fail("no feed URL given")

    val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val fetcher = FeedFetcher { url ->
        val request = Request.Builder()
            .url(url)
            // Some CDNs serve a truncated feed, or none at all, to unknown agents.
            .header("User-Agent", "backbee-phase0/1.0 (+archive completeness probe)")
            .header("Accept", "application/rss+xml, application/xml;q=0.9, */*;q=0.8")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} for $url")
            response.body?.string() ?: error("empty body for $url")
        }
    }

    var worst = 0
    runBlocking {
        for (url in urls) {
            val code = try {
                val archive = ArchiveFetcher(fetcher, maxPages).fetchArchive(url)
                val report = ArchiveProbe.evaluate(url, archive, expected)
                printReport(report)
                if (verbose) {
                    archive.episodes.forEachIndexed { index, episode ->
                        println("  ${(index + 1).toString().padStart(5)}  ${episode.title}")
                    }
                }
                when (report.verdict) {
                    ArchiveProbe.Verdict.COMPLETE, ArchiveProbe.Verdict.COMPLETE_VIA_PAGING -> 0
                    ArchiveProbe.Verdict.LIKELY_TRUNCATED -> 1
                    ArchiveProbe.Verdict.INCONCLUSIVE -> 2
                }
            } catch (e: Exception) {
                println("FETCH ERROR  $url")
                println("  ${e.message}")
                3
            }
            worst = maxOf(worst, code)
        }
    }
    exitProcess(worst)
}

private fun printReport(report: ArchiveProbe.Report) {
    val bar = "-".repeat(72)
    println(bar)
    println(report.showTitle ?: "(untitled feed)")
    println(report.feedUrl)
    println(bar)
    println("  verdict            ${report.verdict}")
    println("  episodes (page 1)  ${report.episodesInFirstPage}")
    println("  episodes (total)   ${report.episodesAfterPaging}")
    println("  pages followed     ${report.pagesFollowed}")
    report.expectedTotal?.let { println("  directory total    $it") }
    report.lowestEpisodeNumber?.let { println("  lowest ep number   $it") }
    println("  date range         ${formatRange(report.oldestPubDateMillis, report.newestPubDateMillis)}")
    if (report.episodesWithoutEnclosure > 0) println("  no enclosure       ${report.episodesWithoutEnclosure}")
    if (report.episodesWithoutDuration > 0) println("  no duration        ${report.episodesWithoutDuration}")
    if (report.findings.isNotEmpty()) {
        println()
        report.findings.forEach { println("  * $it") }
    }
    if (!report.isUsable) {
        println()
        println("  Next steps:")
        println("   1. Look for an <atom:link rel=\"next\"> or a ?page= parameter the publisher")
        println("      supports but does not advertise.")
        println("   2. Query Podcast Index (/api/1.0/episodes/byfeedurl?max=10000) for the full")
        println("      episode list and import from there.")
        println("   3. If neither covers the archive, treat Podcast Index import as a")
        println("      first-class step of milestone 1 for this show.")
    }
    println()
}

private fun formatRange(oldest: Long?, newest: Long?): String {
    if (oldest == null || newest == null) return "unknown"
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US).withZone(ZoneId.systemDefault())
    return "${fmt.format(Instant.ofEpochMilli(oldest))} to ${fmt.format(Instant.ofEpochMilli(newest))}"
}

private fun fail(message: String): Nothing {
    System.err.println("phase0: $message")
    System.err.println(USAGE.trim())
    exitProcess(3)
}
