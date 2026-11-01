#!/usr/bin/env python3
"""Lists every stable Minecraft 26.x release on Fabric with the newest Fabric API / Mod Menu build for it, plus the
latest Fabric Loader releases. Writes versions.json (a CI matrix) and prints a table.

Usage: python3 scripts/versions_probe.py [out.json]
"""
import json
import sys
import time
import urllib.parse
import urllib.request

UA = {"User-Agent": "angelic-halo-ci (github.com/sn7d/angelic-halo)"}


def get(url):
    for i in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
                return json.load(r)
        except Exception:  # noqa: BLE001
            if i == 3:
                raise
            time.sleep(2 ** i * 3)


def newest(project, mc):
    q = urllib.parse.urlencode({"loaders": json.dumps(["fabric"]), "game_versions": json.dumps([mc])})
    vs = get(f"https://api.modrinth.com/v2/project/{project}/version?{q}")
    return vs[0]["version_number"] if vs else None


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "versions.json"
    games = [g["version"] for g in get("https://meta.fabricmc.net/v2/versions/game")
             if g["stable"] and g["version"].startswith("26.")]
    loaders = [l["version"] for l in get("https://meta.fabricmc.net/v2/versions/loader") if l.get("stable", True)][:5]
    rows = []
    for mc in games:
        rows.append({"mc": mc, "fabric_api": newest("fabric-api", mc), "modmenu": newest("modmenu", mc),
                     "iris": newest("iris", mc), "sodium": newest("sodium", mc)})
    data = {"loaders": loaders, "versions": rows}
    with open(out, "w") as f:
        json.dump(data, f, indent=2)
    print("loaders:", loaders)
    for r in rows:
        print(r)


if __name__ == "__main__":
    main()
