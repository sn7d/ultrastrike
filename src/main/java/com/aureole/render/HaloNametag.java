package com.aureole.render;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import com.aureole.config.ColorSpec;
import com.aureole.config.HaloConfig;

/**
 * Your own custom nametag, drawn above your head (third person). Purely client-side: it changes what you see,
 * not your real name, and nobody else sees it.
 */
public final class HaloNametag {
	private HaloNametag() { }

	private static final int FULL_BRIGHT = 0xF000F0;
	private static final int LINE_GAP = 12;

	/** A laid-out line: one sequence per glyph plus widths, cached until the text or style changes. */
	private record Line(String key, List<NametagText.Glyph> glyphs, FormattedCharSequence[] seq, int[] widths,
			FormattedCharSequence plate, int width) { }

	private static Line nameLine;
	private static Line titleLine;

	public static void render(LevelRenderContext ctx, HaloConfig c, LocalPlayer player, double fx, double fy, double fz,
			double time, double health, HaloGeometry.QuadSink glow) {
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		String nameText = c.tagText.isBlank() ? player.getName().getString() : c.tagText;
		nameLine = layout(nameLine, font, nameText, c, true);
		titleLine = !c.tagTitleEnabled || c.tagTitle.isBlank() ? null : layout(titleLine, font, c.tagTitle, c, false);

		boolean leftIcon = !c.tagIconLeft.isBlank();
		boolean rightIcon = !c.tagIconRight.isBlank();
		int iconPx = (int) Math.round(9 * c.tagIconSize);
		int gap = 2;
		int nameLeft = -nameLine.width() / 2;
		int nameRight = nameLeft + nameLine.width();
		int contentLeft = leftIcon ? nameLeft - iconPx - gap : nameLeft;
		int contentRight = rightIcon ? nameRight + iconPx + gap : nameRight;

		float s = (float) (0.025 * c.tagScale);
		double y = fy + player.getBbHeight() + 0.5 + c.tagHeight;
		CameraRenderState camera = ctx.levelState().cameraRenderState;
		PoseStack ps = ctx.poseStack();
		ps.pushPose();
		ps.translate(fx, y, fz);
		HaloDecor.rotate(ps, camera.orientation);
		ps.scale(s, -s, s);

		Font.DisplayMode behind = c.tagThroughWalls ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
		int plateArgb = ((int) Math.round(c.tagBackgroundOpacity * 255) << 24);

		// background plates (a transparent copy of each line padded with spaces carries the plate)
		if (c.tagBackground && c.tagBackgroundOpacity > 0.01) {
			int padLeft = nameLeft - contentLeft;
			int padRight = contentRight - nameRight;
			FormattedCharSequence padded = padded(font, nameLine.plate(), padLeft, padRight);
			ctx.submitNodeCollector().submitText(ps, contentLeft, 0, padded, false, behind, FULL_BRIGHT, 0x05FFFFFF, plateArgb, 0);
			if (titleLine != null) {
				ctx.submitNodeCollector().submitText(ps, -titleLine.width() / 2f, -LINE_GAP, titleLine.plate(), false, behind,
						FULL_BRIGHT, 0x05FFFFFF, plateArgb, 0);
			}
		}

		drawLine(ctx, ps, nameLine, c.tagColor, nameLeft, 0, c, time, health, glow, fx, y, fz, s);
		if (titleLine != null) {
			drawLine(ctx, ps, titleLine, c.tagTitleColor, -titleLine.width() / 2, -LINE_GAP, c, time, health, glow, fx, y, fz, s);
		}

		// icons, vertically centred on the name line
		if (leftIcon || rightIcon) {
			int top = 4 - iconPx / 2;
			int colA = HaloColors.color(c.tagIconColor, 0.0, 0, time, health, 1.0);
			int colB = HaloColors.color(c.tagIconColor, 1.0, 0, time, health, 1.0);
			if (leftIcon) icon(ctx, ps, c.tagIconLeft, contentLeft, top, iconPx, colA, c.tagThroughWalls);
			if (rightIcon) icon(ctx, ps, c.tagIconRight, contentRight - iconPx, top, iconPx, colB, c.tagThroughWalls);
		}

		ps.popPose();
	}

