package com.aureole.compat;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.FriendsButton;
import net.minecraft.client.gui.screens.Screen;

/** Screen access for Minecraft 26.2+, where screens are managed by {@code Gui} and the Esc menu has a Friends button. */
public final class McScreens {
	private McScreens() { }

	public static @Nullable Screen current(Minecraft mc) {
		return mc.gui.screen();
	}

	public static void open(Minecraft mc, @Nullable Screen screen) {
		mc.gui.setScreen(screen);
	}

	public static boolean isFriendsButton(AbstractWidget widget) {
		return widget instanceof FriendsButton;
	}
}
