#!/usr/bin/env python3
"""Renders a small preview (<id>.jpg) for every theme in index.json and records it there.

The preview is a mini dark-mode home screen drawn the way eOr draws it: the theme's background
(its wallpaper, dimmed and blurred, when it has one; otherwise the colour plus glows), tab chips,
and a row of tiles with the first one focused. Run from the repo root after adding a theme:

    python3 themes/make_previews.py

Needs Pillow (pip install pillow).
"""
import colorsys, io, json, os, zipfile
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
W, H, SS = 400, 225, 3                     # output size, supersampling for smooth edges

RAINBOW = ["#4FB7F5", "#7C8CFF", "#B07BFF", "#FF7AA8", "#FF9F66", "#FFC04D", "#3FD3A6", "#53CFE0"]
GREYS = ["#B8BDC7", "#9AA0AC", "#C5CAD3", "#868D9B", "#AEB4BF", "#757C8A", "#CFD3DB", "#9199A6"]
NAVY = {"background": "#111318", "chip": "#1A2448", "chipBorder": "#2E3B68"}
OLED = {"background": "#000000", "chip": "#111214", "chipBorder": "#2A2B2F"}
GLOW_SLOTS = [(0.18, 0.08, 0.02, 0.95), (0.15, 0.98, 0.08, 1.05), (0.10, 0.55, 1.05, 1.05)]


def rgb(h):
    h = h.lstrip("#")[-6:]
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def mono_palette(seed):
    h, s, v = colorsys.rgb_to_hsv(*(c / 255 for c in rgb(seed)))
    if s < 0.05:
        return [rgb(c) for c in GREYS]
    out = []
    for i in range(8):
        t = i / 7
        r, g, b = colorsys.hsv_to_rgb(h, 0.80 + (0.42 - 0.80) * t, 0.96 + (0.60 - 0.96) * t)
        out.append((round(r * 255), round(g * 255), round(b * 255)))
    return out


def load(path):
    with zipfile.ZipFile(path) as z:
        t = json.loads(z.read("theme.json"))
        wp = t.get("wallpaper")
        img = Image.open(io.BytesIO(z.read(wp["image"]))).convert("RGB") if wp else None
    return t, img


def render(t, wallpaper):
    w, h = W * SS, H * SS
    accent = rgb(t["accent"]); accent2 = rgb(t.get("accent2", t["accent"])); accent3 = rgb(t.get("accent3", t["accent"]))
    bgspec = t.get("darkBackground")
    surf = dict(OLED if bgspec in ("oled", "black") else NAVY)
    if isinstance(bgspec, dict):
        surf.update({k: v for k, v in bgspec.items() if k in surf})
    bg = rgb(surf["background"])

    img = Image.new("RGB", (w, h), bg)
    wp = t.get("wallpaper")
    if wallpaper is not None and wp:
        s = max(w / wallpaper.width, h / wallpaper.height)
        photo = wallpaper.resize((round(wallpaper.width * s), round(wallpaper.height * s)), Image.LANCZOS)
        photo = photo.crop(((photo.width - w) // 2, (photo.height - h) // 2, (photo.width - w) // 2 + w, (photo.height - h) // 2 + h))
        if wp.get("blur", 0.25) > 0.01:
            photo = photo.filter(ImageFilter.GaussianBlur(wp.get("blur", 0.25) * 10 * SS))
        img = Image.blend(photo, img, wp.get("dimDark", 0.55))
        glows = [accent, accent2, accent3] if wp.get("glows") else []
    else:
        glows = [rgb(c) for c in t["darkGlows"]] if "darkGlows" in t else ([] if bgspec in ("oled", "black") else [accent, accent2, accent3])
    for (alpha, cx, cy, r), col in zip(GLOW_SLOTS, glows):
        layer = Image.new("L", (w, h), 0)
        rad = min(w, h) * r
        # A radial gradient fades out over its radius, so a blurred disc needs less strength to match.
        ImageDraw.Draw(layer).ellipse([w * cx - rad, h * cy - rad, w * cx + rad, h * cy + rad], fill=round(255 * alpha * 0.55))
        img = Image.composite(Image.new("RGB", (w, h), col), img, layer.filter(ImageFilter.GaussianBlur(rad / 2.2)))

    d = ImageDraw.Draw(img)
    u = SS
    # Tab chips: the selected one in the accent.
    x = 16 * u
    for i, cw in enumerate((62, 72, 58)):
        box = [x, 18 * u, x + cw * u, 40 * u]
        if i == 0:
            d.rounded_rectangle(box, radius=11 * u, fill=accent)
        else:
            d.rounded_rectangle(box, radius=11 * u, fill=rgb(surf["chip"]), outline=rgb(surf["chipBorder"]), width=u)
        x += (cw + 8) * u
    # Tiles: first one focused (full colour + outline), the rest darkened like dark mode does.
    tiles = t.get("tiles", "accent").lower()
    palette = ([rgb(c) for c in RAINBOW] if tiles == "rainbow" else [rgb(c) for c in GREYS] if tiles in ("grey", "gray", "bw", "black-white")
               else mono_palette(t["accent"] if tiles == "accent" else tiles))
    focus = rgb(t.get("focus", {}).get("dark", t["accent"]))
    tw, gap, ty = 82, 10, 128
    for i in range(4):
        x0 = (16 + i * (tw + gap)) * u
        col = palette[i % len(palette)]
        focused = i == 0
        fill = lerp(col, (0, 0, 0), 0.10 if focused else 0.55)
        box = [x0, ty * u, x0 + tw * u, (ty + 80) * u]
        d.rounded_rectangle(box, radius=12 * u, fill=fill, outline=focus if focused else lerp(fill, (255, 255, 255), 0.15), width=(3 if focused else 1) * u)
    return img.resize((W, H), Image.LANCZOS)


def main():
    index_path = os.path.join(HERE, "index.json")
    index = json.load(open(index_path))
    for e in index["themes"]:
        t, wallpaper = load(os.path.join(HERE, e["file"]))
        name = e["id"] + ".jpg"
        render(t, wallpaper).save(os.path.join(HERE, name), quality=82, optimize=True, progressive=True)
        e["preview"] = name
        print("rendered", name)
    open(index_path, "w").write(json.dumps(index, indent=2) + "\n")


if __name__ == "__main__":
    main()
