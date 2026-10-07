package com.gamelaunch.frontend

import com.gamelaunch.frontend.data.network.SteamGridDbApi
import com.gamelaunch.frontend.data.network.SteamGridDbScraper
import com.gamelaunch.frontend.data.network.dto.SgdbGameDto
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SteamGridDbScraperTest {

    private val server = MockWebServer()
    private lateinit var scraper: SteamGridDbScraper

    @Before fun setUp() {
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/api/v2/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SteamGridDbApi::class.java)
        scraper = SteamGridDbScraper(api)
    }

    @After fun tearDown() = server.shutdown()

    private fun ok(json: String) = MockResponse().setBody(json)
    private fun images(vararg urls: String, nsfwFirst: Boolean = false) =
        """{"success":true,"data":[""" + urls.mapIndexed { i, u ->
            """{"id":$i,"url":"$u","nsfw":${nsfwFirst && i == 0},"humor":false,"epilepsy":false}"""
        }.joinToString(",") + "]}"

    @Test fun `validate accepts a working key and rejects a 401`() = runTest {
        server.enqueue(ok("""{"success":true,"data":[]}"""))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"success":false,"errors":["Authentication Required"]}"""))

        assertTrue(scraper.validate(" key123 "))
        assertEquals("Bearer key123", server.takeRequest().getHeader("Authorization"))
        assertFalse(scraper.validate("bad"))
    }

    @Test fun `finds cover and logo by title, skipping unsafe images`() = runTest {
        server.enqueue(ok("""{"success":true,"data":[{"id":7,"name":"Celeste"},{"id":8,"name":"Celeste Classic"}]}"""))
        server.enqueue(ok(images("https://cdn/nsfw.png", "https://cdn/grid.png", nsfwFirst = true)))
        server.enqueue(ok(images("https://cdn/logo.png")))

        val art = scraper.findArt("k", "Celeste (USA)", steamAppId = null, wantBoxArt = true, wantLogo = true)

        assertEquals(SteamGridDbScraper.Art("https://cdn/grid.png", "https://cdn/logo.png"), art)
        assertEquals("/api/v2/search/autocomplete/Celeste", server.takeRequest().path)
        val grids = server.takeRequest().path!!
        assertTrue(grids, grids.startsWith("/api/v2/grids/game/7?dimensions=600x900"))
        assertTrue(server.takeRequest().path!!.startsWith("/api/v2/logos/game/7"))
    }

    @Test fun `steam games go straight to the app id with no search`() = runTest {
        server.enqueue(ok(images("https://cdn/steamgrid.png")))

        val art = scraper.findArt("k", "Whatever", steamAppId = "440", wantBoxArt = true, wantLogo = false)

        assertEquals("https://cdn/steamgrid.png", art?.boxArt)
        assertTrue(server.takeRequest().path!!.startsWith("/api/v2/grids/steam/440"))
        assertEquals(1, server.requestCount)
    }

    @Test fun `no confident title match means no art`() = runTest {
        server.enqueue(ok("""{"success":true,"data":[{"id":1,"name":"Mario Kart 8 Deluxe"}]}"""))

        assertNull(scraper.findArt("k", "Super Mario Bros.", null, wantBoxArt = true, wantLogo = true))
        assertEquals(1, server.requestCount)
    }

    @Test fun `title matching is strict but forgiving about punctuation and articles`() {
        fun c(vararg names: String) = names.mapIndexed { i, n -> SgdbGameDto(id = i.toLong(), name = n) }
        assertEquals("Legend of Zelda, The",
            SteamGridDbScraper.bestMatch("The Legend of Zelda (USA) (Rev 1)", c("Zelda II", "Legend of Zelda, The"))?.name)
        assertEquals("Ratchet & Clank",
            SteamGridDbScraper.bestMatch("Ratchet and Clank", c("Ratchet & Clank"))?.name)
        assertNull(SteamGridDbScraper.bestMatch("Doom", c("Doom Eternal", "Doom 64")))
        assertNull(SteamGridDbScraper.bestMatch("", c("Doom")))
    }

    @Test fun `search terms drop tags and path-breaking characters`() {
        assertEquals("Half-Life 2 Episode One", SteamGridDbScraper.searchTerm("Half-Life 2 / Episode One [!] (USA)"))
    }

    @Test fun `only plain Steam-library paths carry a Steam app id`() {
        assertEquals("440", SteamGridDbScraper.steamAppId("steam:440"))
        assertNull(SteamGridDbScraper.steamAppId("steam:EPIC:fn"))
        assertNull(SteamGridDbScraper.steamAppId("/storage/ROMs/nes/a.nes"))
    }
}
