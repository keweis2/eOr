<div align="center">

# 🎮 eOr

### Your retro library, beautifully organized.

**eOr** — short for *emulation, organized* — is a fast, gorgeous game launcher for Android handhelds and phones. Point it at your ROMs, let it pull box art, screenshots and video previews automatically, and launch straight into your games — all wrapped in a polished, controller-first interface.

[![Latest release](https://img.shields.io/github/v/release/keweis2/eOr?style=flat-square&color=4D7FFF)](https://github.com/keweis2/eOr/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/keweis2/eOr/total?style=flat-square&color=8B5CF6&label=downloads)](https://github.com/keweis2/eOr/releases)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](#requirements)
[![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)](LICENSE)

<img src="docs/screenshots/carousel.png" width="82%" alt="eOr — system carousel with a fanned box-art preview" />

</div>

---

## ✨ Why eOr?

- 🎨 **Looks the part** — a fanned box-art hero over a colourful system carousel, glossy tiles and playful bounce animations. Choose **Light or Dark** from a visual theme picker, and sort your consoles however you like (release date, brand, library size and more).
- 🖼️ **Art that fills itself in** — batch-scrape box art, screenshots, wheel logos and video previews from [ScreenScraper.fr](https://www.screenscraper.fr), with free **libretro thumbnails** and **LaunchBox** as no-account fallbacks. Already have an ES-DE library? Import its `downloaded_media` folder instead. Re-scraping skips anything that's already complete.
- 🎮 **Plays everything** — auto-detects your installed emulators and launches games straight into them, with per-core selection where it applies, across **30+ systems** spanning generations of retro and modern consoles.
- 🕹️ **Built for a controller** — full D-pad and bumper navigation, hold-to-scroll, and your place is remembered as you move between systems, games and detail screens.
- 🖥️🖥️ **Dual-screen Support** — on dual screen handhelds, eOr spreads across both panels following your Light/Dark theme. No additional setup required, it's detected automatically.
- 🏆 **RetroAchievements** — sign in with your username and password to see your points, rank and recently-played progress right inside the launcher.
- 🔄 **Save Sync** — keep your emulator saves in sync across devices with peer-to-peer syncing (powered by Syncthing) and one-QR-code device pairing. eOr shows which of your installed emulators are ready to sync, with optional Wi-Fi-only and charging-only conditions.
- 👥 **Friends** — see what your friends are playing and their RetroAchievements score, right on the home screen. Add friends by sharing a link or tapping a nearby player on the same Wi-Fi — it's fully peer-to-peer with **no account, no login, and nothing stored online**. Turn the whole feature off any time with a single toggle.
- 📱 **More than ROMs** — bring in your installed Android games and Steam / PC streaming launchers alongside your retro collection.
- ⚡ **Fast & tidy** — a 512 MB artwork cache, instant navigation, and a scanner that keeps your library in sync as ROMs come and go.
- ➡️ **Weekly Releases** - with v2 we are moving to a weekly release model so every weekend you will get the most up to date version of eOr 

---

## 📸 Screenshots

<div align="center">

| Game library | Game detail & launch |
|:---:|:---:|
| <img src="docs/screenshots/grid.png" width="430" alt="Box-art grid for a system" /> | <img src="docs/screenshots/detail.png" width="430" alt="Game detail and launch screen" /> |
| **Dark mode** | **RetroAchievements** |
| <img src="docs/screenshots/dark.png" width="430" alt="The same grid in dark mode" /> | <img src="docs/screenshots/retroachievements.png" width="430" alt="RetroAchievements dashboard — points, rank and recently-played progress" /> |
| **Visual theme picker** | **Save Sync** |
| <img src="docs/screenshots/settings.png" width="430" alt="Settings with a Light / Dark theme picker" /> | <img src="docs/screenshots/savesync.png" width="430" alt="Save Sync — cross-device save syncing with one-QR device pairing" /> |
| **Friends** | **Add a friend** |
| <img src="docs/screenshots/friends.png" width="430" alt="Friends tab — each friend's last-played game and RetroAchievements score" /> | <img src="docs/screenshots/friends-pairing.png" width="430" alt="Friends — share your code or add a nearby player; peer-to-peer, no account" /> |

</div>

---

## Supported Platforms

If you grew up with it, eOr probably runs it. **30+ systems** are recognised out of the box — the 8-bit classics, the 16-bit golden age, modern handhelds, HD consoles and arcade. Drop your ROMs into folders named after each system and the scanner sorts everything automatically by folder name and file extension.

*Play all the platforms you love — no spreadsheet required.*

---

## Supported Emulators

eOr launches straight into the emulators you already use. Installed emulators are **auto-detected and assigned per platform** — RetroArch (with per-core selection) for the universal stuff, and your favourite standalones for everything else. Anything we don't recognise can be added in seconds via **Settings → Configure Emulators** with a custom package name.

---

## Requirements

- An Android device running **Android 8.0 (Oreo)** or newer
- Enough free storage for your ROMs and downloaded artwork (a **512 MB** artwork cache is used by default)
- **Permission to install apps from unknown sources**, since eOr is distributed as an APK outside the Play Store
- The **emulator apps** you want to launch your games in

---

## First Launch Setup

On first launch **Otto**, the eOr donkey, walks you through a short guided setup — no menus to hunt through. The whole thing takes about a minute, and everything it asks for can be changed later in **Settings**.

### Before you start (optional, but nice to have)

None of these are required to get through setup, but having them ready means your library is playable the moment setup finishes:

- **Install the emulators you want to play in.** eOr launches games *into* other emulator apps — it doesn't emulate anything itself. Install at least one (RetroArch is a great catch-all; add standalones like Dolphin, PPSSPP or DuckStation for the systems you care about) and eOr will auto-detect and assign them during setup. No emulators yet? You can install them afterwards and eOr will pick them up.
- **Gather your ROMs.** Copy your game files onto the device (internal storage or SD card). Folders named after each system — e.g. `ROMs/SNES/`, `ROMs/PS1/` — scan most cleanly, but eOr can also create an empty, correctly-named folder tree for you and you can drop games in later.
- **Create a free [ScreenScraper.fr](https://www.screenscraper.fr) account.** This gives the best box art and video previews. It's optional — without it eOr falls back to free libretro thumbnails and LaunchBox art.

### The guided walkthrough

1. **Welcome** — Otto says hi. Tap **Let's go!** (or press **A** on a controller) to begin.

2. **Find your games** — eOr searches your storage for a ROM folder automatically. If it finds one, confirm it with **Yes, that's them!**; otherwise let it **create a games folder** for you (one subfolder per system), or pick your own. Tap **Advanced** here if you want to set a custom **artwork folder** or enter your **ScreenScraper** username and password up front.

3. **Pick a theme** — choose **Light** or **Dark**. You can switch any time later from the visual theme picker in Settings.

4. **Building your arcade** — eOr runs the setup pipeline automatically: it scans your ROMs, detects and assigns your installed emulators, pulls in any installed Android games, then downloads box art, screenshots and video previews. Artwork can keep downloading in the background — you can jump into the app and eOr will notify you when it's finished. When it's done, Otto celebrates 🎉 and you land on your library.

> If you reach the finish line with **no games** or **no emulators** detected, Otto shows a tip on how to fix it — add ROM files and rescan, or install an emulator and eOr will assign it. Nothing is blocked; you can always sort this out afterwards.

### After setup

- **Added more ROMs?** Rescan from **Settings → Rescan ROMs** (a scan also runs automatically on launch) and scrape art for the new titles with **Scrape All** — re-scraping skips anything already complete.
- **Fine-tune emulators** in **Settings → Configure Emulators** — pick which emulator handles each platform, and for RetroArch set the core filename (e.g. `snes9x_libretro.so`). Anything eOr didn't recognise can be added with a custom package name.
- **Add or validate ScreenScraper credentials** later under the ScreenScraper section, then tap **Validate** to confirm they work and re-scrape for higher-quality media.

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

## Want to Contribute?

Pull requests are welcome. For major changes, open an issue first to discuss what you'd like to change.

When adding a new platform, add an entry to [`PlatformDefinitions.kt`](app/src/main/java/com/gamelaunch/frontend/domain/platform/PlatformDefinitions.kt) with the correct ScreenScraper `systemeid`.

---

## Credits
### Open Source Buddies

- System console icons are from **[retro-game-console-icons](https://github.com/KyleBing/retro-game-console-icons)** by [KyleBing](https://github.com/KyleBing), licensed under [GPL-3.0](https://github.com/KyleBing/retro-game-console-icons/blob/master/LICENSE). Thank you!

### Services & integrations

eOr builds on the work of these projects and communities:

- **[ScreenScraper](https://www.screenscraper.fr)** — the community-run game database that powers eOr's metadata and artwork scraping. Thanks to the ScreenScraper team and its contributors.
- **[Syncthing](https://syncthing.net)** — the open-source, continuous file-synchronization project behind eOr's save-sync detection. Thanks to the Syncthing project and its maintainers.
- **[RetroAchievements](https://retroachievements.org)** — the achievement platform and API that eOr surfaces game progress from. Thanks to the RetroAchievements team and community.
- **[Obtainium](https://github.com/ImranR98/Obtainium)** — the open-source app updater that eOr hands off to for tracking and installing emulator updates, using the [RJNY Obtainium Emulation Pack](https://github.com/RJNY/Obtainium-Emulation-Pack) mapping. Thanks to [ImranR98](https://github.com/ImranR98) and the Obtainium community, and to [RJNY](https://github.com/RJNY) for maintaining the pack.

### Contributors

Thanks to everyone who has helped improve eOr:

- **[@aarvsn](https://github.com/aarvsn)** — database cleanup, emulator-mapping, and scanner fixes ([#41](https://github.com/keweis2/eOr/pull/41)); **Xbox 360 (Xenia) integration** ([#56](https://github.com/keweis2/eOr/pull/56))
- **[@picodspi](https://github.com/picodspi)** — **Eden support** — FileProvider content-URI launching plus update/DLC/firmware scan filtering ([#68](https://github.com/keweis2/eOr/pull/68)); **NSP artwork** — extract and import artwork embedded in Switch NSP files ([#72](https://github.com/keweis2/eOr/pull/72)); **Home-launcher mode** — run eOr as the device home app, with Home returning to the library ([#76](https://github.com/keweis2/eOr/pull/76)); **Locked Mode** — PIN-protected kiosk mode with an app allowlist and per-game availability ([#79](https://github.com/keweis2/eOr/pull/79))

### Supporters

- **Ban** at **[RetroHandhelds.gg](https://retrohandhelds.gg)** — for the write-up, [_eOr Launcher Aims to Be An Organized Frontend for Emulation_](https://retrohandhelds.gg/eor-launcher-aims-to-emulation-organized-all-in-one/). Thank you!

---

## AI Usage 
For those with questions about AI. I am a technologist, designer, engineer, and creator by trade. I work on MANY projects at once (luckily on this one I have wonderful contributors who help) and yes I use AI to do a lot of the work on my hobby projects, like this one. There simply isn't enough "human power" to work on everything at once. I use AI so I can offer quality experiences I love to the people I love completely free, completely open source, and with no ads or money making schemes ever. I will never ask for anything from any user, not even a coffee tip. I simply like retro gaming and wanted to make a launcher that showed my appreciation for that community. 

I am sorry if the use of AI turns you away but I will keep improving this app either way. Just for the love of the retro game!! <3

Thank you to all of our users, git contributors, and to gamers everywhere for not only helping to inspire and create this app but also this amazing community! All of the love to you all <3

---

## License

[MIT](LICENSE)
