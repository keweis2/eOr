# eOr

A lightweight game frontend for Android. Browse your ROM collection by platform, scrape artwork and video previews automatically via [ScreenScraper.fr](https://www.screenscraper.fr), and launch games directly into your installed emulators — all from a single polished interface.

---

## Features

- **3DS-inspired UI** — a fanned box art preview over a system carousel, with a colourful box art grid per system. Liquid-glass tiles, bounce animations, and a donkey mascot beside the **eOr** wordmark
- **Recently Played tab** — an optional top-level tab (toggle in *Settings → Display*) showing a grid of recently launched games
- **Dark mode** — toggle light/dark theming in *Settings → Display*
- **Hold-to-scroll** — hold a direction on the D-pad to keep moving through any grid or carousel
- **Video previews** — auto-plays a game's video preview after a short delay; cached locally after first download
- **Two artwork sources** — batch-scrape box art, screenshots, wheel logos, and video previews from [ScreenScraper.fr](https://www.screenscraper.fr), or pull box art and snaps from the public **libretro thumbnail** server with no account required
- **ES-DE media import** — import an existing ES-DE `downloaded_media` folder instead of re-scraping
- **Multi-emulator support** — launch into RetroArch (with per-platform core selection), any standalone emulator, or any installed app via a custom intent mapping; emulators are auto-detected and assigned per platform
- **Broad system coverage** — 30 systems from the NES through the Switch, PS2/PS Vita, GameCube/Wii/Wii U, arcade (MAME, FinalBurn Neo, Neo Geo) and more
- **Console & system icons** — full-colour console illustrations for every system, with custom icons for the modern consoles the icon pack doesn't cover
- **ROM scanner** — scans a configurable folder, auto-detects platforms by subfolder name and file extension (including arcade `.zip`/`.7z` sets), and keeps the library in sync when ROMs are added or removed
- **Gamepad-first navigation** — full D-pad/bumper control; LB/RB switch top-level tabs and cycle systems
- **Favorites & play history** — mark favorites and track recently played games

---

## Screenshots

> Screenshots will be added after first device build. To contribute screenshots, open a PR against `main`.

| Carousel View | Grid View | Game Detail | Settings |
|---|---|---|---|
| *(coming soon)* | *(coming soon)* | *(coming soon)* | *(coming soon)* |

---

## Supported Platforms

Thirty systems are recognised out of the box. The scanner matches a ROM's parent folder
name (case-insensitively) against each system, and falls back to the file extension.

**Nintendo**

| System | Typical folder(s) | Extensions |
|---|---|---|
| NES | `nes` | `.nes` |
| Super Nintendo | `snes`, `Super Famicom` | `.sfc`, `.smc`, `.snes` |
| Nintendo 64 | `n64` | `.n64`, `.z64`, `.v64` |
| Game Boy | `gb` | `.gb` |
| Game Boy Color | `gbc` | `.gbc` |
| Game Boy Advance | `gba` | `.gba` |
| Nintendo DS | `nds` | `.nds` |
| Nintendo 3DS | `3ds`, `n3ds` | `.3ds`, `.cia` |
| Nintendo GameCube | `gc`, `gamecube`, `ngc` | `.iso`, `.rvz`, `.gcm`, `.gcz`, `.ciso` |
| Nintendo Wii | `wii` | `.iso`, `.rvz`, `.wbfs` |
| Nintendo Wii U | `wiiu` | `.wua`, `.wux`, `.rpx` |
| Nintendo Switch | `switch` | `.nsp`, `.xci` |

**Sony**

| System | Typical folder(s) | Extensions |
|---|---|---|
| PlayStation | `ps1`, `psx` | `.bin`, `.cue`, `.iso`, `.pbp`, `.chd` |
| PlayStation 2 | `ps2` | `.iso`, `.chd` |
| PSP | `psp` | `.iso`, `.cso` |
| PlayStation Vita | `psvita`, `vita` | `.vpk` |

**Sega**

| System | Typical folder(s) | Extensions |
|---|---|---|
| Genesis / Mega Drive | `genesis`, `md`, `megadrive` | `.md`, `.gen`, `.bin` |
| Master System | `sms`, `mastersystem` | `.sms` |
| Game Gear | `gg`, `gamegear` | `.gg` |
| Sega CD / Mega-CD | `segacd`, `megacd` | `.cue`, `.chd`, `.iso` |
| Sega 32X | `32x`, `sega32x` | `.32x`, `.bin` |
| Saturn | `saturn` | `.cue`, `.iso`, `.chd` |
| Dreamcast | `dc`, `dreamcast` | `.cdi`, `.gdi`, `.chd` |

**Arcade, NEC, SNK & others**

| System | Typical folder(s) | Extensions |
|---|---|---|
| MAME | `mame`, `arcade` | `.zip`, `.7z`, `.chd` |
| FinalBurn Neo | `fbneo`, `fba` | `.zip`, `.7z` |
| Neo Geo | `neogeo` | `.zip`, `.7z` |
| Neo Geo Pocket | `ngp` | `.ngp`, `.ngc` |
| PC Engine / TurboGrafx-16 | `pcengine`, `tg16` | `.pce`, `.sgx`, `.cue`, `.chd` |
| Panasonic 3DO | `3do` | `.iso`, `.chd`, `.cue`, `.bin` |
| Atari 2600 | `atari2600`, `2600` | `.a26`, `.bin` |

---

## Supported Emulators

The app auto-detects whichever of these are installed and lets you assign one per platform. Where multiple variants exist for the same emulator, the first one found (top of each group) is used automatically.

**RetroArch** (universal fallback for most platforms)

| Emulator | Package |
|---|---|
| RetroArch (AArch64) | `com.retroarch.aarch64` |
| RetroArch | `org.libretro.retroarch` |

**PlayStation**

| Emulator | Covers | Package |
|---|---|---|
| DuckStation | PS1 | `com.github.stenzek.duckstation` |
| NetherSX2 / AetherSX2 | PS2 | `xyz.aethersx2.android` |
| NetherSX2 | PS2 | `xyz.trizle.nethersx2` |
| AetherSX2 | PS2 | `net.play.ptmk.ps2` |
| PPSSPP Gold | PSP | `org.ppsspp.ppssppgold` |
| PPSSPP | PSP | `org.ppsspp.ppsspp` |
| Vita3K | PS Vita | `org.vita3k.emulator` |

**Nintendo handhelds**

| Emulator | Covers | Package |
|---|---|---|
| DraStic | NDS | `com.drastic.ds` |
| melonDS | NDS | `me.magnum.melonds` |
| Azahar | 3DS | `org.azahar_emu.azahar` |
| Citra (Retroid build) | 3DS | `org.citra.emu` |
| Citra MMJ | 3DS | `com.weihuoya.citra` |
| Citra | 3DS | `org.citra_emu.citra` |
| GBA.emu | GBA | `com.explusalpha.GbaEmu` |
| GBC.emu | GBC / GB | `com.explusalpha.GbcEmu` |
| Snes9x EX+ | SNES | `com.explusalpha.Snes9xEmu` |

**Nintendo 64 / consoles**

| Emulator | Covers | Package |
|---|---|---|
| Mupen64Plus FZ Pro | N64 | `org.mupen64plusae.v3.fzurita.pro` |
| Mupen64Plus FZ | N64 | `org.mupen64plusae.v3.fzurita` |
| Dolphin | GameCube / Wii | `org.dolphinemu.dolphinemu` |
| Cemu | Wii U | `info.cemu.cemu` |

**Switch**

| Emulator | Package |
|---|---|
| Eden (Retroid build) | `dev.eden.eden_emulator` |
| Eden | `dev.eden.emulator` |
| Sudachi (Retroid build) | `org.sudachi.sudachi_emu` |
| Yuzu | `org.yuzu.yuzu_emu` |

**Sega**

| Emulator | Covers | Package |
|---|---|---|
| Redream | Dreamcast | `io.recompiled.redream` |
| Flycast | Dreamcast | `com.flycast.emulator` |
| Reicast | Dreamcast | `com.reicast.emulator` |
| Yaba Sanshiro 2 Pro | Saturn | `org.devmiyax.yabasanshioro2.pro` |
| Yaba Sanshiro 2 | Saturn | `org.devmiyax.yabasanshioro2` |

**Other**

| Emulator | Covers | Package |
|---|---|---|
| J2ME Loader | Java ME games | `ru.playsoftware.j2meloader` |

Any other emulator can be added via **Settings → Configure Emulators** using a custom package name.

---

## Requirements

- Android **8.0 (API 26)** or higher
- Android Studio **Hedgehog (2023.1.1)** or newer
- JDK **17**
- Android SDK with `compileSdk = 35`

---

## Installation

### 1. Clone the repository

```bash
git clone https://github.com/keweis2/eOr.git
cd eOr
```

### 2. Configure local.properties

Create (or edit) `local.properties` in the project root:

```properties
# Path to your Android SDK
sdk.dir=/Users/<your-username>/Library/Android/sdk   # macOS
# sdk.dir=C:\\Users\\<your-username>\\AppData\\Local\\Android\\Sdk   # Windows

# ScreenScraper developer credentials (optional — needed to use the scraper)
# Register a developer account at https://www.screenscraper.fr
SS_DEV_ID=
SS_DEV_PASSWORD=
```

> `local.properties` is git-ignored and will never be committed.

### 3. Build & run

Open the project in **Android Studio**, select your device or emulator, and press **Run**.

Alternatively, build an APK from the command line:

```bash
./gradlew assembleDebug
# APK will be at app/build/outputs/apk/debug/app-debug.apk
```

---

## First Launch Setup

On first launch the app opens **Settings** automatically. Complete these steps before scanning:

1. **Set your ROM folder** — tap the folder icon next to "ROM Folder" and select the directory where your ROMs live (e.g. `/sdcard/ROMs`). The scanner expects subfolders named after platforms (e.g. `ROMs/SNES/`, `ROMs/PS1/`).

2. **Configure emulators** — tap **Configure Emulators**. For each platform, choose which installed emulator to use. If using RetroArch, also set the core filename (e.g. `snes9x_libretro.so`).

3. **Add ScreenScraper credentials** — sign up for a free account at [screenscraper.fr](https://www.screenscraper.fr), then enter your username and password under the ScreenScraper section. Tap **Validate** to confirm they work.

4. **Scan ROMs** — tap **Rescan ROMs** (or go back to Home; a scan runs automatically on first launch if a ROM folder is set). The scanner hashes the first 8 MB of each file for better ScreenScraper matching.

5. **Scrape artwork** — tap **Scrape All** to fetch box art, screenshots, and video previews for your library. Progress is shown in real time. The scraper respects ScreenScraper's rate limit (1 request per 1.2 seconds) automatically.

---

## ROM Folder Structure

```
/sdcard/ROMs/
├── NES/
│   ├── Super Mario Bros.nes
│   └── Mega Man 2.nes
├── SNES/
│   ├── Super Metroid.sfc
│   └── Chrono Trigger.sfc
├── PS1/
│   ├── Final Fantasy VII Disc1.bin
│   └── Final Fantasy VII Disc1.cue
└── GBA/
    └── Pokemon FireRed.gba
```

Subfolder names are matched case-insensitively against the platform table above. If a subfolder name isn't recognized, the scanner falls back to the file extension.

---

## Project Structure

```
app/src/main/java/com/gamelaunch/frontend/
├── data/
│   ├── db/                  # Room database, DAOs, entities
│   ├── network/             # ScreenScraper Retrofit API + DTOs
│   ├── preferences/         # DataStore wrapper
│   └── repository/          # Repository implementations
├── domain/
│   ├── model/               # Pure Kotlin data models
│   ├── platform/            # Platform definitions + detector
│   ├── repository/          # Repository interfaces
│   └── usecase/             # ScanRoms, ScrapeGame, BatchScrape, LaunchGame
├── launcher/                # EmulatorLauncher + PackageManagerHelper
├── ui/
│   ├── component/           # VideoPlayer, AsyncGameArtwork, PlatformTabRow
│   ├── navigation/          # NavGraph + Screen sealed class
│   ├── screen/              # Home, GameDetail, Scan, Scrape, Settings
│   └── theme/
│       ├── carousel/        # Full-screen carousel layout
│       └── grid/            # Grid layout
└── di/                      # Hilt DI modules
```

---

## Tech Stack

| Library | Purpose |
|---|---|
| Jetpack Compose | UI |
| Hilt | Dependency injection |
| Room | Local game database |
| Retrofit + OkHttp | ScreenScraper API |
| Media3 / ExoPlayer | Video preview playback |
| Coil | Image loading & caching |
| DataStore | Settings persistence |
| Navigation Compose | Screen routing |

---

## Contributing

Pull requests are welcome. For major changes, open an issue first to discuss what you'd like to change.

When adding a new platform, add an entry to [`PlatformDefinitions.kt`](app/src/main/java/com/gamelaunch/frontend/domain/platform/PlatformDefinitions.kt) with the correct ScreenScraper `systemeid`.

---

## Credits

System console icons are from **[retro-game-console-icons](https://github.com/KyleBing/retro-game-console-icons)** by [KyleBing](https://github.com/KyleBing), licensed under [GPL-3.0](https://github.com/KyleBing/retro-game-console-icons/blob/master/LICENSE). Thank you!

---

## License

[MIT](LICENSE)
