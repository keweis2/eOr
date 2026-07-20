package com.gamelaunch.frontend.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A friend (or pending friend request) identified by their Syncthing device id. All fields other
 * than identity/status are a cached copy of the last profile.json we received over P2P sync — they
 * may be stale if the friend is offline. `status` is one of PENDING_OUT / PENDING_IN / ACTIVE.
 */
@Entity(tableName = "friends")
data class FriendEntity(
    @PrimaryKey @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "last_played_title") val lastPlayedTitle: String? = null,
    @ColumnInfo(name = "last_played_platform") val lastPlayedPlatform: String? = null,
    @ColumnInfo(name = "last_played_md5") val lastPlayedMd5: String? = null,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long? = null,
    @ColumnInfo(name = "ra_username") val raUsername: String? = null,
    @ColumnInfo(name = "ra_points") val raPoints: Int? = null,
    @ColumnInfo(name = "ra_softcore_points") val raSoftcorePoints: Int? = null,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "profile_updated_at") val profileUpdatedAt: Long? = null,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long? = null
)
