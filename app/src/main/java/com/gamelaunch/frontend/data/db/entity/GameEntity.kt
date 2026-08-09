package com.gamelaunch.frontend.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "games",
    indices = [Index(value = ["rom_path"], unique = true)]
)
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "rom_path") val romPath: String,
    @ColumnInfo(name = "rom_filename") val romFilename: String,
    @ColumnInfo(name = "platform_id") val platformId: String,
    @ColumnInfo(name = "md5") val md5: String? = null,
    @ColumnInfo(name = "crc") val crc: String? = null,
    @ColumnInfo(name = "scraper_game_id") val scraperGameId: Long? = null,
    @ColumnInfo(name = "description") val description: String? = null,
    @ColumnInfo(name = "genre") val genre: String? = null,
    @ColumnInfo(name = "release_year") val releaseYear: Int? = null,
    @ColumnInfo(name = "rating") val rating: Float? = null,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "last_played_ms") val lastPlayedMs: Long? = null,
    @ColumnInfo(name = "play_count") val playCount: Int = 0,
    @ColumnInfo(name = "date_added") val dateAdded: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_scraped") val isScraped: Boolean = false,
    @ColumnInfo(name = "available_in_locked_mode", defaultValue = "1")
    val isAvailableInLockedMode: Boolean = true
)
