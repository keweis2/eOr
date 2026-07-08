package com.gamelaunch.frontend.domain.model

/** Ordering for the games shown inside a system. Persisted by name. */
enum class GameSort(val label: String) {
    FAVORITES("Favorites"),
    RECENTLY_PLAYED("Recently played"),
    RECENTLY_ADDED("Recently added"),
    ALPHABETICAL("Alphabetical");

    companion object {
        val DEFAULT = ALPHABETICAL
        fun fromName(name: String): GameSort = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Apply a [GameSort] to a list of games. Ties fall back to title so the order is stable. */
fun List<Game>.sortedBy(sort: GameSort): List<Game> = when (sort) {
    GameSort.ALPHABETICAL    -> sortedBy { it.title.lowercase() }
    GameSort.RECENTLY_PLAYED -> sortedWith(
        compareByDescending<Game> { it.lastPlayedMs ?: Long.MIN_VALUE }.thenBy { it.title.lowercase() }
    )
    GameSort.RECENTLY_ADDED  -> sortedWith(
        compareByDescending<Game> { it.dateAdded }.thenBy { it.title.lowercase() }
    )
    GameSort.FAVORITES       -> sortedWith(
        compareByDescending<Game> { it.isFavorite }.thenBy { it.title.lowercase() }
    )
}
