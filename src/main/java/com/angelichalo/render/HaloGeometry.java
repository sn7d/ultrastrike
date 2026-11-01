package com.angelichalo.render;

import java.util.List;

import com.angelichalo.config.ColorSpec;
import com.angelichalo.config.HaloConfig;
import com.angelichalo.config.HaloOptions.Motion;
import com.angelichalo.config.HaloOptions.Style;
import com.angelichalo.config.HaloOptions.TrailStyle;

/**
 * Builds all halo geometry as quads in camera-relative coordinates. It only talks to a small {@link QuadSink}
 * interface, so the maths is independent of Minecraft's rendering API. Render thread only.
 */
public final class HaloGeometry {
	private HaloGeometry() { }

	/** Receives one quad (four corners, each with its own ARGB colour). */
	public interface QuadSink {
		void quad(float x0, float y0, float z0, int c0,
				float x1, float y1, float z1, int c1,
				float x2, float y2, float z2, int c2,
				float x3, float y3, float z3, int c3);
	}

	/** Everything the geometry needs to know about this frame. */
	public record Frame(
			double cx, double cy, double cz,        // player feet relative to the camera
			double camX, double camY, double camZ,  // camera world position (for world-anchored effects)
			double playerHeight,
			double time,
			double health,
			double pixelScale,                      // world units per 1080p pixel at distance 1
			double speed,                           // 0 idle .. ~1 sprinting
			double damageFlash,                     // 1 just hurt .. 0
			List<HaloState.JumpRing> jumpRings
	) { }

	private static final int MAX = 361;
	private static final float[] PX = new float[MAX];
	private static final float[] PY = new float[MAX];
	private static final float[] PZ = new float[MAX];
	private static final int[] COL = new int[MAX];
	private static final float[] QX = new float[MAX];
	private static final float[] QY = new float[MAX];
	private static final float[] QZ = new float[MAX];
	private static final int[] QCOL = new int[MAX];
	private static final float[] OX = new float[MAX];
	private static final float[] OY = new float[MAX];
	private static final float[] OZ = new float[MAX];

	// effective bloom for the current build (0 = off)
	private static double ringBloom;
	private static double trailBloom;
	private static double extrasBloom;
	private static double bloomWidth = 16;

