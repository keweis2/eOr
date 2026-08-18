package com.gamelaunch.frontend.launcher

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.FileProvider
import com.gamelaunch.frontend.BuildConfig
import com.gamelaunch.frontend.domain.model.EmulatorMapping
import com.gamelaunch.frontend.domain.model.Game
import com.gamelaunch.frontend.domain.repository.EmulatorRepository
import com.gamelaunch.frontend.domain.repository.SettingsRepository
import com.gamelaunch.frontend.platform.display.DualScreenManager
import com.gamelaunch.frontend.ui.dualscreen.GameSessionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmulatorLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val emulatorRepository: EmulatorRepository,
    private val packageManagerHelper: PackageManagerHelper,
    private val settingsRepository: SettingsRepository,
    private val gameSessionState: GameSessionState
) {
    suspend fun launch(game: Game): Result<Unit> {
        // Dual-screen: single-screen games open on the top panel when configured (see resolver).
        val options = resolveLaunchOptions(game)

        // Android game: romPath is "package:<pkg>" — launch the app directly.
        val result = if (game.romPath.startsWith("package:")) {
            val pkg = game.romPath.removePrefix("package:")
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent == null) {
                Result.failure(Exception("App not installed: $pkg"))
            } else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                tryStartActivity(intent, options)
            }
        } else {
            var mapping = emulatorRepository.getMappingForPlatform(game.platformId)
            // If the saved package is no longer installed (e.g. stale DB after package name fix),
            // run auto-detect once to update the mapping before trying to launch.
            if (mapping != null && !packageManagerHelper.isPackageInstalled(mapping.packageName)) {
                emulatorRepository.autoDetectAndAssign()
                mapping = emulatorRepository.getMappingForPlatform(game.platformId)
            }
            when {
                mapping == null -> Result.failure(NoEmulatorConfiguredException(game.platformId))
                game.romPath.startsWith("steam:") -> launchSteamGame(game, mapping, options)
                mapping.isRetroArch -> launchRetroArch(game, mapping, options)
                else -> launchStandalone(game, mapping, options)
            }
        }

        // When we placed the game on the top panel, hide the artwork overlay so the game is visible
        // (eOr stays resumed on the bottom, so the overlay isn't torn down by onStop). Restored when
        // the user returns — see MainActivity.onWindowFocusChanged.
        if (options != null && result.isSuccess) gameSessionState.begin()
        return result
    }

    /**
     * ActivityOptions that place a single-screen game on the top panel, or null to launch on the
     * default display. Only kicks in when dual-screen is enabled, the "launch on top" setting is on,
     * and a second screen is actually present. Dual-screen games (NDS/3DS) render both panels
     * themselves, so they always launch on the default display.
     */
    private suspend fun resolveLaunchOptions(game: Game): Bundle? {
        if (!settingsRepository.dualScreenEnabled.first()) return null
        if (!settingsRepository.gameLaunchOnTop.first()) return null
        if (game.platformId in DUAL_SCREEN_PLATFORMS) return null
        val swap = settingsRepository.dualScreenSwap.first()
        val displayId = DualScreenManager.artworkDisplayId(context, swap) ?: return null
        return ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle()
    }

    private fun launchRetroArch(game: Game, mapping: EmulatorMapping, options: Bundle?): Result<Unit> {
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
        return tryStartActivity(intent, options).recoverCatching {
            val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                ?: throw it
            launch.putExtra("ROM", game.romPath)
            corePath?.let { c -> launch.putExtra("LIBRETRO", c) }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch, options)
        }
    }

    /**
     * Steam / PC games (romPath "steam:<appid>") are booted by their configured frontend. GameNative
     * accepts a direct-launch intent (verified against app.gamenative): action LAUNCH_GAME with the
     * numeric app_id and a game_source. Other frontends (GameHub, Winlator, Steam Link) have no known
     * per-game intent, so we open the app and let the user pick — better than a hard failure.
     */
    private fun launchSteamGame(game: Game, mapping: EmulatorMapping, options: Bundle?): Result<Unit> {
        val pkg = mapping.packageName
        // romPath is "steam:<appid>" (STEAM) or "steam:<SOURCE>:<appid>" (Epic/GOG/Amazon/custom).
        val parts = game.romPath.removePrefix("steam:").split(":")
        val appId = parts.last().toIntOrNull()
        val source = if (parts.size >= 2) parts.first() else "STEAM"
        if (pkg == GAMENATIVE_PACKAGE && appId != null) {
            val intent = Intent(GAMENATIVE_LAUNCH_ACTION).apply {
                setPackage(pkg)
                putExtra(GAMENATIVE_EXTRA_APP_ID, appId)
                putExtra(GAMENATIVE_EXTRA_GAME_SOURCE, source)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // If GameNative can't handle the direct launch, fall back to opening its library.
            return tryStartActivity(intent, options)
                .recoverCatching { context.startActivity(openAppIntent(pkg) ?: throw it, options) }
        }
        // No direct-launch recipe for this frontend — open it so the user can start the game.
        return tryStartActivity(
            openAppIntent(pkg) ?: return Result.failure(Exception("App not installed: $pkg")),
            options
        )
    }

    private fun launchStandalone(game: Game, mapping: EmulatorMapping, options: Bundle?): Result<Unit> {
        val pkg  = mapping.packageName
        val spec = launchSpecs[pkg]
        val file = File(game.romPath)

        // Preferred path: each known emulator has a verified launch recipe (explicit activity +
        // either a ROM path extra or a file:// data URI).
        if (spec != null) {
            // Build inside the runCatching so a failure to construct the intent (e.g. FileProvider
            // can't represent the ROM path) falls back like a failed launch instead of crashing —
            // every other launch path is already guarded this way. If the explicit activity is
            // unavailable, try a package-targeted VIEW intent before opening the emulator's game list.
            return runCatching {
                val intent = Intent(spec.action).apply {
                    setClassName(pkg, spec.activity)
                    if (spec.romExtraKey != null) {
                        // ROM handed over as a plain path string — no Uri, nothing to expose.
                        putExtra(spec.romExtraKey, game.romPath)
                    } else {
                        setDataAndType(romUriFor(spec.romUriMode, file), spec.mimeType)
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    grantRomReadPermissionIfNeeded()
                    mapping.intentExtras.forEach { (k, v) -> putExtra(k, v) }
                }
                context.startActivity(intent, options)
            }
                .recoverCatching {
                    context.startActivity(fallbackViewIntent(pkg, file, mapping, spec.romUriMode), options)
                }
                .recoverCatching { context.startActivity(openAppIntent(pkg) ?: throw it, options) }
        }

        // Unknown emulator: best-effort package-targeted VIEW, else open its game list.
        return tryStartActivity(fallbackViewIntent(pkg, file, mapping), options)
            .recoverCatching { context.startActivity(openAppIntent(pkg) ?: throw it, options) }
    }

    /** Last-resort: open the emulator's own UI (its game list) when it can't be booted directly. */
    private fun openAppIntent(pkg: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Best-effort direct-ROM intent when no verified activity recipe can be used. */
    private fun fallbackViewIntent(
        pkg: String,
        file: File,
        mapping: EmulatorMapping,
        romUriMode: RomUriMode = RomUriMode.FILE
    ): Intent =
        Intent(mapping.launchAction ?: Intent.ACTION_VIEW, romUriFor(romUriMode, file)).apply {
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            grantRomReadPermissionIfNeeded()
            mapping.intentExtras.forEach { (k, v) -> putExtra(k, v) }
        }

    /**
     * Content URIs let emulators read eOr's files across Android app sandboxes. The corresponding
     * intent receives a temporary read grant in [grantRomReadPermissionIfNeeded].
     */
    private fun romUriFor(mode: RomUriMode, file: File): Uri =
        if (mode == RomUriMode.CONTENT) {
            FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        } else {
            Uri.fromFile(file)
        }

    private fun Intent.grantRomReadPermissionIfNeeded() {
        if (data?.scheme == "content") addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun tryStartActivity(intent: Intent, options: Bundle? = null): Result<Unit> = runCatching {
        context.startActivity(intent, options)
    }

    /** How to hand a ROM to a specific standalone emulator (all verified on a Retroid Pocket 4). */
    private data class LaunchSpec(
        val activity: String,            // fully-qualified activity to launch explicitly
        val romExtraKey: String? = null, // pass ROM path via this String extra; else as file:// data
        val action: String = Intent.ACTION_VIEW,
        val mimeType: String? = null,
        val romUriMode: RomUriMode = RomUriMode.FILE
    )

    /** How the ROM is supplied: its raw file URI, or a FileProvider URI with temporary read access. */
    private enum class RomUriMode { FILE, CONTENT }

    private companion object {
        const val GAMENATIVE_PACKAGE = "app.gamenative"
        const val GAMENATIVE_LAUNCH_ACTION = "app.gamenative.LAUNCH_GAME"
        const val GAMENATIVE_EXTRA_APP_ID = "app_id"
        const val GAMENATIVE_EXTRA_GAME_SOURCE = "game_source"
    }

    // Platforms whose emulator renders both panels itself (Nintendo DS, 3DS) — never force these
    // onto a single display; they use the whole dual-screen device.
    private val DUAL_SCREEN_PLATFORMS = setOf("nds", "3ds")

    private val launchSpecs: Map<String, LaunchSpec> = mapOf(
        // PS1 — DuckStation reads the ROM from a "bootPath" extra, not VIEW data.
        "com.github.stenzek.duckstation" to
            LaunchSpec("com.github.stenzek.duckstation.EmulationActivity",
                       romExtraKey = "bootPath", action = Intent.ACTION_MAIN),
        // PS2 — NetherSX2 / AetherSX2 share DuckStation's launch convention (same author).
        "xyz.aethersx2.android" to
            LaunchSpec("xyz.aethersx2.android.EmulationActivity",
                       romExtraKey = "bootPath", action = Intent.ACTION_MAIN),
        "xyz.trizle.nethersx2" to
            LaunchSpec("xyz.aethersx2.android.EmulationActivity",
                       romExtraKey = "bootPath", action = Intent.ACTION_MAIN),
        "net.play.ptmk.ps2" to
            LaunchSpec("xyz.aethersx2.android.EmulationActivity",
                       romExtraKey = "bootPath", action = Intent.ACTION_MAIN),
        // PS3 — ChuckStation 3 NativeActivity shim
        "com.chuckstation.chuckstation3" to
            LaunchSpec("com.chuckstation.chuckstation3.MainActivity"),
        // GameCube / Wii — Dolphin boots a game when MainActivity gets an "AutoStartFile" path
        // extra. It must NOT be an ACTION_VIEW intent (its MainActivity rejects VIEW), otherwise
        // it just opens the game-list menu and sits on a loading screen.
        "org.dolphinemu.dolphinemu" to
            LaunchSpec("org.dolphinemu.dolphinemu.ui.main.MainActivity",
                       romExtraKey = "AutoStartFile", action = Intent.ACTION_MAIN),
        // PSP — PPSSPP reads getData().
        "org.ppsspp.ppsspp"     to LaunchSpec("org.ppsspp.ppsspp.PpssppActivity"),
        "org.ppsspp.ppssppgold" to LaunchSpec("org.ppsspp.ppsspp.PpssppActivity"),
        // NDS — DraStic boots a game when its DraSticActivity receives a "GAMEPATH" string extra; it
        // then forwards to DraSticEmuActivity. Verified on the Anbernic RG DS build (r2.5.2.2a). This
        // is the standard package for both the Play Store and Anbernic builds.
        "com.dsemu.drastic" to
            LaunchSpec("com.dsemu.drastic.DraSticActivity",
                       romExtraKey = "GAMEPATH", action = Intent.ACTION_MAIN),
        // NDS — melonDS's EmulatorActivity crashes (ConcurrentModificationException) when launched
        // cold from outside, and the warm-then-launch workaround is blocked by Android's
        // background-activity-start policy. Open its ROM list instead so it never crashes; the
        // user taps the game there. (DraStic or a RetroArch DS core give true direct-boot.)
        "me.magnum.melonds" to LaunchSpec("me.magnum.melonds.ui.romlist.RomListActivity"),
        // N64 — Mupen64Plus FZ splash screen forwards to GameActivity.
        "org.mupen64plusae.v3.fzurita"     to LaunchSpec("paulscode.android.mupen64plusae.SplashActivity"),
        "org.mupen64plusae.v3.fzurita.pro" to LaunchSpec("paulscode.android.mupen64plusae.SplashActivity"),
        // Dreamcast — Redream only accepts a file:// scheme.
        "io.recompiled.redream" to LaunchSpec("io.recompiled.redream.MainActivity"),
        // Saturn — Yaba Sanshiro game activity reads getData().
        "org.devmiyax.yabasanshioro2"     to LaunchSpec("org.uoyabause.android.Yabause"),
        "org.devmiyax.yabasanshioro2.pro" to LaunchSpec("org.uoyabause.android.Yabause"),
        // 3DS — Citra (MMJ) reads the ROM from a "GamePath" extra.
        "org.citra.emu" to LaunchSpec("org.citra.emu.ui.EmulationActivity",
                                      romExtraKey = "GamePath", action = Intent.ACTION_MAIN),
        // Switch — Yuzu-derived emulators expose an EmulationActivity that reads getData().
        // Eden needs a FileProvider content URI and temporary read permission for eOr's ROM file.
        "dev.eden.eden_emulator"  to LaunchSpec(
            "org.yuzu.yuzu_emu.activities.EmulationActivity", romUriMode = RomUriMode.CONTENT
        ),
        "dev.eden.emulator"       to LaunchSpec(
            "org.yuzu.yuzu_emu.activities.EmulationActivity", romUriMode = RomUriMode.CONTENT
        ),
        "org.yuzu.yuzu_emu"       to LaunchSpec("org.yuzu.yuzu_emu.activities.EmulationActivity"),
        "org.sudachi.sudachi_emu" to LaunchSpec("org.sudachi.sudachi_emu.activities.EmulationActivity"),
        // Xbox 360 — Xeo accepts VIEW intent with scheme="file" and mimeType="application/octet-stream"
        "org.adars.xeo"           to LaunchSpec(
            activity = "org.adars.xeo.ui.MainActivity",
            action = Intent.ACTION_VIEW,
            mimeType = "application/octet-stream"
        ),
    )
}

class NoEmulatorConfiguredException(platformId: String) :
    Exception("No emulator configured for platform: $platformId")
