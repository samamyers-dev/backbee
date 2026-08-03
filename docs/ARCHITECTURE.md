# Architecture

## Module layout

```
core/     Pure Kotlin, no Android. Feed parsing, archive paging, the Phase 0
          probe, smart resume, progress maths, download planning, completion
          stats. Runs on any JDK, which is why the logic worth testing lives
          here and is covered by ordinary JUnit tests.

phase0/   A CLI wrapper around the probe. Run before committing to a show.

app/      The Android app. Room, Media3, WorkManager, Compose, Glance.
```

`:app` is included conditionally in `settings.gradle.kts` — only when an Android
SDK is present. That keeps `gradle :core:test` working on a machine (or CI job)
without one, instead of failing at configuration time.

## The shape of the thing

There is one active show. Its archive, oldest-first, *is* the queue; there is no
separate queue model to keep in sync. `episodes.order_index` is the spine, and
everything else hangs off it:

- Resume = the lowest `order_index` that is not played.
- Auto-advance = the next `order_index`.
- Download-ahead = the next N `order_index` values that are unplayed.
- Progress = played count over total count.

### Why order_index is never renumbered after playback starts

Renumbering an archive someone is 300 episodes into would move their bookmark.
So `ShowRepository.writeEpisodes` rebuilds the order from publication date only
while nothing has been played — which covers the case that matters, recovering
from a truncated first import. Once anything is played, new episodes append to
the end, which is also the right behaviour for a show that is still running.

## Playback

`PlaybackService` is a `MediaLibraryService`: it owns the ExoPlayer, and the
media notification, lock screen and Android Auto are all clients of its session.
The app's own UI is a client too, via `PlayerConnection` — so there is exactly
one path into playback, and the phone and the car cannot drift apart.

Three pieces sit around the player:

**`PlaybackCoordinator`** decides what plays. It builds a `QueuePlan` — a window
of 25 episodes plus a start position — and tops the window up as playback
advances. Building a plan and applying it are separate methods on purpose:
Media3's session callbacks want the queue *returned* so they can apply it
themselves, and setting it directly as well would have the session overwrite
what was just set.

**`PositionWriter`** keeps the bookmark. It flushes every five seconds while
playing, and on pause, seek, audio-focus loss, player error, media-item
transition, task removal, and service destruction.

**`AudioRouteMonitor`** covers the case that actually loses positions in podcast
apps: the car turns off, Bluetooth drops, and the process dies before any pause
callback would have run. Route loss is a mandatory flush, not an inference.

Skip increments are user settings, but ExoPlayer fixes them at construction, so
`SkipForwardingPlayer` wraps the player and overrides `seekForward()`/`seekBack()`.
That is the one place headset buttons, steering-wheel controls, the notification
and the widget all converge.

## Downloads

`DownloadPlanner` (in `:core`) is a pure function: given a snapshot of the
archive, the current position, and the configuration, it returns what to fetch
and what to delete. All the context-dependent parts — what is on disk, how big
things are, what the user configured — are gathered in `DownloadRepository`.

The rules it encodes:

- Fill forward from the current episode, nearest first.
- Played episodes inside the window do not consume a look-ahead slot.
- A just-played episode is kept for the delete-after window even though it is
  now behind the current position, so a mis-tap can still get back to it.
- Under storage pressure, give up the *furthest ahead* episode, never the next
  one. Never evict something nearer than what you are making room for.

`LocalFileIndex` mirrors the download table in memory so the player's
`ResolvingDataSource` can swap a remote URL for a local file without touching
the database on the loading thread. Doing the swap at open time rather than at
queue-build time is what lets an episode that finished downloading while an
earlier one played still be played from disk.

## Data

Five tables, matching the spec. `positions` is split from `episodes` because it
is written every few seconds and `episodes` is not — a flush should touch a
narrow row, not a wide one.

`EpisodeRow` is a flat projection joining all five, because the archive list can
be 1,500 rows and stitching relations per item would be slower for no gain.

## Durability

A nightly `VACUUM INTO` writes a consistent snapshot to a folder chosen through
the Storage Access Framework — point it at whatever Syncthing watches. That is
the whole story: no accounts, no sync protocol, no server. Losing the phone
costs at most one day of position.

SAF rather than a storage permission, and a copy through `ContentResolver`
rather than a direct path, because `VACUUM INTO` needs a real filesystem target
and a tree URI is not one. It vacuums to the cache directory first, then copies.

## What is deliberately absent

No DI framework — the graph is small enough that a hand-built container in
`AppContainer` is easier to read. No repository interfaces with single
implementations. No queue model. No sync layer. No notification for new
episodes: someone 300 episodes behind does not need to be told another one
arrived.
