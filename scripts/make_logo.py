#!/usr/bin/env python3
"""Draws the 16x16 pixel-art Aureole logo and writes it at 16px and upscaled (nearest neighbour, so the pixels
stay crisp) for the mod icon and the Modrinth project icon.

Usage: python3 scripts/make_logo.py
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent

# b background   R ring   C sparkle core   S sparkle arm   s faint sparkle tip
GRID = [
    "bbbbbbbbbbbbbbbb",
    "bbbbbbbbbbbbsbbb",
    "bbbbbbbbbbbbSbbb",
    "bbbbbbbbbbbSCSbb",
    "bbbbbbbbbbbbSbbb",
    "bbbbbbbbbbbbsbbb",
    "bbbbbRRRRRRbbbbb",
    "bbbRRbbbbbbRRbbb",
    "bbRbbbbbbbbbbRbb",
    "bbbRRbbbbbbRRbbb",
    "bbbbbRRRRRRbbbbb",
    "bbbbSbbbbbbbbbbb",
    "bbbSCSbbbbbbbbbb",
    "bbbbSbbbbbbsbbbb",
    "bbbbbbbbbbbbbbbb",
    "bbbbbbbbbbbbbbbb",
]

BG_TOP = (36, 33, 72)
BG_BOTTOM = (24, 22, 46)
GOLD = {
    "R": (247, 223, 166),
    "C": (255, 248, 222),
    "S": (240, 205, 140),
    "s": (150, 128, 100),
}
GLOW = (214, 170, 104)


def lerp(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def draw() -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    ring = [(x, y) for y, row in enumerate(GRID) for x, ch in enumerate(row) if ch == "R"]
    for y, row in enumerate(GRID):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if ch in GOLD:
                img.putpixel((x, y), GOLD[ch] + (255,))
                continue
            c = lerp(BG_TOP, BG_BOTTOM, y / 15)
            # a faint one-pixel glow on the outside of the ring; the middle of the halo stays dark
            d = min(abs(x - rx) + abs(y - ry) for rx, ry in ring)
            inside = 7 <= y <= 9 and 3 <= x <= 12 and not (y != 8 and x in (3, 12))
            if d == 1 and not inside:
                c = lerp(c, GLOW, 0.2)
            img.putpixel((x, y), c + (255,))
    return img


def main() -> None:
    logo = draw()
    logo.save(ROOT / "docs/modrinth/icon_16.png")
    logo.resize((512, 512), Image.NEAREST).save(ROOT / "docs/modrinth/icon.png")
    logo.resize((128, 128), Image.NEAREST).save(ROOT / "src/main/resources/assets/aureole/icon.png")


if __name__ == "__main__":
    main()