	/**
	 * @param core translucent pass (crisp shapes)
	 * @param glow additive pass (bloom)
	 */
	public static void build(HaloConfig c, Frame f, QuadSink core, QuadSink glow) {
		ringBloom = c.bloomFor(c.ringBloom);
		trailBloom = c.bloomFor(c.trailBloom);
		extrasBloom = c.bloomFor(c.extrasBloom);
		bloomWidth = c.bloomRadius;
		double spin = Math.toRadians(c.spinSpeed * f.time());
		double radiusScale = c.speedReactive ? 1.0 + c.speedReactiveAmount * Math.min(f.speed(), 1.5) * 0.6 : 1.0;
		double flash = c.damageFlash ? f.damageFlash() : 0;
		int segs = Math.min(c.segments, MAX - 1);

		List<HaloMotion.Ring> rings = HaloMotion.rings(c, f.time(), f.playerHeight());

		// ---- scan trail (drawn first so the ring sits on top) ----
		if (c.motion == Motion.SCAN && c.trailEnabled) {
			ColorSpec spec = c.trailMatchRing ? c.ringColor : c.trailColor;

			if (c.trailStyle != TrailStyle.ECHOES) {
				smear(c, f, spec, spin, radiusScale, core, glow);
			}

			if (c.trailStyle != TrailStyle.SMEAR) {
				for (int e = 1; e <= c.trailEchoes; e++) {
					double k = (double) e / (c.trailEchoes + 1);
					HaloMotion.Scan sc = HaloMotion.scan(c, f.time() - c.trailLength * k, f.playerHeight());
					double a = Math.pow(1.0 - k, 1.4);
					sample(c, f, segs, sc.y(), sc.radius() * radiusScale, 0, spin, spec, sc.frac(), a, 0, 0);
					bloom(segs, bloomWidth, f.pixelScale(), glow, trailBloom);
					ribbon(segs, c.lineWidth, f.pixelScale(), core, 1.0, false, 0, 0);
				}
			}
		}

		// ---- main rings ----
		double firstY = c.heightOffset;
		double firstR = c.radius * radiusScale;
		boolean first = true;

		for (HaloMotion.Ring ring : rings) {
			if (ring.alpha() <= 0.003 || ring.radius() <= 0.0) continue;
			double r = ring.radius() * radiusScale;

			if (first) {
				firstY = ring.y();
				firstR = r;
				first = false;
			}

			double alphaBoost = c.speedReactive ? Math.min(1.0, ring.alpha() * (0.8 + 0.3 * f.speed())) : ring.alpha();
			int n = c.style == Style.ORBS ? c.orbCount : segs;
			sample(c, f, n, ring.y(), r, ring.wave(), spin, c.ringColor, ring.heightFrac(), alphaBoost, flash, c.damageColor);
			drawStyle(c, f, n, ring.y(), core, glow);
		}

		// ---- extras ----
		if (flash > 0) {
			// shockwave ring
			double p = 1.0 - flash;
			double r = firstR * (1.0 + 1.6 * easeOut(p));
			ringLine(c, f, segs, firstY, r, c.damageColor, flash * 0.9, core, glow, 2.5);
		}

		if (c.jumpRings) {
			for (HaloState.JumpRing jr : f.jumpRings()) {
				double p = (f.time() - jr.start()) / HaloState.JUMP_RING_LIFE;
				if (p < 0 || p > 1) continue;
				double r = c.jumpRingSize * (0.15 + 0.85 * easeOut(p));
				double a = Math.pow(1.0 - p, 1.5);
				int col = HaloColors.color(c.extrasColor, 0.0, 0.0, f.time(), f.health(), 1.0) & 0xFFFFFF;
				Frame anchored = new Frame(jr.x() - f.camX(), jr.y() - f.camY(), jr.z() - f.camZ(),
						f.camX(), f.camY(), f.camZ(), f.playerHeight(), f.time(), f.health(), f.pixelScale(),
						f.speed(), 0, f.jumpRings());
				ringLine(c, anchored, Math.min(segs, 96), 0.03, r, col, a, core, glow, 2.0);
			}
		}

		if (c.embers) {
			embers(c, f, firstY, firstR, spin, core, glow);
		}

		if (c.helix) {
			helix(c, f, core, glow);
		}
	}

	// ------------------------------------------------------------------------------------------------
	// sampling

	private static void sample(HaloConfig c, Frame f, int n, double y, double radius, double wave, double spin,
			ColorSpec spec, double heightFrac, double alpha, double flash, int flashColor) {
		double tiltX = Math.toRadians(c.tilt);
		double tiltZ = 0;

		if (c.motion == Motion.GYRO) {
			double w = f.time() * c.gyroSpeed * Math.PI * 2;
			tiltX += Math.toRadians(c.gyroAngle) * Math.sin(w);
			tiltZ += Math.toRadians(c.gyroAngle) * Math.cos(w);
		}

		double cosT = Math.cos(tiltX);
		double sinT = Math.sin(tiltX);
		double cosZ = Math.cos(tiltZ);
		double sinZ = Math.sin(tiltZ);

		for (int i = 0; i <= n; i++) {
			double u = (double) i / n;
			double a = u * Math.PI * 2 + spin;
			double lx = Math.cos(a) * radius;
			double lz = Math.sin(a) * radius;
			double ly = wave != 0 ? wave * Math.sin(a * c.waveFrequency - f.time() * c.waveSpeed * Math.PI * 2) : 0;
			// tilt around X, then around Z
			double ty = ly * cosT - lz * sinT;
			double tz = ly * sinT + lz * cosT;
			double tx = lx * cosZ - ty * sinZ;
			ty = lx * sinZ + ty * cosZ;
			PX[i] = (float) (f.cx() + tx);
			PY[i] = (float) (f.cy() + y + ty);
			PZ[i] = (float) (f.cz() + tz);
			int col = HaloColors.color(spec, u, heightFrac, f.time(), f.health(), alpha);

			if (flash > 0) {
				int rgb = HaloColors.lerpRgb(col & 0xFFFFFF, flashColor, flash * 0.85);
				int na = Math.min(255, (int) (((col >>> 24) & 0xFF) * (1.0 + 0.5 * flash)));
				col = (na << 24) | rgb;
			}

			COL[i] = col;
		}
	}

