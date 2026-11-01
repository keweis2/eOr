package com.gamelaunch.frontend.ui.screen.detail

import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class DetailFocusTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ready = mutableStateOf(false)
    private val dialog = mutableStateOf(false)
    private val child = FocusRequester()
    private var launches = 0
    private lateinit var focusManager: FocusManager

    private fun content() {
        rule.setContent {
            focusManager = LocalFocusManager.current
            val modifier = rememberDetailFocusModifier(ready.value)
            if (ready.value) {
                Box(Modifier.size(200.dp).testTag("page").then(modifier).onKeyEvent {
                    if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionCenter) {
                        launches++
                        true
                    } else false
                }) {
                    Box(Modifier.size(40.dp).testTag("child").focusRequester(child).focusable())
                }
            }
            if (dialog.value) {
                Dialog(onDismissRequest = { dialog.value = false }) {
                    Box(Modifier.size(100.dp).testTag("dialog"))
                }
            }
        }
    }

    private fun assertPageFocused() {
        rule.waitUntil(10_000) { rule.activity.hasWindowFocus() }
        rule.waitUntil(5_000) {
            rule.onNodeWithTag("page").fetchSemanticsNode().config[SemanticsProperties.Focused]
        }
        rule.onNodeWithTag("page").assertIsFocused()
        val before = launches
        rule.onNodeWithTag("page").performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertEquals(before + 1, launches) }
    }

    @Test fun lateLoadingAndFocusLossRestorePageFocus() {
        content()
        rule.waitForIdle()
        rule.runOnIdle { ready.value = true }
        assertPageFocused()
        rule.runOnIdle { focusManager.clearFocus(force = true) }
        assertPageFocused()
    }

    @Test fun descendantFocusIsPreserved() {
        ready.value = true
        content()
        assertPageFocused()
        rule.runOnIdle { child.requestFocus() }
        rule.onNodeWithTag("child").assertIsFocused()
    }

    @Test fun dialogDismissalRestoresPageFocus() {
        ready.value = true
        content()
        assertPageFocused()
        val activity = rule.activity
        rule.runOnIdle { dialog.value = true }
        rule.waitUntil(10_000) { !activity.hasWindowFocus() }
        rule.runOnIdle { focusManager.clearFocus(force = true) }
        rule.onNodeWithTag("page").assertIsNotFocused()
        rule.runOnIdle { dialog.value = false }
        assertPageFocused()
    }

    @Test fun returnFromAnotherActivityRestoresPageFocus() {
        ready.value = true
        content()
        assertPageFocused()
        val activity = rule.activity
        rule.runOnIdle {
            activity.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
        rule.waitUntil(10_000) { !activity.hasWindowFocus() }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            focusManager.clearFocus(force = true)
        }
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_BACK").close()
        rule.waitUntil(10_000) { activity.hasWindowFocus() }
        assertPageFocused()
    }
}
