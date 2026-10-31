package com.angelichalo.render;

import com.angelichalo.config.HaloConfig;

/** Pure colour maths (no Minecraft classes) so it is trivially testable. Colours are 0xAARRGGBB. */
public final class HaloColors {
	private HaloColors() { }

	/**
	 * @param u      position around the ring, 0..1
	 * @param height scan height, 0 (feet) .. 1 (head)
	 * @param time   seconds
	 * @param health own health fraction 0..1
	 * @param alpha  extra alpha multiplier 0..1
	 */
	public static int color(HaloConfig c, double u, double height, double time, double health, double alpha) {
		int rgb;
		double speed = c.colorSpeed;
		double reps = c.colorRepeats;

		switch (c.colorMode) {
			case GRADIENT -> rgb = lerpRgb(c.primaryColor, c.secondaryColor, triangle(u * reps + time * speed));
			case RAINBOW -> rgb = hsv(frac(u * reps - time * speed), c.saturation, c.brightness);
			case CHROMA -> rgb = hsv(frac(time * speed), c.saturation, c.brightness);
			case PULSE -> rgb = lerpRgb(c.primaryColor, c.secondaryColor, 0.5 - 0.5 * Math.cos(time * speed * Math.PI * 2));
			case HEIGHT -> rgb = lerpRgb(c.primaryColor, c.secondaryColor, clamp01(height));
			case COMET -> {
				// bright head travelling around the ring with an exponential tail
				double d = frac(time * speed - u * reps);
				double k = Math.exp(-d * 6.0);
				rgb = lerpRgb(c.secondaryColor, c.primaryColor, k);
				alpha *= 0.25 + 0.75 * k;
			}
			case HEALTH -> {
				double h = clamp01(health);
				// red (0) -> yellow (0.5) -> green (1)
				rgb = hsv(h / 3.0, c.saturation, c.brightness);
			}
			case SPARKLE -> {
				double cell = Math.floor(u * 64.0);
				double phase = hash(cell) * 6.2831853;
				double s = 0.5 + 0.5 * Math.sin(time * (2.0 + 6.0 * speed) + phase);
				rgb = lerpRgb(c.primaryColor, c.secondaryColor, s * s * s);
			}
			default -> rgb = c.primaryColor;
		}

		int a = (int) Math.round(clamp01(c.opacity * alpha) * 255.0);
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	public static int withAlpha(int argb, double alphaMul) {
		int a = (argb >>> 24) & 0xFF;
		int na = (int) Math.round(a * clamp01(alphaMul));
		return (na << 24) | (argb & 0xFFFFFF);
	}

	public static int lerpRgb(int a, int b, double t) {
		t = clamp01(t);
		int r = (int) Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = (int) Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return (r << 16) | (g << 8) | bl;
	}

	public static int hsv(double h, double s, double v) {
		h = frac(h) * 6.0;
		int i = (int) Math.floor(h);
		double f = h - i;
		double p = v * (1 - s);
		double q = v * (1 - s * f);
		double t = v * (1 - s * (1 - f));
		double r, g, b;

		switch (i % 6) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}

		return ((int) Math.round(r * 255) << 16) | ((int) Math.round(g * 255) << 8) | (int) Math.round(b * 255);
	}

	/** 0 -> 1 -> 0 over one period; makes gradients loop seamlessly around the ring. */
	static double triangle(double x) {
		double f = frac(x);
		return f < 0.5 ? f * 2.0 : 2.0 - f * 2.0;
	}

	static double frac(double x) {
		return x - Math.floor(x);
	}

	static double clamp01(double x) {
		return x < 0 ? 0 : (x > 1 ? 1 : x);
	}

	private static double hash(double n) {
		double s = Math.sin(n * 127.1 + 311.7) * 43758.5453;
		return s - Math.floor(s);
	}
}
