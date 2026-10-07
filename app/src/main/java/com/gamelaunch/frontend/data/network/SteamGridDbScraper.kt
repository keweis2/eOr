package com.gamelaunch.frontend.data.network

import com.gamelaunch.frontend.data.network.dto.SgdbGameDto
import com.gamelaunch.frontend.data.network.dto.SgdbImageDto
import com.gamelaunch.frontend.data.network.dto.SgdbResponse
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds a cover (grid) and logo on SteamGridDB. Used to fill art the other sources missed —
 * strongest for PC / Steam / homebrew titles where ScreenScraper is thin.
 */
@Singleton
class SteamGridDbScraper @Inject constructor(
    private val api: SteamGridDbApi
) {
    data class Art(val boxArt: String?, val logo: String?)

    /** True if SteamGridDB accepts [apiKey]; false for a rejected key; throws on network failure. */
    suspend fun validate(apiKey: String): Boolean {
        val resp = api.search(bearer(apiKey), "doom")
        return resp.isSuccessful && resp.body()?.success == true
    }

    /**
     * Art for a game: by Steam app id when known (exact), otherwise by title search with a strict
     * match — a wrong cover is worse than none. Null when nothing usable was found.
     */
    suspend fun findArt(apiKey: String, title: String, steamAppId: String?, wantBoxArt: Boolean, wantLogo: Boolean): Art? {
        if (!wantBoxArt && !wantLogo) return null
        val auth = bearer(apiKey)
        val (idType, id) = if (steamAppId != null) {
            SteamGridDbApi.BY_STEAM to steamAppId
        } else {
            val term = searchTerm(title).ifBlank { return null }
            val candidates = api.search(auth, term).dataOrNull() ?: return null
            val match = bestMatch(title, candidates) ?: return null
            SteamGridDbApi.BY_GAME to match.id.toString()
        }
        val boxArt = if (wantBoxArt) api.grids(auth, idType, id).dataOrNull()?.let(::pickImage) else null
        val logo = if (wantLogo) api.logos(auth, idType, id).dataOrNull()?.let(::pickImage) else null
        return if (boxArt == null && logo == null) null else Art(boxArt, logo)
    }

    private fun bearer(key: String) = "Bearer ${key.trim()}"

    private fun <T> Response<SgdbResponse<T>>.dataOrNull(): T? =
        if (isSuccessful) body()?.takeIf { it.success == true }?.data else null

    companion object {
        /** First image that's safe to show (the API already filters, but don't trust it alone). */
        fun pickImage(images: List<SgdbImageDto>): String? = images.firstOrNull {
            !it.url.isNullOrBlank() && it.nsfw != true && it.humor != true && it.epilepsy != true
        }?.url

        /** The title as a search term: no region/revision tags, no characters that break a path. */
        fun searchTerm(title: String): String =
            title.replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
                .replace(Regex("[/\\\\?#%]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

        /** Lowercase alphanumeric words, with "The X" / "X, The" made equal. */
        fun normalize(title: String): String {
            var t = searchTerm(title).lowercase()
            t = t.replace(Regex(",\\s*the$"), "").replace(Regex("^the\\s+"), "")
            return t.replace("&", " and ").replace(Regex("[^a-z0-9]+"), " ").trim()
        }

        /**
         * Exact normalized match wins; otherwise the first candidate sharing ≥ 80% of words
         * (Jaccard). Anything looser is rejected.
         */
        fun bestMatch(title: String, candidates: List<SgdbGameDto>): SgdbGameDto? {
            val target = normalize(title)
            if (target.isBlank()) return null
            val usable = candidates.filter { it.id != null && !it.name.isNullOrBlank() }
            usable.firstOrNull { normalize(it.name!!) == target }?.let { return it }
            val targetWords = target.split(' ').toSet()
            return usable.firstOrNull { c ->
                val words = normalize(c.name!!).split(' ').toSet()
                val union = (words + targetWords).size
                union > 0 && (words intersect targetWords).size.toDouble() / union >= 0.8
            }
        }

        /** Steam app id from a Steam-library rom path ("steam:<appid>"); other stores have none. */
        fun steamAppId(romPath: String): String? =
            Regex("^steam:(\\d+)$").find(romPath)?.groupValues?.get(1)
    }
}
