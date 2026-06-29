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

data class RaRecentGameDto(
    @SerializedName("GameID")                    val gameId: Int? = null,
    @SerializedName("ConsoleID")                 val consoleId: Int? = null,
    @SerializedName("ConsoleName")               val consoleName: String? = null,
    @SerializedName("Title")                     val title: String? = null,
    @SerializedName("ImageIcon")                 val imageIcon: String? = null,
    @SerializedName("NumAchievements")           val numAchievements: Int? = null,
    @SerializedName("NumAwardedToUser")          val numAwardedToUser: Int? = null,
    @SerializedName("NumAwardedToUserHardcore")  val numAwardedHardcore: Int? = null,
    @SerializedName("ScoreAchieved")             val scoreAchieved: Int? = null,
    @SerializedName("ScoreAchievedHardcore")     val scoreAchievedHardcore: Int? = null,
    @SerializedName("MaxPossible")               val maxPossible: Int? = null,
    @SerializedName("LastPlayed")                val lastPlayed: String? = null,
    @SerializedName("HighestAwardKind")          val highestAwardKind: String? = null
)
