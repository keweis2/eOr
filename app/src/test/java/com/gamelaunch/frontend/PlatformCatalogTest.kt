package com.gamelaunch.frontend

import com.gamelaunch.frontend.data.catalog.CatalogParser
import com.gamelaunch.frontend.domain.model.LaunchSpec
import com.gamelaunch.frontend.domain.model.RomUriMode
import com.gamelaunch.frontend.domain.platform.PlatformCatalog
import com.gamelaunch.frontend.domain.platform.PlatformDefinitions
import com.gamelaunch.frontend.domain.platform.PlatformDetector
import com.gamelaunch.frontend.domain.platform.PlatformMetadata
import com.gamelaunch.frontend.ui.component.bundledIconKeys
import com.gamelaunch.frontend.ui.component.platformIcon
import com.gamelaunch.frontend.ui.component.platformLabel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PlatformCatalogTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private val builtIn = PlatformDefinitions.BUILT_IN_CATALOG

    @After fun tearDown() = PlatformDefinitions.reset()

    private fun ok(json: String, versionCode: Int = 1000): CatalogParser.Result.Ok =
        when (val r = CatalogParser.parse(json, versionCode)) {
            is CatalogParser.Result.Ok -> r
            is CatalogParser.Result.Invalid -> fail("expected Ok, got ${r.reason}") as Nothing
        }

    private fun doc(platforms: String = "[]", emulators: String = "[]", extra: String = "") =
        """{"schemaVersion":1,"revision":5$extra,"platforms":$platforms,"emulators":$emulators}"""

    private val snesOverlay = """[{"id":"snes","displayName":"Super Nintendo","scraperSystemId":4,
        "extensions":[".bs"],"folderNames":["Satellaview"]}]"""

    // ── Published file ───────────────────────────────────────────────────────

    /** Gradle runs unit tests from the module dir; the published catalog sits at the repo root. */
    private val publishedFile = File("../catalog/catalog.json")

    @Test fun `built-in catalog survives a JSON round trip`() {
        val parsed = ok(CatalogParser.toJson(builtIn.copy(revision = 1), minAppVersionCode = 1))
        assertTrue(parsed.rejected.isEmpty())
        assertEquals(builtIn.platforms, parsed.catalog.platforms)
        assertEquals(builtIn.emulators, parsed.catalog.emulators)
        assertEquals(builtIn.emulatorPriority, parsed.catalog.emulatorPriority)
        assertEquals(builtIn.launchSpecs, parsed.catalog.launchSpecs)
    }

    @Test fun `published catalog parses cleanly and is a superset of the built-in data`() {
        // Validate content regardless of minAppVersionCode (gating is tested separately).
        val published = ok(publishedFile.readText(), versionCode = Int.MAX_VALUE)
        assertTrue("rejected: ${published.rejected}", published.rejected.isEmpty())

        val merged = builtIn.mergedWith(published.catalog)
        builtIn.platforms.forEach { p ->
            val m = merged.byId.getValue(p.id)
            assertTrue("${p.id} lost extensions", m.extensions.containsAll(p.extensions))
            assertTrue("${p.id} lost folders", m.folderNames.containsAll(p.folderNames))
        }
        assertTrue(merged.emulatorPackages.containsAll(builtIn.emulatorPackages))
        // Changing a verified launch recipe should be deliberate: update the built-in one too.
        builtIn.launchSpecs.forEach { (pkg, spec) ->
            assertEquals("launch spec for $pkg differs from built-in", spec, merged.launchSpecs[pkg])
        }
    }

    @Test fun `every system the published catalog adds is fully presented`() {
        val published = ok(publishedFile.readText(), versionCode = Int.MAX_VALUE).catalog
        val added = published.platforms.filter { it.id !in builtIn.byId }
        assertTrue("catalog should add systems", added.isNotEmpty())
        added.forEach { p ->
            // A missing/unknown icon key would silently fall back to a generic controller.
            assertTrue("${p.id}: icon '${p.iconKey}' not bundled", p.iconKey in bundledIconKeys)
            assertNotNull("${p.id}: label", p.label)
            assertNotNull("${p.id}: kind", p.kind)
            assertNotNull("${p.id}: releaseYear", p.releaseYear)
            assertNotNull("${p.id}: brand", p.brand)
            assertNotNull("${p.id}: RetroArch core", p.defaultCoreForRetroArch)
            assertTrue("${p.id}: needs an auto-detect emulator", published.emulatorPriority[p.id].orEmpty().isNotEmpty())
            assertFalse("${p.id}: catalog systems should be folder-only", p.detectByExtension)
        }
    }

    @Test fun `published catalog never relies on a folder another system already owns`() {
        val published = ok(publishedFile.readText(), versionCode = Int.MAX_VALUE).catalog
        val owner = mutableMapOf<String, String>()
        builtIn.mergedWith(published).platforms.forEach { p ->
            p.folderNames.forEach { f ->
                val prev = owner.putIfAbsent(f.lowercase(), p.id)
                assertTrue("folder '$f' on ${p.id} is already owned by $prev", prev == null || prev == p.id)
            }
        }
    }

    @Test fun `older apps ignore the grown catalog`() {
        assertTrue(CatalogParser.parse(publishedFile.readText(), 47) is CatalogParser.Result.Invalid)
    }

    // ── Merge rules ──────────────────────────────────────────────────────────

    @Test fun `overlay adds extensions and folders but never removes them`() {
        val merged = builtIn.mergedWith(ok(doc(platforms = snesOverlay)).catalog)
        val snes = merged.byId.getValue("snes")
        assertTrue(snes.extensions.containsAll(builtIn.byId.getValue("snes").extensions + ".bs"))
        assertEquals("snes", merged.byFolderName["satellaview"]?.id)
        assertEquals(builtIn.platforms.size, merged.platforms.size)
    }

    @Test fun `new platform is appended after built-ins`() {
        val overlay = ok(doc(platforms = """[{"id":"vectrex","displayName":"Vectrex","scraperSystemId":102,
            "extensions":[".vec"],"folderNames":["vectrex"],"retroArchCore":"vecx_libretro.so",
            "emulators":["com.retroarch.aarch64"]}]""")).catalog
        val merged = builtIn.mergedWith(overlay)
        assertEquals("vectrex", merged.platforms.last().id)
        assertEquals("vectrex", merged.byFolderName["vectrex"]?.id)
        assertNull("catalog systems are folder-only by default", merged.byExtension[".vec"])
        assertEquals(listOf("com.retroarch.aarch64"), merged.emulatorPriority["vectrex"])
        assertEquals(builtIn.platforms, merged.platforms.dropLast(1))
    }

    @Test fun `overlay priority leads but built-in fallbacks are kept`() {
        val overlay = ok(doc(platforms = """[{"id":"switch","displayName":"Nintendo Switch","scraperSystemId":225,
            "extensions":[".nsp"],"folderNames":["switch"],"emulators":["com.example.newfork","dev.eden.emulator"]}]""")).catalog
        val priority = builtIn.mergedWith(overlay).emulatorPriority.getValue("switch")
        assertEquals(listOf("com.example.newfork", "dev.eden.emulator"), priority.take(2))
        assertTrue(priority.containsAll(builtIn.emulatorPriority.getValue("switch")))
    }

    @Test fun `overlay launch spec replaces the built-in one`() {
        val overlay = ok(doc(emulators = """[{"packageName":"org.ppsspp.ppsspp","displayName":"PPSSPP (PSP)",
            "launch":{"activity":"org.ppsspp.ppsspp.NewActivity","action":"MAIN","romExtraKey":"path","romUri":"CONTENT"}}]""")).catalog
        val spec = builtIn.mergedWith(overlay).launchSpecs.getValue("org.ppsspp.ppsspp")
        assertEquals(
            LaunchSpec("org.ppsspp.ppsspp.NewActivity", "path", LaunchSpec.ACTION_MAIN, null, RomUriMode.CONTENT),
            spec
        )
    }

    @Test fun `installed catalog reaches the ROM scanner`() {
        PlatformDefinitions.install(ok(doc(platforms = """[{"id":"vectrex","displayName":"Vectrex",
            "scraperSystemId":102,"extensions":[".vec"],"folderNames":["Vectrex"]}]""")).catalog)
        val dir = tmpFolder.newFolder("Vectrex")
        val rom = File(dir, "minestorm.vec").also { it.createNewFile() }
        assertEquals("vectrex", PlatformDetector().detect(rom, "Vectrex")?.id)

        PlatformDefinitions.reset()
        assertNull(PlatformDetector().detect(rom, "Vectrex"))
    }

    @Test fun `catalog-added system cannot take over an existing folder`() {
        val overlay = ok(doc(platforms = """[{"id":"thief","displayName":"Thief","scraperSystemId":0,
            "extensions":[".sfc"],"folderNames":["snes","thief"]}]""")).catalog
        val merged = builtIn.mergedWith(overlay)
        assertEquals("snes", merged.byFolderName["snes"]?.id)
        assertEquals("thief", merged.byFolderName["thief"]?.id)
    }

    @Test fun `catalog-added systems match by folder only`() {
        PlatformDefinitions.install(ok(doc(platforms = """[{"id":"dos","displayName":"DOS","scraperSystemId":135,
            "extensions":[".exe",".dat"],"folderNames":["dos"]}]""")).catalog)
        val loose = File(tmpFolder.newFolder("roms"), "readme.dat").also { it.createNewFile() }
        assertNull(PlatformDetector().detect(loose, "roms"))
        val inFolder = File(tmpFolder.newFolder("dos"), "game.exe").also { it.createNewFile() }
        assertEquals("dos", PlatformDetector().detect(inFolder, "dos")?.id)
    }

    @Test fun `presentation fields reach icons, labels and sorting`() {
        PlatformDefinitions.install(ok(doc(platforms = """[{"id":"atarilynx","displayName":"Atari Lynx",
            "scraperSystemId":28,"extensions":[".lnx"],"folderNames":["atarilynx"],"icon":"lynx","label":"LYNX",
            "pad":"handheld","releaseYear":1989,"brand":"Atari","kind":"handheld",
            "libretroThumbnails":"Atari - Lynx","esdeDirs":["atarilynx"]}]""")).catalog)
        assertNotNull(platformIcon("atarilynx"))
        assertEquals("LYNX", platformLabel("atarilynx"))
        assertEquals(1989, PlatformMetadata.year("atarilynx"))
        assertEquals("Atari", PlatformMetadata.brand("atarilynx"))
        // Built-in systems are untouched when the catalog doesn't set these fields.
        assertEquals("SNES", platformLabel("snes"))
        assertEquals(1990, PlatformMetadata.year("snes"))
    }

    @Test fun `invalid presentation fields reject the platform`() {
        val base = """"id":"x","displayName":"X","scraperSystemId":1,"extensions":[".x"],"folderNames":["x"]"""
        listOf(""""icon":"../a"""", """"pad":"wheel"""", """"kind":"toaster"""", """"coverAspect":9""",
               """"releaseYear":1800""", """"libretroThumbnails":"a/b"""", """"esdeDirs":[".."]""")
            .forEach { bad ->
                val r = ok(doc(platforms = "[{$base,$bad}]"))
                assertTrue("accepted $bad", r.catalog.platforms.isEmpty())
            }
    }

    // ── Validation ───────────────────────────────────────────────────────────

    @Test fun `whole document rejected for unknown schema, missing revision or newer app requirement`() {
        assertTrue(CatalogParser.parse("""{"schemaVersion":2,"revision":1}""", 1000) is CatalogParser.Result.Invalid)
        assertTrue(CatalogParser.parse("""{"schemaVersion":1}""", 1000) is CatalogParser.Result.Invalid)
        assertTrue(CatalogParser.parse(doc(extra = ""","minAppVersionCode":2000"""), 1000) is CatalogParser.Result.Invalid)
        assertTrue(CatalogParser.parse("not json", 1000) is CatalogParser.Result.Invalid)
        assertTrue(CatalogParser.parse("[]", 1000) is CatalogParser.Result.Invalid)
    }

    @Test fun `bad platform entries are dropped individually`() {
        val result = ok(doc(platforms = """[
            {"id":"BAD ID","displayName":"x","scraperSystemId":1,"extensions":[".x"],"folderNames":["x"]},
            {"id":"a","displayName":"A","scraperSystemId":1,"extensions":["noDot"],"folderNames":["a"]},
            {"id":"b","displayName":"B","scraperSystemId":1,"extensions":[".b"],"folderNames":["../etc"]},
            {"id":"c","displayName":"C","scraperSystemId":1,"extensions":[".c", null],"folderNames":["c"]},
            {"id":"d","displayName":"D","scraperSystemId":1,"extensions":[".d"],"folderNames":["d"],"retroArchCore":"../evil.so"},
            {"id":"ok","displayName":"OK","scraperSystemId":1,"extensions":[".OK"],"folderNames":["ok"]}
        ]"""))
        assertEquals(listOf("ok"), result.catalog.platforms.map { it.id })
        assertEquals(listOf(".ok"), result.catalog.platforms.single().extensions)
        assertEquals(5, result.rejected.size)
    }

    @Test fun `emulator with an invalid launch recipe is dropped entirely`() {
        val result = ok(doc(emulators = """[
            {"packageName":"com.a.emu","displayName":"A","launch":{"activity":"com.a.Main","action":"SEND"}},
            {"packageName":"com.b.emu","displayName":"B","launch":{"activity":"not qualified"}},
            {"packageName":"com.c.emu","displayName":"C","launch":{"activity":"com.c.Main","romUri":"HTTP"}},
            {"packageName":"com.d.emu","displayName":"D","launch":{"activity":"com.d.Main","mimeType":"text/html; x"}},
            {"packageName":"bad","displayName":"E"},
            {"packageName":"com.ok.emu","displayName":"OK","launch":{"activity":"com.ok.Main"}},
            {"packageName":"com.plain.emu","displayName":"Plain"}
        ]"""))
        assertEquals(listOf("com.ok.emu", "com.plain.emu"), result.catalog.emulators.map { it.packageName })
        assertNotNull(result.catalog.launchSpecs["com.ok.emu"])
        assertNull(result.catalog.launchSpecs["com.plain.emu"])
        assertEquals(5, result.rejected.size)
    }
}
