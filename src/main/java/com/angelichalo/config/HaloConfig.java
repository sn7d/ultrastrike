package com.angelichalo.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;

import com.angelichalo.config.HaloOptions.ColorMode;
import com.angelichalo.config.HaloOptions.Easing;
import com.angelichalo.config.HaloOptions.Motion;
import com.angelichalo.config.HaloOptions.ScanDirection;
import com.angelichalo.config.HaloOptions.Style;

/**
 * Plain data holder, serialised to {@code config/angelichalo.json}. Defaults reproduce the reference video:
 * a thin soft-red line at foot level with a ~3 block radius.
 */
public final class HaloConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("AngelicHalo");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("angelichalo.json");

	private static HaloConfig instance = new HaloConfig();

	// ---- General ----
	public boolean enabled = true;
	public boolean showInFirstPerson = true;
	public boolean showInThirdPerson = true;
	public boolean hideWhenSneaking = false;
	public boolean hideWhenInvisible = true;
	public boolean throughWalls = true;
	/** Switch to third person while the settings screen is open so you can watch your edits live. */
	public boolean previewInThirdPerson = true;

	// ---- Shape ----
	public Style style = Style.LINE;
	public double radius = 3.0;
	/** Line thickness in screen pixels (constant regardless of distance, like the video). */
	public double lineWidth = 2.0;
	/** Height above the feet, in blocks. */
	public double heightOffset = 0.05;
	public int segments = 120;
	/** Degrees per second; negative spins the other way. */
	public double spinSpeed = 0.0;
	/** Tilt of the ring plane in degrees. */
	public double tilt = 0.0;
	/** Extra concentric copies stacked vertically. */
	public int ringCount = 1;
	public double ringSpacing = 0.25;
	public double auraHeight = 0.7;
	public int dashCount = 24;
	public double dashFill = 0.55;
	public int orbCount = 12;
	public double orbSize = 0.12;
	public double glowWidth = 10.0;
	public double glowStrength = 0.45;

	// ---- Motion ----
	public Motion motion = Motion.STATIC;
	/** Full sweeps per second. */
	public double scanSpeed = 0.5;
	public Easing scanEasing = Easing.SINE;
	public ScanDirection scanDirection = ScanDirection.BOTH;
	/** Lowest scan point as a fraction of player height (0 = feet). */
	public double scanMin = 0.0;
	/** Highest scan point as a fraction of player height (1 = top of head). */
	public double scanMax = 1.0;
	/** Seconds to rest at each end of the sweep. */
	public double scanPause = 0.0;
	/** Number of fading after-images that trail the scanning ring. */
	public int scanTrail = 0;
	public double scanTrailSpacing = 0.06;
	/** Scan ring shrinks towards the head to hug the body shape. */
	public boolean scanContour = false;
	public double breatheAmount = 0.15;
	public double breatheSpeed = 0.5;
	public double haloRadius = 0.38;
	public double haloHeight = 0.25;
	public double haloBob = 0.05;
	public double rippleSpeed = 0.6;
	public int rippleCount = 3;
	public double waveAmplitude = 0.12;
	public int waveFrequency = 4;
	public double waveSpeed = 1.0;

	// ---- Colour ----
	public ColorMode colorMode = ColorMode.SOLID;
	public int primaryColor = 0xFF4B55;
	public int secondaryColor = 0xFFD54F;
	public double opacity = 0.9;
	/** Speed multiplier shared by animated colour modes. */
	public double colorSpeed = 0.3;
	public double saturation = 0.85;
	public double brightness = 1.0;
	/** How many times the gradient/rainbow repeats around the ring. */
	public int colorRepeats = 1;

	public static HaloConfig get() {
		return instance;
	}

	public static void set(HaloConfig config) {
		config.sanitize();
		instance = config;
	}

	public static void load() {
		if (!Files.exists(FILE)) {
			instance = new HaloConfig();
			save();
			return;
		}

		try (Reader reader = Files.newBufferedReader(FILE)) {
			HaloConfig loaded = GSON.fromJson(reader, HaloConfig.class);
			instance = loaded != null ? loaded : new HaloConfig();
		} catch (IOException | JsonParseException e) {
			LOGGER.warn("Angelic Halo config was unreadable, using defaults", e);
			instance = new HaloConfig();
		}

		instance.sanitize();
	}

	public static void save() {
		instance.sanitize();

		try {
			Files.createDirectories(FILE.getParent());
			Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");

			try (Writer writer = Files.newBufferedWriter(tmp)) {
				GSON.toJson(instance, writer);
			}

			Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			LOGGER.warn("Could not save Angelic Halo config", e);
		}
	}

	public HaloConfig copy() {
		return GSON.fromJson(GSON.toJson(this), HaloConfig.class);
	}

	/** Clamp everything into safe ranges so hand-edited or old files can never crash rendering. */
	public void sanitize() {
		if (style == null) style = Style.LINE;
		if (motion == null) motion = Motion.STATIC;
		if (scanEasing == null) scanEasing = Easing.SINE;
		if (scanDirection == null) scanDirection = ScanDirection.BOTH;
		if (colorMode == null) colorMode = ColorMode.SOLID;

		radius = clamp(radius, 0.2, 16.0);
		lineWidth = clamp(lineWidth, 0.5, 12.0);
		heightOffset = clamp(heightOffset, -1.0, 3.0);
		segments = clamp(segments, 16, 360);
		spinSpeed = clamp(spinSpeed, -720.0, 720.0);
		tilt = clamp(tilt, -60.0, 60.0);
		ringCount = clamp(ringCount, 1, 8);
		ringSpacing = clamp(ringSpacing, 0.02, 1.0);
		auraHeight = clamp(auraHeight, 0.05, 3.0);
		dashCount = clamp(dashCount, 2, 96);
		dashFill = clamp(dashFill, 0.1, 0.9);
		orbCount = clamp(orbCount, 1, 64);
		orbSize = clamp(orbSize, 0.02, 0.6);
		glowWidth = clamp(glowWidth, 2.0, 40.0);
		glowStrength = clamp(glowStrength, 0.0, 1.0);

		scanSpeed = clamp(scanSpeed, 0.02, 4.0);
		scanMin = clamp(scanMin, 0.0, 1.2);
		scanMax = clamp(scanMax, 0.0, 1.2);
		if (scanMax < scanMin) {
			double t = scanMax;
			scanMax = scanMin;
			scanMin = t;
		}
		scanPause = clamp(scanPause, 0.0, 3.0);
		scanTrail = clamp(scanTrail, 0, 12);
		scanTrailSpacing = clamp(scanTrailSpacing, 0.01, 0.3);
		breatheAmount = clamp(breatheAmount, 0.0, 0.8);
		breatheSpeed = clamp(breatheSpeed, 0.05, 4.0);
		haloRadius = clamp(haloRadius, 0.1, 2.0);
		haloHeight = clamp(haloHeight, 0.0, 2.0);
		haloBob = clamp(haloBob, 0.0, 0.5);
		rippleSpeed = clamp(rippleSpeed, 0.05, 4.0);
		rippleCount = clamp(rippleCount, 1, 8);
		waveAmplitude = clamp(waveAmplitude, 0.0, 1.0);
		waveFrequency = clamp(waveFrequency, 1, 16);
		waveSpeed = clamp(waveSpeed, -6.0, 6.0);

		primaryColor &= 0xFFFFFF;
		secondaryColor &= 0xFFFFFF;
		opacity = clamp(opacity, 0.05, 1.0);
		colorSpeed = clamp(colorSpeed, 0.0, 4.0);
		saturation = clamp(saturation, 0.0, 1.0);
		brightness = clamp(brightness, 0.1, 1.0);
		colorRepeats = clamp(colorRepeats, 1, 12);
	}

	private static double clamp(double v, double min, double max) {
		if (Double.isNaN(v)) return min;
		return Math.max(min, Math.min(max, v));
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}
}
