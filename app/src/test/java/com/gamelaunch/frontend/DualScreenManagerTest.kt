package com.gamelaunch.frontend

import android.app.Application
import android.content.Context
import android.hardware.display.DisplayManager
import androidx.activity.ComponentActivity
import com.gamelaunch.frontend.platform.display.DualScreenManager
import com.gamelaunch.frontend.ui.dualscreen.ArtworkBus
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class DualScreenManagerTest {
    private val activity = mock<ComponentActivity>()
    private val displays = mock<DisplayManager>()
    private lateinit var manager: DualScreenManager

    @Before fun setUp() {
        whenever(activity.getSystemService(Context.DISPLAY_SERVICE)).thenReturn(displays)
        whenever(displays.displays).thenReturn(emptyArray())
        manager = DualScreenManager(activity, ArtworkBus())
        manager.setPreferences(enabled = true, swap = false)
    }

    @Test fun `preferences arriving before start do not attach artwork`() {
        clearInvocations(displays)
        manager.refresh()
        verifyNoInteractions(displays)
        assertFalse(manager.active.value)
    }

    @Test fun `queued display callbacks cannot recreate artwork after stop`() {
        manager.start()
        val listener = argumentCaptor<DisplayManager.DisplayListener>()
        verify(displays).registerDisplayListener(listener.capture(), org.mockito.kotlin.isNull())
        manager.stop()
        clearInvocations(displays)

        // Unregistering a listener does not cancel callbacks already queued on the main thread.
        listener.firstValue.onDisplayChanged(4)
        listener.firstValue.onDisplayAdded(4)
        listener.firstValue.onDisplayRemoved(4)
        manager.refresh()

        verifyNoInteractions(displays)
        assertFalse(manager.active.value)
    }
}
