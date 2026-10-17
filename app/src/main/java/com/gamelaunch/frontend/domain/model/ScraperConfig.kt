package com.gamelaunch.frontend.domain.model

import com.gamelaunch.frontend.BuildConfig
import com.gamelaunch.frontend.data.network.Secrets

data class ScraperConfig(
    val ssid: String = "",
    val sspassword: String = "",
    // Developer API credentials are obfuscated in BuildConfig; decode them at use time.
    val devid: String = Secrets.reveal(BuildConfig.SS_DEV_ID),
    val devpassword: String = Secrets.reveal(BuildConfig.SS_DEV_PASSWORD),
    val softname: String = "eOr",
    val preferredRegion: String = "us",
    val scrapeMetadata: Boolean = true,
    val scrapeBoxArt: Boolean = true,
    val scrapeScreenshots: Boolean = true,
    val scrapeWheelLogos: Boolean = true,
    val scrapeVideos: Boolean = true,
    /** Fetch ScreenScraper's 3D box render as box art (2D cover as fallback). Settings › Scrape options. */
    val prefer3dBoxes: Boolean = false,
    /**
     * ScreenScraper "mix" composites. Not a user setting — it follows [scrapeScreenshots] — but kept
     * separate so a game that has screenshots and is only missing its miximage still gets one.
     */
    val scrapeMiximages: Boolean = scrapeScreenshots,
    val rateLimitMs: Long = 1200,
    /** User's SteamGridDB API key; blank turns SteamGridDB off. */
    val steamGridDbKey: String = ""
) {
    val isConfigured: Boolean get() = ssid.isNotBlank() && sspassword.isNotBlank()
    val hasSteamGridDb: Boolean get() = steamGridDbKey.isNotBlank()
}
