package com.gamelaunch.frontend.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.gamelaunch.frontend.data.db.dao.EmulatorMappingDao
import com.gamelaunch.frontend.data.db.dao.FriendDao
import com.gamelaunch.frontend.data.db.dao.GameDao
import com.gamelaunch.frontend.data.db.dao.GameMediaDao
import com.gamelaunch.frontend.data.db.dao.LaunchBoxDao
import com.gamelaunch.frontend.data.db.entity.EmulatorMappingEntity
import com.gamelaunch.frontend.data.db.entity.FriendEntity
import com.gamelaunch.frontend.data.db.entity.GameEntity
import com.gamelaunch.frontend.data.db.entity.GameMediaEntity
import com.gamelaunch.frontend.data.db.entity.LaunchBoxGameEntity
import com.gamelaunch.frontend.data.db.entity.LaunchBoxImageEntity

@Database(
    entities = [
        GameEntity::class,
        GameMediaEntity::class,
        EmulatorMappingEntity::class,
        LaunchBoxGameEntity::class,
        LaunchBoxImageEntity::class,
        FriendEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun gameMediaDao(): GameMediaDao
    abstract fun emulatorMappingDao(): EmulatorMappingDao
    abstract fun launchBoxDao(): LaunchBoxDao
    abstract fun friendDao(): FriendDao

    companion object {
        const val DATABASE_NAME = "gamelauncher.db"
    }
}
