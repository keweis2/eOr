package com.gamelaunch.frontend.data.network.dto

// SteamGridDB API v2 responses (https://www.steamgriddb.com/api/v2). Fields nullable because Gson
// fills missing ones with null regardless of Kotlin types. Lives in dto/ for the R8 keep rule.

data class SgdbResponse<T>(
    val success: Boolean? = null,
    val data: T? = null,
    val errors: List<String>? = null
)

data class SgdbGameDto(
    val id: Long? = null,
    val name: String? = null,
    val verified: Boolean? = null
)

/** A grid (cover), logo or hero image. */
data class SgdbImageDto(
    val id: Long? = null,
    val url: String? = null,
    val thumb: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val style: String? = null,
    val nsfw: Boolean? = null,
    val humor: Boolean? = null,
    val epilepsy: Boolean? = null,
    val score: Int? = null
)
