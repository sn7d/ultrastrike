#!/usr/bin/env python3
"""Downloads one shaderpack (latest Iris-compatible release) from Modrinth.

Usage: python3 scripts/shaderpacks.py <slug> <out_dir>   -> prints the path of the downloaded zip
       python3 scripts/shaderpacks.py --list              -> prints the default slugs (for the CI matrix)
"""
import json
import sys
import urllib.parse
import urllib.request
from pathlib import Path

API = "https://api.modrinth.com/v2"
UA = {"User-Agent": "aureole-ci (github.com/sn7d/aureole)"}

# the best-known packs on Modrinth
PACKS = [
    "complementary-reimagined",
    "complementary-unbound",
    "bsl-shaders",
    "photon-shader",
    "solas-shader",
    "bliss-shader",
    "makeup-ultra-fast-shaders",
    "sildurs-vibrant-shaders",
    "rethinking-voxels",
]


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        return json.load(r)


def main():
    if sys.argv[1] == "--list":
        print(json.dumps(PACKS))
        return
    slug, out = sys.argv[1], Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    q = urllib.parse.urlencode({"loaders": json.dumps(["iris"])})
    versions = get(f"{API}/project/{slug}/version?{q}")
    if not versions:
        raise SystemExit(f"no Iris version of {slug}")
    v = versions[0]
    f = next((x for x in v["files"] if x.get("primary")), v["files"][0])
    dest = out / f["filename"]
    with urllib.request.urlopen(urllib.request.Request(f["url"], headers=UA), timeout=300) as r:
        dest.write_bytes(r.read())
    (out / "PACK.txt").write_text(f"{slug} {v['version_number']} {f['filename']}\n")
    print(dest.resolve())


if __name__ == "__main__":
    main()
