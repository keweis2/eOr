package com.gamelaunch.frontend

import com.gamelaunch.frontend.data.theme.CustomThemeRepository
import com.gamelaunch.frontend.data.theme.ThemeFile
import com.gamelaunch.frontend.data.theme.ThemeFileException
import com.gamelaunch.frontend.data.theme.ThemeGalleryRepository
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ThemeGalleryTest {

    @get:Rule val tmp = TemporaryFolder()
    private val server = MockWebServer()

    @After fun tearDown() = server.shutdown()

    /** Gradle runs unit tests from the module dir; the gallery sits at the repo root. */
    private val galleryDir = File("../themes")

    @Test fun `every theme in the published gallery loads and matches its index entry`() {
        val entries = ThemeGalleryRepository.parseIndex(File(galleryDir, "index.json").readText())
        assertTrue("gallery should not be empty", entries.isNotEmpty())
        assertEquals("names must be unique", entries.size, entries.map { it.name.lowercase() }.toSet().size)
        entries.forEach { e ->
            val file = File(galleryDir, e.file)
            assertTrue("${e.file} is missing", file.isFile)
            val theme = ThemeFile.read(file.readBytes())
            assertEquals(e.id, e.installedId, theme.id)
            assertEquals(e.id, e.accent, theme.accent)
            assertEquals(e.id, e.accent2, theme.accent2)
        }
        // No stray theme files that the index doesn't list.
        val listed = entries.map { it.file }.toSet()
        val files = galleryDir.listFiles { f -> f.extension == ThemeFile.EXTENSION }!!.map { it.name }.toSet()
        assertEquals(listed, files)
    }

    @Test fun `index entries that don't validate are skipped`() {
        val list = ThemeGalleryRepository.parseIndex(
            """{"schemaVersion":1,"themes":[
                {"id":"ok","name":"OK","file":"ok.eortheme","accent":"#112233"},
                {"id":"Bad Id","name":"X","file":"Bad Id.eortheme","accent":"#112233"},
                {"id":"wrongfile","name":"X","file":"../evil.eortheme","accent":"#112233"},
                {"id":"nocolour","name":"X","file":"nocolour.eortheme","accent":"blue"},
                {"id":"noname","file":"noname.eortheme","accent":"#112233"}
            ]}"""
        )
        assertEquals(listOf("ok"), list.map { it.id })
    }

    @Test fun `an index from a newer gallery format is refused with a message`() {
        try {
            ThemeGalleryRepository.parseIndex("""{"schemaVersion":2,"themes":[]}"""); fail()
        } catch (e: ThemeFileException) {
            assertTrue(e.message!!.contains("newer eOr"))
        }
    }

    @Test fun `lists and installs from the gallery server`() = runTest {
        server.start()
        val custom = CustomThemeRepository(tmp.newFolder("themes"))
        val repo = ThemeGalleryRepository(OkHttpClient(), server.url("/themes/").toString(), custom)
        server.enqueue(MockResponse().setBody(File(galleryDir, "index.json").readText()))
        val dracula = File(galleryDir, "dracula.eortheme").readBytes()
        server.enqueue(MockResponse().setBody(Buffer().write(dracula)))

        val list = repo.list()
        val installed = repo.install(list.first { it.id == "dracula" })

        assertEquals("/themes/index.json", server.takeRequest().path)
        assertEquals("/themes/dracula.eortheme", server.takeRequest().path)
        assertEquals("Dracula", installed.name)
        assertEquals(listOf("custom-dracula"), custom.themes.value.map { it.id })
    }

    @Test fun `server errors become a readable message`() = runTest {
        server.start()
        server.enqueue(MockResponse().setResponseCode(404))
        val repo = ThemeGalleryRepository(
            OkHttpClient(), server.url("/themes/").toString(), CustomThemeRepository(tmp.newFolder("t"))
        )
        try {
            repo.list(); fail()
        } catch (e: ThemeFileException) {
            assertEquals("Theme gallery unavailable (HTTP 404)", e.message)
        }
    }
}
