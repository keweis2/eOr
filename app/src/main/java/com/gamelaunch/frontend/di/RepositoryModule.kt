package com.gamelaunch.frontend.di

import com.gamelaunch.frontend.data.repository.EmulatorRepositoryImpl
import com.gamelaunch.frontend.data.repository.FriendRepositoryImpl
import com.gamelaunch.frontend.data.repository.GameRepositoryImpl
import com.gamelaunch.frontend.data.repository.MediaRepositoryImpl
import com.gamelaunch.frontend.data.repository.RetroAchievementsRepositoryImpl
import com.gamelaunch.frontend.data.repository.ScraperRepositoryImpl
import com.gamelaunch.frontend.data.repository.SettingsRepositoryImpl
import com.gamelaunch.frontend.domain.repository.EmulatorRepository
import com.gamelaunch.frontend.domain.repository.FriendRepository
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.MediaRepository
import com.gamelaunch.frontend.domain.repository.RetroAchievementsRepository
import com.gamelaunch.frontend.domain.repository.ScraperRepository
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindGameRepository(impl: GameRepositoryImpl): GameRepository

    @Binds @Singleton
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository

    @Binds @Singleton
    abstract fun bindScraperRepository(impl: ScraperRepositoryImpl): ScraperRepository

    @Binds @Singleton
    abstract fun bindEmulatorRepository(impl: EmulatorRepositoryImpl): EmulatorRepository

    @Binds @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds @Singleton
    abstract fun bindRetroAchievementsRepository(impl: RetroAchievementsRepositoryImpl): RetroAchievementsRepository

    @Binds @Singleton
    abstract fun bindFriendRepository(impl: FriendRepositoryImpl): FriendRepository
}
