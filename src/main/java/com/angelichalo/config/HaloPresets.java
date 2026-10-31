package com.angelichalo.config;

import java.util.List;
import java.util.function.Consumer;

import com.angelichalo.config.HaloOptions.ColorMode;
import com.angelichalo.config.HaloOptions.Easing;
import com.angelichalo.config.HaloOptions.Motion;
import com.angelichalo.config.HaloOptions.Style;
import com.angelichalo.config.HaloOptions.TrailStyle;

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

	private static void ring(HaloConfig c, ColorMode mode, int a, int b) {
		c.ringColor.mode = mode;
		c.ringColor.primary = a;
		c.ringColor.secondary = b;
	}

	private static void trail(HaloConfig c, ColorMode mode, int a, int b) {
		c.trailColor.mode = mode;
		c.trailColor.primary = a;
		c.trailColor.secondary = b;
	}

	public static final List<Preset> ALL = List.of(
			// exactly the reference video: thin soft-red line, ~3 blocks, at the feet
			new Preset("classic", 0xFF4B55, c -> { }),
			new Preset("classic_scan", 0xFF4B55, c -> {
				c.motion = Motion.SCAN;
				c.radius = 0.8;
				c.scanSpeed = 0.4;
				c.trailStyle = TrailStyle.SMEAR;
				trail(c, ColorMode.GRADIENT, 0xFF4B55, 0x2A0008);
			}),
			new Preset("angelic", 0xFFE7A0, c -> {
				c.style = Style.GLOW_LINE;
				c.motion = Motion.HALO;
				ring(c, ColorMode.PULSE, 0xFFF4D6, 0xFFC94A);
				c.lineWidth = 3.0;
				c.glowWidth = 14.0;
				c.glowStrength = 0.6;
				c.ringColor.speed = 0.4;
				c.embers = true;
				c.emberCount = 10;
				c.emberRise = 0.6;
				c.extrasColor.mode = ColorMode.SOLID;
				c.extrasColor.primary = 0xFFF4D6;
			}),
			new Preset("scanner", 0x38E8FF, c -> {
				c.style = Style.GLOW_LINE;
				c.motion = Motion.SCAN;
				c.radius = 0.75;
				c.scanSpeed = 0.45;
				c.scanContour = true;
				c.trailStyle = TrailStyle.BOTH;
				c.trailLength = 0.5;
				ring(c, ColorMode.HEIGHT, 0x38E8FF, 0x7C4DFF);
				trail(c, ColorMode.RAINBOW, 0x38E8FF, 0x7C4DFF);
				c.trailColor.speed = 0.6;
			}),
			new Preset("inferno", 0xFF6A00, c -> {
				c.style = Style.AURA;
				c.radius = 1.0;
				c.auraHeight = 0.9;
				ring(c, ColorMode.FIRE, 0xFF2A00, 0xFFC400);
				c.ringColor.speed = 1.0;
				c.embers = true;
				c.emberCount = 40;
				c.emberRise = 1.6;
				c.extrasColor.mode = ColorMode.FIRE;
				c.extrasColor.primary = 0xFF2A00;
				c.extrasColor.secondary = 0xFFE066;
			}),
			new Preset("frost", 0xA8E6FF, c -> {
				c.style = Style.ORBS;
				c.radius = 1.1;
				c.orbCount = 10;
				c.motion = Motion.WAVE;
				c.waveFrequency = 2;
				c.spinSpeed = 40;
				ring(c, ColorMode.SPARKLE, 0xA8E6FF, 0xFFFFFF);
			}),
			new Preset("void", 0x8A2BE2, c -> {
				c.style = Style.DISC;
				c.radius = 1.4;
				c.motion = Motion.BREATHE;
				ring(c, ColorMode.PULSE, 0x5B00B5, 0x12001F);
				c.ringColor.opacity = 0.75;
				c.helix = true;
				c.extrasColor.mode = ColorMode.SOLID;
				c.extrasColor.primary = 0xC77DFF;
			}),
			new Preset("toxic", 0x7CFF2B, c -> {
				c.style = Style.DASHED;
				c.radius = 1.2;
				c.spinSpeed = 120;
				c.ringCount = 3;
				c.ringSpacing = 0.18;
				ring(c, ColorMode.COMET, 0x7CFF2B, 0x0E3D00);
			}),
			new Preset("sakura", 0xFF9EC7, c -> {
				c.style = Style.GLOW_LINE;
				c.motion = Motion.RIPPLE;
				c.radius = 2.5;
				c.rippleCount = 3;
				ring(c, ColorMode.GRADIENT, 0xFF9EC7, 0xFFFFFF);
				c.jumpRings = true;
				c.extrasColor.mode = ColorMode.SOLID;
				c.extrasColor.primary = 0xFF9EC7;
			}),
			new Preset("aurora", 0x3DFFB5, c -> {
				c.style = Style.AURA;
				c.radius = 1.3;
				c.auraHeight = 1.4;
				ring(c, ColorMode.AURORA, 0x3DFFB5, 0x7C4DFF);
				c.ringColor.speed = 0.25;
				c.ringColor.repeats = 2;
			}),
			new Preset("rainbow", 0xFF00FF, c -> {
				c.style = Style.GLOW_LINE;
				c.motion = Motion.SCAN;
				c.radius = 0.8;
				c.scanEasing = Easing.SMOOTH;
				c.trailStyle = TrailStyle.BOTH;
				ring(c, ColorMode.RAINBOW, 0xFF0000, 0x0000FF);
				c.ringColor.speed = 0.5;
				c.trailMatchRing = true;
			}),
			new Preset("radar", 0x00FF88, c -> {
				c.radius = 3.0;
				c.motion = Motion.RIPPLE;
				c.rippleCount = 2;
				ring(c, ColorMode.COMET, 0x00FF88, 0x003322);
				c.ringColor.speed = 0.6;
			})
	);
}
