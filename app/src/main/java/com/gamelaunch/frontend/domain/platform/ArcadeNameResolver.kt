package com.gamelaunch.frontend.domain.platform

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves arcade ROM short names (the MAME/FinalBurn Neo set names that double as the ROM
 * filename, e.g. "afighter") to their real game titles (e.g. "Action Fighter").
 *
 * Arcade platforms store their games as archives named after the emulator's romset id rather than
 * the game title, so a raw scan would surface cryptic codes. This resolver reads a bundled
 * short-name → title table (FinalBurn Neo + MAME, sourced from the libretro-database DATs) so the
 * library shows readable names offline, before — or without — a ScreenScraper metadata scrape.
 *
 * The table (~18k entries) is loaded lazily from a gzipped asset on first lookup and cached for the
 * process lifetime.
 */
@Singleton
class ArcadeNameResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile private var names: Map<String, String>? = null

    /** Whether [platformId] is an arcade system whose ROM filenames are romset short names. */
    fun isArcadePlatform(platformId: String?): Boolean = platformId in ARCADE_PLATFORM_IDS

    /**
     * The real title for the arcade ROM [romFilename] on [platformId], or null when [platformId] is
     * not an arcade system or the short name isn't in the table. [romFilename] may be a full file
     * name ("afighter.zip") or a bare short name ("afighter"); matching is case-insensitive.
     */
    fun resolve(platformId: String?, romFilename: String): String? {
        if (!isArcadePlatform(platformId)) return null
        val short = romFilename.substringBeforeLast('.').lowercase()
        if (short.isEmpty()) return null
        return table()[short]
    }

    private fun table(): Map<String, String> {
        names?.let { return it }
        return synchronized(this) {
            names ?: load().also { names = it }
        }
    }

    private fun load(): Map<String, String> = runCatching {
        val map = HashMap<String, String>(20_000)
        context.assets.open(ASSET_NAME).use { raw ->
            GZIPInputStream(raw).bufferedReader().forEachLine { line ->
                val tab = line.indexOf('\t')
                if (tab > 0 && tab < line.length - 1) {
                    map[line.substring(0, tab)] = line.substring(tab + 1)
                }
            }
        }
        map
    }.getOrDefault(emptyMap())

    private companion object {
        const val ASSET_NAME = "arcade_names.tsv.gz"

        // Platforms whose ROM archives are named after MAME/FBNeo romset ids. Mirrors the arcade
        // entries in PlatformDefinitions.
        val ARCADE_PLATFORM_IDS = setOf("mame", "fbneo", "neogeo", "cps1", "cps2", "cps3")
    }
}
