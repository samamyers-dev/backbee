package dev.backbee.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.Locale

/**
 * WCAG normal-text contrast for every readable semantic ink on every regular
 * contextual surface, plus explicit inverse/action pairs. Disabled text and
 * decorative brand/border tokens are deliberately not normal-text contracts.
 *
 * Each pair is its own test: one bad pair must not hide failures in another mode.
 * This uses actual BackbeeColors values, not a copied palette or a source guard.
 */
@RunWith(Parameterized::class)
class CarbonContrastTest(private val pair: ContrastPair) {
    data class ContrastPair(val label: String, val foreground: Color, val background: Color) {
        override fun toString() = label
    }

    @Test fun readableTextMeetsFourPointFiveToOne() {
        val (label, foreground, background) = pair
        val ink = foreground.compositeOver(background)
        val lighter = maxOf(ink.luminance(), background.luminance())
        val darker = minOf(ink.luminance(), background.luminance())
        val ratio = (lighter + 0.05f) / (darker + 0.05f)
        println("CONTRAST $label: ${String.format(Locale.ROOT, "%.3f", ratio)}:1")
        assertTrue("$label: ${String.format(Locale.ROOT, "%.3f", ratio)}:1; expected >= 4.5:1", ratio >= 4.5f)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun pairs(): Collection<Array<Any>> = buildList {
            listOf("light" to LightColors, "dark" to DarkColors).forEach { (mode, colors) ->
                val surfaces = mapOf(
                    "bgPage" to colors.bgPage,
                    "bgPanel" to colors.bgPanel,
                    "layer02" to colors.layer02,
                )
                val readableInk = mapOf(
                    "textPrimary" to colors.textPrimary,
                    "textMuted" to colors.textMuted,
                    "textAccent" to colors.textAccent,
                    "interactive" to colors.interactive,
                    "textFunctional" to colors.textFunctional,
                    "textSecondary" to colors.textSecondary,
                    "textAlert" to colors.textAlert,
                )
                readableInk.forEach { (inkName, ink) ->
                    surfaces.forEach { (surfaceName, surface) ->
                        add(arrayOf(ContrastPair("$mode/$inkName on $surfaceName", ink, surface)))
                    }
                }
                // Fields carry input/placeholder text. Settings' rewind segments
                // use a higher-contrast field-specific link grade; test the actual
                // pairing alongside regular editable-field text.
                mapOf(
                    "textPrimary" to colors.textPrimary,
                    "textMuted" to colors.textMuted,
                    "textAccentOnField" to colors.textAccentOnField,
                ).forEach { (name, ink) ->
                    add(arrayOf(ContrastPair("$mode/$name on field", ink, colors.field)))
                }
                // EpisodeRow selected title, metadata and link treatment.
                mapOf(
                    "textPrimary" to colors.textPrimary,
                    "textMuted" to colors.textMuted,
                    "textAccentSelected" to colors.textAccentSelected,
                ).forEach { (name, ink) ->
                    add(arrayOf(ContrastPair("$mode/$name on layerSelected", ink, colors.layerSelected)))
                }
                mapOf(
                    "textInverse" to colors.textInverse,
                    "onInverseFunctional" to colors.onInverseFunctional,
                    "onInverseAlert" to colors.onInverseAlert,
                ).forEach { (name, ink) -> add(arrayOf(ContrastPair("$mode/$name on bgInverse", ink, colors.bgInverse))) }
                // Every fill with the ink that is actually drawn on it. The palette
                // gives each accent its own on-colour (paper on Press Green,
                // near-black on the lifted green, plum on fluoro), so a single ink
                // is not a contract here; the hover and press fills are, because
                // those are what an outline button renders under its label.
                listOf(
                    Triple("onAccentPrimary on accentPrimary", colors.onAccentPrimary, colors.accentPrimary),
                    Triple("onAccentPrimary on accentPrimaryActive", colors.onAccentPrimary, colors.accentPrimaryActive),
                    Triple("onAccentSecondary on accentSecondary", colors.onAccentSecondary, colors.accentSecondary),
                    Triple("onAccentSecondary on accentInfo", colors.onAccentSecondary, colors.accentInfo),
                    Triple("onAccentAlert on accentAlert", colors.onAccentAlert, colors.accentAlert),
                    Triple("onAccentAlert on accentAlertHover", colors.onAccentAlert, colors.accentAlertHover),
                    Triple("onAccentAlert on accentAlertActive", colors.onAccentAlert, colors.accentAlertActive),
                    Triple("textOnColor on accentPrimary", colors.textOnColor, colors.accentPrimary),
                    Triple("textOnColor on accentPrimaryActive", colors.textOnColor, colors.accentPrimaryActive),
                    Triple("textOnColor on accentFunctional", colors.textOnColor, colors.accentFunctional),
                ).forEach { (name, ink, surface) -> add(arrayOf(ContrastPair("$mode/$name", ink, surface))) }
            }
        }
    }
}
