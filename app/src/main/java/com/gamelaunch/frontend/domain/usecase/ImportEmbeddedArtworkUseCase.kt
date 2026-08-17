package com.gamelaunch.frontend.domain.usecase

import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.repository.MediaRepository
import java.io.File
import javax.inject.Inject

/** Imports artwork bundled with a ROM package without involving the network scraper. */
class ImportEmbeddedArtworkUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val nspArtworkExtractor: NspArtworkExtractor
) {
    /** The single source of truth for ROM formats from which this importer can extract artwork. */
    fun supports(game: Game, romFile: File): Boolean =
        game.platformId == "switch" && romFile.extension.equals("nsp", ignoreCase = true)

    /**
     * Best effort by design: unavailable keys, malformed packages, and storage failures must not
     * prevent the ROM itself from being added to the library.
     */
    suspend operator fun invoke(game: Game, romFile: File) {
        if (!supports(game, romFile)) return

        runCatching {
            if (mediaRepository.getMediaForGame(game.id)?.boxArtLocalPath != null) return@runCatching
            nspArtworkExtractor.extract(romFile)?.let { artwork ->
                mediaRepository.saveEmbeddedBoxArt(game.id, artwork)
            }
        }
    }
}
