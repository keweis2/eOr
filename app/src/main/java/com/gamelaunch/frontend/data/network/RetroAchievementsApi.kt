package com.gamelaunch.frontend.data.network

import com.gamelaunch.frontend.data.network.dto.RaRecentGameDto
import com.gamelaunch.frontend.data.network.dto.RaUserSummaryDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface RetroAchievementsApi {

    @GET("API_GetUserSummary.php")
    suspend fun getUserSummary(
        @Query("z") callerUsername: String,
        @Query("y") apiKey: String,
        @Query("u") targetUsername: String,
        @Query("g") recentGamesCount: Int = 0
    ): Response<RaUserSummaryDto>

    @GET("API_GetUserRecentlyPlayedGames.php")
    suspend fun getRecentlyPlayedGames(
        @Query("z") callerUsername: String,
        @Query("y") apiKey: String,
        @Query("u") targetUsername: String,
        @Query("c") count: Int = 15
    ): Response<List<RaRecentGameDto>>
}
