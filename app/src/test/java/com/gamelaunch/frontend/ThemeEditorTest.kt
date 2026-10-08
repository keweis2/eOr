package com.gamelaunch.frontend

import androidx.compose.ui.graphics.Color
import com.gamelaunch.frontend.data.theme.CustomThemeRepository
import com.gamelaunch.frontend.data.theme.ThemeFile
import com.gamelaunch.frontend.data.theme.ThemeFileException
import com.gamelaunch.frontend.ui.theme.CardColorScheme
import com.gamelaunch.frontend.ui.theme.EorThemes
import com.gamelaunch.frontend.ui.theme.ThemeDraft
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThemeEditorTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun `an untouched draft reproduces every built-in exactly`() {
        EorThemes.All.forEach { t -> assertEquals(t.name, t, ThemeDraft.from(t).toTheme(t, t.id)) }
    }

    @Test fun `changing the accent re-derives tones but keeps everything else`() {
        val base = EorThemes.Violet
        val t = ThemeDraft.from(base).copy(accent = Color(0xFF00FF00)).toTheme(base, base.id)
        assertEquals(Color(0xFF00FF00), t.accent)
        assertEquals(ThemeDraft.darkTones(Color(0xFF00FF00)), t.dark)
        assertEquals(base.darkSurfaces, t.darkSurfaces)
        assertEquals(base.cards, t.cards)
    }

    @Test fun `changing the background re-derives every dark surface`() {
        val base = EorThemes.Default
        val t = ThemeDraft.from(base).copy(background = Color.Black).toTheme(base, base.id)
        assertEquals(Color.Black, t.darkSurfaces.background)
        assertEquals(Color.Black, t.darkSurfaces.containerLowest)
        assertNotEquals(base.darkSurfaces.card, t.darkSurfaces.card)
    }

    @Test fun `tiles and glows follow the draft`() {
        val base = EorThemes.Default
        val t = ThemeDraft.from(base).copy(tiles = CardColorScheme.MONOCHROME, tileColor = Color.Red, glows = false)
            .toTheme(base, base.id)
        assertEquals(CardColorScheme.MONOCHROME, t.cards.scheme)
        assertEquals(Color.Red, t.cards.monochromeSeed)
        assertTrue(t.darkGlows.isEmpty())
        // Turning glows back on for a theme that had none (OLED) uses the accents.
        val oled = EorThemes.Oled
        assertEquals(3, ThemeDraft.from(oled).copy(glows = true).toTheme(oled, oled.id).darkGlows.size)
    }

    @Test fun `on a photo theme the glows switch is the photo's glow setting`() {
        val base = EorThemes.Ocean.copy(wallpaper = com.gamelaunch.frontend.ui.theme.EorTheme.Wallpaper(glows = false))
        val draft = ThemeDraft.from(base)
        assertEquals(false, draft.glows)                               // not "dark glows exist"
        assertEquals(base, draft.toTheme(base, base.id))               // untouched round-trip
        val on = draft.copy(glows = true).toTheme(base, base.id)
        assertEquals(true, on.wallpaper!!.glows)
        assertTrue(on.darkGlows.isNotEmpty())                          // something to draw
    }

    @Test fun `a picked image replaces the old one, removing the wallpaper drops it`() = runTest {
        val dir = tmp.newFolder("w")
        val repo = CustomThemeRepository(dir)
        val first = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1)
        val second = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 2)
        val w = com.gamelaunch.frontend.ui.theme.EorTheme.Wallpaper(dimLight = 0.7f)
        val a = repo.saveEdited(EorThemes.Ocean, EorThemes.Ocean.copy(name = "Pic", wallpaper = w), first)
        assertArrayEquals(first, repo.wallpaperFile(a)!!.readBytes())
        assertEquals(0.7f, repo.resolve(a.id).wallpaper!!.dimLight)

        val b = repo.saveEdited(a, a, second)                          // Replace image
        assertArrayEquals(second, repo.wallpaperFile(b)!!.readBytes())
        val c = repo.saveEdited(b, b.copy(accent = Color.Red))         // colours only: image stays
        assertArrayEquals(second, repo.wallpaperFile(c)!!.readBytes())

        val d = repo.saveEdited(c, c.copy(wallpaper = null), second)   // Remove wins over a stale pick
        assertNull(d.wallpaper)
        assertNull(repo.wallpaperFile(d))
        assertTrue(!dir.resolve("${d.id}.jpg").exists())
    }

    @Test fun `draft photo settings flow into the theme`() {
        val base = EorThemes.Ocean
        val t = ThemeDraft.from(base).copy(
            wallpaper = com.gamelaunch.frontend.ui.theme.EorTheme.Wallpaper(dimDark = 0.3f, blur = 0.6f), glows = true
        ).toTheme(base, base.id)
        assertEquals(0.3f, t.wallpaper!!.dimDark)
        assertEquals(0.6f, t.wallpaper!!.blur)
        assertEquals(true, t.wallpaper!!.glows)
    }

    @Test fun `editing a built-in saves a new custom theme`() = runTest {
        val repo = CustomThemeRepository(tmp.newFolder("t"))
        val saved = repo.saveEdited(EorThemes.Ocean, EorThemes.Ocean.copy(name = "My Ocean"))
        assertEquals("custom-my-ocean", saved.id)
        assertEquals(EorThemes.Ocean.accent, repo.resolve(saved.id).accent)
    }

    @Test fun `renaming moves the theme and its image, duplicates are refused`() = runTest {
        val dir = tmp.newFolder("t")
        val repo = CustomThemeRepository(dir)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1)
        val a = repo.saveEdited(EorThemes.Rose, EorThemes.Rose.copy(name = "My Rose", wallpaper = com.gamelaunch.frontend.ui.theme.EorTheme.Wallpaper()), jpeg)                 // "My Rose", with an image
        repo.import("""{"name":"Taken","accent":"#FF0000"}""".toByteArray())

        try { repo.saveEdited(a, a.copy(name = "Taken")); fail("expected duplicate refusal") }
        catch (e: ThemeFileException) { assertTrue(e.message!!.contains("already have")) }
        try { repo.saveEdited(a, a.copy(name = "  ")); fail("expected empty-name refusal") }
        catch (e: ThemeFileException) { assertTrue(e.message!!.contains("name")) }

        val renamed = repo.saveEdited(a, a.copy(name = "Pink"))
        assertEquals("custom-pink", renamed.id)
        assertEquals(listOf("custom-pink", "custom-taken"), repo.themes.value.map { it.id })
        assertArrayEquals(jpeg, repo.wallpaperFile(renamed)!!.readBytes())
        assertTrue(dir.resolve("${a.id}.jpg").exists().not())

        // Saving under the same name updates in place.
        val same = repo.saveEdited(renamed, renamed.copy(accent = Color.Blue))
        assertEquals(renamed.id, same.id)
        assertEquals(Color.Blue, CustomThemeRepository(dir).resolve(same.id).accent)
        assertNull(CustomThemeRepository(dir).themes.value.firstOrNull { it.id == a.id })
        assertTrue(same.id.startsWith(ThemeFile.ID_PREFIX))
    }
}
