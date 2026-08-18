package com.gamelaunch.frontend.domain.platform

/** How the user can order the systems shown on the home screen. */
enum class SystemSort(val label: String) {
    ALPHABETICAL("Alphabetical"),
    RELEASE_DATE("Release date"),
    CONSOLE_TYPE("Console type"),
    BRAND("Brand name"),
    GAME_COUNT("Number of games");

    companion object {
        fun fromName(name: String): SystemSort? = entries.firstOrNull { it.name == name }
    }
}

enum class ConsoleKind(val order: Int) { CONSOLE(0), HANDHELD(1), ARCADE(2), COMPUTER(3), MOBILE(4), OTHER(5) }

data class PlatformMeta(val releaseYear: Int, val brand: String, val kind: ConsoleKind)

/** Static per-platform metadata used only for sorting the system list. */
object PlatformMetadata {
    val byId: Map<String, PlatformMeta> = mapOf(
        "nes"       to PlatformMeta(1983, "Nintendo", ConsoleKind.CONSOLE),
        "snes"      to PlatformMeta(1990, "Nintendo", ConsoleKind.CONSOLE),
        "n64"       to PlatformMeta(1996, "Nintendo", ConsoleKind.CONSOLE),
        "gb"        to PlatformMeta(1989, "Nintendo", ConsoleKind.HANDHELD),
        "gbc"       to PlatformMeta(1998, "Nintendo", ConsoleKind.HANDHELD),
        "gba"       to PlatformMeta(2001, "Nintendo", ConsoleKind.HANDHELD),
        "nds"       to PlatformMeta(2004, "Nintendo", ConsoleKind.HANDHELD),
        "3ds"       to PlatformMeta(2011, "Nintendo", ConsoleKind.HANDHELD),
        "gc"        to PlatformMeta(2001, "Nintendo", ConsoleKind.CONSOLE),
        "wii"       to PlatformMeta(2006, "Nintendo", ConsoleKind.CONSOLE),
        "wiiu"      to PlatformMeta(2012, "Nintendo", ConsoleKind.CONSOLE),
        "switch"    to PlatformMeta(2017, "Nintendo", ConsoleKind.CONSOLE),
        "ps1"       to PlatformMeta(1994, "Sony", ConsoleKind.CONSOLE),
        "ps2"       to PlatformMeta(2000, "Sony", ConsoleKind.CONSOLE),
        "ps3"       to PlatformMeta(2006, "Sony", ConsoleKind.CONSOLE),
        "psp"       to PlatformMeta(2004, "Sony", ConsoleKind.HANDHELD),
        "psvita"    to PlatformMeta(2011, "Sony", ConsoleKind.HANDHELD),
        "genesis"   to PlatformMeta(1988, "Sega", ConsoleKind.CONSOLE),
        "sms"       to PlatformMeta(1985, "Sega", ConsoleKind.CONSOLE),
        "gg"        to PlatformMeta(1990, "Sega", ConsoleKind.HANDHELD),
        "saturn"    to PlatformMeta(1994, "Sega", ConsoleKind.CONSOLE),
        "32x"       to PlatformMeta(1994, "Sega", ConsoleKind.CONSOLE),
        "segacd"    to PlatformMeta(1991, "Sega", ConsoleKind.CONSOLE),
        "dc"        to PlatformMeta(1998, "Sega", ConsoleKind.CONSOLE),
        "atari2600" to PlatformMeta(1977, "Atari", ConsoleKind.CONSOLE),
        "neogeo"    to PlatformMeta(1990, "SNK", ConsoleKind.CONSOLE),
        "ngp"       to PlatformMeta(1998, "SNK", ConsoleKind.HANDHELD),
        "pcengine"  to PlatformMeta(1987, "NEC", ConsoleKind.CONSOLE),
        "3do"       to PlatformMeta(1993, "Panasonic", ConsoleKind.CONSOLE),
        "mame"      to PlatformMeta(1980, "Arcade", ConsoleKind.ARCADE),
        "fbneo"     to PlatformMeta(1980, "Arcade", ConsoleKind.ARCADE),
        "cps1"      to PlatformMeta(1988, "Capcom", ConsoleKind.ARCADE),
        "cps2"      to PlatformMeta(1993, "Capcom", ConsoleKind.ARCADE),
        "cps3"      to PlatformMeta(1996, "Capcom", ConsoleKind.ARCADE),
        "c64"       to PlatformMeta(1982, "Commodore", ConsoleKind.COMPUTER),
        "pico8"     to PlatformMeta(2015, "Lexaloffle", ConsoleKind.OTHER),
        "steam"     to PlatformMeta(2003, "PC", ConsoleKind.COMPUTER),
        "xbox360"   to PlatformMeta(2005, "Microsoft", ConsoleKind.CONSOLE),
        "android"   to PlatformMeta(2008, "Android", ConsoleKind.MOBILE),
    )

    fun year(id: String): Int = byId[id]?.releaseYear ?: Int.MAX_VALUE
    fun brand(id: String): String = byId[id]?.brand ?: "￿"
    fun kindOrder(id: String): Int = (byId[id]?.kind ?: ConsoleKind.OTHER).order
}

/**
 * Order a list of platform ids by up to two sort keys (primary, then secondary tie-breaker).
 * [displayName] and [gameCount] are looked up via the supplied lambdas. Alphabetical is always the
 * final tie-breaker so the order is stable.
 */
fun List<String>.sortedBySystems(
    sorts: List<SystemSort>,
    displayName: (String) -> String,
    gameCount: (String) -> Int
): List<String> {
    if (sorts.isEmpty()) return this
    fun keyComparator(sort: SystemSort): Comparator<String> = when (sort) {
        SystemSort.ALPHABETICAL -> compareBy { displayName(it).lowercase() }
        SystemSort.RELEASE_DATE -> compareBy { PlatformMetadata.year(it) }
        SystemSort.CONSOLE_TYPE -> compareBy { PlatformMetadata.kindOrder(it) }
        SystemSort.BRAND        -> compareBy { PlatformMetadata.brand(it).lowercase() }
        SystemSort.GAME_COUNT   -> compareByDescending { gameCount(it) }
    }
    var comparator = keyComparator(sorts.first())
    sorts.drop(1).forEach { comparator = comparator.then(keyComparator(it)) }
    // Stable final tie-breaker.
    comparator = comparator.thenBy { displayName(it).lowercase() }
    return sortedWith(comparator)
}
