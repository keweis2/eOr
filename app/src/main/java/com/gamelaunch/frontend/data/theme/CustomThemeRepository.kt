package com.gamelaunch.frontend.data.theme

import android.content.Context
import com.gamelaunch.frontend.ui.theme.EorTheme
import com.gamelaunch.frontend.ui.theme.EorThemes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Themes the user imported (from a file, Web Transfer, or the gallery), kept as `.eortheme` files
 * in app storage. Loaded synchronously on first use — a handful of tiny files — so a custom theme
 * applies on the very first frame instead of flashing the default.
 */
@Singleton
class CustomThemeRepository internal constructor(private val dir: File) {

    @Inject constructor(@ApplicationContext context: Context) : this(File(context.filesDir, "themes"))

    private val _themes = MutableStateFlow(loadAll())
    val themes: StateFlow<List<EorTheme>> = _themes.asStateFlow()

    /** A built-in or imported theme by id; unknown ids fall back to Default. */
    fun resolve(id: String?): EorTheme =
        _themes.value.firstOrNull { it.id == id } ?: EorThemes.byId(id)

    /** Validates and stores a theme file; re-importing the same name replaces it. Throws [ThemeFileException]. */
    suspend fun import(bytes: ByteArray): EorTheme = withContext(Dispatchers.IO) {
        val theme = ThemeFile.read(bytes)
        if (_themes.value.none { it.id == theme.id } && _themes.value.size >= MAX_THEMES) {
            throw ThemeFileException("You have $MAX_THEMES custom themes — delete one first")
        }
        dir.mkdirs()
        // Store our own normalised copy, not the uploaded bytes.
        File(dir, theme.id + "." + ThemeFile.EXTENSION).writeBytes(ThemeFile.write(theme))
        _themes.value = (_themes.value.filterNot { it.id == theme.id } + theme).sortedBy { it.name.lowercase() }
        theme
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        if (!id.startsWith(ThemeFile.ID_PREFIX)) return@withContext
        File(dir, id + "." + ThemeFile.EXTENSION).delete()
        _themes.value = _themes.value.filterNot { it.id == id }
    }

    /** `.eortheme` bytes for any theme (built-in or custom), for Export. */
    fun export(theme: EorTheme): ByteArray = ThemeFile.write(theme)

    private fun loadAll(): List<EorTheme> =
        (dir.listFiles { f -> f.extension == ThemeFile.EXTENSION } ?: emptyArray())
            .mapNotNull { f -> runCatching { ThemeFile.read(f.readBytes()) }.getOrNull() }
            .sortedBy { it.name.lowercase() }

    companion object {
        const val MAX_THEMES = 30
    }
}
