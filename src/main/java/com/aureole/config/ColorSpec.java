package com.aureole.config;

import com.aureole.config.HaloOptions.ColorMode;

/** A full colour definition. The ring, the scan trail and the extras each get their own. */
public final class ColorSpec {
	public ColorMode mode = ColorMode.SOLID;
	public int primary = 0xFF4B55;
	public int secondary = 0xFFD54F;
	public double opacity = 0.9;
	/** Speed multiplier for animated modes. */
	public double speed = 0.3;
	public double saturation = 0.85;
	public double brightness = 1.0;
	/** How many times gradients/rainbows repeat around the ring. */
	public int repeats = 1;

	public ColorSpec() { }

	public ColorSpec(ColorMode mode, int primary, int secondary, double opacity) {
		this.mode = mode;
		this.primary = primary;
		this.secondary = secondary;
		this.opacity = opacity;
	}

	public void sanitize() {
		if (mode == null) mode = ColorMode.SOLID;
		primary &= 0xFFFFFF;
		secondary &= 0xFFFFFF;
		opacity = HaloConfig.clamp(opacity, 0.05, 1.0);
		speed = HaloConfig.clamp(speed, 0.0, 4.0);
		saturation = HaloConfig.clamp(saturation, 0.0, 1.0);
		brightness = HaloConfig.clamp(brightness, 0.1, 1.0);
		repeats = HaloConfig.clamp(repeats, 1, 12);
	}
}
