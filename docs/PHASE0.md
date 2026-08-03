# Phase 0 — is the archive actually reachable?

Everything else in this app assumes you can see all 1,247 episodes. A large
share of podcast feeds only expose the most recent 100–300. When that happens
nothing looks broken: the app cheerfully starts you 900 episodes late and calls
it episode one.

So this check comes before writing app code, and it is wired into the app as
well, because the answer can change when a publisher switches host.

## Running it

```bash
./gradlew :phase0:run --args="https://example.com/feed.xml"

# With a directory episode count as ground truth, which makes the verdict solid
# rather than heuristic:
./gradlew :phase0:run --args="--expect 1247 https://example.com/feed.xml"

# Show every episode the walk produced:
./gradlew :phase0:run --args="--verbose https://example.com/feed.xml"
```

Exit codes are meant for scripting:

| Code | Meaning |
|------|---------|
| 0 | Complete, or complete after following `rel="next"` pages |
| 1 | Likely truncated — do not start this show without an import step |
| 2 | Inconclusive — verify by hand |
| 3 | Fetch error |

## What it checks

The probe walks the feed and every `<atom:link rel="next">` page behind it,
deduplicates by GUID, and then looks for evidence in this order:

1. **A directory total to compare against.** If Podcast Index or iTunes says
   1,247 and the feed yields 300, that is settled.
2. **`<itunes:episode>` numbering.** Running unbroken from 1 is strong evidence
   of completeness. Starting at 948, or having holes, is strong evidence
   against.
3. **A suspiciously round count.** Exactly 100, 300, or 500 items with no next
   page is far more likely to be a server-side window than a real archive size.

If none of these apply, the verdict is `INCONCLUSIVE`, which is deliberately not
a pass. "We found no problem" and "we confirmed it is complete" are different
claims and the report keeps them apart.

The probe also counts episodes with no `<enclosure>` (unplayable) and no
`<itunes:duration>` (which makes "hours left" undercount until they are played).

## When a feed is truncated

In order of preference:

1. **Look for undocumented paging.** Some hosts accept `?page=2` or
   `?before=<date>` without advertising it in the feed. Try it by hand.
2. **Import from Podcast Index.** `/api/1.0/episodes/byfeedurl?max=10000` will
   usually return the whole archive. The app does this automatically when
   credentials are configured — see below — and merges directory episodes with
   feed episodes, preferring the publisher's own data where both exist.
3. **If neither covers it**, archive completion becomes a first-class import
   step in milestone 1 for that show, and probably means hand-assembling a
   feed. Worth knowing before starting, not 300 episodes in.

## Podcast Index credentials

Optional. Without them the app works from feeds alone; with them, truncated
archives can be completed automatically and the probe gets a real episode count
to compare against.

Put them in `~/.gradle/gradle.properties`, not in the repository:

```properties
podcastIndexKey=YOUR_KEY
podcastIndexSecret=YOUR_SECRET
```

## Where this lives in the code

- `core/src/main/kotlin/dev/backbee/core/feed/ArchiveFetcher.kt` — the paged walk
- `core/src/main/kotlin/dev/backbee/core/feed/ArchiveProbe.kt` — the verdict
- `phase0/src/main/kotlin/dev/backbee/phase0/Main.kt` — the CLI
- `app/.../data/repo/ShowRepository.kt` — the same probe, run on every add and
  refresh, with the Podcast Index fallback attached

The probe's rules are covered by `core/src/test/kotlin/.../ArchiveProbeTest.kt`,
including the exact failure this document exists to prevent: a 300-episode
window onto a 1,247-episode show.
