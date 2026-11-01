#!/usr/bin/env python3
"""Downloads a typical Hypixel Skyblock mod set for the target Minecraft version from Modrinth, resolving required
dependencies, so the in-game test can run Angelic Halo alongside them.

Usage: python3 scripts/compat_mods.py <minecraft_version> <out_dir> [slug ...]
       python3 scripts/compat_mods.py <minecraft_version> <out_dir> --list mods.txt

A list file may contain, one per line: Modrinth slugs, jar file names (e.g. "skyblocker-6.11.0+26.2.jar",
matched by searching Modrinth), or "sha1:<hash>" for an exact jar. Lines starting with # are ignored.
Projects without a build for that version are skipped and reported.
"""
import re
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


def from_hash(sha1):
    """Exact version for a jar hash (what the user actually has installed)."""
    return get(f"{API}/version_file/{sha1}?algorithm=sha1")


def search_slug(name, mc):
    """Best Modrinth match for a jar file name like 'Skyblocker-6.11.0+26.2.jar'."""
    base = re.sub(r"\.jar$", "", name, flags=re.I)
    base = re.split(r"[-_+ ](?:v?\d|mc\d|fabric|forge|neoforge|quilt|universal)", base, maxsplit=1, flags=re.I)[0]
    base = base.replace("_", " ").replace("-", " ").strip()
    q = urllib.parse.urlencode({
        "query": base,
        "facets": json.dumps([["project_type:mod"], ["categories:fabric"], [f"versions:{mc}"]]),
        "limit": 1,
    })
    hits = get(f"{API}/search?{q}").get("hits", [])
    return hits[0]["slug"] if hits else None


def main():
    mc = sys.argv[1]
    out = Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    args = sys.argv[3:]
    report_pre = []
    if args[:1] == ["--list"]:
        wanted = []
        for line in Path(args[1]).read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            if line.lower().startswith("sha1:"):
                try:
                    v = from_hash(line[5:].strip())
                    wanted.append(v["project_id"])
                except Exception as e:  # noqa: BLE001
                    report_pre.append(f"SKIP {line}: hash not on Modrinth ({e})")
            elif line.lower().endswith(".jar"):
                try:
                    slug = search_slug(line, mc)
                except Exception as e:  # noqa: BLE001
                    slug = None
                if slug:
                    report_pre.append(f"MATCH {line} -> {slug}")
                    wanted.append(slug)
                else:
                    report_pre.append(f"SKIP {line}: no Modrinth match for {mc}")
            else:
                wanted.append(line)
    else:
        wanted = args or DEFAULT

    queue = list(wanted)
    seen = set()
    seen_ids = set()
    report = list(report_pre)
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
