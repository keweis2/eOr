package com.gamelaunch.frontend.data.catalog

import com.gamelaunch.frontend.data.network.dto.CatalogDto
import com.gamelaunch.frontend.data.network.dto.CatalogEmulatorDto
import com.gamelaunch.frontend.data.network.dto.CatalogLaunchDto
import com.gamelaunch.frontend.data.network.dto.CatalogPlatformDto
import com.gamelaunch.frontend.domain.model.KnownEmulator
import com.gamelaunch.frontend.domain.model.LaunchSpec
import com.gamelaunch.frontend.domain.model.Platform
import com.gamelaunch.frontend.domain.model.RomUriMode
import com.gamelaunch.frontend.domain.platform.PlatformCatalog
import com.google.gson.Gson
import com.google.gson.GsonBuilder

/**
 * Converts catalog JSON to a [PlatformCatalog] and back.
 *
 * The catalog feeds the ROM scanner and the intents eOr fires at other apps, so every field is
 * checked against a strict shape. A malformed entry is dropped (and reported) rather than failing
 * the whole document, so one typo can't knock out every other platform's update; a document with an
 * unknown schema or a too-new minAppVersionCode is rejected outright.
 */
object CatalogParser {

    const val SCHEMA_VERSION = 1

    sealed interface Result {
        data class Ok(val catalog: PlatformCatalog, val rejected: List<String>) : Result
        data class Invalid(val reason: String) : Result
    }

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

    private val ID = Regex("^[a-z0-9][a-z0-9_-]{0,31}$")
    // One or two segments: ".iso", ".nkit.iso".
    private val EXTENSION = Regex("^(\\.[a-z0-9]{1,8}){1,2}$")
    // Java package / fully-qualified class name: dot-separated identifiers, at least two segments.
    private val QUALIFIED_NAME = Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+$")
    private val EXTRA_KEY = Regex("^[A-Za-z0-9_.]{1,64}$")
    private val MIME = Regex("^[a-z]+/[a-z0-9.+-]+$")
    private val CORE = Regex("^[a-z0-9_]+_libretro\\.so$")
    private const val MAX_NAME = 64

    fun parse(json: String, appVersionCode: Int): Result {
        val dto = runCatching { gson.fromJson(json, CatalogDto::class.java) }.getOrNull()
            ?: return Result.Invalid("not a JSON object")
        if (dto.schemaVersion != SCHEMA_VERSION) return Result.Invalid("unsupported schemaVersion ${dto.schemaVersion}")
        val revision = dto.revision?.takeIf { it > 0 } ?: return Result.Invalid("missing revision")
        if ((dto.minAppVersionCode ?: 0) > appVersionCode) {
            return Result.Invalid("needs app versionCode ${dto.minAppVersionCode}, have $appVersionCode")
        }

        val rejected = mutableListOf<String>()
        val platforms = linkedMapOf<String, Platform>()
        val priority = linkedMapOf<String, List<String>>()
        dto.platforms.orEmpty().forEachIndexed { i, p ->
            // runCatching: Gson can put nulls inside List<String> despite the Kotlin type.
            val platform = runCatching { p.toPlatformOrNull() }.getOrNull()
            if (platform == null || platform.id in platforms) {
                rejected += "platform[$i] ${p.id}"
                return@forEachIndexed
            }
            platforms[platform.id] = platform
            val emulators = p.emulators.orEmpty().filter { it != null && it.matches(QUALIFIED_NAME) }.distinct()
            if (emulators.isNotEmpty()) priority[platform.id] = emulators
        }

        val emulators = linkedMapOf<String, KnownEmulator>()
        val launchSpecs = linkedMapOf<String, LaunchSpec>()
        dto.emulators.orEmpty().forEachIndexed { i, e ->
            val emulator = runCatching { e.toEmulatorOrNull() }.getOrNull()
            val spec = runCatching { e.launch?.toLaunchSpecOrNull() }.getOrNull()
            // A launch block that fails validation rejects the whole emulator: silently falling
            // back to the generic VIEW intent would look like the recipe was applied when it wasn't.
            if (emulator == null || (e.launch != null && spec == null) || emulator.packageName in emulators) {
                rejected += "emulator[$i] ${e.packageName}"
                return@forEachIndexed
            }
            emulators[emulator.packageName] = emulator
            if (spec != null) launchSpecs[emulator.packageName] = spec
        }

        return Result.Ok(
            PlatformCatalog(
                revision = revision,
                platforms = platforms.values.toList(),
                emulators = emulators.values.toList(),
                emulatorPriority = priority,
                launchSpecs = launchSpecs
            ),
            rejected
        )
    }