	private static void drawStyle(HaloConfig c, Frame f, int n, double y, QuadSink core, QuadSink glow) {
		double ps = f.pixelScale();
		double rb = ringBloom;
		double orbGlow = Math.max(c.glowStrength, rb);

		switch (c.style) {
			case GLOW_LINE -> {
				bloom(n, Math.max(c.glowWidth, rb > 0 ? bloomWidth : 0), ps, glow, Math.min(1.4, c.glowStrength + rb * 0.5));
				ribbon(n, c.lineWidth, ps, core, 1.0, false, 0, 0);
			}
			case AURA -> {
				curtain(n, c.auraHeight, core, 0.55);
				if (rb > 0) curtain(n, c.auraHeight, glow, 0.35 * Math.min(1.0, rb));
				bloom(n, bloomWidth, ps, glow, rb);
				ribbon(n, c.lineWidth, ps, core, 1.0, false, 0, 0);
			}
			case DISC -> {
				disc(n, (float) f.cx(), (float) (f.cy() + y), (float) f.cz(), core);
				bloom(n, bloomWidth, ps, glow, rb);
				ribbon(n, c.lineWidth, ps, core, 1.0, false, 0, 0);
			}
			case DASHED -> {
				if (rb > 0.01) {
					ribbon(n, bloomWidth, ps, glow, rb * 0.45, true, c.dashCount, c.dashFill);
					ribbon(n, bloomWidth * 0.4, ps, glow, Math.min(0.95, rb * 0.9), true, c.dashCount, c.dashFill);
				}
				ribbon(n, c.lineWidth * 1.5, ps, core, 1.0, true, c.dashCount, c.dashFill);
			}
			case ORBS -> {
				for (int i = 0; i < n; i++) {
					orb(PX[i], PY[i], PZ[i], c.orbSize, COL[i], orbGlow, core, glow);
				}
			}
			case SONAR -> {
				sonar(c, f, n, (float) f.cx(), (float) (f.cy() + y), (float) f.cz(), core, glow);
				bloom(n, bloomWidth, ps, glow, rb);
				ribbon(n, c.lineWidth, ps, core, 1.0, false, 0, 0);
			}
			case CROWN -> {
				crown(c, f, n, core, glow);
				bloom(n, bloomWidth, ps, glow, rb);
				ribbon(n, c.lineWidth, ps, core, 1.0, false, 0, 0);
			}
			case GALAXY -> galaxy(c, f, y, core, glow);
			case LIGHTNING -> {
				lightning(c, f, n);
				bloom(n, Math.max(c.glowWidth, rb > 0 ? bloomWidth : 0), ps, glow, Math.min(1.4, c.glowStrength + rb * 0.5));
				ribbon(n, c.lineWidth, ps, core, 1.0, false, 0, 0);
			}
			default -> {
				bloom(n, bloomWidth, ps, glow, rb);
				ribbon(n, c.lineWidth, ps, core, 1.0, false, 0, 0);
			}
		}
	}

