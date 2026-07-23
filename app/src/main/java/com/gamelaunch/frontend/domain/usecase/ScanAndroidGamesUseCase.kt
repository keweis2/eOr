package com.gamelaunch.frontend.domain.usecase

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class ScanAndroidGamesUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameRepository: GameRepository,
    private val settingsRepository: SettingsRepository,
    private val packageManagerHelper: com.gamelaunch.frontend.launcher.PackageManagerHelper
) {
    // Package prefixes that belong to the OS or this launcher — skip them.
    private val systemPrefixes = setOf(
        "android", "com.android", "com.google.android", "com.google.ar",
        "com.samsung", "com.miui", "com.huawei", "com.gamelaunch.frontend"
    )

    operator fun invoke(): Flow<ScanProgress> = flow {
        val pm = context.packageManager

        // Apps the user manually removed from the library (stored as "package:<pkg>").
        val excludedPaths = settingsRepository.excludedPaths.first()

        // Apps that declare the GAME launcher category (older / explicit signal).
        val gameIntent = Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.GAME")
        val categoryGamePkgs = pm.queryIntentActivities(gameIntent, 0)
            .map { it.activityInfo.packageName }
            .toSet()

        // Every launchable user app — most modern games (CoD, RDR, KOTOR, Stardew, etc.) don't
        // declare the GAME launcher category, so we instead classify by appCategory == GAME, which
        // the Play Store sets for games, plus the legacy game flag and the GAME-category set above.
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchablePkgs = pm.queryIntentActivities(launcherIntent, 0)
            .map { it.activityInfo.packageName }

        val packages = (categoryGamePkgs + launchablePkgs).distinct().filter { pkg ->
            // Respect manual removals so a rescan doesn't bring a hidden app back.
            if ("package:$pkg" in excludedPaths) return@filter false
            if (systemPrefixes.any { pkg == it || pkg.startsWith("$it.") }) return@filter false
            // Emulators/launchers are categorised as games too — keep them out of the library.
            if (pkg in packageManagerHelper.emulatorPackages) return@filter false
            runCatching {
                val ai = pm.getApplicationInfo(pkg, 0)
                // Skip pre-installed system apps (unless the user updated one from the store).
                val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                               (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                if (isSystem) return@runCatching false
                // Must be launchable so we can actually open it.
                if (pm.getLaunchIntentForPackage(pkg) == null) return@runCatching false

                pkg in categoryGamePkgs ||
                    ai.category == ApplicationInfo.CATEGORY_GAME ||
                    @Suppress("DEPRECATION") (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            }.getOrDefault(false)
        }

        emit(ScanProgress(0, packages.size, "Scanning installed games…"))

        val validPaths = mutableListOf<String>()
        var added = 0

        packages.forEachIndexed { index, pkg ->
            val label = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)

            emit(ScanProgress(index, packages.size, label, added))

            val romPath = "package:$pkg"
            validPaths.add(romPath)

            val game = Game(
                title       = label,
                romPath     = romPath,
                romFilename = pkg,
                platformId  = "android"
            )
            val id = gameRepository.insertGame(game)
            if (id > 0) added++
        }

        // Remove android-platform games whose packages are no longer installed.
        val installedPaths = packageManagerHelper.getInstalledApps().map { "package:${it.packageName}" }
        if (installedPaths.isEmpty()) {
            gameRepository.deleteAllAndroidGames()
        } else {
            gameRepository.deleteAndroidGamesNotIn(installedPaths)
        }

        emit(ScanProgress(packages.size, packages.size, added = added))
    }.flowOn(Dispatchers.IO)
}
