package com.aureole.compat;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

/** Screen access for Minecraft 26.1.x, where screens live on {@code Minecraft} and the Esc menu has no Friends button. */
public final class McScreens {
	private McScreens() { }

	public static @Nullable Screen current(Minecraft mc) {
		return mc.screen;
	}

	public static void open(Minecraft mc, @Nullable Screen screen) {
		mc.setScreen(screen);
	}

	public static boolean isFriendsButton(AbstractWidget widget) {
		return false;
	}
}