	/** Radar disc: faint base plus a bright wedge that sweeps around. */
	private static void sonar(HaloConfig c, Frame f, int n, float cx, float cy, float cz, QuadSink core, QuadSink glow) {
		double sweep = frac(f.time() * c.sonarSpeed);

		for (int i = 0; i < n; i++) {
			int j = i + 1;
			double u = (i + 0.5) / n;
			double behind = frac(sweep - u); // 0 right at the beam, growing behind it
			double beam = Math.exp(-behind * 7.0);
			int rimI = HaloColors.withAlpha(COL[i], 0.08 + 0.75 * beam);
			int rimJ = HaloColors.withAlpha(COL[j], 0.08 + 0.75 * beam);
			int mid = HaloColors.withAlpha(COL[i], 0.04 + 0.25 * beam);
			core.quad(cx, cy, cz, mid, cx, cy, cz, mid, PX[j], PY[j], PZ[j], rimJ, PX[i], PY[i], PZ[i], rimI);

			if (beam > 0.6) {
				glow.quad(cx, cy, cz, HaloColors.withAlpha(COL[i], 0.15 * beam), cx, cy, cz, HaloColors.withAlpha(COL[i], 0.15 * beam),
						PX[j], PY[j], PZ[j], HaloColors.withAlpha(COL[j], 0.5 * beam), PX[i], PY[i], PZ[i], HaloColors.withAlpha(COL[i], 0.5 * beam));
			}
		}
	}

	/** Upward triangular spikes standing on the ring, glowing at the tips. */
	private static void crown(HaloConfig c, Frame f, int n, QuadSink core, QuadSink glow) {
		int spikes = c.crownSpikes;

		for (int k = 0; k < spikes; k++) {
			double centre = (k + 0.5) / spikes * n;
			int i0 = (int) Math.floor(centre - n / (double) spikes * 0.3);
			int i1 = (int) Math.ceil(centre + n / (double) spikes * 0.3);
			int ic = (int) Math.round(centre);
			i0 = Math.max(0, i0);
			i1 = Math.min(n, i1);
			ic = Math.max(0, Math.min(n, ic));
			float h = (float) (c.crownHeight * (0.85 + 0.15 * Math.sin(f.time() * 3.0 + k)));
			float tx = PX[ic];
			float ty = PY[ic] + h;
			float tz = PZ[ic];
			int base = COL[ic];
			int tip = HaloColors.withAlpha(base, 0.35);
			core.quad(PX[i0], PY[i0], PZ[i0], base, PX[i1], PY[i1], PZ[i1], base, tx, ty, tz, tip, tx, ty, tz, tip);
			if (ringBloom > 0.01) {
				float gm = (float) Math.min(1.0, ringBloom * 0.4);
				glow.quad(PX[i0], PY[i0], PZ[i0], HaloColors.withAlpha(base, gm), PX[i1], PY[i1], PZ[i1], HaloColors.withAlpha(base, gm),
						tx, ty, tz, HaloColors.withAlpha(tip, gm), tx, ty, tz, HaloColors.withAlpha(tip, gm));
			}
			orb(tx, ty, tz, 0.03, base, Math.max(c.glowStrength * 0.7, ringBloom * 0.8), core, glow);
		}
	}

	/** Spiral arms of glowing stars rotating around the player. */
	private static void galaxy(HaloConfig c, Frame f, double y, QuadSink core, QuadSink glow) {
		final int dots = 16;
		double spin = Math.toRadians(c.spinSpeed * f.time()) + f.time() * 0.35;

		for (int a = 0; a < c.galaxyArms; a++) {
			for (int j = 1; j <= dots; j++) {
				double t = (double) j / dots;
				double ang = a * Math.PI * 2 / c.galaxyArms + t * 2.6 + spin;
				double r = c.radius * t;
				int col = HaloColors.color(c.ringColor, t, 0, f.time(), f.health(), 0.35 + 0.65 * t);
				float x = (float) (f.cx() + Math.cos(ang) * r);
				float yy = (float) (f.cy() + y);
				float z = (float) (f.cz() + Math.sin(ang) * r);
				orb(x, yy, z, 0.025 + 0.035 * t, col, Math.max(c.glowStrength * 0.6, ringBloom * 0.8), core, glow);
			}
		}
	}

