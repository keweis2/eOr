package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.friends.FriendProfile
import com.gamelaunch.frontend.domain.friends.LastPlayed
import com.gamelaunch.frontend.domain.friends.RaInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FriendProfileTest {

    @Test fun `full profile round-trips`() {
        val p = FriendProfile(
            deviceId = "ABC-123",
            displayName = "NeonFalcon-3172",
            lastPlayed = LastPlayed("Chrono Trigger", "snes", "abcd1234", 1_690_000_000_000L),
            ra = RaInfo("kev", 4200, 55),
            updatedAt = 1_690_000_100_000L
        )
        assertEquals(p, FriendProfile.decode(p.encode()))
    }

    @Test fun `profile without RA or last-played round-trips`() {
        val p = FriendProfile("ID", "PixelOtter-9001", lastPlayed = null, ra = null, updatedAt = 5L)
        val decoded = FriendProfile.decode(p.encode())
        assertEquals(p, decoded)
        assertNull(decoded!!.ra)
        assertNull(decoded.lastPlayed)
    }

    @Test fun `md5 omitted when null`() {
        val p = FriendProfile("ID", "Name", LastPlayed("Tetris", "gb", null, 1L), null, 2L)
        val decoded = FriendProfile.decode(p.encode())!!
        assertNull(decoded.lastPlayed!!.md5)
    }

    @Test fun `malformed json decodes to null`() {
        assertNull(FriendProfile.decode("not json"))
        assertNull(FriendProfile.decode("{}"))                       // missing required fields
        assertNull(FriendProfile.decode("""{"deviceId":"x"}"""))     // missing displayName
    }
}
