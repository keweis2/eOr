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
        val a = repo.setWallpaper(EorThemes.Rose, jpeg)                 // "My Rose", with an image
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
