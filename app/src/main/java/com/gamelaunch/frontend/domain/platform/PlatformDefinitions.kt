package com.gamelaunch.frontend.domain.platform

import com.gamelaunch.frontend.domain.model.Platform

object PlatformDefinitions {

    val ALL: List<Platform> = listOf(
        Platform(
            id = "nes", displayName = "Nintendo NES", scraperSystemId = 3,
            extensions = listOf(".nes"),
            folderNames = listOf("NES", "nes", "Nintendo Entertainment System", "Famicom"),
            defaultCoreForRetroArch = "nestopia_libretro.so"
        ),
        Platform(
            id = "snes", displayName = "Super Nintendo", scraperSystemId = 4,
            extensions = listOf(".sfc", ".smc", ".snes", ".fig"),
            folderNames = listOf("SNES", "snes", "Super Nintendo", "Super Famicom", "SFC"),
            defaultCoreForRetroArch = "snes9x_libretro.so"
        ),
        Platform(
            id = "n64", displayName = "Nintendo 64", scraperSystemId = 14,
            extensions = listOf(".n64", ".z64", ".v64"),
            folderNames = listOf("N64", "n64", "Nintendo 64", "Nintendo64"),
            defaultCoreForRetroArch = "mupen64plus_next_libretro.so"
        ),
        Platform(
            id = "gb", displayName = "Game Boy", scraperSystemId = 9,
            extensions = listOf(".gb"),
            folderNames = listOf("GB", "gb", "GameBoy", "Game Boy", "Gameboy"),
            defaultCoreForRetroArch = "gambatte_libretro.so"
        ),
        Platform(
            id = "gbc", displayName = "Game Boy Color", scraperSystemId = 10,
            extensions = listOf(".gbc"),
            folderNames = listOf("GBC", "gbc", "GameBoy Color", "Game Boy Color"),
            defaultCoreForRetroArch = "gambatte_libretro.so"
        ),
        Platform(
            id = "gba", displayName = "Game Boy Advance", scraperSystemId = 12,
            extensions = listOf(".gba"),
            folderNames = listOf("GBA", "gba", "GameBoy Advance", "Game Boy Advance"),
            defaultCoreForRetroArch = "mgba_libretro.so"
        ),
        Platform(
            id = "nds", displayName = "Nintendo DS", scraperSystemId = 15,
            extensions = listOf(".nds"),
            folderNames = listOf("NDS", "nds", "Nintendo DS", "DS"),
            defaultEmulatorPackage = "com.drastic.ds",
            defaultCoreForRetroArch = "desmume2015_libretro.so"
        ),
        Platform(
            id = "ps1", displayName = "PlayStation", scraperSystemId = 57,
            extensions = listOf(".bin", ".cue", ".iso", ".pbp", ".chd", ".img"),
            folderNames = listOf("PS1", "ps1", "PSX", "psx", "PlayStation", "PS"),
            defaultEmulatorPackage = "com.github.stenzek.duckstation",
            defaultCoreForRetroArch = "pcsx_rearmed_libretro.so"
        ),
        Platform(
            id = "ps2", displayName = "PlayStation 2", scraperSystemId = 58,
            extensions = listOf(".iso", ".chd", ".cso"),
            folderNames = listOf("PS2", "ps2", "PlayStation 2", "PlayStation2"),
            defaultCoreForRetroArch = "pcsx2_libretro.so"
        ),
        Platform(
            id = "psp", displayName = "PSP", scraperSystemId = 61,
            extensions = listOf(".iso", ".cso", ".pbp"),
            folderNames = listOf("PSP", "psp"),
            defaultEmulatorPackage = "org.ppsspp.ppsspp",
            defaultCoreForRetroArch = "ppsspp_libretro.so"
        ),
        Platform(
            id = "dc", displayName = "Dreamcast", scraperSystemId = 23,
            extensions = listOf(".cdi", ".gdi", ".chd", ".cue"),
            folderNames = listOf("DC", "Dreamcast", "dreamcast", "Sega Dreamcast"),
            defaultEmulatorPackage = "com.flycast.emulator",
            defaultCoreForRetroArch = "flycast_libretro.so"
        ),
        Platform(
            id = "genesis", displayName = "Sega Genesis / Mega Drive", scraperSystemId = 1,
            extensions = listOf(".md", ".gen", ".smd"),
            folderNames = listOf("Genesis", "MD", "MegaDrive", "Mega Drive", "genesis", "Sega Genesis"),
            defaultCoreForRetroArch = "genesis_plus_gx_libretro.so"
        ),
        Platform(
            id = "sms", displayName = "Sega Master System", scraperSystemId = 2,
            extensions = listOf(".sms"),
            folderNames = listOf("SMS", "sms", "mastersystem", "Master System", "Sega Master System"),
            defaultCoreForRetroArch = "genesis_plus_gx_libretro.so"
        ),
        Platform(
            id = "gg", displayName = "Game Gear", scraperSystemId = 21,
            extensions = listOf(".gg"),
            folderNames = listOf("GG", "GameGear", "Game Gear", "game gear"),
            defaultCoreForRetroArch = "genesis_plus_gx_libretro.so"
        ),
        Platform(
            id = "saturn", displayName = "Sega Saturn", scraperSystemId = 22,
            extensions = listOf(".cue", ".iso", ".chd", ".ccd"),
            folderNames = listOf("Saturn", "Sega Saturn", "saturn"),
            defaultCoreForRetroArch = "mednafen_saturn_libretro.so"
        ),
        Platform(
            id = "32x", displayName = "Sega 32X", scraperSystemId = 19,
            extensions = listOf(".32x"),
            folderNames = listOf("32X", "Sega 32X", "32x", "sega32x"),
            defaultCoreForRetroArch = "picodrive_libretro.so"
        ),
        Platform(
            id = "3ds", displayName = "Nintendo 3DS", scraperSystemId = 17,
            extensions = listOf(".3ds", ".cia", ".cci"),
            folderNames = listOf("3DS", "3ds", "n3ds", "Nintendo 3DS"),
            defaultCoreForRetroArch = "citra_libretro.so"
        ),
        Platform(
            id = "switch", displayName = "Nintendo Switch", scraperSystemId = 225,
            extensions = listOf(".nsp", ".xci"),
            folderNames = listOf("Switch", "switch", "Nintendo Switch"),
            defaultEmulatorPackage = "org.yuzu.yuzu_emu"
        ),
        Platform(
            id = "gc", displayName = "Nintendo GameCube", scraperSystemId = 13,
            extensions = listOf(".iso", ".rvz", ".gcm", ".gcz", ".ciso", ".nkit.iso"),
            folderNames = listOf("gc", "GC", "GameCube", "gamecube", "Nintendo GameCube", "ngc"),
            defaultEmulatorPackage = "org.dolphinemu.dolphinemu"
        ),
        Platform(
            id = "wii", displayName = "Nintendo Wii", scraperSystemId = 16,
            extensions = listOf(".iso", ".rvz", ".wbfs", ".wad", ".nkit.iso"),
            folderNames = listOf("wii", "Wii", "Nintendo Wii"),
            defaultEmulatorPackage = "org.dolphinemu.dolphinemu"
        ),
        Platform(
            id = "wiiu", displayName = "Nintendo Wii U", scraperSystemId = 18,
            extensions = listOf(".wux", ".wud", ".rpx", ".wua"),
            folderNames = listOf("wiiu", "WiiU", "Wii U", "Nintendo Wii U"),
            defaultEmulatorPackage = "info.cemu.cemu"
        ),
        Platform(
            id = "atari2600", displayName = "Atari 2600", scraperSystemId = 26,
            extensions = listOf(".a26", ".bin"),
            folderNames = listOf("Atari2600", "atari2600", "Atari 2600", "2600"),
            defaultCoreForRetroArch = "stella2014_libretro.so"
        ),
        Platform(
            id = "mame", displayName = "MAME / Arcade", scraperSystemId = 75,
            extensions = listOf(".zip", ".7z", ".chd"),
            folderNames = listOf("MAME", "mame", "Arcade", "arcade"),
            defaultCoreForRetroArch = "mame2003_plus_libretro.so"
        ),
        Platform(
            id = "fbneo", displayName = "FinalBurn Neo", scraperSystemId = 75,
            extensions = listOf(".zip", ".7z"),
            folderNames = listOf("fbneo", "FBNeo", "fba", "FBA", "FinalBurn Neo"),
            defaultCoreForRetroArch = "fbneo_libretro.so"
        ),
        Platform(
            id = "neogeo", displayName = "Neo Geo", scraperSystemId = 142,
            extensions = listOf(".zip", ".7z"),
            folderNames = listOf("neogeo", "NeoGeo", "Neo Geo"),
            defaultCoreForRetroArch = "fbneo_libretro.so"
        ),
        Platform(
            id = "ngp", displayName = "Neo Geo Pocket", scraperSystemId = 25,
            extensions = listOf(".ngp", ".ngc"),
            folderNames = listOf("ngp", "NGP", "ngpc", "Neo Geo Pocket"),
            defaultCoreForRetroArch = "mednafen_ngp_libretro.so"
        ),
        Platform(
            id = "pcengine", displayName = "PC Engine / TurboGrafx-16", scraperSystemId = 31,
            extensions = listOf(".pce", ".sgx", ".cue", ".chd"),
            folderNames = listOf("pcengine", "PCEngine", "tg16", "TG16", "TurboGrafx-16", "pce"),
            defaultCoreForRetroArch = "mednafen_pce_fast_libretro.so"
        ),
        Platform(
            id = "segacd", displayName = "Sega CD / Mega-CD", scraperSystemId = 20,
            extensions = listOf(".chd", ".cue", ".iso"),
            folderNames = listOf("segacd", "SegaCD", "Sega CD", "megacd", "MegaCD", "Mega CD"),
            defaultCoreForRetroArch = "genesis_plus_gx_libretro.so"
        ),
        Platform(
            id = "3do", displayName = "Panasonic 3DO", scraperSystemId = 29,
            extensions = listOf(".iso", ".chd", ".cue", ".bin"),
            folderNames = listOf("3do", "3DO", "Panasonic 3DO"),
            defaultCoreForRetroArch = "opera_libretro.so"
        ),
        Platform(
            id = "psvita", displayName = "PlayStation Vita", scraperSystemId = 62,
            extensions = listOf(".vpk"),
            folderNames = listOf("psvita", "PSVita", "PS Vita", "Vita"),
            defaultEmulatorPackage = "org.vita3k.emulator"
        ),
        Platform(
            id = "steam", displayName = "PC / Steam", scraperSystemId = 0,
            extensions = listOf(".lnk", ".url", ".exe"),
            folderNames = listOf("steam", "Steam", "pc", "PC", "Windows"),
            defaultEmulatorPackage = "com.gamenative.android"
        )
    )

    // Deduplicate — index by platform id; last definition for same id wins (if duplicates exist)
    val byId: Map<String, Platform> = ALL.associateBy { it.id }

    // Extension → platform; for ambiguous extensions (e.g. .iso, .bin) folder name takes priority
    val byExtension: Map<String, Platform> = buildMap {
        ALL.forEach { platform ->
            platform.extensions.forEach { ext ->
                putIfAbsent(ext.lowercase(), platform)
            }
        }
    }

    val byFolderName: Map<String, Platform> = buildMap {
        ALL.forEach { platform ->
            platform.folderNames.forEach { name ->
                put(name.lowercase(), platform)
            }
        }
    }
}
