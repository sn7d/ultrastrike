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
			double time, double health, boolean onTop, float partial) {
		if (c.emblemEnabled) {
			emblem(ctx, c, player, fx, fy, fz, time, health, onTop, partial);
		}

		if (c.textEnabled && !c.text.isBlank()) {
			text(ctx, c, player, fx, fy, fz, time, health, onTop);
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

		// corners in a local (right, up) basis, uv per corner
		double[][] local = {{-half, -half, 0, 1}, {half, -half, 1, 1}, {half, half, 1, 0}, {-half, half, 0, 0}};
		float[][] pts = new float[4][3];
		int[] cols = new int[4];

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
					// plane spanned by the body's right vector and world up
					x = cx + (-fwdZ) * rx;
					y = cy + ry;
					z = cz + fwdX * rx;
				}
				default -> {
					x = fx + rx;
					y = fy + c.emblemHeight;
					z = fz + ry;
				}
			}

			pts[i][0] = (float) x;
			pts[i][1] = (float) y;
			pts[i][2] = (float) z;
			cols[i] = HaloColors.color(c.emblemColor, i / 4.0, i / 3.0, time, health, pulse);
		}

		float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
		boolean twoSided = c.emblemMode != com.angelichalo.config.HaloOptions.EmblemMode.ABOVE;
		ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), type, (pose, buffer) -> {
			quad(pose, buffer, pts, cols, uv, false);
			if (twoSided) quad(pose, buffer, pts, cols, uv, true);
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
			double time, double health, boolean onTop) {
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
			for (int i = 0; i < len; i++) {
				FormattedCharSequence g = glyph(c, text.charAt(i));
				int col = HaloColors.color(c.textColor, len <= 1 ? 0 : (double) i / (len - 1), 0, time, health, 1.0);
				ctx.submitNodeCollector().submitText(ps, x, 0, g, c.textShadow, mode, FULL_BRIGHT, col, 0, 0);
				x += font.width(g);
			}
			ps.popPose();
			return;
		}

		double radius = Math.max(0.3, c.radius + c.textOffset);
		double base = Math.toRadians(c.textSpeed * time);
		double y = fy + c.heightOffset + (c.textMode == TextMode.ENGRAVED ? 0.02 : c.textHeight);

		for (int copy = 0; copy < c.textRepeat; copy++) {
			double start = base + copy * Math.PI * 2 / c.textRepeat;
			double arc = 0;

			for (int i = 0; i < len; i++) {
				FormattedCharSequence g = glyph(c, text.charAt(i));
				double w = font.width(g) * scale * c.textSpacing;
				double a = start + (arc + w / 2) / radius;
				arc += w;
				double px = fx + Math.cos(a) * radius;
				double pz = fz + Math.sin(a) * radius;
				int col = HaloColors.color(c.textColor, len <= 1 ? 0 : (double) i / (len - 1), 0, time, health, 1.0);

				ps.pushPose();
				ps.translate(px, y, pz);
				if (c.textMode == TextMode.ENGRAVED) {
					// lie flat, baseline along the circle, reading clockwise from above
					ps.mulPose(new Quaternionf().rotateY((float) (-a - Math.PI / 2)).rotateX((float) (Math.PI / 2)));
					ps.scale(scale, -scale, scale);
				} else {
					ps.mulPose(camera.orientation);
					ps.scale(scale, -scale, scale);
				}
				ctx.submitNodeCollector().submitText(ps, -font.width(g) / 2.0f, -4, g, c.textShadow, mode, FULL_BRIGHT, col, 0, 0);
				ps.popPose();
			}
		}
	}

	private static FormattedCharSequence glyph(HaloConfig c, char ch) {
		Component comp = Component.literal(String.valueOf(ch));
		if (c.textSmoothFont) {
			comp = Component.literal(String.valueOf(ch)).withStyle(s -> s.withFont(SMOOTH_FONT));
		}
		return comp.getVisualOrderText();
	}
}
