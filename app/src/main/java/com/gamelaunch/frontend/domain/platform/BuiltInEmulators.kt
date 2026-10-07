package com.gamelaunch.frontend.domain.platform

import com.gamelaunch.frontend.domain.model.KnownEmulator
import com.gamelaunch.frontend.domain.model.LaunchSpec
import com.gamelaunch.frontend.domain.model.RomUriMode

/**
 * Emulator data compiled into the app. This is the offline fallback for [PlatformCatalog] — a
 * downloaded catalog merges on top of it (see [PlatformCatalog.mergedWith]) but never removes it.
 */
object BuiltInEmulators {

    // Both RetroArch variants — com.retroarch.aarch64 ships on Retroid Pocket devices.
    val RETROARCH_PACKAGES = setOf("com.retroarch.aarch64", "org.libretro.retroarch")

    val KNOWN_EMULATORS: List<KnownEmulator> = listOf(
        // RetroArch variants
        KnownEmulator("com.retroarch.aarch64",              "RetroArch (AArch64)"),          // Retroid Pocket build
        KnownEmulator("org.libretro.retroarch",             "RetroArch"),
        // PS1 / PS2 / PS3 / PSP / Vita
        KnownEmulator("com.github.stenzek.duckstation",     "DuckStation (PS1)"),
        KnownEmulator("xyz.aethersx2.android",              "NetherSX2 / AetherSX2 (PS2)"),  // shared package id
        KnownEmulator("xyz.trizle.nethersx2",               "NetherSX2 (PS2)"),
        KnownEmulator("net.play.ptmk.ps2",                  "AetherSX2 (PS2)"),
        KnownEmulator("com.chuckstation.chuckstation3",     "ChuckStation3 (PS3)"),
        KnownEmulator("org.ppsspp.ppssppgold",              "PPSSPP Gold (PSP)"),
        KnownEmulator("org.ppsspp.ppsspp",                  "PPSSPP (PSP)"),
        KnownEmulator("org.vita3k.emulator",                "Vita3K (PS Vita)"),
        // N64
        KnownEmulator("org.mupen64plusae.v3.fzurita.pro",   "Mupen64Plus FZ Pro (N64)"),    // Retroid Pocket build
        KnownEmulator("org.mupen64plusae.v3.fzurita",       "Mupen64Plus FZ (N64)"),
        // GameCube / Wii / Wii U
        KnownEmulator("org.dolphinemu.dolphinemu",          "Dolphin (GC/Wii)"),
        KnownEmulator("info.cemu.cemu",                     "Cemu (Wii U)"),                 // lowercase pkg name
        // NDS / 3DS
        KnownEmulator("me.magnum.melonds",                  "melonDS (NDS)"),
        KnownEmulator("com.dsemu.drastic",                  "DraStic (NDS)"),
        KnownEmulator("org.azahar_emu.azahar",              "Azahar (3DS)"),
        KnownEmulator("org.citra.emu",                      "Citra (3DS)"),                  // Retroid Pocket build
        KnownEmulator("com.weihuoya.citra",                 "Citra MMJ (3DS)"),
        KnownEmulator("org.citra_emu.citra",                "Citra (3DS)"),
        // Switch
        KnownEmulator("dev.eden.eden_emulator",             "Eden (Switch)"),                // Retroid Pocket build
        KnownEmulator("dev.eden.emulator",                  "Eden (Switch)"),
        KnownEmulator("org.sudachi.sudachi_emu",            "Sudachi (Switch)"),             // Retroid Pocket build
        KnownEmulator("org.yuzu.yuzu_emu",                  "Yuzu (Switch)"),
        // Dreamcast / Saturn
        KnownEmulator("io.recompiled.redream",              "Redream (Dreamcast)"),
        KnownEmulator("com.reicast.emulator",               "Reicast (Dreamcast)"),
        KnownEmulator("com.flycast.emulator",               "Flycast (Dreamcast)"),
        KnownEmulator("org.devmiyax.yabasanshioro2.pro",   "Yaba Sanshiro 2 Pro (Saturn)"), // Retroid Pocket build
        KnownEmulator("org.devmiyax.yabasanshioro2",       "Yaba Sanshiro 2 (Saturn)"),
        // Classic handhelds / SNES
        KnownEmulator("com.explusalpha.GbaEmu",             "GBA.emu (GBA)"),
        KnownEmulator("com.explusalpha.GbcEmu",             "GBC.emu (GBC/GB)"),
        KnownEmulator("com.explusalpha.Snes9xEmu",          "Snes9x EX+ (SNES)"),
        // PC / Steam launchers
        KnownEmulator("com.valvesoftware.steamlink",         "Steam Link"),
        KnownEmulator("app.gamenative",                     "GameNative"),
        KnownEmulator("com.saber.gamehub",                  "GameHub"),
        // Other
        KnownEmulator("ru.playsoftware.j2meloader",         "J2ME Loader"),
        KnownEmulator("org.adars.xeo",                      "Xeo (Xbox 360)"),
        // Frontends & Launchers
        KnownEmulator("org.es_de.frontend",                 "ES-DE (Frontend)"),
        KnownEmulator("com.magneticchen.daijishou",         "Daijishō (Frontend)"),
        KnownEmulator("org.pegasus_frontend.pegasus",       "Pegasus (Frontend)"),
        KnownEmulator("org.pegasus_frontend.Pegasus",       "Pegasus (Frontend)"),
        KnownEmulator("com.digdroid.alman.dig",             "Dig (Frontend)"),
        KnownEmulator("com.retrogamer.resetcollection",     "Reset Collection (Frontend)"),
        KnownEmulator("com.unbrokensoftware.launchbox",     "LaunchBox (Frontend)"),
        KnownEmulator("com.lucasfeis.beacon",               "Beacon (Frontend)"),
        KnownEmulator("com.k2.consolelauncher",             "Console Launcher (Frontend)")
    )

