#!/usr/bin/env bash
# Dev helper (CI): dumps the API shapes Aureole touches for one Minecraft version.
# Usage: scripts/api_probe.sh <mc> <fabric_api> <modmenu> > out.txt
set -u
MC=$1; API=$2; MM=$3
CP=$(./gradlew -q printCompileClasspath -Pminecraft_version=$MC -Pfabric_api_version=$API -Pmodmenu_version=$MM | sed -n 's/^CP: //p' | tr '\n' ':')
echo "### jars"; echo "$CP" | tr ':' '\n' | grep -iE "minecraft|lwjgl|sdl|joml|blaze" | sed 's#.*/##'
find_class() { for j in $(echo "$CP" | tr ':' ' '); do [ -f "$j" ] && unzip -l "$j" 2>/dev/null | awk '{print $4}' | grep -E "$1" | sed "s#^#$(basename $j): #"; done; }
echo "### class search"
find_class '/(RenderPipeline|BlendFunction|DepthStencilState|CompareOp|ColorTargetState|InputConstants|FriendsButton|PoseStack|KeyEvent|MouseButtonEvent)\.class$'
find_class '(glfw/GLFW|sdl/SDL[A-Za-z]*Keycode|SDLKeyboard|SDLMouse)\.class$' | head -20
jp() { echo "### javap $1"; javap -cp "$CP" -public "$1" 2>&1 | head -${2:-120}; }
for c in $(find_class '/(RenderPipeline|BlendFunction|DepthStencilState|CompareOp|ColorTargetState|FriendsButton)\.class$' | awk '{print $2}' | sed 's#\.class$##; s#/#.#g' | sort -u); do jp "$c" 80; done
jp com.mojang.blaze3d.platform.InputConstants 400
jp com.mojang.blaze3d.vertex.PoseStack
jp net.minecraft.client.Camera 80
jp net.minecraft.client.gui.Gui 60
jp net.minecraft.client.Minecraft 400 | grep -iE "screen|class "
jp net.minecraft.client.renderer.RenderPipelines 400 | grep -iE "DEBUG|class "
jp net.minecraft.client.input.KeyEvent
jp net.minecraft.client.input.MouseButtonEvent
jp net.fabricmc.fabric.api.client.gametest.v1.TestInput 80