	private static void drawLine(LevelRenderContext ctx, PoseStack ps, Line line, ColorSpec spec, int x0, int yOff,
			HaloConfig c, double time, double health, HaloGeometry.QuadSink glow, double fx, double wy, double fz, float s) {
		int n = line.seq().length;
		double[] basis = glow != HaloGeometry.DISCARD && c.tagGlow > 0.01 ? billboard(fx, wy, fz) : null;
		int x = x0;

		for (int i = 0; i < n; i++) {
			NametagText.Glyph g = line.glyphs().get(i);
			int col = g.rgb() >= 0
					? (Math.round((float) spec.opacity * 255) << 24) | g.rgb()
					: HaloColors.color(spec, n <= 1 ? 0 : (double) i / (n - 1), 0, time, health, 1.0);

			if (c.tagThroughWalls) {
				// faint copy visible through blocks (like vanilla nametags), then the full-strength one
				ctx.submitNodeCollector().submitText(ps, x, yOff, line.seq()[i], false, Font.DisplayMode.SEE_THROUGH,
						FULL_BRIGHT, HaloColors.withAlpha(col, 0.32), 0, 0);
			}
			ctx.submitNodeCollector().submitText(ps, x, yOff, line.seq()[i], c.tagShadow, Font.DisplayMode.NORMAL,
					FULL_BRIGHT, col, 0, 0);

			if (basis != null && !g.text().isBlank()) {
				double off = (x + line.widths()[i] / 2.0) * s;
				double up = -(yOff + 4) * s;
				HaloGeometry.glowSpot(glow, (float) (fx + basis[0] * off + basis[3] * up), (float) (wy + basis[4] * up),
						(float) (fz + basis[2] * off + basis[5] * up), 6.5 * s, HaloColors.withAlpha(col, Math.min(0.9, c.tagGlow * 0.4)));
			}

			x += line.widths()[i];
		}
	}

	private static void icon(LevelRenderContext ctx, PoseStack ps, String key, int x, int y, int size, int argb, boolean throughWalls) {
		Identifier tex = HaloImages.ICONS.textureFor(key);
		RenderType type = throughWalls ? RenderTypes.textSeeThrough(tex) : RenderTypes.text(tex);
		float x0 = x;
		float y0 = y;
		float x1 = x + size;
		float y1 = y + size;
		ctx.submitNodeCollector().submitCustomGeometry(ps, type, (pose, buffer) -> {
			v(pose, buffer, x0, y0, 0, 0, argb);
			v(pose, buffer, x0, y1, 0, 1, argb);
			v(pose, buffer, x1, y1, 1, 1, argb);
			v(pose, buffer, x1, y0, 1, 0, argb);
		});
	}

	private static void v(PoseStack.Pose pose, VertexConsumer b, float x, float y, float u, float vv, int argb) {
		b.addVertex(pose, x, y, 0).setColor(argb).setUv(u, vv).setLight(FULL_BRIGHT);
	}

