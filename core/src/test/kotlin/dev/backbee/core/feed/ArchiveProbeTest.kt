package dev.backbee.core.feed

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Phase 0 is the "does this project even work" check, so these tests pin the
 * exact situations that decide whether a show can be started at all.
 */
class ArchiveProbeTest {

    private suspend fun probe(
        pages: Map<String, String>,
        expectedTotal: Int? = null,
        maxPages: Int = 50,
    ): ArchiveProbe.Report {
        val fetcher = FeedFetcher { url -> pages[url] ?: error("unexpected fetch of $url") }
        val archive = ArchiveFetcher(fetcher, maxPages).fetchArchive("https://example.com/feed")
        return ArchiveProbe.evaluate("https://example.com/feed", archive, expectedTotal)
    }

    @Test
    fun `unbroken numbering from one reads as complete`() = runTest {
        val report = probe(mapOf("https://example.com/feed" to Fixtures.generated(count = 437)))

        assertThat(report.verdict).isEqualTo(ArchiveProbe.Verdict.COMPLETE)
        assertThat(report.isUsable).isTrue()
        assertThat(report.episodesAfterPaging).isEqualTo(437)
        assertThat(report.lowestEpisodeNumber).isEqualTo(1)
    }

    @Test
    fun `a feed that starts at episode 948 is a window, not an archive`() = runTest {
        // The exact failure the spec calls fatal: 300 recent episodes of a
        // 1,247-episode show, with nothing pointing at the rest.
        val report = probe(mapOf("https://example.com/feed" to Fixtures.generated(count = 300, firstNumber = 948)))

        assertThat(report.verdict).isEqualTo(ArchiveProbe.Verdict.LIKELY_TRUNCATED)
        assertThat(report.isUsable).isFalse()
        assertThat(report.findings.joinToString()).contains("episodes 1-947 are absent")
    }

    @Test
    fun `falling short of the directory total is truncation regardless of numbering`() = runTest {
        val report = probe(
            pages = mapOf("https://example.com/feed" to Fixtures.generated(count = 300, numbered = false)),
            expectedTotal = 1247,
        )

        assertThat(report.verdict).isEqualTo(ArchiveProbe.Verdict.LIKELY_TRUNCATED)
        assertThat(report.findings.joinToString()).contains("Missing 947")
    }

    @Test
    fun `matching the directory total after paging reads as complete via paging`() = runTest {
        val report = probe(
            pages = mapOf(
                "https://example.com/feed" to
                    Fixtures.generated(count = 300, firstNumber = 701, nextPage = "https://example.com/feed?p=2"),
                "https://example.com/feed?p=2" to
                    Fixtures.generated(count = 700, firstNumber = 1),
            ),
            expectedTotal = 1000,
        )

        assertThat(report.verdict).isEqualTo(ArchiveProbe.Verdict.COMPLETE_VIA_PAGING)
        assertThat(report.episodesAfterPaging).isEqualTo(1000)
        assertThat(report.episodesInFirstPage).isEqualTo(300)
        assertThat(report.pagesFollowed).isEqualTo(2)
    }

    @Test
    fun `a round episode count with no paging is treated as suspicious`() = runTest {
        val report = probe(mapOf("https://example.com/feed" to Fixtures.generated(count = 300, numbered = false)))

        assertThat(report.verdict).isEqualTo(ArchiveProbe.Verdict.LIKELY_TRUNCATED)
        assertThat(report.findings.joinToString()).contains("common server-side window size")
    }

    @Test
    fun `an unremarkable count with no numbering is inconclusive, not a pass`() = runTest {
        val report = probe(mapOf("https://example.com/feed" to Fixtures.generated(count = 437, numbered = false)))

        assertThat(report.verdict).isEqualTo(ArchiveProbe.Verdict.INCONCLUSIVE)
        assertThat(report.isUsable).isFalse()
    }

    @Test
    fun `gaps in numbering are reported as truncation`() = runTest {
        val report = probe(
            mapOf("https://example.com/feed" to Fixtures.generated(count = 100, skip = setOf(17, 18, 63)))
        )

        assertThat(report.verdict).isEqualTo(ArchiveProbe.Verdict.LIKELY_TRUNCATED)
        assertThat(report.missingEpisodeNumbers).containsExactly(17, 18, 63).inOrder()
    }

    @Test
    fun `hitting the page limit is inconclusive rather than a false pass`() = runTest {
        val fetcher = FeedFetcher { url ->
            val page = url.substringAfter("p=", "1").toInt()
            Fixtures.generated(count = 10, nextPage = "https://example.com/feed?p=${page + 1}", firstNumber = page * 100)
        }
        val archive = ArchiveFetcher(fetcher, maxPages = 2).fetchArchive("https://example.com/feed?p=1")
        val report = ArchiveProbe.evaluate("https://example.com/feed?p=1", archive)

        assertThat(report.verdict).isEqualTo(ArchiveProbe.Verdict.INCONCLUSIVE)
        assertThat(report.stoppedAtPageLimit).isTrue()
        assertThat(report.findings.joinToString()).contains("raise ArchiveFetcher.maxPages")
    }

    @Test
    fun `counts episodes that could never be played or estimated`() = runTest {
        val report = probe(mapOf("https://example.com/feed" to Fixtures.SPARSE_ITEM))

        assertThat(report.episodesWithoutEnclosure).isEqualTo(1)
        assertThat(report.episodesWithoutDuration).isEqualTo(1)
        assertThat(report.findings.joinToString()).contains("no <enclosure>")
    }

    @Test
    fun `missing number scan is bounded`() {
        val sparse = listOf(1, 100_000)
        assertThat(ArchiveProbe.findMissingNumbers(sparse, limit = 10)).hasSize(10)
        assertThat(ArchiveProbe.findMissingNumbers(emptyList())).isEmpty()
        assertThat(ArchiveProbe.findMissingNumbers(listOf(5))).isEmpty()
    }
}
