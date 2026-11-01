#!/usr/bin/env python3
"""Fetches a player's skin from Mojang's public profile API and installs it as every default skin inside the
*test* client's resources (used only for README screenshots; never shipped in the mod jar).

Usage: python3 scripts/fetch_skin.py <username> <gametest_resources_dir>
"""
import base64
import json
import sys
import urllib.request
from pathlib import Path

DEFAULTS = ["alex", "ari", "efe", "kai", "makena", "noor", "steve", "sunny", "zuri"]


def get(url, attempts=5):
    import time
    for i in range(attempts):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "angelic-halo-ci"}), timeout=30) as r:
                return r.read()
        except Exception:  # noqa: BLE001 - rate limits (429) and hiccups: back off and retry
            if i == attempts - 1:
                raise
            time.sleep(5 * (i + 1))


def main():
    name, res = sys.argv[1], Path(sys.argv[2])
    uuid = json.loads(get(f"https://api.mojang.com/users/profiles/minecraft/{name}"))["id"]
    profile = json.loads(get(f"https://sessionserver.mojang.com/session/minecraft/profile/{uuid}"))
    textures = next(p for p in profile["properties"] if p["name"] == "textures")
    data = json.loads(base64.b64decode(textures["value"]))
    skin = data["textures"]["SKIN"]
    png = get(skin["url"])
    slim = skin.get("metadata", {}).get("model") == "slim"
    base = res / "assets/minecraft/textures/entity/player"
    for model in ("wide", "slim"):
        (base / model).mkdir(parents=True, exist_ok=True)
        for d in DEFAULTS:
            (base / model / f"{d}.png").write_bytes(png)
    print(f"{name}: {uuid} ({'slim' if slim else 'wide'} model)")


if __name__ == "__main__":
    main()
