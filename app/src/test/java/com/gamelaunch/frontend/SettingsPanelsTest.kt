package com.gamelaunch.frontend

import android.app.Application
import android.view.KeyEvent
import androidx.compose.runtime.CompositionContext
import com.gamelaunch.frontend.ui.dualscreen.SettingsPane
import com.gamelaunch.frontend.ui.dualscreen.SettingsPanels
import com.gamelaunch.frontend.ui.screen.settings.SettingsCategory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class SettingsPanelsTest {
    private val panels = SettingsPanels()
    private var closed = 0
    private fun pane() = SettingsPane(
        parent = mock<CompositionContext>(),
        back = { panels.focusCategories() },
        close = { closed++ },
        selectCategory = { category, edit ->
            panels.selectCategory(category)
            if (edit) panels.focusControls() else panels.focusCategories()
        },
        content = {},
    )
    private fun key(code: Int, action: Int = KeyEvent.ACTION_DOWN, repeat: Int = 0) =
        panels.dispatch(KeyEvent(0, 0, action, code, repeat))

    @Test fun `B returns to categories and requires another press to close`() {
        panels.show(pane())
        panels.focusControls()
        assertTrue(key(KeyEvent.KEYCODE_BUTTON_B))
        assertFalse(panels.controlsFocused)
        assertTrue(key(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.ACTION_UP))
        assertEquals(0, closed)
        assertTrue(key(KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(1, closed)
    }

    @Test fun `directions select a category and A enters its controls without view focus`() {
        panels.show(pane())
        assertTrue(key(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(SettingsCategory.HOME_LAYOUT, panels.selectedCategory)
        assertFalse(panels.controlsFocused)
        assertTrue(key(KeyEvent.KEYCODE_BUTTON_A))
        assertTrue(panels.controlsFocused)
        assertEquals(SettingsCategory.HOME_LAYOUT, panels.selectedCategory)
    }

    @Test fun `category navigation stops at list boundaries`() {
        panels.show(pane())
        key(KeyEvent.KEYCODE_DPAD_UP)
        assertEquals(SettingsCategory.APPEARANCE, panels.selectedCategory)
        panels.selectCategory(SettingsCategory.entries.last())
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals(SettingsCategory.entries.last(), panels.selectedCategory)
    }

    @Test fun `outgoing destination cannot clear replacement controls`() {
        val outgoing = pane()
        val incoming = pane()
        panels.show(outgoing)
        panels.show(incoming)
        panels.clear(outgoing)
        assertSame(incoming, panels.pane)
        panels.clear(incoming)
        assertNull(panels.pane)
        assertFalse(key(KeyEvent.KEYCODE_BUTTON_A))
    }

    @Test fun `unhandled directions on top cannot move category focus`() {
        panels.show(pane())
        panels.focusControls()
        assertTrue(key(KeyEvent.KEYCODE_DPAD_DOWN))
        assertTrue(key(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(SettingsCategory.APPEARANCE, panels.selectedCategory)
        assertFalse(key(KeyEvent.KEYCODE_VOLUME_UP))
    }
    @Test fun `holding Back cannot close after returning to categories`() {
        panels.show(pane())
        panels.focusControls()
        key(KeyEvent.KEYCODE_BUTTON_B)
        key(KeyEvent.KEYCODE_BUTTON_B, repeat = 1)
        key(KeyEvent.KEYCODE_BUTTON_B, repeat = 2)
        assertFalse(panels.controlsFocused)
        assertEquals(0, closed)
        key(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.ACTION_UP)
        key(KeyEvent.KEYCODE_BUTTON_B)
        assertEquals(1, closed)
    }

    @Test fun `repeated activation cannot enter controls`() {
        panels.show(pane())
        assertTrue(key(KeyEvent.KEYCODE_BUTTON_A, repeat = 1))
        assertFalse(panels.controlsFocused)
    }

    @Test fun `reset releases the portal and starts a fresh settings session`() {
        panels.show(pane())
        panels.focusControls()
        panels.selectCategory(SettingsCategory.entries.last())
        val scroll = panels.categoryScrollState
        panels.reset()
        assertNull(panels.pane)
        assertNull(panels.controlsView)
        assertFalse(panels.controlsFocused)
        assertEquals(SettingsCategory.APPEARANCE, panels.selectedCategory)
        assertNotSame(scroll, panels.categoryScrollState)
        assertFalse(panels.back())
        panels.show(pane())
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals(SettingsCategory.HOME_LAYOUT, panels.selectedCategory)
    }

    @Test fun `focused controls get first chance to consume Back`() {
        panels.show(pane())
        panels.focusControls()
        val view = mock<androidx.compose.ui.platform.ComposeView>()
        org.mockito.kotlin.whenever(view.dispatchKeyEvent(org.mockito.kotlin.any())).thenReturn(true)
        panels.attachControls(view)
        key(KeyEvent.KEYCODE_BACK)
        assertTrue(panels.controlsFocused)
        assertEquals(0, closed)
    }

    @Test fun `unhandled Back from controls returns to categories`() {
        panels.show(pane())
        panels.focusControls()
        panels.attachControls(mock())
        key(KeyEvent.KEYCODE_BACK)
        assertFalse(panels.controlsFocused)
        assertEquals(0, closed)
    }

    @Test fun `entering controls does not send the activation release to a different target`() {
        panels.show(pane())
        key(KeyEvent.KEYCODE_BUTTON_A)
        val view = mock<androidx.compose.ui.platform.ComposeView>()
        panels.attachControls(view)
        assertTrue(key(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.ACTION_UP))
        org.mockito.kotlin.verifyNoInteractions(view)
    }

    @Test fun `modal input takes priority even when categories own panel focus`() {
        panels.show(pane())
        var dismissed = 0
        val dialog = mock<android.view.View>()
        panels.addModal(dialog) { dismissed++ }
        assertTrue(key(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(SettingsCategory.APPEARANCE, panels.selectedCategory)
        key(KeyEvent.KEYCODE_BUTTON_B)
        assertEquals(1, dismissed)
        assertEquals(0, closed)
        panels.removeModal(dialog)
        key(KeyEvent.KEYCODE_BUTTON_B)
        assertEquals(1, closed)
    }

    @Test fun `modal Back policy applies to controller and gesture without leaking input`() {
        panels.show(pane())
        var dismissed = 0
        var dismissOnBack = false
        val dialog = mock<android.view.View>()
        panels.addModal(dialog, dismissOnBack = { dismissOnBack }) { dismissed++ }
        assertTrue(key(KeyEvent.KEYCODE_BUTTON_B))
        assertTrue(key(KeyEvent.KEYCODE_BACK))
        assertTrue(panels.back())
        assertEquals(0, dismissed)
        assertEquals(0, closed)
        dismissOnBack = true
        assertTrue(key(KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(1, dismissed)
        assertTrue(panels.back())
        assertEquals(2, dismissed)
    }

}
