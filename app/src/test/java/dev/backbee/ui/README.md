# Carbon UI regression coverage

These tests render production composables, ViewModels and Room queries. They do not use source-string guards or fake destination screens.

## Run

From the repository root, using the installed isolated Android toolchain:

```sh
bash /home/samm/.local/share/backbee-toolchain/build.sh :app:testDebugUnitTest \
  --tests 'dev.backbee.ui.theme.CarbonContrastTest' \
  --tests 'dev.backbee.ui.components.*' \
  --tests 'dev.backbee.ui.screenshot.DestinationScreenshotTest'
```

The existing configuration records PNGs under `app/build/outputs/roborazzi/`; it does **not** compare committed baselines. This is native rendering plus semantic/interaction assertions, not pixel-diff gating.

## Coverage and isolation

- `CarbonContrastTest` wraps colors in `ContrastPair`: inline Compose `Color` arguments directly on a JUnit parameterized constructor create an incompatible synthetic JVM constructor.
- Its 76 real-token pairs cover all seven readable inks on `bgPage`, `bgPanel`, and `layer02`; primary/muted on fields; primary/muted/selected-link on selected episode rows; inverse and action/on-color pairs. It additionally checks `textAccentOnField` because Settings' rewind segments use this higher-contrast grade. Unused alert/functional/interactive-on-field and regular-link-on-selected cross-products are not contracts. Every pair asserts WCAG normal-text 4.5:1, and prints its measured ratio.
- `CarbonInteractionTest` checks button callbacks/disabled behavior, actual editable input, accessible naming, progress, dialogs, and toggles. Null-callback toggles are decorative: a parent toggleable row owns the single switch role/state/action. Disabled BasicTextField has `IsEditable=false` and can omit `Focused`; absence or false is required.
- `PlaybackControlsTest` verifies seek boundaries, pending acknowledgement, NaN rejection, and 320dp/200% font-scale transport controls. It dispatches the explicitly labelled row accessibility action and separately touches the unmerged title, whose width must be at least 160dp. The original merged-text `performClick()` could hit a transport child at the merged row's center.
- `CarbonFieldAccessibilityTest` is maintained by the accessibility-fix agent and checks explicit field labels, error semantics, and destructive/disabled button colors.
- `DestinationScreenshotTest` renders actual Now, Archive, Shelf, Episode detail, Downloads, Settings, and Completion screens in both themes. It captures Settings' rewind, backup, and About sections as additional viewports. It also navigates all real NavHost tabs and Archive → Episode → Back in both themes, capturing the screens with tab chrome.
- Archive search types into the actual search input, checks real filtered rows, preserved whole-archive counts, and the episode navigation callback. The search field is distinguished from the numeric jump by its IME action: flattened semantics make a shared-sibling-label matcher ambiguous.
- Inactive and completed Shelf fixtures check visible Activate/Recap/Remove controls with at least 48dp width and height, then open and cancel the local removal dialog. They do not activate a show, enqueue work, or confirm deletion.
- AppContainer owns its database; this fixture uses Robolectric's isolated temporary on-disk Room database, not an injected in-memory DB. Plain `Application` avoids BackbeeApp startup scheduling, artwork is null, PlayerConnection stays disconnected, and `.invalid` feed/media URLs are identifiers only. No directory search, refresh, download, playback, or external network action is invoked.

## Deterministic native fixture lessons

1. Drain Robolectric's main Looper inside bounded waits as well as advancing Compose frames. Room resumes `viewModelScope` on the Android Handler; Compose's frame clock alone left both eager Completion loading and Archive subscription work queued. Real lifecycle subscriptions and real dispatchers are preserved; no fake StateFlow values are injected.
2. Cancel fixture ViewModel/application jobs and wait for completion before closing Room. Cancellation alone raced observer-unregistration finally blocks and caused a later test's `UncaughtExceptionsBeforeTest` / closed SQLite error.
3. Await loaded episode rows before capturing navigation screenshots. A heading such as Storage exists during the empty initial state and is insufficient evidence of a populated screen.
4. Standalone Now renders with the same `bgPage` host surface provided by BackbeeNavHost. Theme installation alone does not paint a background; otherwise light text fixtures can capture against transparent black.
5. Capture after meaningful UI assertions, inspect the PNGs, and distinguish viewport screenshots from whole-scroll-content images. Settings uses multiple viewports rather than pretending the first screen contains all settings.

## Verified execution

Final command: `:core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`.
Log: `/home/samm/.local/share/backbee-toolchain/logs/carbon-button-state-final.log`.

- **Core: 65/65; Android: 179/179; zero failures, errors or skips.**
- Carbon contrast: 76/76, including the field-specific link token used in rewind settings.
- Carbon interaction: 12/12; playback controls: 6/6; field accessibility: 4/4.
- Button interaction states: 22/22; actual hover/press input verifies both normal
  and destructive outline fills/ink in both themes. Disabled behavior, idle
  restoration, and filled-button behavior are also covered.
- Destination/navigation tests: 19/19; component screenshot tests: 18/18.
- Existing Room/bulk marking tests: 22/22.
- Source guards: 9/9 (separate from the native suite).
- Debug APK packaged and APK Signature Scheme v2 verified.
- Lint: no new findings relative to the pristine source baseline. Existing baseline
  has 58 errors, 4 fatal findings and 13 warnings; a passing debug build is not a
  claim that the project is release-ready.

The formerly failing Settings field contrast now uses `textAccentOnField`;
Shelf Remove has the same weight as Activate/Recap, and the retained visibility,
48dp bounds, dialog open/cancel assertions pass. No valid test is suppressed.

There are **66 recorded PNGs**: 34 destination/navigation viewports, 18
component/form/dialog/large-text renderings and 14 button interaction renderings. Every destination is rendered in
both themes; Settings has additional lower-section viewports. These are seeded
local fixtures, not personal podcast data. Paired previews are in
`docs/carbon-previews/`; original images are under `app/build/outputs/roborazzi/`.
Physical-device audio, Bluetooth, widget host and Android Auto testing remain
outside this Robolectric verification.
