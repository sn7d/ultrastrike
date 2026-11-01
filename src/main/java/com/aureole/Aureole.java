package com.aureole;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import com.aureole.compat.HaloPipelines;
import com.aureole.compat.McScreens;
import com.aureole.config.HaloConfig;
import com.aureole.config.HaloPresets;
import com.aureole.gui.HaloConfigScreen;
import com.aureole.gui.PauseMenuButton;
import com.aureole.render.HaloRenderer;
import com.aureole.render.HaloState;

/**
 * Aureole — a purely cosmetic, client-side ring around your own player.
 *
 * <p>Nothing here sends packets, changes the world or touches game mechanics. Client commands are handled
 * locally by Fabric and never reach the server.
 */
public final class Aureole implements ClientModInitializer {
	public static final String MOD_ID = "aureole";
	public static final Logger LOGGER = LoggerFactory.getLogger("Aureole");

	private static KeyMapping openSettings;
	private static KeyMapping toggleHalo;
	private static KeyMapping cyclePreset;
	private static boolean openScreenNextTick;
	private static int presetIndex;

	@Override
	public void onInitializeClient() {
		HaloConfig.load();
		HaloPipelines.registerWithIris();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		openSettings = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.aureole.settings", InputConstants.Type.KEYSYM, InputConstants.KEY_H, category));
		toggleHalo = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.aureole.toggle", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
		cyclePreset = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.aureole.cycle_preset", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));

		ClientTickEvents.END_CLIENT_TICK.register(Aureole::onTick);
		PauseMenuButton.register();
		LevelRenderEvents.COLLECT_SUBMITS.register(HaloRenderer::render);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
				ClientCommands.literal("aureole")
						.executes(ctx -> {
							openScreenNextTick = true;
							return 1;
						})
						.then(ClientCommands.literal("toggle").executes(ctx -> {
							toggle();
							return 1;
						}))
						.then(ClientCommands.literal("reload").executes(ctx -> {
							HaloConfig.load();
							ctx.getSource().sendFeedback(Component.translatable("aureole.msg.reloaded"));
							return 1;
						}))
						.then(ClientCommands.literal("preset")
								.then(ClientCommands.argument("name", StringArgumentType.word())
										.suggests((ctx, builder) -> {
											HaloPresets.ALL.forEach(p -> builder.suggest(p.id()));
											return builder.buildFuture();
										})
										.executes(ctx -> {
											String name = StringArgumentType.getString(ctx, "name");
											for (HaloPresets.Preset p : HaloPresets.ALL) {
												if (p.id().equalsIgnoreCase(name)) {
													applyPreset(p);
													return 1;
												}
											}
											ctx.getSource().sendError(Component.translatable("aureole.msg.unknown_preset", name));
											return 0;
										})))));
	}

	private static void onTick(Minecraft mc) {
		HaloState.tick(mc);

		if (openScreenNextTick) {
			openScreenNextTick = false;
			McScreens.open(mc, new HaloConfigScreen(McScreens.current(mc)));
		}

		while (openSettings.consumeClick()) {
			if (McScreens.current(mc) == null) {
				McScreens.open(mc, new HaloConfigScreen(null));
			}
		}

		while (toggleHalo.consumeClick()) {
			toggle();
		}

		while (cyclePreset.consumeClick()) {
			presetIndex = (presetIndex + 1) % HaloPresets.ALL.size();
			applyPreset(HaloPresets.ALL.get(presetIndex));
		}
	}

	private static void toggle() {
		HaloConfig c = HaloConfig.get();
		c.enabled = !c.enabled;
		HaloConfig.save();
		actionBar(Component.translatable(c.enabled ? "aureole.msg.on" : "aureole.msg.off"));
	}

	private static void applyPreset(HaloPresets.Preset preset) {
		HaloConfig.set(preset.build(HaloConfig.get()));
		HaloConfig.save();
		actionBar(Component.translatable("aureole.msg.preset", Component.translatable("aureole.preset." + preset.id())));
	}

	/** Local-only message above the hotbar. */
	private static void actionBar(Component message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendOverlayMessage(message);
		}
	}
}
