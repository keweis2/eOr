## Highlights

A short polish week focused on touch-first users and a friendlier first launch — a back button on the game grid, smarter onboarding nudges when your library comes up empty, and more Locked Mode navigation hardening from **@picodspi**. Thanks for the feedback — keep it coming. Happy gaming :)

## Home screen
- **Back button on the game grid** — eOr is controller-first, but touch users had no way back to the console list once inside a system (the back action lived only on the controller's B button). There's now a back button at the top-left of the grid header, matching the game detail screen, so a tap takes you back to your systems.

## First launch & onboarding
- **Empty-library nudges** — if you reach the end of first-run setup with **no games** or **no emulators** detected, Otto now shows a friendly tip on how to fix it: add ROM files and rescan, or install an emulator (RetroArch covers most systems) and eOr will auto-assign it. Nothing is blocked — it's just a pointer in the right direction.
- **Rewritten First Launch Setup guide** — the README now walks through the actual Otto-guided setup (Welcome → Find your games → Theme → Building your arcade), with a **Before you start** checklist (install emulators, gather ROMs, optional ScreenScraper account) and an **After setup** section (rescan/scrape new ROMs, fine-tune emulators, validate credentials).

## Locked Mode — thanks @picodspi 🙏
- **Warn before first system-navigation block** — Locked Mode now warns you before it blocks system navigation for the first time, so kiosk setup is less surprising.
- **Navigation-blocking is revealed only when developer options is active**, keeping the option out of the way until it's usable.

## Downloads
- **eOr-v2.5.2-full.apk** — for everything else
- **eOr-v2.5.2-lite.apk** — for low-power handhelds / weak chipsets (RG DS RK3568 chipset and similar)

Both APKs are signed with the same key as previous releases, so you can update in place.
