package com.gamelaunch.frontend.domain.usecase

import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.platform.ArcadeNameResolver
import com.gamelaunch.frontend.domain.platform.PlatformDetector
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.util.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
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
    private val settingsRepository: SettingsRepository,
    private val importEmbeddedArtwork: ImportEmbeddedArtworkUseCase,
    private val prodKeysLocator: ProdKeysLocator,
    private val arcadeNameResolver: ArcadeNameResolver
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

    /**
     * The library-eligible ROM files under [rootPath] — the walk + cue/m3u de-duplication +
     * platform-exclusion filtering, shared by the full [invoke] scan and the quick [hasLibraryChanges]
     * check so both agree on exactly which files count as games. Returns null when the root folder
     * doesn't exist.
     */
    private suspend fun collectFilteredRomFiles(rootPath: String): List<File>? {
        val resolvedPath = StorageUtils.resolveStoredPath(rootPath)
        val rootDir = File(resolvedPath)
        if (!rootDir.exists() || !rootDir.isDirectory) return null

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

        return romFiles.filterNot { file ->
            getNormalizedPath(file) in referencedPaths
        }.filterNot { file ->
            val platform = platformDetector.detect(file, file.parentFile?.name ?: "")
            shouldExcludeFromLibrary(file, platform?.id)
        }
    }

    /**
     * A fast check — no hashing or DB writes — for whether the eligible paths on disk differ from
     * the ROM paths in the library. A missing root returns false so an unmounted SD card can never
     * erase the library; an existing empty root is a real change when the database still has ROMs.
     */
    suspend fun hasLibraryChanges(
        rootPath: String,
        minimumFileAgeMs: Long = 0L
    ): Boolean = withContext(Dispatchers.IO) {
        val files = collectFilteredRomFiles(rootPath) ?: return@withContext false
        val eligibleFiles = files.filter { file ->
            platformDetector.detect(file, file.parentFile?.name ?: "") != null
        }

        // FTP clients commonly write directly to the final filename. Do not start a full scan while
        // any candidate is still fresh: that could persist a partial ROM and attempt embedded-art
        // extraction before the relevant bytes have arrived. Waiting until the whole library is
        // quiet also prevents another stable addition from causing an in-progress file to be swept
        // into the same full scan.
        if (minimumFileAgeMs > 0L) {
            val stableBefore = System.currentTimeMillis() - minimumFileAgeMs
            if (eligibleFiles.any { it.lastModified() > stableBefore }) return@withContext false
        }

        val diskPaths = eligibleFiles.mapTo(hashSetOf()) { it.absolutePath }
        val knownPaths = gameRepository.getNonAndroidRomPaths().toHashSet()
        diskPaths != knownPaths
    }

    operator fun invoke(rootPath: String): Flow<ScanProgress> = flow {
        // Refresh the prod.keys search once per scan. The locator memoises its (expensive) storage
        // walk so it runs once for the whole scan rather than once per NSP; invalidating here keeps
        // that cache from hiding a key file the user added since the previous scan.
        prodKeysLocator.invalidate()

        val filteredRomFiles = collectFilteredRomFiles(rootPath)
        if (filteredRomFiles == null) {
            val resolvedPath = StorageUtils.resolveStoredPath(rootPath)
            emit(ScanProgress(0, 0, "Root folder not found: $resolvedPath"))
            return@flow
        }

        val validPaths = mutableListOf<String>()
        var added = 0

        filteredRomFiles.forEachIndexed { index, file ->
            emit(ScanProgress(index, filteredRomFiles.size, file.name, added))

            val platform = platformDetector.detect(file, file.parentFile?.name ?: "") ?: return@forEachIndexed

            validPaths.add(file.absolutePath)

            val md5 = computeMd5Partial(file)
            // Arcade ROMs are named after their MAME/FBNeo romset id (e.g. "afighter"), so map that
            // to the real game title ("Action Fighter") when we know it; otherwise fall back to the
            // cleaned filename like every other platform.
            val arcadeName = arcadeNameResolver.resolve(platform.id, file.name)
            val fallbackTitle = file.nameWithoutExtension
                .replace(Regex("\\(.*?\\)"), "")
                .replace(Regex("\\[.*?]"), "")
                .trim()
            val title = arcadeName ?: fallbackTitle

            val game = Game(
                title = title,
                romPath = file.absolutePath,
                romFilename = file.name,
                platformId = platform.id,
                md5 = md5
            )

            val insertedId = gameRepository.insertGame(game)
            if (insertedId > 0) added++

            val persistedGame = if (insertedId > 0) {
                game.copy(id = insertedId)
            } else {
                val existing = gameRepository.getGameByRomPath(file.absolutePath)
                // Backfill arcade titles for games added before we had the name table — but only when
                // the entry still shows its raw romset short name and hasn't been scraped or renamed,
                // so we never clobber a scraped title or a user's manual edit.
                if (existing != null && arcadeName != null && !existing.isScraped &&
                    existing.title == fallbackTitle && existing.title != arcadeName
                ) {
                    gameRepository.renameGame(existing.id, arcadeName)
                    existing.copy(title = arcadeName)
                } else {
                    existing
                }
            }
            persistedGame?.let { importEmbeddedArtwork(it, file) }
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

    /**
     * Platform-specific package conventions belong here, while the scan pipeline stays
     * format-agnostic. Add a new platform rule only when it can reliably identify a
     * non-launchable package.
     */
    private fun shouldExcludeFromLibrary(file: File, platformId: String?): Boolean = when (platformId) {
        "switch" -> isSwitchSupplementalPackage(file)
        else -> false
    }

    /**
     * Switch update and DLC NSPs cannot be launched as standalone games, so don't add
     * them to the library alongside their base NSP. Updates are commonly named with a
     * non-zero version tag (for example "[v65536]") or a patch title ID ending in 800;
     * DLC packages conventionally include the standalone word "DLC" or a bracketed
     * Add-On label.
     *
     * This intentionally only recognises explicit, conventional markers. An NSP with
     * an unfamiliar filename remains visible rather than risking a false positive.
     */
    private fun isSwitchSupplementalPackage(file: File): Boolean {
        if (!file.extension.equals("nsp", ignoreCase = true)) {
            return false
        }

        val filename = file.nameWithoutExtension
        return SWITCH_NON_ZERO_VERSION_TAG.containsMatchIn(filename) ||
            SWITCH_DLC_TAG.containsMatchIn(filename) ||
            SWITCH_TITLE_ID_TAG.findAll(filename).any { match ->
                match.groupValues[1].endsWith("800", ignoreCase = true)
            }
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
        val SWITCH_NON_ZERO_VERSION_TAG = Regex("""\[\s*v[1-9]\d*\s*]""", RegexOption.IGNORE_CASE)
        val SWITCH_DLC_TAG = Regex(
            """(?:\bDLC\b|\[\s*add[ -]?on\s*]|\(\s*add[ -]?on\s*\))""",
            RegexOption.IGNORE_CASE
        )
        val SWITCH_TITLE_ID_TAG = Regex("""\[([0-9a-f]{16})]""", RegexOption.IGNORE_CASE)
    }
}
