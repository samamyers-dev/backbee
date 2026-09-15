"""Source-contract guards runnable without the Android SDK; UI tests live in app/."""
import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
UI = ROOT / 'app/src/main/java/dev/backbee/ui'


class CarbonFoundationTest(unittest.TestCase):
    def test_carbon_theme_and_local_plex_replace_brutalist_tokens(self):
        theme = (UI / 'theme/Theme.kt').read_text()
        for token in ['0xFFF4F4F4', '0xFF161616', '0xFF0F62FE', '0xFF78A9FF', '0xFFC99653']:
            self.assertIn(token, theme)
        for font in ['ibm_plex_sans_regular', 'ibm_plex_sans_semibold', 'ibm_plex_sans_light', 'ibm_plex_mono_regular']:
            self.assertIn('R.font.' + font, theme)
            self.assertTrue((ROOT / 'app/src/main/res/font' / (font + '.ttf')).is_file())
        self.assertNotIn('FontWeight.Black', theme)
        self.assertNotIn('object Shadow', theme)


    def test_controls_have_carbon_states_and_accessibility(self):
        path = UI / 'components/Carbon.kt'
        self.assertTrue(path.exists(), 'Carbon component layer is missing')
        source = path.read_text()
        for contract in ['fun CarbonButton(', 'fun CarbonPanel(', 'fun CarbonTextField(',
                         'fun CarbonToggle(', 'fun CarbonDialog(', 'Role.Button',
                         'collectIsFocusedAsState', 'collectIsPressedAsState',
                         'progressBarRangeInfo', 'textDisabled']:
            self.assertIn(contract, source)
        for retired in ['shadowColor', 'Modifier.brutal', '.offset(', 'text.uppercase()']:
            self.assertNotIn(retired, source)


    def test_player_surfaces_are_flat_and_seek_is_accessible(self):
        scan = (UI / 'components/ScanBar.kt').read_text()
        self.assertIn('setProgress', scan)
        self.assertIn('progressBarRangeInfo', scan)
        self.assertIn('onKeyEvent', scan)
        self.assertNotIn('.height(44.dp)', scan)
        self.assertNotIn('.border(Stroke.divider', (UI / 'components/NowPlayingBar.kt').read_text())
        self.assertNotIn('.border(Stroke.divider', (UI / 'components/Common.kt').read_text())


if __name__ == '__main__':
    unittest.main()
