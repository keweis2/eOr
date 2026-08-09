package com.gamelaunch.frontend.domain.usecase

import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.repository.FriendRepository
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.lockedmode.LockedModeRepository
import com.gamelaunch.frontend.launcher.EmulatorLauncher
import javax.inject.Inject

class LaunchGameUseCase @Inject constructor(
    private val emulatorLauncher: EmulatorLauncher,
    private val gameRepository: GameRepository,
    private val friendRepository: FriendRepository,
    private val lockedModeRepository: LockedModeRepository
) {
    suspend operator fun invoke(game: Game): Result<Unit> {
        if (lockedModeRepository.isLocked()) {
            val persisted = gameRepository.getGameById(game.id)
            if (persisted == null || !persisted.isAvailableInLockedMode) {
                return Result.failure(IllegalStateException("This game is unavailable in Locked Mode."))
            }
        }
        val result = emulatorLauncher.launch(game)
        if (result.isSuccess) {
            gameRepository.recordPlay(game.id)
            // Refresh the profile we share with friends (no-op when Friends is disabled).
            runCatching { friendRepository.publishMyProfile() }
        }
        return result
    }
}
