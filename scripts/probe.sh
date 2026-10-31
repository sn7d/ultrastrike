#!/usr/bin/env bash
# CI-only helper: dumps public signatures of the Minecraft/Fabric classes the mod uses.
set -u
OUT=${1:-probe}
mkdir -p "$OUT"
./gradlew --no-configuration-cache -q printCompileClasspath | sed -n 's/^CP: //p' > "$OUT/classpath.txt"
CP=$(paste -sd: "$OUT/classpath.txt")
grep -i -E 'minecraft|fabric-api|fabric-render|fabric-key|fabric-command|fabric-lifecycle|fabric-screen' "$OUT/classpath.txt" > "$OUT/interesting.txt" || true
while read -r cls; do
  [ -z "$cls" ] && continue
  javap -cp "$CP" -public "$cls" > "$OUT/$cls.txt" 2>&1 || true
done < scripts/probe-classes.txt
# list of class names inside the minecraft jar(s) for discovery
for j in $(grep -E 'minecraft' "$OUT/classpath.txt"); do
  unzip -Z1 "$j" 2>/dev/null | grep -E '^(net/minecraft/client/(renderer|gui|KeyMapping|Options|CameraType|Minecraft|DeltaTracker)|com/mojang/blaze3d)' | grep -v '\$[0-9]' >> "$OUT/classlist.txt"
done
# Mod Menu versions for 26.2
curl -s 'https://api.modrinth.com/v2/project/modmenu/version?game_versions=%5B%2226.2%22%5D&loaders=%5B%22fabric%22%5D' | python3 -c 'import json,sys; [print(v["version_number"], v["date_published"]) for v in json.load(sys.stdin)[:5]]' > "$OUT/modmenu.txt" 2>&1 || true
curl -s 'https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%5B%2226.2%22%5D' | python3 -c 'import json,sys; [print(v["version_number"]) for v in json.load(sys.stdin)[:5]]' > "$OUT/fabricapi.txt" 2>&1 || true
