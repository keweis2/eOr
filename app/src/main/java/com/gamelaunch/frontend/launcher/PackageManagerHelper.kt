package com.gamelaunch.frontend.launcher

import android.content.Context
import com.gamelaunch.frontend.domain.model.InstalledEmulator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackageManagerHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val knownEmulators = listOf(
        "org.libretro.retroarch"            to "RetroArch",
        "com.github.stenzek.duckstation"    to "DuckStation (PS1)",
        "xyz.trizle.nethersx2"              to "NetherSX2 (PS2)",
        "net.play.ptmk.ps2"                 to "AetherSX2 (PS2)",
        "org.ppsspp.ppsspp"                 to "PPSSPP (PSP)",
        "org.ppsspp.ppssppgold"             to "PPSSPP Gold (PSP)",
        "org.vita3k.emulator"               to "Vita3K (PS Vita)",
        "org.mupen64plusae.v3.fzurita"      to "Mupen64Plus FZ (N64)",
        "org.dolphinemu.dolphinemu"         to "Dolphin (GC/Wii)",
        "info.cemu.Cemu"                    to "Cemu (Wii U)",
        "me.magnum.melonds"                 to "melonDS (NDS)",
        "com.drastic.ds"                    to "DraStic (NDS)",
        "org.azahar_emu.azahar"             to "Azahar (3DS)",
        "com.weihuoya.citra"                to "Citra MMJ (3DS)",
        "org.citra_emu.citra"               to "Citra (3DS)",
        "org.yuzu.yuzu_emu"                 to "Yuzu (Switch)",
        "dev.eden.emulator"                 to "Eden (Switch)",
        "io.recompiled.redream"             to "Redream (Dreamcast)",
        "com.reicast.emulator"              to "Reicast (Dreamcast)",
        "com.flycast.emulator"              to "Flycast (Dreamcast)",
        "org.devmiyax.yabasanshioro2"       to "Yaba Sanshiro 2 (Saturn)",
        "com.explusalpha.GbaEmu"            to "GBA.emu (GBA)",
        "com.explusalpha.GbcEmu"            to "GBC.emu (GBC/GB)",
        "com.explusalpha.Snes9xEmu"         to "Snes9x EX+ (SNES)",
        "ru.playsoftware.j2meloader"        to "J2ME Loader"
    )

    // Ordered preference list per platform — first installed entry wins during auto-detect.
    // Standalone emulators ranked above RetroArch where they offer better compatibility.
    val platformEmulatorPriority: Map<String, List<String>> = mapOf(
        "nes"       to listOf("org.libretro.retroarch"),
        "snes"      to listOf("com.explusalpha.Snes9xEmu", "org.libretro.retroarch"),
        "n64"       to listOf("org.mupen64plusae.v3.fzurita", "org.libretro.retroarch"),
        "gb"        to listOf("com.explusalpha.GbcEmu", "org.libretro.retroarch"),
        "gbc"       to listOf("com.explusalpha.GbcEmu", "org.libretro.retroarch"),
        "gba"       to listOf("com.explusalpha.GbaEmu", "org.libretro.retroarch"),
        "nds"       to listOf("com.drastic.ds", "me.magnum.melonds", "org.libretro.retroarch"),
        "3ds"       to listOf("org.azahar_emu.azahar", "com.weihuoya.citra", "org.citra_emu.citra", "org.libretro.retroarch"),
        "switch"    to listOf("dev.eden.emulator", "org.yuzu.yuzu_emu"),
        "ps1"       to listOf("com.github.stenzek.duckstation", "org.libretro.retroarch"),
        "ps2"       to listOf("xyz.trizle.nethersx2", "net.play.ptmk.ps2", "org.libretro.retroarch"),
        "psp"       to listOf("org.ppsspp.ppssppgold", "org.ppsspp.ppsspp", "org.libretro.retroarch"),
        "dc"        to listOf("io.recompiled.redream", "com.flycast.emulator", "com.reicast.emulator", "org.libretro.retroarch"),
        "genesis"   to listOf("org.libretro.retroarch"),
        "sms"       to listOf("org.libretro.retroarch"),
        "gg"        to listOf("org.libretro.retroarch"),
        "saturn"    to listOf("org.devmiyax.yabasanshioro2", "org.libretro.retroarch"),
        "32x"       to listOf("org.libretro.retroarch"),
        "atari2600" to listOf("org.libretro.retroarch"),
        "mame"      to listOf("org.libretro.retroarch")
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
}
