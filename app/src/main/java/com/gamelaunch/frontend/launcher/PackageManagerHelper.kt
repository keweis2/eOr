package com.gamelaunch.frontend.launcher

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import com.gamelaunch.frontend.domain.model.InstalledApp
import com.gamelaunch.frontend.domain.model.InstalledEmulator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackageManagerHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Both RetroArch variants — com.retroarch.aarch64 ships on Retroid Pocket devices.
    val retroArchPackages = setOf("com.retroarch.aarch64", "org.libretro.retroarch")

    private val knownEmulators = listOf(
        // RetroArch variants
        "com.retroarch.aarch64"              to "RetroArch (AArch64)",          // Retroid Pocket build
        "org.libretro.retroarch"             to "RetroArch",
        // PS1 / PS2 / PSP / Vita
        "com.github.stenzek.duckstation"     to "DuckStation (PS1)",
        "xyz.aethersx2.android"              to "NetherSX2 / AetherSX2 (PS2)",  // shared package id
        "xyz.trizle.nethersx2"               to "NetherSX2 (PS2)",
        "net.play.ptmk.ps2"                  to "AetherSX2 (PS2)",
        "org.ppsspp.ppssppgold"              to "PPSSPP Gold (PSP)",
        "org.ppsspp.ppsspp"                  to "PPSSPP (PSP)",
        "org.vita3k.emulator"                to "Vita3K (PS Vita)",
        // N64
        "org.mupen64plusae.v3.fzurita.pro"   to "Mupen64Plus FZ Pro (N64)",    // Retroid Pocket build
        "org.mupen64plusae.v3.fzurita"       to "Mupen64Plus FZ (N64)",
        // GameCube / Wii / Wii U
        "org.dolphinemu.dolphinemu"          to "Dolphin (GC/Wii)",
        "info.cemu.cemu"                     to "Cemu (Wii U)",                 // lowercase pkg name
        // NDS / 3DS
        "me.magnum.melonds"                  to "melonDS (NDS)",
        "com.drastic.ds"                     to "DraStic (NDS)",
        "org.azahar_emu.azahar"              to "Azahar (3DS)",
        "org.citra.emu"                      to "Citra (3DS)",                  // Retroid Pocket build
        "com.weihuoya.citra"                 to "Citra MMJ (3DS)",
        "org.citra_emu.citra"                to "Citra (3DS)",
        // Switch
        "dev.eden.eden_emulator"             to "Eden (Switch)",                // Retroid Pocket build
        "dev.eden.emulator"                  to "Eden (Switch)",
        "org.sudachi.sudachi_emu"            to "Sudachi (Switch)",             // Retroid Pocket build
        "org.yuzu.yuzu_emu"                  to "Yuzu (Switch)",
        // Dreamcast / Saturn
        "io.recompiled.redream"              to "Redream (Dreamcast)",
        "com.reicast.emulator"               to "Reicast (Dreamcast)",
        "com.flycast.emulator"               to "Flycast (Dreamcast)",
        "org.devmiyax.yabasanshioro2.pro"   to "Yaba Sanshiro 2 Pro (Saturn)", // Retroid Pocket build
        "org.devmiyax.yabasanshioro2"       to "Yaba Sanshiro 2 (Saturn)",
        // Classic handhelds / SNES
        "com.explusalpha.GbaEmu"             to "GBA.emu (GBA)",
        "com.explusalpha.GbcEmu"             to "GBC.emu (GBC/GB)",
        "com.explusalpha.Snes9xEmu"          to "Snes9x EX+ (SNES)",
        // PC / Steam launchers
        "com.valvesoftware.steamlink"         to "Steam Link",
        "com.gamenative.android"              to "GameNative",
        "com.saber.gamehub"                  to "GameHub",
        // Other
        "ru.playsoftware.j2meloader"         to "J2ME Loader"
    )

    // Ordered preference list per platform — first installed entry wins during auto-detect.
    // Each list puts the Retroid Pocket-specific variant first, then the standard variant, then
    // RetroArch as the universal fallback (also as two variants).
    val platformEmulatorPriority: Map<String, List<String>> = mapOf(
        "nes"       to listOf("com.retroarch.aarch64", "org.libretro.retroarch"),
        "snes"      to listOf("com.explusalpha.Snes9xEmu", "com.retroarch.aarch64", "org.libretro.retroarch"),
        "n64"       to listOf("org.mupen64plusae.v3.fzurita.pro", "org.mupen64plusae.v3.fzurita", "com.retroarch.aarch64", "org.libretro.retroarch"),
        "gb"        to listOf("com.explusalpha.GbcEmu", "com.retroarch.aarch64", "org.libretro.retroarch"),
        "gbc"       to listOf("com.explusalpha.GbcEmu", "com.retroarch.aarch64", "org.libretro.retroarch"),
        "gba"       to listOf("com.explusalpha.GbaEmu", "com.retroarch.aarch64", "org.libretro.retroarch"),
        "nds"       to listOf("com.drastic.ds", "me.magnum.melonds", "com.retroarch.aarch64", "org.libretro.retroarch"),
        "3ds"       to listOf("org.azahar_emu.azahar", "org.citra.emu", "com.weihuoya.citra", "org.citra_emu.citra", "com.retroarch.aarch64", "org.libretro.retroarch"),
        "switch"    to listOf("dev.eden.eden_emulator", "dev.eden.emulator", "org.sudachi.sudachi_emu", "org.yuzu.yuzu_emu"),
        "ps1"       to listOf("com.github.stenzek.duckstation", "com.retroarch.aarch64", "org.libretro.retroarch"),
        "ps2"       to listOf("xyz.aethersx2.android", "xyz.trizle.nethersx2", "net.play.ptmk.ps2", "com.retroarch.aarch64", "org.libretro.retroarch"),
        "psp"       to listOf("org.ppsspp.ppssppgold", "org.ppsspp.ppsspp", "com.retroarch.aarch64", "org.libretro.retroarch"),
        "dc"        to listOf("io.recompiled.redream", "com.flycast.emulator", "com.reicast.emulator", "com.retroarch.aarch64", "org.libretro.retroarch"),
        "genesis"   to listOf("com.retroarch.aarch64", "org.libretro.retroarch"),
        "sms"       to listOf("com.retroarch.aarch64", "org.libretro.retroarch"),
        "gg"        to listOf("com.retroarch.aarch64", "org.libretro.retroarch"),
        "saturn"    to listOf("org.devmiyax.yabasanshioro2.pro", "org.devmiyax.yabasanshioro2", "com.retroarch.aarch64", "org.libretro.retroarch"),
        "32x"       to listOf("com.retroarch.aarch64", "org.libretro.retroarch"),
        "atari2600" to listOf("com.retroarch.aarch64", "org.libretro.retroarch"),
        "mame"      to listOf("com.retroarch.aarch64", "org.libretro.retroarch"),
        "fbneo"     to listOf("com.retroarch.aarch64", "org.libretro.retroarch"),
        "neogeo"    to listOf("com.retroarch.aarch64", "org.libretro.retroarch"),
        "ngp"       to listOf("com.retroarch.aarch64", "org.libretro.retroarch"),
        "pcengine"  to listOf("com.retroarch.aarch64", "org.libretro.retroarch"),
        "segacd"    to listOf("com.retroarch.aarch64", "org.libretro.retroarch"),
        "3do"       to listOf("com.retroarch.aarch64", "org.libretro.retroarch"),
        "gc"        to listOf("org.dolphinemu.dolphinemu", "com.retroarch.aarch64", "org.libretro.retroarch"),
        "wii"       to listOf("org.dolphinemu.dolphinemu", "com.retroarch.aarch64", "org.libretro.retroarch"),
        "wiiu"      to listOf("info.cemu.cemu", "com.retroarch.aarch64", "org.libretro.retroarch"),
        "psvita"    to listOf("org.vita3k.emulator"),
        "steam"     to listOf("com.gamenative.android", "com.saber.gamehub", "com.valvesoftware.steamlink")
    )

    fun getInstalledEmulators(): List<InstalledEmulator> {
        val pm = context.packageManager
        return knownEmulators.map { (pkg, name) ->
            val installed = runCatching { pm.getPackageInfo(pkg, 0); true }.getOrDefault(false)
            InstalledEmulator(packageName = pkg, displayName = name, isInstalled = installed)
        }
    }

    fun isPackageInstalled(packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    /** All user-launchable apps (anything with a LAUNCHER activity), minus this launcher itself. */
    fun getInstalledApps(): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { pkg ->
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
                InstalledApp(packageName = pkg, label = label)
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** Loads an app's launcher icon. Returns null if the package can't be resolved. */
    fun getAppIcon(packageName: String): Drawable? = runCatching {
        context.packageManager.getApplicationIcon(packageName)
    }.getOrNull()

    /** Launches an installed app by package name. */
    fun launchApp(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }
}