    // Ordered preference list per platform — first installed entry wins during auto-detect.
    // Each list puts the Retroid Pocket-specific variant first, then the standard variant, then
    // RetroArch as the universal fallback (also as two variants).
    val PLATFORM_EMULATOR_PRIORITY: Map<String, List<String>> = mapOf(
        "nes"       to listOf("com.retroarch.aarch64", "org.libretro.retroarch"),
        "snes"      to listOf("com.explusalpha.Snes9xEmu", "com.retroarch.aarch64", "org.libretro.retroarch"),
        "n64"       to listOf("org.mupen64plusae.v3.fzurita.pro", "org.mupen64plusae.v3.fzurita", "com.retroarch.aarch64", "org.libretro.retroarch"),
        "gb"        to listOf("com.explusalpha.GbcEmu", "com.retroarch.aarch64", "org.libretro.retroarch"),
        "gbc"       to listOf("com.explusalpha.GbcEmu", "com.retroarch.aarch64", "org.libretro.retroarch"),
        "gba"       to listOf("com.explusalpha.GbaEmu", "com.retroarch.aarch64", "org.libretro.retroarch"),
        "nds"       to listOf("com.dsemu.drastic", "me.magnum.melonds", "com.retroarch.aarch64", "org.libretro.retroarch"),
        "3ds"       to listOf("org.azahar_emu.azahar", "org.citra.emu", "com.weihuoya.citra", "org.citra_emu.citra", "com.retroarch.aarch64", "org.libretro.retroarch"),
        "switch"    to listOf("dev.eden.eden_emulator", "dev.eden.emulator", "org.sudachi.sudachi_emu", "org.yuzu.yuzu_emu"),
        "ps1"       to listOf("com.github.stenzek.duckstation", "com.retroarch.aarch64", "org.libretro.retroarch"),
        "ps2"       to listOf("xyz.aethersx2.android", "xyz.trizle.nethersx2", "net.play.ptmk.ps2", "com.retroarch.aarch64", "org.libretro.retroarch"),
        "ps3"       to listOf("com.chuckstation.chuckstation3"),
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
        "steam"     to listOf("app.gamenative", "com.saber.gamehub", "com.valvesoftware.steamlink"),
        "xbox360"   to listOf("org.adars.xeo")
    )

    /** Verified launch recipes (all checked on a Retroid Pocket 4 unless noted). */
    val LAUNCH_SPECS: Map<String, LaunchSpec> = mapOf(
        // PS1 — DuckStation reads the ROM from a "bootPath" extra, not VIEW data.
        "com.github.stenzek.duckstation" to
            LaunchSpec("com.github.stenzek.duckstation.EmulationActivity",
                       romExtraKey = "bootPath", action = LaunchSpec.ACTION_MAIN),
        // PS2 — NetherSX2 / AetherSX2 share DuckStation's launch convention (same author).
        "xyz.aethersx2.android" to
            LaunchSpec("xyz.aethersx2.android.EmulationActivity",
                       romExtraKey = "bootPath", action = LaunchSpec.ACTION_MAIN),
        "xyz.trizle.nethersx2" to
            LaunchSpec("xyz.aethersx2.android.EmulationActivity",
                       romExtraKey = "bootPath", action = LaunchSpec.ACTION_MAIN),
        "net.play.ptmk.ps2" to
            LaunchSpec("xyz.aethersx2.android.EmulationActivity",
                       romExtraKey = "bootPath", action = LaunchSpec.ACTION_MAIN),
        // PS3 — ChuckStation 3 NativeActivity shim
        "com.chuckstation.chuckstation3" to
            LaunchSpec("com.chuckstation.chuckstation3.MainActivity"),
        // GameCube / Wii — Dolphin boots a game when MainActivity gets an "AutoStartFile" path
        // extra. It must NOT be an ACTION_VIEW intent (its MainActivity rejects VIEW), otherwise
        // it just opens the game-list menu and sits on a loading screen.
        "org.dolphinemu.dolphinemu" to
            LaunchSpec("org.dolphinemu.dolphinemu.ui.main.MainActivity",
                       romExtraKey = "AutoStartFile", action = LaunchSpec.ACTION_MAIN),
        // PSP — PPSSPP reads getData().
        "org.ppsspp.ppsspp"     to LaunchSpec("org.ppsspp.ppsspp.PpssppActivity"),
        "org.ppsspp.ppssppgold" to LaunchSpec("org.ppsspp.ppsspp.PpssppActivity"),
        // NDS — DraStic boots a game when its DraSticActivity receives a "GAMEPATH" string extra; it
        // then forwards to DraSticEmuActivity. Verified on the Anbernic RG DS build (r2.5.2.2a). This
        // is the standard package for both the Play Store and Anbernic builds.
        "com.dsemu.drastic" to
            LaunchSpec("com.dsemu.drastic.DraSticActivity",
                       romExtraKey = "GAMEPATH", action = LaunchSpec.ACTION_MAIN),
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
                                      romExtraKey = "GamePath", action = LaunchSpec.ACTION_MAIN),
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
            action = LaunchSpec.ACTION_VIEW,
            mimeType = "application/octet-stream"
        ),
    )
}
