# eOr themes

The theme gallery in eOr (Settings → Appearance → **Get more themes**) lists the themes in this
folder. Merging a theme to `main` publishes it — no app release needed.

## Make a theme

A theme is a `.eortheme` file: a zip containing one `theme.json`. Only `name` and `accent` are
required; everything else is derived from the accent if you leave it out.

```json
{
  "format": "eor-theme",
  "schemaVersion": 1,
  "name": "Synthwave",
  "author": "you",
  "accent": "#FF2E97",
  "accent2": "#7A5CFF",
  "accent3": "#00E5FF",
  "darkBackground": "navy",
  "tiles": "accent"
}
```

| Field | Values |
|---|---|
| `accent`, `accent2`, `accent3` | `#RRGGBB` — highlights, gradient buttons, small highlights |
| `dark`, `light` | optional Material primary tones: `primary`, `onPrimary`, `primaryContainer`, `onPrimaryContainer` |
| `darkBackground` | `"navy"` (default), `"oled"` (true black), or an object: `background`, `surface`, `card`, `border`, `containerLowest`, `containerLow`, `container`, `containerHigh`, `containerHighest`, `outlineVariant`, `chip`, `chipBorder` |
| `darkGlows`, `lightGlows` | up to 4 colours for the soft background glows (`[]` = none) |
| `tiles` | `"accent"` (default), `"rainbow"`, `"grey"`, or a `#RRGGBB` tile colour |
| `focus` | `{"dark": "#…", "light": "#…"}` — outline on the selected item |

The quickest start: in eOr, pick a theme close to what you want and use **Export current** — you
get a complete `.eortheme` to edit. Import it back with **Import theme**, or drag it onto the
Themes tab in Web Transfer.

## Add it to the gallery

1. Put `<id>.eortheme` in this folder (`id`: lowercase letters, digits and dashes).
2. Add an entry to `index.json` with `id`, `name`, `author`, `file` (`<id>.eortheme`) and the
   swatch colours `accent`, `accent2`, `background` (the theme's dark background).
3. Run `./gradlew :app:testFullDebugUnitTest --tests '*ThemeGallery*'` — it checks every entry
   has a matching file that eOr can load.
