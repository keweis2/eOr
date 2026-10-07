package com.gamelaunch.frontend.domain.usecase

import com.gamelaunch.frontend.domain.platform.PlatformDefinitions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * No-credentials art source: the public libretro thumbnail server (the same one RetroArch uses).
 * Box art, in-game snaps and title screens are addressed by the ROM's No-Intro/Redump name, so as
 * long as the ROM filenames are standard this matches a huge fraction of a typical library.
 *
 * https://thumbnails.libretro.com/<System>/Named_Boxarts|Named_Snaps|Named_Titles/<Name>.png
 */
@Singleton
class LibretroThumbnailScraper @Inject constructor(
    @Named("launchbox") private val httpClient: OkHttpClient
) {
    data class Thumbs(val boxArt: String, val screenshot: String, val title: String)

    /** Returns thumbnail URLs if a box-art exists for this game, else null. */
    suspend fun fetch(romFilename: String, platformId: String): Thumbs? = withContext(Dispatchers.IO) {
        val system = PlatformDefinitions.byId[platformId]?.libretroThumbnails
            ?: SYSTEM_MAP[platformId] ?: return@withContext null

        // libretro replaces these characters in thumbnail filenames with '_'
        val name = romFilename.substringBeforeLast(".")
            .replace(Regex("[&*/:`<>?\\\\|\"]"), "_")

        val box = url(system, "Named_Boxarts", name)
        if (!exists(box)) return@withContext null

        Thumbs(
            boxArt     = box,
            screenshot = url(system, "Named_Snaps", name),
            title      = url(system, "Named_Titles", name)
        )
    }

    private fun url(system: String, kind: String, name: String): String =
        "https://thumbnails.libretro.com".toHttpUrl().newBuilder()
            .addPathSegment(system)
            .addPathSegment(kind)
            .addPathSegment("$name.png")
            .build()
            .toString()

    private fun exists(url: String): Boolean = runCatching {
        val req = Request.Builder().url(url).head().build()
        httpClient.newCall(req).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    companion object {
        // platformId -> libretro thumbnail system folder name
        val SYSTEM_MAP = mapOf(
            "nes"       to "Nintendo - Nintendo Entertainment System",
            "snes"      to "Nintendo - Super Nintendo Entertainment System",
            "n64"       to "Nintendo - Nintendo 64",
            "gb"        to "Nintendo - Game Boy",
            "gbc"       to "Nintendo - Game Boy Color",
            "gba"       to "Nintendo - Game Boy Advance",
            "nds"       to "Nintendo - Nintendo DS",
            "3ds"       to "Nintendo - Nintendo 3DS",
            "genesis"   to "Sega - Mega Drive - Genesis",
            "sms"       to "Sega - Master System - Mark III",
            "gg"        to "Sega - Game Gear",
            "saturn"    to "Sega - Saturn",
            "32x"       to "Sega - 32X",
            "dc"        to "Sega - Dreamcast",
            "ps1"       to "Sony - PlayStation",
            "ps2"       to "Sony - PlayStation 2",
            "psp"       to "Sony - PlayStation Portable",
            "atari2600" to "Atari - 2600",
            "gc"        to "Nintendo - GameCube",
            "wii"       to "Nintendo - Wii",
            "wiiu"      to "Nintendo - Wii U",
            "neogeo"    to "SNK - Neo Geo",
            "fbneo"     to "FBNeo - Arcade Games",
            "mame"      to "MAME",
            "ngp"       to "SNK - Neo Geo Pocket Color",
            "pcengine"  to "NEC - PC Engine - TurboGrafx 16",
            "segacd"    to "Sega - Mega-CD - Sega CD",
            "3do"       to "The 3DO Company - 3DO",
        )
    }
}
