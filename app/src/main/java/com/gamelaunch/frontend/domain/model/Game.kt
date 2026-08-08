package com.gamelaunch.frontend.domain.model

data class Game(
    val id: Long = 0,
    val title: String,
    val romPath: String,
    val romFilename: String,
    val platformId: String,
    val md5: String? = null,
    val crc: String? = null,
    val scraperGameId: Long? = null,
    val description: String? = null,
    val genre: String? = null,
    val releaseYear: Int? = null,
    val rating: Float? = null,
    val isFavorite: Boolean = false,
    val lastPlayedMs: Long? = null,
    val playCount: Int = 0,
    val dateAdded: Long = System.currentTimeMillis(),
    val isScraped: Boolean = false,
    val isAvailableInLockedMode: Boolean = true
)
