package com.angelichalo.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

import com.angelichalo.config.HaloConfig;
import com.angelichalo.config.HaloOptions;
import com.angelichalo.gui.ui.Controls;
import com.angelichalo.gui.ui.Ui;
import com.angelichalo.render.HaloColors;
import com.angelichalo.render.HaloGeometry;
import com.angelichalo.render.HaloImages;
import com.angelichalo.render.HaloState;

/**
 * Real-time "effect projector": renders the current halo settings around a mannequin with a virtual orbiting
 * camera, entirely inside the GUI. Uses the exact same geometry code as the in-world renderer.
 */
public final class HaloProjector {
	private float yaw = 35f;
	private float pitch = 18f;
	private float zoom = 1f;
	private float autoSpin = 1f;
	private boolean dragging;
	private double lastX;
	private double lastY;

	private int x0;
	private int y0;
	private int x1;
	private int y1;

	public boolean contains(double mx, double my) {
		return mx >= x0 && mx < x1 && my >= y0 && my < y1;
	}

	public boolean mouseDown(double mx, double my) {
		if (!contains(mx, my)) return false;
		dragging = true;
		lastX = mx;
		lastY = my;
		return true;
	}

	public void mouseDrag(double mx, double my) {
		if (!dragging) return;
		yaw += (float) (mx - lastX) * 0.8f;
		pitch = Math.max(-5f, Math.min(75f, pitch + (float) (my - lastY) * 0.6f));
		lastX = mx;
		lastY = my;
		autoSpin = 0f;
	}

	public void mouseUp() {
		dragging = false;
	}

	public boolean scroll(double mx, double my, double amount) {
		if (!contains(mx, my)) return false;
		zoom = Math.max(0.45f, Math.min(2.5f, zoom * (float) Math.pow(0.9, amount)));
		return true;
	}

