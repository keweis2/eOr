## Highlights

A polish-and-fixes week — sharper scraping, smarter library scanning, offline arcade names, dark-mode contrast fixes, and a big Locked Mode hardening from **@picodspi**. Thanks to everyone filing issues; keep the feedback coming. Happy gaming :)

## Scraping & artwork
- **Per-system artwork scrape** — the game grid's Select menu now has a "Scrape artwork" action that scrapes just the current system instead of the whole library.
- **Uses your ES-DE library before the network** — before hitting ScreenScraper, eOr now satisfies artwork and metadata from an existing ES-DE library, and it now imports **game descriptions from `gamelist.xml`** too, not just images.
- **Fixed an inflated "needs scraping" count** — the "needs scraping" gate used to key off an internal flag that only the online scraper ever set, so every ES-DE- or embedded-artwork game counted as un-scraped. It now judges each game purely on the art/description it actually has.
- **Faster ES-DE imports** — import writes run in a single transaction, and `gamelist.xml` is found via per-system lookups instead of walking the entire ROM tree.
- **"One moment please…" prep state** on the scrape screen while it works out what needs scraping.

## Library scanning
- **Scan on every launch** — a lightweight scan now runs each time you open eOr and picks up games added since last launch. Android/Steam refreshes are cheap; ROM folders are only fully re-scanned when a fast no-hash probe detects something new. A small **"Scanning for new games…"** indicator shows on Home while a ROM scan runs.
- **Offline arcade name resolution** — arcade ROMs are named after their MAME/FBNeo romset id (e.g. `afighter`), so they used to show cryptic codes until you scraped. eOr now bundles an offline romset-id → title table (FBNeo + MAME from the libretro-database DATs, ~18k entries, gzipped) and resolves real names **at scan time** for `mame`, `fbneo`, `neogeo`, and `cps1/2/3`, falling back to the cleaned filename. Existing library entries are backfilled on rescan — but only when the stored title is still the raw short name and the game isn't scraped or manually renamed, so scraped titles and hand edits are never clobbered.

## Home screen & layout
- **Scrollable home tab strip** — when you enable enough mode tabs to overflow the screen, the strip now scrolls horizontally instead of squashing/clipping, and the selected tab is kept in view so gamepad **L1/R1** cycling can still reach tabs that scrolled off-screen.
- **Master game-grid size** — a new overall grid-size slider under **Settings → Display → Library Layout** (shown only when the grid layout is active) sets the default tile size for every system. A system you've sized from its own grid still overrides it, and Recents/Favorites keep their own fixed sizing.
- **Home-card color schemes** — pick how tiles are colored under **Settings → Display → Appearance**: **Rainbow** (default), **Black & White**, or **Monochrome** (a single hue you choose, applied across all tiles). Persisted and applied system-wide.
- **Settings tabs reordered** to General → Games → Media → Locked Mode → RetroAchievements → Save Sync → Friends.

## Dark mode & dual screen
- **Dark-mode contrast fix** — text on colored glass tiles (the system game counter and the view-options panel) was low-contrast in dark mode; on-tile secondary text now uses a near-opaque light color instead of the dim gray.
- **Dual-screen tab icons** — non-game Home tabs (Favorites, Recent, Apps, RetroAchievements, Friends) now mirror their icon on the top panel, matching how the Settings gear already behaved.
- **Bottom-panel dim on launch** — on dual-screen devices, when a game opens on the top panel the bottom screen stays running but dims to ~18% brightness (animated, fading back on return) to push focus to the game.

## Locked Mode — thanks @picodspi 🙏
- **System-navigation blocking** — Locked Mode can now block system navigation using an embedded privilege broker and a Wireless ADB pairing flow, so kiosk mode is much harder to escape.
- Navigation-lock **setup and status are exposed in Settings**.
- System navigation is **restored when Locked Mode is unlocked**, and active Locked Mode state is **reset after a device reboot**.
- Adds security and navigation-lock test coverage, and documents Shizuku provenance and third-party licensing.

## Fixes
- **Grid selection could fall off-screen** — with tall arcade art at a large grid size, where barely one full row fits, the focus-scroll anchored the selected card to the second visible row and pushed it off the bottom edge. eOr now measures how many whole rows actually fit and pins the selected card's row to the top when fewer than two do.

## Downloads
- **eOr-v2.5.1-full.apk** — for everything else
- **eOr-v2.5.1-lite.apk** — for low-power handhelds / weak chipsets (RG DS RK3568 chipset and similar)

Both APKs are signed with the same key as previous releases, so you can update in place.
