package dev.backbee.ui.components

import android.app.Application
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.backbee.ui.theme.BackbeeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real Compose input/semantics tests; Application avoids BackbeeApp startup work. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class CarbonInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun primaryButtonCallsBackExactlyOnceAndHasButtonRole() = checkButton(outline = false)
    @Test fun outlineButtonCallsBackExactlyOnceAndHasButtonRole() = checkButton(outline = true)
    @Test fun disabledPrimaryButtonDoesNotCallBack() = checkDisabledButton(outline = false)
    @Test fun disabledOutlineButtonDoesNotCallBack() = checkDisabledButton(outline = true)

    private fun checkButton(outline: Boolean) {
        var calls = 0
        themed { TestButton(outline, enabled = true, onClick = { calls++ }) }
        compose.onNodeWithTag("action")
            .assertIsEnabled()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    private fun checkDisabledButton(outline: Boolean) {
        var calls = 0
        themed { TestButton(outline, enabled = false, onClick = { calls++ }) }
        compose.onNodeWithTag("action")
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun togglePublishesCheckedStateAndUpdatesThroughCallback() {
        val changes = mutableListOf<Boolean>()
        themed {
            var checked by remember { mutableStateOf(false) }
            CarbonToggle(checked, { checked = it; changes += it }, Modifier.testTag("toggle"))
        }
        compose.onNodeWithTag("toggle")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertIsOff().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("toggle").assertIsOn().performClick()
        compose.onNodeWithTag("toggle").assertIsOff()
        compose.runOnIdle { assertEquals(listOf(true, false), changes) }
    }

    @Test fun disabledToggleRetainsCheckedStateWithoutCallingBack() {
        var calls = 0
        themed { CarbonToggle(true, { calls++ }, Modifier.testTag("toggle"), enabled = false) }
        compose.onNodeWithTag("toggle").assertIsOn().assertIsNotEnabled()
            .performTouchInput { click() }
        compose.onNodeWithTag("toggle").assertIsOn()
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun decorativeToggleLeavesOneAccessibleSwitchOnItsParent() {
        var calls = 0
        themed {
            var checked by remember { mutableStateOf(true) }
            Row(Modifier.testTag("setting").toggleable(checked, role = Role.Switch,
                onValueChange = { checked = it; calls++ })) {
                Label("Keep after playing")
                CarbonToggle(checked, null, Modifier.testTag("decoration"))
            }
        }
        val switch = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
        compose.onAllNodes(switch, useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag("decoration", useUnmergedTree = true)
            .assertHasNoClickAction()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
        compose.onNodeWithTag("setting").assertIsOn().performClick()
        compose.onNodeWithTag("setting").assertIsOff()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun textFieldEditsActualInputAndKeepsPersistentLabel() {
        val changes = mutableListOf<String>()
        themed {
            var value by remember { mutableStateOf("Original note") }
            CarbonTextField(value, { value = it; changes += it },
                modifier = Modifier.testTag("field"), label = { Label("Episode note") })
        }
        // The supplied modifier belongs to the field's Column, not BasicTextField.
        val input = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("field")))
        input.assertTextEquals("Original note").performTextReplacement("Revised note")
        input.assertTextEquals("Revised note")
        compose.onNodeWithText("Episode note").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("Revised note"), changes) }
    }

    @Test fun textFieldAccessibleInputIncludesItsPersistentLabel() {
        themed {
            CarbonTextField("Saved note", {}, label = { Label("Episode note") },
                accessibleLabel = "Episode note")
        }
        // A visible sibling alone does not name the editable accessibility node.
        // Accept either merged label text or a content description, without
        // forcing one production implementation.
        compose.onNode(hasSetTextAction()).assert(
            hasText("Episode note") or hasContentDescription("Episode note"),
        )
    }

    @Test fun disabledTextFieldIsNotEditableByTouch() {
        var calls = 0
        themed {
            CarbonTextField("Saved note", { calls++ }, enabled = false,
                label = { Label("Episode note") })
        }
        compose.onNodeWithText("Saved note").assertIsNotEnabled()
            .performTouchInput { click() }
        compose.onNodeWithText("Saved note")
            // Disabled BasicTextField omits Focused rather than publishing false.
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused) or
                SemanticsMatcher.expectValue(SemanticsProperties.Focused, false))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.IsEditable, false))
            .assertTextEquals("Saved note")
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun progressReportsClampedFiniteRangeForEveryBoundary() {
        val cases = listOf(
            -1f to 0f, 0f to 0f, 0.375f to 0.375f, 1f to 1f, 2f to 1f,
            Float.NaN to 0f, Float.POSITIVE_INFINITY to 0f, Float.NEGATIVE_INFINITY to 0f,
        )
        themed {
            Column {
                cases.forEachIndexed { index, (input, _) ->
                    CarbonProgress(input, Modifier.testTag("progress-$index"))
                }
            }
        }
        cases.forEachIndexed { index, (_, expected) ->
            compose.onNodeWithTag("progress-$index").assert(
                SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(expected, 0f..1f)),
            )
        }
    }

    @Test fun dialogActionsReachTheirCallbacksAndDismissTheRealDialog() {
        var confirms = 0
        var cancels = 0
        themed {
            var visible by remember { mutableStateOf(true) }
            Column {
                CarbonButton({ visible = true }) { Label("Open dialog") }
                if (visible) CarbonDialog(
                    onDismissRequest = { visible = false },
                    title = { Label("Remove download?") },
                    text = { Mono("Listening progress stays on this device.") },
                    confirmButton = {
                        CarbonButton({ confirms++; visible = false }) { Label("Remove") }
                    },
                    dismissButton = {
                        CarbonOutlineButton({ cancels++; visible = false }) { Label("Cancel") }
                    },
                )
            }
        }
        compose.onNodeWithText("Remove download?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Remove download?").assertDoesNotExist()
        compose.onNodeWithText("Open dialog").performClick()
        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithText("Remove download?").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, confirms); assertEquals(1, cancels) }
    }

    private fun themed(content: @Composable () -> Unit) {
        compose.setContent { BackbeeTheme(darkTheme = false) { content() } }
    }
}

@Composable
private fun TestButton(outline: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val modifier = Modifier.width(240.dp).testTag("action")
    if (outline) CarbonOutlineButton(onClick, modifier, enabled, minHeight = 24.dp) { Label("Continue") }
    else CarbonButton(onClick, modifier, enabled, minHeight = 24.dp) { Label("Continue") }
}
