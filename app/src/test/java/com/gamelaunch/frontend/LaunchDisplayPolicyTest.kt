package com.gamelaunch.frontend

import android.app.ActivityOptions
import android.app.Application
import android.os.Bundle
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.launcher.LaunchDisplayPolicy
import com.gamelaunch.frontend.launcher.LaunchScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

// The app bundles Android Conscrypt; routing tests do not need its native crypto provider.
@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class LaunchDisplayPolicyTest {
    private val context = mock<Context>()
    private val displays = mock<DisplayManager>()
    private val settings = mock<SettingsRepository>()
    private val enabled = MutableStateFlow(true)
    private val swapped = MutableStateFlow(false)
    private val gamesOnTop = MutableStateFlow(true)
    private val policy = LaunchDisplayPolicy(context, settings)

    @Before fun setUp() {
        whenever(settings.dualScreenEnabled).thenReturn(enabled)
        whenever(settings.dualScreenSwap).thenReturn(swapped)
        whenever(settings.gameLaunchOnTop).thenReturn(gamesOnTop)
        whenever(context.getSystemService(Context.DISPLAY_SERVICE)).thenReturn(displays)
        val connectedDisplays = arrayOf(display(0), display(7))
        whenever(displays.displays).thenReturn(connectedDisplays)
    }

    @Test fun `top and bottom routing follows physical layout and swap`() = runTest {
        for (thor in listOf(false, true)) {
            ShadowBuild.setManufacturer(if (thor) "AYN" else "Anbernic")
            ShadowBuild.setModel(if (thor) "Thor" else "RG DS")
            ShadowBuild.setDevice(if (thor) "thor" else "rgds")
            for (swap in listOf(false, true)) {
                swapped.value = swap
                val top = if (thor != swap) 0 else 7
                assertEquals(top, target(LaunchScreen.TOP))
                assertEquals(if (top == 0) 7 else 0, target(LaunchScreen.BOTTOM))
                assertEquals(top, target(null))
            }
        }
    }

    @Test fun `disabled dual screen preserves default routing for apps and games`() = runTest {
        enabled.value = false
        for (screen in listOf(null, LaunchScreen.TOP, LaunchScreen.BOTTOM)) {
            assertNull(policy.options(screen = screen))
        }
    }

    @Test fun `missing or powered off secondary display preserves default routing`() = runTest {
        for (available in listOf(arrayOf(display(0)), arrayOf(display(0), display(7, Display.STATE_OFF)))) {
            whenever(displays.displays).thenReturn(available)
            for (screen in listOf(null, LaunchScreen.TOP, LaunchScreen.BOTTOM)) {
                assertNull(policy.options(screen = screen))
            }
        }
    }

    @Test fun `DS and 3DS keep emulator managed routing even for explicit screens`() = runTest {
        for (platform in listOf("nds", "3ds")) {
            for (screen in listOf(null, LaunchScreen.TOP, LaunchScreen.BOTTOM)) {
                assertNull(policy.options(platform, screen))
            }
        }
    }

    @Test fun `game top preference does not override explicit app screen`() = runTest {
        gamesOnTop.value = false
        assertNull(policy.options())
        assertNotNull(policy.options(screen = LaunchScreen.TOP))
        assertNotNull(policy.options(screen = LaunchScreen.BOTTOM))
    }

    private suspend fun target(screen: LaunchScreen?): Int {
        // Android exposes the display getter but hides bundle deserialization from the SDK.
        val options = ActivityOptions::class.java.getDeclaredMethod("fromBundle", Bundle::class.java)
            .invoke(null, requireNotNull(policy.options(screen = screen))) as ActivityOptions
        return options.launchDisplayId
    }

    private fun display(id: Int, state: Int = Display.STATE_ON): Display = mock<Display>().also {
        whenever(it.displayId).thenReturn(id)
        whenever(it.state).thenReturn(state)
    }
}
