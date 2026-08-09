package com.gamelaunch.frontend.ui.screen.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.window.Dialog
import com.gamelaunch.frontend.ui.theme.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeLauncherSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun notDefault_showsSetActionAndHandlesClick() {
        var clicked = false
        composeRule.setContent {
            AppTheme {
                val focusedAction = remember { mutableStateOf<(() -> Unit)?>(null) }
                CompositionLocalProvider(LocalFocusedAction provides focusedAction) {
                    HomeLauncherSection(isDefault = false, onOpenSettings = { clicked = true })
                }
            }
        }

        composeRule.onNodeWithText("Another Home app is selected").assertIsDisplayed()
        composeRule.onNodeWithText("Set eOr as Home app").performClick()
        assertTrue(clicked)
    }

    @Test
    fun default_showsManageActionAndHandlesClick() {
        var clicked = false
        composeRule.setContent {
            AppTheme {
                val focusedAction = remember { mutableStateOf<(() -> Unit)?>(null) }
                CompositionLocalProvider(LocalFocusedAction provides focusedAction) {
                    HomeLauncherSection(isDefault = true, onOpenSettings = { clicked = true })
                }
            }
        }

        composeRule.onNodeWithText("eOr is your Home app").assertIsDisplayed()
        composeRule.onNodeWithText("Manage Home app").performClick()
        assertTrue(clicked)
    }

    @Test
    fun dialogContent_inheritsFocusedActionProvider() {
        var inheritedProvider = false

        composeRule.setContent {
            AppTheme {
                val focusedAction = remember { mutableStateOf<(() -> Unit)?>(null) }
                CompositionLocalProvider(LocalFocusedAction provides focusedAction) {
                    Dialog(onDismissRequest = {}) {
                        val dialogFocusedAction = LocalFocusedAction.current
                        SideEffect {
                            inheritedProvider = dialogFocusedAction === focusedAction
                        }
                    }
                }
            }
        }

        composeRule.runOnIdle {
            assertTrue(inheritedProvider)
        }
    }
}
