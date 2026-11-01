package com.aureole.gametest;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import com.aureole.config.HaloConfig;
import com.aureole.config.HaloOptions;
import com.aureole.config.HaloPresets;
import com.aureole.render.HaloRenderer;

/**
 * Shader compatibility: only runs when HALO_SHADERPACK points at a shaderpack zip (CI sets this, with Iris and
 * Sodium installed). Photographs the same scenes without and with the pack so colours can be compared.
 * Singleplayer only.
 */
public class ShaderCompatTest implements FabricClientGameTest {
	private int sx;
	private int sy;
	private int sz;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		String pack = System.getenv("HALO_SHADERPACK");
		if (pack == null || pack.isBlank()) return;
		Path zip = Path.of(pack);

		ctx.getInput().resizeWindow(1280, 720);
		ctx.waitTicks(2);

		try (TestSingleplayerContext sp = ctx.worldBuilder()
				.adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
				.create()) {
			TestServerContext server = sp.getServer();
			BlockPos p = ctx.computeOnClient(mc -> mc.player.blockPosition());
			sx = p.getX();
			sy = p.getY();
			sz = p.getZ();
			stage(server);
			server.runCommand("weather clear");
			try {
				sp.getConnection().waitForChunksRender();
			} catch (RuntimeException | AssertionError e) {
				ctx.waitTicks(100);
			}
			ctx.getInput().pressKey(o -> o.keyToggleGui);

			scenes(ctx, server, "noshader");

			ctx.runOnClient(mc -> {
				try {
					enableShaderpack(zip);
				} catch (Exception e) {
					throw new AssertionError("Could not load shaderpack " + zip.getFileName() + " through Iris", e);
				}
			});
			ctx.waitTicks(80);
			scenes(ctx, server, "shader");

			ctx.runOnClient(mc -> {
				if (HaloRenderer.hasFailed()) throw new AssertionError("Halo renderer threw with the shaderpack active");
			});
		}
	}

	private void scenes(ClientGameTestContext ctx, TestServerContext server, String prefix) {
		// 1) the reference ring, first person, daytime
		server.runCommand("time set 6000");
		pose(server, 0, 24);
		shot(ctx, prefix + "_1_classic", CameraType.FIRST_PERSON, c -> HaloConfig.set(new HaloConfig()));

		// 2) glow line with bloom at dusk
		server.runCommand("time set 12600");
		pose(server, 0, 10);
		shot(ctx, prefix + "_2_glow", CameraType.THIRD_PERSON_FRONT, c -> {
			HaloConfig n = new HaloConfig();
			n.style = HaloOptions.Style.GLOW_LINE;
			n.radius = 1.3;
			n.lineWidth = 3;
			n.hiddenOpacity = 0.3;
			n.bloom = true;
			n.ringColor.mode = HaloOptions.ColorMode.GRADIENT;
			n.ringColor.primary = 0x38E8FF;
			n.ringColor.secondary = 0xC77DFF;
			HaloConfig.set(n);
		});

		// 3) scanner + rainbow trail
		shot(ctx, prefix + "_3_scan", CameraType.THIRD_PERSON_FRONT, c -> HaloConfig.set(preset("scanner")));

		// 4) text + floor emblem
		pose(server, 0, 38);
		shot(ctx, prefix + "_4_text_emblem", CameraType.THIRD_PERSON_BACK, c -> {
			HaloConfig n = preset("angelic");
			n.motion = HaloOptions.Motion.STATIC;
			n.style = HaloOptions.Style.GLOW_LINE;
			n.radius = 1.6;
			n.textEnabled = true;
			n.emblemEnabled = true;
			n.emblemSize = 2.6;
			HaloConfig.set(n);
		});

		// 5) night: seraph crown and embers
		server.runCommand("time set 18000");
		pose(server, 0, 8);
		shot(ctx, prefix + "_5_night", CameraType.THIRD_PERSON_FRONT, c -> HaloConfig.set(preset("seraph")));
	}

	private static HaloConfig preset(String id) {
		return HaloPresets.ALL.stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow().build(new HaloConfig());
	}

	/** Loads a shaderpack through Iris's own config + reload, via reflection so the mod never depends on Iris. */
	private static void enableShaderpack(Path zip) throws Exception {
		Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
		Path dir = (Path) iris.getMethod("getShaderpacksDirectory").invoke(null);
		Files.createDirectories(dir);
		Files.copy(zip, dir.resolve(zip.getFileName()), StandardCopyOption.REPLACE_EXISTING);
		Object config = iris.getMethod("getIrisConfig").invoke(null);
		config.getClass().getMethod("setShaderPackName", String.class).invoke(config, zip.getFileName().toString());
		config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, true);
		Method save = config.getClass().getMethod("save");
		save.invoke(config);
		iris.getMethod("reload").invoke(null);
	}

	private void shot(ClientGameTestContext ctx, String name, CameraType camera, Consumer<HaloConfig> setup) {
		ctx.runOnClient(mc -> {
			setup.accept(HaloConfig.get());
			mc.options.setCameraType(camera);
		});
		ctx.waitTicks(10);
		ctx.takeScreenshot(name.toLowerCase(Locale.ROOT));
	}

	private void pose(TestServerContext server, float yaw, float pitch) {
		server.runCommand(String.format(Locale.ROOT, "tp @a %d.5 %d %d.5 %.1f %.1f", sx, sy, sz, yaw, pitch));
	}

	private void stage(TestServerContext s) {
		int x = sx;
		int y = sy - 1;
		int z = sz;
		s.runCommand(fill(x - 12, y, z - 12, x + 12, y, z + 12, "minecraft:polished_deepslate"));
		s.runCommand(fill(x - 12, y + 1, z - 12, x + 12, y + 10, z + 12, "minecraft:air"));
		for (int dx = -6; dx <= 6; dx += 6) {
			s.runCommand(fill(x + dx, y + 1, z - 8, x + dx, y + 5, z - 8, "minecraft:deepslate_tiles"));
			s.runCommand(set(x + dx, y + 5, z - 7, "minecraft:lantern[hanging=true]"));
		}
		s.runCommand(fill(x - 6, y + 6, z - 8, x + 6, y + 6, z - 8, "minecraft:cherry_leaves[persistent=true]"));
		s.runCommand(set(x - 4, y + 1, z + 4, "minecraft:lantern"));
		s.runCommand(set(x + 4, y + 1, z + 4, "minecraft:lantern"));
	}

	private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
		return "fill %d %d %d %d %d %d %s".formatted(x1, y1, z1, x2, y2, z2, block);
	}

	private static String set(int x, int y, int z, String block) {
		return "setblock %d %d %d %s".formatted(x, y, z, block);
	}
}
