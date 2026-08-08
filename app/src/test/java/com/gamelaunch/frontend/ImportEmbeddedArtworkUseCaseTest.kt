package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.domain.repository.MediaRepository
import com.gamelaunch.frontend.domain.usecase.ImportEmbeddedArtworkUseCase
import com.gamelaunch.frontend.domain.usecase.NspArtworkExtractor
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

class ImportEmbeddedArtworkUseCaseTest {
    private val mediaRepository: MediaRepository = mock()
    private val extractor: NspArtworkExtractor = mock()
    private val useCase = ImportEmbeddedArtworkUseCase(mediaRepository, extractor)

    private val switchGame = Game(
        id = 7L,
        title = "Game",
        romPath = "/roms/Game.nsp",
        romFilename = "Game.nsp",
        platformId = "switch"
    )

    @Test fun `extracts and caches artwork from a Switch NSP`() = runTest {
        val file = File(switchGame.romPath)
        val artwork = byteArrayOf(1, 2, 3)
        whenever(mediaRepository.getMediaForGame(switchGame.id)).thenReturn(null)
        whenever(extractor.extract(file)).thenReturn(artwork)

        useCase(switchGame, file)

        verify(mediaRepository).saveEmbeddedBoxArt(switchGame.id, artwork)
    }

    @Test fun `existing local artwork skips extraction`() = runTest {
        val file = File(switchGame.romPath)
        whenever(mediaRepository.getMediaForGame(switchGame.id)).thenReturn(
            GameMedia(gameId = switchGame.id, boxArtLocalPath = "/media/7.jpg")
        )

        useCase(switchGame, file)

        verify(extractor, never()).extract(any())
        verify(mediaRepository, never()).saveEmbeddedBoxArt(any(), any())
    }

    @Test fun `unsupported games are ignored without querying media`() = runTest {
        val nesGame = switchGame.copy(platformId = "nes", romPath = "/roms/Game.nes")

        useCase(nesGame, File(nesGame.romPath))
        useCase(switchGame, File("/roms/Game.xci"))

        verify(mediaRepository, never()).getMediaForGame(any())
        verify(extractor, never()).extract(any())
    }

    @Test fun `missing embedded artwork does not write media`() = runTest {
        val file = File(switchGame.romPath)
        whenever(mediaRepository.getMediaForGame(switchGame.id)).thenReturn(null)
        whenever(extractor.extract(file)).thenReturn(null)

        useCase(switchGame, file)

        verify(mediaRepository, never()).saveEmbeddedBoxArt(any(), any())
    }

    @Test fun `extraction failures are best effort`() = runTest {
        val file = File(switchGame.romPath)
        whenever(mediaRepository.getMediaForGame(switchGame.id)).thenReturn(null)
        whenever(extractor.extract(file)).thenThrow(IllegalStateException("broken package"))

        useCase(switchGame, file)
    }

    @Test fun `storage failures are best effort`() = runTest {
        val file = File(switchGame.romPath)
        whenever(mediaRepository.getMediaForGame(switchGame.id)).thenReturn(null)
        whenever(extractor.extract(file)).thenReturn(byteArrayOf(1))
        doThrow(IllegalStateException("storage unavailable"))
            .whenever(mediaRepository).saveEmbeddedBoxArt(any(), any())

        useCase(switchGame, file)
    }
}
