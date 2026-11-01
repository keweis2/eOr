package com.gamelaunch.frontend

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Display
import androidx.lifecycle.Lifecycle
import com.gamelaunch.frontend.platform.display.DualScreenManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class MenuDisplayRelaunchTest {
    private val activity = mock<MainActivity>()
    private val manager = mock<DualScreenManager>()

    private fun prepare(intent: Intent, state: Lifecycle.State = Lifecycle.State.STARTED) {
        val display = mock<Display>()
        whenever(display.displayId).thenReturn(Display.DEFAULT_DISPLAY)
        whenever(activity.display).thenReturn(display)
        whenever(activity.intent).thenReturn(intent)
        val lifecycle = mock<Lifecycle>()
        whenever(lifecycle.currentState).thenReturn(state)
        whenever(activity.lifecycle).thenReturn(lifecycle)
        whenever(manager.requiredMenuDisplayId()).thenReturn(1)
        field("dualScreenManager").set(activity, manager)
        field("menuPreferencesReady").setBoolean(activity, true)
    }

    private fun field(name: String) = MainActivity::class.java.getDeclaredField(name).apply {
        isAccessible = true
    }

    private fun relaunch(): Boolean = MainActivity::class.java
        .getDeclaredMethod("relaunchOnMenuDisplayIfNeeded").apply { isAccessible = true }
        .invoke(activity) as Boolean

    @Test fun `friend link survives menu relocation`() {
        val link = Uri.parse("eor://friend/ABCDEFG-HIJKLMN-OPQRSTU-VWXYZ23-4567ABC-DEFGHIJ-KLMNOPQ-RSTUVWX?n=Regression%20Test")
        val original = Intent(Intent.ACTION_VIEW, link).addCategory(Intent.CATEGORY_BROWSABLE)
        prepare(original)

        assertTrue(relaunch())

        val launched = argumentCaptor<Intent>()
        verify(activity).startActivity(launched.capture(), any<Bundle>())
        assertEquals(Intent.ACTION_VIEW, launched.firstValue.action)
        assertEquals(link, launched.firstValue.data)
        assertTrue(launched.firstValue.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertEquals(Intent.ACTION_VIEW, original.action)
        verify(activity).finish()
    }

    @Test fun `home relocation uses regular launcher task`() {
        prepare(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))

        assertTrue(relaunch())

        val launched = argumentCaptor<Intent>()
        verify(activity).startActivity(launched.capture(), any<Bundle>())
        assertEquals(Intent.ACTION_MAIN, launched.firstValue.action)
        assertFalse(launched.firstValue.hasCategory(Intent.CATEGORY_HOME))
        assertTrue(launched.firstValue.hasCategory(Intent.CATEGORY_LAUNCHER))
    }

    @Test fun `failed relocation restores artwork and permits retry`() {
        prepare(Intent(Intent.ACTION_MAIN))
        doThrow(SecurityException("Display launch denied")).whenever(activity)
            .startActivity(any<Intent>(), any<Bundle>())

        assertFalse(relaunch())

        assertFalse(field("menuRelaunchPending").getBoolean(activity))
        inOrder(manager) {
            verify(manager).stop()
            verify(manager).start()
        }
        verify(activity, never()).finish()
        doNothing().whenever(activity).startActivity(any<Intent>(), any<Bundle>())
        assertTrue(relaunch())
    }

    @Test fun `failed relocation while stopped defers artwork until next start`() {
        prepare(Intent(Intent.ACTION_MAIN), Lifecycle.State.CREATED)
        doThrow(SecurityException("Display launch denied")).whenever(activity)
            .startActivity(any<Intent>(), any<Bundle>())

        assertFalse(relaunch())

        assertFalse(field("menuRelaunchPending").getBoolean(activity))
        verify(manager, never()).start()
        verify(activity, never()).finish()
    }
}
