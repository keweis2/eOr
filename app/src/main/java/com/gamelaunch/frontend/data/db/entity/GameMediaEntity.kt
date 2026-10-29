package com.gamelaunch.frontend.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "game_media",
    foreignKeys = [ForeignKey(
        entity = GameEntity::class,
        parentColumns = ["id"],
        childColumns = ["game_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["game_id"], unique = true)]
)
data class GameMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "game_id") val gameId: Long,
    @ColumnInfo(name = "box_art_local") val boxArtLocalPath: String? = null,
    @ColumnInfo(name = "box_art_remote") val boxArtRemoteUrl: String? = null,
    @ColumnInfo(name = "screenshot_local") val screenshotLocalPath: String? = null,
    @ColumnInfo(name = "screenshot_remote") val screenshotRemoteUrl: String? = null,
    @ColumnInfo(name = "wheel_logo_local") val wheelLogoLocalPath: String? = null,
    @ColumnInfo(name = "wheel_logo_remote") val wheelLogoRemoteUrl: String? = null,
    @ColumnInfo(name = "video_local") val videoLocalPath: String? = null,
    @ColumnInfo(name = "video_remote") val videoRemoteUrl: String? = null,
    @ColumnInfo(name = "background_local") val backgroundLocalPath: String? = null,
    @ColumnInfo(name = "miximage_local") val miximageLocalPath: String? = null,
    @ColumnInfo(name = "miximage_remote") val miximageRemoteUrl: String? = null,
    @ColumnInfo(name = "scraper_timestamp_ms") val scraperTimestampMs: Long? = null,
    @ColumnInfo(name = "banner_local") val bannerLocalPath: String? = null
)
