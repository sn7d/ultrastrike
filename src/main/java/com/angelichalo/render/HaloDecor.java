package com.angelichalo.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Quaternionf;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import com.angelichalo.AngelicHalo;
import com.angelichalo.config.HaloConfig;
import com.angelichalo.config.HaloOptions.TextMode;

/** Custom text and emblem images drawn around the player. Client-side drawing only. */
final class HaloDecor {
	private HaloDecor() { }

	private static final int FULL_BRIGHT = 0xF000F0;
	static final FontDescription SMOOTH_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath(AngelicHalo.MOD_ID, "world"));

	static void render(LevelRenderContext ctx, HaloConfig c, LocalPlayer player, double fx, double fy, double fz,
			double time, double health, boolean onTop, float partial, HaloGeometry.QuadSink glow) {
		if (c.emblemEnabled) {
			emblem(ctx, c, player, fx, fy, fz, time, health, onTop, partial);
		}

		if (c.textEnabled && !c.text.isBlank()) {
			text(ctx, c, player, fx, fy, fz, time, health, onTop, glow);
		}
	}

	// ------------------------------------------------------------------------------------------------

	private static void emblem(LevelRenderContext ctx, HaloConfig c, LocalPlayer player, double fx, double fy, double fz,
			double time, double health, boolean onTop, float partial) {
		Identifier tex = HaloImages.texture(c.emblem);
		RenderType type = onTop ? RenderTypes.textSeeThrough(tex) : RenderTypes.text(tex);
		double half = c.emblemSize / 2.0;
		double spin = Math.toRadians(c.emblemSpin * time);
		double pulse = 1.0 - c.emblemPulse * 0.5 * (1.0 + Math.sin(time * 2.2));
		double h = player.getBbHeight();

		// bloom: a few larger, fainter copies of the image behind it give a soft glow in the image's own shape
		double bloom = c.bloomFor(c.emblemBloom);
		int glowLayers = bloom > 0.01 ? 4 : 0;
		float[][][] layerPts = new float[glowLayers + 1][4][3];
		int[][] layerCols = new int[glowLayers + 1][4];

		for (int layer = 0; layer <= glowLayers; layer++) {
			boolean main = layer == glowLayers;
			double grow = main ? 1.0 : 1.0 + 0.045 * (glowLayers - layer);
			double alphaMul = main ? pulse : pulse * Math.min(0.9, bloom) * 0.28 * (layer + 1) / glowLayers;
			double hs = half * grow;
			double[][] local = {{-hs, -hs}, {hs, -hs}, {hs, hs}, {-hs, hs}};

			for (int i = 0; i < 4; i++) {
				double lx = local[i][0];
				double ly = local[i][1];
				double rx = lx * Math.cos(spin) - ly * Math.sin(spin);
				double ry = lx * Math.sin(spin) + ly * Math.cos(spin);
				double x;
				double y;
				double z;

				switch (c.emblemMode) {
					case ABOVE -> {
						double cy = fy + h + 0.45 + c.emblemHeight + half;
						double[] b = billboard(fx, cy, fz);
						x = fx + b[0] * rx + b[3] * ry;
						y = cy + b[4] * ry;
						z = fz + b[2] * rx + b[5] * ry;
					}
					case BACK -> {
						float bodyYaw = (float) Math.toRadians(player.yBodyRotO + (player.yBodyRot - player.yBodyRotO) * partial);
						double fwdX = -Math.sin(bodyYaw);
						double fwdZ = Math.cos(bodyYaw);
						double cx = fx - fwdX * 0.38;
						double cz = fz - fwdZ * 0.38;
						double cy = fy + h * 0.6 + c.emblemHeight;
						x = cx + (-fwdZ) * rx;
						y = cy + ry;
						z = cz + fwdX * rx;
					}
					default -> {
						x = fx + rx;
						y = fy + c.emblemHeight + (main ? 0.002 : 0.0);
						z = fz + ry;
					}
				}

				layerPts[layer][i][0] = (float) x;
				layerPts[layer][i][1] = (float) y;
				layerPts[layer][i][2] = (float) z;
				layerCols[layer][i] = HaloColors.color(c.emblemColor, i / 4.0, i / 3.0, time, health, alphaMul);
			}
		}

		float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
		boolean twoSided = c.emblemMode != com.angelichalo.config.HaloOptions.EmblemMode.ABOVE;
		ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), type, (pose, buffer) -> {
			for (int layer = 0; layer < layerPts.length; layer++) {
				quad(pose, buffer, layerPts[layer], layerCols[layer], uv, false);
				if (twoSided) quad(pose, buffer, layerPts[layer], layerCols[layer], uv, true);
			}
		});
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer buffer, float[][] p, int[] col, float[][] uv, boolean reverse) {
		for (int k = 0; k < 4; k++) {
			int i = reverse ? 3 - k : k;
			buffer.addVertex(pose, p[i][0], p[i][1], p[i][2]).setColor(col[i]).setUv(uv[i][0], uv[i][1]).setLight(FULL_BRIGHT);
		}
	}

	/** right (x,y,z) and up (x,y,z) vectors of a camera-facing plane at a camera-relative position. */
	private static double[] billboard(double vx, double vy, double vz) {
		double rx = -vz;
		double rz = vx;
		double rl = Math.sqrt(rx * rx + rz * rz);
		if (rl < 1e-6) {
			rx = 1;
			rz = 0;
			rl = 1;
		}
		rx /= rl;
		rz /= rl;
		double ux = -rz * vy;
		double uy = rz * vx - rx * vz;
		double uz = rx * vy;
		double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
		if (ul < 1e-9) {
			ux = 0;
			uy = 1;
			uz = 0;
			ul = 1;
		}
		return new double[] {rx, 0, rz, ux / ul, uy / ul, uz / ul};
	}

	// ------------------------------------------------------------------------------------------------

	private static void text(LevelRenderContext ctx, HaloConfig c, LocalPlayer player, double fx, double fy, double fz,
			double time, double health, boolean onTop, HaloGeometry.QuadSink glow) {
		double bloom = c.bloomFor(c.textBloom);
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		CameraRenderState camera = ctx.levelState().cameraRenderState;
		PoseStack ps = ctx.poseStack();
		Font.DisplayMode mode = onTop ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
		float scale = (float) (0.025 * c.textSize);
		String text = c.text;
		int len = text.length();

		if (c.textMode == TextMode.BANNER) {
			int total = 0;
			for (int i = 0; i < len; i++) total += font.width(glyph(c, text.charAt(i)));
			double y = fy + player.getBbHeight() + 0.35 + c.textHeight;
			ps.pushPose();
			ps.translate(fx, y, fz);
			ps.mulPose(camera.orientation);
			ps.scale(scale, -scale, scale);
			float x = -total / 2.0f;
			double[] basis = billboard(fx, y, fz);
			for (int i = 0; i < len; i++) {
				FormattedCharSequence g = glyph(c, text.charAt(i));
				int col = HaloColors.color(c.textColor, len <= 1 ? 0 : (double) i / (len - 1), 0, time, health, 1.0);
				if (bloom > 0.01) {
					double off = (x + font.width(g) / 2.0) * scale;
					HaloGeometry.glowSpot(glow, (float) (fx + basis[0] * off), (float) y, (float) (fz + basis[2] * off),
							7.5 * scale, HaloColors.withAlpha(col, Math.min(0.9, bloom * 0.45)));
				}
				ctx.submitNodeCollector().submitText(ps, x, 0, g, c.textShadow, mode, FULL_BRIGHT, col, 0, 0);
				x += font.width(g);
			}
			ps.popPose();
			return;
		}

		double radius = Math.max(0.3, c.radius + c.textOffset);
		double base = Math.toRadians(c.textSpeed * time);
		boolean engraved = c.textMode == TextMode.ENGRAVED;
		double y = fy + c.heightOffset + (engraved ? 0.03 : c.textHeight);

		for (int copy = 0; copy < c.textRepeat; copy++) {
			double start = base + copy * Math.PI * 2 / c.textRepeat;
			double arc = 0;

			for (int i = 0; i < len; i++) {
				FormattedCharSequence g = glyph(c, text.charAt(i));
				double w = font.width(g) * scale * c.textSpacing;
				double mid = (arc + w / 2) / radius;
				arc += w;
				// orbit text reads left-to-right from outside (clockwise), engraved text reads around from above
				double a = engraved ? start + mid : start - mid;
				double ca = Math.cos(a);
				double sa = Math.sin(a);
				double px = fx + ca * radius;
				double pz = fz + sa * radius;
				double alpha = 1.0;

				if (!engraved) {
					// fade letters on the far side so the band reads cleanly
					double dist = Math.sqrt(px * px + (y * y) + pz * pz);
					double facing = -(ca * px + sa * pz) / Math.max(dist, 1e-4);
					double k = Math.max(0, Math.min(1, (facing + 0.15) / 0.45));
					alpha = 0.12 + 0.88 * k * k * (3 - 2 * k);
				}

				int col = HaloColors.color(c.textColor, len <= 1 ? 0 : (double) i / (len - 1), 0, time, health, alpha);
				if ((col >>> 24) < 4) continue;
				if (bloom > 0.01) {
					HaloGeometry.glowSpot(glow, (float) px, (float) y, (float) pz, 7.0 * scale,
							HaloColors.withAlpha(col, Math.min(0.9, bloom * 0.4)));
				}

				ps.pushPose();
				ps.translate(px, y, pz);
				if (engraved) {
					ps.mulPose(new Quaternionf().rotateY((float) (-a - Math.PI / 2)).rotateX((float) (-Math.PI / 2)));
				} else {
					ps.mulPose(new Quaternionf().rotateY((float) (Math.PI / 2 - a)));
				}
				ps.scale(scale, -scale, scale);
				ctx.submitNodeCollector().submitText(ps, -font.width(g) / 2.0f, -4, g, c.textShadow && !engraved, mode, FULL_BRIGHT, col, 0, 0);
				ps.popPose();
			}
		}
	}

	private static final java.util.Map<Integer, FormattedCharSequence> GLYPHS = new java.util.HashMap<>();

	/** Per-letter sequences are cached: building components every frame is wasted work. */
	private static FormattedCharSequence glyph(HaloConfig c, char ch) {
		int key = (ch << 1) | (c.textSmoothFont ? 1 : 0);
		FormattedCharSequence cached = GLYPHS.get(key);
		if (cached != null) return cached;
		if (GLYPHS.size() > 512) GLYPHS.clear();
		Component comp = c.textSmoothFont
				? Component.literal(String.valueOf(ch)).withStyle(s -> s.withFont(SMOOTH_FONT))
				: Component.literal(String.valueOf(ch));
		FormattedCharSequence seq = comp.getVisualOrderText();
		GLYPHS.put(key, seq);
		return seq;
	}
}
