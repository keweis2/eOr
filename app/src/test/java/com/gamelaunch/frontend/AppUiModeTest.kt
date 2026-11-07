package com.gamelaunch.frontend

import com.gamelaunch.frontend.ui.app.AppUiMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AppUiModeTest {
    @Test fun `a single display keeps the existing interface even when dual screen is enabled`() {
        assertEquals(AppUiMode.SINGLE_SCREEN, AppUiMode.resolve(true, false))
    }

    @Test fun `disabling dual screen keeps the existing interface on either device type`() {
        assertEquals(AppUiMode.SINGLE_SCREEN, AppUiMode.resolve(false, true))
        assertEquals(AppUiMode.SINGLE_SCREEN, AppUiMode.resolve(false, false))
    }

    @Test fun `an available second display uses the dual screen interface when enabled`() {
        assertEquals(AppUiMode.DUAL_SCREEN, AppUiMode.resolve(true, true))
    }
}
