#!/usr/bin/env python3
"""Turns raw in-game screenshots into the README hero photo and gallery images.

Usage: python3 scripts/hero_image.py <screenshots_dir> <out_dir>

Pure post-processing of real gameplay captures: cinematic crop, soft bloom, gentle grade, vignette.
No text, no logos, no fake effects.
"""
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageFilter


def find(shots: Path, name: str) -> Path:
    matches = sorted(shots.glob(f"*_{name}.png"))
    if not matches:
        raise SystemExit(f"missing screenshot: {name}")
    return matches[-1]


def bloom(img: Image.Image, threshold: int = 170, radius: int = 18, strength: float = 0.55) -> Image.Image:
    lum = img.convert("L").point(lambda v: 255 if v > threshold else int(v * v / (threshold * 1.6)))
    bright = ImageChops.multiply(img, Image.merge("RGB", [lum] * 3))
    glow = bright.filter(ImageFilter.GaussianBlur(radius))
    glow = ImageEnhance.Brightness(glow).enhance(strength)
    return ImageChops.screen(img, glow)


def vignette(img: Image.Image, strength: float = 0.55) -> Image.Image:
    w, h = img.size
    mask = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(mask)
    d.ellipse((-w * 0.18, -h * 0.25, w * 1.18, h * 1.25), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(min(w, h) * 0.18))
    dark = ImageEnhance.Brightness(img).enhance(1.0 - strength)
    return Image.composite(img, dark, mask)


def grade(img: Image.Image, warmth: float = 1.0) -> Image.Image:
    img = ImageEnhance.Contrast(img).enhance(1.08)
    img = ImageEnhance.Color(img).enhance(1.12)
    r, g, b = img.split()
    r = r.point(lambda v: min(255, int(v * (1.0 + 0.04 * warmth))))
    b = b.point(lambda v: int(v * (1.0 - 0.03 * warmth)))
    return Image.merge("RGB", (r, g, b))


def crop_center(img: Image.Image, aspect: float, zoom: float = 1.0, dy: float = 0.0) -> Image.Image:
    w, h = img.size
    cw = w / zoom
    ch = cw / aspect
    if ch > h / zoom:
        ch = h / zoom
        cw = ch * aspect
    cx, cy = w / 2, h / 2 + dy * h
    box = (int(cx - cw / 2), int(cy - ch / 2), int(cx + cw / 2), int(cy + ch / 2))
    return img.crop(box)


def polish(src: Path, aspect: float, zoom: float, dy: float, out_w: int, warmth: float = 1.0) -> Image.Image:
    img = Image.open(src).convert("RGB")
    img = crop_center(img, aspect, zoom, dy)
    img = img.resize((out_w, int(out_w / aspect)), Image.LANCZOS)
    img = bloom(img)
    img = grade(img, warmth)
    img = vignette(img)
    return img


def crop_box(img: Image.Image, box, out_w: int) -> Image.Image:
    w, h = img.size
    c = img.crop((int(box[0] * w), int(box[1] * h), int(box[2] * w), int(box[3] * h)))
    return c.resize((out_w, int(out_w * c.size[1] / c.size[0])), Image.LANCZOS)


def main() -> None:
    shots, out = Path(sys.argv[1]), Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)

    polish(find(shots, "hero_selfie"), 2.0, 1.0, 0.0, 1600).save(out / "hero.png", optimize=True)
    polish(find(shots, "scan_trail_1"), 16 / 9, 1.45, 0.05, 1280, warmth=-0.5).save(out / "scan.png", optimize=True)
    polish(find(shots, "classic_first_person"), 16 / 9, 1.0, 0.0, 1280).save(out / "classic.png", optimize=True)
    polish(find(shots, "feature_text_orbit"), 16 / 9, 1.35, 0.1, 1280).save(out / "text.png", optimize=True)
    polish(find(shots, "feature_text_engraved"), 16 / 9, 1.35, 0.12, 1280).save(out / "text_engraved.png", optimize=True)
    polish(find(shots, "feature_emblem_floor"), 16 / 9, 1.3, 0.12, 1280).save(out / "emblem.png", optimize=True)
    Image.open(find(shots, "gui_tab_0")).convert("RGB").resize((1280, 720), Image.LANCZOS).save(out / "menu.png", optimize=True)
    Image.open(find(shots, "gui_trail_colors")).convert("RGB").resize((1280, 720), Image.LANCZOS).save(out / "menu_colours.png", optimize=True)
    Image.open(find(shots, "gui_tab_6")).convert("RGB").resize((1280, 720), Image.LANCZOS).save(out / "menu_emblem.png", optimize=True)
    crop_box(Image.open(find(shots, "pause_menu")).convert("RGB"), (0.38, 0.36, 0.62, 0.62), 720).save(out / "pause.png", optimize=True)

    tiles = out / "gallery"
    tiles.mkdir(exist_ok=True)
    for src in sorted(shots.glob("*.png")):
        name = src.stem.split("_", 1)[1]
        if name.startswith(("preset_", "style_", "scan_trail_", "extras_", "feature_")):
            polish(src, 4 / 3, 1.55, 0.03, 480).save(tiles / f"{name}.jpg", quality=88)
        elif name.startswith("gui_"):
            Image.open(src).convert("RGB").resize((960, 540), Image.LANCZOS).save(tiles / f"{name}.jpg", quality=90)


if __name__ == "__main__":
    main()
