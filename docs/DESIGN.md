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

The colours are the **FREE THEM.** palette, from the "Free Them. — Home"
refresh (24 Sep 2026), carried on the Carbon component structure below.
Token names in `ui/theme/Theme.kt` stay Carbon's so screens did not change;
every value is the design system's or derived from it by the mix noted.

Raw swatches:

| Name | Hex | Role in the design |
|---|---|---|
| Paper | `#F4F1E8` | light canvas; text and borders on dark |
| Pressroom | `#101A13` | dark canvas |
| Bottle | `#1B2E1F` | ink, borders and shadows on light; panel on dark |
| Press Green | `#0F7A4A` | primary and functional accent |
| Press Green, lifted | `#2FA96C` | the same accent on the dark ground |
| Fluoro Pink | `#FF4B7D` | secondary, info, and the brand pop |
| Acid | `#D6FF3F` | highlight (unused in the app so far) |
| Amber | `#F2B705` | warning |
| Vermilion | `#E8442E` | alert |
| Field | `#EAE6DB` | inputs and the recessed layer |

Semantic tokens:

| Token | Light | Dark |
|---|---|---|
| Page | paper | pressroom |
| Panel / tile | `#FBF9F3` paper lifted toward white | bottle |
| Layer 02, field | field | `#2C3E2F` paper 8% over bottle |
| Selected layer | field (the design's row hover) | `#3B4A3D` |
| Text primary | bottle | paper |
| Text muted | `#5C685B` bottle 70% | `#C0C0B8` paper 80% |
| Border subtle | `#D1D2C8` bottle 16% | `#343C35` paper 16% |
| Border strong | bottle | `#8D9088` paper 55% |
| Interactive text | `#0B5E38` green deep | `#45C182` green bright |
| Interactive text on fields, selected rows | `#094A2C` | `#6ED49B` |
| Focus | Press Green | paper |
| Success text | `#0B5E38` | `#45C182` |
| Warning text | `#7A5D00` amber ink | amber |
| Error text | `#B8301E` vermilion ink | `#F78D7C` vermilion lifted twice |
| Primary button | Press Green / paper; pressed `#0B5E38` | lifted green / `#06170E`; pressed `#45C182` |
| Secondary, info | fluoro / `#2B0A16` | same |
| Danger button | `#B8301E` / paper; pressed `#8F2416` | vermilion / `#06170E`; pressed `#F26B57` |
| Brand accent | fluoro | fluoro |

Every text token clears 4.5:1 on every surface it is drawn on, including
the recessed field layer and the selected row, and every fill clears it
under its own ink; `CarbonContrastTest` holds that line. Where a raw swatch
fell short as text it was pressed a step in the direction of its ground:
Press Green is 4.3:1 on the field layer, the lifted green 3.9:1 on the
raised dark layer, amber 4.49:1 on paper, vermilion 3.5:1 on paper and 3.7:1
on the raised dark layer. The fills keep the swatches except light-mode
alert, where paper on vermilion is 3.5:1 and the ink grade is used for the
fill too.

Use semantic tokens from `ui/theme/Theme.kt`, not raw hex in screen code.
Filled actions use `accentPrimary` with `onAccentPrimary`; links use
`textAccent`. Outline buttons fill with `accentPrimary` on hover and
`accentPrimaryActive` on press, danger with `accentAlertHover` and
`accentAlertActive`, each under its own ink (`onAccentPrimary`,
`onAccentAlert`); `textOnColor` is the ink for the primary family only. Inverse diagnostic
panels use explicit inverse text tokens rather than assuming one colour works
on both themes.

### The brand detail

Fluoro Pink is **`brandAccent`**: the rule down the side of a readout, the
visualiser bars over artwork, the launcher ribbon. The design's instrument
bars are bottle with the live bar in fluoro, which is where that comes from.
Plex Mono remains for timestamps, episode indexes and diagnostics. The
archive-spine metaphor remains. Hard offset shadows, page grids, heavy
outlines, black-weight headings and forced uppercase stay retired: this is
the palette on the Carbon structure, not the brutalist form.

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
