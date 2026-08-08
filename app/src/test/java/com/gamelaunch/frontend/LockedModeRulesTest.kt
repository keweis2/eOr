package com.gamelaunch.frontend

import com.gamelaunch.frontend.data.repository.deriveLockedModeState
import com.gamelaunch.frontend.data.repository.isValidLockedModePin
import com.gamelaunch.frontend.domain.lockedmode.LockedModeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockedModeRulesTest {
    @Test
    fun `state requires a stored PIN and preserves active lock`() {
        assertEquals(LockedModeState.UNCONFIGURED, deriveLockedModeState("", active = true))
        assertEquals(LockedModeState.READY, deriveLockedModeState("0427", active = false))
        assertEquals(LockedModeState.LOCKED, deriveLockedModeState("0427", active = true))
    }

    @Test
    fun `only exactly four numeric digits form a PIN`() {
        assertTrue(isValidLockedModePin("0427"))
        assertFalse(isValidLockedModePin("427"))
        assertFalse(isValidLockedModePin("04270"))
        assertFalse(isValidLockedModePin("12a4"))
    }
}