	public void draw(GuiGraphicsExtractor g, int px0, int py0, int px1, int py1, float dt, int mx, int my) {
		x0 = px0;
		y0 = py0;
		x1 = px1;
		y1 = py1;
		int w = x1 - x0;
		int h = y1 - y0;
		if (w < 40 || h < 40) return;

		HaloConfig c = HaloConfig.get();
		double time = HaloState.now();
		if (!dragging) autoSpin = Ui.approach(autoSpin, 1f, dt, 0.6f);
		yaw += dt * 14f * autoSpin;

		// backdrop
		Ui.round(g, x0, y0, x1, y1, 6, 0xFF07080C);
		Ui.gradientV(g, x0 + 1, y0 + 1, x1 - 1, y0 + h / 2, 0xFF141629, 0xFF0B0C14);
		Ui.gradientV(g, x0 + 1, y0 + h / 2, x1 - 1, y1 - 1, 0xFF0B0C14, 0xFF07080C);

		// camera
		double extent = Math.max(c.radius * 1.15, 1.2);
		if (c.motion == HaloOptions.Motion.RIPPLE) extent = Math.max(extent, c.radius);
		if (c.jumpRings) extent = Math.max(extent, c.jumpRingSize * 0.6);
		double dist = (2.6 + extent * 1.9) / zoom;
		double fov = Math.toRadians(40);
		double targetY = 0.95;
		double yr = Math.toRadians(yaw);
		double pr = Math.toRadians(pitch);
		double camX = Math.sin(yr) * Math.cos(pr) * dist;
		double camY = targetY + Math.sin(pr) * dist;
		double camZ = Math.cos(yr) * Math.cos(pr) * dist;

		// view basis
		double fx = -camX;
		double fy = targetY - camY;
		double fz = -camZ;
		double fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
		fx /= fl;
		fy /= fl;
		fz /= fl;
		double rx = -fz; // right = forward x worldUp
		double rz = fx;
		double rl = Math.sqrt(rx * rx + rz * rz);
		rx /= rl;
		rz /= rl;
		double ux = -rz * fy;
		double uy = rz * fx - rx * fz;
		double uz = rx * fy;
		double focal = (h * 0.5) / Math.tan(fov / 2);
		double cxs = x0 + w * 0.5;
		double cys = y0 + h * 0.56;

		Projector proj = new Projector(camX, camY, camZ, rx, rz, ux, uy, uz, fx, fy, fz, focal, cxs, cys);
		QuadCollector quads = new QuadCollector(proj);

		// floor grid
		floor(quads, extent);

		// mannequin
		mannequin(quads);

		// halo geometry
		LocalPlayer player = Minecraft.getInstance().player;
		double health = player != null && player.getMaxHealth() > 0 ? player.getHealth() / player.getMaxHealth() : 1.0;
		List<HaloState.JumpRing> rings = new ArrayList<>();
		if (player != null) {
			for (HaloState.JumpRing jr : HaloState.jumpRings()) {
				rings.add(new HaloState.JumpRing(jr.x() - player.getX(), jr.y() - player.getY(), jr.z() - player.getZ(), jr.start()));
			}
		}
		double pixelScale = 2.0 * Math.tan(fov / 2) / h * 0.75;
		HaloGeometry.Frame frame = new HaloGeometry.Frame(-camX, -camY, -camZ, camX, camY, camZ, 1.8, time, health,
				pixelScale, 0.0, c.damageFlash ? HaloState.damageFlash() : 0.0, rings);
		quads.offset(camX, camY, camZ);
		HaloGeometry.build(c, frame, quads.core(), quads.glow());

		if (c.emblemEnabled && c.emblemMode == HaloOptions.EmblemMode.FLOOR) {
			// draw the emblem as a projected, tinted image under the scene
			emblemFloor(g, c, proj, time);
		}

		g.guiRenderState.addGuiElement(quads.build(new ScreenRectangle(x0, y0, w, h)));

		if (c.textEnabled && !c.text.isBlank()) {
			text(g, c, proj, time);
		}

		if (c.emblemEnabled && c.emblemMode != HaloOptions.EmblemMode.FLOOR) {
			double cy = c.emblemMode == HaloOptions.EmblemMode.ABOVE ? 1.8 + 0.45 + c.emblemHeight + c.emblemSize / 2 : 1.1 + c.emblemHeight;
			double[] p = proj.project(0, cy, c.emblemMode == HaloOptions.EmblemMode.BACK ? -0.38 : 0);
			if (p != null) {
				int size = (int) Math.round(c.emblemSize * proj.focal / p[2]);
				int col = HaloColors.color(c.emblemColor, 0.3, 0.5, time, health, 1.0);
				Controls.image(g, HaloImages.texture(c.emblem), (int) p[0] - size / 2, (int) p[1] - size / 2, size, col);
			}
		}

		// caption
		boolean live = (System.currentTimeMillis() / 600) % 2 == 0;
		Ui.round(g, x0 + 8, y0 + 8, x0 + 13, y0 + 13, 2, live ? 0xFFFF4B55 : 0x66FF4B55);
		Ui.text(g, "LIVE PREVIEW", x0 + 17, y0 + 6, Ui.TEXT_MUTED, Ui.FONT_BOLD);
		if (contains(mx, my)) {
			Ui.textCentered(g, "drag to orbit · scroll to zoom", x0 + w / 2, y1 - 13, Ui.TEXT_DIM, Ui.FONT);
		}
		Ui.roundOutline(g, x0, y0, x1, y1, 6, 0x22FFFFFF);
	}

	private void floor(QuadCollector q, double extent) {
		HaloGeometry.QuadSink s = q.core();
		double r = extent * 1.6;
		int rings = 4;
		for (int k = 1; k <= rings; k++) {
			double rr = r * k / rings;
			int seg = 64;
			for (int i = 0; i < seg; i++) {
				double a0 = Math.PI * 2 * i / seg;
				double a1 = Math.PI * 2 * (i + 1) / seg;
				float w = 0.012f;
				int col = 0x26FFFFFF;
				q.flatLine(s, Math.cos(a0) * rr, Math.sin(a0) * rr, Math.cos(a1) * rr, Math.sin(a1) * rr, w, col, true);
			}
		}
		for (int i = 0; i < 8; i++) {
			double a = Math.PI * 2 * i / 8;
			q.flatLine(s, 0, 0, Math.cos(a) * r, Math.sin(a) * r, 0.01f, 0x1AFFFFFF, true);
		}
	}

