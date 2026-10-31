package com.angelichalo.config;

/**
 * All enum-typed options. Each constant carries a translation key suffix so the GUI can label it.
 */
public final class HaloOptions {
	private HaloOptions() { }

	/** How the ring itself is drawn. */
	public enum Style {
		/** Thin, crisp line — the look from the reference video. */
		LINE,
		/** Line with a soft bloom around it. */
		GLOW_LINE,
		/** Line plus a vertical curtain that fades upwards (classic "aura"). */
		AURA,
		/** Filled disc fading towards the centre. */
		DISC,
		/** Dashed line; pairs well with spin. */
		DASHED,
		/** Orbs evenly spaced around the circle. */
		ORBS
	}

	/** Movement of the ring over time. */
	public enum Motion {
		/** Stays at the chosen height. */
		STATIC,
		/** Sweeps from feet to head and back. */
		SCAN,
		/** Radius gently grows and shrinks. */
		BREATHE,
		/** Small floating ring above the head, bobbing. */
		HALO,
		/** Rings spawn at the feet and expand outwards like sonar. */
		RIPPLE,
		/** Ring surface undulates in a travelling wave. */
		WAVE
	}

	/** Easing applied to the scan sweep. */
	public enum Easing {
		SINE, LINEAR, SMOOTH, BOUNCE, ELASTIC
	}

	/** Direction of a scan sweep. */
	public enum ScanDirection {
		/** Up then down. */
		BOTH,
		/** Feet to head, then snap back. */
		UP,
		/** Head to feet, then snap back. */
		DOWN
	}

	/** Colouring. */
	public enum ColorMode {
		SOLID,
		/** Primary to secondary around the ring. */
		GRADIENT,
		/** Full hue wheel around the ring, scrolling. */
		RAINBOW,
		/** Whole ring cycles through hues together. */
		CHROMA,
		/** Fades between primary and secondary over time. */
		PULSE,
		/** Primary at the feet, secondary at the head (follows the scan). */
		HEIGHT,
		/** A bright comet that chases around the ring. */
		COMET,
		/** Green when healthy, red when hurt (your own health only). */
		HEALTH,
		/** Random twinkling between primary and secondary. */
		SPARKLE
	}
}
