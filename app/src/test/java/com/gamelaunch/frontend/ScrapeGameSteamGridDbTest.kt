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
import com.gamelaunch.frontend.domain.usecase.ScrapeResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ScrapeGameSteamGridDbTest {

    private val scraperRepository: ScraperRepository = mock()
    private val launchBox: ScrapeLaunchBoxUseCase = mock()
    private val libretro: LibretroThumbnailScraper = mock()
    private val sgdb: SteamGridDbScraper = mock()
    private val games: GameRepository = mock()
    private val media: MediaRepository = mock()
    private val useCase = ScrapeGameUseCase(scraperRepository, launchBox, libretro, sgdb, games, media)

    private val game = Game(id = 3, title = "Celeste", romPath = "/r/pico8/celeste.p8", romFilename = "celeste.p8", platformId = "pico8")
    // No ScreenScraper account: the chain is libretro → LaunchBox → (SteamGridDB).
    private val withKey = ScraperConfig(ssid = "", sspassword = "", steamGridDbKey = "k")

    @Test fun `when every other source misses, SteamGridDB art counts as a scrape`() = runTest {
        whenever(sgdb.findArt(eq("k"), eq("Celeste"), anyOrNull(), eq(true), eq(true)))
            .thenReturn(SteamGridDbScraper.Art("https://cdn/grid.png", "https://cdn/logo.png"))

        val result = useCase(game, withKey)

        assertTrue(result is ScrapeResult.Success)
        val saved = argumentCaptor<GameMedia>().also { verify(media).upsertMedia(it.capture()) }.firstValue
        assertEquals("https://cdn/grid.png", saved.boxArtRemoteUrl)
        assertEquals("https://cdn/logo.png", saved.wheelLogoRemoteUrl)
        verify(media).downloadAndCacheBoxArt(3, "https://cdn/grid.png")
        verify(games).markScraped(3, "Celeste")
    }

    @Test fun `only missing art is requested — existing covers are never replaced`() = runTest {
        whenever(media.getMediaForGame(3)).thenReturn(GameMedia(gameId = 3, boxArtLocalPath = "/m/boxart/3.jpg"))
        whenever(sgdb.findArt(any(), any(), anyOrNull(), any(), any())).thenReturn(SteamGridDbScraper.Art(null, "https://cdn/logo.png"))

        useCase(game, withKey)

        verify(sgdb).findArt("k", "Celeste", null, wantBoxArt = false, wantLogo = true)
        verify(media, never()).downloadAndCacheBoxArt(any(), any())
    }

    @Test fun `without a key SteamGridDB is never called`() = runTest {
        val result = useCase(game, ScraperConfig(ssid = "", sspassword = ""))

        assertTrue(result is ScrapeResult.NotFound)
        verify(sgdb, never()).findArt(any(), any(), anyOrNull(), any(), any())
    }

    @Test fun `steam library games pass their app id`() = runTest {
        val steamGame = game.copy(romPath = "steam:440", platformId = "steam", title = "Team Fortress 2")
        whenever(sgdb.findArt(any(), any(), anyOrNull(), any(), any())).thenReturn(null)

        useCase(steamGame, withKey)

        verify(sgdb).findArt("k", "Team Fortress 2", "440", wantBoxArt = true, wantLogo = true)
    }
}