	/** Simple 32-pixel-tall player mannequin built from shaded boxes. */
	private void mannequin(QuadCollector q) {
		double s = 1.8 / 32.0;
		box(q, -4, 0, -2, 0, 12, 2, s, 0xFF2B2F3E);   // left leg
		box(q, 0, 0, -2, 4, 12, 2, s, 0xFF2B2F3E);    // right leg
		box(q, -4, 12, -2, 4, 24, 2, s, 0xFF3A3F55);  // body
		box(q, -8, 12, -2, -4, 24, 2, s, 0xFF343A4E); // left arm
		box(q, 4, 12, -2, 8, 24, 2, s, 0xFF343A4E);   // right arm
		box(q, -4, 24, -4, 4, 32, 4, s, 0xFF434964);  // head
	}

	private void box(QuadCollector q, double ax, double ay, double az, double bx, double by, double bz, double s, int color) {
		double x0 = ax * s;
		double y0 = ay * s;
		double z0 = az * s;
		double x1 = bx * s;
		double y1 = by * s;
		double z1 = bz * s;
		HaloGeometry.QuadSink sink = q.solid();
		// shading per face (light from upper front-left)
		int top = shade(color, 1.25f);
		int side = shade(color, 1.0f);
		int front = shade(color, 1.1f);
		int dark = shade(color, 0.75f);
		q.face(sink, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, top);
		q.face(sink, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, dark);
		q.face(sink, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, front);
		q.face(sink, x1, y0, z0, x1, y1, z0, x0, y1, z0, x0, y0, z0, dark);
		q.face(sink, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, side);
		q.face(sink, x1, y0, z1, x1, y1, z1, x1, y1, z0, x1, y0, z0, shade(color, 0.88f));
	}

