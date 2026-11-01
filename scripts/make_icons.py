#!/usr/bin/env python3
"""Generates the built-in nametag icons (white on transparent, tinted in game). Run: python3 scripts/make_icons.py"""
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/angelichalo/textures/icon"
S = 512
C = S / 2


def new():
    return Image.new("L", (S, S), 0)


def star_pts(cx, cy, r, inner, n=5, rot=-math.pi / 2):
    pts = []
    for k in range(n * 2):
        rr = r if k % 2 == 0 else r * inner
        a = rot + k * math.pi / n
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    return pts


def save(m, name):
    img = Image.new("RGBA", (S, S), (255, 255, 255, 0))
    img.putalpha(m)
    img.resize((64, 64), Image.LANCZOS).save(OUT / f"{name}.png", optimize=True)


def crown():
    m = new(); d = ImageDraw.Draw(m)
    d.polygon([(70, 400), (70, 170), (165, 285), (256, 120), (347, 285), (442, 170), (442, 400)], fill=255)
    d.rectangle((70, 410, 442, 450), fill=255)
    for x in (70, 256, 442):
        d.ellipse((x - 30, 100 if x == 256 else 145, x + 30, 160 if x == 256 else 205), fill=255)
    save(m, "crown")


def star():
    m = new(); ImageDraw.Draw(m).polygon(star_pts(C, C + 15, 230, 0.45), fill=255); save(m, "star")


def heart():
    m = new(); d = ImageDraw.Draw(m)
    d.ellipse((50, 90, 270, 310), fill=255); d.ellipse((242, 90, 462, 310), fill=255)
    d.polygon([(62, 240), (450, 240), (256, 460)], fill=255); save(m, "heart")


def sword():
    m = new(); d = ImageDraw.Draw(m)
    d.polygon([(400, 60), (450, 60), (450, 110), (190, 370), (140, 320)], fill=255)
    d.polygon([(110, 290), (220, 400), (195, 425), (85, 315)], fill=255)
    d.line([(150, 360), (75, 435)], fill=255, width=40)
    d.ellipse((45, 420, 95, 470), fill=255); save(m, "sword")


def shield():
    m = new(); d = ImageDraw.Draw(m)
    d.polygon([(256, 50), (440, 110), (420, 300), (256, 465), (92, 300), (72, 110)], fill=255)
    d.polygon([(256, 110), (380, 150), (366, 290), (256, 400)], fill=150); save(m, "shield")


def gem():
    m = new(); d = ImageDraw.Draw(m)
    d.polygon([(130, 90), (382, 90), (470, 200), (256, 450), (42, 200)], fill=255)
    d.line([(42, 200), (470, 200)], fill=120, width=14)
    d.line([(190, 90), (256, 450), (322, 90)], fill=120, width=12); save(m, "gem")


def flame():
    m = new(); d = ImageDraw.Draw(m)
    d.ellipse((110, 220, 402, 470), fill=255)
    d.polygon([(130, 320), (256, 30), (382, 320)], fill=255)
    d.ellipse((190, 300, 322, 440), fill=110)
    d.polygon([(200, 360), (256, 230), (312, 360)], fill=110); save(m, "flame")


def moon():
    m = new(); d = ImageDraw.Draw(m)
    d.ellipse((60, 60, 452, 452), fill=255); d.ellipse((170, 20, 520, 370), fill=0); save(m, "moon")


def bolt():
    m = new(); ImageDraw.Draw(m).polygon([(300, 30), (110, 290), (240, 290), (190, 482), (402, 210), (270, 210), (340, 30)], fill=255)
    save(m, "bolt")


def sparkle():
    m = new(); d = ImageDraw.Draw(m)
    d.polygon(star_pts(C, C, 240, 0.18, 4, 0), fill=255)
    d.polygon(star_pts(400, 110, 70, 0.25, 4, 0), fill=255); save(m, "sparkle")


def leaf():
    m = new(); d = ImageDraw.Draw(m)
    a, b = (95, 430), (430, 85)
    mx, my = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
    dx, dy = b[0] - a[0], b[1] - a[1]
    nx, ny = -dy, dx
    pts = []
    for side in (1, -1):
        rng = range(0, 41) if side == 1 else range(40, -1, -1)
        for i in rng:
            t = i / 40
            bulge = math.sin(math.pi * t) * 0.32 * side
            pts.append((a[0] + dx * t + nx * bulge, a[1] + dy * t + ny * bulge))
    d.polygon(pts, fill=255)
    d.line([a, (mx + dx * 0.35, my + dy * 0.35)], fill=110, width=14)
    d.line([(a[0] - 30, a[1] + 40), a], fill=255, width=26)
    save(m, "leaf")


def skull():
    m = new(); d = ImageDraw.Draw(m)
    d.ellipse((80, 50, 432, 380), fill=255); d.rectangle((150, 300, 362, 450), fill=255)
    d.ellipse((140, 170, 230, 270), fill=0); d.ellipse((282, 170, 372, 270), fill=0)
    d.polygon([(256, 280), (230, 330), (282, 330)], fill=0)
    for x in (190, 240, 290):
        d.rectangle((x, 400, x + 20, 450), fill=0)
    save(m, "skull")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for fn in (crown, star, heart, sword, shield, gem, flame, moon, bolt, sparkle, leaf, skull):
        fn()
    print("ok")
