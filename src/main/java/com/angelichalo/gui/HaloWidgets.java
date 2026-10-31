package com.angelichalo.gui;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

import com.angelichalo.render.HaloColors;

/** Small custom widgets for the settings screen. */
final class HaloWidgets {
	private HaloWidgets() { }

	/** Slider bound to a double setting, snapping to a step. */
	static final class Slider extends AbstractSliderButton {
		private final Component label;
		private final double min;
		private final double max;
		private final double step;
		private final DoubleSupplier getter;
		private final DoubleConsumer setter;
		private final DoubleFunction<String> format;

		Slider(int x, int y, int w, Component label, double min, double max, double step,
				DoubleSupplier getter, DoubleConsumer setter, DoubleFunction<String> format) {
			super(x, y, w, 20, Component.empty(), 0);
			this.label = label;
			this.min = min;
			this.max = max;
			this.step = step;
			this.getter = getter;
			this.setter = setter;
			this.format = format;
			this.value = (clamp(getter.getAsDouble()) - min) / (max - min);
			updateMessage();
		}

		private double clamp(double v) {
			return Math.max(min, Math.min(max, v));
		}

		private double current() {
			double v = min + value * (max - min);
			if (step > 0) v = Math.round(v / step) * step;
			return clamp(v);
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.empty().append(label).append(": " + format.apply(current())));
		}

		@Override
		protected void applyValue() {
			setter.accept(current());
		}
	}

	/** Rainbow strip: click or drag to pick a hue, keeping the colour's saturation/brightness. */
	static final class HueBar extends AbstractWidget {
		private final IntSupplier getter;
		private final IntConsumer setter;

		HueBar(int x, int y, int w, int h, IntSupplier getter, IntConsumer setter) {
			super(x, y, w, h, Component.translatable("angelichalo.gui.hue"));
			this.getter = getter;
			this.setter = setter;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			int x0 = getX();
			int y0 = getY();
			int w = getWidth();
			int h = getHeight();
			g.fill(x0 - 1, y0 - 1, x0 + w + 1, y0 + h + 1, isHoveredOrFocused() ? 0xFFFFFFFF : 0xFF000000);

			for (int i = 0; i < w; i++) {
				int rgb = HaloColors.hsv((double) i / w, 1.0, 1.0);
				g.fill(x0 + i, y0, x0 + i + 1, y0 + h, 0xFF000000 | rgb);
			}

			float[] hsv = rgbToHsv(getter.getAsInt());
			int mx = x0 + Math.round(hsv[0] * (w - 1));
			g.fill(mx - 1, y0 - 2, mx + 2, y0 + h + 2, 0xFF000000);
			g.fill(mx, y0 - 1, mx + 1, y0 + h + 1, 0xFFFFFFFF);
		}

		private void pick(double mouseX) {
			double hue = Math.max(0, Math.min(0.999, (mouseX - getX()) / getWidth()));
			float[] hsv = rgbToHsv(getter.getAsInt());
			// greys have no hue: give them some saturation so the pick is visible
			double s = hsv[1] < 0.05 ? 0.85 : hsv[1];
			double v = hsv[2] < 0.05 ? 1.0 : hsv[2];
			setter.accept(HaloColors.hsv(hue, s, v));
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			pick(event.x());
		}

		@Override
		protected void onDrag(MouseButtonEvent event, double dx, double dy) {
			pick(event.x());
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}

	/** Flat colour square; left click picks it as primary, right click as secondary. */
	static final class Swatch extends AbstractWidget {
		private final int rgb;
		private final IntConsumer left;
		private final IntConsumer right;

		Swatch(int x, int y, int size, int rgb, IntConsumer left, IntConsumer right) {
			super(x, y, size, size, Component.literal(String.format("#%06X", rgb)));
			this.rgb = rgb;
			this.left = left;
			this.right = right;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			int x0 = getX();
			int y0 = getY();
			g.fill(x0, y0, x0 + width, y0 + height, isHoveredOrFocused() ? 0xFFFFFFFF : 0xFF202020);
			g.fill(x0 + 1, y0 + 1, x0 + width - 1, y0 + height - 1, 0xFF000000 | rgb);
		}

		@Override
		protected boolean isValidClickButton(MouseButtonInfo info) {
			return info.button() == 0 || info.button() == 1;
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			if (event.button() == 1) {
				right.accept(rgb);
			} else {
				left.accept(rgb);
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}

	/** Non-interactive live colour preview: shows a colour, or an animated strip of a colour function. */
	static final class Preview extends AbstractWidget {
		interface ColorFn {
			int color(double u);
		}

		private final ColorFn fn;

		Preview(int x, int y, int w, int h, ColorFn fn) {
			super(x, y, w, h, Component.empty());
			this.fn = fn;
			this.active = false;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			int x0 = getX();
			int y0 = getY();
			g.fill(x0 - 1, y0 - 1, x0 + width + 1, y0 + height + 1, 0xFF000000);
			// checkerboard so transparency is visible
			for (int i = 0; i < width; i += 4) {
				for (int j = 0; j < height; j += 4) {
					boolean dark = ((i / 4) + (j / 4)) % 2 == 0;
					g.fill(x0 + i, y0 + j, Math.min(x0 + i + 4, x0 + width), Math.min(y0 + j + 4, y0 + height),
							dark ? 0xFF2A2A2A : 0xFF3A3A3A);
				}
			}

			for (int i = 0; i < width; i++) {
				g.fill(x0 + i, y0, x0 + i + 1, y0 + height, fn.color((double) i / width));
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
		}
	}

	static float[] rgbToHsv(int rgb) {
		double r = ((rgb >> 16) & 0xFF) / 255.0;
		double g = ((rgb >> 8) & 0xFF) / 255.0;
		double b = (rgb & 0xFF) / 255.0;
		double max = Math.max(r, Math.max(g, b));
		double min = Math.min(r, Math.min(g, b));
		double d = max - min;
		double h;

		if (d < 1e-9) {
			h = 0;
		} else if (max == r) {
			h = ((g - b) / d) / 6.0;
		} else if (max == g) {
			h = ((b - r) / d + 2) / 6.0;
		} else {
			h = ((r - g) / d + 4) / 6.0;
		}

		h = h - Math.floor(h);
		return new float[] {(float) h, (float) (max <= 0 ? 0 : d / max), (float) max};
	}
}
