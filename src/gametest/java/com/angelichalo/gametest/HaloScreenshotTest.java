package com.angelichalo.gametest;

import java.util.function.Consumer;

import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import com.angelichalo.config.HaloConfig;
import com.angelichalo.config.HaloOptions;
import com.angelichalo.config.HaloPresets;
import com.angelichalo.gui.HaloConfigScreen;
import com.angelichalo.render.HaloRenderer;
import com.angelichalo.render.HaloState;

/**
 * Boots a real 26.2 client into a throwaway singleplayer world (never a server), builds a small stage and
 * photographs every preset, style and menu tab. Fails if the halo renderer ever throws.
 */
public class HaloScreenshotTest implements FabricClientGameTest {
	private int sx;
	private int sy;
	private int sz;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (System.getenv("HALO_SHADERPACK") != null) return; // shader runs only do ShaderCompatTest
		ctx.getInput().resizeWindow(1920, 1080);
		ctx.waitTicks(2);

		try (TestSingleplayerContext sp = ctx.worldBuilder()
				.adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
				.create()) {
			TestServerContext server = sp.getServer();
			BlockPos p = ctx.computeOnClient(mc -> mc.player.blockPosition());
			sx = p.getX();
			sy = p.getY();
			sz = p.getZ();
			buildStage(server);
			server.runCommand("weather clear");
			waitForChunks(ctx, sp);
			ctx.getInput().pressKey(o -> o.keyToggleGui); // clean shots: hide HUD
			ctx.waitTicks(5);

			// 1) the reference video: classic thin red ring in a dungeon-like corridor, first person
			buildCorridor(server);
			server.runCommand("time set 18000");
			server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %d.5 %d %d.5 180 12", sx + 40, sy, sz));
			waitForChunks(ctx, sp);
			shot(ctx, "classic_first_person", CameraType.FIRST_PERSON, c -> HaloConfig.set(new HaloConfig()));
			shot(ctx, "classic_third_person", CameraType.THIRD_PERSON_BACK, c -> { });

			// 2) every preset as a front "selfie" at sunset, with a tighter lens
			server.runCommand("time set 11900");
			pose(server, 0, 8);
			ctx.runOnClient(mc -> mc.options.fov().set(48));
			for (HaloPresets.Preset preset : HaloPresets.ALL) {
				shot(ctx, "preset_" + preset.id(), CameraType.THIRD_PERSON_FRONT, c -> HaloConfig.set(preset.build(c)));
			}

			// 3) every style, compact radius so it frames nicely
			for (HaloOptions.Style style : HaloOptions.Style.values()) {
				shot(ctx, "style_" + style.name().toLowerCase(), CameraType.THIRD_PERSON_FRONT, c -> {
					HaloConfig n = new HaloConfig();
					n.style = style;
					n.radius = 1.3;
					n.lineWidth = 3;
					n.spinSpeed = 45;
					n.ringColor.mode = HaloOptions.ColorMode.GRADIENT;
					n.ringColor.primary = 0x38E8FF;
					n.ringColor.secondary = 0xC77DFF;
					HaloConfig.set(n);
				});
			}

			// 4) scan with the fading trail, a few moments apart
			HaloConfig scan = HaloPresets.ALL.stream().filter(x -> x.id().equals("scanner")).findFirst().orElseThrow()
					.build(new HaloConfig());
			for (int i = 0; i < 4; i++) {
				shot(ctx, "scan_trail_" + i, CameraType.THIRD_PERSON_FRONT, c -> HaloConfig.set(scan.copy()));
				ctx.waitTicks(7);
			}

			// 5) reactive extras
			shot(ctx, "extras_jump_ring", CameraType.THIRD_PERSON_BACK, c -> {
				HaloConfig n = HaloPresets.ALL.get(0).build(c);
				n.jumpRings = true;
				n.radius = 1.0;
				HaloConfig.set(n);
			});
			ctx.runOnClient(mc -> HaloState.simulateJump(mc.player.position()));
			ctx.waitTicks(4);
			ctx.takeScreenshot("extras_jump_ring_burst");

			// 6) the hero "selfie" for the README
			server.runCommand("time set 12250");
			pose(server, 0, 6);
			ctx.runOnClient(mc -> mc.options.fov().set(40));
			shot(ctx, "hero_selfie", CameraType.THIRD_PERSON_FRONT, c -> {
				HaloConfig n = HaloPresets.ALL.stream().filter(x -> x.id().equals("seraph")).findFirst().orElseThrow().build(c);
				n.embers = true;
				n.emberCount = 22;
				n.emberRise = 0.5;
				HaloConfig.set(n);
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("hero_selfie_b");
			shot(ctx, "hero_scan", CameraType.THIRD_PERSON_FRONT, c -> {
				HaloConfig n = scan.copy();
				n.embers = true;
				n.extrasColor.mode = HaloOptions.ColorMode.SOLID;
				n.extrasColor.primary = 0x9FF6FF;
				HaloConfig.set(n);
			});

			// 6b) custom text and emblems
			ctx.runOnClient(mc -> mc.options.fov().set(48));
			pose(server, 0, 14);
			shot(ctx, "feature_text_orbit", CameraType.THIRD_PERSON_FRONT, c -> {
				HaloConfig n = new HaloConfig();
				n.style = HaloOptions.Style.GLOW_LINE;
				n.radius = 1.3;
				n.lineWidth = 2.5;
				n.hiddenOpacity = 0.3;
				n.ringColor.mode = HaloOptions.ColorMode.GRADIENT;
				n.ringColor.primary = 0xFFD27A;
				n.ringColor.secondary = 0xFF7AA8;
				n.textEnabled = true;
				n.text = "ANGELIC HALO";
				n.textMode = HaloOptions.TextMode.ORBIT;
				n.textHeight = 0.35;
				n.textRepeat = 2;
				HaloConfig.set(n);
			});
			shot(ctx, "feature_text_engraved", CameraType.THIRD_PERSON_FRONT, c -> {
				HaloConfig n = c.copy();
				n.textMode = HaloOptions.TextMode.ENGRAVED;
				n.textSize = 1.6;
				n.textOffset = 0.35;
				HaloConfig.set(n);
			});
			shot(ctx, "feature_text_banner", CameraType.THIRD_PERSON_FRONT, c -> {
				HaloConfig n = c.copy();
				n.textMode = HaloOptions.TextMode.BANNER;
				n.text = "sn7d";
				n.textSize = 1.4;
				n.textColor.mode = HaloOptions.ColorMode.RAINBOW;
				HaloConfig.set(n);
			});
			pose(server, 0, 38);
			shot(ctx, "feature_emblem_floor", CameraType.THIRD_PERSON_BACK, c -> {
				HaloConfig n = new HaloConfig();
				n.style = HaloOptions.Style.GLOW_LINE;
				n.radius = 1.5;
				n.hiddenOpacity = 0.3;
				n.ringColor.primary = 0xFFD27A;
				n.emblemEnabled = true;
				n.emblem = "builtin:sigil";
				n.emblemSize = 2.8;
				HaloConfig.set(n);
			});
			pose(server, 0, 8);
			shot(ctx, "feature_emblem_back", CameraType.THIRD_PERSON_BACK, c -> {
				HaloConfig n = c.copy();
				n.emblem = "builtin:sunburst";
				n.emblemMode = HaloOptions.EmblemMode.BACK;
				n.emblemSize = 1.8;
				n.emblemColor.mode = HaloOptions.ColorMode.GRADIENT;
				n.emblemColor.primary = 0xFFD27A;
				n.emblemColor.secondary = 0xFF7AA8;
				HaloConfig.set(n);
			});
			shot(ctx, "feature_emblem_above", CameraType.THIRD_PERSON_FRONT, c -> {
				HaloConfig n = c.copy();
				n.emblem = "builtin:crescent";
				n.emblemMode = HaloOptions.EmblemMode.ABOVE;
				n.emblemSize = 0.8;
				n.emblemSpin = 0;
				HaloConfig.set(n);
			});

			// custom nametag
			pose(server, 0, 4);
			shot(ctx, "feature_nametag", CameraType.THIRD_PERSON_FRONT, c -> {
				HaloConfig n = HaloPresets.ALL.stream().filter(x -> x.id().equals("angelic")).findFirst().orElseThrow().build(c);
				n.tagEnabled = true;
				n.tagText = "&lD7 ✦ Angelic";
				n.tagTitle = "&#C9A7FF☾ SkyBlock Wizard ☽";
				n.tagIconLeft = "builtin:crown";
				n.tagIconRight = "builtin:star";
				HaloConfig.set(n);
			});
			shot(ctx, "feature_nametag_rainbow", CameraType.THIRD_PERSON_FRONT, c -> {
				HaloConfig n = c.copy();
				n.tagText = "★ itsd7 ★";
				n.tagTitle = "&aMVP&6+ &7| &bLevel 300";
				n.tagColor.mode = HaloOptions.ColorMode.RAINBOW;
				n.tagColor.speed = 0.4;
				n.tagIconLeft = "builtin:bolt";
				n.tagIconRight = "builtin:gem";
				n.tagIconColor.mode = HaloOptions.ColorMode.RAINBOW;
				n.tagScale = 1.4;
				HaloConfig.set(n);
			});

			// pause menu with the halo button next to Friends
			ctx.runOnClient(mc -> mc.options.fov().set(70));
			ctx.getInput().pressKey(o -> o.keyToggleGui);
			ctx.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(4);
			ctx.takeScreenshot("pause_menu");
			ctx.setScreen(() -> null);
			ctx.getInput().pressKey(o -> o.keyToggleGui);
			ctx.waitTicks(2);

			// 7) the settings screen, every tab (screens render on top of the live world)
			ctx.runOnClient(mc -> {
				mc.options.fov().set(70);
				mc.options.guiScale().set(3);
				mc.resizeGui();
				HaloConfig n = HaloPresets.ALL.stream().filter(x -> x.id().equals("scanner")).findFirst().orElseThrow().build(new HaloConfig());
				n.radius = 1.0;
				HaloConfig.set(n);
			});
			server.runCommand("time set 12000");
			ctx.waitTicks(2);
			for (int tab = 0; tab < HaloConfigScreen.tabCount(); tab++) {
				int t = tab;
				ctx.runOnClient(mc -> HaloConfigScreen.setStartTab(t));
				ctx.setScreen(() -> new HaloConfigScreen(null));
				ctx.waitTicks(4);
				ctx.takeScreenshot("gui_tab_" + tab);
				ctx.setScreen(() -> null);
				ctx.waitTicks(1);
			}

			// nametag tab with the projector zoomed on the head
			ctx.runOnClient(mc -> {
				HaloConfig n = HaloConfig.get().copy();
				n.tagEnabled = true;
				n.tagText = "&lD7 ✦ Angelic";
				n.tagTitle = "☾ SkyBlock Wizard ☽";
				n.tagIconLeft = "builtin:crown";
				HaloConfig.set(n);
			});
			ctx.runOnClient(mc -> HaloConfigScreen.setStartTab(8));
			ctx.setScreen(() -> new HaloConfigScreen(null));
			ctx.waitTicks(12);
			ctx.takeScreenshot("gui_nametag");
			ctx.setScreen(() -> null);

			ctx.runOnClient(mc -> HaloConfigScreen.setStartColorTarget(1));
			ctx.runOnClient(mc -> HaloConfigScreen.setStartTab(4));
			ctx.setScreen(() -> new HaloConfigScreen(null));
			ctx.waitTicks(4);
			ctx.takeScreenshot("gui_trail_colors");
			ctx.setScreen(() -> null);

			ctx.runOnClient(mc -> {
				if (HaloRenderer.hasFailed()) {
					throw new AssertionError("Halo renderer threw during the test — see log");
				}
			});
		}
	}

	/** Chunk-render waiting relies on vanilla's renderer; with renderer mods (Sodium) fall back to a fixed wait. */
	private static void waitForChunks(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		try {
			sp.getConnection().waitForChunksRender();
		} catch (RuntimeException | AssertionError e) {
			ctx.waitTicks(100);
		}
	}

	private void shot(ClientGameTestContext ctx, String name, CameraType camera, Consumer<HaloConfig> setup) {
		ctx.runOnClient(mc -> {
			setup.accept(HaloConfig.get());
			mc.options.setCameraType(camera);
		});
		ctx.waitTicks(6);
		ctx.takeScreenshot(name);
	}

	private void pose(TestServerContext server, float yaw, float pitch) {
		server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %d.5 %d %d.5 %.1f %.1f", sx, sy, sz, yaw, pitch));
	}

	/** A narrow stone corridor, like a dungeon hallway, to recreate the reference clip. */
	private void buildCorridor(TestServerContext s) {
		int x = sx + 40;
		int y = sy - 1;
		int z = sz;
		s.runCommand(fill(x - 6, y, z - 20, x + 6, y + 6, z + 4, "minecraft:air"));
		s.runCommand(fill(x - 6, y, z - 20, x + 6, y, z + 4, "minecraft:deepslate_tiles"));
		s.runCommand(fill(x - 2, y + 1, z - 20, x - 2, y + 4, z + 2, "minecraft:cobbled_deepslate"));
		s.runCommand(fill(x + 2, y + 1, z - 20, x + 2, y + 4, z + 2, "minecraft:cobbled_deepslate"));
		s.runCommand(fill(x - 2, y + 5, z - 20, x + 2, y + 5, z + 2, "minecraft:deepslate_bricks"));
		s.runCommand(fill(x - 1, y + 1, z + 2, x + 1, y + 4, z + 2, "minecraft:cobbled_deepslate"));
		for (int dz = -2; dz >= -18; dz -= 4) {
			s.runCommand(set(x - 1, y + 4, z + dz, "minecraft:soul_lantern[hanging=true]"));
			s.runCommand(set(x + 1, y + 4, z + dz - 2, "minecraft:lantern[hanging=true]"));
		}
	}

	private void buildStage(TestServerContext s) {
		int x = sx;
		int y = sy - 1;
		int z = sz;
		// floor
		s.runCommand(fill(x - 14, y, z - 14, x + 14, y, z + 14, "minecraft:polished_deepslate"));
		s.runCommand(fill(x - 2, y, z - 2, x + 2, y, z + 2, "minecraft:polished_blackstone_bricks"));
		s.runCommand(fill(x - 14, y + 1, z - 14, x + 14, y + 12, z + 14, "minecraft:air"));

		// backdrop (north, behind the player in the front view): pillars, leaves and lanterns
		for (int dx = -9; dx <= 9; dx += 6) {
			s.runCommand(fill(x + dx, y + 1, z - 9, x + dx, y + 5, z - 9, "minecraft:deepslate_tiles"));
			s.runCommand(set(x + dx, y + 6, z - 9, "minecraft:cherry_leaves[persistent=true]"));
			s.runCommand(set(x + dx, y + 5, z - 8, "minecraft:lantern[hanging=true]"));
		}
		s.runCommand(fill(x - 9, y + 6, z - 10, x + 9, y + 7, z - 10, "minecraft:cherry_leaves[persistent=true]"));
		s.runCommand(fill(x - 12, y + 1, z - 12, x + 12, y + 1, z - 12, "minecraft:moss_block"));

		// a few lights around the stage
		for (int[] o : new int[][] {{-5, -5}, {5, -5}, {-5, 5}, {5, 5}}) {
			s.runCommand(set(x + o[0], y + 1, z + o[1], "minecraft:lantern"));
		}
	}

	private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
		return "fill %d %d %d %d %d %d %s".formatted(x1, y1, z1, x2, y2, z2, block);
	}

	private static String set(int x, int y, int z, String block) {
		return "setblock %d %d %d %s".formatted(x, y, z, block);
	}
}
