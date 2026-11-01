#!/usr/bin/env python3
"""Downloads a typical Hypixel Skyblock mod set for the target Minecraft version from Modrinth, resolving required
dependencies, so the in-game test can run Angelic Halo alongside them.

Usage: python3 scripts/compat_mods.py <minecraft_version> <out_dir> [slug ...]
Projects without a build for that version are skipped and reported.
"""
import json
import sys
import urllib.parse
import urllib.request
from pathlib import Path

API = "https://api.modrinth.com/v2"
UA = {"User-Agent": "angelic-halo-ci (github.com/sn7d/angelic-halo)"}

DEFAULT = [
    "sodium",            # renderer replacement — the most important one to be compatible with
    "lithium",
    "iris",              # shaders loader (no pack active)
    "ferrite-core",
    "immediatelyfast",   # batches GUI/text rendering
    "entityculling",
    "skyblocker-liap",   # Skyblocker
    "firmament",
    "modmenu",
    "zoomify",
]

# provided by the dev environment already
SKIP = {"fabric-api", "P7dR8mSH", "fabric-loader", "minecraft", "java"}


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        return json.load(r)


def latest(project, mc):
    q = urllib.parse.urlencode({"loaders": json.dumps(["fabric"]), "game_versions": json.dumps([mc])})
    versions = get(f"{API}/project/{project}/version?{q}")
    versions = [v for v in versions if v.get("version_type") in ("release", "beta", "alpha")]
    return versions[0] if versions else None


def main():
    mc = sys.argv[1]
    out = Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    wanted = sys.argv[3:] or DEFAULT

    queue = list(wanted)
    seen = set()
    seen_ids = set()
    report = []
    while queue:
        slug = queue.pop(0)
        if slug in seen or slug in SKIP:
            continue
        seen.add(slug)
        try:
            v = latest(slug, mc)
        except Exception as e:  # noqa: BLE001
            report.append(f"SKIP {slug}: {e}")
            continue
        if v is None:
            report.append(f"SKIP {slug}: no Fabric build for {mc}")
            continue
        if v["project_id"] in seen_ids or v["project_id"] in SKIP:
            continue
        seen_ids.add(v["project_id"])
        f = next((x for x in v["files"] if x.get("primary")), v["files"][0])
        dest = out / f["filename"]
        if not dest.exists():
            with urllib.request.urlopen(urllib.request.Request(f["url"], headers=UA), timeout=120) as r:
                dest.write_bytes(r.read())
        report.append(f"OK   {slug} {v['version_number']} -> {f['filename']}")
        for dep in v.get("dependencies", []):
            if dep.get("dependency_type") == "required" and dep.get("project_id"):
                queue.append(dep["project_id"])

    (out / "MODS.txt").write_text("\n".join(report) + "\n")
    print("\n".join(report))


if __name__ == "__main__":
    main()