	/** Jitters the sampled ring radially, re-rolling ~14 times a second, with occasional big sparks. */
	private static void lightning(HaloConfig c, Frame f, int n) {
		double step = Math.floor(f.time() * 14.0);
		double amp = 0.02 + 0.18 * c.lightningIntensity;

		for (int i = 0; i <= n; i++) {
			int idx = i == n ? 0 : i; // keep the loop closed
			double j = hash(idx * 0.913 + step * 17.17) - 0.5;
			double spark = hash(idx * 2.71 + step * 3.3) > 0.97 ? 2.5 : 1.0;
			double dx = PX[i] - f.cx();
			double dz = PZ[i] - f.cz();
			double k = 1.0 + j * amp * spark;
			PX[i] = (float) (f.cx() + dx * k);
			PZ[i] = (float) (f.cz() + dz * k);
			PY[i] += (float) ((hash(idx * 5.1 + step) - 0.5) * amp * 0.6);
		}
	}

	/** A single glowing line ring of a fixed colour (shockwaves, jump rings). */
	private static void ringLine(HaloConfig c, Frame f, int n, double y, double r, int rgb, double alpha,
			QuadSink core, QuadSink glow, double width) {
		int col = ((int) Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24) | (rgb & 0xFFFFFF);

		for (int i = 0; i <= n; i++) {
			double a = Math.PI * 2 * i / n;
			PX[i] = (float) (f.cx() + Math.cos(a) * r);
			PY[i] = (float) (f.cy() + y);
			PZ[i] = (float) (f.cz() + Math.sin(a) * r);
			COL[i] = col;
		}

		bloom(n, Math.max(8.0, Math.max(c.glowWidth, bloomWidth)), f.pixelScale(), glow, Math.min(1.2, 0.5 + extrasBloom * 0.6));
		ribbon(n, width, f.pixelScale(), core, 1.0, false, 0, 0);
	}

	// ------------------------------------------------------------------------------------------------
	// scan trail

	/** Smooth curtain from the scanning ring back along its recent path, fading with age. */
	private static void smear(HaloConfig c, Frame f, ColorSpec spec, double spin, double radiusScale, QuadSink sink, QuadSink glow) {
		float glowMul = (float) Math.min(1.0, trailBloom * 0.55);
		final int steps = 18;
		int n = Math.min(c.segments, 96);

		HaloMotion.Scan s0 = HaloMotion.scan(c, f.time(), f.playerHeight());
		sample(c, f, n, s0.y(), s0.radius() * radiusScale, 0, spin, spec, s0.frac(), 0.8, 0, 0);
		copyToQ(n, 1.0);

		for (int k = 1; k <= steps; k++) {
			double age = (double) k / steps;
			HaloMotion.Scan s = HaloMotion.scan(c, f.time() - c.trailLength * age, f.playerHeight());
			double fade = Math.pow(1.0 - age, 2.2) * 0.8;
			sample(c, f, n, s.y(), s.radius() * radiusScale, 0, spin, spec, s.frac(), fade, 0, 0);

			for (int i = 0; i < n; i++) {
				int j = i + 1;
				sink.quad(QX[i], QY[i], QZ[i], QCOL[i],
						QX[j], QY[j], QZ[j], QCOL[j],
						PX[j], PY[j], PZ[j], COL[j],
						PX[i], PY[i], PZ[i], COL[i]);
				if (glowMul > 0.01f) {
					// the same curtain again, additively, so the trail glows
					glow.quad(QX[i], QY[i], QZ[i], HaloColors.withAlpha(QCOL[i], glowMul),
							QX[j], QY[j], QZ[j], HaloColors.withAlpha(QCOL[j], glowMul),
							PX[j], PY[j], PZ[j], HaloColors.withAlpha(COL[j], glowMul),
							PX[i], PY[i], PZ[i], HaloColors.withAlpha(COL[i], glowMul));
				}
			}

			copyToQ(n, 1.0);
		}
	}

	private static void copyToQ(int n, double alphaMul) {
		for (int i = 0; i <= n; i++) {
			QX[i] = PX[i];
			QY[i] = PY[i];
			QZ[i] = PZ[i];
			QCOL[i] = alphaMul >= 0.999 ? COL[i] : HaloColors.withAlpha(COL[i], alphaMul);
		}
	}

	// ------------------------------------------------------------------------------------------------
	// primitives

