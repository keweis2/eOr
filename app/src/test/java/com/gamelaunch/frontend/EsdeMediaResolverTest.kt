package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.usecase.ImportEsdeMediaUseCase.Companion.buildIndex
import com.gamelaunch.frontend.domain.usecase.ImportEsdeMediaUseCase.Companion.candidateDirs
import com.gamelaunch.frontend.domain.usecase.ImportEsdeMediaUseCase.Companion.resolveEsdeMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** ES-DE media matching: folder names come from where the ROM lives, and 3D boxes are a fallback. */
class EsdeMediaResolverTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun media(vararg relPaths: String): File {
        val root = tmp.newFolder("downloaded_media")
        relPaths.forEach { File(root, it).apply { parentFile!!.mkdirs(); writeText("x") } }
        return root
    }

    private fun game(romPath: String, platformId: String) =
        Game(id = 1, title = "t", romPath = romPath, romFilename = File(romPath).name, platformId = platformId)

    @Test fun `3DS media is found under ES-DE's n3ds folder`() {
        val root = media("n3ds/covers/Zelda.png", "n3ds/videos/Zelda.mp4", "n3ds/screenshots/Zelda.png")
        val g = game("/sd/ROMs/n3ds/Zelda.3ds", "3ds")

        val m = resolveEsdeMedia(g, platformDirs = listOf("3ds"), index = buildIndex(root), prefer3dBoxes = false)

        assertTrue(m.boxArt!!.endsWith("n3ds/covers/Zelda.png"))
        assertTrue(m.video!!.endsWith("n3ds/videos/Zelda.mp4"))
        assertTrue(m.screenshot!!.endsWith("n3ds/screenshots/Zelda.png"))
    }

    @Test fun `regional and custom ES-DE systems are matched by the ROM folder`() {
        val root = media("famicom/covers/Mario.png", "mysystem/covers/Thing.png")
        val index = buildIndex(root)

        assertTrue(resolveEsdeMedia(game("/sd/ROMs/famicom/Mario.nes", "nes"), listOf("nes"), index, false)
            .boxArt!!.endsWith("famicom/covers/Mario.png"))
        assertTrue(resolveEsdeMedia(game("/sd/ROMs/mysystem/Thing.zip", "mame"), listOf("mame"), index, false)
            .boxArt!!.endsWith("mysystem/covers/Thing.png"))
    }

    @Test fun `games kept in their own sub-folder still match the system folder`() {
        val root = media("psx/covers/Final Fantasy VII.png")
        val g = game("/sd/ROMs/psx/Final Fantasy VII/Final Fantasy VII.m3u", "ps1")
        assertTrue(resolveEsdeMedia(g, listOf("psx"), buildIndex(root), false).boxArt!!.endsWith("psx/covers/Final Fantasy VII.png"))
    }

    @Test fun `3D boxes are used when there is no 2D cover`() {
        val root = media("psx/3dboxes/Crash.png")
        val m = resolveEsdeMedia(game("/sd/ROMs/psx/Crash.chd", "ps1"), listOf("psx"), buildIndex(root), false)
        assertTrue(m.boxArt!!.endsWith("psx/3dboxes/Crash.png"))
    }

    @Test fun `2D covers win by default, 3D boxes win when preferred`() {
        val root = media("psx/covers/Crash.png", "psx/3dboxes/Crash.png")
        val index = buildIndex(root)
        val g = game("/sd/ROMs/psx/Crash.chd", "ps1")
        assertTrue(resolveEsdeMedia(g, listOf("psx"), index, prefer3dBoxes = false).boxArt!!.endsWith("covers/Crash.png"))
        assertTrue(resolveEsdeMedia(g, listOf("psx"), index, prefer3dBoxes = true).boxArt!!.endsWith("3dboxes/Crash.png"))
    }

    @Test fun `catalog names still work when the ROM folder isn't an ES-DE name`() {
        val root = media("megadrive/covers/Sonic.png")
        val g = game("/sd/Games/Sega Genesis/Sonic.md", "genesis")
        assertTrue(resolveEsdeMedia(g, listOf("megadrive", "genesis"), buildIndex(root), false).boxArt!!.endsWith("megadrive/covers/Sonic.png"))
    }

    @Test fun `nothing found gives nulls`() {
        val m = resolveEsdeMedia(game("/sd/ROMs/nes/Nope.nes", "nes"), listOf("nes"), buildIndex(media("nes/covers/Other.png")), false)
        assertNull(m.boxArt); assertNull(m.video)
    }

    @Test fun `ROM folders come before catalog names, without duplicates`() {
        assertEquals(
            listOf("n3ds", "roms", "sd", "3ds"),
            candidateDirs(game("/sd/ROMs/n3ds/Zelda.3ds", "3ds"), listOf("n3ds", "3ds"))
        )
    }
}
