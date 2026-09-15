package dev.backbee.ui.components

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import com.github.takahirom.roborazzi.captureRoboImage
import dev.backbee.ui.theme.BackbeeTheme
import dev.backbee.ui.theme.BackbeeColors
import dev.backbee.ui.theme.DarkColors
import dev.backbee.ui.theme.LightColors
import dev.backbee.ui.theme.backbeeColors
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercise real pointer events, rendered fill and inherited label ink. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
@OptIn(ExperimentalTestApi::class)
class CarbonButtonStatesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightOutlineHover() = checkInteraction(dark = false, danger = false, pressed = false)
    @Test fun lightOutlinePress() = checkInteraction(dark = false, danger = false, pressed = true)
    @Test fun darkOutlineHover() = checkInteraction(dark = true, danger = false, pressed = false)
    @Test fun darkOutlinePress() = checkInteraction(dark = true, danger = false, pressed = true)
    @Test fun lightDangerOutlineHover() = checkInteraction(dark = false, danger = true, pressed = false)
    @Test fun lightDangerOutlinePress() = checkInteraction(dark = false, danger = true, pressed = true)
    @Test fun darkDangerOutlineHover() = checkInteraction(dark = true, danger = true, pressed = false)
    @Test fun darkDangerOutlinePress() = checkInteraction(dark = true, danger = true, pressed = true)

    @Test fun arbitraryRedInkDoesNotImplyDangerInLightTheme() =
        checkInteraction(dark = false, danger = false, pressed = false, suppliedInk = Color.Red)
    @Test fun arbitraryRedInkDoesNotImplyDangerInDarkTheme() =
        checkInteraction(dark = true, danger = false, pressed = true, suppliedInk = Color.Red)

    @Test fun lightDisabledOutlineIgnoresPointerInput() = checkDisabled(dark = false, danger = false)
    @Test fun darkDisabledOutlineIgnoresPointerInput() = checkDisabled(dark = true, danger = false)
    @Test fun lightDisabledDangerOutlineIgnoresPointerInput() = checkDisabled(dark = false, danger = true)
    @Test fun darkDisabledDangerOutlineIgnoresPointerInput() = checkDisabled(dark = true, danger = true)

    @Test fun lightResolvedIdleColorsKeepSuppliedInkAndInteractiveOutline() = checkIdleColors(LightColors)
    @Test fun darkResolvedIdleColorsKeepSuppliedInkAndInteractiveOutline() = checkIdleColors(DarkColors)
    @Test fun lightResolvedDisabledColorsOverrideEveryVariantAndInteraction() = checkDisabledColors(LightColors)
    @Test fun darkResolvedDisabledColorsOverrideEveryVariantAndInteraction() = checkDisabledColors(DarkColors)
    @Test fun lightResolvedOutlineInteractionsHaveContrastAndPressPrecedence() = checkActiveColors(LightColors)
    @Test fun darkResolvedOutlineInteractionsHaveContrastAndPressPrecedence() = checkActiveColors(DarkColors)
    @Test fun lightResolvedFilledButtonBehaviorIsUnchanged() = checkFilledColors(LightColors)
    @Test fun darkResolvedFilledButtonBehaviorIsUnchanged() = checkFilledColors(DarkColors)

    private fun checkInteraction(dark: Boolean, danger: Boolean, pressed: Boolean, suppliedInk: Color? = null) {
        var clicks = 0
        val colors = if (dark) DarkColors else LightColors
        val idleInk = suppliedInk ?: if (danger) colors.textAlert else colors.interactive
        val activeFill = when {
            danger && pressed -> Color(0xFF750E13)
            danger -> Color(0xFFDA1E28)
            pressed -> Color(0xFF002D9C)
            else -> Color(0xFF0F62FE)
        }
        compose.setContent {
            BackbeeTheme(darkTheme = dark) {
                CarbonOutlineButton({ clicks++ }, Modifier.testTag("button"), contentColor = idleInk) {
                    // A caller's explicit color must not defeat active/disabled control ink.
                    Label("Action", color = backbeeColors.textPrimary)
                }
            }
        }
        assertEquals(idleInk, labelInk())
        val button = compose.onNodeWithTag("button")
        if (pressed) button.performTouchInput { down(center) }
        else button.performMouseInput { enter(center) }
        compose.waitForIdle()
        assertEquals("Active outline label must be white", Color.White, labelInk())
        val name = "${if (dark) "dark" else "light"}-${if (danger) "danger" else "outline"}-${if (pressed) "pressed" else "hover"}${if (suppliedInk != null) "-custom" else ""}"
        val renderedFill = renderedFill(name)
        assertEquals("Native $name fill", activeFill.toArgb(), renderedFill.toArgb())
        val ratio = (labelInk().luminance() + 0.05f) / (renderedFill.luminance() + 0.05f)
        println("BUTTON CONTRAST $name: $ratio:1")
        assertTrue("$name contrast $ratio must be >= 4.5:1", ratio >= 4.5f)
        compose.runOnIdle { assertEquals(0, clicks) }
        if (pressed) button.performTouchInput { up() }
        else button.performMouseInput { exit(Offset(-10f, -10f)) }
        compose.waitForIdle()
        assertEquals("Idle ink restored after interaction", idleInk, labelInk())
        compose.runOnIdle { assertEquals(if (pressed) 1 else 0, clicks) }
    }

    private fun checkDisabled(dark: Boolean, danger: Boolean) {
        val colors = if (dark) DarkColors else LightColors
        var clicks = 0
        compose.setContent {
            BackbeeTheme(darkTheme = dark) {
                CarbonOutlineButton({ clicks++ }, Modifier.testTag("button"), enabled = false,
                    contentColor = if (danger) colors.textAlert else colors.interactive) {
                    Label("Action", color = colors.textAlert)
                }
            }
        }
        val button = compose.onNodeWithTag("button").assertIsNotEnabled()
        button.performMouseInput { enter(center) }
        compose.waitForIdle()
        assertEquals(colors.textDisabled, labelInk())
        button.performMouseInput { exit(Offset(-10f, -10f)) }
        button.performTouchInput { down(center) }
        compose.waitForIdle()
        assertEquals(colors.textDisabled, labelInk())
        assertEquals(colors.disabled.toArgb(), renderedFill("${if (dark) "dark" else "light"}-${if (danger) "danger" else "outline"}-disabled").toArgb())
        button.performTouchInput { up() }
        compose.runOnIdle { assertEquals(0, clicks) }
    }

    private fun checkIdleColors(colors: BackbeeColors) {
        CarbonButtonVariant.entries.forEach { variant ->
            listOf(colors.interactive, colors.textAlert, Color.Magenta).forEach { ink ->
                assertEquals(CarbonButtonStateColors(colors.bgPanel, ink, colors.interactive),
                    resolveCarbonButtonColors(colors, colors.bgPanel, ink, variant,
                        enabled = true, pressed = false, hovered = false))
            }
        }
    }

    private fun checkDisabledColors(colors: BackbeeColors) {
        CarbonButtonVariant.entries.forEach { variant ->
            listOf(false, true).forEach { pressed ->
                listOf(false, true).forEach { hovered ->
                    assertEquals(CarbonButtonStateColors(colors.disabled, colors.textDisabled, colors.disabled),
                        resolveCarbonButtonColors(colors, colors.bgPanel, colors.textAlert, variant,
                            enabled = false, pressed = pressed, hovered = hovered))
                }
            }
        }
    }

    private fun checkActiveColors(colors: BackbeeColors) {
        listOf(CarbonButtonVariant.Outline, CarbonButtonVariant.DangerOutline).forEach { variant ->
            listOf(false to true, true to false, true to true).forEach { (pressed, hovered) ->
                val danger = variant == CarbonButtonVariant.DangerOutline
                val fill = when {
                    danger && pressed -> Color(0xFF750E13)
                    danger -> Color(0xFFDA1E28)
                    pressed -> Color(0xFF002D9C)
                    else -> Color(0xFF0F62FE)
                }
                val state = resolveCarbonButtonColors(colors, colors.bgPanel,
                    if (danger) colors.textAlert else colors.interactive, variant,
                    enabled = true, pressed = pressed, hovered = hovered)
                assertEquals(CarbonButtonStateColors(fill, Color.White, fill), state)
                assertTrue("$variant pressed=$pressed hovered=$hovered",
                    (state.ink.luminance() + 0.05f) / (state.fill.luminance() + 0.05f) >= 4.5f)
            }
        }
    }

    private fun checkFilledColors(colors: BackbeeColors) {
        listOf(false, true).forEach { pressed ->
            listOf(false, true).forEach { hovered ->
                val fill = when {
                    pressed -> Color.Black.copy(alpha = 0.22f).compositeOver(colors.accentPrimary)
                    hovered -> Color.Black.copy(alpha = 0.10f).compositeOver(colors.accentPrimary)
                    else -> colors.accentPrimary
                }
                assertEquals(CarbonButtonStateColors(fill, colors.onAccentPrimary, colors.interactive),
                    resolveCarbonButtonColors(colors, colors.accentPrimary, colors.onAccentPrimary,
                        CarbonButtonVariant.Filled, enabled = true, pressed = pressed, hovered = hovered))
            }
        }
    }

    private fun renderedFill(name: String): Color {
        val output = File("build/outputs/roborazzi/button-states/$name.png")
        requireNotNull(output.parentFile).mkdirs()
        compose.onNodeWithTag("button").captureRoboImage(output.path)
        val bitmap = requireNotNull(BitmapFactory.decodeFile(output.path))
        // Sample empty button interior, clear of text, outline and focus ring.
        return Color(bitmap.getPixel(bitmap.width - 12, bitmap.height / 2))
    }

    private fun labelInk(): Color {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Action", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single().layoutInput.style.color
    }
}
