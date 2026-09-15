# Carbon migration review

## Scope

Reviewed UI architecture, navigation, shared controls, viewmodels, repositories,
Room ordering, and playback integration. Source before migration is preserved at
`/home/samm/backbee-before-carbon.tar.gz`; this project folder did not contain
Git metadata. The migration edits UI, widget appearance, fonts/resources, design
documentation and UI tests—not playback, repository or database behavior.

## Existing issues found by static review

These findings reference the **pre-migration source** and were not runtime
reproductions. They are deliberately not mixed into this visual migration.
Paths below are relative to `app/src/main/java/dev/backbee/`.

| Priority | Finding | Source and follow-up |
|---|---|---|
| P1 | Feed refresh can duplicate archive order indices when an unplayed sliding feed drops older retained episodes. Partial listening does not guard this path. Strict greater-than auto-advance can skip equal indices. | `data/repo/ShowRepository.kt:180–220`, `data/db/Entities.kt:39–42`, `data/db/Daos.kt:181–187`. Reindex the complete persisted/incoming union transactionally; regression-test a shrinking feed after partial listening. |
| P1 | Retrying a transient playback failure calls play without preparing the errored player. Player state has no explicit error for UI recovery. | `playback/PlaybackCoordinator.kt:164–180`, `playback/PlayerConnection.kt:18–32,100–108`. Prepare idle/errored playback while preserving position; expose retry state. |
| P2 | A pending media-controller connection can complete after release, installing a late controller/ticker. | `playback/PlayerConnection.kt:63–83`, `ui/MainActivity.kt:43–50`. Retain/cancel the future and reject stale generations. |
| P2 | Intro/outro/resume UI promises and service behavior diverge on queue transitions and settings changes. | `playback/PlaybackCoordinator.kt:103–119,153–158,232,272–275`, `ui/settings/SettingsViewModel.kt:64–70`, `playback/PlayerConnection.kt:100–108`. Centralize transition/resume policy and observe loaded-show settings. |
| P2 | Whole-entity updates can overwrite settings changed while a feed fetch is in flight. | `data/repo/ShowRepository.kt:89–98`, `ui/settings/SettingsViewModel.kt:68–70`. Use column-specific DAO updates. |
| P2 | Shelf recency comes from feed refresh, not listening; episode place comes from played count, not the actual resume target. | `ui/shelf/ShelfViewModel.kt:45–53,87–89`. Derive recency from positions and reuse Now's resume-target query. |

## Accessibility changes included

- Carbon actions have button roles, at least 48dp targets, and distinct keyboard
  focus, hover, pressed and disabled states.
- Steppers announce which setting they increase/decrease.
- Seek control exposes a progress range, set-progress action and arrow keys.
- All typography is bundled IBM Plex, with 12sp minimum small metadata rather
  than the former 10sp readouts.
- Labels are sentence case; status meaning is carried by text, not color alone.
- Dialog bodies and empty states scroll when space is constrained.

## Verified results

The SDK license was explicitly authorized by the user. JDK and Android tools
are installed without root under `/home/samm/.local/share/backbee-toolchain/`.
The full verified command (from the project directory) is:

```sh
bash /home/samm/.local/share/backbee-toolchain/build.sh \
  :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
python -m unittest discover -s tests -p 'test_carbon*.py'
```

- **244 native tests pass**: 65 core and 179 Android; no failures/errors/skips.
- **9 source-contract tests pass** separately.
- **66 actual Compose screenshots** recorded; all seven destinations in both
  themes, actual navigation, additional Settings viewports, forms/dialogs,
  populated Shelf action states, and narrow 320dp/200% text player controls.
- **Debug APK built and signature verified** (`dev.backbee.debug`, min API 26,
  target/compile API 36). IBM Plex fonts and OFL license are present in the APK.
- **Zero new lint findings** compared with the pristine baseline. Existing
  lint: 58 errors, 4 fatal findings, 13 warnings. Release readiness is not claimed.
- **50 backend/core files compared to the source archive: none changed.**
- Independent review identified field naming/error semantics, selected contrast,
  invalid seek values and a toggle-test contract mismatch. These were fixed with
  native regressions. Re-review confirmed those fixes and found an additional
  hover/pressed outline-button contrast issue. A state-color resolver and 22
  new tests now verify real pointer input/rendered color plus all interaction
  variants; the focused and full suites pass. Final independent targeted review
  passed with no remaining introduced security or logic findings.

Tests caught and verified fixes for selected-row links, Settings field links,
NaN seek requests, narrow mini-player title readability, and squeezed Shelf
Activate/Recap controls. Destructive actions have explicit danger ink. Fonts and
license are bundled offline; the license is an asset so resource shrinking does
not remove it.

### Deliverables

- `artifacts/backbee-carbon-debug.apk`
- `artifacts/carbon-ui-screenshots.zip`
- `docs/carbon-previews/` — paired light/dark destination previews
- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html`

Build log: `/home/samm/.local/share/backbee-toolchain/logs/carbon-button-state-final.log`.
Original source backup: `/home/samm/backbee-before-carbon.tar.gz`.

### Verification limits

Screenshots use local seeded fixtures, not real user data. Robolectric validates
native Compose rendering and interactions but not hardware playback, Bluetooth,
widget-host behavior or Android Auto. Widget tokens were migrated and compiled;
Android controls system media surfaces. Only a debug APK is delivered, not a
store-signed release, and the inherited release-blocking lint items still need a
separate maintenance change.
