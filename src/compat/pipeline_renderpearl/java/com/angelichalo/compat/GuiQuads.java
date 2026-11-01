package com.angelichalo.compat;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

/** One GUI element holding pre-projected coloured quads (the settings projector), already sorted back to front. */
public record GuiQuads(float[] xy, int[] col, int quads, ScreenRectangle bounds) implements GuiElementRenderState {
	private static final Matrix3x2f IDENTITY = new Matrix3x2f();

	@Override
	public void buildVertices(VertexConsumer vertices) {
		for (int q = 0; q < quads; q++) {
			int b = q * 8;
			int k = q * 4;
			vertices.addVertexWith2DPose(IDENTITY, xy[b], xy[b + 1]).setColor(col[k]);
			vertices.addVertexWith2DPose(IDENTITY, xy[b + 2], xy[b + 3]).setColor(col[k + 1]);
			vertices.addVertexWith2DPose(IDENTITY, xy[b + 4], xy[b + 5]).setColor(col[k + 2]);
			vertices.addVertexWith2DPose(IDENTITY, xy[b + 6], xy[b + 7]).setColor(col[k + 3]);
		}
	}

	@Override
	public RenderPipeline pipeline() {
		return RenderPipelines.GUI;
	}

	@Override
	public TextureSetup textureSetup() {
		return TextureSetup.noTexture();
	}

	@Override
	public @Nullable ScreenRectangle scissorArea() {
		return bounds;
	}
}
