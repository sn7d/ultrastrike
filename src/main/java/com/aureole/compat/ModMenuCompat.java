package com.aureole.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import com.aureole.gui.HaloConfigScreen;

/** Optional: adds a "Config" button for Aureole in Mod Menu. Only loaded when Mod Menu is installed. */
public final class ModMenuCompat implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return HaloConfigScreen::new;
	}
}
