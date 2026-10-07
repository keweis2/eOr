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
    val rateLimitMs: Long = 1200,
    /** User's SteamGridDB API key; blank turns SteamGridDB off. */
    val steamGridDbKey: String = ""
) {
    val isConfigured: Boolean get() = ssid.isNotBlank() && sspassword.isNotBlank()
    val hasSteamGridDb: Boolean get() = steamGridDbKey.isNotBlank()
}
