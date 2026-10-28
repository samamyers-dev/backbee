# Backbee — Carbon for native Android

Backbee implements an **IBM Carbon Design System adaptation in Jetpack Compose**.
It is not a WebView and does not claim to be an official IBM Android component
package. Compose Foundation supplies native input/accessibility; Material 3 is
an implementation detail with Carbon colors, shapes and all type slots mapped.
Playback, feeds, persistence, downloads and backup behavior are not redesigned.

## Sources

- [Carbon color tokens](https://carbondesignsystem.com/elements/color/tokens/)
- [Carbon themes](https://carbondesignsystem.com/elements/themes/overview/)
- [Carbon typography](https://carbondesignsystem.com/elements/typography/type-sets/)
- [Carbon buttons](https://carbondesignsystem.com/components/button/style/)
- [IBM Plex](https://github.com/IBM/plex), bundled under the SIL Open Font License
  (see `docs/licenses/IBM-Plex-OFL.txt`). Fonts require no runtime connection.

## Visual foundation

Backbee ships with **four user-selectable color palettes** (`BackbeePalette` in
`ui/theme/Theme.kt`), switchable in Settings → Color palette and persisted in
DataStore:

1. **Parchment** *(default)* — Warm sandstone paper (`#F4EFE6`) and espresso
   ink (`#231E1A`) with terracotta (`#9A4B1A`), ochre (`#C99653`), and sage
   green (`#1E6641`).
2. **Carbon** — Clean graphite (`#F4F4F4` / `#161616`) with IBM blue
   (`#0F62FE` / `#5C97FF`) and functional green (`#196F31` / `#42BE65`).
3. **Fjord** — Cool limestone (`#EEF2F6`) and deep slate (`#0F1720`) with
   oceanic teal (`#0E5A7A` / `#48B5DE`) and seafoam (`#166544` / `#48C792`).
4. **Pressroom** — Botanical paper (`#F4F1E8`) and dark pressroom (`#101A13`)
   with press green (`#0F7A4A` / `#2FA96C`) and balanced sage tones.

Every text token in all four palettes (light and dark) clears 4.5:1 WCAG
contrast on every contextual surface it is drawn on (`bgPage`, `bgPanel`,
`layer02`, `field`, `layerSelected`, `bgInverse`), and every interactive fill
clears 4.5:1 under its own ink; `CarbonContrastTest` verifies all 8 theme
variants (4 palettes × light/dark).

Use semantic tokens from `ui/theme/Theme.kt`, not raw hex in screen code.
Filled actions use `accentPrimary` with `onAccentPrimary`; links use
`textAccent`. Outline buttons fill with `accentPrimary` on hover and
`accentPrimaryActive` on press, danger with `accentAlertHover` and
`accentAlertActive`, each under its own ink (`onAccentPrimary`,
`onAccentAlert`); `textOnColor` is the ink for the primary family only. Inverse
diagnostic panels use explicit inverse text tokens rather than assuming one
colour works on both themes.

### The brand detail

`brandAccent` colours the rule down the side of a readout and the visualiser
bars over artwork. Plex Mono remains for timestamps, episode indexes and
diagnostics. The archive-spine metaphor remains. Hard offset shadows, page
grids, heavy outlines, black-weight headings and forced uppercase stay retired:
each palette sits on the Carbon component structure.

### Typography and spacing

IBM Plex Sans is bundled in regular, light and semibold; IBM Plex Mono in regular.
Productive body text is 14/20sp, expressive body text 16/24sp, labels 12/16sp or
14/18sp, component headings 16/24sp semibold, page headings 24/32sp regular,
and larger numerals use light 42/50sp or 54/64sp. Labels are sentence case.

Spacing follows Carbon's 4/8/12/16/24/32/40/48/64 scale. The standard page gutter
is 16dp. Android controls retain **at least 48dp touch targets** even where
Carbon's desktop component would be smaller. Text uses sp and layouts expand
vertically. Corners are square except Carbon's pill tags and toggle anatomy.

## Component contract

`ui/components/Carbon.kt` is the shared visual layer:

- `CarbonPanel`: a flat tile; borders are opt-in, never shadows.
- `CarbonButton`: blue primary action, left-aligned label, separate pressed,
  hover, focus and disabled states, button semantics.
- `CarbonOutlineButton`: interactive outlined tertiary action.
- `CarbonTextField`: persistent label, filled rectangular field, bottom rule,
  full focus outline, native editable text and keyboard options.
- `CarbonToggle`: 48dp target with 48×24dp track and an explicit switch role.
  Pass a null callback only when its parent owns the accessible toggle action.
- `CarbonDialog`: square modal, scrollable content and action footer.
- `CarbonProgress`: thin track with clamped accessible progress semantics.
- `CarbonDivider`: subtle 1dp separator.
- `StatusChip`: compact Carbon-style tag; state also has a textual label.
- `Readout`: restrained inverse diagnostic panel, without terminal prompt glyphs.

`ScanBar` keeps the intentional seek-on-release behavior but uses a thin neutral
track and circular thumb. Accessibility set-progress and arrow-key actions are
provided alongside touch. The persistent player uses flat transport controls.
Artwork remains square; archive rows use layered selection rather than tinted
brutalist boxes.

## Surface inventory

The migration covers Now, Archive/search/year navigation, episode detail/notes/
bulk actions, Shelf/add-show/remove confirmation, Downloads, Settings and
backup controls, completion recap, persistent playback, primary navigation,
empty states, and the Glance home-screen widget. Android Auto and system media
notifications remain host-rendered: Android owns their layout and typography,
so imposing an app-specific component system there would violate platform UI.

## Verification

Source-contract tests (no Android SDK required):

```sh
python -m unittest discover -s tests -p 'test_carbon*.py'
```

Native build and tests (JDK 17 and Android API 36 SDK required):

```sh
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Compose/Robolectric tests live under `app/src/test/java/dev/backbee/ui/`.
Roborazzi records actual Compose PNGs to `app/build/outputs/roborazzi/` in light
and dark modes. They are review artifacts, **not pixel-diff golden baselines**.
Source-contract tests do not replace compilation, interaction tests or visual
inspection. See `docs/CARBON_REVIEW.md` for the actual execution results and
remaining limitations of this migration.

## Existing behavior worth preserving

Now follows the loaded episode before the resume bookmark; auto-advance can
momentarily differ from the bookmark, so detour feedback waits for confirmation.
Bulk marking excludes its anchor and retains exact prior positions/timestamps
for undo. The mini-player is outside the navigation host and survives tab changes.
One show is active at a time; other bookmarks are frozen rather than promoted
implicitly. No design-system change should alter those behaviors.
