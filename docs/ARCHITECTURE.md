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

There is one active show. Its archive, oldest-first, is the storage spine; the
order episodes are listened in is a per-show *direction* laid over it. There is
no separate queue model to keep in sync. `episodes.order_index` is the spine, and
everything else hangs off it:

- Resume = the first unplayed `order_index` from the show's listening end.
- Auto-advance = the next unplayed `order_index` in that direction. Skipping the
  played ones keeps the queue in agreement with Resume and "Next up" — replaying
  an episode you already finished because it happened to sit between two
  unplayed ones is playing out of sequence, not gapless playback.
- Download-ahead = the next N unplayed `order_index` values in that direction.
- Progress = played count over total count, with "Ep n of N" counted from the
  end the show is listened from.

### Listening direction

`shows.direction` is `OLDEST_FIRST` (the default, and what every existing show
becomes on upgrade) or `NEWEST_FIRST`. It decides which way the queue walks, not
how the archive is stored: `order_index` keeps its oldest-first meaning, nothing
is renumbered, and switching direction cannot move a bookmark or clear a played
flag. Resume simply resolves to the first unplayed episode from the new end.

The DAO queries take a single `newestFirst` flag and reverse the walk order in
SQL rather than keeping two copies of every query. The player's media-item order
is the queue order, so auto-advance direction falls out of the window; Android
Auto and the widget call the same direction-aware repository methods as the
phone.

A feed refresh still appends new episodes to the end of `order_index`. On a
newest-first show that end is the front of the queue, so a new arrival becomes
Resume and starts playing next. That is the definition of newest-first; the
half-heard older episode keeps its position and is still one tap away.

The Archive list is shown in listening order too. It is the queue, and a list
that reads oldest-first while Resume and "Next up" walk the other way would
disagree with the two screens beside it. The header says which direction is in
force; the year bands still describe archive years, using min/max episode
numbers so they never read backwards.

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

- Fill in the direction of travel from the current episode, nearest first.
- Played episodes inside the window do not consume a look-ahead slot.
- A just-played episode is kept for the delete-after window even though it is
  now behind the current position, so a mis-tap can still get back to it.
- Under storage pressure, give up the *furthest ahead* episode, never the next
  one. "Ahead" follows the show's direction: for a newest-first show the
  sacrifice is the furthest one towards older episodes.

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
the Storage Access Framework — point it at a folder something syncs off the
phone. That is the whole story: no accounts, no sync protocol, no server.
Losing the phone costs at most one day of position. Android's own backup
covers the database and the settings DataStore as well.

`BackupRestorer` is the way back: it stages the chosen file in the cache,
opens it read-only for an integrity check and a look at its tables and
`user_version`, closes Room, swaps the file (dropping the old `-wal`/`-shm`),
and restarts the process so nothing holds a handle to the old database.

`VACUUM INTO` needs SQLite 3.27, so below Android 11 the worker checkpoints
the WAL and copies the main file inside a write transaction instead. For the
same reason none of the DAO writes use `ON CONFLICT DO UPDATE` (SQLite 3.24):
every upsert is `INSERT OR IGNORE` followed by `UPDATE`, which runs on every
supported release.

SAF rather than a storage permission, and a copy through `ContentResolver`
rather than a direct path, because `VACUUM INTO` needs a real filesystem target
and a tree URI is not one. It vacuums to the cache directory first, then copies.

## What is deliberately absent

No DI framework — the graph is small enough that a hand-built container in
`AppContainer` is easier to read. No repository interfaces with single
implementations. No queue model. No sync layer. No notification for new
episodes: someone 300 episodes behind does not need to be told another one
arrived.
