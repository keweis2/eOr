package com.gamelaunch.frontend.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gamelaunch.frontend.data.db.entity.FriendEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendDao {

    @Query("SELECT * FROM friends ORDER BY display_name ASC")
    fun observeAll(): Flow<List<FriendEntity>>

    @Query("SELECT * FROM friends WHERE status = :status ORDER BY display_name ASC")
    fun observeByStatus(status: String): Flow<List<FriendEntity>>

    @Query("SELECT * FROM friends WHERE device_id = :deviceId")
    suspend fun getByDeviceId(deviceId: String): FriendEntity?

    @Query("SELECT * FROM friends")
    suspend fun getAll(): List<FriendEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(friend: FriendEntity)

    @Query("DELETE FROM friends WHERE device_id = :deviceId")
    suspend fun delete(deviceId: String)

    @Query("DELETE FROM friends")
    suspend fun deleteAll()
}
