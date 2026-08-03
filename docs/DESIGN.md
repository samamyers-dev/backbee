# Design system — "FREE THEM."

Extracted from `Podcast-bee v2 · Screens` (SPEC-02 // HI-FI). This is the
reference the Compose theme implements; when the two disagree, this document is
wrong and should be corrected from the source.

## Palette

Raw values. Components use the semantic aliases below, never these directly.

| Token | Hex | Name |
|-------|-----|------|
| `paper` | `#F2EDE4` | Sandstone Paper — light canvas |
| `basalt` | `#181614` | Basalt Charcoal — dark canvas |
| `espresso` | `#2E2824` | Espresso Ink — light text/border/shadow |
| `ochre` | `#C99653` | Muted Ochre — primary accent |
| `terracotta` | `#AD563E` | Terracotta Clay — secondary accent |
| `sage` | `#5D7B66` | Sage Green — functional / success |
| `slate` | `#3E5066` | Slate Indigo — informational |
| `crimson` | `#8C382A` | Oxide Crimson — alert / danger |

### Semantic aliases

| Alias | Light | Dark |
|-------|-------|------|
| `bgPage`, `bgPanel` | paper | basalt |
| `bgInverse` | espresso | paper |
| `textPrimary` | espresso | paper |
| `textInverse` | paper | basalt |
| `textMuted` | espresso @ 70% | paper @ 70% |
| `borderColor` | espresso | paper |
| `shadowColor` | espresso | paper |
| `ditherLine` | `rgba(46,40,36,.05)` | `rgba(242,237,228,.02)` |

Accents do not change between modes. `onAccentPrimary` is espresso in both;
`onAccentSecondary` and `onAccentAlert` are paper in both.

## Form

The look is print/brutalist, and three properties carry nearly all of it:

- **`--radius: 0px`.** Nothing is rounded. Not buttons, not cards, not artwork.
- **Hard offset shadows**, no blur: `3px 3px 0`, `6px 6px 0`, `9px 9px 0` in
  `shadowColor`. These read as printed registration offsets, not elevation.
- **Visible borders**: 1px thin, 2px divider, 3px heavy, 4px thick.

A dither pattern (`--pattern-dither`, 24px grid of `ditherLine`) sits behind
panels, and a CRT scanline overlay is available for the terminal readouts.

## Type

- **Display**: Impact / Arial Narrow Bold / Arial Black. Used for the big
  numerals and screen titles.
- **Mono**: JetBrains Mono / IBM Plex Mono. Used for every `> READOUT` line,
  timestamps, episode numbers, and status chips.

Scale: `micro 0.625rem`, `xs 0.6875rem`, `sm 0.8125rem`, `base 0.875rem`,
`lg 1rem`, `display-sm 2rem`, `display-md 2.75rem`, `display-lg 3.5rem`.

Weights 400/500/700/800. Leading: tight 1, normal 1.5, relaxed 1.7.
Tracking: tight `-0.02em`, normal 0, wide `0.05em`, widest `0.15em` — the
widest is what gives the small caps labels (`NEXT UP`, `ON DEVICE`) their look.

Spacing scale: 4, 8, 12, 16, 20, 24, 32, 40, 48, 64.

Motion: `fast 120ms`, `standard 300ms`, `cubic-bezier(0.4, 0, 0.2, 1)`.

## Screens in the spec

### 01 · Now — three directions

- **1A Spine** *(recommended default)* — artwork plus a horizontal
  "book-spine" progress rail marked with years, `EP 247/512`, percent read and
  hours left, giant RESUME, `−10s / +30s / 1.6×`, then a Next-up list with
  per-row download state.
- **1B Ledger** — no artwork; the episode numeral is the hero, with elapsed and
  remaining clocks side by side.
- **1C Instrument** — inverted panel, terminal readout header
  (`> POS RESTORED …`), car-legible at arm's length.

### 02 · Archive

- Oldest-first list, sticky year band (`2021 · EPISODES 231–286`), per-row
  state: `✓` played, `●` in-progress with percent, `▼` on device, `★` starred,
  `○` untouched. A `PLAYING` chip marks the current row.
- Year rail `'18 … '26` down the edge.
- Footer counters: `✓ PLAYED 246 · ▼ ON DEVICE 10 · ★ STARRED 31`.
- **Episode detail**: size and on-device state, PLAY FROM 00:00, star, full
  description, free-text note, MARK PLAYED / UNPLAYED, **KEEP AFTER PLAYING**
  toggle, DELETE DOWNLOAD with reclaimable size.
- **Search**: match count against total (`4 MATCHES IN 512 EPISODES`), matched
  substring highlighted in each title, jump-to-episode-number field, and a
  readout footer (`> QUERY … > 4 HITS // 512 INDEXED`).

### 03 · Shelf, add, settings

- **Shelf**: active show badged `READING`, others `BOOKMARKED — FROZEN` with
  `PAUSED AT EP 89 / 412 · 8 MONTHS AGO`, completed shows with a date and
  episode count. `ACTIVATE` is the only promotion.
- **Add show**: RSS URL or search, and an **ARCHIVE COMPLETENESS CHECK** panel
  that is exactly the Phase 0 probe rendered as a terminal readout — items
  declared, enclosures reachable, `rel=next` present or absent, Podcast Index
  cross-check, and a verdict line. Plus MAKE ACTIVE IMMEDIATELY and START AT
  OLDEST EPISODE toggles.
- **Settings**: per-show speed / skip intro / skip outro; downloads keep-ahead,
  storage cap, delete-played-after; resume rewind tiers as a segmented control;
  and a diagnostics readout (`DB CHECKPOINT`, `SYNCTHING TARGET`,
  `POSITION FLUSHES TODAY`).

### 04 · States

- **Offline / download failed** — banner, playback unaffected, stalled queue
  with `FAILED ×3`, readout explaining no action is required.
- **Downloading / storage cap hit** — `8.0 / 8.0 GB`, trimmed-queue
  explanation, in-progress item with rate and ETA, `RAISE CAP` and
  `PURGE PLAYED · 2.1 GB`.
- **First run / empty shelf** — "The shelf is empty", paste-an-RSS-URL.
- **Smart resume** — shows saved position, matched tier, and resulting offset
  (`SAVED 26:41 → RESUME FROM 26:11`), with BT connected but not playing.
- **Archive complete** — "You finished the book", hours listened, years of
  archive, average speed, eps/week, date range, starred list, next show.

### 05 · Off-app surfaces

Lock screen / media notification, Android Auto (800×480), widget 4×2.

## Gaps against the current implementation

Tracked so the retheme covers them rather than only restyling:

1. Bottom nav (NOW / ARCHIVE / SHELF) — currently top-bar icons.
2. Dedicated Downloads screen with cap state, queue and purge.
3. Per-episode **keep after playing** — new column on `marks`.
4. Search result highlighting and match-count readout.
5. Sticky year band in the archive, plus the footer counters.
6. Archive-completeness readout on Add Show (data already exists in
   `ArchiveProbe.Report`; only the rendering is missing).
7. Offline banner and stalled-queue surfacing.
8. Settings diagnostics readout — needs a flush counter and last-checkpoint
   timestamp to be recorded.
9. Relative "8 MONTHS AGO" / "PAUSED 3 DAYS AGO" formatting.

## Verifying the look without a device

`app/src/test/java/dev/backbee/ui/screenshot/ScreenshotTest.kt` renders the
components to PNG on the JVM using Robolectric's native graphics mode, so the
design can be inspected without an emulator or a system image.

CI publishes the results to the **`screenshots`** branch (orphan, force-pushed
each run) rather than as a build artifact, because artifact downloads are signed
blob URLs that some environments cannot reach, and because a branch keeps the
images reviewable instead of expiring.

```bash
git fetch origin screenshots && git checkout origin/screenshots -- screenshots/
```

## Known limitation: late-arriving out-of-order episodes

`ShowRepository.writeEpisodes` rebuilds `order_index` from publication date only
while nothing has been played; after that, new episodes are appended. That is
right for a still-running show, and wrong for the case where *older* episodes
arrive later - they would land after the newest ones instead of at the start.

It is dormant today: it needs episodes to arrive out of chronological order
after playback has begun, which in practice means recovering a truncated
archive mid-show. Worth knowing before that path is built out.

The fix is not difficult and is safe: `positions` is keyed by `episode_id`, not
by `order_index`, so renumbering loses no position and no played flag. What it
does change is the *derived* resume pointer, so a full reindex should come with
an explicit resume pointer on the show row to avoid moving the listener.
