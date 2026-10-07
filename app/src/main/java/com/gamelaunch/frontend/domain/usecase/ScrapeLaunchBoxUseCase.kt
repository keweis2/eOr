package com.gamelaunch.frontend.domain.usecase

import com.gamelaunch.frontend.data.db.dao.LaunchBoxDao
import com.gamelaunch.frontend.data.db.entity.LaunchBoxImageEntity
import com.gamelaunch.frontend.domain.platform.PlatformDefinitions
import javax.inject.Inject

private const val IMAGE_BASE_URL = "https://images.launchbox-app.com/"

data class LaunchBoxGameMedia(
    val title: String,
    val overview: String?,
    val releaseYear: Int?,
    val developer: String?,
    val publisher: String?,
    val rating: Float?,
    val boxFrontUrl: String?,
    val backgroundUrl: String?,
    val screenshotUrl: String?,
    val logoUrl: String?
)

class ScrapeLaunchBoxUseCase @Inject constructor(
    private val launchBoxDao: LaunchBoxDao
) {
    suspend operator fun invoke(gameName: String, platformId: String): LaunchBoxGameMedia? {
        val lbPlatform = PlatformDefinitions.byId[platformId]?.launchBoxPlatform
            ?: PLATFORM_MAP[platformId] ?: return null

        // Strip region codes and punctuation for a more robust match
        val cleanName = gameName
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("[^a-zA-Z0-9 ]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

        if (cleanName.isBlank()) return null

        val games = launchBoxDao.searchGames("%$cleanName%", lbPlatform)
        val game = games.firstOrNull() ?: return null

        val images = launchBoxDao.getImagesForGame(game.id)

        return LaunchBoxGameMedia(
            title = game.name,
            overview = game.overview,
            releaseYear = game.releaseYear,
            developer = game.developer,
            publisher = game.publisher,
            rating = game.rating,
            boxFrontUrl = pickImage(images, "Box - Front", "Fanart - Box - Front", "Box - 3D"),
            backgroundUrl = pickImage(images, "Fanart - Background"),
            screenshotUrl = pickImage(images, "Screenshot - Gameplay", "Screenshot - Game Title"),
            logoUrl = pickImage(images, "Clear Logo")
        )
    }

    private fun pickImage(images: List<LaunchBoxImageEntity>, vararg types: String): String? {
        for (type in types) {
            val match = images
                .filter { it.type == type }
                .sortedWith(compareBy {
                    when (it.region) {
                        "North America" -> 0
                        "United States" -> 1
                        "", null        -> 2
                        else            -> 3
                    }
                })
                .firstOrNull()
            if (match != null) return "$IMAGE_BASE_URL${match.fileName}"
        }
        return null
    }

    companion object {
        val PLATFORM_MAP = mapOf(
            "nes"       to "Nintendo Entertainment System",
            "snes"      to "Super Nintendo Entertainment System",
            "n64"       to "Nintendo 64",
            "gb"        to "Nintendo Game Boy",
            "gbc"       to "Nintendo Game Boy Color",
            "gba"       to "Nintendo Game Boy Advance",
            "nds"       to "Nintendo DS",
            "3ds"       to "Nintendo 3DS",
            "switch"    to "Nintendo Switch",
            "ps1"       to "Sony Playstation",
            "ps2"       to "Sony Playstation 2",
            "psp"       to "Sony PSP",
            "dc"        to "Sega Dreamcast",
            "genesis"   to "Sega Genesis",
            "sms"       to "Sega Master System",
            "gg"        to "Sega Game Gear",
            "saturn"    to "Sega Saturn",
            "32x"       to "Sega 32X",
            "atari2600" to "Atari 2600",
            "mame"      to "Arcade",
            "xbox360"   to "Microsoft Xbox 360"
        )
    }
}
