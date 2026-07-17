package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.platform.PlatformDetector
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.SettingsRepository
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
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ScanRomsUseCaseTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private val gameRepository: GameRepository = mock()
    private val settingsRepository: SettingsRepository = mock()
    private val platformDetector = PlatformDetector()
    private lateinit var useCase: ScanRomsUseCase

    @Before fun setup() {
        whenever(settingsRepository.excludedPaths).thenReturn(flowOf(emptySet()))
        useCase = ScanRomsUseCase(gameRepository, platformDetector, settingsRepository)
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
}
