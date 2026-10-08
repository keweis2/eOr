package com.gamelaunch.frontend.data.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.gamelaunch.frontend.data.network.dto.ThemeFileDto
import com.gamelaunch.frontend.data.network.dto.ThemeFocusDto
import com.gamelaunch.frontend.data.network.dto.ThemeSurfacesDto
import com.gamelaunch.frontend.data.network.dto.ThemeTonesDto
import com.gamelaunch.frontend.data.network.dto.ThemeWallpaperDto
import com.gamelaunch.frontend.ui.theme.CardColorConfig
import com.gamelaunch.frontend.ui.theme.CardColorScheme
import com.gamelaunch.frontend.ui.theme.EorTheme
import com.gamelaunch.frontend.ui.theme.ThemeDraft
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** A theme file couldn't be read; [message] is shown to the user as-is. */
class ThemeFileException(message: String) : IllegalArgumentException(message)

/** A theme plus its background image bytes, if it has one. */
class ThemePackage(val theme: EorTheme, val wallpaper: ByteArray?)

/**
 * Reads and writes `.eortheme` files: a zip holding one `theme.json` (a bare `.json` is accepted
 * too) and, optionally, the background image it names. Only `name` and `accent` are required —
 * every other colour is derived from the accent when missing, so a minimal theme is a few lines.
 * Everything is validated; nothing but `theme.json` and JPEG/PNG/WebP images is read out of the zip.
 */
object ThemeFile {

    const val FORMAT = "eor-theme"
    const val SCHEMA_VERSION = 1
    const val EXTENSION = "eortheme"
    const val ID_PREFIX = "custom-"
    private const val ENTRY = "theme.json"
    const val WALLPAPER_ENTRY = "wallpaper.jpg"
    private const val MAX_JSON_BYTES = 256 * 1024
    const val MAX_IMAGE_BYTES = 6 * 1024 * 1024
    const val MAX_FILE_BYTES = 8 * 1024 * 1024
    private const val MAX_NAME = 32

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val HEX = Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
    private val IMAGE_NAME = Regex("^[A-Za-z0-9_-][A-Za-z0-9._-]{0,63}\\.(jpe?g|png|webp)$", RegexOption.IGNORE_CASE)

    /** Parses an `.eortheme` (zip) or raw theme JSON. Throws [ThemeFileException]. */
    fun read(bytes: ByteArray): EorTheme = readPackage(bytes).theme

    /** Like [read], but also returns the background image (not yet decoded or resized). */
    fun readPackage(bytes: ByteArray): ThemePackage {
        if (bytes.size > MAX_FILE_BYTES) throw ThemeFileException("Theme file is too large")
        val entries = if (isZip(bytes)) entriesFromZip(bytes) else mapOf(ENTRY to bytes)
        val json = entries[ENTRY]?.toString(Charsets.UTF_8)
            ?: throw ThemeFileException("No theme.json found in the theme file")
        val dto = runCatching { gson.fromJson(json, ThemeFileDto::class.java) }.getOrNull()
            ?: throw ThemeFileException("Not a valid theme file")
        val theme = toTheme(dto)
        val image = dto.wallpaper?.let { w ->
            val name = w.image?.trim()?.takeIf { it.matches(IMAGE_NAME) }
                ?: throw ThemeFileException("\"wallpaper.image\" must be an image file name like wallpaper.jpg")
            val img = entries[name.lowercase()] ?: throw ThemeFileException("The theme's background image ($name) is missing")
            if (!isImage(img)) throw ThemeFileException("The theme's background image isn't a JPEG, PNG or WebP")
            img
        }
        return ThemePackage(theme, image)
    }