	private static int shade(int argb, float f) {
		int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * f));
		int gg = Math.min(255, Math.round(((argb >> 8) & 0xFF) * f));
		int b = Math.min(255, Math.round((argb & 0xFF) * f));
		return (argb & 0xFF000000) | (r << 16) | (gg << 8) | b;
	}

	private void emblemFloor(GuiGraphicsExtractor g, HaloConfig c, Projector proj, double time) {
		double[] centre = proj.project(0, c.emblemHeight, 0);
		if (centre == null) return;
		double size = c.emblemSize * proj.focal / centre[2];
		double squash = Math.max(0.08, Math.abs(Math.sin(Math.toRadians(pitch))));
		int col = HaloColors.color(c.emblemColor, 0.3, 0.5, time, 1.0, 1.0 - c.emblemPulse * 0.5 * (1 + Math.sin(time * 2.2)));
		g.pose().pushMatrix();
		g.pose().translate((float) centre[0], (float) centre[1]);
		g.pose().scale(1f, (float) squash);
		g.pose().rotate((float) Math.toRadians(c.emblemSpin * time + yaw));
		int s = (int) Math.round(size);
		Controls.image(g, HaloImages.texture(c.emblem), -s / 2, -s / 2, s, col);
		g.pose().popMatrix();
	}

	private void text(GuiGraphicsExtractor g, HaloConfig c, Projector proj, double time) {
		String t = c.text;
		int len = t.length();
		if (c.textMode == HaloOptions.TextMode.BANNER) {
			double[] p = proj.project(0, 1.8 + 0.4 + c.textHeight, 0);
			if (p == null) return;
			int total = Ui.width(t);
			int x = (int) p[0] - total / 2;
			for (int i = 0; i < len; i++) {
				String ch = String.valueOf(t.charAt(i));
				int col = HaloColors.color(c.textColor, len <= 1 ? 0 : (double) i / (len - 1), 0, time, 1, 1);
				Ui.text(g, ch, x, (int) p[1], col, Ui.FONT_BOLD);
				x += Ui.width(ch);
			}
			return;
		}

		double radius = Math.max(0.3, c.radius + c.textOffset);
		double base = Math.toRadians(c.textSpeed * time);
		double y = c.heightOffset + (c.textMode == HaloOptions.TextMode.ENGRAVED ? 0.02 : c.textHeight);
		double charW = 0.16 * c.textSize * c.textSpacing;
		for (int copy = 0; copy < c.textRepeat; copy++) {
			double start = base + copy * Math.PI * 2 / c.textRepeat;
			for (int i = 0; i < len; i++) {
				double a = start + (i + 0.5) * charW / radius;
				double[] p = proj.project(Math.cos(a) * radius, y, Math.sin(a) * radius);
				if (p == null) continue;
				String ch = String.valueOf(t.charAt(i));
				int col = HaloColors.color(c.textColor, len <= 1 ? 0 : (double) i / (len - 1), 0, time, 1, 1);
				Ui.text(g, ch, (int) p[0] - Ui.width(ch) / 2, (int) p[1] - 4, col, Ui.FONT_BOLD);
			}
		}
	}

	// ------------------------------------------------------------------------------------------------

	/** Perspective projection for world points relative to the mannequin's feet. */
	static final class Projector {
		final double camX;
		final double camY;
		final double camZ;
		final double rx;
		final double rz;
		final double ux;
		final double uy;
		final double uz;
		final double fx;
		final double fy;
		final double fz;
		final double focal;
		final double cx;
		final double cy;

		Projector(double camX, double camY, double camZ, double rx, double rz, double ux, double uy, double uz,
				double fx, double fy, double fz, double focal, double cx, double cy) {
			this.camX = camX;
			this.camY = camY;
			this.camZ = camZ;
			this.rx = rx;
			this.rz = rz;
			this.ux = ux;
			this.uy = uy;
			this.uz = uz;
			this.fx = fx;
			this.fy = fy;
			this.fz = fz;
			this.focal = focal;
			this.cx = cx;
			this.cy = cy;
		}

		/** @return {screenX, screenY, depth} or null when behind the camera */
		double[] project(double wx, double wy, double wz) {
			return projectRel(wx - camX, wy - camY, wz - camZ);
		}

		double[] projectRel(double px, double py, double pz) {
			double z = px * fx + py * fy + pz * fz;
			if (z < 0.05) return null;
			double sx = px * rx + pz * rz;
			double sy = px * ux + py * uy + pz * uz;
			return new double[] {cx + focal * sx / z, cy - focal * sy / z, z};
		}
	}

	/** Collects projected quads from both the halo geometry and the mannequin, then depth-sorts them. */
	static final class QuadCollector {
		private final Projector proj;
		private double ox;
		private double oy;
		private double oz;
		private float[] xy = new float[8 * 512];
		private int[] col = new int[4 * 512];
		private float[] depth = new float[512];
		private int count;

		QuadCollector(Projector proj) {
			this.proj = proj;
		}

		/** Halo geometry arrives camera-relative; shift it back to feet-relative world space. */
		void offset(double x, double y, double z) {
			ox = x;
			oy = y;
			oz = z;
		}

		HaloGeometry.QuadSink core() {
			return (x0, y0, z0, c0, x1, y1, z1, c1, x2, y2, z2, c2, x3, y3, z3, c3) ->
					addRel(x0, y0, z0, c0, x1, y1, z1, c1, x2, y2, z2, c2, x3, y3, z3, c3, 1f);
		}

		HaloGeometry.QuadSink glow() {
			return (x0, y0, z0, c0, x1, y1, z1, c1, x2, y2, z2, c2, x3, y3, z3, c3) ->
					addRel(x0, y0, z0, c0, x1, y1, z1, c1, x2, y2, z2, c2, x3, y3, z3, c3, 0.85f);
		}

		HaloGeometry.QuadSink solid() {
			return (x0, y0, z0, c0, x1, y1, z1, c1, x2, y2, z2, c2, x3, y3, z3, c3) ->
					addWorld(x0, y0, z0, c0, x1, y1, z1, c1, x2, y2, z2, c2, x3, y3, z3, c3);
		}

		private void addRel(float x0, float y0, float z0, int c0, float x1, float y1, float z1, int c1,
				float x2, float y2, float z2, int c2, float x3, float y3, float z3, int c3, float alphaMul) {
			// camera-relative already
			double[] p0 = proj.projectRel(x0, y0, z0);
			double[] p1 = proj.projectRel(x1, y1, z1);
			double[] p2 = proj.projectRel(x2, y2, z2);
			double[] p3 = proj.projectRel(x3, y3, z3);
			push(p0, p1, p2, p3, c0, c1, c2, c3, alphaMul);
		}

		private void addWorld(float x0, float y0, float z0, int c0, float x1, float y1, float z1, int c1,
				float x2, float y2, float z2, int c2, float x3, float y3, float z3, int c3) {
			push(proj.project(x0, y0, z0), proj.project(x1, y1, z1), proj.project(x2, y2, z2), proj.project(x3, y3, z3), c0, c1, c2, c3, 1f);
		}

		void face(HaloGeometry.QuadSink sink, double ax, double ay, double az, double bx, double by, double bz,
				double cx, double cy, double cz, double dx, double dy, double dz, int color) {
			sink.quad((float) ax, (float) ay, (float) az, color, (float) bx, (float) by, (float) bz, color,
					(float) cx, (float) cy, (float) cz, color, (float) dx, (float) dy, (float) dz, color);
		}

		void flatLine(HaloGeometry.QuadSink sink, double ax, double az, double bx, double bz, float w, int color, boolean world) {
			double dx = bx - ax;
			double dz = bz - az;
			double l = Math.sqrt(dx * dx + dz * dz);
			if (l < 1e-6) return;
			double nx = -dz / l * w;
			double nz = dx / l * w;
			face(sink, ax - nx, 0, az - nz, ax + nx, 0, az + nz, bx + nx, 0, bz + nz, bx - nx, 0, bz - nz, color);
		}

		private void push(double[] p0, double[] p1, double[] p2, double[] p3, int c0, int c1, int c2, int c3, float alphaMul) {
			if (p0 == null || p1 == null || p2 == null || p3 == null) return;
			if (count == depth.length) {
				int cap = count * 2;
				xy = Arrays.copyOf(xy, cap * 8);
				col = Arrays.copyOf(col, cap * 4);
				depth = Arrays.copyOf(depth, cap);
			}
			int b = count * 8;
			xy[b] = (float) p0[0];
			xy[b + 1] = (float) p0[1];
			xy[b + 2] = (float) p1[0];
			xy[b + 3] = (float) p1[1];
			xy[b + 4] = (float) p2[0];
			xy[b + 5] = (float) p2[1];
			xy[b + 6] = (float) p3[0];
			xy[b + 7] = (float) p3[1];
			int k = count * 4;
			col[k] = mulAlpha(c0, alphaMul);
			col[k + 1] = mulAlpha(c1, alphaMul);
			col[k + 2] = mulAlpha(c2, alphaMul);
			col[k + 3] = mulAlpha(c3, alphaMul);
			depth[count] = (float) ((p0[2] + p1[2] + p2[2] + p3[2]) * 0.25);
			count++;
		}

		private static int mulAlpha(int c, float m) {
			float a = ((c >>> 24) & 0xFF) * m * Ui.alpha;
			return (Math.round(a) << 24) | (c & 0xFFFFFF);
		}

		Element build(ScreenRectangle bounds) {
			Integer[] order = new Integer[count];
			for (int i = 0; i < count; i++) order[i] = i;
			Arrays.sort(order, (a, b) -> Float.compare(depth[b], depth[a]));
			float[] sxy = new float[count * 8];
			int[] scol = new int[count * 4];
			for (int i = 0; i < count; i++) {
				int src = order[i];
				System.arraycopy(xy, src * 8, sxy, i * 8, 8);
				System.arraycopy(col, src * 4, scol, i * 4, 4);
			}
			return new Element(sxy, scol, count, bounds);
		}
	}

	/** One GUI element holding every projected quad, already sorted back to front. */
	record Element(float[] xy, int[] col, int quads, ScreenRectangle bounds) implements GuiElementRenderState {
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
}
