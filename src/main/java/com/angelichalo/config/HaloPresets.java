package com.angelichalo.config;

import java.util.List;
import java.util.function.Consumer;

import com.angelichalo.config.HaloOptions.ColorMode;
import com.angelichalo.config.HaloOptions.Easing;
import com.angelichalo.config.HaloOptions.Motion;
import com.angelichalo.config.HaloOptions.Style;

/** One-click looks. Each preset starts from defaults so results are predictable. */
public final class HaloPresets {
	private HaloPresets() { }

	public record Preset(String id, int swatch, Consumer<HaloConfig> apply) {
		public HaloConfig build(HaloConfig current) {
			HaloConfig c = new HaloConfig();
			// keep the user's visibility preferences
			c.enabled = current.enabled;
			c.showInFirstPerson = current.showInFirstPerson;
			c.showInThirdPerson = current.showInThirdPerson;
			c.hideWhenSneaking = current.hideWhenSneaking;
			c.hideWhenInvisible = current.hideWhenInvisible;
			c.throughWalls = current.throughWalls;
			c.previewInThirdPerson = current.previewInThirdPerson;
			apply.accept(c);
			c.sanitize();
			return c;
		}
	}

	public static final List<Preset> ALL = List.of(
			new Preset("classic", 0xFF4B55, c -> { }),
			new Preset("angelic", 0xFFE7A0, c -> {
				c.style = Style.GLOW_LINE;
				c.motion = Motion.HALO;
				c.colorMode = ColorMode.PULSE;
				c.primaryColor = 0xFFF4D6;
				c.secondaryColor = 0xFFC94A;
				c.lineWidth = 3.0;
				c.glowWidth = 14.0;
				c.glowStrength = 0.6;
				c.colorSpeed = 0.4;
			}),
			new Preset("scanner", 0x38E8FF, c -> {
				c.style = Style.GLOW_LINE;
				c.motion = Motion.SCAN;
				c.radius = 0.75;
				c.scanSpeed = 0.45;
				c.scanTrail = 5;
				c.scanContour = true;
				c.colorMode = ColorMode.HEIGHT;
				c.primaryColor = 0x38E8FF;
				c.secondaryColor = 0x7C4DFF;
			}),
			new Preset("inferno", 0xFF6A00, c -> {
				c.style = Style.AURA;
				c.radius = 1.0;
				c.auraHeight = 0.9;
				c.colorMode = ColorMode.GRADIENT;
				c.colorRepeats = 3;
				c.primaryColor = 0xFF2A00;
				c.secondaryColor = 0xFFC400;
				c.spinSpeed = 90;
			}),
			new Preset("frost", 0xA8E6FF, c -> {
				c.style = Style.ORBS;
				c.radius = 1.1;
				c.orbCount = 10;
				c.motion = Motion.WAVE;
				c.waveFrequency = 2;
				c.spinSpeed = 40;
				c.colorMode = ColorMode.SPARKLE;
				c.primaryColor = 0xA8E6FF;
				c.secondaryColor = 0xFFFFFF;
			}),
			new Preset("void", 0x8A2BE2, c -> {
				c.style = Style.DISC;
				c.radius = 1.4;
				c.motion = Motion.BREATHE;
				c.colorMode = ColorMode.PULSE;
				c.primaryColor = 0x5B00B5;
				c.secondaryColor = 0x12001F;
				c.opacity = 0.75;
			}),
			new Preset("toxic", 0x7CFF2B, c -> {
				c.style = Style.DASHED;
				c.radius = 1.2;
				c.spinSpeed = 120;
				c.ringCount = 3;
				c.ringSpacing = 0.18;
				c.colorMode = ColorMode.COMET;
				c.primaryColor = 0x7CFF2B;
				c.secondaryColor = 0x0E3D00;
			}),
			new Preset("sakura", 0xFF9EC7, c -> {
				c.style = Style.GLOW_LINE;
				c.motion = Motion.RIPPLE;
				c.radius = 2.5;
				c.rippleCount = 3;
				c.colorMode = ColorMode.GRADIENT;
				c.primaryColor = 0xFF9EC7;
				c.secondaryColor = 0xFFFFFF;
			}),
			new Preset("rainbow", 0xFF00FF, c -> {
				c.style = Style.GLOW_LINE;
				c.motion = Motion.SCAN;
				c.radius = 0.8;
				c.scanEasing = Easing.SMOOTH;
				c.scanTrail = 3;
				c.colorMode = ColorMode.RAINBOW;
				c.colorSpeed = 0.5;
			}),
			new Preset("radar", 0x00FF88, c -> {
				c.style = Style.LINE;
				c.radius = 3.0;
				c.motion = Motion.RIPPLE;
				c.rippleCount = 2;
				c.colorMode = ColorMode.COMET;
				c.primaryColor = 0x00FF88;
				c.secondaryColor = 0x003322;
				c.colorSpeed = 0.6;
			})
	);
}
