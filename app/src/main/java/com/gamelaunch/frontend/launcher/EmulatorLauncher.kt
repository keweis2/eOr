package com.gamelaunch.frontend.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.gamelaunch.frontend.domain.model.EmulatorMapping
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.repository.EmulatorRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmulatorLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val emulatorRepository: EmulatorRepository
) {
    suspend fun launch(game: Game): Result<Unit> {
        val mapping = emulatorRepository.getMappingForPlatform(game.platformId)
            ?: return Result.failure(NoEmulatorConfiguredException(game.platformId))

        return if (mapping.isRetroArch) {
            launchRetroArch(game, mapping)
        } else {
            launchStandalone(game, mapping)
        }
    }

    private fun launchRetroArch(game: Game, mapping: EmulatorMapping): Result<Unit> {
        val pkg = mapping.packageName
        // Cores live in the app's internal data directory — construct full path from filename.
        val corePath = mapping.retroArchCore?.let { "/data/user/0/$pkg/cores/$it" }
        val intent = Intent(Intent.ACTION_MAIN).apply {
            setPackage(pkg)
            putExtra("ROM", game.romPath)
            corePath?.let { putExtra("LIBRETRO", it) }
            putExtra("SUBSYSTEM", "")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return tryStartActivity(intent)
    }

    private fun launchStandalone(game: Game, mapping: EmulatorMapping): Result<Unit> {
        val file = File(game.romPath)
        val uri = Uri.fromFile(file)
        val action = mapping.launchAction ?: Intent.ACTION_VIEW
        val intent = Intent(action, uri).apply {
            setPackage(mapping.packageName)
            mapping.intentExtras.forEach { (k, v) -> putExtra(k, v) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return tryStartActivity(intent)
    }

    private fun tryStartActivity(intent: Intent): Result<Unit> = runCatching {
        context.startActivity(intent)
    }
}

class NoEmulatorConfiguredException(platformId: String) :
    Exception("No emulator configured for platform: $platformId")
