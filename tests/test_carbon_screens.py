"""Executable source contracts; Android rendering/compilation is verified separately."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/dev/backbee"
SCREENS = sorted((JAVA / "ui").glob("*/*Screen.kt"))


def source(relative):
    return (JAVA / relative).read_text()


class CarbonScreensTest(unittest.TestCase):
    def test_form_controls_use_shared_carbon_contracts(self):
        expected = {
            "ui/archive/ArchiveScreen.kt": ("CarbonTextField(", "viewModel::setQuery", "indexForEpisodeNumber", "imeAction = ImeAction.Done"),
            "ui/archive/EpisodeDetailScreen.kt": ("CarbonTextField(", "CarbonToggle(", "viewModel::setNoteDraft", "viewModel::saveNote", "viewModel.setKeepAfterPlaying(it)", "role = Role.Switch"),
            "ui/settings/SettingsScreen.kt": ("CarbonToggle(", "onCheckedChange = null", "role = Role.Switch", "viewModel::confirmRestore", "viewModel::cancelRestore"),
            "ui/shelf/ShelfScreen.kt": ("CarbonTextField(", "CarbonDialog(", "viewModel.removeShow(showId)", "pendingRemoval = null"),
        }
        for path, contracts in expected.items():
            text = source(path)
            with self.subTest(path=path):
                self.assertNotRegex(text, r"\b(?:OutlinedTextField|AlertDialog|Switch|TextButton)\(")
                for contract in contracts:
                    self.assertIn(contract, text)
                self.assertNotIn("placeholder =", text, "Fields need persistent visible labels")

    def test_destinations_are_flat_sentence_case_sans_surfaces(self):
        self.assertEqual(len(SCREENS), 7)
        for path in SCREENS:
            text = path.read_text()
            with self.subTest(screen=path.name):
                self.assertNotRegex(text, r"shadow\s*=|borderWidth\s*=|\.border\(|Stroke\.(?:thick|divider)|\.uppercase\(")
                self.assertNotRegex(text, r"BackbeeType\.mono|\bMono\(", "Use Plex Sans for screen UI, not terminal-style labels")
                self.assertNotRegex(text, r"minHeight = (?:40|44)\.dp")
                # Ignore developer comments, but catch all-caps UI prose.
                code = re.sub(r"(?m)^\s*//.*$", "", text)
                self.assertNotRegex(code, r'\b[A-Z]{3,} [A-Z]{3,}\b')
                self.assertIn("Dimens.gutter", text)
        now = source("ui/now/NowScreen.kt")
        self.assertIn(".height(8.dp)", now, "Archive progress should be a restrained Carbon track")
        self.assertIn("progressBarRangeInfo", now, "Custom archive track exposes progress to accessibility")
        self.assertIn("player::seekTo", now)
        self.assertIn("player.playEpisode(target.id)", now)
        self.assertIn("viewModel::markCurrentPlayedAndAdvance", now)
        self.assertIn("state.isDetour && confirmedDetour", now)

    def test_navigation_keeps_destinations_with_accessible_blue_indicator(self):
        text = source("ui/BackbeeNavHost.kt")
        for route in ("NOW", "ARCHIVE", "SHELF", "SETTINGS", "DOWNLOADS", "EPISODE", "COMPLETION"):
            self.assertRegex(text, rf"(?:composable\(|route = )Routes\.{route}")
        for label in ("Now", "Archive", "Shelf", "Downloads", "Settings"):
            self.assertIn(f'to "{label}"', text)
        self.assertIn(".selectableGroup()", text)
        self.assertIn("role = Role.Tab", text)
        self.assertIn("selected = selected", text)
        self.assertIn(".heightIn(min = 48.dp)", text)
        self.assertIn(".height(2.dp)", text)
        self.assertIn(".background(colors.interactive)", text)
        self.assertNotIn(".background(if (selected) colors.accentPrimary", text)
        self.assertNotIn("Stroke.thick", text)
        self.assertIn("onTogglePlay = player::togglePlayPause", text)
        self.assertIn("onSkipBack = player::skipBack", text)
        self.assertIn("onSkipForward = player::skipForward", text)
        self.assertIn("wasLoaded && playingId != null", text)

    def test_widget_and_android_window_use_carbon_tokens(self):
        import xml.etree.ElementTree as ET
        values = ROOT / "app/src/main/res/values"
        colors = {node.attrib['name']: node.text.lower() for node in ET.parse(values / 'colors.xml').getroot()}
        self.assertEqual(colors["window_background"], "#f4f1e8")
        self.assertEqual(colors["ic_launcher_background"], "#101a13")
        self.assertEqual(colors["widget_background"], "#fbf9f3")
        self.assertEqual(colors["interactive"], "#0f7a4a")
        night = {node.attrib['name']: node.text.lower() for node in ET.parse(values.parent / 'values-night' / 'colors.xml').getroot()}
        self.assertEqual(night["window_background"], "#101a13")
        self.assertEqual(night["interactive"], "#2fa96c")
        theme = (values / "themes.xml").read_text()
        self.assertIn('name="android:colorAccent">@color/interactive', theme)
        self.assertIn('name="android:fontFamily">sans', theme)
        widget = source("widget/BackbeeWidget.kt")
        self.assertIn("0xFF0F7A4A", widget)
        self.assertIn("0xFF2FA96C", widget)
        self.assertIn(".height(48.dp)", widget)
        self.assertIn(".padding(16.dp)", widget)
        self.assertIn("FontFamily.SansSerif", widget)
        self.assertNotIn("GlanceTheme.colors", widget, "Launcher colors must not override the Carbon palette")
        self.assertIn("actionRunCallback<TogglePlaybackAction>()", widget)
        self.assertIn("actionRunCallback<SkipForwardAction>()", widget)
        self.assertIn("WidgetPlaybackCommands.toggle(context)", widget)
        self.assertIn("WidgetPlaybackCommands.skipForward(context)", widget)

    def test_fields_match_parent_api_and_buttons_inherit_state_colors(self):
        for path in SCREENS:
            text = path.read_text()
            with self.subTest(screen=path.name):
                self.assertNotRegex(text, r'label = "', "CarbonTextField takes a composable label")
                self.assertNotIn("keyboardActions =", text, "Use native Done dismissal with the shared field API")
                # Each action's immediate content must honor LocalContentColor or
                # Carbon's Label; raw Text with a fixed color masks disabled state.
                for match in re.finditer(r'(?:CarbonButton|CarbonOutlineButton)\(', text):
                    start = match.end()
                    depth = 1
                    for i in range(start, len(text)):
                        if text[i] == '(':
                            depth += 1
                        elif text[i] == ')':
                            depth -= 1
                            if depth == 0:
                                rest = text[i + 1:].lstrip()
                                self.assertNotRegex(rest, r'^\{\s*Text\(')
                                break

    def test_compact_actions_remain_labeled_mobile_targets(self):
        detail = source("ui/archive/EpisodeDetailScreen.kt")
        self.assertRegex(detail, r'\.clickable\(onClick = onBack\)\s*\.heightIn\(min = 48\.dp\)')
        archive = source("ui/archive/ArchiveScreen.kt")
        self.assertIn(".width(48.dp)", archive)
        self.assertIn(".heightIn(min = 48.dp)", archive)
        settings = source("ui/settings/SettingsScreen.kt")
        self.assertIn('StepKey("−", "Decrease $label", onDown)', settings)
        self.assertIn('StepKey("+", "Increase $label", onUp)', settings)
        self.assertIn("contentDescription = actionLabel", settings)
        self.assertIn(".width(48.dp)", settings)


if __name__ == "__main__":
    unittest.main()
