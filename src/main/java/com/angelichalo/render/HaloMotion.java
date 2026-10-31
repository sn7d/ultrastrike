package com.angelichalo.render;

import java.util.ArrayList;
import java.util.List;

import com.angelichalo.config.HaloConfig;
import com.angelichalo.config.HaloOptions.Easing;

/** Turns the config + time into the list of rings to draw this frame. Pure maths, no Minecraft classes. */
public final class HaloMotion {
	private HaloMotion() { }

	/**
	 * One ring instance.
	 *
	 * @param y          height above the player's feet, blocks
	 * @param radius     blocks
	 * @param alpha      alpha multiplier 0..1
	 * @param heightFrac 0 (feet) .. 1 (head), used by the HEIGHT colour mode
	 * @param wave       vertical undulation amplitude, blocks
	 */
	public record Ring(double y, double radius, double alpha, double heightFrac, double wave) { }

	public static List<Ring> rings(HaloConfig c, double time, double playerHeight) {
		List<Ring> out = new ArrayList<>();
		double baseR = c.radius;
		double baseY = c.heightOffset;

		switch (c.motion) {
			case SCAN -> {
				for (int i = 0; i <= c.scanTrail; i++) {
					double t = time - i * c.scanTrailSpacing / Math.max(c.scanSpeed, 0.02);
					double f = scanFraction(c, t);
					double frac = c.scanMin + (c.scanMax - c.scanMin) * f;
					double y = baseY + frac * playerHeight;
					double r = baseR;
					if (c.scanContour) {
						r = baseR * contour(frac);
					}
					double a = i == 0 ? 1.0 : Math.pow(1.0 - (double) i / (c.scanTrail + 1), 1.6) * 0.7;
					addStack(out, c, y, r, a, frac, 0);
				}
			}
			case BREATHE -> {
				double s = 1.0 + c.breatheAmount * Math.sin(time * c.breatheSpeed * Math.PI * 2);
				double a = 0.8 + 0.2 * Math.sin(time * c.breatheSpeed * Math.PI * 2);
				addStack(out, c, baseY, baseR * s, a, 0, 0);
			}
			case HALO -> {
				double y = playerHeight + c.haloHeight + c.haloBob * Math.sin(time * Math.PI * 2 * 0.6);
				addStack(out, c, y, c.haloRadius, 1.0, 1.0, 0);
			}
			case RIPPLE -> {
				int n = c.rippleCount;
				for (int i = 0; i < n; i++) {
					double p = frac(time * c.rippleSpeed + (double) i / n);
					double r = baseR * (0.15 + 0.85 * p);
					// fade in quickly, fade out towards the edge
					double a = Math.min(1.0, p * 6.0) * Math.pow(1.0 - p, 1.3);
					addStack(out, c, baseY, r, a, p, 0);
				}
			}
			case WAVE -> addStack(out, c, baseY + c.waveAmplitude, baseR, 1.0, 0, c.waveAmplitude);
			default -> addStack(out, c, baseY, baseR, 1.0, 0, 0);
		}

		return out;
	}

	private static void addStack(List<Ring> out, HaloConfig c, double y, double r, double a, double hf, double wave) {
		for (int k = 0; k < c.ringCount; k++) {
			double fade = c.ringCount == 1 ? 1.0 : 1.0 - 0.5 * k / (c.ringCount - 1.0);
			out.add(new Ring(y + k * c.ringSpacing, r, a * fade, hf, wave));
		}
	}

	/** Scan position 0..1 for the given time, honouring speed, pause, direction and easing. */
	static double scanFraction(HaloConfig c, double time) {
		double travel = 1.0 / Math.max(c.scanSpeed, 0.02);
		double pause = c.scanPause;

		return switch (c.scanDirection) {
			case UP, DOWN -> {
				double period = travel + pause;
				double t = mod(time, period);
				double lin = t < travel ? t / travel : 1.0;
				double e = ease(c.scanEasing, lin);
				yield c.scanDirection == com.angelichalo.config.HaloOptions.ScanDirection.UP ? e : 1.0 - e;
			}
			default -> {
				// travel up (half), pause, travel down (half), pause
				double half = travel / 2.0;
				double period = 2 * half + 2 * pause;
				double t = mod(time, period);
				double lin;

				if (t < half) {
					lin = t / half;
				} else if (t < half + pause) {
					lin = 1.0;
				} else if (t < 2 * half + pause) {
					lin = 1.0 - (t - half - pause) / half;
				} else {
					lin = 0.0;
				}

				yield ease(c.scanEasing, lin);
			}
		};
	}

	static double ease(Easing e, double x) {
		x = Math.max(0, Math.min(1, x));

		return switch (e) {
			case LINEAR -> x;
			case SMOOTH -> x * x * x * (x * (x * 6 - 15) + 10);
			case BOUNCE -> bounceOut(x);
			case ELASTIC -> {
				if (x == 0 || x == 1) yield x;
				double c4 = (2 * Math.PI) / 3;
				yield Math.pow(2, -10 * x) * Math.sin((x * 10 - 0.75) * c4) + 1;
			}
			default -> 0.5 - 0.5 * Math.cos(Math.PI * x);
		};
	}

	private static double bounceOut(double x) {
		double n1 = 7.5625;
		double d1 = 2.75;

		if (x < 1 / d1) {
			return n1 * x * x;
		} else if (x < 2 / d1) {
			x -= 1.5 / d1;
			return n1 * x * x + 0.75;
		} else if (x < 2.5 / d1) {
			x -= 2.25 / d1;
			return n1 * x * x + 0.9375;
		} else {
			x -= 2.625 / d1;
			return n1 * x * x + 0.984375;
		}
	}

	/** Rough body silhouette: full width at legs/torso, narrower at the head. */
	private static double contour(double frac) {
		if (frac < 0.72) return 1.0;
		double t = Math.min(1.0, (frac - 0.72) / 0.28);
		return 1.0 - 0.35 * t;
	}

	private static double frac(double x) {
		return x - Math.floor(x);
	}

	private static double mod(double a, double b) {
		double r = a % b;
		return r < 0 ? r + b : r;
	}
}
