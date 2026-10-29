package com.gamelaunch.frontend.domain.model

data class GameMedia(
    val id: Long = 0,
    val gameId: Long,
    val boxArtLocalPath: String? = null,
    val boxArtRemoteUrl: String? = null,
    val screenshotLocalPath: String? = null,
    val screenshotRemoteUrl: String? = null,
    val wheelLogoLocalPath: String? = null,
    val wheelLogoRemoteUrl: String? = null,
    val videoLocalPath: String? = null,
    val videoRemoteUrl: String? = null,
    val backgroundLocalPath: String? = null,
    val miximageLocalPath: String? = null,
    val miximageRemoteUrl: String? = null,
    val scraperTimestampMs: Long? = null,
    val bannerLocalPath: String? = null
) {
    val hasBoxArt: Boolean get() = boxArtLocalPath != null || boxArtRemoteUrl != null
    val hasVideo: Boolean get() = videoLocalPath != null || videoRemoteUrl != null
    val effectiveBoxArt: String? get() = boxArtLocalPath ?: boxArtRemoteUrl
    val effectiveVideo: String? get() = videoLocalPath ?: videoRemoteUrl
    val effectiveBackground: String? get() = backgroundLocalPath ?: screenshotRemoteUrl
    val effectiveScreenshot: String? get() = screenshotLocalPath ?: screenshotRemoteUrl
    /** Screenshots take precedence; a banner is a separate fallback, never the small box-art icon. */
    val effectiveDetailArtwork: String? get() = effectiveScreenshot ?: bannerLocalPath ?: effectiveBoxArt
    val detailArtworkIsBanner: Boolean get() = effectiveScreenshot == null && bannerLocalPath != null
    val effectiveMiximage: String? get() = miximageLocalPath ?: miximageRemoteUrl
}
