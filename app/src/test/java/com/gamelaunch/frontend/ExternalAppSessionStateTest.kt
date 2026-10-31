package com.gamelaunch.frontend

import com.gamelaunch.frontend.ui.dualscreen.ExternalAppSessionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalAppSessionStateTest {
    @Test fun `apps hide artwork without dimming menu`() {
        val session = ExternalAppSessionState()
        session.begin()
        assertTrue(session.launchedOnTop.value)
        assertFalse(session.dimBottomScreen.value)
    }

    @Test fun `game dimming clears on return and subsequent app launch`() {
        val session = ExternalAppSessionState()
        session.begin(dimBottomScreen = true)
        assertTrue(session.dimBottomScreen.value)
        session.end()
        assertFalse(session.launchedOnTop.value)
        assertFalse(session.dimBottomScreen.value)
        session.begin()
        assertTrue(session.launchedOnTop.value)
        assertFalse(session.dimBottomScreen.value)
    }
}
