package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.platform.PlatformDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PlatformDetectorTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private lateinit var detector: PlatformDetector

    @Before fun setup() { detector = PlatformDetector() }

    @Test fun `folder name takes priority over extension for ambiguous bin`() {
        val ps1Dir = tmpFolder.newFolder("PS1")
        val file = File(ps1Dir, "game.bin").also { it.createNewFile() }
        val result = detector.detect(file, "PS1")
        assertEquals("ps1", result?.id)
    }

    @Test fun `genesis bin detected via folder`() {
        val genDir = tmpFolder.newFolder("Genesis")
        val file = File(genDir, "sonic.bin").also { it.createNewFile() }
        val result = detector.detect(file, "Genesis")
        assertEquals("genesis", result?.id)
    }

    @Test fun `nes detected by extension when folder is unknown`() {
        val dir = tmpFolder.newFolder("roms")
        val file = File(dir, "mario.nes").also { it.createNewFile() }
        val result = detector.detect(file, "roms")
        assertEquals("nes", result?.id)
    }

    @Test fun `unknown extension returns null`() {
        val dir = tmpFolder.newFolder("misc")
        val file = File(dir, "readme.txt").also { it.createNewFile() }
        val result = detector.detect(file, "misc")
        assertNull(result)
    }

    @Test fun `gba detected by extension`() {
        val dir = tmpFolder.newFolder("games")
        val file = File(dir, "pokemon.gba").also { it.createNewFile() }
        assertEquals("gba", detector.detect(file, "games")?.id)
    }

    @Test fun `psp iso detected via PSP folder`() {
        val dir = tmpFolder.newFolder("PSP")
        val file = File(dir, "game.iso").also { it.createNewFile() }
        assertEquals("psp", detector.detect(file, "PSP")?.id)
    }

    @Test fun `ps1 m3u detected via PS1 folder`() {
        val dir = tmpFolder.newFolder("PS1")
        val file = File(dir, "game.m3u").also { it.createNewFile() }
        assertEquals("ps1", detector.detect(file, "PS1")?.id)
    }

    @Test fun `m3u outside platform folder is ignored as ambiguous`() {
        val dir = tmpFolder.newFolder("misc")
        val file = File(dir, "game.m3u").also { it.createNewFile() }
        assertNull(detector.detect(file, "misc"))
    }

    @Test fun `xbox360 xex detected via xbox360 folder`() {
        val dir = tmpFolder.newFolder("xbox360")
        val file = File(dir, "default.xex").also { it.createNewFile() }
        assertEquals("xbox360", detector.detect(file, "xbox360")?.id)
    }

    @Test fun `xbox360 stfs detected via 360 folder`() {
        val dir = tmpFolder.newFolder("360")
        val file = File(dir, "game.stfs").also { it.createNewFile() }
        assertEquals("xbox360", detector.detect(file, "360")?.id)
    }

    @Test fun `ps3 pkg detected via PS3 folder`() {
        val dir = tmpFolder.newFolder("PS3")
        val file = File(dir, "game.pkg").also { it.createNewFile() }
        assertEquals("ps3", detector.detect(file, "PS3")?.id)
    }

    @Test fun `ps3 iso detected via PS3 folder`() {
        val dir = tmpFolder.newFolder("PS3")
        val file = File(dir, "game.iso").also { it.createNewFile() }
        assertEquals("ps3", detector.detect(file, "PS3")?.id)
    }
}
