package com.gamelaunch.frontend.domain.usecase

import android.util.Xml
import androidx.room.withTransaction
import com.gamelaunch.frontend.data.db.AppDatabase
import com.gamelaunch.frontend.data.db.dao.GameMediaDao
import com.gamelaunch.frontend.data.db.entity.GameMediaEntity
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.util.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import javax.inject.Inject

sealed class EsdeImportStatus {
    object Scanning  : EsdeImportStatus()
    data class Complete(val matched: Int, val total: Int) : EsdeImportStatus()
    data class Error(val message: String) : EsdeImportStatus()
}

// Index built once per folder: (platformDir_lc, typeDir_lc, nameWithoutExt) → absolutePath.
private typealias MediaIndex = HashMap<Triple<String, String, String>, String>

class ImportEsdeMediaUseCase @Inject constructor(
    private val gameRepository: GameRepository,
    private val gameMediaDao: GameMediaDao,
    private val settingsRepository: SettingsRepository,
    private val appDatabase: AppDatabase
) {

    /** The media files ES-DE holds for one game (any subset may be present). */
    private data class ResolvedEsdeMedia(
        val boxArt: String?,
        val screenshot: String?,
        val video: String?,
        val background: String?,
        val wheelLogo: String?,
        val miximage: String?
    ) {
        val hasAny: Boolean
            get() = boxArt != null || screenshot != null || video != null ||
                    background != null || wheelLogo != null || miximage != null
    }

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
        "xbox360"   to listOf("xbox360", "xbox 360"),
        "cps1"      to listOf("cps1", "arcade"),
        "cps2"      to listOf("cps2", "arcade"),
        "cps3"      to listOf("cps3", "arcade"),
        "c64"       to listOf("c64"),
        "pico8"     to listOf("pico8"),
    )

    /** Resolve the ES-DE `downloaded_media` root under [mediaFolderPath], or null if inaccessible. */
    private fun resolveRoot(mediaFolderPath: String): File? {
        if (mediaFolderPath.isBlank()) return null
        val picked = File(mediaFolderPath)
        return when {
            File(picked, "downloaded_media").isDirectory -> File(picked, "downloaded_media")
            picked.isDirectory -> picked
            else -> null
        }
    }

    /**
     * Build the (platformDir, typeDir, name) → path index once for the whole folder, avoiding
     * per-game filesystem traversal (O(files) once vs O(games*files)).
     */
    private fun buildIndex(root: File): MediaIndex {
        val index = MediaIndex()
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
        return index
    }

    /** Look a game's media up in [index] across the platform's candidate ES-DE directory names. */
    private fun resolve(game: Game, index: MediaIndex): ResolvedEsdeMedia {
        val dirs = (platformDirMap[game.platformId] ?: listOf(game.platformId)).map { it.lowercase() }
        val nameKey = game.romFilename.substringBeforeLast(".")

        var boxArt:     String? = null
        var screenshot: String? = null
        var video:      String? = null
        var background: String? = null
        var wheelLogo:  String? = null
        var miximage:   String? = null

        for (dir in dirs) {
            boxArt     = boxArt     ?: index[Triple(dir, "box2dfront",   nameKey)]
                                    ?: index[Triple(dir, "covers",       nameKey)]
            screenshot = screenshot ?: index[Triple(dir, "screenshots",  nameKey)]
                                    ?: index[Triple(dir, "titlescreens", nameKey)]
            video      = video      ?: index[Triple(dir, "videos",       nameKey)]
            background = background ?: index[Triple(dir, "fanart",       nameKey)]
                                    ?: index[Triple(dir, "backgrounds",  nameKey)]
            // ES-DE stores the wheel/logo art in `marquees` (plural). Older ES-DE builds and
            // other frontends use `marquee`/`wheel`/`logos`, so try those too.
            wheelLogo  = wheelLogo  ?: index[Triple(dir, "marquees",     nameKey)]
                                    ?: index[Triple(dir, "marquee",      nameKey)]
                                    ?: index[Triple(dir, "wheel",        nameKey)]
                                    ?: index[Triple(dir, "logos",        nameKey)]
            // ES-DE composites live in `miximages`.
            miximage   = miximage   ?: index[Triple(dir, "miximages",    nameKey)]
                                    ?: index[Triple(dir, "miximage",      nameKey)]
            if (boxArt != null && screenshot != null && video != null &&
                background != null && wheelLogo != null && miximage != null) break
        }
        return ResolvedEsdeMedia(boxArt, screenshot, video, background, wheelLogo, miximage)
    }

    /** Merge resolved ES-DE paths into the game's media row, keeping anything already stored. */
    private suspend fun upsert(gameId: Long, media: ResolvedEsdeMedia) {
        val existing = gameMediaDao.getMediaForGame(gameId)
        val updated  = (existing ?: GameMediaEntity(gameId = gameId)).copy(
            boxArtLocalPath     = media.boxArt     ?: existing?.boxArtLocalPath,
            screenshotLocalPath = media.screenshot ?: existing?.screenshotLocalPath,
            videoLocalPath      = media.video      ?: existing?.videoLocalPath,
            backgroundLocalPath = media.background ?: existing?.backgroundLocalPath,
            wheelLogoLocalPath  = media.wheelLogo  ?: existing?.wheelLogoLocalPath,
            miximageLocalPath   = media.miximage   ?: existing?.miximageLocalPath,
        )
        gameMediaDao.upsertMedia(updated)
    }

    /**
     * Apply resolved ES-DE media and gamelist descriptions to [games]; returns ids that got either.
     * All writes run inside a single transaction — without it, a large library was thousands of
     * individually-committed UPDATE/UPSERTs, each fsync'ing, which made the scrape's prep phase crawl.
     */
    private suspend fun applyToGames(
        games: List<Game>,
        index: MediaIndex,
        descIndex: Map<String, String>
    ): Set<Long> = appDatabase.withTransaction {
        val matched = mutableSetOf<Long>()
        games.forEach { game ->
            var got = false
            if (index.isNotEmpty()) {
                val media = resolve(game, index)
                if (media.hasAny) {
                    upsert(game.id, media)
                    got = true
                }
            }
            if (descIndex.isNotEmpty()) {
                val desc = descIndex[game.romFilename.substringBeforeLast(".")]
                if (!desc.isNullOrBlank()) {
                    gameRepository.fillDescriptionIfMissing(game.id, desc)
                    got = true
                }
            }
            if (got) matched += game.id
        }
        matched
    }

    /**
     * Import ES-DE media (and gamelist descriptions) for just [games] from [mediaFolderPath],
     * returning the ids that matched something. Used by the scraper to satisfy artwork/metadata from
     * an existing ES-DE library before making any network request. A no-op (empty set) when the
     * folder is unset/missing and no gamelist descriptions are found.
     */
    suspend fun importForGames(mediaFolderPath: String, games: List<Game>): Set<Long> =
        withContext(Dispatchers.IO) {
            val root = resolveRoot(mediaFolderPath)
            val index = root?.let { buildIndex(it) } ?: MediaIndex()
            val descIndex = buildDescriptionIndex(mediaFolderPath)
            if (index.isEmpty() && descIndex.isEmpty()) return@withContext emptySet()
            applyToGames(games, index, descIndex)
        }

    operator fun invoke(mediaFolderPath: String): Flow<EsdeImportStatus> = flow {
        emit(EsdeImportStatus.Scanning)

        val root = resolveRoot(mediaFolderPath)
        if (root == null) {
            emit(EsdeImportStatus.Error("Folder not accessible: $mediaFolderPath"))
            return@flow
        }

        val index = buildIndex(root)
        val descIndex = buildDescriptionIndex(mediaFolderPath)
        if (index.isEmpty() && descIndex.isEmpty()) {
            // No existing media or descriptions — not an error; the folder is just empty (e.g. picked
            // for future storage). Report nothing matched.
            emit(EsdeImportStatus.Complete(matched = 0, total = 0))
            return@flow
        }

        val games = gameRepository.getAllGames().first()
        val matched = applyToGames(games, index, descIndex)
        emit(EsdeImportStatus.Complete(matched = matched.size, total = games.size))
    }.flowOn(Dispatchers.IO)

    /**
     * Build a `romNameWithoutExtension → description` map from every ES-DE gamelist.xml reachable
     * from the media folder: the standard `<ES-DE root>/gamelists/<system>/gamelist.xml`, plus any
     * gamelist.xml inside the ROM tree (legacy / in-place layout). Empty when none are found.
     */
    private suspend fun buildDescriptionIndex(mediaFolderPath: String): Map<String, String> {
        val roots = LinkedHashSet<File>()
        if (mediaFolderPath.isNotBlank()) {
            val picked = File(mediaFolderPath)
            // The ES-DE root is the folder holding downloaded_media (the picked folder, or its parent
            // when the user pointed straight at downloaded_media).
            val esdeRoot = when {
                File(picked, "downloaded_media").isDirectory -> picked
                picked.name.equals("downloaded_media", ignoreCase = true) -> picked.parentFile
                else -> picked
            }
            esdeRoot?.let { File(it, "gamelists").takeIf(File::isDirectory)?.let(roots::add) }
            File(picked, "gamelists").takeIf(File::isDirectory)?.let(roots::add)
        }
        // Legacy: gamelist.xml living inside the ROM system folders.
        val romRoot = settingsRepository.romRootPath.first()
        if (romRoot.isNotBlank()) {
            File(StorageUtils.resolveStoredPath(romRoot)).takeIf(File::isDirectory)?.let(roots::add)
        }
        if (roots.isEmpty()) return emptyMap()

        // ES-DE keeps exactly one gamelist.xml per system: <root>/<system>/gamelist.xml (and some
        // setups put one at <root>/gamelist.xml). Look those up directly — a handful of stat calls
        // per root — instead of walking the whole (potentially enormous) ROM tree to find them.
        val out = HashMap<String, String>()
        roots.flatMap(::gamelistFilesIn).distinctBy { it.absolutePath }
            .forEach { parseGamelistDescriptions(it, out) }
        return out
    }

    /** gamelist.xml files directly under [root] and each of its immediate sub-folders. */
    private fun gamelistFilesIn(root: File): List<File> {
        val found = ArrayList<File>()
        File(root, "gamelist.xml").takeIf(File::isFile)?.let(found::add)
        root.listFiles()?.forEach { child ->
            if (child.isDirectory) File(child, "gamelist.xml").takeIf(File::isFile)?.let(found::add)
        }
        return found
    }

    /** Read `<game><path>…</path><desc>…</desc></game>` entries into [out], keyed by rom base name. */
    private fun parseGamelistDescriptions(file: File, out: HashMap<String, String>) {
        runCatching {
            file.inputStream().use { stream ->
                val parser = Xml.newPullParser().apply {
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    setInput(stream, null)
                }
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "game") {
                        var path: String? = null
                        var desc: String? = null
                        var e = parser.next()
                        while (!(e == XmlPullParser.END_TAG && parser.name == "game")) {
                            if (e == XmlPullParser.START_TAG) {
                                when (parser.name) {
                                    "path" -> path = parser.nextText().trim()
                                    "desc" -> desc = parser.nextText().trim()
                                    else   -> { /* skip other fields */ }
                                }
                            }
                            e = parser.next()
                        }
                        val key = path?.let { File(it).nameWithoutExtension }
                        if (!key.isNullOrBlank() && !desc.isNullOrBlank()) out.putIfAbsent(key, desc)
                    }
                    event = parser.next()
                }
            }
        }
    }
}
