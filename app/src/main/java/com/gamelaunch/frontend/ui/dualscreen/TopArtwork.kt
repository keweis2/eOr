package com.gamelaunch.frontend.ui.dualscreen

import com.gamelaunch.frontend.domain.model.GameMedia
import java.io.File

internal enum class TopArtKind { LOGO, PHOTO }
internal data class TopArt(val path: String, val kind: TopArtKind)

/** Respect the preferred image, then use the game's other art before falling back to text. */
internal fun resolveTopArt(media: GameMedia?, type: TopScreenImage): TopArt? {
    val m = media ?: return null
    fun image(local: String?, remote: String? = null): String? =
        local?.takeIf { it.isNotBlank() && File(it).let { f -> f.isFile && f.length() > 0 } }
            ?: remote?.takeIf { it.isNotBlank() }
    val logo = image(m.wheelLogoLocalPath, m.wheelLogoRemoteUrl)?.let { TopArt(it, TopArtKind.LOGO) }
    val shot = image(m.screenshotLocalPath, m.screenshotRemoteUrl)?.let { TopArt(it, TopArtKind.PHOTO) }
    val mix = image(m.miximageLocalPath, m.miximageRemoteUrl)?.let { TopArt(it, TopArtKind.PHOTO) }
    val preferred = when (type) {
        TopScreenImage.MARQUEE -> logo ?: mix ?: shot
        TopScreenImage.SCREENSHOT -> shot ?: mix ?: logo
        TopScreenImage.MIXIMAGE -> mix ?: shot ?: logo
    }
    return preferred ?: (image(m.bannerLocalPath) ?: image(m.boxArtLocalPath, m.boxArtRemoteUrl))
        ?.let { TopArt(it, TopArtKind.PHOTO) }
}
