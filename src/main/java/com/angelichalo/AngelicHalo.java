package com.angelichalo;

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

import com.angelichalo.compat.HaloPipelines;
import com.angelichalo.compat.McScreens;
import com.angelichalo.config.HaloConfig;
import com.angelichalo.config.HaloPresets;
import com.angelichalo.gui.HaloConfigScreen;
import com.angelichalo.gui.PauseMenuButton;
import com.angelichalo.render.HaloRenderer;
import com.angelichalo.render.HaloState;

/**
 * Angelic Halo — a purely cosmetic, client-side ring around your own player.
 *
 * <p>Nothing here sends packets, changes the world or touches game mechanics. Client commands are handled
 * locally by Fabric and never reach the server.
 */
public final class AngelicHalo implements ClientModInitializer {
	public static final String MOD_ID = "angelichalo";
	public static final Logger LOGGER = LoggerFactory.getLogger("AngelicHalo");

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
				"key.angelichalo.settings", InputConstants.Type.KEYSYM, InputConstants.KEY_H, category));
		toggleHalo = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.angelichalo.toggle", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
		cyclePreset = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.angelichalo.cycle_preset", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));

		ClientTickEvents.END_CLIENT_TICK.register(AngelicHalo::onTick);
		PauseMenuButton.register();
		LevelRenderEvents.COLLECT_SUBMITS.register(HaloRenderer::render);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
				ClientCommands.literal("angelichalo")
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
							ctx.getSource().sendFeedback(Component.translatable("angelichalo.msg.reloaded"));
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
											ctx.getSource().sendError(Component.translatable("angelichalo.msg.unknown_preset", name));
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
		actionBar(Component.translatable(c.enabled ? "angelichalo.msg.on" : "angelichalo.msg.off"));
	}

	private static void applyPreset(HaloPresets.Preset preset) {
		HaloConfig.set(preset.build(HaloConfig.get()));
		HaloConfig.save();
		actionBar(Component.translatable("angelichalo.msg.preset", Component.translatable("angelichalo.preset." + preset.id())));
	}

	/** Local-only message above the hotbar. */
	private static void actionBar(Component message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendOverlayMessage(message);
		}
	}
}
