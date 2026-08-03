# backbee

A single-device Android app for listening through one show's archive at a time.
A bookmark in a very long book — not a podcast inbox.

Built for backlogs of 600–1,500 episodes, tackled serially in the car, on dog
walks, and during chores. One user, one device. No sync, no accounts, no
backend.

## Principles it is built around

1. **One show active at a time.** Switching is deliberate and rare.
2. **Eyes-free first.** The screen matters for three seconds at the start of a
   session. The real surfaces are the media notification, the lock screen,
   Android Auto, and the home-screen widget.
3. **Position is sacred.** Losing playback progress is the one unforgivable
   failure.
4. **Zero decisions while listening.** Auto-advance, auto-download,
   auto-cleanup. The next episode is always known.
5. **Finishing an archive is an event.**
6. **Big touch targets.**

## Layout

```
core/     Pure Kotlin. Feed parsing, archive paging, the Phase 0 probe, smart
          resume, progress maths, download planning, completion stats.
phase0/   CLI archive-completeness probe. Run this before starting a show.
app/      The Android app.
docs/     PHASE0.md, ARCHITECTURE.md, ANDROID_AUTO.md
```

## Building

Open in Android Studio and run, or:

```bash
./gradlew :app:assembleDebug
```

`:core` and `:phase0` are plain JVM modules and build anywhere a JDK 17+ exists:

```bash
./gradlew :core:test          # 54 tests, no Android SDK required
./gradlew :phase0:installDist
```

`settings.gradle.kts` only includes `:app` when it can find an Android SDK, so
the JVM modules stay buildable on a machine or CI job without one.

### Optional: Podcast Index credentials

Without them the app works from feeds alone. With them, a feed that only exposes
its most recent episodes can be completed from the directory automatically. Put
them in `~/.gradle/gradle.properties`, not in the repository:

```properties
podcastIndexKey=YOUR_KEY
podcastIndexSecret=YOUR_SECRET
```

## Before you start a show: Phase 0

Many feeds only expose the most recent ~300 episodes. For a 1,000-episode
backlog that is fatal, and it fails silently — the app just starts you 700
episodes late.

```bash
./gradlew :phase0:run --args="--expect 1247 https://example.com/feed.xml"
```

Exit code 0 means the archive is reachable. See [docs/PHASE0.md](docs/PHASE0.md)
for what the probe checks, and what to do when a feed turns out to be a window
rather than an archive. The same probe runs inside the app on every add and
refresh, with a Podcast Index fallback attached.

## Android Auto

Auto only lists media apps installed from Play, so a sideloaded build is
invisible until you enable Developer settings → **Unknown sources** in the
Android Auto app. Full instructions in
[docs/ANDROID_AUTO.md](docs/ANDROID_AUTO.md).

## Backup

A nightly `VACUUM INTO` checkpoint of the database is written to a folder you
pick in Settings. Point it at whatever Syncthing watches and it reaches the home
server on its own. That is the entire durability story; losing the phone costs
at most one day of position.

## Milestones

The build order from the spec, and where each one landed:

| # | Milestone | Where |
|---|-----------|-------|
| 1 | Feed ingest + archive list, Phase 0 | `core/feed/`, `phase0/`, `ShowRepository`, `ArchiveScreen` |
| 2 | Playback + position persistence + notification | `PlaybackService`, `PositionWriter`, `AudioRouteMonitor` |
| 3 | Auto-advance, smart resume, per-show speed & skip | `PlaybackCoordinator`, `SmartResume`, `SkipForwardingPlayer` |
| 4 | Download-ahead engine + storage management | `core/download/`, `DownloadAheadWorker`, `EpisodeFiles` |
| 5 | Android Auto | `AutoLibraryCallback` |
| 6 | Shelf, multi-show, completion screen | `ShelfScreen`, `CompletionScreen`, `core/stats/` |
| 7 | Widget, nightly backup, stats | `widget/`, `BackupWorker` |

## Out of scope in v1

Sync, accounts, discovery beyond add-by-URL/search, sleep timer, silence
trimming, chapters, cross-show playlists, new-episode notifications, video.

v1.5 candidates: loudness normalisation, chapters, sleep timer, OPML
import/export, per-episode speed override.

## Getting an APK onto a phone

**[Download the latest debug build](https://github.com/samamyers-dev/backbee/releases/download/debug-latest/backbee-debug.apk)**

Open that link on the phone and tap it. Android will ask you to allow "Install
unknown apps" for whichever browser you used, then install. No sign-in, no zip,
no file manager.

CI republishes that same URL on every green build, so it is always the newest
passing APK. Builds that compile but fail their tests do not reach it - those
are still available as the `backbee-debug-apk` artifact on the run itself,
which needs a GitHub sign-in and arrives as a zip.

Locally, with Android Studio or a configured SDK:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug build uses the `dev.backbee.debug` application id, so it installs
alongside a release build rather than replacing it.

## Test status

`:core` — 54 tests, all passing. This is where the logic that is expensive to
get wrong lives: feed parsing against malformed real-world XML, paged archive
walking, the Phase 0 verdict rules, smart-resume tiers, progress formatting, and
download planning including storage-cap eviction.

`:app` — compiles and assembles on CI; `BackbeeDatabaseTest` covers the SQL the
rest of the app depends on. Nothing in `:app` can be built in the development
container used to write it (Google's Maven host and the Android SDK endpoint are
both unreachable there), so the GitHub Actions build is the authority on whether
the Android module is sound.
