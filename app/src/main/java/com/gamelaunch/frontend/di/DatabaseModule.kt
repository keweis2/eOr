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
import com.gamelaunch.frontend.data.db.dao.PlaySessionDao
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

    /**
     * v3 → v4 adds the two `miximage` columns to `game_media` (for the dual-screen top-panel
     * "miximage" image option). Nullable TEXT with no default, matching Room's expected schema.
     * Explicit (non-destructive) so users keep their scraped library and media.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE game_media ADD COLUMN miximage_local TEXT")
            db.execSQL("ALTER TABLE game_media ADD COLUMN miximage_remote TEXT")
        }
    }

    /**
     * v4 → v5 adds per-game Locked Mode availability. Existing rows receive `1` so upgrading
     * preserves the current behavior: every imported game remains available while eOr is locked.
     * Explicit (non-destructive) so users keep their existing library and media.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE games ADD COLUMN available_in_locked_mode INTEGER NOT NULL DEFAULT 1"
            )
        }
    }

    /**
     * The `play_sessions` table and its two indices, copied verbatim from PlaySessionEntity's
     * generated Room schema (app/schemas/.../6.json). Must stay identical to what Room expects —
     * guarded by PlaySessionsMigrationSchemaTest.
     */
    const val PLAY_SESSIONS_CREATE_SQL =
        "CREATE TABLE IF NOT EXISTS `play_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `game_id` INTEGER NOT NULL, `platform_id` TEXT NOT NULL, `started_at` INTEGER NOT NULL, `ended_at` INTEGER, `duration_ms` INTEGER NOT NULL, `segment_started_at` INTEGER)"
    const val PLAY_SESSIONS_GAME_INDEX_SQL =
        "CREATE INDEX IF NOT EXISTS `index_play_sessions_game_id` ON `play_sessions` (`game_id`)"
    const val PLAY_SESSIONS_STARTED_INDEX_SQL =
        "CREATE INDEX IF NOT EXISTS `index_play_sessions_started_at` ON `play_sessions` (`started_at`)"

    /**
     * v5 → v6 adds `play_sessions` for playtime tracking. Explicit (non-destructive) so users keep
     * their library — the builder's destructive fallback would otherwise wipe it.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(PLAY_SESSIONS_CREATE_SQL)
            db.execSQL(PLAY_SESSIONS_GAME_INDEX_SQL)
            db.execSQL(PLAY_SESSIONS_STARTED_INDEX_SQL)
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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
    fun providePlaySessionDao(db: AppDatabase): PlaySessionDao = db.playSessionDao()

    @Provides
    fun provideFriendDao(db: AppDatabase): FriendDao = db.friendDao()
}
