package com.gamelaunch.frontend.data.theme

import androidx.compose.ui.graphics.Color
import com.gamelaunch.frontend.data.network.dto.ThemeGalleryDto
import com.gamelaunch.frontend.ui.theme.EorTheme
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** One theme listed in the online gallery. */
data class GalleryTheme(
    val id: String,
    val name: String,
    val author: String?,
    val file: String,
    val accent: Color,
    val accent2: Color,
    val background: Color
) {
    /** The id it gets once installed — matches CustomThemeRepository's naming. */
    val installedId: String get() = ThemeFile.idFor(name)
}

/**
 * The theme gallery: `themes/index.json` plus `.eortheme` files in the eOr repo, served from
 * GitHub like the platform catalog. Fetched only when the user opens the gallery. Installing
 * downloads the file and goes through [CustomThemeRepository.import], so a gallery theme is
 * validated exactly like one imported by hand.
 */
@Singleton
class ThemeGalleryRepository internal constructor(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val customThemes: CustomThemeRepository
) {
    @Inject constructor(
        @Named("catalog") client: OkHttpClient,
        customThemes: CustomThemeRepository
    ) : this(client, BASE_URL, customThemes)

    /** The gallery list. Throws with a user-facing message if it can't be loaded. */
    suspend fun list(): List<GalleryTheme> = withContext(Dispatchers.IO) {
        val json = fetch("index.json", MAX_INDEX_BYTES).toString(Charsets.UTF_8)
        parseIndex(json)
    }

    /** Downloads, validates and stores a gallery theme. */
    suspend fun install(theme: GalleryTheme): EorTheme {
        val bytes = withContext(Dispatchers.IO) { fetch(theme.file, MAX_THEME_BYTES) }
        return customThemes.import(bytes)
    }

    private fun fetch(path: String, maxBytes: Long): ByteArray {
        val request = Request.Builder().url(baseUrl + path).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw ThemeFileException("Theme gallery unavailable (HTTP ${resp.code})")
            val source = resp.body?.source() ?: throw ThemeFileException("Theme gallery sent nothing")
            if (source.request(maxBytes + 1)) throw ThemeFileException("Theme gallery file is too large")
            return source.readByteArray()
        }
    }

    companion object {
        const val BASE_URL = "https://raw.githubusercontent.com/keweis2/eOr/main/themes/"
        private const val MAX_INDEX_BYTES = 256L * 1024
        private const val MAX_THEME_BYTES = 1024L * 1024
        private val ID = Regex("^[a-z0-9][a-z0-9-]{0,39}$")
        private val HEX = Regex("^#[0-9a-fA-F]{6}$")

        /** Parses index.json; entries that don't validate are skipped rather than failing the list. */
        fun parseIndex(json: String): List<GalleryTheme> {
            val dto = runCatching { Gson().fromJson(json, ThemeGalleryDto::class.java) }.getOrNull()
                ?: throw ThemeFileException("Theme gallery index is not valid")
            if ((dto.schemaVersion ?: 0) != 1) throw ThemeFileException("Theme gallery needs a newer eOr")
            return dto.themes.orEmpty().mapNotNull { e ->
                val id = e.id?.takeIf { it.matches(ID) } ?: return@mapNotNull null
                val name = e.name?.trim()?.takeIf { it.isNotEmpty() && it.length <= 32 } ?: return@mapNotNull null
                // Files must sit right next to index.json and be named after the id.
                val file = e.file?.takeIf { it == "$id.${ThemeFile.EXTENSION}" } ?: return@mapNotNull null
                fun c(v: String?) = v?.takeIf { it.matches(HEX) }?.let { Color(0xFF000000 or it.substring(1).toLong(16)) }
                val accent = c(e.accent) ?: return@mapNotNull null
                GalleryTheme(
                    id = id,
                    name = name,
                    author = e.author?.trim()?.take(40)?.takeIf { it.isNotEmpty() },
                    file = file,
                    accent = accent,
                    accent2 = c(e.accent2) ?: accent,
                    background = c(e.background) ?: Color(0xFF111318)
                )
            }
        }
    }
}
