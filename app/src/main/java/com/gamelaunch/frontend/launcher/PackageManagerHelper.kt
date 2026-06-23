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
    // All known emulators — shown regardless of install state, marked accordingly.
    // Sourced from Retroid Pocket 2 guide + common Android emulators.
    private val knownEmulators = listOf(
        // Multi-system
        "org.libretro.retroarch"            to "RetroArch",

        // PlayStation 1
        "com.github.stenzek.duckstation"    to "DuckStation (PS1)",

        // PlayStation 2
        "xyz.trizle.nethersx2"              to "NetherSX2 (PS2)",
        "net.play.ptmk.ps2"                 to "AetherSX2 (PS2)",

        // PlayStation Portable
        "org.ppsspp.ppsspp"                 to "PPSSPP (PSP)",
        "org.ppsspp.ppssppgold"             to "PPSSPP Gold (PSP)",

        // PlayStation Vita
        "org.vita3k.emulator"               to "Vita3K (PS Vita)",

        // Nintendo 64
        "org.mupen64plusae.v3.fzurita"      to "Mupen64Plus FZ (N64)",

        // GameCube / Wii
        "org.dolphinemu.dolphinemu"         to "Dolphin (GC/Wii)",

        // Wii U
        "info.cemu.Cemu"                    to "Cemu (Wii U)",

        // Nintendo DS
        "me.magnum.melonds"                 to "melonDS (NDS)",
        "com.drastic.ds"                    to "DraStic (NDS)",

        // Nintendo 3DS
        "org.azahar_emu.azahar"             to "Azahar (3DS)",
        "com.weihuoya.citra"                to "Citra MMJ (3DS)",
        "org.citra_emu.citra"               to "Citra (3DS)",

        // Nintendo Switch
        "org.yuzu.yuzu_emu"                 to "Yuzu (Switch)",
        "dev.eden.emulator"                 to "Eden (Switch)",

        // Sega Dreamcast
        "io.recompiled.redream"             to "Redream (Dreamcast)",
        "com.reicast.emulator"              to "Reicast (Dreamcast)",
        "com.flycast.emulator"              to "Flycast (Dreamcast)",

        // Sega Saturn
        "org.devmiyax.yabasanshioro2"       to "Yaba Sanshiro 2 (Saturn)",

        // Older handhelds
        "com.explusalpha.GbaEmu"            to "GBA.emu (GBA)",
        "com.explusalpha.GbcEmu"            to "GBC.emu (GBC/GB)",
        "com.explusalpha.Snes9xEmu"         to "Snes9x EX+ (SNES)",
        "ru.playsoftware.j2meloader"        to "J2ME Loader"
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
