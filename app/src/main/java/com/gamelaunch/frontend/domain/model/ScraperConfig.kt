package com.gamelaunch.frontend.domain.model

import com.gamelaunch.frontend.BuildConfig

data class ScraperConfig(
    val ssid: String = "",
    val sspassword: String = "",
    val devid: String = BuildConfig.SS_DEV_ID,
    val devpassword: String = BuildConfig.SS_DEV_PASSWORD,
    val softname: String = "eOr",
    val preferredRegion: String = "us",
    val scrapeMetadata: Boolean = true,
    val scrapeBoxArt: Boolean = true,
    val scrapeScreenshots: Boolean = true,
    val scrapeWheelLogos: Boolean = true,
    val scrapeVideos: Boolean = true,
    val rateLimitMs: Long = 1200
) {
    val isConfigured: Boolean get() = ssid.isNotBlank() && sspassword.isNotBlank()
}
