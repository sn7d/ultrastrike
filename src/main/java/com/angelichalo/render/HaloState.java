package com.angelichalo.render;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side bookkeeping for reactive effects. Reads only your own local player (position, on-ground flag,
 * health bar) — nothing is sent anywhere.
 */
public final class HaloState {
	private HaloState() { }

	/** World-anchored expanding ring spawned by a jump or landing. */
	public record JumpRing(double x, double y, double z, double start) { }

	public static final double JUMP_RING_LIFE = 0.9;
	public static final double DAMAGE_FLASH_LIFE = 0.7;

	private static final long START = System.nanoTime();
	private static final List<JumpRing> JUMP_RINGS = new ArrayList<>();

	private static float lastHealth = -1;
	private static boolean wasOnGround = true;
	private static int airTicks;
	private static double damageTime = -100;
	private static double smoothSpeed;
	private static double lastX = Double.NaN;
	private static double lastZ;

	/** Seconds since the mod loaded; keeps animating even while the game is paused. */
	public static double now() {
		return (System.nanoTime() - START) / 1_000_000_000.0;
	}

	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;

		if (player == null) {
			lastHealth = -1;
			lastX = Double.NaN;
			JUMP_RINGS.clear();
			return;
		}

		double t = now();

		// damage flash: own health bar went down
		float health = player.getHealth();
		if (lastHealth >= 0 && health < lastHealth - 0.01f && player.isAlive()) {
			damageTime = t;
		}
		lastHealth = health;

		// jump / landing rings
		boolean onGround = player.onGround();
		if (wasOnGround && !onGround && player.getDeltaMovement().y > 0.2) {
			JUMP_RINGS.add(new JumpRing(player.getX(), player.getY(), player.getZ(), t));
		} else if (!wasOnGround && onGround && airTicks > 8) {
			JUMP_RINGS.add(new JumpRing(player.getX(), player.getY(), player.getZ(), t));
		}
		airTicks = onGround ? 0 : airTicks + 1;
		wasOnGround = onGround;

		Iterator<JumpRing> it = JUMP_RINGS.iterator();
		while (it.hasNext()) {
			if (t - it.next().start() > JUMP_RING_LIFE) it.remove();
		}
		while (JUMP_RINGS.size() > 16) JUMP_RINGS.remove(0);

		// horizontal speed in blocks/tick, smoothed
		double x = player.getX();
		double z = player.getZ();
		if (!Double.isNaN(lastX)) {
			double dx = x - lastX;
			double dz = z - lastZ;
			double sp = Math.sqrt(dx * dx + dz * dz);
			if (sp > 3.0) sp = 0; // teleport
			smoothSpeed += (sp - smoothSpeed) * 0.25;
		}
		lastX = x;
		lastZ = z;
	}

	public static List<JumpRing> jumpRings() {
		return JUMP_RINGS;
	}

	/** 1 right after taking damage, fading to 0. */
	public static double damageFlash() {
		double age = now() - damageTime;
		return age < 0 || age > DAMAGE_FLASH_LIFE ? 0 : 1.0 - age / DAMAGE_FLASH_LIFE;
	}

	public static double damageAge() {
		return now() - damageTime;
	}

	/** 0 standing still, ~1 sprinting, more when flying fast. */
	public static double speedFactor() {
		return Math.min(smoothSpeed / 0.28, 2.0);
	}

	/** For the settings screen "test" buttons. */
	public static void simulateDamage() {
		damageTime = now();
	}

	public static void simulateJump(Vec3 pos) {
		JUMP_RINGS.add(new JumpRing(pos.x, pos.y, pos.z, now()));
	}
}
