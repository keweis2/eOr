# eOr themes

The theme gallery in eOr (Settings → Appearance → **Get more themes**) lists the themes in this
folder. Merging a theme to `main` publishes it — no app release needed.

## Make a theme

A theme is a `.eortheme` file: a zip containing one `theme.json`, plus an optional background
image. Only `name` and `accent` are required; everything else is derived from the accent if you
leave it out. Keep wallpapers around 1920×1080 and the whole file under 1 MB (eOr shrinks larger
images on import, and older eOr versions only accept files up to 1 MB — they ignore the image).

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
| `wallpaper` | optional background photo: `{"image": "wallpaper.jpg", "dimDark": 0.55, "dimLight": 0.35, "blur": 0.25, "glows": false}` — `image` is a JPEG/PNG/WebP in the same zip; `dim*` (0–1) is how much plain background colour covers it so text stays readable; `blur` is 0–1; `glows` keeps the colour glows on top |

The quickest start: in eOr, pick a theme close to what you want, tap **Customise** (or **Edit**)
to change its colours in the theme editor, add a background with **Choose image** if you like,
then use **Export current** — you get a complete `.eortheme`. Import one with **Import theme**,
or drag it onto the Themes tab in Web Transfer.

## Add it to the gallery

1. Put `<id>.eortheme` in this folder (`id`: lowercase letters, digits and dashes).
2. Add an entry to `index.json` with `id`, `name`, `author`, `file` (`<id>.eortheme`) and the
   swatch colours `accent`, `accent2`, `background` (the theme's dark background).
3. Run `python3 themes/make_previews.py` (needs Pillow) — it renders the small preview shown in
   the gallery (`<id>.jpg`) for every theme and adds `preview` to `index.json`.
4. Run `./gradlew :app:testFullDebugUnitTest --tests '*ThemeGallery*'` — it checks every entry
   has a matching file that eOr can load.
