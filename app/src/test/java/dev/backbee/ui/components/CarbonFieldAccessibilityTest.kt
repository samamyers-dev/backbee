package dev.backbee.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import dev.backbee.ui.theme.BackbeeTheme
import dev.backbee.ui.theme.backbeeColors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class CarbonFieldAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun explicitAccessibleLabelNamesEditableInputWithoutReplacingValueOrCallback() {
        val changes = mutableListOf<String>()
        compose.setContent {
            BackbeeTheme(darkTheme = false) {
                var value by remember { mutableStateOf("Saved note") }
                CarbonTextField(value, { value = it; changes += it },
                    label = { Label("Something worth remembering") },
                    accessibleLabel = "Episode note")
            }
        }
        val input = compose.onNode(hasSetTextAction() and hasContentDescription("Episode note"))
        input.assertTextEquals("Saved note").performTextReplacement("Revised note")
        input.assertTextEquals("Revised note")
        compose.onNodeWithText("Something worth remembering").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("Revised note"), changes) }
    }

    @Test fun errorSemanticsUseCustomMessageAndDisappearWhenCorrected() {
        val invalid = mutableStateOf(true)
        compose.setContent {
            BackbeeTheme(darkTheme = false) {
                CarbonTextField("bad feed", {}, accessibleLabel = "Feed URL",
                    isError = invalid.value, errorMessage = "Enter a valid feed URL")
            }
        }
        val input = compose.onNode(hasSetTextAction())
        input.assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Enter a valid feed URL"))
        compose.runOnIdle { invalid.value = false }
        input.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
        input.assertContentDescriptionEquals("Feed URL").assertTextEquals("bad feed")
    }

    @Test fun defaultErrorMessageAlsoReachesDisabledInput() {
        compose.setContent {
            BackbeeTheme(darkTheme = false) {
                CarbonTextField("bad feed", {}, enabled = false, isError = true,
                    accessibleLabel = "Feed URL")
            }
        }
        compose.onNodeWithContentDescription("Feed URL").assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Invalid input"))
            .assertTextEquals("bad feed")
    }

    @Test fun destructiveOutlineUsesRequestedInkButDisabledInkStillOverridesChildColor() {
        var calls = 0
        var alert = androidx.compose.ui.graphics.Color.Unspecified
        var disabled = androidx.compose.ui.graphics.Color.Unspecified
        compose.setContent {
            BackbeeTheme(darkTheme = false) {
                val colors = backbeeColors
                alert = colors.textAlert
                disabled = colors.textDisabled
                Column {
                    CarbonOutlineButton({ calls++ }, contentColor = colors.textAlert) {
                        Label("Delete", color = colors.textPrimary)
                    }
                    CarbonOutlineButton({ calls++ }, enabled = false, contentColor = colors.textAlert) {
                        Label("Unavailable delete", color = colors.textAlert)
                    }
                }
            }
        }
        fun textColor(label: String): androidx.compose.ui.graphics.Color {
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            return results.single().layoutInput.style.color
        }
        assertEquals(alert, textColor("Delete"))
        assertEquals(disabled, textColor("Unavailable delete"))
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Unavailable delete").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, calls) }
    }
}
