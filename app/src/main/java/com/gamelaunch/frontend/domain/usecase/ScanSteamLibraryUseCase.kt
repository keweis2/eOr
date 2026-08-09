package com.gamelaunch.frontend.domain.usecase

import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.util.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject

/**
 * Adds PC games to the "steam" platform from a folder the user points
 * [SettingsRepository.steamLibraryPath] at. Two folder shapes are understood:
 *
 *  1. **A GameNative "Export for ES-DE" directory (preferred).** GameNative writes one small file
 *     per installed game, named `<title>.<ext>` where the extension is the store (`.steam`, `.epic`,
 *     `.gog`, `.amazon`, `.pcgame`) and the file's text is the numeric app id. This lives on shared
 *     storage and is exactly what an ES-DE-style frontend is meant to consume — filename → title,
 *     contents → app id, extension → source.
 *  2. **A raw Steam `steamapps` tree / Steam install root.** Falls back to parsing
 *     `appmanifest_<appid>.acf` manifests (all treated as the STEAM source).
 *
 * Games are stored with romPath "steam:<SOURCE>:<appid>" (SOURCE omitted ⇒ STEAM);
 * [com.gamelaunch.frontend.launcher.EmulatorLauncher] boots them by handing the id + source to the
 * configured frontend (GameNative launches directly; others open the app).
 *
 * Note: a frontend that keeps its library in private app storage and has no export directory set
 * exposes nothing eOr can read — a GameNative export dir on shared storage (or a steamapps tree on a
 * mounted drive) is what makes this find anything.
 */
class ScanSteamLibraryUseCase @Inject constructor(
    private val gameRepository: GameRepository,
    private val settingsRepository: SettingsRepository
) {
    operator fun invoke(): Flow<ScanProgress> = flow {
        val rawPath = settingsRepository.steamLibraryPath.first()
        if (rawPath.isBlank()) {
            emit(ScanProgress(0, 0, "No Steam library folder set"))
            return@flow
        }
        val root = File(StorageUtils.resolveStoredPath(rawPath))

        // Prefer GameNative's export files; only fall back to raw steamapps manifests if none exist.
        val entries = findExportEntries(root).ifEmpty { findManifestEntries(root) }

        emit(ScanProgress(0, entries.size, "Scanning Steam library…"))

        var added = 0
        entries.forEachIndexed { index, entry ->
            emit(ScanProgress(index, entries.size, entry.title, added))
            if (entry.appId in IGNORED_APP_IDS || entry.looksLikeRuntime()) return@forEachIndexed
            // "steam:<appid>" for the default STEAM source keeps the path short; other stores carry
            // their source so the launcher can pass the right game_source to GameNative.
            val romPath =
                if (entry.source == SOURCE_STEAM) "steam:${entry.appId}"
                else "steam:${entry.source}:${entry.appId}"
            val game = Game(
                title       = entry.title,
                romPath     = romPath,
                romFilename = entry.installDir.ifBlank { entry.title },
                platformId  = "steam"
            )
            // insertGame ignores rows that clash on the unique romPath (returns <= 0), so a rescan
            // only counts genuinely new games — mirrors ScanAndroidGamesUseCase.
            if (gameRepository.insertGame(game) > 0) added++
        }

        emit(ScanProgress(entries.size, entries.size, added = added))
    }.flowOn(Dispatchers.IO)

    // ── GameNative "Export for ES-DE" files ───────────────────────────────────────────────────────

    /** Reads `<title>.<store-ext>` export files (title = filename, app id = contents). */
    private fun findExportEntries(root: File): List<SteamApp> {
        if (!root.isDirectory) return emptyList()
        // Look in the folder itself and one level down (users may pick a parent of the export dir).
        val files = root.walkTopDown().maxDepth(2)
            .filter { it.isFile && EXPORT_EXTENSIONS.containsKey(it.extension.lowercase()) }
        return files.mapNotNull { file ->
            val source = EXPORT_EXTENSIONS[file.extension.lowercase()] ?: return@mapNotNull null
            val appId = runCatching { file.readText().trim() }.getOrNull()?.toIntOrNull()
                ?: return@mapNotNull null
            SteamApp(appId, file.nameWithoutExtension.trim(), installDir = "", source = source)
        }.distinctBy { it.source to it.appId }.toList()
    }

    // ── Raw steamapps `appmanifest_*.acf` fallback ────────────────────────────────────────────────

    private fun findManifestEntries(root: File): List<SteamApp> =
        findAppManifests(root).mapNotNull { parseManifest(it) }.distinctBy { it.appId }

    private fun findAppManifests(root: File): List<File> {
        if (!root.isDirectory) return emptyList()
        val direct = (root.listAcf() + File(root, "steamapps").listAcf()).distinctBy { it.absolutePath }
        if (direct.isNotEmpty()) return direct
        return root.walkTopDown()
            .maxDepth(6)
            .filter { it.isFile && it.name.startsWith("appmanifest_") && it.extension == "acf" }
            .toList()
    }

    private fun File.listAcf(): List<File> =
        if (isDirectory) {
            listFiles { f -> f.isFile && f.name.startsWith("appmanifest_") && f.extension == "acf" }
                ?.toList() ?: emptyList()
        } else emptyList()

    private fun parseManifest(acf: File): SteamApp? {
        val text = runCatching { acf.readText() }.getOrNull() ?: return null
        val appId = KEY_APPID.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val name = KEY_NAME.find(text)?.groupValues?.get(1)?.trim().orEmpty()
        val installDir = KEY_INSTALLDIR.find(text)?.groupValues?.get(1)?.trim().orEmpty()
        if (name.isBlank()) return null
        return SteamApp(appId, name, installDir, SOURCE_STEAM)
    }

    private data class SteamApp(
        val appId: Int,
        val title: String,
        val installDir: String,
        val source: String
    ) {
        fun looksLikeRuntime(): Boolean =
            RUNTIME_NAME_PREFIXES.any { title.startsWith(it, ignoreCase = true) }
    }

    private companion object {
        const val SOURCE_STEAM = "STEAM"
        // GameNative export extension → GameSource enum name it maps to (see FrontendSyncManager).
        val EXPORT_EXTENSIONS = mapOf(
            "steam" to "STEAM",
            "epic" to "EPIC",
            "gog" to "GOG",
            "amazon" to "AMAZON",
            "pcgame" to "CUSTOM_GAME"
        )
        // Steamworks Common Redistributables — GameNative writes this alongside real games.
        val IGNORED_APP_IDS = setOf(228980)
        val RUNTIME_NAME_PREFIXES = listOf(
            "Steamworks Common", "Steam Linux Runtime", "Proton"
        )
        // ACF is Valve's VDF: `"key"\t\t"value"`. Whitespace between key and value varies.
        val KEY_APPID = Regex("\"appid\"\\s+\"(\\d+)\"", RegexOption.IGNORE_CASE)
        val KEY_NAME = Regex("\"name\"\\s+\"([^\"]*)\"", RegexOption.IGNORE_CASE)
        val KEY_INSTALLDIR = Regex("\"installdir\"\\s+\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    }
}
