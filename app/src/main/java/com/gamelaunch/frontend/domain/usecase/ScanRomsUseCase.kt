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
        ".txt", ".xml", ".nfo", ".jpg", ".png", ".mp4", ".rar",
        ".sav", ".srm", ".state"
    )

    // Emulator data sub-folders (saves, shaders, system files…) that hold no ROMs. Pruning
    // them keeps stray data files (e.g. PSP SAVEDATA/*.bin) out of the library and speeds scans.
    private val skipFolders = setOf(
        "savedata", "save", "saves", "savestates", "states", "savefiles",
        "sdmc", "nand", "shaders", "cache", "log", "logs", "dump", "dumps",
        "screenshots", "cheats", "textures", "texture_cache", "system",
        "memcards", "memory cards", "bios", "firmware", "firmwares", "tmp", "temp", "config", "configs",
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

        val referencedPaths = mutableSetOf<String>()
        romFiles.forEach { file ->
            val ext = file.extension.lowercase()
            if (ext == "cue") {
                parseCueReferencedFiles(file).forEach { refFile ->
                    referencedPaths.add(getNormalizedPath(refFile))
                }
            } else if (ext == "m3u") {
                parseM3uReferencedFiles(file).forEach { refFile ->
                    referencedPaths.add(getNormalizedPath(refFile))
                }
            }
        }

        val filteredRomFiles = romFiles.filterNot { file ->
            getNormalizedPath(file) in referencedPaths
        }

        val validPaths = mutableListOf<String>()
        var added = 0

        filteredRomFiles.forEachIndexed { index, file ->
            emit(ScanProgress(index, filteredRomFiles.size, file.name, added))

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

        emit(ScanProgress(filteredRomFiles.size, filteredRomFiles.size, added = added))
    }.flowOn(Dispatchers.IO) // move all file I/O and hashing off the main thread

    private fun parseCueReferencedFiles(cueFile: File): List<File> {
        val referencedFiles = mutableListOf<File>()
        val parentDir = cueFile.parentFile ?: return emptyList()
        runCatching {
            cueFile.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("FILE", ignoreCase = true)) {
                    val quoteStart = trimmed.indexOf('"')
                    val filename = if (quoteStart >= 0) {
                        val quoteEnd = trimmed.indexOf('"', quoteStart + 1)
                        if (quoteEnd > quoteStart) {
                            trimmed.substring(quoteStart + 1, quoteEnd)
                        } else {
                            trimmed.substring(quoteStart + 1)
                        }
                    } else {
                        val parts = trimmed.split(Regex("\\s+"))
                        if (parts.size >= 2) {
                            if (parts.size >= 3) {
                                parts.subList(1, parts.size - 1).joinToString(" ")
                            } else {
                                parts[1]
                            }
                        } else {
                            ""
                        }
                    }
                    if (filename.isNotEmpty()) {
                        referencedFiles.add(File(parentDir, filename))
                    }
                }
            }
        }
        return referencedFiles
    }

    private fun parseM3uReferencedFiles(m3uFile: File): List<File> {
        val referencedFiles = mutableListOf<File>()
        val parentDir = m3uFile.parentFile ?: return emptyList()
        runCatching {
            m3uFile.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    referencedFiles.add(File(parentDir, trimmed))
                }
            }
        }
        return referencedFiles
    }

    private fun getNormalizedPath(file: File): String {
        return runCatching { file.canonicalFile.absolutePath }.getOrDefault(file.absolutePath)
    }

    private fun computeMd5Partial(file: File): String? = runCatching {
        val md = MessageDigest.getInstance("MD5")
        if (file.extension.equals("zip", ignoreCase = true)) {
            ZipInputStream(file.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                var foundFile = false
                while (entry != null) {
                    if (!entry.isDirectory) {
                        foundFile = true
                        updateWithPartial(md, zip)
                        break
                    }
                    entry = zip.nextEntry
                }
                if (!foundFile) {
                    file.inputStream().use { stream -> updateWithPartial(md, stream) }
                }
            }
        } else {
            file.inputStream().use { stream -> updateWithPartial(md, stream) }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    /**
     * Feeds up to [PARTIAL_HASH_BYTES] of [stream] into [md]. A single
     * [java.io.InputStream.read] is not guaranteed to fill the buffer — this is
     * especially true for decompressing streams like [ZipInputStream], where one
     * read typically returns only a small inflate chunk. Looping until the window
     * is filled (or EOF) makes the hashed byte count deterministic and consistent
     * between raw and zipped ROMs.
     */
    private fun updateWithPartial(md: MessageDigest, stream: java.io.InputStream) {
        val buffer = ByteArray(PARTIAL_HASH_BYTES)
        var off = 0
        while (off < buffer.size) {
            val n = stream.read(buffer, off, buffer.size - off)
            if (n < 0) break
            off += n
        }
        if (off > 0) md.update(buffer, 0, off)
    }

    private companion object {
        const val PARTIAL_HASH_BYTES = 512 * 1024 // 512 KB — first-window hash, keeps memory bounded
    }
}
