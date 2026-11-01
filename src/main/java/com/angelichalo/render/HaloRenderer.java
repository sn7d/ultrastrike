package com.angelichalo.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import com.angelichalo.AngelicHalo;
import com.angelichalo.config.HaloConfig;

/**
 * Submits halo geometry into the level render. Pure client-side drawing: no packets, no world changes.
 * Uses vanilla's position-colour shader (same as debug quads) so it works on both OpenGL and Vulkan backends.
 */
public final class HaloRenderer {
	private HaloRenderer() { }

	// Same as vanilla DEBUG_QUADS: translucent, POSITION_COLOR quads, depth test without depth writes, no culling.
	private static final RenderPipeline DEPTH_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(id("pipeline/halo"))
			.withCull(false)
			.build();

	private static final RenderPipeline XRAY_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(id("pipeline/halo_xray"))
			.withCull(false)
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build();

	// Additive (SRC_ALPHA, ONE) bloom, like vanilla lightning.
	private static final RenderPipeline GLOW_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(id("pipeline/halo_glow"))
			.withCull(false)
			.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
			.build();

	private static final RenderPipeline GLOW_XRAY_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(id("pipeline/halo_glow_xray"))
			.withCull(false)
			.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build();

	// Only where something is in front (reverse-Z: occluded fragments are LESS than the stored depth).
	private static final RenderPipeline HIDDEN_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(id("pipeline/halo_hidden"))
			.withCull(false)
			.withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN, false))
			.build();

	private static final RenderPipeline GLOW_HIDDEN_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(id("pipeline/halo_glow_hidden"))
			.withCull(false)
			.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
			.withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN, false))
			.build();

	private static final RenderType CORE = type("halo_core", DEPTH_PIPELINE);
	private static final RenderType CORE_HIDDEN = type("halo_core_hidden", HIDDEN_PIPELINE);
	private static final RenderType GLOW_HIDDEN = type("halo_glow_hidden", GLOW_HIDDEN_PIPELINE);
	private static final RenderType CORE_XRAY = type("halo_core_xray", XRAY_PIPELINE);
	private static final RenderType GLOW = type("halo_glow", GLOW_PIPELINE);
	private static final RenderType GLOW_XRAY = type("halo_glow_xray", GLOW_XRAY_PIPELINE);

	private static final QuadBuffer CORE_QUADS = new QuadBuffer();
	private static final QuadBuffer GLOW_QUADS = new QuadBuffer();

	private static boolean loggedError;

	/**
	 * Shader support: Iris only draws render pipelines it knows. Register ours through Iris's public API
	 * ({@code IrisApi.assignPipeline}, API v0.3) as basic coloured geometry, so the halo stays visible with
	 * shaderpacks. Reflection keeps Iris an optional companion rather than a dependency.
	 */
	public static void registerWithIris() {
		try {
			Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Class<?> programClass = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
			Object api = apiClass.getMethod("getInstance").invoke(null);
			@SuppressWarnings({"unchecked", "rawtypes"})
			Object basic = Enum.valueOf((Class) programClass, "BASIC");
			java.lang.reflect.Method assign = apiClass.getMethod("assignPipeline", RenderPipeline.class, programClass);
			int count = 0;
			for (RenderPipeline p : new RenderPipeline[] {DEPTH_PIPELINE, XRAY_PIPELINE, GLOW_PIPELINE, GLOW_XRAY_PIPELINE,
					HIDDEN_PIPELINE, GLOW_HIDDEN_PIPELINE}) {
				assign.invoke(api, p, basic);
				count++;
			}
			AngelicHalo.LOGGER.info("Registered {} halo render pipelines with Iris for shaderpack support", count);
		} catch (ClassNotFoundException e) {
			// Iris not installed: nothing to do
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			AngelicHalo.LOGGER.warn("Could not register halo pipelines with Iris; the halo may be hidden while shaders are on", e);
		}
	}

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

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(AngelicHalo.MOD_ID, path);
	}

	private static RenderType type(String name, RenderPipeline pipeline) {
		return RenderType.create(AngelicHalo.MOD_ID + "_" + name, RenderSetup.builder(pipeline).sortOnUpload().createRenderSetup());
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
		if (!c.enabled) return;

		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) return;

		CameraType cam = mc.options.getCameraType();
		if (cam.isFirstPerson() ? !c.showInFirstPerson : !c.showInThirdPerson) return;
		if (c.hideWhenSneaking && player.isCrouching()) return;
		if (c.hideWhenInvisible && player.isInvisible()) return;
		if (player.isSpectator()) return;

		CameraRenderState camera = context.levelState().cameraRenderState;
		Vec3 camPos = camera.pos;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 feet = player.getPosition(partial);

		// world size of one 1080p pixel at distance 1: 2 * tan(fov/2) / 1080 = 2 / (proj.m11 * 1080)
		Matrix4f proj = camera.projectionMatrix;
		double m11 = proj != null && proj.m11() > 1e-4f ? proj.m11() : 1.0 / Math.tan(Math.toRadians(35));
		double pixelScale = 2.0 / (m11 * 1080.0);

		float maxHealth = player.getMaxHealth();
		double health = maxHealth > 0 ? player.getHealth() / maxHealth : 1.0;

		HaloGeometry.Frame frame = new HaloGeometry.Frame(
				feet.x - camPos.x, feet.y - camPos.y, feet.z - camPos.z,
				camPos.x, camPos.y, camPos.z,
				player.getBbHeight(),
				HaloState.now(),
				health,
				pixelScale,
				HaloState.speedFactor(),
				HaloState.damageFlash(),
				HaloState.jumpRings());

		CORE_QUADS.clear();
		GLOW_QUADS.clear();
		// Shaderpacks bring their own bloom and dither soft alpha edges, so with a pack active the halo hands glow
		// over to the pack (unless the player opts in) and draws crisp edges only. Keeps colours true, no white-out.
		boolean shaders = shadersActive();
		HaloGeometry.setShaderMode(shaders);
		boolean ownGlow = !shaders || c.bloomWithShaders;
		HaloGeometry.build(c, frame, CORE_QUADS, ownGlow ? GLOW_QUADS : HaloGeometry.DISCARD);

		PoseStack poseStack = context.poseStack();
		HaloDecor.render(context, c, player, frame.cx(), frame.cy(), frame.cz(), frame.time(), health,
				c.throughWalls && c.hiddenOpacity >= 0.995, partial, ownGlow ? GLOW_QUADS : HaloGeometry.DISCARD);
		boolean allOnTop = c.throughWalls && c.hiddenOpacity >= 0.995;
		boolean hiddenPass = c.throughWalls && !allOnTop && c.hiddenOpacity > 0.005;

		if (shaders && c.shaderColorBoost > 1.001) {
			CORE_QUADS.saturate((float) c.shaderColorBoost);
			GLOW_QUADS.saturate((float) c.shaderColorBoost);
		}

		if (!GLOW_QUADS.isEmpty()) {
			QuadBuffer glow = GLOW_QUADS.snapshot();
			submit(context, poseStack, allOnTop ? GLOW_XRAY : GLOW, glow, 1.0);
			if (hiddenPass) submit(context, poseStack, GLOW_HIDDEN, glow, c.hiddenOpacity);
		}

		if (!CORE_QUADS.isEmpty()) {
			QuadBuffer core = CORE_QUADS.snapshot();
			submit(context, poseStack, allOnTop ? CORE_XRAY : CORE, core, 1.0);
			if (hiddenPass) submit(context, poseStack, CORE_HIDDEN, core, c.hiddenOpacity);
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