    /**
     * The `.eortheme` bytes for [theme] — what Export writes and the gallery hosts. Pass the
     * background image as [wallpaper] when the theme has one; it's stored as [WALLPAPER_ENTRY].
     */
    fun write(theme: EorTheme, author: String? = null, wallpaper: ByteArray? = null): ByteArray {
        val image = wallpaper?.takeIf { theme.wallpaper != null }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(ENTRY))
            zip.write(toJson(theme, author, hasImage = image != null).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            if (image != null) {
                zip.putNextEntry(ZipEntry(WALLPAPER_ENTRY))
                zip.write(image)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** True for JPEG, PNG and WebP data (by signature). */
    fun isImage(b: ByteArray): Boolean = when {
        b.size >= 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte() -> true
        b.size >= 8 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() &&
            b[3] == 'G'.code.toByte() -> true
        b.size >= 12 && String(b, 0, 4, Charsets.US_ASCII) == "RIFF" && String(b, 8, 4, Charsets.US_ASCII) == "WEBP" -> true
        else -> false
    }

    fun toJson(theme: EorTheme, author: String? = null, hasImage: Boolean = false): String = gson.toJson(
        ThemeFileDto(
            format = FORMAT,
            schemaVersion = SCHEMA_VERSION,
            name = theme.name,
            author = author,
            accent = hex(theme.accent),
            accent2 = hex(theme.accent2),
            accent3 = hex(theme.accent3),
            dark = tonesDto(theme.dark),
            light = tonesDto(theme.light),
            darkBackground = gson.toJsonTree(surfacesDto(theme.darkSurfaces)),
            darkGlows = theme.darkGlows.map(::hex),
            lightGlows = theme.lightGlows.map(::hex),
            tiles = when (theme.cards.scheme) {
                CardColorScheme.RAINBOW -> "rainbow"
                CardColorScheme.BLACK_WHITE -> "grey"
                CardColorScheme.MONOCHROME -> hex(theme.cards.monochromeSeed)
            },
            focus = ThemeFocusDto(dark = hex(theme.focusDark), light = hex(theme.focusLight)),
            wallpaper = theme.wallpaper?.takeIf { hasImage }?.let {
                ThemeWallpaperDto(WALLPAPER_ENTRY, it.dimDark, it.dimLight, it.blur, it.glows)
            }
        )
    )

    /** Stable id for an imported theme: re-importing the same name updates it. */
    fun idFor(name: String): String =
        ID_PREFIX + name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifEmpty { "theme" }

    // ── Parsing ─────────────────────────────────────────────────────────────────────────────────

    private fun isZip(b: ByteArray) = b.size >= 4 && b[0] == 0x50.toByte() && b[1] == 0x4B.toByte() &&
        b[2] == 0x03.toByte() && b[3] == 0x04.toByte()

    /**
     * theme.json and any images, keyed by lower-cased file name. Entries may sit at the root or
     * inside one top-level folder (how zip tools often pack); everything else is skipped unread.
     */
    private fun entriesFromZip(bytes: ByteArray): Map<String, ByteArray> {
        val found = HashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val path = entry.name.replace('\\', '/')
                if (entry.isDirectory || path.count { it == '/' } > 1) continue
                val name = path.substringAfterLast('/')
                val limit = when {
                    name == ENTRY -> MAX_JSON_BYTES
                    name.matches(IMAGE_NAME) -> MAX_IMAGE_BYTES
                    else -> continue
                }
                val buf = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val n = zip.read(chunk)
                    if (n < 0) break
                    buf.write(chunk, 0, n)
                    total += n
                    if (buf.size() > limit) {
                        throw ThemeFileException(if (name == ENTRY) "theme.json is too large" else "The theme's background image is too large")
                    }
                    // Guards against zip bombs: the unpacked total can't grow far past the file cap.
                    if (total > MAX_FILE_BYTES * 2L) throw ThemeFileException("Theme file is too large")
                }
                found.putIfAbsent(name.lowercase(), buf.toByteArray())
            }
        }
        return found
    }

    private fun toTheme(d: ThemeFileDto): EorTheme {
        if (d.format != null && d.format != FORMAT) throw ThemeFileException("Not an eOr theme")
        if ((d.schemaVersion ?: SCHEMA_VERSION) > SCHEMA_VERSION) {
            throw ThemeFileException("This theme needs a newer version of eOr")
        }
        val name = d.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ThemeFileException("Theme has no name")
        if (name.length > MAX_NAME) throw ThemeFileException("Theme name is too long (max $MAX_NAME)")
        val accent = color(d.accent, "accent") ?: throw ThemeFileException("Theme has no accent colour")
        val accent2 = color(d.accent2, "accent2") ?: accent
        val accent3 = color(d.accent3, "accent3") ?: lerp(accent, Color.White, 0.35f)

        val surfaces = surfaces(d.darkBackground)
        val tiles = when (val t = d.tiles?.trim()?.lowercase()) {
            null, "accent" -> CardColorConfig(CardColorScheme.MONOCHROME, accent)
            "rainbow" -> CardColorConfig(CardColorScheme.RAINBOW)
            "grey", "gray", "bw", "black-white" -> CardColorConfig(CardColorScheme.BLACK_WHITE)
            else -> CardColorConfig(CardColorScheme.MONOCHROME, color(t, "tiles")!!)
        }
        val darkGlows = d.darkGlows?.let { glows(it, "darkGlows") }
            ?: if (surfaces == EorTheme.Surfaces.Oled) emptyList() else listOf(accent, accent2, accent3)
        val lightGlows = d.lightGlows?.let { glows(it, "lightGlows") } ?: listOf(accent, accent2, accent3, accent2)

        return EorTheme(
            id = idFor(name),
            name = name,
            accent = accent,
            accent2 = accent2,
            accent3 = accent3,
            dark = tones(d.dark, "dark") ?: derivedDark(accent),
            light = tones(d.light, "light") ?: derivedLight(accent),
            darkSurfaces = surfaces,
            darkGlows = darkGlows,
            lightGlows = lightGlows,
            cards = tiles,
            focusDark = color(d.focus?.dark, "focus.dark") ?: accent,
            focusLight = color(d.focus?.light, "focus.light") ?: accent,
            wallpaper = d.wallpaper?.let { w ->
                val defaults = EorTheme.Wallpaper()
                fun unit(v: Float?, fallback: Float) = (v ?: fallback).takeIf { !it.isNaN() }?.coerceIn(0f, 1f) ?: fallback
                EorTheme.Wallpaper(
                    dimDark = unit(w.dimDark, defaults.dimDark),
                    dimLight = unit(w.dimLight, defaults.dimLight),
                    blur = unit(w.blur, defaults.blur),
                    glows = w.glows ?: defaults.glows
                )
            }
        )
    }

    private fun color(value: String?, field: String): Color? {
        val v = value?.trim() ?: return null
        if (!v.matches(HEX)) throw ThemeFileException("\"$field\" must be a colour like #FF8A3D")
        val digits = v.substring(1)
        val argb = if (digits.length == 6) 0xFF000000 or digits.toLong(16) else digits.toLong(16)
        return Color(argb.toInt())
    }

    private fun glows(list: List<String>, field: String): List<Color> {
        if (list.size > 4) throw ThemeFileException("\"$field\" can have at most 4 colours")
        return list.map { color(it, field) ?: throw ThemeFileException("\"$field\" has an empty colour") }
    }

    private fun tones(t: ThemeTonesDto?, field: String): EorTheme.Tones? {
        t ?: return null
        fun req(v: String?, n: String) = color(v, "$field.$n") ?: throw ThemeFileException("\"$field.$n\" is missing")
        return EorTheme.Tones(
            req(t.primary, "primary"), req(t.onPrimary, "onPrimary"),
            req(t.primaryContainer, "primaryContainer"), req(t.onPrimaryContainer, "onPrimaryContainer")
        )
    }

    private fun surfaces(el: JsonElement?): EorTheme.Surfaces {
        if (el == null || el.isJsonNull) return EorTheme.Surfaces.Navy
        if (el.isJsonPrimitive) return when (el.asString.lowercase()) {
            "navy" -> EorTheme.Surfaces.Navy
            "oled", "black" -> EorTheme.Surfaces.Oled
            else -> throw ThemeFileException("\"darkBackground\" must be \"navy\", \"oled\" or a set of colours")
        }
        val s = runCatching { gson.fromJson(el, ThemeSurfacesDto::class.java) }.getOrNull()
            ?: throw ThemeFileException("\"darkBackground\" is not valid")
        val base = EorTheme.Surfaces.Navy
        fun c(v: String?, n: String, fallback: Color) = color(v, "darkBackground.$n") ?: fallback
        return EorTheme.Surfaces(
            background = c(s.background, "background", base.background),
            surface = c(s.surface, "surface", base.surface),
            card = c(s.card, "card", base.card),
            border = c(s.border, "border", base.border),
            containerLowest = c(s.containerLowest, "containerLowest", base.containerLowest),
            containerLow = c(s.containerLow, "containerLow", base.containerLow),
            container = c(s.container, "container", base.container),
            containerHigh = c(s.containerHigh, "containerHigh", base.containerHigh),
            containerHighest = c(s.containerHighest, "containerHighest", base.containerHighest),
            outlineVariant = c(s.outlineVariant, "outlineVariant", base.outlineVariant),
            chip = c(s.chip, "chip", base.chip),
            chipBorder = c(s.chipBorder, "chipBorder", base.chipBorder)
        )
    }

    private fun derivedDark(a: Color) = ThemeDraft.darkTones(a)

    private fun derivedLight(a: Color) = ThemeDraft.lightTones(a)

    // ── Writing ─────────────────────────────────────────────────────────────────────────────────

    private fun hex(c: Color): String {
        val argb = c.toArgb()
        return if ((argb ushr 24) == 0xFF) "#%06X".format(argb and 0xFFFFFF) else "#%08X".format(argb)
    }

    private fun tonesDto(t: EorTheme.Tones) =
        ThemeTonesDto(hex(t.primary), hex(t.onPrimary), hex(t.primaryContainer), hex(t.onPrimaryContainer))

    private fun surfacesDto(s: EorTheme.Surfaces) = ThemeSurfacesDto(
        hex(s.background), hex(s.surface), hex(s.card), hex(s.border), hex(s.containerLowest),
        hex(s.containerLow), hex(s.container), hex(s.containerHigh), hex(s.containerHighest),
        hex(s.outlineVariant), hex(s.chip), hex(s.chipBorder)
    )
}
