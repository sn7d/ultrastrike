package com.aureole.gametest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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
}
