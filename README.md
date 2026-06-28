# eOr

A lightweight game frontend for Android. Browse your ROM collection by platform, scrape artwork and video previews automatically via [ScreenScraper.fr](https://www.screenscraper.fr), and launch games directly into your installed emulators — all from a single polished interface.

---

## Features

- **Carousel & Grid layouts** — switch between a full-screen carousel with animated video previews, or a compact box art grid
- **Video previews** — auto-plays a game's video preview after 1.5 seconds of selection; cached locally after first download
- **ScreenScraper.fr scraper** — batch-scrapes box art, screenshots, wheel logos, and video previews for your entire library
- **Multi-emulator support** — launch into RetroArch (with per-platform core selection), any standalone emulator, or any installed app via a custom intent mapping
- **ROM scanner** — scans a configurable folder, auto-detects platforms by subfolder name and file extension, and keeps the library in sync when ROMs are added or removed
- **Favorites & play history** — mark favorites and track recently played games
- **Material You theming** — adapts to your system's dynamic color palette (Android 12+)

---

## Screenshots

> Screenshots will be added after first device build. To contribute screenshots, open a PR against `main`.

| Carousel View | Grid View | Game Detail | Settings |
|---|---|---|---|
| *(coming soon)* | *(coming soon)* | *(coming soon)* | *(coming soon)* |

---

## Supported Platforms

| System | Folder Name(s) | Extensions |
|---|---|---|
| Nintendo NES | `NES`, `nes` | `.nes` |
| Super Nintendo | `SNES`, `snes`, `Super Famicom` | `.sfc`, `.smc`, `.snes` |
| Nintendo 64 | `N64`, `n64` | `.n64`, `.z64`, `.v64` |
| Game Boy | `GB`, `gb` | `.gb` |
| Game Boy Color | `GBC`, `gbc` | `.gbc` |
| Game Boy Advance | `GBA`, `gba` | `.gba` |
| Nintendo DS | `NDS`, `nds` | `.nds` |
| Nintendo 3DS | `3DS`, `3ds` | `.3ds`, `.cia` |
| Nintendo Switch | `Switch`, `switch` | `.nsp`, `.xci` |
| PlayStation | `PS1`, `psx`, `PSX` | `.bin`, `.cue`, `.iso`, `.pbp`, `.chd` |
| PlayStation 2 | `PS2`, `ps2` | `.iso`, `.chd` |
| PSP | `PSP`, `psp` | `.iso`, `.cso` |
| Dreamcast | `Dreamcast`, `DC` | `.cdi`, `.gdi`, `.chd` |
| Sega Genesis / Mega Drive | `Genesis`, `MD`, `MegaDrive` | `.md`, `.gen` |
| Sega Master System | `SMS`, `sms` | `.sms` |
| Game Gear | `GG`, `GameGear` | `.gg` |
| Sega Saturn | `Saturn` | `.cue`, `.iso`, `.chd` |
| Atari 2600 | `Atari2600`, `2600` | `.a26`, `.bin` |
| MAME / Arcade | `MAME`, `Arcade`, `FBNeo` | `.zip`, `.7z`, `.chd` |

---

## Supported Emulators

The app auto-detects whichever of these are installed and lets you assign one per platform:

| Emulator | Package |
|---|---|
| RetroArch | `org.libretro.retroarch` |
| Dolphin | `org.dolphinemu.dolphinemu` |
| PPSSPP | `org.ppsspp.ppsspp` |
| DuckStation | `com.github.stenzek.duckstation` |
| DraStic | `com.drastic.ds` |
| Flycast | `com.flycast.emulator` |
| GBC.emu | `com.explusalpha.GbcEmu` |
| GBA.emu | `com.explusalpha.GbaEmu` |
| Snes9x EX+ | `com.explusalpha.Snes9xEmu` |
| Yuzu | `org.yuzu.yuzu_emu` |
| Citra | `org.citra_emu.citra` |

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

## License

[MIT](LICENSE)
