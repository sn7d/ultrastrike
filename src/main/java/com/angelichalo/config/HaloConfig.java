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
import com.angelichalo.config.HaloOptions.EmblemMode;
import com.angelichalo.config.HaloOptions.Easing;
import com.angelichalo.config.HaloOptions.TextMode;
import com.angelichalo.config.HaloOptions.Motion;
import com.angelichalo.config.HaloOptions.ScanDirection;
import com.angelichalo.config.HaloOptions.Style;
import com.angelichalo.config.HaloOptions.TrailStyle;

/**
 * Plain data holder, serialised to {@code config/angelichalo.json}. Defaults reproduce the reference video:
 * a thin soft-red line at foot level with a ~3 block radius.
 */
public final class HaloConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("AngelicHalo");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static HaloConfig instance = new HaloConfig();

	// ---- General ----
	public boolean enabled = true;
	public boolean showInFirstPerson = true;
	public boolean showInThirdPerson = true;
	public boolean hideWhenSneaking = false;
	public boolean hideWhenInvisible = true;
	public boolean throughWalls = true;
	/**
	 * With "through blocks" on: opacity of the parts hidden behind blocks or your own body.
	 * 1.0 draws everything on top (the reference video); lower values make effects read as 3D.
	 */
	public double hiddenOpacity = 1.0;

	// ---- Shape ----
	public Style style = Style.LINE;
	public double radius = 3.0;
	/** Line thickness in pixels (measured at 1080p, constant regardless of distance — like the video). */
	public double lineWidth = 2.0;
	/** Height above the feet, in blocks. */
	public double heightOffset = 0.05;
	public int segments = 120;
	/** Degrees per second; negative spins the other way. */
	public double spinSpeed = 0.0;
	/** Tilt of the ring plane in degrees. */
	public double tilt = 0.0;
	/** Extra copies stacked vertically. */
	public int ringCount = 1;
	public double ringSpacing = 0.25;
	public double auraHeight = 0.7;
	public int dashCount = 24;
	public double dashFill = 0.55;
	public int orbCount = 12;
	public double orbSize = 0.12;
	public double glowWidth = 10.0;
	public double glowStrength = 0.45;
	/** Sonar sweeps per second. */
	public double sonarSpeed = 0.5;
	public int crownSpikes = 8;
	public double crownHeight = 0.35;
	public int galaxyArms = 3;
	public double lightningIntensity = 0.5;

	// ---- Glow / bloom (applies to every layer) ----
	/** Soft additive bloom around everything. Off for the classic look so it matches the reference exactly. */
	public boolean bloom = false;
	public double bloomStrength = 0.7;
	/** Bloom radius in pixels (at 1080p). */
	public double bloomRadius = 16.0;
	public double ringBloom = 1.0;
	public double trailBloom = 1.0;
	public double textBloom = 1.0;
	public double emblemBloom = 1.0;
	public double extrasBloom = 1.0;
	/** Keep the halo's own bloom even while an Iris shaderpack is active (packs usually have their own). */
	public boolean bloomWithShaders = false;
	/** Extra saturation while a shaderpack is active, to counter packs that grade thin coloured lines to white. */
	public double shaderColorBoost = 1.4;

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
	/** Scan ring narrows around the head to hug the body shape. */
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
	/** Maximum wobble angle for GYRO, degrees. */
	public double gyroAngle = 18.0;
	public double gyroSpeed = 0.6;
	/** Beats per minute for HEARTBEAT (amplitude uses breatheAmount). */
	public double heartRate = 72.0;

	// ---- Scan trail (fading effect that follows the scanning ring) ----
	public boolean trailEnabled = true;
	public TrailStyle trailStyle = TrailStyle.SMEAR;
	/** How far back in time the trail reaches, seconds. */
	public double trailLength = 0.35;
	/** Number of echo rings (ECHOES / BOTH). */
	public int trailEchoes = 4;
	/** Use the ring colours for the trail instead of its own colour settings. */
	public boolean trailMatchRing = false;
	public ColorSpec trailColor = new ColorSpec(ColorMode.GRADIENT, 0xFF4B55, 0x7C4DFF, 0.55);

	// ---- Ring colour ----
	public ColorSpec ringColor = new ColorSpec(ColorMode.SOLID, 0xFF4B55, 0xFFD54F, 0.9);

	// ---- Extras ----
	/** Glowing embers that drift upwards off the ring. */
	public boolean embers = false;
	public int emberCount = 24;
	public double emberRise = 1.2;
	public double emberSize = 0.05;
	/** Two orbs spiralling up and down your body in a double helix. */
	public boolean helix = false;
	public double helixSpeed = 0.6;
	public double helixRadius = 0.55;
	public int helixStrands = 2;
	/** An expanding ring at your feet every time you jump/land. */
	public boolean jumpRings = false;
	public double jumpRingSize = 2.0;
	/**
	 * Opt-in: ring flashes and shockwaves outward when you take damage (reads only your own health bar).
	 * Off by default — the ring is otherwise completely unaffected by being hurt or dying.
	 */
	public boolean damageFlash = false;
	public int damageColor = 0xFF1A1A;
	/** Ring expands and brightens with your movement speed. */
	public boolean speedReactive = false;
	public double speedReactiveAmount = 0.35;
	public ColorSpec extrasColor = new ColorSpec(ColorMode.PULSE, 0xFFE7A0, 0xFF4B55, 0.85);

	// ---- Custom text ----
	public boolean textEnabled = false;
	public String text = "ANGELIC HALO";
	public TextMode textMode = TextMode.ORBIT;
	public double textSize = 1.0;
	/** Extra distance from the ring (orbit/engraved) or above the head (banner), blocks. */
	public double textOffset = 0.25;
	public double textHeight = 0.4;
	/** Degrees per second the text travels around the ring. */
	public double textSpeed = 20.0;
	/** Copies of the text around the ring. */
	public int textRepeat = 2;
	public double textSpacing = 1.0;
	public boolean textShadow = true;
	/** Use the bundled smooth typeface instead of the blocky Minecraft font. */
	public boolean textSmoothFont = true;
	public ColorSpec textColor = new ColorSpec(ColorMode.GRADIENT, 0xFFFFFF, 0xFFD27A, 1.0);

	// ---- Emblem image ----
	public boolean emblemEnabled = false;
	/** "builtin:<name>" or "file:<name>.png" from config/angelichalo/images. */
	public String emblem = "builtin:sigil";
	public EmblemMode emblemMode = EmblemMode.FLOOR;
	public double emblemSize = 2.2;
	public double emblemSpin = 15.0;
	public double emblemHeight = 0.02;
	/** Gentle opacity pulse, 0 = steady. */
	public double emblemPulse = 0.25;
	public ColorSpec emblemColor = new ColorSpec(ColorMode.SOLID, 0xFFD27A, 0xFF4B55, 0.8);

	/** Effective bloom strength for a layer multiplier (0 when bloom is off). */
	public double bloomFor(double layer) {
		return bloom ? bloomStrength * layer : 0.0;
	}

	// ---- Nametag (your own, client-side only) ----
	public boolean tagEnabled = false;
	/** Your displayed name; blank = your username. Supports &-codes (&a, &l, &o, &r ...). */
	public String tagText = "";
	/** Optional title line above the name. */
	public String tagTitle = "";
	public boolean tagTitleEnabled = true;
	public double tagScale = 1.0;
	public double tagHeight = 0.0;
	public boolean tagBackground = true;
	public double tagBackgroundOpacity = 0.3;
	public boolean tagShadow = true;
	public boolean tagSmoothFont = true;
	public boolean tagBold = false;
	public boolean tagThroughWalls = true;
	/** "" for none, "builtin:x" or "file:y.png" from config/angelichalo/icons. */
	public String tagIconLeft = "builtin:crown";
	public String tagIconRight = "";
	public double tagIconSize = 1.0;
	public double tagGlow = 0.6;
	public ColorSpec tagColor = new ColorSpec(ColorMode.GRADIENT, 0xFFE7A0, 0xFFFFFF, 1.0);
	public ColorSpec tagTitleColor = new ColorSpec(ColorMode.SOLID, 0xC9A7FF, 0xFFFFFF, 1.0);
	public ColorSpec tagIconColor = new ColorSpec(ColorMode.SOLID, 0xFFD27A, 0xFFFFFF, 1.0);

	public static HaloConfig get() {
		return instance;
	}

	public static void set(HaloConfig config) {
		config.sanitize();
		instance = config;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("angelichalo.json");
	}

	public static void load() {
		Path file = file();

		if (!Files.exists(file)) {
			instance = new HaloConfig();
			save();
			return;
		}

		try (Reader reader = Files.newBufferedReader(file)) {
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
		Path file = file();

		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");

			try (Writer writer = Files.newBufferedWriter(tmp)) {
				GSON.toJson(instance, writer);
			}

			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException atomicFailed) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			LOGGER.warn("Could not save Angelic Halo config", e);
		}
	}

	public HaloConfig copy() {
		HaloConfig c = GSON.fromJson(GSON.toJson(this), HaloConfig.class);
		c.sanitize();
		return c;
	}

	public String toJson() {
		return GSON.toJson(this);
	}

	/** Clamp everything into safe ranges so hand-edited or old files can never crash rendering. */
	public void sanitize() {
		if (style == null) style = Style.LINE;
		if (motion == null) motion = Motion.STATIC;
		if (scanEasing == null) scanEasing = Easing.SINE;
		if (scanDirection == null) scanDirection = ScanDirection.BOTH;
		if (trailStyle == null) trailStyle = TrailStyle.SMEAR;
		if (ringColor == null) ringColor = new ColorSpec();
		if (trailColor == null) trailColor = new ColorSpec(ColorMode.GRADIENT, 0xFF4B55, 0x7C4DFF, 0.55);
		if (extrasColor == null) extrasColor = new ColorSpec(ColorMode.PULSE, 0xFFE7A0, 0xFF4B55, 0.85);
		if (textMode == null) textMode = TextMode.ORBIT;
		if (emblemMode == null) emblemMode = EmblemMode.FLOOR;
		if (text == null) text = "";
		if (text.length() > 48) text = text.substring(0, 48);
		if (emblem == null || emblem.isBlank()) emblem = "builtin:sigil";
		if (textColor == null) textColor = new ColorSpec(ColorMode.GRADIENT, 0xFFFFFF, 0xFFD27A, 1.0);
		if (emblemColor == null) emblemColor = new ColorSpec(ColorMode.SOLID, 0xFFD27A, 0xFF4B55, 0.8);
		textColor.sanitize();
		emblemColor.sanitize();
		textSize = clamp(textSize, 0.2, 4.0);
		textOffset = clamp(textOffset, -2.0, 4.0);
		textHeight = clamp(textHeight, -1.0, 4.0);
		textSpeed = clamp(textSpeed, -360.0, 360.0);
		textRepeat = clamp(textRepeat, 1, 8);
		textSpacing = clamp(textSpacing, 0.5, 3.0);
		emblemSize = clamp(emblemSize, 0.2, 12.0);
		emblemSpin = clamp(emblemSpin, -360.0, 360.0);
		emblemHeight = clamp(emblemHeight, -1.0, 4.0);
		emblemPulse = clamp(emblemPulse, 0.0, 1.0);
		if (tagText == null) tagText = "";
		if (tagTitle == null) tagTitle = "";
		if (tagText.length() > 64) tagText = tagText.substring(0, 64);
		if (tagTitle.length() > 64) tagTitle = tagTitle.substring(0, 64);
		if (tagIconLeft == null) tagIconLeft = "";
		if (tagIconRight == null) tagIconRight = "";
		if (tagColor == null) tagColor = new ColorSpec(ColorMode.GRADIENT, 0xFFE7A0, 0xFFFFFF, 1.0);
		if (tagTitleColor == null) tagTitleColor = new ColorSpec(ColorMode.SOLID, 0xC9A7FF, 0xFFFFFF, 1.0);
		if (tagIconColor == null) tagIconColor = new ColorSpec(ColorMode.SOLID, 0xFFD27A, 0xFFFFFF, 1.0);
		tagColor.sanitize();
		tagTitleColor.sanitize();
		tagIconColor.sanitize();
		tagScale = clamp(tagScale, 0.3, 4.0);
		tagHeight = clamp(tagHeight, -1.0, 3.0);
		tagBackgroundOpacity = clamp(tagBackgroundOpacity, 0.0, 1.0);
		tagIconSize = clamp(tagIconSize, 0.5, 3.0);
		tagGlow = clamp(tagGlow, 0.0, 2.0);
		ringColor.sanitize();
		trailColor.sanitize();
		extrasColor.sanitize();

		hiddenOpacity = clamp(hiddenOpacity, 0.0, 1.0);
		bloomStrength = clamp(bloomStrength, 0.0, 1.5);
		bloomRadius = clamp(bloomRadius, 4.0, 48.0);
		ringBloom = clamp(ringBloom, 0.0, 2.0);
		trailBloom = clamp(trailBloom, 0.0, 2.0);
		textBloom = clamp(textBloom, 0.0, 2.0);
		emblemBloom = clamp(emblemBloom, 0.0, 2.0);
		extrasBloom = clamp(extrasBloom, 0.0, 2.0);
		shaderColorBoost = clamp(shaderColorBoost, 1.0, 2.5);
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
		sonarSpeed = clamp(sonarSpeed, 0.05, 3.0);
		crownSpikes = clamp(crownSpikes, 3, 32);
		crownHeight = clamp(crownHeight, 0.05, 1.5);
		galaxyArms = clamp(galaxyArms, 1, 8);
		lightningIntensity = clamp(lightningIntensity, 0.0, 1.0);

		scanSpeed = clamp(scanSpeed, 0.02, 4.0);
		scanMin = clamp(scanMin, 0.0, 1.2);
		scanMax = clamp(scanMax, 0.0, 1.2);
		if (scanMax < scanMin) {
			double t = scanMax;
			scanMax = scanMin;
			scanMin = t;
		}
		scanPause = clamp(scanPause, 0.0, 3.0);
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
		gyroAngle = clamp(gyroAngle, 0.0, 60.0);
		gyroSpeed = clamp(gyroSpeed, 0.05, 4.0);
		heartRate = clamp(heartRate, 30.0, 200.0);

		trailLength = clamp(trailLength, 0.05, 2.0);
		trailEchoes = clamp(trailEchoes, 1, 12);

		emberCount = clamp(emberCount, 1, 128);
		emberRise = clamp(emberRise, 0.1, 4.0);
		emberSize = clamp(emberSize, 0.01, 0.3);
		helixSpeed = clamp(helixSpeed, 0.05, 4.0);
		helixRadius = clamp(helixRadius, 0.1, 3.0);
		helixStrands = clamp(helixStrands, 1, 6);
		jumpRingSize = clamp(jumpRingSize, 0.5, 8.0);
		damageColor &= 0xFFFFFF;
		speedReactiveAmount = clamp(speedReactiveAmount, 0.0, 1.5);
	}

	static double clamp(double v, double min, double max) {
		if (Double.isNaN(v)) return min;
		return Math.max(min, Math.min(max, v));
	}

	static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}
}
