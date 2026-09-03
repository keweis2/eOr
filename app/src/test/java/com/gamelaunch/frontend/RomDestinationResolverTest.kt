package com.gamelaunch.frontend

import com.gamelaunch.frontend.data.webserver.RomDestinationResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The routing contract that keeps browser uploads in the user's *existing* library layout instead of
 * creating duplicate canonical folders.
 */
class RomDestinationResolverTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var resolver: RomDestinationResolver
    private lateinit var root: File

    @Before fun setup() {
        resolver = RomDestinationResolver()
        root = tmp.newFolder("ROMs")
    }

    @Test fun `routes into an existing non-canonical alias folder`() {
        // User already keeps NES games under "Famicom" (an alias), not the canonical "NES".
        val famicom = File(root, "Famicom").apply { mkdirs() }
        val dir = resolver.resolveRomDir(root, "nes", null)
        assertEquals(famicom.canonicalPath, dir.canonicalPath)
        // And crucially it did NOT invent a fresh canonical folder.
        assertTrue(!File(root, "NES").exists())
    }

    @Test fun `routes into an existing nested folder`() {
        val nested = File(root, "Nintendo/SNES").apply { mkdirs() }
        val dir = resolver.resolveRomDir(root, "snes", null)
        assertEquals(nested.canonicalPath, dir.canonicalPath)
    }

    @Test fun `falls back to the canonical folder when none exists`() {
        val dir = resolver.resolveRomDir(root, "gba", null)
        assertEquals(File(root, "GBA").canonicalPath, dir.canonicalPath)
    }

    @Test fun `prefers the shallowest existing folder`() {
        File(root, "Nintendo/SNES").mkdirs()
        val shallow = File(root, "SNES").apply { mkdirs() }
        val dir = resolver.resolveRomDir(root, "snes", null)
        assertEquals(shallow.canonicalPath, dir.canonicalPath)
    }

    @Test fun `explicit dest that escapes the root is rejected`() {
        assertNull(resolver.containedDir(root, "../evil"))
        assertNull(resolver.containedDir(root, "/etc"))
        try {
            resolver.resolveRomDir(root, "nes", "../evil")
            fail("Expected IllegalArgumentException for escaping dest")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test fun `explicit dest inside the root is honored`() {
        val dir = resolver.resolveRomDir(root, "nes", "Custom/Nes Games")
        assertEquals(File(root, "Custom/Nes Games").canonicalPath, dir.canonicalPath)
    }

    @Test fun `sanitizeFilename strips path components`() {
        assertEquals("passwd", resolver.sanitizeFilename("../../etc/passwd"))
        assertEquals("rom.zip", resolver.sanitizeFilename("a/b/rom.zip"))
        assertEquals("game (USA).sfc", resolver.sanitizeFilename("game (USA).sfc"))
    }

    @Test fun `sanitizeFilename rejects traversal-only names`() {
        for (bad in listOf("..", ".", "", "/")) {
            try {
                resolver.sanitizeFilename(bad)
                fail("Expected rejection of '$bad'")
            } catch (e: IllegalArgumentException) { /* expected */ }
        }
    }
}
