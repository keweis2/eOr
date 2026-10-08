package com.gamelaunch.frontend

import androidx.compose.ui.graphics.Color
import com.gamelaunch.frontend.data.theme.CustomThemeRepository
import com.gamelaunch.frontend.data.theme.ThemeFile
import com.gamelaunch.frontend.data.theme.ThemeFileException
import com.gamelaunch.frontend.ui.theme.CardColorScheme
import com.gamelaunch.frontend.ui.theme.EorTheme
import com.gamelaunch.frontend.ui.theme.EorThemes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ThemeFileTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun json(s: String) = s.trimIndent().toByteArray()

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            entries.forEach { (name, body) ->
                z.putNextEntry(ZipEntry(name)); z.write(body.toByteArray()); z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun rejects(bytes: ByteArray, messagePart: String) {
        try {
            ThemeFile.read(bytes); fail("expected rejection containing \"$messagePart\"")
        } catch (e: ThemeFileException) {
            assertTrue("\"${e.message}\" should mention \"$messagePart\"", e.message!!.contains(messagePart))
        }
    }

    /** Everything but id/name, which an imported theme gets from its file. */
    private fun EorTheme.look() = copy(id = "", name = "")

    @Test fun `a two-line theme is enough — the rest is derived`() {
        val t = ThemeFile.read(json("""{"name":"Synthwave","accent":"#FF2E97"}"""))
        assertEquals("custom-synthwave", t.id)
        assertEquals(Color(0xFFFF2E97), t.accent)
        assertEquals(t.accent, t.accent2)
        assertEquals(CardColorScheme.MONOCHROME, t.cards.scheme)
        assertEquals(t.accent, t.cards.monochromeSeed)
        assertEquals(EorTheme.Surfaces.Navy, t.darkSurfaces)
        assertEquals(3, t.darkGlows.size)
        assertNotEquals(t.dark.primary, t.light.primary)   // tones derived for each brightness
    }

    @Test fun `every built-in theme survives export and re-import unchanged`() {
        EorThemes.All.forEach { theme ->
            val back = ThemeFile.read(ThemeFile.write(theme))
            assertEquals(theme.id, theme.look(), back.look())
            assertEquals(theme.name, back.name)
        }
    }

    @Test fun `theme json can sit in a top-level folder inside the zip, or be uploaded bare`() {
        val body = """{"name":"Nested","accent":"#00FF88"}"""
        assertEquals("Nested", ThemeFile.read(zip("my-theme/theme.json" to body)).name)
        assertEquals("Nested", ThemeFile.read(body.toByteArray()).name)
    }

    @Test fun `options for background, tiles and focus`() {
        val t = ThemeFile.read(json("""
            {"name":"Night","accent":"#8888FF","darkBackground":"oled","tiles":"rainbow",
             "focus":{"dark":"#FFFFFF","light":"#000000"}}"""))
        assertEquals(EorTheme.Surfaces.Oled, t.darkSurfaces)
        assertEquals(emptyList<Color>(), t.darkGlows)      // OLED means no glows unless asked
        assertEquals(CardColorScheme.RAINBOW, t.cards.scheme)
        assertEquals(Color.White, t.focusDark)
        assertEquals(Color.Black, t.focusLight)
        assertEquals(CardColorScheme.BLACK_WHITE,
            ThemeFile.read(json("""{"name":"G","accent":"#808080","tiles":"grey"}""")).cards.scheme)
        assertEquals(Color(0xFF123456),
            ThemeFile.read(json("""{"name":"T","accent":"#808080","tiles":"#123456"}""")).cards.monochromeSeed)
    }

    @Test fun `bad files are rejected with a readable reason`() {
        rejects(json("""{"accent":"#FF0000"}"""), "no name")
        rejects(json("""{"name":"X"}"""), "no accent")
        rejects(json("""{"name":"X","accent":"red"}"""), "\"accent\" must be a colour")
        rejects(json("""{"name":"X","accent":"#FF0000","schemaVersion":99}"""), "newer version")
        rejects(json("""{"name":"X","accent":"#FF0000","format":"something-else"}"""), "Not an eOr theme")
        rejects(json("""{"name":"X","accent":"#FF0000","darkBackground":"purple"}"""), "darkBackground")
        rejects(json("""{"name":"X","accent":"#FF0000","darkGlows":["#1","#2","#3","#4","#5"]}"""), "at most 4")
        rejects(json("""{"name":"X","accent":"#FF0000","dark":{"primary":"#FFFFFF"}}"""), "dark.onPrimary")
        rejects(json("""{"name":"${"a".repeat(40)}","accent":"#FF0000"}"""), "too long")
        rejects(zip("readme.txt" to "hi"), "No theme.json")
        rejects("not json at all".toByteArray(), "Not a valid theme")
        rejects(ByteArray(ThemeFile.MAX_FILE_BYTES + 1), "too large")
    }

    @Test fun `ids come from the name so re-importing updates`() {
        assertEquals("custom-my-cool-theme", ThemeFile.idFor("My Cool Theme!"))
        assertEquals("custom-theme", ThemeFile.idFor("???"))
    }

    @Test fun `repository stores, reloads, replaces and deletes`() = runTest {
        val dir = tmp.newFolder("themes")
        val repo = CustomThemeRepository(dir)
        repo.import(json("""{"name":"Mint","accent":"#3EB489"}"""))
        repo.import(json("""{"name":"Mint","accent":"#00FF00"}"""))   // same name → replaces

        val reloaded = CustomThemeRepository(dir)                     // e.g. next app start
        assertEquals(listOf("custom-mint"), reloaded.themes.value.map { it.id })
        assertEquals(Color(0xFF00FF00), reloaded.resolve("custom-mint").accent)
        assertEquals(EorThemes.Oled, reloaded.resolve("oled"))
        assertEquals(EorThemes.Default, reloaded.resolve("custom-gone"))

        reloaded.delete("custom-mint")
        reloaded.delete("oled")                                        // built-ins can't be deleted
        assertEquals(emptyList<EorTheme>(), CustomThemeRepository(dir).themes.value)
    }

    @Test fun `repository caps the number of custom themes`() = runTest {
        val repo = CustomThemeRepository(tmp.newFolder("themes"))
        repeat(CustomThemeRepository.MAX_THEMES) { repo.import(json("""{"name":"T$it","accent":"#FF0000"}""")) }
        try {
            repo.import(json("""{"name":"One more","accent":"#FF0000"}""")); fail("expected cap")
        } catch (e: ThemeFileException) {
            assertTrue(e.message!!.contains("delete one"))
        }
        repo.import(json("""{"name":"T0","accent":"#00FF00"}"""))      // updating an existing one is fine
    }
}