	/**
	 * Camera-facing strip along the sampled points with constant on-screen width. With {@code alphaMul < 1}
	 * (glow) the strip fades to transparent at both edges.
	 */
	private static void ribbon(int n, double widthPx, double pixelScale, QuadSink sink, double alphaMul,
			boolean dashed, int dashCount, double dashFill) {
		boolean soft = alphaMul < 0.999;

		for (int i = 0; i <= n; i++) {
			int prev = i == 0 ? n - 1 : i - 1;
			int next = i == n ? 1 : i + 1;
			double tx = PX[next] - PX[prev];
			double ty = PY[next] - PY[prev];
			double tz = PZ[next] - PZ[prev];
			double vx = PX[i];
			double vy = PY[i];
			double vz = PZ[i];
			double dist = Math.sqrt(vx * vx + vy * vy + vz * vz);
			// side = tangent x view direction
			double sx = ty * vz - tz * vy;
			double sy = tz * vx - tx * vz;
			double sz = tx * vy - ty * vx;
			double len = Math.sqrt(sx * sx + sy * sy + sz * sz);

			if (len < 1e-9) {
				sx = 0;
				sy = 1;
				sz = 0;
				len = 1;
			}

			double half = Math.max(widthPx * 0.5 * pixelScale * Math.max(dist, 0.05), 0.001);
			OX[i] = (float) (sx / len * half);
			OY[i] = (float) (sy / len * half);
			OZ[i] = (float) (sz / len * half);
		}

		// anti-aliasing feather, ~1 screen pixel wide, as a fraction of the half width
		double featherRatio = soft ? 0 : Math.min(1.5, 1.6 / Math.max(widthPx, 0.5));

		for (int i = 0; i < n; i++) {
			if (dashed) {
				double mid = (i + 0.5) / n * dashCount;
				if (mid - Math.floor(mid) > dashFill) continue;
			}

			int j = i + 1;

			if (soft) {
				int ci = HaloColors.withAlpha(COL[i], alphaMul);
				int cj = HaloColors.withAlpha(COL[j], alphaMul);
				int ei = ci & 0x00FFFFFF;
				int ej = cj & 0x00FFFFFF;
				sink.quad(PX[i], PY[i], PZ[i], ci,
						PX[i] + OX[i], PY[i] + OY[i], PZ[i] + OZ[i], ei,
						PX[j] + OX[j], PY[j] + OY[j], PZ[j] + OZ[j], ej,
						PX[j], PY[j], PZ[j], cj);
				sink.quad(PX[i], PY[i], PZ[i], ci,
						PX[j], PY[j], PZ[j], cj,
						PX[j] - OX[j], PY[j] - OY[j], PZ[j] - OZ[j], ej,
						PX[i] - OX[i], PY[i] - OY[i], PZ[i] - OZ[i], ei);
			} else {
				sink.quad(PX[i] - OX[i], PY[i] - OY[i], PZ[i] - OZ[i], COL[i],
						PX[i] + OX[i], PY[i] + OY[i], PZ[i] + OZ[i], COL[i],
						PX[j] + OX[j], PY[j] + OY[j], PZ[j] + OZ[j], COL[j],
						PX[j] - OX[j], PY[j] - OY[j], PZ[j] - OZ[j], COL[j]);

				// soft outer edges so thin lines look smooth instead of jagged
				float k = (float) (1.0 + featherRatio);
				int ei = COL[i] & 0x00FFFFFF;
				int ej = COL[j] & 0x00FFFFFF;
				sink.quad(PX[i] + OX[i], PY[i] + OY[i], PZ[i] + OZ[i], COL[i],
						PX[i] + OX[i] * k, PY[i] + OY[i] * k, PZ[i] + OZ[i] * k, ei,
						PX[j] + OX[j] * k, PY[j] + OY[j] * k, PZ[j] + OZ[j] * k, ej,
						PX[j] + OX[j], PY[j] + OY[j], PZ[j] + OZ[j], COL[j]);
				sink.quad(PX[i] - OX[i], PY[i] - OY[i], PZ[i] - OZ[i], COL[i],
						PX[j] - OX[j], PY[j] - OY[j], PZ[j] - OZ[j], COL[j],
						PX[j] - OX[j] * k, PY[j] - OY[j] * k, PZ[j] - OZ[j] * k, ej,
						PX[i] - OX[i] * k, PY[i] - OY[i] * k, PZ[i] - OZ[i] * k, ei);
			}
		}
	}

