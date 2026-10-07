package com.gamelaunch.frontend.data.network

import com.gamelaunch.frontend.data.network.dto.SgdbGameDto
import com.gamelaunch.frontend.data.network.dto.SgdbImageDto
import com.gamelaunch.frontend.data.network.dto.SgdbResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * SteamGridDB API v2. Every call takes the user's own key as a Bearer token — SteamGridDB keys are
 * personal (free with a Steam login), so eOr asks for one in Settings rather than shipping one.
 */
interface SteamGridDbApi {

    @GET("search/autocomplete/{term}")
    suspend fun search(
        @Header("Authorization") auth: String,
        @Path("term") term: String
    ): Response<SgdbResponse<List<SgdbGameDto>>>

    /** Covers. 600x900 is SteamGridDB's portrait box-art size. */
    @GET("grids/{idType}/{id}")
    suspend fun grids(
        @Header("Authorization") auth: String,
        @Path("idType") idType: String,
        @Path("id") id: String,
        @Query("dimensions") dimensions: String = "600x900",
        @Query("types") types: String = "static",
        @Query("nsfw") nsfw: String = "false",
        @Query("humor") humor: String = "false",
        @Query("epilepsy") epilepsy: String = "false"
    ): Response<SgdbResponse<List<SgdbImageDto>>>

    @GET("logos/{idType}/{id}")
    suspend fun logos(
        @Header("Authorization") auth: String,
        @Path("idType") idType: String,
        @Path("id") id: String,
        @Query("types") types: String = "static",
        @Query("nsfw") nsfw: String = "false",
        @Query("humor") humor: String = "false",
        @Query("epilepsy") epilepsy: String = "false"
    ): Response<SgdbResponse<List<SgdbImageDto>>>

    companion object {
        const val BASE_URL = "https://www.steamgriddb.com/api/v2/"
        /** [idType] for [grids]/[logos]: a SteamGridDB game id, or a Steam app id. */
        const val BY_GAME = "game"
        const val BY_STEAM = "steam"
    }
}
