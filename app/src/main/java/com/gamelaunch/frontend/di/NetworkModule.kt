package com.gamelaunch.frontend.di

import com.gamelaunch.frontend.data.network.LaunchBoxService
import com.gamelaunch.frontend.data.network.RetroAchievementsApi
import com.gamelaunch.frontend.data.network.ScreenScraperApi
import com.gamelaunch.frontend.data.network.interceptor.RateLimitInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://api.screenscraper.fr/api2/"

    @Provides
    @Singleton
    fun provideRateLimitInterceptor(): RateLimitInterceptor = RateLimitInterceptor(1200)

    @Provides
    @Singleton
    fun provideOkHttpClient(rateLimitInterceptor: RateLimitInterceptor): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (com.gamelaunch.frontend.BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.BASIC
            else
                HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(rateLimitInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideScreenScraperApi(retrofit: Retrofit): ScreenScraperApi =
        retrofit.create(ScreenScraperApi::class.java)

    // Separate client for LaunchBox — no rate limit, long timeout for ~190 MB download
    @Provides
    @Singleton
    @Named("launchbox")
    fun provideLaunchBoxOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @Named("launchbox")
    fun provideLaunchBoxRetrofit(@Named("launchbox") client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://gamesdb.launchbox-app.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideLaunchBoxService(@Named("launchbox") retrofit: Retrofit): LaunchBoxService =
        retrofit.create(LaunchBoxService::class.java)

    @Provides
    @Singleton
    @Named("ra")
    fun provideRaOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @Named("ra")
    fun provideRaRetrofit(@Named("ra") client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://retroachievements.org/API/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideRetroAchievementsApi(@Named("ra") retrofit: Retrofit): RetroAchievementsApi =
        retrofit.create(RetroAchievementsApi::class.java)
}
