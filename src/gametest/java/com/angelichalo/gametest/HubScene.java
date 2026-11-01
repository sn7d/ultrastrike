package com.angelichalo.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;

/**
 * An original SkyBlock-hub-style town square for the README photos (inspired by the vibe of a hub village, not a
 * copy of any server's map): stone plaza, fountain, timber houses, market stalls, lamp posts and trees.
 * The player stands at (x, y, z) facing south; the fountain and main street are to the north (behind them in
 * front-camera shots).
 */
final class HubScene {
	private final TestServerContext s;
	private final int x;
	private final int y; // floor level (block under the player's feet)
	private final int z;

	private HubScene(TestServerContext s, int px, int py, int pz) {
		this.s = s;
		this.x = px;
		this.y = py - 1;
		this.z = pz;
	}

	static void build(TestServerContext s, int px, int py, int pz) {
		new HubScene(s, px, py, pz).build();
	}

	private void build() {
		// clear + ground
		for (int layer = 1; layer <= 20; layer++) {
			fill(-34, layer, -40, 34, layer, 30, "air"); // per layer: /fill is capped at 32768 blocks
		}
		fill(-34, -1, -40, 34, -1, 30, "dirt");
		fill(-34, 0, -40, 34, 0, 30, "grass_block");

		// plaza: polished andesite with a stone-brick border and a cobbled ring road
		fill(-16, 0, -28, 16, 0, 10, "stone_bricks");
		fill(-15, 0, -27, 15, 0, 9, "polished_andesite");
		for (int i = -15; i <= 15; i += 3) {
			fill(i, 0, -27, i, 0, 9, "andesite");
		}
		fill(-4, 0, -19, 4, 0, -9, "cobblestone");
		fill(-3, 0, 9, 3, 0, 30, "dirt_path");
		fill(-3, 0, -40, 3, 0, -28, "dirt_path");

		fountain(0, -14);

		// street of timber houses behind the fountain (north)
		house(-15, -38, 9, 7, 5, "oak_planks", "spruce_log", "dark_oak_stairs");
		house(-4, -38, 8, 7, 6, "white_terracotta", "dark_oak_log", "spruce_stairs");
		house(6, -38, 9, 7, 5, "spruce_planks", "stripped_dark_oak_log", "dark_oak_stairs");
		// side houses
		house(-30, -24, 8, 9, 5, "white_terracotta", "spruce_log", "dark_oak_stairs");
		house(22, -24, 8, 9, 5, "oak_planks", "dark_oak_log", "spruce_stairs");
		house(-30, -6, 8, 8, 4, "spruce_planks", "stripped_spruce_log", "dark_oak_stairs");
		house(22, -6, 8, 8, 4, "white_terracotta", "spruce_log", "dark_oak_stairs");

		// market stalls along the plaza edges
		stall(-13, -22, "red_wool");
		stall(-13, -15, "yellow_wool");
		stall(-13, -8, "blue_wool");
		stall(10, -22, "green_wool");
		stall(10, -15, "orange_wool");
		stall(10, -8, "purple_wool");

		// lamp posts around the plaza
		int[][] lamps = {{-16, -28}, {16, -28}, {-16, 10}, {16, 10}, {-8, -28}, {8, -28}, {-16, -9}, {16, -9}, {-5, 4}, {5, 4}};
		for (int[] l : lamps) lamp(l[0], l[1]);

		// trees on the grass around the square
		int[][] trees = {{-22, -14}, {20, -16}, {-24, 6}, {24, 4}, {-12, 18}, {12, 18}, {-26, -34}, {26, -34}};
		for (int[] t : trees) {
			s.runCommand("place feature minecraft:fancy_oak %d %d %d".formatted(x + t[0], y + 1, z + t[1]));
		}

		// planters along the main path
		for (int i : new int[] {-5, 5}) {
			for (int k : new int[] {2, 6}) {
				set(i, 0, k, "grass_block");
				set(i, 1, k, k == 2 ? "poppy" : "cornflower");
			}
		}
	}

