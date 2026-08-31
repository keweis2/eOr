## Highlights  <!-- DRAFT — review before publishing -->

A performance-and-polish release. Box art now loads in one sweep instead of trickling in card-by-card, Settings has been reorganized into a cleaner index-and-drill-in layout, and there's a round of under-the-hood security hardening. Happy gaming :)

## Performance
- **Box art loads together, not one-at-a-time** — the grid now brings artwork in as a batch instead of popping in card-by-card, so the home screen settles faster and looks steadier while scrolling.
- **Smoother focused-card motion** — the idle motion on the focused card now defers until box art has loaded, and its start delay was tuned, so artwork appears before the animation kicks in (matching the lite build's feel).

## Settings
- **Reorganized into an index + drill-in** — the old single long settings screen is now a tidy index that drills into per-category screens, so options are easier to find.

## Security & hardening
- **Hardened credential storage, file sharing, sync trust, and LAN discovery** — credentials are better protected at rest, file sharing is more tightly scoped, sync trust handling is stricter, and the nearby-device beacon is sanitized.

## Downloads
- **eOr-v2.6.3-full.apk** — for everything else
- **eOr-v2.6.3-lite.apk** — for low-power handhelds / weak chipsets (RG DS RK3568 chipset and similar)

Both APKs are signed with the same key as previous releases, so you can update in place.
