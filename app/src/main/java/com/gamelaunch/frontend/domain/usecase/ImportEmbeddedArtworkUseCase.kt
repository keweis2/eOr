package com.gamelaunch.frontend.domain.usecase

import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Imports artwork bundled with a ROM package without involving the network scraper. */
@Singleton
class ImportEmbeddedArtworkUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val nspArtworkExtractor: NspArtworkExtractor,
    private val threeDsArtworkExtractor: ThreeDsArtworkExtractor,
    private val threeDsBannerExtractor: ThreeDsBannerExtractor
) {
    private val importMutex = Mutex()

    /** The single source of truth for ROM formats from which this importer can extract artwork. */
    fun supports(game: Game, romFile: File): Boolean =
        (game.platformId == "switch" && romFile.extension.equals("nsp", ignoreCase = true)) ||
            (game.platformId == "3ds" && romFile.extension.lowercase() in setOf("3ds", "cci", "cia"))

    /**
     * Best effort by design: unavailable keys, malformed packages, and storage failures must not
     * prevent the ROM itself from being added to the library.
     */
    suspend operator fun invoke(game: Game, romFile: File) = withContext(Dispatchers.IO) {
        if (!supports(game, romFile)) return@withContext
        importMutex.withLock {
            val media = runCatching { mediaRepository.getMediaForGame(game.id) }.getOrNull()
            if (media?.boxArtLocalPath == null) {
                runCatching {
                    val artwork = when (game.platformId) {
                        "3ds" -> threeDsArtworkExtractor.extract(romFile)
                        else -> nspArtworkExtractor.extract(romFile)
                    }
                    artwork?.let { mediaRepository.saveEmbeddedBoxArt(game.id, it) }
                }
            }
            // Banner generation is independent of icon import: existing 48x48 icons must not suppress it.
            if (game.platformId == "3ds" &&
                (media?.bannerLocalPath == null || !File(media.bannerLocalPath).isFile) &&
                media?.effectiveScreenshot == null && media?.effectiveVideo == null
            ) {
                runCatching {
                    threeDsBannerExtractor.extract(romFile)?.let {
                        mediaRepository.saveEmbeddedBanner(game.id, it)
                    }
                }
            }
        }
    }
}
