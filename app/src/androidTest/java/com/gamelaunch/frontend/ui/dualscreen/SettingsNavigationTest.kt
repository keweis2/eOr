package com.gamelaunch.frontend.ui.dualscreen

import android.hardware.display.DisplayManager
import android.view.KeyEvent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import com.gamelaunch.frontend.ui.screen.settings.SettingsCategory
import com.gamelaunch.frontend.MainActivity
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Covers the real navigation graph, including the portal's destination ownership. */
class SettingsNavigationTest {
    @get:Rule(order = 0) val settings = object : org.junit.rules.ExternalResource() {
        override fun before() {
            // These journeys begin at Home, including on a fresh emulator installation.
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            kotlinx.coroutines.runBlocking {
                com.gamelaunch.frontend.data.preferences.AppDataStore(context).setFirstLaunchComplete()
            }
        }
    }
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    @Test fun rescanReleasesTheSettingsPortalAndControllerRouting() {
        assumeTrue("Requires two displays", rule.activity.getSystemService(DisplayManager::class.java).displays.size > 1)
        val panels = rule.activity.settingsPanels
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Games").fetchSemanticsNodes().isNotEmpty() }
        rule.runOnIdle {
            rule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_START))
            rule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_START))
        }
        rule.waitUntil(10_000) { panels.pane != null }
        rule.onAllNodesWithText("Games & Library").onFirst().performClick()
        rule.onNodeWithText("Rescan ROMs").performScrollTo().performClick()
        rule.waitUntil(5_000) { panels.pane == null && panels.controlsView == null }
        rule.runOnIdle {
            assertFalse(panels.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)))
            assertFalse(panels.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B)))
            assertFalse(panels.back())
        }
        rule.onAllNodesWithText("Settings categories").assertCountEquals(0)
    }

    @Test fun categoriesReplaceTheTopScreenWithTouchAndController() {
        assumeTrue("Requires two displays", rule.activity.getSystemService(DisplayManager::class.java).displays.size > 1)
        val panels = rule.activity.settingsPanels
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Games").fetchSemanticsNodes().isNotEmpty() }
        rule.runOnIdle {
            rule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_START))
            rule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_START))
        }
        rule.waitUntil(10_000) { panels.pane != null }
        // Back belongs to the bottom category pane, not the top detail pane.
        rule.onAllNodesWithContentDescription("Back").assertCountEquals(1)
        rule.onNode(hasContentDescription("Back") and
            hasAnyAncestor(hasAnyDescendant(hasText("Settings categories")))).assertExists()
        val appearance = rule.runOnIdle { panels.pane }
        val view = rule.runOnIdle { panels.controlsView }
        rule.onAllNodesWithText("Home & Layout").onFirst().performClick()
        rule.waitUntil(5_000) { panels.pane !== appearance }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Home & Layout").fetchSemanticsNodes().size == 2 }
        rule.onAllNodesWithContentDescription("Back").assertCountEquals(1)
        rule.onNode(hasContentDescription("Back") and
            hasAnyAncestor(hasAnyDescendant(hasText("Settings categories")))).assertExists()
        rule.runOnIdle {
            assertSame(view, panels.controlsView)
            panels.focusCategories()
            rule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
            rule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN))
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Games & Library").fetchSemanticsNodes().size == 2 }
        rule.runOnIdle { assertSame(view, panels.controlsView) }
        // Navigate past the bottom viewport, then back to the first category. The
        // selected row must stay visible even when the controls window owns focus.
        for ((key, categories) in listOf(
            KeyEvent.KEYCODE_DPAD_DOWN to SettingsCategory.entries.drop(3),
            KeyEvent.KEYCODE_DPAD_UP to SettingsCategory.entries.dropLast(1).reversed(),
        )) {
            for (category in categories) {
                rule.runOnIdle {
                    rule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
                    rule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
                }
                rule.waitUntil(5_000) { panels.selectedCategory == category }
                rule.waitForIdle()
                rule.onNode(hasText(category.title) and
                    hasAnyAncestor(hasAnyDescendant(hasText("Settings categories"))))
                    .assertIsDisplayed()
                if (category == SettingsCategory.BACKUP) {
                    rule.runOnIdle { assertTrue(panels.categoryScrollState.value > 0) }
                }
            }
        }
    }
}
