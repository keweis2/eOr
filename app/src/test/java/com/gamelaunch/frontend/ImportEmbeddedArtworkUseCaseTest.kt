package com.gamelaunch.frontend

import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.domain.repository.MediaRepository
import com.gamelaunch.frontend.domain.usecase.ImportEmbeddedArtworkUseCase
import com.gamelaunch.frontend.domain.usecase.NspArtworkExtractor
import com.gamelaunch.frontend.domain.usecase.ThreeDsArtworkExtractor
import com.gamelaunch.frontend.domain.usecase.ThreeDsBannerExtractor
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
    private val threeDsExtractor: ThreeDsArtworkExtractor = mock()
    private val bannerExtractor: ThreeDsBannerExtractor = mock()
    private val useCase = ImportEmbeddedArtworkUseCase(mediaRepository, extractor, threeDsExtractor, bannerExtractor)

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

    @Test fun `extracts and caches 3DS icons for each supported extension`() = runTest {
        for (extension in listOf("3ds", "cci", "CIA")) {
            val game = switchGame.copy(platformId = "3ds", romPath = "/roms/Game.$extension")
            val file = File(game.romPath)
            val artwork = byteArrayOf(extension.length.toByte())
            whenever(threeDsExtractor.extract(file)).thenReturn(artwork)

            useCase(game, file)

            verify(threeDsExtractor).extract(file)
        }
        verify(extractor, never()).extract(any())
        verify(mediaRepository, org.mockito.kotlin.times(3)).saveEmbeddedBoxArt(any(), any())
    }

    @Test fun `3DS existing artwork is preserved and missing icons are ignored`() = runTest {
        val game = switchGame.copy(platformId = "3ds", romPath = "/roms/Game.3ds")
        whenever(mediaRepository.getMediaForGame(game.id)).thenReturn(
            GameMedia(gameId = game.id, boxArtLocalPath = "/media/existing.png")
        )
        useCase(game, File(game.romPath))
        verify(threeDsExtractor, never()).extract(any())

        whenever(mediaRepository.getMediaForGame(game.id)).thenReturn(null)
        useCase(game, File(game.romPath))
        verify(mediaRepository, never()).saveEmbeddedBoxArt(any(), any())
    }

    @Test fun `existing 3DS icon does not suppress automatic device banner generation`() = runTest {
        val game = switchGame.copy(platformId = "3ds", romPath = "/roms/Game.cci")
        val png = byteArrayOf(1, 2, 3)
        whenever(mediaRepository.getMediaForGame(game.id)).thenReturn(
            GameMedia(gameId = game.id, boxArtLocalPath = "/icons/7.png"))
        whenever(bannerExtractor.extract(File(game.romPath))).thenReturn(png)

        useCase(game, File(game.romPath))

        verify(threeDsExtractor, never()).extract(any())
        verify(mediaRepository).saveEmbeddedBanner(game.id, png)
    }

    @Test fun `cached banner is reused without rendering`() = runTest {
        val file = kotlin.io.path.createTempFile().toFile()
        try {
            val game = switchGame.copy(platformId = "3ds")
            whenever(mediaRepository.getMediaForGame(game.id)).thenReturn(
                GameMedia(gameId = game.id, boxArtLocalPath = "/icons/7.png", bannerLocalPath = file.path))
            useCase(game, File("Game.cci"))
            verify(bannerExtractor, never()).extract(any())
        } finally { file.delete() }
    }

    @Test fun `missing cached file is regenerated and render failure preserves icon`() = runTest {
        val game = switchGame.copy(platformId = "3ds")
        whenever(mediaRepository.getMediaForGame(game.id)).thenReturn(
            GameMedia(gameId = game.id, boxArtLocalPath = "/icons/7.png", bannerLocalPath = "/missing/banner.png"))
        whenever(bannerExtractor.extract(any())).thenThrow(IllegalStateException("unsupported GPU"))
        useCase(game, File("Game.cci"))
        verify(bannerExtractor).extract(any())
        verify(mediaRepository, never()).saveEmbeddedBanner(any(), any())
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
