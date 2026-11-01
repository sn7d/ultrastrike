package com.aureole.gametest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Test-only: logs which screen is open every ~5 s, so a run stuck behind another mod's popup is easy to diagnose. */
public class TestWatchdog implements ClientModInitializer {
	private int ticks;
	private String last = "";

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void tick(Minecraft mc) {
		if (++ticks % 100 != 0) return;
		Screen screen = com.aureole.compat.McScreens.current(mc);
		String now = screen == null ? "none (in world: " + (mc.level != null) + ")"
				: screen.getClass().getName() + " \"" + screen.getTitle().getString() + "\"";
		if (!now.equals(last) || ticks % 1200 == 0) {
			System.out.println("[halo-watchdog] screen = " + now);
			last = now;
		}
	}
}
