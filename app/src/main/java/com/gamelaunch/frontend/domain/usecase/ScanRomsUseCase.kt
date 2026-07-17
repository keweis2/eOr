package com.gamelaunch.frontend.domain.usecase

import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.platform.PlatformDetector
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.util.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.inject.Inject

data class ScanProgress(
    val scanned: Int,
    val total: Int,
    val currentFile: String = "",
    val added: Int = 0
)

class ScanRomsUseCase @Inject constructor(
    private val gameRepository: GameRepository,
    private val platformDetector: PlatformDetector,
    private val settingsRepository: SettingsRepository
) {
    private val skipExtensions = setOf(
        ".txt", ".xml", ".cue", ".nfo", ".jpg", ".png", ".mp4", ".rar",
        ".sav", ".srm", ".state", ".m3u"
    )

    // Emulator data sub-folders (saves, shaders, system files…) that hold no ROMs. Pruning
    // them keeps stray data files (e.g. PSP SAVEDATA/*.bin) out of the library and speeds scans.
    private val skipFolders = setOf(
        "savedata", "save", "saves", "savestates", "states", "savefiles",
        "sdmc", "nand", "shaders", "cache", "log", "logs", "dump", "dumps",
        "screenshots", "cheats", "textures", "texture_cache", "system",
        "memcards", "memory cards", "bios", "tmp", "temp", "config", "configs",
        "os0", "vs0", "ur0", "tm0", "ud0", "pd0", "sa0", "gro0", "grw0",
        "license", "appmeta", "ppsspp_state", "private"
    )

    operator fun invoke(rootPath: String): Flow<ScanProgress> = flow {
        val resolvedPath = StorageUtils.resolveStoredPath(rootPath)
        val rootDir = File(resolvedPath)
        if (!rootDir.exists() || !rootDir.isDirectory) {
            emit(ScanProgress(0, 0, "Root folder not found: $resolvedPath"))
            return@flow
        }

        // Paths the user has manually removed from the library — never re-add them.
        val excludedPaths = settingsRepository.excludedPaths.first()

        val romFiles = rootDir.walkTopDown()
            // Don't descend into hidden folders or known emulator-data folders.
            .onEnter { !it.name.startsWith(".") && it.name.lowercase() !in skipFolders }
            // Skip hidden files (e.g. macOS "._Foo.chd" AppleDouble files and .DS_Store)
            .filter { it.isFile && !it.name.startsWith(".") && ".${it.extension.lowercase()}" !in skipExtensions }
            .filterNot { it.absolutePath in excludedPaths }
            .toList()

        val validPaths = mutableListOf<String>()
        var added = 0

        romFiles.forEachIndexed { index, file ->
            emit(ScanProgress(index, romFiles.size, file.name, added))

            val platform = platformDetector.detect(file, file.parentFile?.name ?: "") ?: return@forEachIndexed

            validPaths.add(file.absolutePath)

            val md5 = computeMd5Partial(file)
            val title = file.nameWithoutExtension
                .replace(Regex("\\(.*?\\)"), "")
                .replace(Regex("\\[.*?]"), "")
                .trim()

            val game = Game(
                title = title,
                romPath = file.absolutePath,
                romFilename = file.name,
                platformId = platform.id,
                md5 = md5
            )

            val insertedId = gameRepository.insertGame(game)
            if (insertedId > 0) added++
        }

        if (validPaths.isEmpty()) {
            gameRepository.deleteAllNonAndroidGames()
        } else {
            gameRepository.deleteGamesNotInPaths(validPaths)
        }

        emit(ScanProgress(romFiles.size, romFiles.size, added = added))
    }.flowOn(Dispatchers.IO) // move all file I/O and hashing off the main thread

    private fun computeMd5Partial(file: File): String? = runCatching {
        val md = MessageDigest.getInstance("MD5")
        if (file.extension.equals("zip", ignoreCase = true)) {
            ZipInputStream(file.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                var foundFile = false
                while (entry != null) {
                    if (!entry.isDirectory) {
                        foundFile = true
                        val buffer = ByteArray(512 * 1024)
                        val read = zip.read(buffer)
                        if (read > 0) md.update(buffer, 0, read)
                        break
                    }
                    entry = zip.nextEntry
                }
                if (!foundFile) {
                    file.inputStream().use { stream ->
                        val buffer = ByteArray(512 * 1024)
                        val read = stream.read(buffer)
                        if (read > 0) md.update(buffer, 0, read)
                    }
                }
            }
        } else {
            file.inputStream().use { stream ->
                val buffer = ByteArray(512 * 1024)
                val read = stream.read(buffer)
                if (read > 0) md.update(buffer, 0, read)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
