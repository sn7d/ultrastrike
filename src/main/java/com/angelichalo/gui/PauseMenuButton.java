package com.angelichalo.gui;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.FriendsButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import com.angelichalo.gui.ui.Ui;
import com.angelichalo.render.HaloColors;
import com.angelichalo.render.HaloState;

/** Small animated halo button placed next to the Friends button on the Esc menu. */
public final class PauseMenuButton extends AbstractWidget {
	private float hover;
	private long last = System.nanoTime();

	public PauseMenuButton(int x, int y, int size) {
		super(x, y, size, size, Component.translatable("angelichalo.gui.title"));
		setTooltip(Tooltip.create(Component.translatable("angelichalo.gui.pause_tip")));
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof PauseScreen) attach(screen);
		});
	}

	private static void attach(Screen screen) {
		List<AbstractWidget> widgets = Screens.getWidgets(screen);
		AbstractWidget anchor = null;
		for (AbstractWidget wdg : widgets) {
			if (wdg instanceof FriendsButton) {
				anchor = wdg;
				break;
			}
		}

		int size = 20;
		int x;
		int y;

		if (anchor != null) {
			// directly to the left of Friends, same row
			x = anchor.getX() - size - 4;
			y = anchor.getY() + (anchor.getHeight() - size) / 2;
		} else {
			// fallback: left of the topmost wide button (Back to Game)
			AbstractWidget top = null;
			for (AbstractWidget wdg : widgets) {
				if (wdg.getWidth() >= 150 && (top == null || wdg.getY() < top.getY())) top = wdg;
			}
			if (top == null) return;
			x = top.getX() - size - 4;
			y = top.getY();
		}

		widgets.add(new PauseMenuButton(x, y, size));
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		hover = Ui.approach(hover, isHoveredOrFocused() ? 1f : 0f, dt, 14f);

		int x0 = getX();
		int y0 = getY();
		int x1 = x0 + width;
		int y1 = y0 + height;
		Ui.round(g, x0, y0, x1, y1, 5, Ui.mix(0xE0101118, 0xF01C1E2A, hover));
		Ui.roundOutline(g, x0, y0, x1, y1, 5, Ui.mix(0x50FFD27A, 0xFFFFD27A, hover));

		// tiny tilted ring that spins faster on hover
		double t = HaloState.now() * (0.8 + 1.6 * hover);
		double cx = x0 + width / 2.0;
		double cy = y0 + height / 2.0 + 1;
		for (int i = 0; i < 40; i++) {
			double a = i / 40.0 * Math.PI * 2 + t;
			int px = (int) Math.round(cx + Math.cos(a) * 6.5);
			int py = (int) Math.round(cy + Math.sin(a) * 2.6);
			boolean front = Math.sin(a) > 0;
			int col = 0xFF000000 | HaloColors.hsv(0.11 + 0.04 * Math.sin(a + t), 0.55, front ? 1.0 : 0.6);
			g.fill(px, py, px + 1, py + 1, col);
		}
		g.fill((int) cx - 1, (int) cy - 6, (int) cx + 1, (int) cy - 4, 0xFFFFE7A0);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		Minecraft mc = Minecraft.getInstance();
		mc.gui.setScreen(new HaloConfigScreen(mc.gui.screen()));
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
