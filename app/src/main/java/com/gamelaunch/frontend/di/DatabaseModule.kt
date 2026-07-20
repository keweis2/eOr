package com.gamelaunch.frontend.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gamelaunch.frontend.data.db.AppDatabase
import com.gamelaunch.frontend.data.db.dao.EmulatorMappingDao
import com.gamelaunch.frontend.data.db.dao.FriendDao
import com.gamelaunch.frontend.data.db.dao.GameDao
import com.gamelaunch.frontend.data.db.dao.GameMediaDao
import com.gamelaunch.frontend.data.db.dao.LaunchBoxDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * The `friends` table DDL, copied verbatim from FriendEntity's generated Room schema
     * (app/schemas/.../3.json, `createSql` with `${'$'}{TABLE_NAME}` resolved). Keeping it identical to
     * what Room expects is what makes [MIGRATION_2_3] pass Room's schema validation — guarded by a test.
     */
    const val FRIENDS_CREATE_SQL =
        "CREATE TABLE IF NOT EXISTS `friends` (`device_id` TEXT NOT NULL, `display_name` TEXT NOT NULL, " +
        "`status` TEXT NOT NULL, `last_played_title` TEXT, `last_played_platform` TEXT, " +
        "`last_played_md5` TEXT, `last_played_at` INTEGER, `ra_username` TEXT, `ra_points` INTEGER, " +
        "`ra_softcore_points` INTEGER, `added_at` INTEGER NOT NULL, `profile_updated_at` INTEGER, " +
        "`last_synced_at` INTEGER, PRIMARY KEY(`device_id`))"

    /**
     * v2 → v3 adds the `friends` table for the P2P Friends feature. Explicit (non-destructive) so
     * users keep their existing library, which the builder's destructive fallback would otherwise wipe.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(FRIENDS_CREATE_SQL)
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addMigrations(MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideGameDao(db: AppDatabase): GameDao = db.gameDao()

    @Provides
    fun provideGameMediaDao(db: AppDatabase): GameMediaDao = db.gameMediaDao()

    @Provides
    fun provideEmulatorMappingDao(db: AppDatabase): EmulatorMappingDao = db.emulatorMappingDao()

    @Provides
    fun provideLaunchBoxDao(db: AppDatabase): LaunchBoxDao = db.launchBoxDao()

    @Provides
    fun provideFriendDao(db: AppDatabase): FriendDao = db.friendDao()
}
