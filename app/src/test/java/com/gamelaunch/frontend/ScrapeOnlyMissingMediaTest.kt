package com.gamelaunch.frontend

import com.gamelaunch.frontend.data.network.SteamGridDbScraper
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.model.GameMedia
import com.gamelaunch.frontend.domain.model.ScraperConfig
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.MediaRepository
import com.gamelaunch.frontend.domain.repository.ScraperRepository
import com.gamelaunch.frontend.domain.usecase.LibretroThumbnailScraper
import com.gamelaunch.frontend.domain.usecase.ScrapeGameUseCase
import com.gamelaunch.frontend.domain.usecase.ScrapeLaunchBoxUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * A game queued for one gap (here: a screenshot) must not have its existing art — typically an ES-DE
 * library's cover — re-downloaded into eOr's folder and repointed at the copy.
 */
class ScrapeOnlyMissingMediaTest {

    private val scraperRepository: ScraperRepository = mock()
    private val launchBox: ScrapeLaunchBoxUseCase = mock()
    private val libretro: LibretroThumbnailScraper = mock()
    private val sgdb: SteamGridDbScraper = mock()
    private val games: GameRepository = mock()
    private val media: MediaRepository = mock()
    private val useCase = ScrapeGameUseCase(scraperRepository, launchBox, libretro, sgdb, games, media)

    private val game = Game(id = 7, title = "Sonic", romPath = "/r/megadrive/Sonic.md", romFilename = "Sonic.md", platformId = "genesis")
    private val noAccount = ScraperConfig(ssid = "", sspassword = "")

    @Test fun `existing ES-DE cover is kept, only the missing screenshot is fetched`() = runTest {
        whenever(media.getMediaForGame(7)).thenReturn(
            GameMedia(gameId = 7, boxArtLocalPath = "/sd/ES-DE/downloaded_media/megadrive/covers/Sonic.png")
        )
        whenever(libretro.fetch(any(), any())).thenReturn(
            LibretroThumbnailScraper.Thumbs("https://lr/box.png", "https://lr/shot.png", "Sonic")
        )

        useCase(game, noAccount)

        verify(media, never()).downloadAndCacheBoxArt(any(), any())
        verify(media).downloadAndCacheScreenshot(7, "https://lr/shot.png")
        val saved = argumentCaptor<GameMedia>().also { verify(media).upsertMedia(it.capture()) }.firstValue
        assertNull(saved.boxArtRemoteUrl)
        assertEquals("https://lr/shot.png", saved.screenshotRemoteUrl)
    }

    @Test fun `a game with no media still gets everything`() = runTest {
        whenever(libretro.fetch(any(), any())).thenReturn(
            LibretroThumbnailScraper.Thumbs("https://lr/box.png", "https://lr/shot.png", "Sonic")
        )

        useCase(game, noAccount)

        verify(media).downloadAndCacheBoxArt(7, "https://lr/box.png")
        verify(media).downloadAndCacheScreenshot(7, "https://lr/shot.png")
    }
}
