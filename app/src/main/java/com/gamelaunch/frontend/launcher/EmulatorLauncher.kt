package com.gamelaunch.frontend.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
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
    private val emulatorRepository: EmulatorRepository,
    private val packageManagerHelper: PackageManagerHelper
) {
    suspend fun launch(game: Game): Result<Unit> {
        var mapping = emulatorRepository.getMappingForPlatform(game.platformId)
            ?: return Result.failure(NoEmulatorConfiguredException(game.platformId))

        // If the saved package is no longer installed (e.g. stale DB after package name fix),
        // run auto-detect once to update the mapping before trying to launch.
        if (!packageManagerHelper.isPackageInstalled(mapping.packageName)) {
            emulatorRepository.autoDetectAndAssign()
            mapping = emulatorRepository.getMappingForPlatform(game.platformId)
                ?: return Result.failure(NoEmulatorConfiguredException(game.platformId))
        }

        return if (mapping.isRetroArch) {
            launchRetroArch(game, mapping)
        } else {
            launchStandalone(game, mapping)
        }
    }

    private fun launchRetroArch(game: Game, mapping: EmulatorMapping): Result<Unit> {
        val pkg = mapping.packageName
        // RetroArch's content-loading activity. Launching MainMenuActivity (the package's
        // default launch intent) only opens the menu; RetroActivityFuture with ROM/LIBRETRO/
        // CONFIGFILE extras is what actually boots a game directly.
        // Android RetroArch core files carry an "_android" suffix (e.g.
        // nestopia_libretro_android.so), so the canonical core name from PlatformDefinitions
        // must be adapted before building the path.
        val corePath = mapping.retroArchCore?.let { name ->
            val androidName = if (name.endsWith("_android.so")) name
                              else name.removeSuffix(".so") + "_android.so"
            "/data/user/0/$pkg/cores/$androidName"
        }
        val configFile = "/storage/emulated/0/Android/data/$pkg/files/retroarch.cfg"
        val intent = Intent().apply {
            setClassName(pkg, "com.retroarch.browser.retroactivity.RetroActivityFuture")
            putExtra("ROM", game.romPath)
            corePath?.let { putExtra("LIBRETRO", it) }
            putExtra("CONFIGFILE", configFile)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        // Fall back to the plain launch intent (opens the menu) if the content activity
        // can't be started for some reason — better than a hard failure.
        return tryStartActivity(intent).recoverCatching {
            val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                ?: throw it
            launch.putExtra("ROM", game.romPath)
            corePath?.let { c -> launch.putExtra("LIBRETRO", c) }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
        }
    }

    private fun launchStandalone(game: Game, mapping: EmulatorMapping): Result<Unit> {
        val file = File(game.romPath)
        // On Android 7+ (targetSdk >= 24), file:// URIs passed to other apps throw
        // FileUriExposedException. Use FileProvider to hand out a content:// URI instead.
        val uri = runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }.getOrElse {
            Uri.fromFile(file)
        }
        val action = mapping.launchAction ?: Intent.ACTION_VIEW
        val intent = Intent(action, uri).apply {
            setPackage(mapping.packageName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            mapping.intentExtras.forEach { (k, v) -> putExtra(k, v) }
        }
        return tryStartActivity(intent)
    }

    private fun tryStartActivity(intent: Intent): Result<Unit> = runCatching {
        context.startActivity(intent)
    }
}

class NoEmulatorConfiguredException(platformId: String) :
    Exception("No emulator configured for platform: $platformId")
