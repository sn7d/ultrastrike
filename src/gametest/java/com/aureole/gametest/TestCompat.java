package com.aureole.gametest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

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

	/** Screenshots that could not be taken (see {@link #screenshot}); reported at the end of a run. */
	static int skippedScreenshots() {
		return skipped;
	}

	/**
	 * {@code ctx.takeScreenshot(name)}, tolerant of one known incompatibility: Fabric's screenshot helper renders an
	 * extra frame outside the game loop, which Sodium's 26.1 builds reject ("Global terrain uniforms have not been
	 * updated"). That frame is Sodium's terrain, not Aureole; the game keeps running normally, so the image is skipped
	 * and the test goes on (renderer errors from Aureole are still checked via HaloRenderer.hasFailed()).
	 */
	static void screenshot(ClientGameTestContext ctx, String name) {
		for (int attempt = 1; ; attempt++) {
			try {
				ctx.takeScreenshot(name);
				return;
			} catch (RuntimeException e) {
				Throwable root = e;
				while (root.getCause() != null) root = root.getCause();
				String msg = String.valueOf(e.getMessage()) + " / " + root.getMessage();
				if (!msg.contains("terrain uniforms")) throw e;
				if (attempt >= 2) {
					skipped++;
					System.out.println("[aureole-test] screenshot " + name + " skipped (Sodium rejects off-loop frames): " + root.getMessage());
					ctx.waitTicks(5);
					return;
				}
				ctx.waitTicks(20);
			}
		}
	}
}
