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
  javap -cp "$CP" -protected "$cls" > "$OUT/$cls.txt" 2>&1 || true
done < scripts/probe-classes.txt
# list of class names inside the minecraft jar(s) for discovery
for j in $(grep -E 'minecraft' "$OUT/classpath.txt"); do
  unzip -Z1 "$j" 2>/dev/null | grep -E '^(net/minecraft/client/(renderer|gui|KeyMapping|Options|CameraType|Minecraft|DeltaTracker)|com/mojang/blaze3d)' | grep -v '\$[0-9]' >> "$OUT/classlist.txt"
done
# Mod Menu versions for 26.2
curl -s 'https://api.modrinth.com/v2/project/modmenu/version?game_versions=%5B%2226.2%22%5D&loaders=%5B%22fabric%22%5D' | python3 -c 'import json,sys; [print(v["version_number"], v["date_published"]) for v in json.load(sys.stdin)[:5]]' > "$OUT/modmenu.txt" 2>&1 || true
curl -s 'https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%5B%2226.2%22%5D' | python3 -c 'import json,sys; [print(v["version_number"]) for v in json.load(sys.stdin)[:5]]' > "$OUT/fabricapi.txt" 2>&1 || true
# bytecode of pipeline/render type definitions (to mirror vanilla settings exactly)
javap -cp "$CP" -c -p net.minecraft.client.renderer.RenderPipelines > "$OUT/bytecode-RenderPipelines.txt" 2>&1 || true
javap -cp "$CP" -c -p net.minecraft.client.renderer.rendertype.RenderTypes > "$OUT/bytecode-RenderTypes.txt" 2>&1 || true
javap -cp "$CP" -c -p net.minecraft.client.gui.components.AbstractSliderButton > "$OUT/bytecode-AbstractSliderButton.txt" 2>&1 || true
javap -cp "$CP" -c -p net.minecraft.client.gui.screens.PauseScreen > "$OUT/bytecode-PauseScreen.txt" 2>&1 || true
for j in $(grep -E 'minecraft' "$OUT/classpath.txt"); do
  unzip -Z1 "$j" 2>/dev/null | grep -iE 'font|chat/Style' >> "$OUT/classlist-font.txt"
  unzip -Z1 "$j" 2>/dev/null | grep -E '^assets/minecraft/font/' >> "$OUT/classlist-font.txt"
done