	private void fountain(int cx, int cz) {
		fill(cx - 4, 0, cz - 4, cx + 4, 0, cz + 4, "stone_bricks");
		fill(cx - 4, 1, cz - 4, cx + 4, 1, cz + 4, "stone_brick_wall");
		fill(cx - 3, 1, cz - 3, cx + 3, 1, cz + 3, "water");
		fill(cx - 3, 0, cz - 3, cx + 3, 0, cz + 3, "polished_deepslate");
		fill(cx, 1, cz, cx, 4, cz, "chiseled_stone_bricks");
		set(cx, 5, cz, "water");
		set(cx - 4, 2, cz - 4, "lantern");
		set(cx + 4, 2, cz - 4, "lantern");
		set(cx - 4, 2, cz + 4, "lantern");
		set(cx + 4, 2, cz + 4, "lantern");
	}

	/** Timber-framed house with a stone base, windows and a gabled roof running along x. */
	private void house(int x0, int z0, int w, int d, int h, String wall, String frame, String stairs) {
		int x1 = x0 + w - 1;
		int z1 = z0 + d - 1;
		fill(x0, 1, z0, x1, h, z1, wall);
		fill(x0 + 1, 2, z0 + 1, x1 - 1, h - 1, z1 - 1, "air");
		fill(x0, 1, z0, x1, 1, z1, "stone_bricks");
		for (int[] c : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
			fill(c[0], 1, c[1], c[0], h, c[1], frame);
		}
		fill(x0, h, z0, x1, h, z0, frame);
		fill(x0, h, z1, x1, h, z1, frame);
		// windows on the south face, door in the middle
		for (int wx = x0 + 2; wx <= x1 - 2; wx += 3) {
			fill(wx, 3, z1, wx, Math.max(3, h - 2), z1, "glass_pane");
		}
		int door = (x0 + x1) / 2;
		fill(door, 2, z1, door, 3, z1, "air");
		set(door, 2, z1 + 1, "lantern");
		// gabled roof
		int layers = (d + 1) / 2 + 1;
		for (int k = 0; k < layers; k++) {
			int ry = h + 1 + k;
			int zs = z1 + 1 - k;
			int zn = z0 - 1 + k;
			if (zs < zn) break;
			if (zs == zn) {
				fill(x0 - 1, ry, zs, x1 + 1, ry, zs, stairs.replace("stairs", "slab"));
				break;
			}
			fill(x0 - 1, ry, zs, x1 + 1, ry, zs, stairs + "[facing=north]");
			fill(x0 - 1, ry, zn, x1 + 1, ry, zn, stairs + "[facing=south]");
			if (zs - zn > 1) {
				fill(x0, ry, zn + 1, x0, ry, zs - 1, wall);
				fill(x1, ry, zn + 1, x1, ry, zs - 1, wall);
			}
		}
	}

	private void stall(int sx, int sz, String roof) {
		for (int[] c : new int[][] {{0, 0}, {3, 0}, {0, 3}, {3, 3}}) {
			fill(sx + c[0], 1, sz + c[1], sx + c[0], 3, sz + c[1], "spruce_fence");
		}
		fill(sx, 4, sz, sx + 3, 4, sz + 3, roof);
		fill(sx + 1, 1, sz + 1, sx + 2, 1, sz + 1, "barrel");
		set(sx + 1, 1, sz + 2, "chest");
		set(sx + 2, 2, sz + 1, "flower_pot");
	}

	private void lamp(int lx, int lz) {
		fill(lx, 1, lz, lx, 3, lz, "dark_oak_fence");
		set(lx, 4, lz, "lantern");
	}

	private void fill(int ax, int ay, int az, int bx, int by, int bz, String block) {
		s.runCommand("fill %d %d %d %d %d %d minecraft:%s".formatted(x + ax, y + ay, z + az, x + bx, y + by, z + bz, block));
	}

	private void set(int ax, int ay, int az, String block) {
		s.runCommand("setblock %d %d %d minecraft:%s".formatted(x + ax, y + ay, z + az, block));
	}
}
