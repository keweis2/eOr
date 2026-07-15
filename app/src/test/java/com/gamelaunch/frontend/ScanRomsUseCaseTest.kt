package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.platform.PlatformDetector
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.usecase.ScanRomsUseCase
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
import java.io.File

class ScanRomsUseCaseTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private val gameRepository: GameRepository = mock()
    private val platformDetector = PlatformDetector()
    private lateinit var useCase: ScanRomsUseCase

    @Before fun setup() {
        useCase = ScanRomsUseCase(gameRepository, platformDetector)
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
}
