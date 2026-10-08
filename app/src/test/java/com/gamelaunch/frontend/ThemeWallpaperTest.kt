package com.gamelaunch.frontend

import com.gamelaunch.frontend.data.theme.CustomThemeRepository
import com.gamelaunch.frontend.data.theme.ThemeFile
import com.gamelaunch.frontend.data.theme.ThemeFileException
import com.gamelaunch.frontend.ui.theme.EorTheme
import com.gamelaunch.frontend.ui.theme.EorThemes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ThemeWallpaperTest {

    @get:Rule val tmp = TemporaryFolder()

    // A JPEG signature is all ThemeFile checks; decoding happens in the (Android) normalizer.
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return out.toByteArray()
    }

    private fun rejects(bytes: ByteArray, part: String) {
        try { ThemeFile.readPackage(bytes); fail("expected \"$part\"") } catch (e: ThemeFileException) {
            assertTrue("got: ${e.message}", e.message!!.contains(part))
        }
    }

    private val withImage = """{"name":"Beach","accent":"#22AAFF","wallpaper":{"image":"Beach.JPG","dimDark":0.7,"blur":5}}"""

    @Test fun `reads the image the json names, in any order and case`() {
        val pkg = ThemeFile.readPackage(zip("Beach.jpg" to jpeg, "theme.json" to withImage.toByteArray()))
        assertArrayEquals(jpeg, pkg.wallpaper)
        val w = pkg.theme.wallpaper!!
        assertEquals(0.7f, w.dimDark)
        assertEquals(EorTheme.Wallpaper().dimLight, w.dimLight)       // missing → default
        assertEquals(1f, w.blur)                                       // clamped
    }

    @Test fun `bad wallpapers are rejected`() {
        rejects(zip("theme.json" to withImage.toByteArray()), "missing")
        rejects(zip("theme.json" to withImage.toByteArray(), "beach.jpg" to "not an image".toByteArray()), "isn't a JPEG")
        rejects(withImage.toByteArray(), "missing")                    // bare json can't carry one
        rejects(zip("theme.json" to """{"name":"X","accent":"#FF0000","wallpaper":{"image":"../x.jpg"}}""".toByteArray()), "image file name")
    }

    @Test fun `themes without a wallpaper are unchanged`() {
        val pkg = ThemeFile.readPackage("""{"name":"Plain","accent":"#FF0000"}""".toByteArray())
        assertNull(pkg.theme.wallpaper)
        assertNull(pkg.wallpaper)
        assertFalse(ThemeFile.toJson(pkg.theme).contains("wallpaper"))
    }

    @Test fun `write and read round-trip the image`() {
        val theme = EorThemes.Ocean.copy(wallpaper = EorTheme.Wallpaper(dimDark = 0.4f, glows = true))
        val pkg = ThemeFile.readPackage(ThemeFile.write(theme, wallpaper = jpeg))
        assertEquals(theme.wallpaper, pkg.theme.wallpaper)
        assertArrayEquals(jpeg, pkg.wallpaper)
        // A wallpaper setting without the image is dropped rather than written broken.
        assertNull(ThemeFile.readPackage(ThemeFile.write(theme)).theme.wallpaper)
    }

    @Test fun `repository sets, adjusts, exports and removes a wallpaper`() = runTest {
        val dir = tmp.newFolder("themes")
        val repo = CustomThemeRepository(dir)

        val mine = repo.setWallpaper(EorThemes.Ocean, jpeg)           // built-in → custom copy
        assertEquals("My Ocean", mine.name)
        assertTrue(mine.id.startsWith(ThemeFile.ID_PREFIX))
        assertArrayEquals(jpeg, repo.wallpaperFile(mine)!!.readBytes())

        repo.updateWallpaper(mine, EorTheme.Wallpaper(blur = 0.8f))
        val reloaded = CustomThemeRepository(dir)                      // next app start
        val again = reloaded.resolve(mine.id)
        assertEquals(0.8f, again.wallpaper!!.blur)

        // Export → import elsewhere carries the image.
        val other = CustomThemeRepository(tmp.newFolder("other"))
        val imported = other.import(reloaded.export(again))
        assertArrayEquals(jpeg, other.wallpaperFile(imported)!!.readBytes())

        val plain = reloaded.removeWallpaper(again)
        assertNull(plain.wallpaper)
        assertNull(reloaded.wallpaperFile(plain))
        assertFalse(dir.resolve("${mine.id}.jpg").exists())

        reloaded.delete(mine.id)
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test fun `a missing unpacked image is restored from the theme file`() = runTest {
        val dir = tmp.newFolder("themes")
        val mine = CustomThemeRepository(dir).setWallpaper(EorThemes.Rose, jpeg)
        dir.resolve("${mine.id}.jpg").delete()
        val repo = CustomThemeRepository(dir)
        assertArrayEquals(jpeg, repo.wallpaperFile(repo.resolve(mine.id))!!.readBytes())
    }
}
