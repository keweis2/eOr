package com.gamelaunch.frontend.data.webserver

import com.gamelaunch.frontend.domain.platform.PlatformDefinitions
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides *where* a browser upload lands so it matches the user's existing library layout instead of
 * forcing eOr's canonical folder names.
 *
 * A user's ROMs may already sit in a non-canonical alias (`ROMs/Famicom`, `ROMs/psx`) or a nested
 * layout (`ROMs/Nintendo/SNES`). Writing to a fresh `<root>/<canonical>/` would split their library,
 * so we prefer a folder that already exists for the target platform and only create the canonical one
 * when there is nothing to reuse. All folder→platform recognition goes through
 * [PlatformDefinitions.byFolderName], which knows every alias.
 *
 * Pure `java.io.File` logic (no Android dependencies) so it is unit-testable on the JVM.
 */
@Singleton
class RomDestinationResolver @Inject constructor() {

    /** A recognised platform folder found under the ROM root. */
    data class PlatformFolder(
        val platformId: String,
        val displayName: String,
        /** Path relative to the ROM root, using '/' separators (e.g. "Nintendo/SNES"). */
        val relativePath: String,
        val depth: Int,
    )

    /**
     * Every directory under [root] whose name maps to a known platform, grouped by platform id.
     * The walk skips hidden and emulator-data folders and is depth-limited to keep it cheap.
     */
    fun existingPlatformFolders(root: File): Map<String, List<PlatformFolder>> {
        if (!root.isDirectory) return emptyMap()
        val found = mutableListOf<PlatformFolder>()
        fun visit(dir: File, depth: Int) {
            if (depth > MAX_SCAN_DEPTH) return
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (!child.isDirectory) continue
                val name = child.name
                if (name.startsWith(".") || name.lowercase() in SKIP_FOLDERS) continue
                val platform = PlatformDefinitions.byFolderName[name.lowercase()]
                if (platform != null) {
                    found += PlatformFolder(
                        platformId = platform.id,
                        displayName = platform.displayName,
                        relativePath = relativeTo(root, child),
                        depth = depth,
                    )
                }
                visit(child, depth + 1)
            }
        }
        visit(root, 1)
        return found.groupBy { it.platformId }
    }

    /**
     * The directory an uploaded ROM for [platformId] should be written to.
     *
     * @param explicitDest optional user-chosen relative path under [root]; used verbatim after
     *   containment validation. Throws [IllegalArgumentException] if it escapes [root].
     */
    fun resolveRomDir(root: File, platformId: String, explicitDest: String? = null): File {
        if (!explicitDest.isNullOrBlank()) {
            return containedDir(root, explicitDest)
                ?: throw IllegalArgumentException("Destination escapes the ROM root")
        }
        val existing = existingPlatformFolders(root)[platformId]
        if (!existing.isNullOrEmpty()) {
            // Prefer the shallowest existing folder (the most likely "main" one for that system).
            val best = existing.minByOrNull { it.depth }!!
            return File(root, best.relativePath)
        }
        val platform = PlatformDefinitions.byId[platformId]
            ?: throw IllegalArgumentException("Unknown platform: $platformId")
        return File(root, platform.folderNames.first())
    }

    /**
     * Strip any path components from [name] and reject traversal tokens, so a filename from the
     * network can never write outside its intended directory.
     */
    fun sanitizeFilename(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\').trim()
        require(base.isNotEmpty() && base != "." && base != "..") { "Invalid filename: $name" }
        // Drop control characters; keep everything else (ROM names carry spaces, brackets, unicode).
        val cleaned = base.filter { it.code >= 0x20 }
        require(cleaned.isNotEmpty()) { "Invalid filename: $name" }
        return cleaned
    }

    /**
     * Resolve [relPath] under [root] and return it only if the canonical result stays inside [root]
     * (defends against `..` and absolute paths). Creates the directory. Returns null if it escapes.
     */
    fun containedDir(root: File, relPath: String): File? {
        val candidate = File(root, relPath)
        val rootCanon = root.canonicalFile
        val candidateCanon = candidate.canonicalFile
        val rootPath = rootCanon.path + File.separator
        return if (candidateCanon == rootCanon || (candidateCanon.path + File.separator).startsWith(rootPath)) {
            candidateCanon
        } else {
            null
        }
    }

    private fun relativeTo(root: File, child: File): String =
        child.canonicalFile.path
            .removePrefix(root.canonicalFile.path)
            .trimStart(File.separatorChar)
            .replace(File.separatorChar, '/')

    private companion object {
        const val MAX_SCAN_DEPTH = 4
        // Emulator-data / non-ROM folders we never descend into or treat as a destination.
        val SKIP_FOLDERS = setOf(
            "savedata", "save", "saves", "savestates", "states", "savefiles",
            "sdmc", "nand", "shaders", "cache", "log", "logs", "dump", "dumps",
            "screenshots", "cheats", "textures", "texture_cache", "system",
            "memcards", "memory cards", "bios", "firmware", "firmwares", "tmp", "temp",
            "config", "configs", "media", "eor_media", "downloaded_media",
        )
    }
}
