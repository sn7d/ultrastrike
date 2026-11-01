package com.angelichalo.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import com.angelichalo.AngelicHalo;
import com.angelichalo.compat.HaloPipelines;
import com.angelichalo.config.HaloConfig;

/**
 * Submits halo geometry into the level render. Pure client-side drawing: no packets, no world changes.
 * Uses vanilla's position-colour shader (same as debug quads) so it works on both OpenGL and Vulkan backends.
 */
public final class HaloRenderer {
	private HaloRenderer() { }

	private static final QuadBuffer CORE_QUADS = new QuadBuffer();
	private static final QuadBuffer GLOW_QUADS = new QuadBuffer();

	private static boolean loggedError;

	private static java.lang.invoke.MethodHandle shaderInUse;
	private static boolean shaderLookupDone;

	/** True while an Iris shaderpack is active (looked up reflectively; false without Iris). */
	public static boolean shadersActive() {
		if (!shaderLookupDone) {
			shaderLookupDone = true;
			try {
				Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				Object api = apiClass.getMethod("getInstance").invoke(null);
				shaderInUse = java.lang.invoke.MethodHandles.publicLookup()
						.findVirtual(apiClass, "isShaderPackInUse", java.lang.invoke.MethodType.methodType(boolean.class))
						.bindTo(api);
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				shaderInUse = null;
			}
		}
		if (shaderInUse == null) return false;
		try {
			return (boolean) shaderInUse.invokeExact();
		} catch (Throwable t) {
			return false;
		}
	}

	/** True if a render error was ever caught (checked by the in-game test). */
	public static boolean hasFailed() {
		return loggedError;
	}

	public static void render(LevelRenderContext context) {
		try {
			renderUnsafe(context);
		} catch (RuntimeException e) {
			// never take the game down because of a cosmetic
			if (!loggedError) {
				loggedError = true;
				AngelicHalo.LOGGER.error("Angelic Halo failed to render; it will keep trying silently", e);
			}
		}
	}

	private static void renderUnsafe(LevelRenderContext context) {
		HaloConfig c = HaloConfig.get();
		if (!c.enabled && !c.tagEnabled) return;

		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || player.isSpectator()) return;

		CameraType cam = mc.options.getCameraType();
		boolean haloOn = c.enabled
				&& (cam.isFirstPerson() ? c.showInFirstPerson : c.showInThirdPerson)
				&& !(c.hideWhenSneaking && player.isCrouching())
				&& !(c.hideWhenInvisible && player.isInvisible());
		// your own nametag only makes sense when you can see yourself
		boolean tagOn = c.tagEnabled && !cam.isFirstPerson() && !player.isInvisible();
		if (!haloOn && !tagOn) return;

		CameraRenderState camera = context.levelState().cameraRenderState;
		Vec3 camPos = camera.pos;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 feet = player.getPosition(partial);
		double fx = feet.x - camPos.x;
		double fy = feet.y - camPos.y;
		double fz = feet.z - camPos.z;
		double time = HaloState.now();

		float maxHealth = player.getMaxHealth();
		double health = maxHealth > 0 ? player.getHealth() / maxHealth : 1.0;

		CORE_QUADS.clear();
		GLOW_QUADS.clear();
		// Shaderpacks bring their own bloom and dither soft alpha edges, so with a pack active the halo hands glow
		// over to the pack (unless the player opts in) and draws crisp edges only. Keeps colours true, no white-out.
		boolean shaders = shadersActive();
		HaloGeometry.setShaderMode(shaders);
		boolean ownGlow = !shaders || c.bloomWithShaders;
		HaloGeometry.QuadSink glowSink = ownGlow ? GLOW_QUADS : HaloGeometry.DISCARD;
		boolean allOnTop = c.throughWalls && c.hiddenOpacity >= 0.995;

		if (haloOn) {
			// world size of one 1080p pixel at distance 1: 2 * tan(fov/2) / 1080 = 2 / (proj.m11 * 1080)
			Matrix4f proj = camera.projectionMatrix;
			double m11 = proj != null && proj.m11() > 1e-4f ? proj.m11() : 1.0 / Math.tan(Math.toRadians(35));
			double pixelScale = 2.0 / (m11 * 1080.0);

			HaloGeometry.Frame frame = new HaloGeometry.Frame(fx, fy, fz, camPos.x, camPos.y, camPos.z,
					player.getBbHeight(), time, health, pixelScale, HaloState.speedFactor(),
					HaloState.damageFlash(), HaloState.jumpRings());
			HaloGeometry.build(c, frame, CORE_QUADS, glowSink);
			HaloDecor.render(context, c, player, fx, fy, fz, time, health, allOnTop, partial, glowSink);
		}