	/** Two-layer bloom: a wide faint halo plus a tighter, brighter one. */
	private static void bloom(int n, double widthPx, double pixelScale, QuadSink glow, double strength) {
		if (strength <= 0.01) return;
		ribbon(n, widthPx, pixelScale, glow, strength * 0.45, false, 0, 0);
		ribbon(n, widthPx * 0.4, pixelScale, glow, Math.min(0.95, strength * 0.9), false, 0, 0);
	}

	/** Vertical wall that fades out upwards. */
	private static void curtain(int n, double height, QuadSink sink, double alpha) {
		float h = (float) height;

		for (int i = 0; i < n; i++) {
			int j = i + 1;
			int ci = HaloColors.withAlpha(COL[i], alpha);
			int cj = HaloColors.withAlpha(COL[j], alpha);
			sink.quad(PX[i], PY[i], PZ[i], ci,
					PX[j], PY[j], PZ[j], cj,
					PX[j], PY[j] + h, PZ[j], cj & 0x00FFFFFF,
					PX[i], PY[i] + h, PZ[i], ci & 0x00FFFFFF);
		}
	}

	/** Filled disc, faint in the middle and stronger towards the rim. */
	private static void disc(int n, float cx, float cy, float cz, QuadSink sink) {
		for (int i = 0; i < n; i++) {
			int j = i + 1;
			int ci = HaloColors.withAlpha(COL[i], 0.45);
			int cj = HaloColors.withAlpha(COL[j], 0.45);
			int mid = HaloColors.withAlpha(COL[i], 0.06);
			sink.quad(cx, cy, cz, mid, cx, cy, cz, mid,
					PX[j], PY[j], PZ[j], cj,
					PX[i], PY[i], PZ[i], ci);
		}
	}

	/** Soft round billboard with an additive bloom around it. */
	private static void orb(float x, float y, float z, double size, int color, double glowStrength,
			QuadSink core, QuadSink glow) {
		double vx = x;
		double vy = y;
		double vz = z;
		// right = normalize(view x worldUp), up = right x view
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

		ux /= ul;
		uy /= ul;
		uz /= ul;

		softDisc(core, x, y, z, rx, rz, ux, uy, uz, size, color);
		if (glowStrength > 0.01) {
			softDisc(glow, x, y, z, rx, rz, ux, uy, uz, size * 1.9, HaloColors.withAlpha(color, glowStrength * 0.55));
		}
	}

	/** A soft additive glow spot facing the camera (used behind text letters). */
	public static void glowSpot(QuadSink glow, float x, float y, float z, double size, int color) {
		double vx = x;
		double vy = y;
		double vz = z;
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
		softDisc(glow, x, y, z, rx, rz, ux / ul, uy / ul, uz / ul, size, color);
	}

	/** Round billboard: bright core, smooth falloff to a transparent rim. */
	private static void softDisc(QuadSink sink, float x, float y, float z,
			double rx, double rz, double ux, double uy, double uz, double size, int color) {
		final int seg = 16;
		final double midR = 0.45;
		int mid = HaloColors.withAlpha(color, 0.7);
		int rim = color & 0x00FFFFFF;

		for (int k = 0; k < seg; k++) {
			double a0 = Math.PI * 2 * k / seg;
			double a1 = Math.PI * 2 * (k + 1) / seg;
			double c0 = Math.cos(a0);
			double s0 = Math.sin(a0);
			double c1 = Math.cos(a1);
			double s1 = Math.sin(a1);
			float mx0 = (float) (x + (c0 * rx + s0 * ux) * size * midR);
			float my0 = (float) (y + s0 * uy * size * midR);
			float mz0 = (float) (z + (c0 * rz + s0 * uz) * size * midR);
			float mx1 = (float) (x + (c1 * rx + s1 * ux) * size * midR);
			float my1 = (float) (y + s1 * uy * size * midR);
			float mz1 = (float) (z + (c1 * rz + s1 * uz) * size * midR);
			float ox0 = (float) (x + (c0 * rx + s0 * ux) * size);
			float oy0 = (float) (y + s0 * uy * size);
			float oz0 = (float) (z + (c0 * rz + s0 * uz) * size);
			float ox1 = (float) (x + (c1 * rx + s1 * ux) * size);
			float oy1 = (float) (y + s1 * uy * size);
			float oz1 = (float) (z + (c1 * rz + s1 * uz) * size);
			sink.quad(x, y, z, color, x, y, z, color, mx1, my1, mz1, mid, mx0, my0, mz0, mid);
			sink.quad(mx0, my0, mz0, mid, mx1, my1, mz1, mid, ox1, oy1, oz1, rim, ox0, oy0, oz0, rim);
		}
	}

