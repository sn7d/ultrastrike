package com.aureole.gametest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

/** Small differences in Fabric's client gametest API between Minecraft lines. */
final class TestCompat {
	private TestCompat() { }

	/**
	 * {@code sp.getConnection().waitForChunksRender()} on Fabric API for 26.2+, {@code sp.getClientWorld()...} on the
	 * 26.1 line. Failures from the wait itself are rethrown unchanged, so callers can keep their fallback.
	 */
	static void waitForChunksRender(TestSingleplayerContext sp) {
		for (String getter : new String[] {"getConnection", "getClientWorld", "getClientLevel"}) {
			Method get;
			try {
				get = TestSingleplayerContext.class.getMethod(getter);
			} catch (NoSuchMethodException e) {
				continue;
			}
			try {
				Object ctx = get.invoke(sp);
				get.getReturnType().getMethod("waitForChunksRender").invoke(ctx);
				return;
			} catch (InvocationTargetException e) {
				if (e.getCause() instanceof RuntimeException r) throw r;
				if (e.getCause() instanceof Error err) throw err;
				throw new IllegalStateException(e.getCause());
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException("cannot wait for chunks via " + getter, e);
			}
		}
		throw new IllegalStateException("no chunk-wait method on TestSingleplayerContext");
	}

	private static int skipped;
	private static Boolean offLoopFramesUnsafe;

	/** Screenshots that could not be taken (see {@link #screenshot}). */
	static int skippedScreenshots() {
		return skipped;
	}

	/**
	 * Fabric's screenshot helper renders one extra frame outside the game loop. Sodium's 26.1 builds reject such frames
	 * ("Global terrain uniforms have not been updated"), and each rejected frame leaves render state behind, so with
	 * Sodium on 26.1 we never ask for one: the scenes still run through the normal game loop (exercising Aureole's
	 * renderer every frame, checked via HaloRenderer.hasFailed()), only the image files are skipped.
	 */
	static boolean offLoopFramesUnsafe() {
		if (offLoopFramesUnsafe == null) {
			FabricLoader loader = FabricLoader.getInstance();
			String mc = loader.getModContainer("minecraft").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
			offLoopFramesUnsafe = loader.isModLoaded("sodium") && mc.startsWith("26.1");
			if (offLoopFramesUnsafe) System.out.println("[aureole-test] Sodium on Minecraft " + mc + ": screenshots are skipped, scenes still run");
		}
		return offLoopFramesUnsafe;
	}

	/** {@code ctx.takeScreenshot(name)}, except where off-loop frames are unsafe (see above). */
	static void screenshot(ClientGameTestContext ctx, String name) {
		if (offLoopFramesUnsafe()) {
			skipped++;
			ctx.waitTick();
			return;
		}
		try {
			ctx.takeScreenshot(name);
		} catch (RuntimeException e) {
			Throwable root = e;
			while (root.getCause() != null) root = root.getCause();
			if (!String.valueOf(root.getMessage()).contains("terrain uniforms")) throw e;
			// another Sodium build with the same limitation: stop asking after the first rejected frame
			offLoopFramesUnsafe = true;
			skipped++;
			System.out.println("[aureole-test] screenshots disabled after " + name + ": " + root.getMessage());
		}
	}
}
