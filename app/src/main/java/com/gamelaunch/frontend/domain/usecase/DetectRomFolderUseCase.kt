package com.gamelaunch.frontend.domain.usecase

import android.content.Context
import android.os.Environment
import com.gamelaunch.frontend.domain.platform.PlatformDetector
import com.gamelaunch.frontend.util.StorageUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Best-effort auto-detection of the user's ROM library so onboarding can lead with "I found your
 * games!" instead of Android's cryptic folder picker. Probes the usual shared-storage locations
 * (internal + SD) for the common ROM folder names and counts recognisable ROMs (capped for speed).
 */
class DetectRomFolderUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val platformDetector: PlatformDetector
) {
    /**
     * A candidate games folder. [gameCount] is capped at [CAP] (shown as "N+"); [perSystem] maps
     * platform display name → games counted there, so onboarding can show *what* it found.
     */
    data class Result(
        val path: String,
        val gameCount: Int,
        val perSystem: Map<String, Int> = emptyMap(),
        /** True when counting stopped early, so the real total is higher ("N+"). */
        val capped: Boolean = false
    )

    /** @return the folder with the most recognisable ROMs, or null if nothing convincing was found. */
    suspend operator fun invoke(preferred: String? = null): Result? = all(preferred).firstOrNull()

    /**
     * Every folder that looks like a games library, best first. [preferred] (e.g. ES-DE's configured
     * ROM directory) is ranked first whenever it holds any games, so we agree with the user's setup.
     */
    suspend fun all(preferred: String? = null): List<Result> = withContext(Dispatchers.IO) {
        val roots = buildList {
            add(Environment.getExternalStorageDirectory().absolutePath)   // /storage/emulated/0
            StorageUtils.getStorageVolumes(context).forEach { add(it.second) }  // SD cards, USB…
        }.distinct()

        val candidates = buildList {
            preferred?.let { add(File(it)) }
            roots.forEach { root -> CANDIDATE_NAMES.forEach { add(File(root, it)) } }
        }.filter { it.isDirectory }
            // SD cards (FAT/exFAT) are case-insensitive, so "ROMs", "roms" and "ROMS" can all be the
            // same folder — collapse them, keeping the first spelling.
            .distinctBy { it.canonicalPath.lowercase() }

        val found = candidates.map { dir -> count(dir) }.filter { it.gameCount > 0 }
        val pref = preferred?.let { p ->
            found.firstOrNull { File(it.path).canonicalPath.equals(File(p).canonicalPath, ignoreCase = true) }
        }
        listOfNotNull(pref) + (found - setOfNotNull(pref)).sortedByDescending { it.gameCount }
    }

    /**
     * Count ROM-like files under [dir], skipping emulator-data folders. Each top-level sub-folder
     * (usually one per system) is counted separately and capped at [PER_FOLDER_CAP], so the breakdown
     * covers every system instead of just the first few alphabetically, and it stays snappy.
     */
    private fun count(dir: File): Result {
        val perSystem = HashMap<String, Int>()
        var total = 0
        var capped = false
        val children = dir.listFiles()?.filter { !it.name.startsWith(".") } ?: emptyList()
        // Loose files at the top level count as their own "folder".
        val groups: List<Sequence<File>> = children.filter { it.isDirectory && it.name.lowercase() !in SKIP_FOLDERS }
            .map { walkGames(it) } + sequenceOf(children.asSequence().filter { it.isFile })
        for (files in groups) {
            if (total >= CAP) { capped = true; break }
            var inFolder = 0
            for (file in files) {
                if (inFolder >= PER_FOLDER_CAP) { capped = true; break }
                if (".${file.extension.lowercase()}" in SKIP_EXTENSIONS) continue
                val platform = platformDetector.detect(file, file.parentFile?.name ?: "") ?: continue
                inFolder++
                perSystem.merge(platform.displayName, 1, Int::plus)
            }
            total += inFolder
        }
        return Result(dir.absolutePath, total, perSystem, capped)
    }

    private fun walkGames(folder: File): Sequence<File> = folder.walkTopDown()
        .maxDepth(3)
        .onEnter { !it.name.startsWith(".") && it.name.lowercase() !in SKIP_FOLDERS }
        .filter { it.isFile && !it.name.startsWith(".") }

    companion object {
        const val CAP = 3000
        /** Most games counted per system folder; onboarding shows "200+" at the cap. */
        const val PER_FOLDER_CAP = 200
        private val CANDIDATE_NAMES = listOf(
            "ROMs", "Roms", "roms", "ROMS",
            "Games", "games",
            "RetroArch/roms", "retroarch/roms",
            "Emulation/roms"
        )
        private val SKIP_EXTENSIONS = setOf(
            ".txt", ".xml", ".nfo", ".jpg", ".jpeg", ".png", ".mp4", ".rar",
            ".sav", ".srm", ".state", ".dat", ".db"
        )
        // Emulator-data + OS folders that never contain ROMs (mirrors ScanRomsUseCase).
        private val SKIP_FOLDERS = setOf(
            "savedata", "save", "saves", "savestates", "states", "savefiles",
            "sdmc", "nand", "shaders", "cache", "log", "logs", "dump", "dumps",
            "screenshots", "cheats", "textures", "texture_cache", "system",
            "memcards", "bios", "firmware", "firmwares", "tmp", "temp", "config", "configs", "media",
            "android", "dcim", "download", "downloads", "music", "movies",
            "pictures", "documents", "notifications", "ringtones", "alarms"
        )
    }
}
