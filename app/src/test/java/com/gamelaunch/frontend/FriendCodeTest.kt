package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.friends.FriendCode
import com.gamelaunch.frontend.domain.friends.FriendFolders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendCodeTest {

    // A plausible 56-char base32 Syncthing id (8 groups of 7; alphabet A–Z and 2–7 only).
    private val id = "ABCDEFG-HIJKLMN-OPQRSTU-VWXYZ23-4567ABC-DEFGHIJ-KLMNOPQ-RSTUVWX"

    @Test fun `build then parse link recovers id and name`() {
        val link = FriendCode.buildLink(id, "Neon Falcon")
        val parsed = FriendCode.parse(link)!!
        assertEquals(id.uppercase(), parsed.deviceId)
        assertEquals("Neon Falcon", parsed.displayName)
    }

    @Test fun `link without name parses with null name`() {
        val parsed = FriendCode.parse(FriendCode.buildLink(id))!!
        assertEquals(id.uppercase(), parsed.deviceId)
        assertNull(parsed.displayName)
    }

    @Test fun `bare pasted id parses`() {
        val parsed = FriendCode.parse("  ${id.lowercase()}  ")!!
        assertEquals(id.uppercase(), parsed.deviceId)
    }

    @Test fun `garbage and wrong host reject`() {
        assertNull(FriendCode.parse(""))
        assertNull(FriendCode.parse("hello world"))
        assertNull(FriendCode.parse("eor://save/$id"))          // wrong host
        assertNull(FriendCode.parse("eor://friend/tooshort"))   // invalid id
    }

    @Test fun `name with special chars survives encode-decode`() {
        val parsed = FriendCode.parse(FriendCode.buildLink(id, "A&B=C ?x"))!!
        assertEquals("A&B=C ?x", parsed.displayName)
    }

    @Test fun `profile folder id is deterministic and owner-specific`() {
        val a = FriendFolders.profileFolderId(id)
        assertEquals(a, FriendFolders.profileFolderId(id.lowercase())) // case-insensitive
        assertTrue(a.startsWith(FriendFolders.PREFIX))
        assertNotEquals(a, FriendFolders.profileFolderId(id.dropLast(1) + "G"))
    }
}