    /** Serialises [catalog] in the published format (used to generate catalog/catalog.json). */
    fun toJson(catalog: PlatformCatalog, minAppVersionCode: Int): String = gson.toJson(
        CatalogDto(
            schemaVersion = SCHEMA_VERSION,
            revision = catalog.revision,
            minAppVersionCode = minAppVersionCode,
            platforms = catalog.platforms.map { p ->
                CatalogPlatformDto(
                    id = p.id,
                    displayName = p.displayName,
                    scraperSystemId = p.scraperSystemId,
                    extensions = p.extensions,
                    folderNames = p.folderNames,
                    retroArchCore = p.defaultCoreForRetroArch,
                    defaultEmulator = p.defaultEmulatorPackage,
                    emulators = catalog.emulatorPriority[p.id]
                )
            },
            emulators = catalog.emulators.map { e ->
                CatalogEmulatorDto(
                    packageName = e.packageName,
                    displayName = e.displayName,
                    launch = catalog.launchSpecs[e.packageName]?.let { s ->
                        CatalogLaunchDto(
                            activity = s.activity,
                            action = if (s.action == LaunchSpec.ACTION_MAIN) "MAIN" else null,
                            romExtraKey = s.romExtraKey,
                            mimeType = s.mimeType,
                            romUri = if (s.romUriMode == RomUriMode.CONTENT) "CONTENT" else null
                        )
                    }
                )
            }
        )
    )

    private fun CatalogPlatformDto.toPlatformOrNull(): Platform? {
        val id = id?.takeIf { it.matches(ID) } ?: return null
        val name = displayName?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_NAME } ?: return null
        val scraperId = scraperSystemId?.takeIf { it >= 0 } ?: return null
        val exts = extensions.orEmpty().map { it.lowercase() }
        if (exts.isEmpty() || exts.any { !it.matches(EXTENSION) }) return null
        val folders = folderNames.orEmpty()
        if (folders.isEmpty() || folders.any { !it.isValidFolderName() }) return null
        val core = retroArchCore?.also { if (!it.matches(CORE)) return null }
        val defaultPkg = defaultEmulator?.also { if (!it.matches(QUALIFIED_NAME)) return null }
        return Platform(
            id = id,
            displayName = name,
            scraperSystemId = scraperId,
            extensions = exts.distinct(),
            folderNames = folders.distinct(),
            defaultEmulatorPackage = defaultPkg,
            defaultCoreForRetroArch = core
        )
    }

    private fun String.isValidFolderName(): Boolean =
        isNotBlank() && length <= MAX_NAME && none { it == '/' || it == '\\' || it.isISOControl() } &&
            this != "." && this != ".."

    private fun CatalogEmulatorDto.toEmulatorOrNull(): KnownEmulator? {
        val pkg = packageName?.takeIf { it.matches(QUALIFIED_NAME) } ?: return null
        val name = displayName?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_NAME } ?: return null
        return KnownEmulator(pkg, name)
    }

    private fun CatalogLaunchDto.toLaunchSpecOrNull(): LaunchSpec? {
        val activity = activity?.takeIf { it.matches(QUALIFIED_NAME) } ?: return null
        val intentAction = when (action ?: "VIEW") {
            "VIEW" -> LaunchSpec.ACTION_VIEW
            "MAIN" -> LaunchSpec.ACTION_MAIN
            else -> return null
        }
        val uriMode = when (romUri ?: "FILE") {
            "FILE" -> RomUriMode.FILE
            "CONTENT" -> RomUriMode.CONTENT
            else -> return null
        }
        if (romExtraKey != null && !romExtraKey.matches(EXTRA_KEY)) return null
        if (mimeType != null && !mimeType.matches(MIME)) return null
        return LaunchSpec(
            activity = activity,
            romExtraKey = romExtraKey,
            action = intentAction,
            mimeType = mimeType,
            romUriMode = uriMode
        )
    }
}
