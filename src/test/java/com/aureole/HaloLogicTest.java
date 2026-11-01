package com.aureole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aureole.config.ColorSpec;
import com.aureole.config.HaloConfig;
import com.aureole.config.HaloOptions;
import com.aureole.config.HaloPresets;
import com.aureole.render.HaloColors;
import com.aureole.render.HaloGeometry;
import com.aureole.render.HaloMotion;
import com.aureole.render.HaloState;

class HaloLogicTest {
	/** Collects quads and validates every vertex is finite. */
	private static final class CountingSink implements HaloGeometry.QuadSink {
		int quads;
		boolean finite = true;

		@Override
		public void quad(float x0, float y0, float z0, int c0, float x1, float y1, float z1, int c1,
				float x2, float y2, float z2, int c2, float x3, float y3, float z3, int c3) {
			quads++;
			for (float v : new float[] {x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3}) {
				if (!Float.isFinite(v)) finite = false;
			}
		}
	}

	private static HaloGeometry.Frame frame(double t) {
		List<HaloState.JumpRing> jumps = new ArrayList<>();
		jumps.add(new HaloState.JumpRing(0, 0, 0, t - 0.2));
		return new HaloGeometry.Frame(0.5, -1.62, 0.3, 0, 0, 0, 1.8, t, 0.7, 2.0 / (1.43 * 1080), 1.0, 0.5, jumps);
	}

	@Test
	void defaultsMatchTheReferenceVideo() {
		HaloConfig c = new HaloConfig();
		assertEquals(HaloOptions.Style.LINE, c.style);
		assertEquals(3.0, c.radius, 1e-9);
		assertEquals(HaloOptions.Motion.STATIC, c.motion);
		assertEquals(HaloOptions.ColorMode.SOLID, c.ringColor.mode);
		assertEquals(0xFF4B55, c.ringColor.primary);
		assertTrue(c.lineWidth <= 3.0, "thin line like the video");
		assertFalse(c.damageFlash, "ring must not react to being hurt unless the user opts in");
	}

	@Test
	void everyStyleMotionAndColourModeBuildsFiniteGeometry() {
		for (HaloOptions.Style style : HaloOptions.Style.values()) {
			for (HaloOptions.Motion motion : HaloOptions.Motion.values()) {
				for (HaloOptions.ColorMode mode : HaloOptions.ColorMode.values()) {
					HaloConfig c = new HaloConfig();
					c.style = style;
					c.motion = motion;
					c.ringColor.mode = mode;
					c.trailColor.mode = mode;
					c.trailStyle = HaloOptions.TrailStyle.BOTH;
					c.embers = true;
					c.helix = true;
					c.jumpRings = true;
					c.damageFlash = true;
					c.speedReactive = true;
					c.bloom = true;
					c.sanitize();

					for (double t : new double[] {0, 0.37, 1.9, 12345.678}) {
						CountingSink core = new CountingSink();
						CountingSink glow = new CountingSink();
						HaloGeometry.build(c, frame(t), core, glow);
						String what = style + "/" + motion + "/" + mode + " t=" + t;
						assertTrue(core.finite && glow.finite, "non-finite vertex: " + what);
						assertTrue(core.quads + glow.quads > 0, "nothing drawn: " + what);
					}
				}
			}
		}
	}

	@Test
	void presetsAreValidAndDistinct() {
		HaloConfig base = new HaloConfig();
		java.util.Set<String> seen = new java.util.HashSet<>();

		for (HaloPresets.Preset p : HaloPresets.ALL) {
			HaloConfig c = p.build(base);
			assertTrue(seen.add(p.id()), "duplicate preset id " + p.id());
			CountingSink core = new CountingSink();
			CountingSink glow = new CountingSink();
			HaloGeometry.build(c, frame(3.3), core, glow);
			assertTrue(core.quads > 0, p.id());
		}
	}

	@Test
	void scanSweepsBetweenFeetAndHead() {
		HaloConfig c = new HaloConfig();
		c.motion = HaloOptions.Motion.SCAN;
		c.scanSpeed = 0.5;
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;

		for (double t = 0; t < 4; t += 0.01) {
			HaloMotion.Scan s = HaloMotion.scan(c, t, 1.8);
			min = Math.min(min, s.y());
			max = Math.max(max, s.y());
		}

		assertEquals(c.heightOffset, min, 0.02);
		assertEquals(c.heightOffset + 1.8, max, 0.02);
	}

	@Test
	void sanitizeClampsGarbage() {
		HaloConfig c = new HaloConfig();
		c.radius = Double.NaN;
		c.segments = -5;
		c.ringColor = null;
		c.style = null;
		c.scanMin = 0.9;
		c.scanMax = 0.1;
		c.sanitize();
		assertTrue(c.radius >= 0.2);
		assertTrue(c.segments >= 16);
		assertTrue(c.ringColor != null && c.style != null);
		assertTrue(c.scanMin <= c.scanMax);
	}

	@Test
	void copyRoundTripsThroughJson() {
		HaloConfig c = HaloPresets.ALL.get(3).build(new HaloConfig());
		assertEquals(c.toJson(), c.copy().toJson());
	}

	@Test
	void nametagFormattingCodes() {
		java.util.List<com.aureole.render.NametagText.Glyph> g = com.aureole.render.NametagText.parse("&6&lD7&r ★ &#FF77AAx&&");
		assertEquals("D7 ★ x&", com.aureole.render.NametagText.plain("&6&lD7&r ★ &#FF77AAx&&"));
		assertEquals(0xFFAA00, g.get(0).rgb());
		assertTrue(g.get(0).bold());
		assertEquals(-1, g.get(2).rgb(), "reset returns to the colour effect");
		assertFalse(g.get(2).bold());
		assertEquals(0xFF77AA, g.get(5).rgb());
		assertEquals("&", g.get(6).text());
		assertEquals(7, g.size());
		// a lone & or unknown code stays visible
		assertEquals("a & b &z", com.aureole.render.NametagText.plain("a & b &z"));
	}

	@Test
	void shareCodesRoundTripIncludingOldPrefix() {
		HaloConfig c = HaloPresets.ALL.get(5).build(new HaloConfig());
		String code = com.aureole.config.PresetLibrary.toCode(c);
		assertTrue(code.startsWith("AU1:"));
		assertEquals(c.toJson(), com.aureole.config.PresetLibrary.fromCode(code).toJson());
		// codes shared before the rename (Angelic Halo) still work
		assertEquals(c.toJson(), com.aureole.config.PresetLibrary.fromCode("AH1:" + code.substring(4)).toJson());
		assertEquals(null, com.aureole.config.PresetLibrary.fromCode("hello"));
	}

	@Test
	void colourAlphaAndOpacity() {
		ColorSpec s = new ColorSpec(HaloOptions.ColorMode.SOLID, 0x123456, 0, 0.5);
		int argb = HaloColors.color(s, 0, 0, 0, 1, 1);
		assertEquals(0x123456, argb & 0xFFFFFF);
		assertEquals(128, argb >>> 24, 1);
		assertEquals(0xFF0000, HaloColors.hsv(0, 1, 1));
	}
}
