package com.gamelaunch.frontend.domain.usecase

import com.gamelaunch.frontend.data.db.dao.GameMediaDao
import com.gamelaunch.frontend.data.db.entity.GameMediaEntity
import com.gamelaunch.frontend.domain.repository.GameRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject

sealed class EsdeImportStatus {
    object Scanning  : EsdeImportStatus()
    data class Complete(val matched: Int, val total: Int) : EsdeImportStatus()
    data class Error(val message: String) : EsdeImportStatus()
}

class ImportEsdeMediaUseCase @Inject constructor(
    private val gameRepository: GameRepository,
    private val gameMediaDao: GameMediaDao
) {

    // Maps our internal platformId → the directory names ES-DE uses (try in order)
    private val platformDirMap = mapOf(
        "nes"       to listOf("nes"),
        "snes"      to listOf("snes"),
        "n64"       to listOf("n64"),
        "gb"        to listOf("gb"),
        "gbc"       to listOf("gbc"),
        "gba"       to listOf("gba"),
        "nds"       to listOf("nds"),
        "ps1"       to listOf("psx", "ps1"),
        "ps2"       to listOf("ps2"),
        "psp"       to listOf("psp"),
        "dc"        to listOf("dreamcast"),
        "genesis"   to listOf("megadrive", "genesis"),
        "sms"       to listOf("mastersystem"),
        "gg"        to listOf("gamegear"),
        "saturn"    to listOf("saturn"),
        "32x"       to listOf("sega32x"),
        "3ds"       to listOf("3ds"),
        "switch"    to listOf("switch"),
        "atari2600" to listOf("atari2600"),
        "mame"      to listOf("mame", "arcade"),
    )

    operator fun invoke(mediaFolderPath: String): Flow<EsdeImportStatus> = flow {
        emit(EsdeImportStatus.Scanning)

        val picked = File(mediaFolderPath)
        // Accept ES-DE root (contains downloaded_media/) or the folder itself
        val root = when {
            File(picked, "downloaded_media").isDirectory -> File(picked, "downloaded_media")
            picked.isDirectory -> picked
            else -> {
                emit(EsdeImportStatus.Error("Folder not accessible: $mediaFolderPath"))
                return@flow
            }
        }

        // Build index: (platformDir_lc, typeDir_lc, nameWithoutExt) → absolutePath
        // This avoids per-game filesystem traversal (O(files) once vs O(games*files))
        val index = HashMap<Triple<String, String, String>, String>()
        root.listFiles()?.forEach { platformDir ->
            if (!platformDir.isDirectory) return@forEach
            val pKey = platformDir.name.lowercase()
            platformDir.listFiles()?.forEach { typeDir ->
                if (!typeDir.isDirectory) return@forEach
                val tKey = typeDir.name.lowercase()
                typeDir.listFiles()?.forEach { file ->
                    index[Triple(pKey, tKey, file.nameWithoutExtension)] = file.absolutePath
                }
            }
        }

        if (index.isEmpty()) {
            emit(EsdeImportStatus.Error(
                "No media found. Expected structure: downloaded_media/<system>/<type>/<name>.*"
            ))
            return@flow
        }

        val games = gameRepository.getAllGames().first()
        var matched = 0

        games.forEach { game ->
            val dirs = (platformDirMap[game.platformId] ?: listOf(game.platformId))
                .map { it.lowercase() }
            val nameKey = game.romFilename.substringBeforeLast(".")

            var boxArt:     String? = null
            var screenshot: String? = null
            var video:      String? = null
            var background: String? = null
            var wheelLogo:  String? = null

            for (dir in dirs) {
                boxArt     = boxArt     ?: index[Triple(dir, "box2dfront",   nameKey)]
                                        ?: index[Triple(dir, "3dboxes",      nameKey)]
                                        ?: index[Triple(dir, "covers",       nameKey)]
                screenshot = screenshot ?: index[Triple(dir, "screenshots",  nameKey)]
                                        ?: index[Triple(dir, "titlescreens", nameKey)]
                video      = video      ?: index[Triple(dir, "videos",       nameKey)]
                background = background ?: index[Triple(dir, "fanart",       nameKey)]
                                        ?: index[Triple(dir, "backgrounds",  nameKey)]
                wheelLogo  = wheelLogo  ?: index[Triple(dir, "marquee",      nameKey)]
                                        ?: index[Triple(dir, "logos",        nameKey)]
                if (boxArt != null && screenshot != null && video != null &&
                    background != null && wheelLogo != null) break
            }

            if (boxArt != null || screenshot != null || video != null ||
                background != null || wheelLogo != null) {
                val existing = gameMediaDao.getMediaForGame(game.id)
                val updated  = (existing ?: GameMediaEntity(gameId = game.id)).copy(
                    boxArtLocalPath     = boxArt     ?: existing?.boxArtLocalPath,
                    screenshotLocalPath = screenshot ?: existing?.screenshotLocalPath,
                    videoLocalPath      = video      ?: existing?.videoLocalPath,
                    backgroundLocalPath = background ?: existing?.backgroundLocalPath,
                    wheelLogoLocalPath  = wheelLogo  ?: existing?.wheelLogoLocalPath,
                )
                gameMediaDao.upsertMedia(updated)
                matched++
            }
        }

        emit(EsdeImportStatus.Complete(matched = matched, total = games.size))
    }.flowOn(Dispatchers.IO)
}
