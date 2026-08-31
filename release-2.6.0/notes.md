## Highlights

Your emulators finally get the same update love eOr gives itself. This release integrates **Obtainium** so eOr can surface available **emulator updates** right in Settings and help you install the ones you're missing. Plus **ChuckStation3 (PS3)** support from **@aarvsn**, live library syncing while eOr is running from **@picodspi**, and a nasty Steam-library scan loop squashed. Thanks to everyone sending PRs and feedback — happy gaming :)

## Emulator updates (new)
- **See when your emulators are out of date** — eOr now checks your installed, GitHub-tracked emulators and surfaces available updates in **Settings → Games → Emulators**, with a launch banner and (optional) notification when something's behind.
- **One tap to update** — the per-emulator **Update** button hands off to **Obtainium**, the community-standard updater for off-store emulators, so you update in place with the tool built for it. If Obtainium isn't installed, eOr points you to it.
- **Install what you're missing** — eOr can hand Obtainium the essential emulators you don't have yet, using the RJNY Obtainium Emulation Pack as the source-of-truth mapping (fetched latest, with a bundled fallback).
- **Update notifications toggle** — a new switch under *Check for updates* (default on) lets you silence the launch banner and system notification while keeping the manual check.
- **Gentler on GitHub's rate limit** — update checks now use cached ETags (unchanged releases cost nothing against the limit), fall back to the last-known version on a rate-limit hit instead of dropping the emulator, and the Settings card only hits the network on an explicit **Check for updates** tap.

## New system support
- **ChuckStation3 (PS3)** — thanks **@aarvsn** 🙏 — full platform support for the ChuckStation3 PS3 emulator: detection, launch, and save-location handling.

## Library scanning
- **Keeps up while eOr is running** — thanks **@picodspi** 🙏 — eOr now detects games added while it's open (e.g. an FTP upload) and refreshes the library instead of only scanning at startup.
- **Steam scan-loop fixed** — for anyone with a Steam library, that foreground refresh was re-running the full ROM scan every 30 seconds and deleting/re-adding Steam games on a loop. Steam entries are now correctly excluded from disk-change detection and from scan cleanup, so the loop is gone and a real scan can never wipe your Steam library. On lite builds the refresh now runs once per foreground entry instead of constant polling.
- **Steadier artwork & path handling** — embedded ROM artwork retries when it's missing, removed ROM paths are reconciled instead of lingering, and scans wait for in-progress ROM uploads to settle.

## Downloads
- **eOr-v2.6.0-full.apk** — for everything else
- **eOr-v2.6.0-lite.apk** — for low-power handhelds / weak chipsets (RG DS RK3568 chipset and similar)

Both APKs are signed with the same key as previous releases, so you can update in place.