	/**
	 * Draws the nametag for the menu's projector, in text pixels around (0, 0) (the caller positions and scales the
	 * pose). Uses the same layout and colours as in-game.
	 */
	public static void drawPreview(net.minecraft.client.gui.GuiGraphicsExtractor g, HaloConfig c, String fallbackName,
			double time, float alpha) {
		Font font = Minecraft.getInstance().font;
		nameLine = layout(nameLine, font, c.tagText.isBlank() ? fallbackName : c.tagText, c, true);
		titleLine = !c.tagTitleEnabled || c.tagTitle.isBlank() ? null : layout(titleLine, font, c.tagTitle, c, false);
		int iconPx = (int) Math.round(9 * c.tagIconSize);
		int nameLeft = -nameLine.width() / 2;
		int nameRight = nameLeft + nameLine.width();
		boolean leftIcon = !c.tagIconLeft.isBlank();
		boolean rightIcon = !c.tagIconRight.isBlank();
		int contentLeft = leftIcon ? nameLeft - iconPx - 2 : nameLeft;
		int contentRight = rightIcon ? nameRight + iconPx + 2 : nameRight;

		if (c.tagBackground && c.tagBackgroundOpacity > 0.01) {
			int plate = (int) Math.round(c.tagBackgroundOpacity * 255 * alpha) << 24;
			g.fill(contentLeft - 1, -1, contentRight + 1, 9, plate);
			if (titleLine != null) {
				g.fill(-titleLine.width() / 2 - 1, -LINE_GAP - 1, titleLine.width() / 2 + 1, -LINE_GAP + 9, plate);
			}
		}

		previewLine(g, font, nameLine, c.tagColor, nameLeft, 0, c, time, alpha);
		if (titleLine != null) previewLine(g, font, titleLine, c.tagTitleColor, -titleLine.width() / 2, -LINE_GAP, c, time, alpha);

		int top = 4 - iconPx / 2;
		if (leftIcon) previewIcon(g, c.tagIconLeft, contentLeft, top, iconPx, HaloColors.color(c.tagIconColor, 0, 0, time, 1, alpha));
		if (rightIcon) previewIcon(g, c.tagIconRight, contentRight - iconPx, top, iconPx, HaloColors.color(c.tagIconColor, 1, 0, time, 1, alpha));
	}

	private static void previewLine(net.minecraft.client.gui.GuiGraphicsExtractor g, Font font, Line line, ColorSpec spec,
			int x0, int y, HaloConfig c, double time, float alpha) {
		int n = line.seq().length;
		int x = x0;
		for (int i = 0; i < n; i++) {
			NametagText.Glyph gl = line.glyphs().get(i);
			int col = gl.rgb() >= 0
					? (Math.round((float) spec.opacity * 255 * alpha) << 24) | gl.rgb()
					: HaloColors.color(spec, n <= 1 ? 0 : (double) i / (n - 1), 0, time, 1, alpha);
			g.text(font, line.seq()[i], x, y, col, c.tagShadow);
			x += line.widths()[i];
		}
	}

	private static void previewIcon(net.minecraft.client.gui.GuiGraphicsExtractor g, String key, int x, int y, int size, int argb) {
		g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, HaloImages.ICONS.textureFor(key), x, y, 0, 0,
				size, size, size, size, argb);
	}

	// ------------------------------------------------------------------------------------------------
	// layout (cached)

	private static Line layout(Line cached, Font font, String text, HaloConfig c, boolean isName) {
		String key = text + '\u0000' + c.tagSmoothFont + c.tagBold + isName;
		if (cached != null && cached.key().equals(key)) return cached;

		List<NametagText.Glyph> glyphs = NametagText.parse(text);
		FormattedCharSequence[] seq = new FormattedCharSequence[glyphs.size()];
		int[] widths = new int[glyphs.size()];
		MutableComponent all = Component.empty();
		int total = 0;

		for (int i = 0; i < glyphs.size(); i++) {
			NametagText.Glyph g = glyphs.get(i);
			MutableComponent comp = Component.literal(g.text()).withStyle(st -> {
				var out = st.withBold(g.bold() || (isName && c.tagBold))
						.withItalic(g.italic())
						.withUnderlined(g.underline())
						.withStrikethrough(g.strike())
						.withObfuscated(g.obfuscated());
				return c.tagSmoothFont ? out.withFont(HaloDecor.SMOOTH_FONT) : out;
			});
			seq[i] = comp.getVisualOrderText();
			widths[i] = font.width(seq[i]);
			total += widths[i];
			all.append(comp);
		}

		return new Line(key, glyphs, seq, widths, all.getVisualOrderText(), total);
	}

	private static FormattedCharSequence padded(Font font, FormattedCharSequence line, int padLeft, int padRight) {
		int space = Math.max(1, font.width(" "));
		String l = " ".repeat(Math.max(0, (padLeft + space - 1) / space));
		String r = " ".repeat(Math.max(0, (padRight + space - 1) / space));
		return FormattedCharSequence.composite(Component.literal(l).getVisualOrderText(), line, Component.literal(r).getVisualOrderText());
	}

	/** right (x,y,z) and up (x,y,z) of a camera-facing plane at a camera-relative position. */
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
}
