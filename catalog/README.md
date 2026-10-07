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

Invalid entries are dropped individually on-device (logged under the `Catalog` tag); an unknown
`schemaVersion` makes the app ignore the file entirely.

To regenerate the file from the built-in data (overwrites manual edits):
`EOR_EXPORT_CATALOG=1 ./gradlew :app:testFullDebugUnitTest --tests '*PlatformCatalogTest*'`
