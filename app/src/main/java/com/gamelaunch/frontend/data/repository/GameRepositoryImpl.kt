package com.gamelaunch.frontend.data.repository

import com.gamelaunch.frontend.data.db.dao.GameDao
import com.gamelaunch.frontend.data.db.entity.GameEntity
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.repository.GameRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameRepositoryImpl @Inject constructor(
    private val gameDao: GameDao
) : GameRepository {

    override fun getAllGames(): Flow<List<Game>> =
        gameDao.getAllGames().map { it.map(GameEntity::toDomain) }

    override fun getGamesByPlatform(platformId: String): Flow<List<Game>> =
        gameDao.getGamesByPlatform(platformId).map { it.map(GameEntity::toDomain) }

    override suspend fun getGameById(id: Long): Game? =
        gameDao.getGameById(id)?.toDomain()

    override suspend fun getUnscrapedGames(): List<Game> =
        gameDao.getUnscrapedGames().map(GameEntity::toDomain)

    override suspend fun getGamesNeedingScrape(
        needMeta: Boolean,
        needBox: Boolean,
        needShot: Boolean,
        needWheel: Boolean,
        needVideo: Boolean
    ): List<Game> =
        gameDao.getGamesNeedingScrape(
            if (needMeta) 1 else 0,
            if (needBox) 1 else 0,
            if (needShot) 1 else 0,
            if (needWheel) 1 else 0,
            if (needVideo) 1 else 0
        ).map(GameEntity::toDomain)

    override fun getFavorites(): Flow<List<Game>> =
        gameDao.getFavorites().map { it.map(GameEntity::toDomain) }

    override fun getRecentlyPlayed(limit: Int): Flow<List<Game>> =
        gameDao.getRecentlyPlayed(limit).map { it.map(GameEntity::toDomain) }

    override fun getDistinctPlatformIds(): Flow<List<String>> =
        gameDao.getDistinctPlatformIds()

    override fun getPlatformCounts(): Flow<Map<String, Int>> =
        gameDao.getPlatformCounts().map { rows -> rows.associate { it.platformId to it.count } }

    override suspend fun insertGame(game: Game): Long =
        gameDao.insertGame(game.toEntity())

    override suspend fun insertGames(games: List<Game>) {
        gameDao.insertGames(games.map(Game::toEntity))
    }

    override suspend fun updateGame(game: Game) {
        gameDao.updateGame(game.toEntity())
    }

    override suspend fun updateScrapedMetadata(
        gameId: Long,
        scraperGameId: Long?,
        title: String,
        description: String?,
        genre: String?,
        releaseYear: Int?,
        rating: Float?
    ) {
        gameDao.updateTitle(gameId, title)
        gameDao.updateScrapedMetadata(gameId, scraperGameId, description, genre, releaseYear, rating)
    }

    override suspend fun markScraped(gameId: Long, title: String) {
        gameDao.updateTitle(gameId, title)
    }

    override suspend fun setFavorite(gameId: Long, isFavorite: Boolean) {
        gameDao.setFavorite(gameId, isFavorite)
    }

    override suspend fun recordPlay(gameId: Long) {
        gameDao.recordPlay(gameId, System.currentTimeMillis())
    }

    override suspend fun deleteGamesNotInPaths(validPaths: List<String>): Int =
        gameDao.deleteGamesNotInPaths(validPaths)

    override suspend fun deleteAllNonAndroidGames(): Int =
        gameDao.deleteAllNonAndroidGames()

    override suspend fun deleteAndroidGamesNotIn(validPaths: List<String>): Int =
        gameDao.deleteAndroidGamesNotIn(validPaths)

    override suspend fun deleteAllAndroidGames(): Int =
        gameDao.deleteAllAndroidGames()

    override suspend fun getTotalCount(): Int = gameDao.getTotalCount()
}

private fun GameEntity.toDomain() = Game(
    id = id,
    title = title,
    romPath = romPath,
    romFilename = romFilename,
    platformId = platformId,
    md5 = md5,
    crc = crc,
    scraperGameId = scraperGameId,
    description = description,
    genre = genre,
    releaseYear = releaseYear,
    rating = rating,
    isFavorite = isFavorite,
    lastPlayedMs = lastPlayedMs,
    playCount = playCount,
    dateAdded = dateAdded,
    isScraped = isScraped
)

private fun Game.toEntity() = GameEntity(
    id = id,
    title = title,
    romPath = romPath,
    romFilename = romFilename,
    platformId = platformId,
    md5 = md5,
    crc = crc,
    scraperGameId = scraperGameId,
    description = description,
    genre = genre,
    releaseYear = releaseYear,
    rating = rating,
    isFavorite = isFavorite,
    lastPlayedMs = lastPlayedMs,
    playCount = playCount,
    dateAdded = dateAdded,
    isScraped = isScraped
)
