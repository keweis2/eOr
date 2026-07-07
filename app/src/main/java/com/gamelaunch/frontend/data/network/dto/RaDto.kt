package com.gamelaunch.frontend.data.network.dto

import com.google.gson.annotations.SerializedName

data class RaUserSummaryDto(
    @SerializedName("User")               val user: String? = null,
    @SerializedName("TotalPoints")        val totalPoints: Int? = null,
    @SerializedName("TotalSoftcorePoints") val softcorePoints: Int? = null,
    @SerializedName("TotalTruePoints")    val truePoints: Int? = null,
    @SerializedName("Rank")               val rank: Int? = null,
    @SerializedName("UserPic")            val userPic: String? = null,
    @SerializedName("Status")             val status: String? = null,
    @SerializedName("RecentlyPlayed")     val recentlyPlayed: List<RaRecentlyPlayedDto>? = null
)

data class RaRecentlyPlayedDto(
    @SerializedName("GameID")      val gameId: Int? = null,
    @SerializedName("ConsoleID")   val consoleId: Int? = null,
    @SerializedName("ConsoleName") val consoleName: String? = null,
    @SerializedName("Title")       val title: String? = null,
    @SerializedName("ImageIcon")   val imageIcon: String? = null,
    @SerializedName("LastPlayed")  val lastPlayed: String? = null
)

data class RaLoginDto(
    @SerializedName("Success")       val success: Boolean? = null,
    @SerializedName("User")          val user: String? = null,
    @SerializedName("Token")         val token: String? = null,
    @SerializedName("Score")         val score: Int? = null,
    @SerializedName("SoftcoreScore") val softcoreScore: Int? = null,
    @SerializedName("AccountType")   val accountType: String? = null,
    @SerializedName("Error")         val error: String? = null,
    @SerializedName("Code")          val code: String? = null
)

data class RaRecentGameDto(
    @SerializedName("GameID")                    val gameId: Int? = null,
    @SerializedName("ConsoleID")                 val consoleId: Int? = null,
    @SerializedName("ConsoleName")               val consoleName: String? = null,
    @SerializedName("Title")                     val title: String? = null,
    @SerializedName("ImageIcon")                 val imageIcon: String? = null,
    // API_GetUserRecentlyPlayedGames returns NumPossibleAchievements / NumAchieved / PossibleScore —
    // not the NumAchievements / NumAwardedToUser / MaxPossible names used by other RA endpoints.
    @SerializedName("NumPossibleAchievements")   val numAchievements: Int? = null,
    @SerializedName("NumAchieved")               val numAwardedToUser: Int? = null,
    @SerializedName("NumAchievedHardcore")       val numAwardedHardcore: Int? = null,
    @SerializedName("ScoreAchieved")             val scoreAchieved: Int? = null,
    @SerializedName("ScoreAchievedHardcore")     val scoreAchievedHardcore: Int? = null,
    @SerializedName("PossibleScore")             val maxPossible: Int? = null,
    @SerializedName("LastPlayed")                val lastPlayed: String? = null,
    @SerializedName("HighestAwardKind")          val highestAwardKind: String? = null
)