	// ------------------------------------------------------------------------------------------------
	// extras

	/** Glowing embers that rise off the ring, drift and fade. Stateless: positions are pure functions of time. */
	private static void embers(HaloConfig c, Frame f, double baseY, double radius, double spin, QuadSink core, QuadSink glow) {
		double riseHeight = f.playerHeight() * 0.9 + 0.3;

		for (int k = 0; k < c.emberCount; k++) {
			double h1 = hash(k * 1.731 + 0.13);
			double h2 = hash(k * 7.117 + 3.7);
			double h3 = hash(k * 3.33 + 9.1);
			double phase = frac(f.time() * c.emberRise * (0.25 + 0.2 * h3) + h1);
			double ang = h2 * Math.PI * 2 + spin + phase * 0.8;
			double r = radius * (0.85 + 0.25 * h3);
			double y = baseY + phase * riseHeight;
			double twinkle = 0.75 + 0.25 * Math.sin(f.time() * (5.0 + 4.0 * h2) + k);
			double a = Math.sin(Math.PI * phase) * twinkle;
			int col = HaloColors.color(c.extrasColor, h1, phase, f.time(), f.health(), a);
			float x = (float) (f.cx() + Math.cos(ang) * r);
			float yy = (float) (f.cy() + y);
			float z = (float) (f.cz() + Math.sin(ang) * r);
			orb(x, yy, z, c.emberSize * (1.0 - 0.5 * phase), col, Math.max(0.35, extrasBloom), core, glow);
		}
	}

	/** Orbs spiralling around the body in a helix, each with a fading comet tail. */
	private static void helix(HaloConfig c, Frame f, QuadSink core, QuadSink glow) {
		final int tail = 12;
		double h = f.playerHeight();

		for (int s = 0; s < c.helixStrands; s++) {
			double offset = (double) s / c.helixStrands;

			for (int k = tail; k >= 0; k--) {
				double t = f.time() - k * 0.025;
				double ang = Math.PI * 2 * (t * c.helixSpeed * 1.5 + offset);
				double vert = 0.5 - 0.5 * Math.cos(Math.PI * 2 * (t * c.helixSpeed * 0.35));
				double age = (double) k / (tail + 1);
				int col = HaloColors.color(c.extrasColor, offset, vert, t, f.health(), Math.pow(1.0 - age, 1.5));
				float x = (float) (f.cx() + Math.cos(ang) * c.helixRadius);
				float y = (float) (f.cy() + 0.1 + vert * (h - 0.1));
				float z = (float) (f.cz() + Math.sin(ang) * c.helixRadius);
				orb(x, y, z, 0.045 * (1.0 - 0.55 * age), col, k == 0 ? Math.max(0.5, extrasBloom) : Math.max(0.12, extrasBloom * 0.3), core, glow);
			}
		}
	}

	// ------------------------------------------------------------------------------------------------

	private static double easeOut(double p) {
		double q = 1.0 - Math.max(0, Math.min(1, p));
		return 1.0 - q * q * q;
	}

	private static double frac(double x) {
		return x - Math.floor(x);
	}

	private static double hash(double n) {
		double s = Math.sin(n * 127.1 + 311.7) * 43758.5453;
		return s - Math.floor(s);
	}
}
