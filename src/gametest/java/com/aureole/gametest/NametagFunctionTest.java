package com.aureole.gametest;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import com.aureole.compat.McScreens;
import com.aureole.config.HaloConfig;
import com.aureole.gui.HaloConfigScreen;
import com.aureole.render.HaloRenderer;

/**
 * Drives the Nametag tab with real mouse clicks and keyboard input, then checks the settings changed and the
 * nametag renders without errors. Singleplayer only.
 */
public class NametagFunctionTest implements FabricClientGameTest {
	private static final int NAMETAG_TAB = 8;
	// control order in the Nametag tab
	private static final int TOGGLE = 0;
	private static final int NAME_FIELD = 1;
	private static final int TITLE_FIELD = 2;
	private static final int TITLE_TOGGLE = 3;
	private static final int INSERT_INTO = 6;
	private static final int SYMBOLS = 7;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (System.getenv("HALO_SHADERPACK") != null) return;

		ctx.getInput().resizeWindow(1600, 900);
		try (TestSingleplayerContext sp = ctx.worldBuilder()
				.adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
				.create()) {
			try {
				TestCompat.waitForChunksRender(sp);
			} catch (RuntimeException | AssertionError e) {
				ctx.waitTicks(100);
			}

			ctx.runOnClient(mc -> {
				HaloConfig c = new HaloConfig();
				c.enabled = false; // test the nametag on its own
				c.tagEnabled = false;
				c.tagText = "";
				c.tagTitle = "";
				HaloConfig.set(c);
				mc.options.guiScale().set(3);
				mc.resizeGui();
				HaloConfigScreen.setStartTab(NAMETAG_TAB);
			});
			ctx.setScreen(() -> new HaloConfigScreen(null));
			ctx.waitTicks(10); // open animation + first layout

			// 1) the toggle turns the nametag on
			click(ctx, TOGGLE, 0.95, 0.5);
			check(ctx, c -> c.tagEnabled, "clicking the toggle enables the nametag");

			// 2) typing into the name field
			click(ctx, NAME_FIELD, 0.5, 0.7);
			ctx.getInput().typeChars("Angel");
			ctx.getInput().pressKey(InputConstants.KEY_RETURN);
			ctx.waitTicks(2);
			check(ctx, c -> c.tagText.equals("Angel"), "typed name is saved");

			// 3) clicking a symbol appends it to the name
			click(ctx, SYMBOLS, 8, 8, true);
			check(ctx, c -> c.tagText.equals("Angel★"), "symbol ★ is appended to the name");

			// 4) switch the symbol target to the title, insert ✦
			click(ctx, INSERT_INTO, 0.9, 0.5);
			click(ctx, SYMBOLS, 2 * 19 + 8, 8, true);
			check(ctx, c -> c.tagTitle.equals("✦"), "symbol ✦ goes into the title");

			// 5) type a title with formatting codes
			click(ctx, TITLE_FIELD, 0.5, 0.7);
			ctx.getInput().typeChars(" &aGuardian");
			ctx.getInput().pressKey(InputConstants.KEY_RETURN);
			ctx.waitTicks(2);
			check(ctx, c -> c.tagTitle.equals("✦ &aGuardian"), "title with &-codes is saved");
			ctx.takeScreenshot("func_nametag_menu");

			// 6) the title toggle hides and shows the title without deleting it
			click(ctx, TITLE_TOGGLE, 0.95, 0.5);
			check(ctx, c -> !c.tagTitleEnabled && c.tagTitle.equals("✦ &aGuardian"), "title toggle hides the title and keeps its text");
			ctx.takeScreenshot("func_nametag_title_off");
			click(ctx, TITLE_TOGGLE, 0.95, 0.5);
			check(ctx, c -> c.tagTitleEnabled, "title toggle shows the title again");

			// 7) close and look at it in third person
			ctx.setScreen(() -> null);
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			ctx.waitTicks(10);
			ctx.takeScreenshot("func_nametag_world");

			ctx.runOnClient(mc -> {
				if (HaloRenderer.hasFailed()) throw new AssertionError("renderer threw while drawing the nametag");
			});
		}
	}

	/** Clicks a control at a fraction (or absolute offset) of its rectangle, using real mouse input. */
	private static void click(ClientGameTestContext ctx, int control, double fx, double fy) {
		click(ctx, control, fx, fy, false);
	}

	private static void click(ClientGameTestContext ctx, int control, double ox, double oy, boolean absolute) {
		double[] pos = null;

		// scroll the list with the real mouse wheel until the target point is visible
		for (int attempt = 0; attempt < 20 && pos == null; attempt++) {
			double[] r = ctx.computeOnClient(mc -> {
				if (!(McScreens.current(mc) instanceof HaloConfigScreen screen)) throw new AssertionError("settings screen is not open");
				int[] rect = screen.debugControlRect(control);
				int[] area = screen.debugContentBounds();
				double gx = absolute ? rect[0] + ox : rect[0] + rect[2] * ox;
				double gy = absolute ? rect[1] + oy : rect[1] + rect[3] * oy;
				double scale = mc.getWindow().getGuiScale();
				return new double[] {gx, gy, area[0], area[1], area[2], area[3], scale};
			});
			double scale = r[6];
			if (r[1] >= r[3] + 2 && r[1] <= r[5] - 2) {
				pos = new double[] {r[0] * scale, r[1] * scale, scale};
			} else {
				ctx.getInput().setCursorPos((r[2] + r[4]) / 2 * scale, (r[3] + r[5]) / 2 * scale);
				ctx.getInput().scroll(r[1] > r[5] ? -1 : 1);
				ctx.waitTicks(4);
			}
		}

		if (pos == null) throw new AssertionError("control " + control + " never scrolled into view");
		lastTarget = String.format(java.util.Locale.ROOT, "control %d at gui (%.1f, %.1f) -> cursor (%.1f, %.1f)",
				control, pos[0] / pos[2], pos[1] / pos[2], pos[0], pos[1]);
		ctx.getInput().setCursorPos(pos[0], pos[1]);
		ctx.waitTick();
		ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(2);
	}

	private static String lastTarget = "none";

	private interface Check {
		boolean ok(HaloConfig c);
	}

	private static void check(ClientGameTestContext ctx, Check check, String what) {
		boolean ok = ctx.computeOnClient(mc -> check.ok(HaloConfig.get()));
		if (!ok) {
			HaloConfig c = ctx.computeOnClient(mc -> HaloConfig.get());
			String window = ctx.computeOnClient(mc -> "gui " + mc.getWindow().getGuiScaledWidth() + "x"
					+ mc.getWindow().getGuiScaledHeight() + " scale " + mc.getWindow().getGuiScale()
					+ ", screen " + com.aureole.compat.McScreens.current(mc));
			throw new AssertionError("Nametag menu test failed: " + what + " (name='" + c.tagText + "', title='" + c.tagTitle
					+ "', enabled=" + c.tagEnabled + "; clicked " + lastTarget + "; screen saw " + HaloConfigScreen.debugLastClick()
					+ "; " + window + ")");
		}
	}
}
