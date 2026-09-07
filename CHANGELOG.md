# Changelog

Release notes for eOr, newest first. APK downloads live on the [GitHub Releases](https://github.com/keweis2/eOr/releases) page.

Both `full` and `lite` APKs are signed with the same key across releases, so you can always update in place without losing anything. The `lite` build is for low-power handhelds / weak chipsets (RG DS RK3568 and similar); `full` is for everything else.

---

## 2.7.0

### Send games to your device from your computer's browser — no cable, no fuss

The big new thing this release is **Web Transfer**: eOr can now host a little web page on your device that you open from any computer on the same Wi-Fi. Drag files in from your browser and they land straight on the handheld.

**How it works:** go to **Settings → Web Transfer** and turn it on. The screen shows a web address and a QR code — open that address in your computer's browser (or scan the code), enter the PIN it shows to pair, and you're connected. From there you can send **games, BIOS files, artwork, background images, and even your settings** directly to the device. No USB cable, no pulling the SD card, no extra apps on your computer.

A few things worth knowing:
- Both devices have to be on the **same Wi-Fi network**.
- The **PIN changes every time** you turn Web Transfer on, so an old browser tab can't reconnect later.
- There's an **Activity** list on the settings screen so you can watch transfers come in.

### Also in this release
- **Manual game selection sticks now.** If you've hand-picked which game a box art / entry maps to, a background auto-scan will no longer come along and overwrite your choice.
- **Fixed a duplicate "Enable Friends" toggle** that was showing twice in settings.

---

## 2.6.4

### An apology, and the real fix for image loading

First, I'm sorry. Box art loading has been slow and flaky for a few versions now, and past attempts to fix it didn't get to the bottom of it. This is an emergency mid-week release because I finally found the actual cause — and fixed it properly.

**What was really wrong:** your box art is stored as large (0.5–1.3 MB) image files on your SD card, and the app was re-reading and decoding a full-size file *every single time* a cover scrolled into view — just to show it at thumbnail size. That's why systems took forever to fill in, and why covers you'd already seen would reload when you scrolled back. It was never your device being slow.

**The fix:** eOr now creates a tiny thumbnail of each cover the first time it sees it, and loads that afterward — around 50× less work. It also builds those thumbnails for your whole library quietly in the background after launch, so browsing is fast everywhere, not just where you've already been. Once you've seen a cover, it stays put and won't reload.

On my Retroid Pocket 4 Pro, screens that used to take ~9 seconds to fill now fill almost instantly, and scrolling to the bottom of a system and back no longer reloads a thing.

### Notes
- The first launch after updating does a one-time background pass to thumbnail your library. You can use the app normally while it runs — it's throttled to stay out of your way, and it only happens once (new games you add get handled automatically).
- Low-power (lite) build behavior is unchanged in this release.

---

## 2.6.3

A performance-and-polish release. Box art now loads in one sweep instead of trickling in card-by-card, Settings has been reorganized into a cleaner index-and-drill-in layout, and there's a round of under-the-hood security hardening. Happy gaming :)

### Performance
- **Box art loads together, not one-at-a-time** — the grid now brings artwork in as a batch instead of popping in card-by-card, so the home screen settles faster and looks steadier while scrolling.
- **Smoother focused-card motion** — the idle motion on the focused card now defers until box art has loaded, and its start delay was tuned, so artwork appears before the animation kicks in (matching the lite build's feel).

### Settings
- **Reorganized into an index + drill-in** — the old single long settings screen is now a tidy index that drills into per-category screens, so options are easier to find.

### Security & hardening
- **Hardened credential storage, file sharing, sync trust, and LAN discovery** — credentials are better protected at rest, file sharing is more tightly scoped, sync trust handling is stricter, and the nearby-device beacon is sanitized.

---

## 2.6.0

Your emulators finally get the same update love eOr gives itself. This release integrates **Obtainium** so eOr can surface available **emulator updates** right in Settings and help you install the ones you're missing. Plus **ChuckStation3 (PS3)** support from **@aarvsn**, live library syncing while eOr is running from **@picodspi**, and a nasty Steam-library scan loop squashed. Thanks to everyone sending PRs and feedback — happy gaming :)

### Emulator updates (new)
- **See when your emulators are out of date** — eOr now checks your installed, GitHub-tracked emulators and surfaces available updates in **Settings → Games → Emulators**, with a launch banner and (optional) notification when something's behind.
- **One tap to update** — the per-emulator **Update** button hands off to **Obtainium**, the community-standard updater for off-store emulators, so you update in place with the tool built for it. If Obtainium isn't installed, eOr points you to it.
- **Install what you're missing** — eOr can hand Obtainium the essential emulators you don't have yet, using the RJNY Obtainium Emulation Pack as the source-of-truth mapping (fetched latest, with a bundled fallback).
- **Update notifications toggle** — a new switch under *Check for updates* (default on) lets you silence the launch banner and system notification while keeping the manual check.
- **Gentler on GitHub's rate limit** — update checks now use cached ETags (unchanged releases cost nothing against the limit), fall back to the last-known version on a rate-limit hit instead of dropping the emulator, and the Settings card only hits the network on an explicit **Check for updates** tap.

### New system support
- **ChuckStation3 (PS3)** — thanks **@aarvsn** 🙏 — full platform support for the ChuckStation3 PS3 emulator: detection, launch, and save-location handling.

### Library scanning
- **Keeps up while eOr is running** — thanks **@picodspi** 🙏 — eOr now detects games added while it's open (e.g. an FTP upload) and refreshes the library instead of only scanning at startup.
- **Steam scan-loop fixed** — for anyone with a Steam library, that foreground refresh was re-running the full ROM scan every 30 seconds and deleting/re-adding Steam games on a loop. Steam entries are now correctly excluded from disk-change detection and from scan cleanup, so the loop is gone and a real scan can never wipe your Steam library. On lite builds the refresh now runs once per foreground entry instead of constant polling.
- **Steadier artwork & path handling** — embedded ROM artwork retries when it's missing, removed ROM paths are reconciled instead of lingering, and scans wait for in-progress ROM uploads to settle.

---

## 2.5.2

A short polish week focused on touch-first users and a friendlier first launch — a back button on the game grid, smarter onboarding nudges when your library comes up empty, and more Locked Mode navigation hardening from **@picodspi**. Thanks for the feedback — keep it coming. Happy gaming :)

### Home screen
- **Back button on the game grid** — eOr is controller-first, but touch users had no way back to the console list once inside a system (the back action lived only on the controller's B button). There's now a back button at the top-left of the grid header, matching the game detail screen, so a tap takes you back to your systems.

### First launch & onboarding
- **Empty-library nudges** — if you reach the end of first-run setup with **no games** or **no emulators** detected, Otto now shows a friendly tip on how to fix it: add ROM files and rescan, or install an emulator (RetroArch covers most systems) and eOr will auto-assign it. Nothing is blocked — it's just a pointer in the right direction.
- **Rewritten First Launch Setup guide** — the README now walks through the actual Otto-guided setup (Welcome → Find your games → Theme → Building your arcade), with a **Before you start** checklist (install emulators, gather ROMs, optional ScreenScraper account) and an **After setup** section (rescan/scrape new ROMs, fine-tune emulators, validate credentials).

### Locked Mode — thanks @picodspi 🙏
- **Warn before first system-navigation block** — Locked Mode now warns you before it blocks system navigation for the first time, so kiosk setup is less surprising.
- **Navigation-blocking is revealed only when developer options is active**, keeping the option out of the way until it's usable.

---

## 2.5.1

A polish-and-fixes week — sharper scraping, smarter library scanning, offline arcade names, dark-mode contrast fixes, and a big Locked Mode hardening from **@picodspi**. Thanks to everyone filing issues; keep the feedback coming. Happy gaming :)

### Scraping & artwork
- **Per-system artwork scrape** — the game grid's Select menu now has a "Scrape artwork" action that scrapes just the current system instead of the whole library.
- **Uses your ES-DE library before the network** — before hitting ScreenScraper, eOr now satisfies artwork and metadata from an existing ES-DE library, and it now imports **game descriptions from `gamelist.xml`** too, not just images.
- **Fixed an inflated "needs scraping" count** — the "needs scraping" gate used to key off an internal flag that only the online scraper ever set, so every ES-DE- or embedded-artwork game counted as un-scraped. It now judges each game purely on the art/description it actually has.
- **Faster ES-DE imports** — import writes run in a single transaction, and `gamelist.xml` is found via per-system lookups instead of walking the entire ROM tree.
- **"One moment please…" prep state** on the scrape screen while it works out what needs scraping.

### Library scanning
- **Scan on every launch** — a lightweight scan now runs each time you open eOr and picks up games added since last launch. Android/Steam refreshes are cheap; ROM folders are only fully re-scanned when a fast no-hash probe detects something new. A small **"Scanning for new games…"** indicator shows on Home while a ROM scan runs.
- **Offline arcade name resolution** — arcade ROMs are named after their MAME/FBNeo romset id (e.g. `afighter`), so they used to show cryptic codes until you scraped. eOr now bundles an offline romset-id → title table (FBNeo + MAME from the libretro-database DATs, ~18k entries, gzipped) and resolves real names **at scan time** for `mame`, `fbneo`, `neogeo`, and `cps1/2/3`, falling back to the cleaned filename. Existing library entries are backfilled on rescan — but only when the stored title is still the raw short name and the game isn't scraped or manually renamed, so scraped titles and hand edits are never clobbered.

### Home screen & layout
- **Scrollable home tab strip** — when you enable enough mode tabs to overflow the screen, the strip now scrolls horizontally instead of squashing/clipping, and the selected tab is kept in view so gamepad **L1/R1** cycling can still reach tabs that scrolled off-screen.
- **Master game-grid size** — a new overall grid-size slider under **Settings → Display → Library Layout** (shown only when the grid layout is active) sets the default tile size for every system. A system you've sized from its own grid still overrides it, and Recents/Favorites keep their own fixed sizing.
- **Home-card color schemes** — pick how tiles are colored under **Settings → Display → Appearance**: **Rainbow** (default), **Black & White**, or **Monochrome** (a single hue you choose, applied across all tiles). Persisted and applied system-wide.
- **Settings tabs reordered** to General → Games → Media → Locked Mode → RetroAchievements → Save Sync → Friends.

### Dark mode & dual screen
- **Dark-mode contrast fix** — text on colored glass tiles (the system game counter and the view-options panel) was low-contrast in dark mode; on-tile secondary text now uses a near-opaque light color instead of the dim gray.
- **Dual-screen tab icons** — non-game Home tabs (Favorites, Recent, Apps, RetroAchievements, Friends) now mirror their icon on the top panel, matching how the Settings gear already behaved.
- **Bottom-panel dim on launch** — on dual-screen devices, when a game opens on the top panel the bottom screen stays running but dims to ~18% brightness (animated, fading back on return) to push focus to the game.

### Locked Mode — thanks @picodspi 🙏
- **System-navigation blocking** — Locked Mode can now block system navigation using an embedded privilege broker and a Wireless ADB pairing flow, so kiosk mode is much harder to escape.
- Navigation-lock **setup and status are exposed in Settings**.
- System navigation is **restored when Locked Mode is unlocked**, and active Locked Mode state is **reset after a device reboot**.
- Adds security and navigation-lock test coverage, and documents Shizuku provenance and third-party licensing.

### Fixes
- **Grid selection could fall off-screen** — with tall arcade art at a large grid size, where barely one full row fits, the focus-scroll anchored the selected card to the second visible row and pushed it off the bottom edge. eOr now measures how many whole rows actually fit and pins the selected card's row to the top when fewer than two do.