		if (tagOn) {
			HaloNametag.render(context, c, player, fx, fy, fz, time, health, glowSink);
		}

		if (shaders && c.shaderColorBoost > 1.001) {
			CORE_QUADS.saturate((float) c.shaderColorBoost);
			GLOW_QUADS.saturate((float) c.shaderColorBoost);
		}

		// The buffers are consumed when the frame's submits are drawn, before the next frame refills them,
		// so they can be handed over directly without per-frame copies.
		PoseStack poseStack = context.poseStack();
		boolean hiddenPass = c.throughWalls && !allOnTop && c.hiddenOpacity > 0.005;

		if (!GLOW_QUADS.isEmpty()) {
			submit(context, poseStack, allOnTop ? HaloPipelines.GLOW_XRAY : HaloPipelines.GLOW, GLOW_QUADS, 1.0);
			if (hiddenPass) submit(context, poseStack, HaloPipelines.GLOW_HIDDEN, GLOW_QUADS, c.hiddenOpacity);
		}

		if (!CORE_QUADS.isEmpty()) {
			submit(context, poseStack, allOnTop ? HaloPipelines.CORE_XRAY : HaloPipelines.CORE, CORE_QUADS, 1.0);
			if (hiddenPass) submit(context, poseStack, HaloPipelines.CORE_HIDDEN, CORE_QUADS, c.hiddenOpacity);
		}
	}

	private static void submit(LevelRenderContext context, PoseStack poseStack, RenderType type, QuadBuffer quads, double alpha) {
		context.submitNodeCollector().submitCustomGeometry(poseStack, type, (pose, buffer) -> quads.emit(pose, buffer, alpha));
	}

	/** Growable float/int buffer of quads; geometry is built once, then replayed into the vertex consumer. */
	static final class QuadBuffer implements HaloGeometry.QuadSink {
		private float[] pos = new float[4096 * 3];
		private int[] col = new int[4096];
		private int vertices;

		void clear() {
			vertices = 0;
		}

		boolean isEmpty() {
			return vertices == 0;
		}

		QuadBuffer snapshot() {
			QuadBuffer copy = new QuadBuffer(vertices);
			System.arraycopy(pos, 0, copy.pos, 0, vertices * 3);
			System.arraycopy(col, 0, copy.col, 0, vertices);
			copy.vertices = vertices;
			return copy;
		}

		QuadBuffer() { }

		private QuadBuffer(int capacity) {
			pos = new float[Math.max(capacity, 4) * 3];
			col = new int[Math.max(capacity, 4)];
		}

		private void ensure(int extra) {
			if ((vertices + extra) > col.length) {
				int cap = Math.max(col.length * 2, vertices + extra);
				pos = java.util.Arrays.copyOf(pos, cap * 3);
				col = java.util.Arrays.copyOf(col, cap);
			}
		}

		private void put(float x, float y, float z, int c) {
			int p = vertices * 3;
			pos[p] = x;
			pos[p + 1] = y;
			pos[p + 2] = z;
			col[vertices++] = c;
		}

		@Override
		public void quad(float x0, float y0, float z0, int c0, float x1, float y1, float z1, int c1,
				float x2, float y2, float z2, int c2, float x3, float y3, float z3, int c3) {
			// skip fully transparent quads
			if (((c0 | c1 | c2 | c3) >>> 24) == 0) return;
			ensure(4);
			put(x0, y0, z0, c0);
			put(x1, y1, z1, c1);
			put(x2, y2, z2, c2);
			put(x3, y3, z3, c3);
		}

		/** Pushes every colour away from its grey value (1 = unchanged). */
		void saturate(float k) {
			for (int v = 0; v < vertices; v++) {
				int c = col[v];
				float r = (c >> 16) & 0xFF;
				float g = (c >> 8) & 0xFF;
				float b = c & 0xFF;
				float l = 0.299f * r + 0.587f * g + 0.114f * b;
				int nr = Math.max(0, Math.min(255, Math.round(l + (r - l) * k)));
				int ng = Math.max(0, Math.min(255, Math.round(l + (g - l) * k)));
				int nb = Math.max(0, Math.min(255, Math.round(l + (b - l) * k)));
				col[v] = (c & 0xFF000000) | (nr << 16) | (ng << 8) | nb;
			}
		}

		void emit(PoseStack.Pose pose, VertexConsumer buffer, double alpha) {
			for (int v = 0; v < vertices; v++) {
				int p = v * 3;
				int c = alpha >= 0.999 ? col[v] : HaloColors.withAlpha(col[v], alpha);
				buffer.addVertex(pose, pos[p], pos[p + 1], pos[p + 2]).setColor(c);
			}
		}
	}
}
