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
    data class Result(val path: String, val gameCount: Int)

    /** @return the folder with the most recognisable ROMs, or null if nothing convincing was found. */
    suspend operator fun invoke(): Result? = withContext(Dispatchers.IO) {
        val roots = buildList {
            add(Environment.getExternalStorageDirectory().absolutePath)   // /storage/emulated/0
            StorageUtils.getStorageVolumes(context).forEach { add(it.second) }  // SD cards, USB…
        }.distinct()

        val candidates = roots.flatMap { root ->
            CANDIDATE_NAMES.map { File(root, it) }
        }.filter { it.isDirectory }.distinctBy { it.absolutePath }

        candidates
            .map { dir -> Result(dir.absolutePath, countRoms(dir)) }
            .filter { it.gameCount > 0 }
            .maxByOrNull { it.gameCount }
    }

    /** Count ROM-like files under [dir], skipping emulator-data folders, capped so it stays snappy. */
    private fun countRoms(dir: File): Int {
        var count = 0
        val walk = dir.walkTopDown()
            .maxDepth(4)
            .onEnter { !it.name.startsWith(".") && it.name.lowercase() !in SKIP_FOLDERS }
        for (file in walk) {
            if (count >= CAP) break
            if (!file.isFile || file.name.startsWith(".")) continue
            if (".${file.extension.lowercase()}" in SKIP_EXTENSIONS) continue
            if (platformDetector.detect(file, file.parentFile?.name ?: "") != null) count++
        }
        return count
    }

    private companion object {
        const val CAP = 500
        val CANDIDATE_NAMES = listOf(
            "ROMs", "Roms", "roms", "ROMS",
            "Games", "games",
            "RetroArch/roms", "retroarch/roms",
            "Emulation/roms"
        )
        val SKIP_EXTENSIONS = setOf(
            ".txt", ".xml", ".cue", ".nfo", ".jpg", ".jpeg", ".png", ".mp4", ".rar",
            ".sav", ".srm", ".state", ".m3u", ".dat", ".db"
        )
        // Emulator-data + OS folders that never contain ROMs (mirrors ScanRomsUseCase).
        val SKIP_FOLDERS = setOf(
            "savedata", "save", "saves", "savestates", "states", "savefiles",
            "sdmc", "nand", "shaders", "cache", "log", "logs", "dump", "dumps",
            "screenshots", "cheats", "textures", "texture_cache", "system",
            "memcards", "bios", "tmp", "temp", "config", "configs", "media",
            "android", "dcim", "download", "downloads", "music", "movies",
            "pictures", "documents", "notifications", "ringtones", "alarms"
        )
    }
}
