# Platform catalog

`catalog.json` is downloaded by eOr (at most once a day) from
`https://raw.githubusercontent.com/keweis2/eOr/main/catalog/catalog.json`, so changes here reach
users **when they merge to `main`** — no app release needed.

It's layered on top of the data compiled into the app (`PlatformDefinitions.kt`,
`BuiltInEmulators.kt`). It can **add and adjust, never remove**: extensions and folder names are
unioned with the built-in ones, and built-in platforms/emulators always stay.

## Making a change

1. Edit `catalog.json` and **bump `revision`** (devices never step back to a lower revision).
2. Run `./gradlew :app:testFullDebugUnitTest --tests '*PlatformCatalogTest*'` — it fails if any entry
   would be rejected on-device or would change a built-in launch recipe.
3. For a new emulator launch recipe, launch a real game with it on a release build before merging.

| Field | Notes |
|---|---|
| `platforms[].id` | lowercase, `a-z0-9_-`; matches an existing id to extend that platform |
| `extensions` | lowercase with leading dot: `.sfc`, `.nkit.iso` |
| `folderNames` | ROM folder names to recognise (matched case-insensitively) |
| `scraperSystemId` | ScreenScraper system id (`0` = none) |
| `retroArchCore` | e.g. `snes9x_libretro.so` |
| `emulators` | auto-detect order (package names); first installed wins |
| `emulators[].launch` | `activity` (required), `action` `VIEW`/`MAIN`, `romExtraKey`, `mimeType`, `romUri` `FILE`/`CONTENT` |
| `minAppVersionCode` | older apps ignore the whole catalog — use when adding a field old apps would misread |
| presentation | `icon`, `label`, `pad`, `coverAspect`, `releaseYear`, `brand`, `kind` — see below |
| artwork | `libretroThumbnails`, `launchBoxPlatform`, `esdeDirs` |
| `detectByExtension` | let loose files match by extension (default off for added systems) |

Invalid entries are dropped individually on-device (logged under the `Catalog` tag); an unknown
`schemaVersion` makes the app ignore the file entirely.

## Adding a system

New systems need presentation data too, or they show a generic controller and sort last — the
test enforces this for every system the catalog adds:

- `icon` — a key from the bundled icon set (`iconByKey` in `PlatformVisuals.kt`). If nothing fits,
  add a drawable in the app first; the catalog can't ship images.
- `label`, `pad` (`nes`/`handheld`/`arcade`/`gamepad`), `releaseYear`, `brand`, `kind`
  (`console`/`handheld`/`arcade`/`computer`/`mobile`/`other`), optional `coverAspect`.
- `retroArchCore` that exists on the Android buildbot, and `emulators` for auto-detect.
- Optional artwork fallbacks: `libretroThumbnails` (folder on thumbnails.libretro.com),
  `launchBoxPlatform`, `esdeDirs`.

Catalog-added systems match **by folder only** unless `detectByExtension: true` — keeps generic
extensions (`.dat`, `.exe`, `.img`) from turning stray files into games. They also can't claim a
folder name another system already owns.

If a new field or system needs newer app code, raise `minAppVersionCode` so older apps keep the
previous revision instead.
