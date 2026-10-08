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
     * Saves a theme from the editor. The id follows the name, so renaming a custom theme moves it
     * (the old one is removed) and editing a built-in always makes a new custom theme. A name
     * already used by a *different* custom theme is refused rather than silently overwritten.
     * The background image: [newImage] (already normalised) if the editor picked one, otherwise
     * [original]'s comes along — unless [edited] has no wallpaper, which removes it.
     */
    suspend fun saveEdited(original: EorTheme, edited: EorTheme, newImage: ByteArray? = null): EorTheme = withContext(Dispatchers.IO) {
        val name = edited.name.trim()
        if (name.isEmpty()) throw ThemeFileException("Give the theme a name")
        if (name.length > 32) throw ThemeFileException("Theme name is too long (max 32)")
        val id = ThemeFile.idFor(name)
        val originalIsCustom = original.id.startsWith(ThemeFile.ID_PREFIX)
        if (_themes.value.any { it.id == id } && !(originalIsCustom && original.id == id)) {
            throw ThemeFileException("You already have a theme called \"$name\"")
        }
        val image = when {
            edited.wallpaper == null -> null
            newImage != null -> newImage
            else -> wallpaperFile(original)?.readBytes()
        }
        val saved = save(edited.copy(id = id, name = name), image)
        if (originalIsCustom && original.id != id) {
            File(dir, original.id + "." + ThemeFile.EXTENSION).delete()
            wallpaperFile(original.id).delete()
            _themes.value = _themes.value.filterNot { it.id == original.id }
        }
        saved
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
