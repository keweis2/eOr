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
 * in app storage. Loaded synchronously on first use — a handful of small files — so a custom theme
 * applies on the very first frame instead of flashing the default. A theme's background image is
 * also kept unpacked beside it (`<id>.jpg`, see [wallpaperFile]) so drawing never touches the zip.
 */
@Singleton
class CustomThemeRepository internal constructor(
    private val dir: File,
    private val normalizer: WallpaperNormalizer = WallpaperNormalizer { it }
) {

    @Inject constructor(@ApplicationContext context: Context) :
        this(File(context.filesDir, "themes"), WallpaperImages)

    private val _themes = MutableStateFlow(loadAll())
    val themes: StateFlow<List<EorTheme>> = _themes.asStateFlow()

    /** A built-in or imported theme by id; unknown ids fall back to Default. */
    fun resolve(id: String?): EorTheme =
        _themes.value.firstOrNull { it.id == id } ?: EorThemes.byId(id)

    /** Validates and stores a theme file; re-importing the same name replaces it. Throws [ThemeFileException]. */
    suspend fun import(bytes: ByteArray): EorTheme = withContext(Dispatchers.IO) {
        val pkg = ThemeFile.readPackage(bytes)
        // Re-encode the image ourselves: bounded size, and only ever JPEG on disk.
        save(pkg.theme, pkg.wallpaper?.let(normalizer::normalize))
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        if (!id.startsWith(ThemeFile.ID_PREFIX)) return@withContext
        File(dir, id + "." + ThemeFile.EXTENSION).delete()
        wallpaperFile(id).delete()
        _themes.value = _themes.value.filterNot { it.id == id }
    }

    /** `.eortheme` bytes for any theme (built-in or custom), for Export — with its image if it has one. */
    fun export(theme: EorTheme): ByteArray =
        ThemeFile.write(theme, wallpaper = wallpaperFile(theme)?.readBytes())

    /** The unpacked background image of [theme], or null when it has none. */
    fun wallpaperFile(theme: EorTheme): File? =
        if (theme.wallpaper == null) null else wallpaperFile(theme.id).takeIf { it.isFile }

    /**
     * Gives [base] the picked image as its background. A custom theme is updated in place; a
     * built-in one is copied to a new custom theme ("My Ocean") so it can be exported and shared.
     */
    suspend fun setWallpaper(base: EorTheme, image: ByteArray): EorTheme = withContext(Dispatchers.IO) {
        val jpeg = normalizer.normalize(image)
        val target = if (base.id.startsWith(ThemeFile.ID_PREFIX)) base else {
            val name = "My ${base.name}".take(32)
            base.copy(id = ThemeFile.idFor(name), name = name)
        }
        save(target.copy(wallpaper = target.wallpaper ?: EorTheme.Wallpaper()), jpeg)
    }

    /** Changes how a custom theme's background image is drawn (dim, blur, glows). */
    suspend fun updateWallpaper(theme: EorTheme, wallpaper: EorTheme.Wallpaper): EorTheme = withContext(Dispatchers.IO) {
        val image = wallpaperFile(theme)?.readBytes() ?: return@withContext theme
        save(theme.copy(wallpaper = wallpaper), image)
    }

    /** Drops a custom theme's background image, keeping its colours. */
    suspend fun removeWallpaper(theme: EorTheme): EorTheme = withContext(Dispatchers.IO) {
        if (!theme.id.startsWith(ThemeFile.ID_PREFIX)) return@withContext theme
        save(theme.copy(wallpaper = null), null)
    }

    private fun save(theme: EorTheme, wallpaper: ByteArray?): EorTheme {
        val stored = if (wallpaper == null) theme.copy(wallpaper = null) else theme
        if (_themes.value.none { it.id == stored.id } && _themes.value.size >= MAX_THEMES) {
            throw ThemeFileException("You have $MAX_THEMES custom themes — delete one first")
        }
        dir.mkdirs()
        val image = wallpaperFile(stored.id)
        if (wallpaper != null) image.writeBytes(wallpaper) else image.delete()
        // Store our own normalised copy, not the uploaded bytes.
        File(dir, stored.id + "." + ThemeFile.EXTENSION).writeBytes(ThemeFile.write(stored, wallpaper = wallpaper))
        _themes.value = (_themes.value.filterNot { it.id == stored.id } + stored).sortedBy { it.name.lowercase() }
        return stored
    }

    private fun wallpaperFile(id: String) = File(dir, "$id.jpg")

    private fun loadAll(): List<EorTheme> =
        (dir.listFiles { f -> f.extension == ThemeFile.EXTENSION } ?: emptyArray())
            .mapNotNull { f ->
                runCatching {
                    val pkg = ThemeFile.readPackage(f.readBytes())
                    // Re-unpack the image if it went missing; drop the setting if it can't be.
                    val image = wallpaperFile(pkg.theme.id)
                    when {
                        pkg.theme.wallpaper == null -> pkg.theme
                        image.isFile -> pkg.theme
                        pkg.wallpaper != null -> pkg.theme.also { image.writeBytes(pkg.wallpaper) }
                        else -> pkg.theme.copy(wallpaper = null)
                    }
                }.getOrNull()
            }
            .sortedBy { it.name.lowercase() }

    companion object {
        const val MAX_THEMES = 30
    }
}
