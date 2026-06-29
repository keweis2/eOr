package com.gamelaunch.frontend.domain.repository

import com.gamelaunch.frontend.domain.model.RaProfile
import com.gamelaunch.frontend.domain.model.RaRecentGame

interface RetroAchievementsRepository {
    suspend fun getUserProfile(username: String, apiKey: String): Result<RaProfile>
    suspend fun getRecentlyPlayed(username: String, apiKey: String, count: Int = 15): Result<List<RaRecentGame>>
}
