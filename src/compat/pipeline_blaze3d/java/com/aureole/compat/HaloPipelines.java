package com.aureole.compat;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import com.aureole.Aureole;

/**
 * The halo's render pipelines (Minecraft 26.1 – 26.2, where they live in {@code com.mojang.blaze3d.pipeline}).
 * All are vanilla's position-colour debug quads with a different depth test or blend, so they work on both the
 * OpenGL and Vulkan backends.
 */
public final class HaloPipelines {
	private HaloPipelines() { }

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

	public static final RenderType CORE = type("halo_core", DEPTH_PIPELINE);
	public static final RenderType CORE_HIDDEN = type("halo_core_hidden", HIDDEN_PIPELINE);
	public static final RenderType CORE_XRAY = type("halo_core_xray", XRAY_PIPELINE);
	public static final RenderType GLOW = type("halo_glow", GLOW_PIPELINE);
	public static final RenderType GLOW_HIDDEN = type("halo_glow_hidden", GLOW_HIDDEN_PIPELINE);
	public static final RenderType GLOW_XRAY = type("halo_glow_xray", GLOW_XRAY_PIPELINE);

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
			Aureole.LOGGER.info("Registered {} halo render pipelines with Iris for shaderpack support", count);
		} catch (ClassNotFoundException e) {
			// Iris not installed: nothing to do
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			Aureole.LOGGER.warn("Could not register halo pipelines with Iris; the halo may be hidden while shaders are on", e);
		}
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(Aureole.MOD_ID, path);
	}

	private static RenderType type(String name, RenderPipeline pipeline) {
		return RenderType.create(Aureole.MOD_ID + "_" + name, RenderSetup.builder(pipeline).sortOnUpload().createRenderSetup());
	}
}
