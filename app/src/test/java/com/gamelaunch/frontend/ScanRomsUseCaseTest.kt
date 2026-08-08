package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.platform.PlatformDetector
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.domain.usecase.ImportEmbeddedArtworkUseCase
import com.gamelaunch.frontend.domain.usecase.ScanRomsUseCase
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ScanRomsUseCaseTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private val gameRepository: GameRepository = mock()
    private val settingsRepository: SettingsRepository = mock()
    private val importEmbeddedArtwork: ImportEmbeddedArtworkUseCase = mock()
    private val platformDetector = PlatformDetector()
    private lateinit var useCase: ScanRomsUseCase

    @Before fun setup() {
        whenever(settingsRepository.excludedPaths).thenReturn(flowOf(emptySet()))
        useCase = ScanRomsUseCase(
            gameRepository, platformDetector, settingsRepository, importEmbeddedArtwork
        )
    }

    @Test fun `emits error progress for missing root folder`() = runTest {
        val results = useCase("/nonexistent/path").toList()
        assertEquals(1, results.size)
        assertTrue(results[0].currentFile.startsWith("Root folder not found"))
    }

    @Test fun `detects nes roms in NES subfolder`() = runTest {
        val nesDir = tmpFolder.newFolder("NES")
        File(nesDir, "mario.nes").createNewFile()
        File(nesDir, "zelda.nes").createNewFile()

        whenever(gameRepository.insertGame(any())).thenReturn(1L, 2L)
        whenever(gameRepository.deleteGamesNotInPaths(any())).thenReturn(0)

        val results = useCase(tmpFolder.root.absolutePath).toList()
        val final = results.last()
        assertEquals(2, final.added)
        assertEquals(2, final.total)
    }

    @Test fun `detects zipped roms inside a system folder`() = runTest {
        val snesDir = tmpFolder.newFolder("SNES")
        File(snesDir, "Super Mario World.zip").createNewFile()
        File(snesDir, "Chrono Trigger.7z").createNewFile()

        whenever(gameRepository.insertGame(any())).thenReturn(1L, 2L)
        whenever(gameRepository.deleteGamesNotInPaths(any())).thenReturn(0)

        val results = useCase(tmpFolder.root.absolutePath).toList()
        val final = results.last()
        assertEquals(2, final.added)
        assertEquals(2, final.total)
    }

    @Test fun `skips paths the user excluded`() = runTest {
        val nesDir = tmpFolder.newFolder("NES")
        val keep = File(nesDir, "keep.nes").also { it.createNewFile() }
        val removed = File(nesDir, "removed.nes").also { it.createNewFile() }
        whenever(settingsRepository.excludedPaths).thenReturn(flowOf(setOf(removed.absolutePath)))

        whenever(gameRepository.insertGame(any())).thenReturn(1L)
        whenever(gameRepository.deleteGamesNotInPaths(any())).thenReturn(0)

        val results = useCase(tmpFolder.root.absolutePath).toList()
        val final = results.last()
        assertEquals(1, final.total) // only keep.nes counted; excluded file skipped
    }

    @Test fun `skips txt and xml files`() = runTest {
        val dir = tmpFolder.newFolder("SNES")
        File(dir, "game.sfc").createNewFile()
        File(dir, "readme.txt").createNewFile()
        File(dir, "gamelist.xml").createNewFile()

        whenever(gameRepository.insertGame(any())).thenReturn(1L)
        whenever(gameRepository.deleteGamesNotInPaths(any())).thenReturn(0)

        val results = useCase(tmpFolder.root.absolutePath).toList()
        val final = results.last()
        assertEquals(1, final.total) // only .sfc counted
    }

    @Test fun `skips firmware files inside a platform folder`() = runTest {
        val switchDir = tmpFolder.newFolder("switch")
        File(switchDir, "game.nsp").createNewFile()
        val firmwareDir = File(switchDir, "firmwares").also { it.mkdir() }
        File(firmwareDir, "Firmware.zip").createNewFile()

        whenever(gameRepository.insertGame(any())).thenReturn(1L)
        whenever(gameRepository.deleteGamesNotInPaths(any())).thenReturn(0)

        val results = useCase(tmpFolder.root.absolutePath).toList()
        val final = results.last()

        assertEquals(1, final.total)
        assertEquals(1, final.added)
    }

    @Test fun `skips Switch update NSPs but keeps the base package`() = runTest {
        val switchDir = tmpFolder.newFolder("switch")
        val base = File(switchDir, "Base Game [v0].nsp").also { it.createNewFile() }
        File(switchDir, "Patch Package [AAAAAAAAAAAAA800].nsp").createNewFile()

        whenever(gameRepository.insertGame(any())).thenReturn(1L)
        whenever(gameRepository.deleteGamesNotInPaths(any())).thenReturn(0)

        val results = useCase(tmpFolder.root.absolutePath).toList()
        val gameCaptor = argumentCaptor<Game>()
        val pathsCaptor = argumentCaptor<List<String>>()
        verify(gameRepository).insertGame(gameCaptor.capture())
        verify(gameRepository).deleteGamesNotInPaths(pathsCaptor.capture())

        assertEquals(1, results.last().total)
        assertEquals(1, results.last().added)
        assertEquals(base.absolutePath, gameCaptor.firstValue.romPath)
        assertEquals(listOf(base.absolutePath), pathsCaptor.firstValue)
    }

    @Test fun `keeps untagged Switch NSPs when update status is unknown`() = runTest {
        val switchDir = tmpFolder.newFolder("switch")
        val gameFile = File(switchDir, "Unknown Game.nsp").also { it.createNewFile() }

        whenever(gameRepository.insertGame(any())).thenReturn(1L)
        whenever(gameRepository.deleteGamesNotInPaths(any())).thenReturn(0)

        useCase(tmpFolder.root.absolutePath).toList()
        val gameCaptor = argumentCaptor<Game>()
        verify(gameRepository).insertGame(gameCaptor.capture())

        assertEquals(gameFile.absolutePath, gameCaptor.firstValue.romPath)
    }

    @Test fun `imports embedded artwork with returned id for a newly inserted game`() = runTest {
        val switchDir = tmpFolder.newFolder("switch")
        val gameFile = File(switchDir, "Game.nsp").also { it.createNewFile() }
        whenever(gameRepository.insertGame(any())).thenReturn(42L)
        whenever(gameRepository.deleteGamesNotInPaths(any())).thenReturn(0)

        useCase(tmpFolder.root.absolutePath).toList()

        val gameCaptor = argumentCaptor<Game>()
        verify(importEmbeddedArtwork).invoke(gameCaptor.capture(), org.mockito.kotlin.eq(gameFile))
        assertEquals(42L, gameCaptor.firstValue.id)
        verify(gameRepository, never()).getGameByRomPath(any())
    }

    @Test fun `resolves an existing game by rom path before importing embedded artwork`() = runTest {
        val switchDir = tmpFolder.newFolder("switch")
        val gameFile = File(switchDir, "Existing.nsp").also { it.createNewFile() }
        val persisted = Game(
            id = 24L,
            title = "Existing",
            romPath = gameFile.absolutePath,
            romFilename = gameFile.name,
            platformId = "switch"
        )
        whenever(gameRepository.insertGame(any())).thenReturn(-1L)
        whenever(gameRepository.getGameByRomPath(gameFile.absolutePath)).thenReturn(persisted)
        whenever(gameRepository.deleteGamesNotInPaths(any())).thenReturn(0)

        val results = useCase(tmpFolder.root.absolutePath).toList()

        verify(importEmbeddedArtwork).invoke(persisted, gameFile)
        assertEquals(0, results.last().added)
    }

    @Test fun `skips Switch DLC NSPs labelled without brackets`() = runTest {
        val switchDir = tmpFolder.newFolder("switch")
        File(switchDir, "Base Game [v0].nsp").createNewFile()
        File(switchDir, "Base Game - Extra DLC [v0].nsp").createNewFile()

        whenever(gameRepository.insertGame(any())).thenReturn(1L)
        whenever(gameRepository.deleteGamesNotInPaths(any())).thenReturn(0)

        val results = useCase(tmpFolder.root.absolutePath).toList()
        val gameCaptor = argumentCaptor<Game>()
        verify(gameRepository).insertGame(gameCaptor.capture())

        assertEquals(1, results.last().total)
        assertEquals("Base Game [v0].nsp", gameCaptor.firstValue.romFilename)
    }

    @Test fun `keeps non Switch games whose filename contains DLC`() = runTest {
        val nesDir = tmpFolder.newFolder("NES")
        val gameFile = File(nesDir, "Example DLC.nes").also { it.createNewFile() }

        whenever(gameRepository.insertGame(any())).thenReturn(1L)
        whenever(gameRepository.deleteGamesNotInPaths(any())).thenReturn(0)

        useCase(tmpFolder.root.absolutePath).toList()
        val gameCaptor = argumentCaptor<Game>()
        verify(gameRepository).insertGame(gameCaptor.capture())

        assertEquals(gameFile.absolutePath, gameCaptor.firstValue.romPath)
    }

    @Test fun `computes md5 on uncompressed contents of zip files`() = runTest {
        val snesDir = tmpFolder.newFolder("SNES")
        val zipFile = File(snesDir, "game.zip")

        ZipOutputStream(zipFile.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("inner.sfc"))
            zos.write("ROMDATA".toByteArray())
            zos.closeEntry()
        }

        whenever(gameRepository.insertGame(any())).thenReturn(1L)
        whenever(gameRepository.deleteGamesNotInPaths(any())).thenReturn(0)

        useCase(tmpFolder.root.absolutePath).toList()

        val gameCaptor = argumentCaptor<Game>()
        verify(gameRepository).insertGame(gameCaptor.capture())

        val expectedMd5 = MessageDigest.getInstance("MD5")
            .digest("ROMDATA".toByteArray())
            .joinToString("") { "%02x".format(it) }

        assertEquals(expectedMd5, gameCaptor.firstValue.md5)
    }

    /**
     * A single ZipInputStream.read() returns only the first inflate chunk, not the
     * whole entry. For an inner ROM that is larger than one chunk but still under the
     * 512 KB partial-hash window, the hash must cover the ENTIRE inner file so it
     * equals the ROM's full-file md5 (what ScreenScraper matches on). Uses pseudo-random,
     * poorly-compressible data so the inner stream spans several inflate reads.
     */
    @Test fun `hashes full inner rom when it spans multiple inflate chunks`() = runTest {
        val innerRom = ByteArray(200 * 1024).also { java.util.Random(42).nextBytes(it) }

        val snesDir = tmpFolder.newFolder("SNES")
        val zipFile = File(snesDir, "big.zip")
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("inner.sfc"))
            zos.write(innerRom)
            zos.closeEntry()
        }

        whenever(gameRepository.insertGame(any())).thenReturn(1L)
        whenever(gameRepository.deleteGamesNotInPaths(any())).thenReturn(0)

        useCase(tmpFolder.root.absolutePath).toList()

        val gameCaptor = argumentCaptor<Game>()
        verify(gameRepository).insertGame(gameCaptor.capture())

        val expectedMd5 = MessageDigest.getInstance("MD5")
            .digest(innerRom)
            .joinToString("") { "%02x".format(it) }

        assertEquals(expectedMd5, gameCaptor.firstValue.md5)
    }

    @Test fun `detects ps1 cue file and filters out bin files referenced by cue`() = runTest {
        val ps1Dir = tmpFolder.newFolder("PS1")
        val cueFile = File(ps1Dir, "game.cue")
        cueFile.writeText("""
            FILE "game (Track 1).bin" BINARY
            FILE "game (Track 2).bin" BINARY
        """.trimIndent())

        File(ps1Dir, "game (Track 1).bin").createNewFile()
        File(ps1Dir, "game (Track 2).bin").createNewFile()

        whenever(gameRepository.insertGame(any())).thenReturn(1L)
        whenever(gameRepository.deleteGamesNotInPaths(any())).thenReturn(0)

        val results = useCase(tmpFolder.root.absolutePath).toList()
        val final = results.last()

        // Only the .cue file should be counted and added, the .bin files are filtered out
        assertEquals(1, final.total)
        assertEquals(1, final.added)
    }

    @Test fun `detects ps1 m3u file and filters out cue and bin files referenced by m3u and cue`() = runTest {
        val ps1Dir = tmpFolder.newFolder("PS1")

        val m3uFile = File(ps1Dir, "game.m3u")
        m3uFile.writeText("""
            game (Disc 1).cue
            game (Disc 2).cue
        """.trimIndent())

        val cue1 = File(ps1Dir, "game (Disc 1).cue")
        cue1.writeText("FILE \"game (Disc 1) (Track 1).bin\" BINARY")
        File(ps1Dir, "game (Disc 1) (Track 1).bin").createNewFile()

        val cue2 = File(ps1Dir, "game (Disc 2).cue")
        cue2.writeText("FILE \"game (Disc 2) (Track 1).bin\" BINARY")
        File(ps1Dir, "game (Disc 2) (Track 1).bin").createNewFile()

        whenever(gameRepository.insertGame(any())).thenReturn(1L)
        whenever(gameRepository.deleteGamesNotInPaths(any())).thenReturn(0)

        val results = useCase(tmpFolder.root.absolutePath).toList()
        val final = results.last()

        // Only the .m3u file should be counted and added, cues and bins are filtered out
        assertEquals(1, final.total)
        assertEquals(1, final.added)
    }
}
