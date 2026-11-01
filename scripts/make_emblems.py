#!/usr/bin/env python3
"""Generates the built-in emblem textures (white on transparent, tinted in game). Run: python3 scripts/make_emblems.py"""
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageChops

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/aureole/textures/emblem"
S = 2048
C = S / 2


def canvas():
    return Image.new("L", (S, S), 0)


def ring(d, r, w):
    d.ellipse((C - r, C - r, C + r, C + r), outline=255, width=int(w))


def poly(d, pts, fill=255):
    d.polygon([(C + x, C + y) for x, y in pts], fill=fill)


def polar(r, a):
    return (r * math.cos(a), r * math.sin(a))


def finish(mask, name, glow=0.45):
    halo = mask.filter(ImageFilter.GaussianBlur(S * 0.012))
    halo = halo.point(lambda v: int(v * glow))
    alpha = ImageChops.lighter(mask, halo)
    img = Image.new("RGBA", (S, S), (255, 255, 255, 0))
    img.putalpha(alpha)
    img = img.resize((512, 512), Image.LANCZOS)
    img.save(OUT / f"{name}.png", optimize=True)


def sigil():
    m = canvas(); d = ImageDraw.Draw(m)
    ring(d, 960, 22); ring(d, 905, 8); ring(d, 640, 14); ring(d, 600, 6); ring(d, 250, 12)
    # rune band between 640 and 905
    for i in range(48):
        a = i / 48 * 2 * math.pi
        r0, r1 = 680, 860
        kind = i % 4
        if kind == 0:
            poly(d, [polar(r0, a - 0.012), polar(r1, a - 0.012), polar(r1, a + 0.012), polar(r0, a + 0.012)])
        elif kind == 1:
            poly(d, [polar(770, a), polar(830, a + 0.04), polar(770, a + 0.08), polar(710, a + 0.04)])
        elif kind == 2:
            for rr in (720, 820):
                x, y = polar(rr, a)
                d.ellipse((C + x - 14, C + y - 14, C + x + 14, C + y + 14), fill=255)
        else:
            d.line([tuple(map(sum, zip((C, C), polar(r0 + 20, a)))), tuple(map(sum, zip((C, C), polar(r1 - 20, a + 0.05))))], fill=255, width=10)
    # 8-point star (two squares)
    for off in (0, math.pi / 4):
        pts = [polar(600, off + k * math.pi / 2) for k in range(4)]
        d.line([(C + x, C + y) for x, y in pts + [pts[0]]], fill=255, width=12, joint="curve")
    for k in range(8):
        a = k * math.pi / 4
        poly(d, [polar(250, a - 0.18), polar(420, a), polar(250, a + 0.18)])
    finish(m, "sigil")


def sunburst():
    m = canvas(); d = ImageDraw.Draw(m)
    d.ellipse((C - 300, C - 300, C + 300, C + 300), fill=255)
    ring(d, 360, 14)
    for i in range(32):
        a = i / 32 * 2 * math.pi
        long = i % 2 == 0
        r1 = 980 if long else 760
        wdt = 0.06 if long else 0.045
        poly(d, [polar(400, a - wdt), polar(r1, a), polar(400, a + wdt)])
    finish(m, "sunburst")


def compass():
    m = canvas(); d = ImageDraw.Draw(m)
    ring(d, 960, 16); ring(d, 880, 6)
    for i in range(72):
        a = i / 72 * 2 * math.pi
        r0 = 820 if i % 6 == 0 else 850
        d.line([tuple(map(sum, zip((C, C), polar(r0, a)))), tuple(map(sum, zip((C, C), polar(880, a))))], fill=255, width=8)
    for k in range(4):
        a = k * math.pi / 2 - math.pi / 2
        poly(d, [polar(0, 0), polar(160, a - math.pi / 4), polar(800, a), polar(160, a + math.pi / 4)])
    for k in range(4):
        a = k * math.pi / 2 - math.pi / 4
        poly(d, [polar(0, 0), polar(110, a - math.pi / 4), polar(520, a), polar(110, a + math.pi / 4)], fill=170)
    d.ellipse((C - 70, C - 70, C + 70, C + 70), fill=0)
    ring(d, 70, 14)
    finish(m, "compass")


def lotus():
    m = canvas()
    for n, L, Wd, off, fill in [(12, 930, 200, math.pi / 12, 70), (8, 700, 230, 0.0, 120)]:
        for i in range(n):
            a = i / n * 2 * math.pi + off
            petal = Image.new("L", (S, S), 0)
            pd = ImageDraw.Draw(petal)
            pd.ellipse((C - Wd, C - L, C + Wd, C - 120), fill=fill, outline=255, width=16)
            pd.line([(C, C - 160), (C, C - L + 120)], fill=255, width=8)
            petal = petal.rotate(-math.degrees(a), center=(C, C), resample=Image.BICUBIC)
            m = ImageChops.lighter(m, petal)
    d = ImageDraw.Draw(m)
    d.ellipse((C - 200, C - 200, C + 200, C + 200), fill=0)
    ring(d, 200, 16)
    for k in range(12):
        x, y = polar(130, k / 12 * 2 * math.pi)
        d.ellipse((C + x - 22, C + y - 22, C + x + 22, C + y + 22), fill=255)
    d.ellipse((C - 55, C - 55, C + 55, C + 55), fill=255)
    finish(m, "lotus")


def snowflake():
    m = canvas(); d = ImageDraw.Draw(m)
    def seg(r0, a0, r1, a1, w):
        d.line([tuple(map(sum, zip((C, C), polar(r0, a0)))), tuple(map(sum, zip((C, C), polar(r1, a1))))], fill=255, width=w)
    for k in range(6):
        a = k * math.pi / 3
        seg(0, a, 940, a, 34)
        for t, ln in ((0.35, 260), (0.6, 220), (0.82, 140)):
            base = 940 * t
            bx, by = polar(base, a)
            for s in (-1, 1):
                ex, ey = bx + ln * math.cos(a + s * math.pi / 3.2), by + ln * math.sin(a + s * math.pi / 3.2)
                d.line([(C + bx, C + by), (C + ex, C + ey)], fill=255, width=26)
    ring(d, 200, 22)
    finish(m, "snowflake")


def crescent():
    m = canvas(); d = ImageDraw.Draw(m)
    d.ellipse((C - 760, C - 760, C + 760, C + 760), fill=255)
    d.ellipse((C - 520, C - 860, C + 900, C + 560), fill=0)
    def star(cx, cy, r):
        pts = []
        for k in range(10):
            rr = r if k % 2 == 0 else r * 0.42
            a = -math.pi / 2 + k * math.pi / 5
            pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
        d.polygon(pts, fill=255)
    star(C + 380, C - 120, 150); star(C + 140, C + 260, 80); star(C + 520, C + 240, 60)
    ring(d, 980, 10)
    finish(m, "crescent")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for fn in (sigil, sunburst, compass, lotus, snowflake, crescent):
        fn()
    print("ok")
