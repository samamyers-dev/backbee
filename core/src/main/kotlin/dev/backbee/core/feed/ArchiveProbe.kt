package dev.backbee.core.feed

/**
 * Phase 0: can we actually see the whole archive?
 *
 * Many feeds expose only the most recent ~100-300 episodes. For a 1,000-episode
 * backlog that is fatal, and it is fatal *silently* - the app looks fine, it
 * just quietly starts you 700 episodes late. This probe is meant to be run
 * against a candidate show before committing to it.
 */
object ArchiveProbe {

    enum class Verdict {
        /** Everything the publisher has appears to be on page one. */
        COMPLETE,

        /** Everything appears reachable, but only by following `rel="next"`. */
        COMPLETE_VIA_PAGING,

        /** Positive evidence of a window: a known total we fall short of, a hard cap, or numbering gaps. */
        LIKELY_TRUNCATED,

        /** No evidence either way. Treat as "verify by hand before starting the show". */
        INCONCLUSIVE,
    }

    /**
     * Item counts that smell like a server-side window rather than a real archive
     * size. A show genuinely having exactly 300 episodes is possible but rare
     * enough that it is worth a second look.
     */
    private val SUSPICIOUS_CAPS = setOf(50, 100, 150, 200, 250, 300, 400, 500, 1000)

    data class Report(
        val feedUrl: String,
        val showTitle: String?,
        val episodesInFirstPage: Int,
        val episodesAfterPaging: Int,
        val pagesFollowed: Int,
        val stoppedAtPageLimit: Boolean,
        val oldestPubDateMillis: Long?,
        val newestPubDateMillis: Long?,
        val lowestEpisodeNumber: Int?,
        val missingEpisodeNumbers: List<Int>,
        val episodesWithoutEnclosure: Int,
        val episodesWithoutDuration: Int,
        /** From Podcast Index / iTunes, when the caller could get it. Null means unknown. */
        val expectedTotal: Int?,
        val verdict: Verdict,
        val findings: List<String>,
    ) {
        val isUsable: Boolean get() = verdict == Verdict.COMPLETE || verdict == Verdict.COMPLETE_VIA_PAGING
    }

    fun evaluate(
        feedUrl: String,
        archive: PagedArchive,
        expectedTotal: Int? = null,
    ): Report {
        val episodes = archive.episodes
        val findings = mutableListOf<String>()

        val numbered = episodes.mapNotNull { it.episodeNumber }.filter { it > 0 }.sorted()
        val lowest = numbered.firstOrNull()
        val missing = findMissingNumbers(numbered)

        val withoutEnclosure = episodes.count { it.enclosureUrl.isNullOrBlank() }
        val withoutDuration = episodes.count { it.durationSeconds == null }

        if (archive.pagesFollowed > 1) {
            findings += "Followed ${archive.pagesFollowed} pages via <atom:link rel=\"next\">; " +
                "page one alone held ${archive.episodesInFirstPage} of ${episodes.size} episodes."
        }
        if (archive.stoppedAtPageLimit) {
            findings += "Stopped at the page limit with more pages still advertised - " +
                "raise ArchiveFetcher.maxPages and re-run before trusting this count."
        }
        if (withoutEnclosure > 0) {
            findings += "$withoutEnclosure episode(s) have no <enclosure> and cannot be downloaded or played."
        }
        if (withoutDuration > 0) {
            findings += "$withoutDuration episode(s) have no <itunes:duration>; " +
                "\"hours left\" estimates will be low until those are played or probed."
        }

        val verdict = when {
            expectedTotal != null && episodes.size >= expectedTotal -> {
                findings += "Directory reports $expectedTotal episodes; feed yielded ${episodes.size}."
                if (archive.pagesFollowed > 1) Verdict.COMPLETE_VIA_PAGING else Verdict.COMPLETE
            }

            expectedTotal != null -> {
                findings += "Directory reports $expectedTotal episodes but the feed yielded only " +
                    "${episodes.size}. Missing ${expectedTotal - episodes.size}."
                Verdict.LIKELY_TRUNCATED
            }

            archive.stoppedAtPageLimit -> Verdict.INCONCLUSIVE

            lowest == 1 && missing.isEmpty() && numbered.size == episodes.size -> {
                findings += "<itunes:episode> runs unbroken from 1 to ${numbered.last()}."
                if (archive.pagesFollowed > 1) Verdict.COMPLETE_VIA_PAGING else Verdict.COMPLETE
            }

            missing.isNotEmpty() -> {
                findings += "Gaps in <itunes:episode>: ${missing.take(20).joinToString()}" +
                    if (missing.size > 20) " (+${missing.size - 20} more)" else ""
                Verdict.LIKELY_TRUNCATED
            }

            lowest != null && lowest > 1 -> {
                findings += "Lowest <itunes:episode> is $lowest, so episodes 1-${lowest - 1} are absent."
                Verdict.LIKELY_TRUNCATED
            }

            episodes.size in SUSPICIOUS_CAPS -> {
                findings += "Episode count is exactly ${episodes.size}, a common server-side window size, " +
                    "and no next-page link was offered."
                Verdict.LIKELY_TRUNCATED
            }

            else -> {
                findings += "No episode numbering and no directory total to compare against."
                Verdict.INCONCLUSIVE
            }
        }

        return Report(
            feedUrl = feedUrl,
            showTitle = archive.head.title,
            episodesInFirstPage = archive.episodesInFirstPage,
            episodesAfterPaging = episodes.size,
            pagesFollowed = archive.pagesFollowed,
            stoppedAtPageLimit = archive.stoppedAtPageLimit,
            oldestPubDateMillis = episodes.firstNotNullOfOrNull { it.pubDateMillis },
            newestPubDateMillis = episodes.lastOrNull { it.pubDateMillis != null }?.pubDateMillis,
            lowestEpisodeNumber = lowest,
            missingEpisodeNumbers = missing,
            episodesWithoutEnclosure = withoutEnclosure,
            episodesWithoutDuration = withoutDuration,
            expectedTotal = expectedTotal,
            verdict = verdict,
            findings = findings,
        )
    }

    /** Gaps in an ascending, already-sorted list of episode numbers. Capped so a wild feed can't blow up memory. */
    internal fun findMissingNumbers(sorted: List<Int>, limit: Int = 500): List<Int> {
        if (sorted.size < 2) return emptyList()
        val present = sorted.toSet()
        val missing = mutableListOf<Int>()
        for (n in sorted.first()..sorted.last()) {
            if (n !in present) {
                missing += n
                if (missing.size >= limit) break
            }
        }
        return missing
    }
}
