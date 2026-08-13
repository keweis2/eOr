package com.gamelaunch.frontend.di

import com.gamelaunch.frontend.data.system.AndroidBootCountProvider
import com.gamelaunch.frontend.domain.system.BootCountProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SystemModule {
    @Binds
    @Singleton
    abstract fun bindBootCountProvider(impl: AndroidBootCountProvider): BootCountProvider
}
