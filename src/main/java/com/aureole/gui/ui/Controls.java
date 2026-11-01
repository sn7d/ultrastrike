package com.aureole.gui.ui;

import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import com.aureole.render.HaloColors;

/** The custom-drawn controls used by the settings screen. */
public final class Controls {
	private Controls() { }

	/** Base class: positioned by the screen every frame. */
	public abstract static class Control {
		public int x;
		public int y;
		public int w;
		public float hover;
		public String tooltip;

		public abstract int height();

		public abstract void draw(GuiGraphicsExtractor g, int mx, int my, float dt);

		public boolean mouseDown(double mx, double my, int button) {
			return false;
		}

		public void mouseDrag(double mx, double my) {
		}

		public void mouseUp() {
		}

		public boolean keyPressed(KeyEvent e) {
			return false;
		}

		public boolean charTyped(int codepoint) {
			return false;
		}

		public boolean focused() {
			return false;
		}

		public void unfocus() {
		}

		public boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + height();
		}

		protected void animateHover(int mx, int my, float dt) {
			hover = Ui.approach(hover, contains(mx, my) ? 1f : 0f, dt, 14f);
		}

		public Control tip(String t) {
			this.tooltip = t;
			return this;
		}
	}

	// ------------------------------------------------------------------------------------------------

	public static final class Header extends Control {
		private final String title;
		private final String subtitle;

		public Header(String title, String subtitle) {
			this.title = title;
			this.subtitle = subtitle;
		}

		@Override
		public int height() {
			return subtitle == null ? 16 : 26;
		}

		@Override
		public void draw(GuiGraphicsExtractor g, int mx, int my, float dt) {
			Ui.text(g, title.toUpperCase(Locale.ROOT), x, y + 5, Ui.ACCENT, Ui.FONT_BOLD);
			int tw = Ui.width(title.toUpperCase(Locale.ROOT), Ui.FONT_BOLD);
			Ui.gradientH(g, x + tw + 6, y + 9, x + w, y + 10, Ui.withAlpha(Ui.ACCENT, 0.35f), 0x00FFD27A);
			if (subtitle != null) Ui.text(g, Ui.fit(subtitle, w), x, y + 16, Ui.TEXT_MUTED);
		}
	}

	public static final class Note extends Control {
		private final Supplier<String> text;
		private final int color;

		public Note(Supplier<String> text, int color) {
			this.text = text;
			this.color = color;
		}

		@Override
		public int height() {
			return 12 * lines().length;
		}

		private String[] lines() {
			String t = text.get();
			if (w <= 20) return new String[] {t};
			java.util.List<String> out = new java.util.ArrayList<>();
			StringBuilder line = new StringBuilder();
			for (String word : t.split(" ")) {
				String trial = line.isEmpty() ? word : line + " " + word;
				if (Ui.width(trial) > w && !line.isEmpty()) {
					out.add(line.toString());
					line = new StringBuilder(word);
				} else {
					line = new StringBuilder(trial);
				}
			}
			if (!line.isEmpty()) out.add(line.toString());
			return out.toArray(new String[0]);
		}

		@Override
		public void draw(GuiGraphicsExtractor g, int mx, int my, float dt) {
			String[] ls = lines();
			for (int i = 0; i < ls.length; i++) Ui.text(g, ls[i], x, y + 2 + i * 12, color);
		}
	}

	// ------------------------------------------------------------------------------------------------

	public static final class Slider extends Control {
		private final String label;
		private final double min;
		private final double max;
		private final double step;
		private final DoubleSupplier get;
		private final DoubleConsumer set;
		private final DoubleFunction<String> format;
		private boolean dragging;
		private float shown = -1;

		public Slider(String label, double min, double max, double step, DoubleSupplier get, DoubleConsumer set, DoubleFunction<String> format) {
			this.label = label;
			this.min = min;
			this.max = max;
			this.step = step;
			this.get = get;
			this.set = set;
			this.format = format;
		}

		@Override
		public int height() {
			return 24;
		}

		private float frac() {
			return (float) ((Math.max(min, Math.min(max, get.getAsDouble())) - min) / (max - min));
		}

		@Override
		public void draw(GuiGraphicsExtractor g, int mx, int my, float dt) {
			animateHover(mx, my, dt);
			float target = frac();
			shown = shown < 0 ? target : Ui.approach(shown, target, dt, dragging ? 40f : 18f);
			Ui.round(g, x - 4, y, x + w + 4, y + height(), 4, Ui.withAlpha(0x14FFFFFF, hover));
			Ui.text(g, label, x, y + 3, Ui.TEXT);
			Ui.textRight(g, format.apply(get.getAsDouble()), x + w, y + 3, Ui.mix(Ui.TEXT_MUTED, Ui.ACCENT, Math.max(hover, dragging ? 1 : 0)));

			int ty = y + 17;
			Ui.round(g, x, ty - 1, x + w, ty + 2, 1, 0x26FFFFFF);
			int fx = x + Math.round(w * shown);
			Ui.gradientH(g, x, ty - 1, Math.max(x + 1, fx), ty + 2, Ui.withAlpha(Ui.ACCENT, 0.55f), Ui.ACCENT);
			int k = dragging ? 4 : (int) (3 + hover);
			Ui.round(g, fx - k - 1, ty + 1 - k - 1, fx + k + 1, ty + 1 + k + 1, k + 1, 0x55000000);
			Ui.round(g, fx - k, ty + 1 - k, fx + k, ty + 1 + k, k, 0xFFFFFFFF);
		}

		@Override
		public boolean mouseDown(double mx, double my, int button) {
			if (button != InputConstants.MOUSE_BUTTON_LEFT || !contains(mx, my)) return false;
			dragging = true;
			mouseDrag(mx, my);
			return true;
		}

		@Override
		public void mouseDrag(double mx, double my) {
			if (!dragging) return;
			double f = Math.max(0, Math.min(1, (mx - x) / Math.max(1, w)));
			double v = min + f * (max - min);
			if (step > 0) v = Math.round(v / step) * step;
			set.accept(Math.max(min, Math.min(max, v)));
		}

		@Override
		public void mouseUp() {
			dragging = false;
		}
	}

	public static final class Toggle extends Control {
		private final String label;
		private final BooleanSupplier get;
		private final Consumer<Boolean> set;
		private float knob = -1;

		public Toggle(String label, BooleanSupplier get, Consumer<Boolean> set) {
			this.label = label;
			this.get = get;
			this.set = set;
		}

		@Override
		public int height() {
			return 18;
		}

		@Override
		public void draw(GuiGraphicsExtractor g, int mx, int my, float dt) {
			animateHover(mx, my, dt);
			boolean on = get.getAsBoolean();
			knob = knob < 0 ? (on ? 1 : 0) : Ui.approach(knob, on ? 1f : 0f, dt, 16f);
			Ui.round(g, x - 4, y, x + w + 4, y + height(), 4, Ui.withAlpha(0x14FFFFFF, hover));
			Ui.text(g, label, x, y + 5, Ui.TEXT);

			int pw = 22;
			int ph = 12;
			int px = x + w - pw;
			int py = y + 3;
			Ui.round(g, px, py, px + pw, py + ph, 6, Ui.mix(0x33FFFFFF, Ui.ACCENT, knob));
			int kx = px + 2 + Math.round((pw - 12) * knob);
			Ui.round(g, kx, py + 2, kx + 8, py + 10, 4, Ui.mix(0xFFCDD0DC, 0xFF1A1407, knob));
		}

		@Override
		public boolean mouseDown(double mx, double my, int button) {
			if (button != InputConstants.MOUSE_BUTTON_LEFT || !contains(mx, my)) return false;
			set.accept(!get.getAsBoolean());
			return true;
		}
	}

	/** Enum/value selector: click right half for next, left half (or right-click) for previous. */
	public static final class Choice<T> extends Control {
		private final String label;
		private final List<T> values;
		private final Supplier<T> get;
		private final Consumer<T> set;
		private final Function<T, String> name;
		private float flash;

		public Choice(String label, List<T> values, Supplier<T> get, Consumer<T> set, Function<T, String> name) {
			this.label = label;
			this.values = values;
			this.get = get;
			this.set = set;
			this.name = name;
		}

		@Override
		public int height() {
			return 18;
		}

		@Override
		public void draw(GuiGraphicsExtractor g, int mx, int my, float dt) {
			animateHover(mx, my, dt);
			flash = Ui.approach(flash, 0f, dt, 6f);
			Ui.round(g, x - 4, y, x + w + 4, y + height(), 4, Ui.withAlpha(0x14FFFFFF, hover));
			Ui.text(g, label, x, y + 5, Ui.TEXT);

			String v = name.apply(get.get());
			int vw = Ui.width(v);
			int right = x + w;
			int boxL = right - vw - 26;
			Ui.round(g, boxL, y + 2, right, y + 16, 4, Ui.mix(Ui.BG_FIELD, Ui.BG_FIELD_HOVER, hover));
			if (flash > 0.01f) Ui.round(g, boxL, y + 2, right, y + 16, 4, Ui.withAlpha(Ui.ACCENT, flash * 0.35f));
			Ui.text(g, "‹", boxL + 4, y + 5, Ui.mix(Ui.TEXT_DIM, Ui.ACCENT, hover));
			Ui.text(g, v, boxL + 13, y + 5, Ui.TEXT);
			Ui.text(g, "›", right - 9, y + 5, Ui.mix(Ui.TEXT_DIM, Ui.ACCENT, hover));
		}

		@Override
		public boolean mouseDown(double mx, double my, int button) {
			if (!contains(mx, my)) return false;
			int i = Math.max(0, values.indexOf(get.get()));
			boolean back = button == InputConstants.MOUSE_BUTTON_RIGHT || mx < x + w * 0.5;
			int n = values.size();
			set.accept(values.get(((i + (back ? -1 : 1)) % n + n) % n));
			flash = 1f;
			return true;
		}
	}

	public static final class Button extends Control {
		private final Supplier<String> label;
		private final Runnable action;
		private final boolean primary;
		private float press;

		public Button(Supplier<String> label, Runnable action, boolean primary) {
			this.label = label;
			this.action = action;
			this.primary = primary;
		}

		@Override
		public int height() {
			return 18;
		}

		@Override
		public void draw(GuiGraphicsExtractor g, int mx, int my, float dt) {
			animateHover(mx, my, dt);
			press = Ui.approach(press, 0f, dt, 10f);
			int bg = primary ? Ui.mix(0xFFE9BE63, Ui.ACCENT, hover) : Ui.mix(Ui.BG_FIELD, Ui.BG_FIELD_HOVER, hover);
			int inset = Math.round(press * 1.5f);
			Ui.round(g, x + inset, y + inset, x + w - inset, y + height() - inset, 5, bg);
			if (!primary) Ui.roundOutline(g, x + inset, y + inset, x + w - inset, y + height() - inset, 5, Ui.withAlpha(Ui.ACCENT, 0.15f + 0.35f * hover));
			String t = Ui.fit(label.get(), w - 8);
			Ui.textCentered(g, t, x + w / 2, y + 5, primary ? 0xFF1A1407 : Ui.TEXT, primary ? Ui.FONT_BOLD : Ui.FONT);
		}

		@Override
		public boolean mouseDown(double mx, double my, int button) {
			if (button != InputConstants.MOUSE_BUTTON_LEFT || !contains(mx, my)) return false;
			press = 1f;
			Minecraft.getInstance().execute(action);
			return true;
		}
	}

	/** Several controls side by side in one row. */
	public static final class Row extends Control {
		private final Control[] parts;

		public Row(Control... parts) {
			this.parts = parts;
		}

		@Override
		public int height() {
			int h = 0;
			for (Control c : parts) h = Math.max(h, c.height());
			return h;
		}

		private void layout() {
			int gap = 6;
			int pw = (w - gap * (parts.length - 1)) / parts.length;
			for (int i = 0; i < parts.length; i++) {
				parts[i].x = x + i * (pw + gap);
				parts[i].y = y;
				parts[i].w = pw;
			}
		}

		@Override
		public void draw(GuiGraphicsExtractor g, int mx, int my, float dt) {
			layout();
			for (Control c : parts) c.draw(g, mx, my, dt);
		}

		@Override
		public boolean mouseDown(double mx, double my, int button) {
			layout();
			for (Control c : parts) if (c.mouseDown(mx, my, button)) return true;
			return false;
		}

		@Override
		public void mouseDrag(double mx, double my) {
			for (Control c : parts) c.mouseDrag(mx, my);
		}

		@Override
		public void mouseUp() {
			for (Control c : parts) c.mouseUp();
		}

		@Override
		public boolean keyPressed(KeyEvent e) {
			for (Control c : parts) if (c.keyPressed(e)) return true;
			return false;
		}

		@Override
		public boolean charTyped(int cp) {
			for (Control c : parts) if (c.charTyped(cp)) return true;
			return false;
		}

		@Override
		public boolean focused() {
			for (Control c : parts) if (c.focused()) return true;
			return false;
		}

		@Override
		public void unfocus() {
			for (Control c : parts) c.unfocus();
		}

		public Control hovered(double mx, double my) {
			for (Control c : parts) if (c.contains(mx, my)) return c;
			return this;
		}
	}

	// ------------------------------------------------------------------------------------------------

	/** Single-line text input. */
	public static final class TextField extends Control {
		private final String label;
		private final Supplier<String> get;
		private final Consumer<String> set;
		private final int maxLength;
		private final java.util.function.Predicate<Integer> allowed;
		private boolean focus;
		private String buffer;
		private float glow;

		public TextField(String label, Supplier<String> get, Consumer<String> set, int maxLength, java.util.function.Predicate<Integer> allowed) {
			this.label = label;
			this.get = get;
			this.set = set;
			this.maxLength = maxLength;
			this.allowed = allowed;
		}

		@Override
		public int height() {
			return label == null ? 18 : 30;
		}

		@Override
		public void draw(GuiGraphicsExtractor g, int mx, int my, float dt) {
			animateHover(mx, my, dt);
			glow = Ui.approach(glow, focus ? 1f : 0f, dt, 14f);
			int fy = y;
			if (label != null) {
				Ui.text(g, label, x, y + 2, Ui.TEXT);
				fy = y + 12;
			}
			String value = focus ? buffer : get.get();
			Ui.round(g, x, fy, x + w, fy + 18, 4, Ui.mix(Ui.BG_FIELD, Ui.BG_FIELD_HOVER, Math.max(hover, glow)));
			Ui.roundOutline(g, x, fy, x + w, fy + 18, 4, Ui.withAlpha(Ui.ACCENT, 0.12f + 0.6f * glow));
			String shown = value;
			while (Ui.width(shown) > w - 14 && shown.length() > 1) shown = shown.substring(1);
			Ui.text(g, shown.isEmpty() && !focus ? "…" : shown, x + 6, fy + 5, shown.isEmpty() ? Ui.TEXT_DIM : Ui.TEXT);
			if (focus && (System.currentTimeMillis() / 500) % 2 == 0) {
				int cx = x + 6 + Ui.width(shown) + 1;
				Ui.fill(g, cx, fy + 4, cx + 1, fy + 14, Ui.ACCENT);
			}
		}

		@Override
		public boolean mouseDown(double mx, double my, int button) {
			if (contains(mx, my)) {
				if (!focus) {
					focus = true;
					buffer = get.get();
				}
				return true;
			}
			unfocus();
			return false;
		}

		@Override
		public boolean focused() {
			return focus;
		}

		@Override
		public void unfocus() {
			if (focus) {
				focus = false;
				set.accept(buffer);
			}
		}

		@Override
		public boolean keyPressed(KeyEvent e) {
			if (!focus) return false;
			int key = e.key();
			if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_ESCAPE) {
				unfocus();
				return true;
			}
			if (key == InputConstants.KEY_BACKSPACE) {
				if (!buffer.isEmpty()) buffer = buffer.substring(0, buffer.offsetByCodePoints(buffer.length(), -1));
				set.accept(buffer);
				return true;
			}
			if (key == InputConstants.KEY_V && (e.modifiers() & InputConstants.MOD_CONTROL) != 0) {
				String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
				clip.codePoints().filter(cp -> allowed.test(cp)).forEach(this::append);
				set.accept(buffer);
				return true;
			}
			return true; // swallow other keys while typing (e.g. inventory key)
		}

		private void append(int cp) {
			if (buffer.codePointCount(0, buffer.length()) < maxLength) buffer += new String(Character.toChars(cp));
		}

		@Override
		public boolean charTyped(int cp) {
			if (!focus) return false;
			if (allowed.test(cp)) {
				append(cp);
				set.accept(buffer);
			}
			return true;
		}
	}

	// ------------------------------------------------------------------------------------------------

	/** Colour row: swatch, hex field and a draggable hue/brightness strip. */
	public static final class ColorRow extends Control {
		private final String label;
		private final IntSupplier get;
		private final IntConsumer set;
		private final TextField hex;
		private boolean draggingHue;
		private boolean draggingVal;

		public ColorRow(String label, IntSupplier get, IntConsumer set) {
			this.label = label;
			this.get = get;
			this.set = set;
			this.hex = new TextField(null, () -> String.format("#%06X", get.getAsInt()), s -> {
				String t = s.startsWith("#") ? s.substring(1) : s;
				if (t.length() == 6) {
					try {
						set.accept(Integer.parseInt(t, 16));
					} catch (NumberFormatException ignored) {
						// still typing
					}
				}
			}, 7, cp -> cp == '#' || Character.digit(cp, 16) >= 0);
		}

		@Override
		public int height() {
			return 40;
		}

		private int hueX0() {
			return x;
		}

		@Override
		public void draw(GuiGraphicsExtractor g, int mx, int my, float dt) {
			animateHover(mx, my, dt);
			int rgb = get.getAsInt();
			Ui.text(g, label, x, y + 5, Ui.TEXT);
			int sw = 14;
			int right = x + w;
			hex.x = right - 64;
			hex.y = y;
			hex.w = 64;
			hex.draw(g, mx, my, dt);
			Ui.round(g, hex.x - sw - 6, y + 2, hex.x - 6, y + 16, 4, 0xFF000000 | rgb);
			Ui.roundOutline(g, hex.x - sw - 6, y + 2, hex.x - 6, y + 16, 4, 0x40FFFFFF);

			// hue strip
			float[] hsv = rgbToHsv(rgb);
			int hy = y + 22;
			int hw = w;
			for (int i = 0; i < hw; i++) {
				Ui.fill(g, x + i, hy, x + i + 1, hy + 6, 0xFF000000 | HaloColors.hsv((double) i / hw, 1, 1));
			}
			int hx = x + Math.round(hsv[0] * (hw - 1));
			Ui.round(g, hx - 3, hy - 2, hx + 3, hy + 8, 2, 0xFFFFFFFF);
			Ui.fill(g, hx - 1, hy, hx + 1, hy + 6, 0xFF000000 | HaloColors.hsv(hsv[0], 1, 1));

			// brightness/saturation strip: dark -> pure hue -> white
			int vy = y + 32;
			int pure = HaloColors.hsv(hsv[0], 1, 1);
			Ui.gradientH(g, x, vy, x + hw / 2, vy + 5, 0xFF000000, 0xFF000000 | pure);
			Ui.gradientH(g, x + hw / 2, vy, x + hw, vy + 5, 0xFF000000 | pure, 0xFFFFFFFF);
			float pos = hsv[1] >= 0.999f ? hsv[2] * 0.5f : 0.5f + (1f - hsv[1]) * 0.5f;
			int vx = x + Math.round(pos * (hw - 1));
			Ui.round(g, vx - 3, vy - 2, vx + 3, vy + 7, 2, 0xFFFFFFFF);
		}

		@Override
		public boolean mouseDown(double mx, double my, int button) {
			if (hex.mouseDown(mx, my, button)) return true;
			if (mx >= x && mx < x + w && my >= y + 20 && my < y + 30) {
				draggingHue = true;
				mouseDrag(mx, my);
				return true;
			}
			if (mx >= x && mx < x + w && my >= y + 30 && my < y + 40) {
				draggingVal = true;
				mouseDrag(mx, my);
				return true;
			}
			return false;
		}

		@Override
		public void mouseDrag(double mx, double my) {
			double f = Math.max(0, Math.min(0.999, (mx - x) / Math.max(1, w)));
			float[] hsv = rgbToHsv(get.getAsInt());
			if (draggingHue) {
				double s = hsv[1] < 0.05 ? 1.0 : hsv[1];
				double v = hsv[2] < 0.05 ? 1.0 : hsv[2];
				set.accept(HaloColors.hsv(f, s, v));
			} else if (draggingVal) {
				if (f < 0.5) set.accept(HaloColors.hsv(hsv[0], 1.0, f * 2));
				else set.accept(HaloColors.hsv(hsv[0], 1.0 - (f - 0.5) * 2, 1.0));
			}
		}

		@Override
		public void mouseUp() {
			draggingHue = false;
			draggingVal = false;
		}

		@Override
		public boolean keyPressed(KeyEvent e) {
			return hex.keyPressed(e);
		}

		@Override
		public boolean charTyped(int cp) {
			return hex.charTyped(cp);
		}

		@Override
		public boolean focused() {
			return hex.focused();
		}

		@Override
		public void unfocus() {
			hex.unfocus();
		}
	}

	/** Quick palette: left-click sets primary, right-click secondary. */
	public static final class Palette extends Control {
		private final int[] colors;
		private final IntConsumer primary;
		private final IntConsumer secondary;

		public Palette(int[] colors, IntConsumer primary, IntConsumer secondary) {
			this.colors = colors;
			this.primary = primary;
			this.secondary = secondary;
			this.tooltip = "Left-click: primary · Right-click: secondary";
		}

		@Override
		public int height() {
			return 16;
		}

		private int size() {
			return Math.max(8, Math.min(16, (w + 3) / colors.length - 3));
		}

		@Override
		public void draw(GuiGraphicsExtractor g, int mx, int my, float dt) {
			int s = size();
			for (int i = 0; i < colors.length; i++) {
				int sx = x + i * (s + 3);
				boolean over = mx >= sx && mx < sx + s && my >= y && my < y + s;
				int grow = over ? 1 : 0;
				Ui.round(g, sx - grow, y + 1 - grow, sx + s + grow, y + 1 + s + grow, 3, 0xFF000000 | colors[i]);
				if (over) Ui.roundOutline(g, sx - 2, y - 1, sx + s + 2, y + s + 3, 3, 0xCCFFFFFF);
			}
		}

		@Override
		public boolean mouseDown(double mx, double my, int button) {
			int s = size();
			for (int i = 0; i < colors.length; i++) {
				int sx = x + i * (s + 3);
				if (mx >= sx && mx < sx + s && my >= y && my < y + s + 2) {
					(button == InputConstants.MOUSE_BUTTON_RIGHT ? secondary : primary).accept(colors[i]);
					return true;
				}
			}
			return false;
		}
	}

	/** Animated strip showing a colour function around the ring. */
	public static final class Strip extends Control {
		private final String label;
		private final java.util.function.DoubleUnaryOperator unused = d -> d;
		private final java.util.function.IntUnaryOperator colorAt;

		public Strip(String label, java.util.function.IntUnaryOperator colorAt) {
			this.label = label;
			this.colorAt = colorAt;
		}

		@Override
		public int height() {
			return 20;
		}

		@Override
		public void draw(GuiGraphicsExtractor g, int mx, int my, float dt) {
			Ui.text(g, label, x, y + 5, Ui.TEXT_MUTED);
			int sx = x + Math.max(40, Ui.width(label) + 8);
			int sw = x + w - sx;
			Ui.round(g, sx - 1, y + 3, x + w + 1, y + 15, 3, 0xFF000000);
			for (int i = 0; i < sw; i++) {
				int checker = ((i / 4) % 2 == 0) ? 0xFF2A2A2A : 0xFF3A3A3A;
				Ui.fill(g, sx + i, y + 4, sx + i + 1, y + 14, checker);
				Ui.fill(g, sx + i, y + 4, sx + i + 1, y + 14, colorAt.applyAsInt(i * 1000 / Math.max(1, sw)));
			}
		}
	}

	// ------------------------------------------------------------------------------------------------

	/** Grid of selectable cards (presets, images). */
	public static final class CardGrid<T> extends Control {
		public interface CardPainter<T> {
			void paint(GuiGraphicsExtractor g, T item, int x, int y, int w, int h, float hover, boolean selected);
		}

		private final Supplier<List<T>> items;
		private final Predicate<T> selected;
		private final Consumer<T> pick;
		private final CardPainter<T> painter;
		private final int cardH;
		private final int minW;
		private float[] hovers = new float[0];

		public interface Predicate<T> {
			boolean test(T t);
		}

		public CardGrid(Supplier<List<T>> items, Predicate<T> selected, Consumer<T> pick, CardPainter<T> painter, int cardH, int minW) {
			this.items = items;
			this.selected = selected;
			this.pick = pick;
			this.painter = painter;
			this.cardH = cardH;
			this.minW = minW;
		}

		private int cols() {
			return Math.max(1, (w + 6) / (minW + 6));
		}

		@Override
		public int height() {
			int n = items.get().size();
			int rows = (n + cols() - 1) / cols();
			return rows * (cardH + 6);
		}

		@Override
		public void draw(GuiGraphicsExtractor g, int mx, int my, float dt) {
			List<T> list = items.get();
			if (hovers.length != list.size()) hovers = new float[list.size()];
			int cols = cols();
			int cw = (w - 6 * (cols - 1)) / cols;
			for (int i = 0; i < list.size(); i++) {
				int cx = x + (i % cols) * (cw + 6);
				int cy = y + (i / cols) * (cardH + 6);
				boolean over = mx >= cx && mx < cx + cw && my >= cy && my < cy + cardH;
				hovers[i] = Ui.approach(hovers[i], over ? 1f : 0f, dt, 14f);
				painter.paint(g, list.get(i), cx, cy, cw, cardH, hovers[i], selected.test(list.get(i)));
			}
		}

		@Override
		public boolean mouseDown(double mx, double my, int button) {
			if (button != InputConstants.MOUSE_BUTTON_LEFT || !contains(mx, my)) return false;
			List<T> list = items.get();
			int cols = cols();
			int cw = (w - 6 * (cols - 1)) / cols;
			for (int i = 0; i < list.size(); i++) {
				int cx = x + (i % cols) * (cw + 6);
				int cy = y + (i / cols) * (cardH + 6);
				if (mx >= cx && mx < cx + cw && my >= cy && my < cy + cardH) {
					pick.accept(list.get(i));
					return true;
				}
			}
			return false;
		}
	}

	/** Grid of special characters; clicking one inserts it. */
	public static final class SymbolGrid extends Control {
		private final String[] symbols;
		private final Consumer<String> insert;
		private float[] hovers;
		private int flashIndex = -1;
		private float flash;

		public SymbolGrid(String[] symbols, Consumer<String> insert) {
			this.symbols = symbols;
			this.insert = insert;
			this.hovers = new float[symbols.length];
		}

		private int cols() {
			return Math.max(1, (w + 3) / 19);
		}

		@Override
		public int height() {
			int rows = (symbols.length + cols() - 1) / cols();
			return rows * 19;
		}

		@Override
		public void draw(GuiGraphicsExtractor g, int mx, int my, float dt) {
			int cols = cols();
			flash = Ui.approach(flash, 0f, dt, 5f);
			for (int i = 0; i < symbols.length; i++) {
				int cx = x + (i % cols) * 19;
				int cy = y + (i / cols) * 19;
				boolean over = mx >= cx && mx < cx + 16 && my >= cy && my < cy + 16;
				hovers[i] = Ui.approach(hovers[i], over ? 1f : 0f, dt, 16f);
				int bg = Ui.mix(Ui.BG_FIELD, Ui.BG_FIELD_HOVER, hovers[i]);
				if (i == flashIndex) bg = Ui.mix(bg, Ui.ACCENT, flash * 0.6f);
				Ui.round(g, cx, cy, cx + 16, cy + 16, 3, bg);
				Ui.textCentered(g, symbols[i], cx + 8, cy + 4, Ui.mix(Ui.TEXT_MUTED, Ui.ACCENT, hovers[i]), Ui.FONT);
			}
		}

		@Override
		public boolean mouseDown(double mx, double my, int button) {
			if (button != InputConstants.MOUSE_BUTTON_LEFT || !contains(mx, my)) return false;
			int cols = cols();
			int col = (int) ((mx - x) / 19);
			int row = (int) ((my - y) / 19);
			int i = row * cols + col;
			if (col < cols && i >= 0 && i < symbols.length) {
				insert.accept(symbols[i]);
				flashIndex = i;
				flash = 1f;
				return true;
			}
			return false;
		}
	}

	/** Draws a texture scaled into a square (used for emblem thumbnails). */
	public static void image(GuiGraphicsExtractor g, Identifier tex, int x, int y, int size, int argb) {
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0, 0, size, size, size, size, Ui.a(argb));
	}

	static float[] rgbToHsv(int rgb) {
		double r = ((rgb >> 16) & 0xFF) / 255.0;
		double gg = ((rgb >> 8) & 0xFF) / 255.0;
		double b = (rgb & 0xFF) / 255.0;
		double max = Math.max(r, Math.max(gg, b));
		double min = Math.min(r, Math.min(gg, b));
		double d = max - min;
		double h;
		if (d < 1e-9) h = 0;
		else if (max == r) h = ((gg - b) / d) / 6.0;
		else if (max == gg) h = ((b - r) / d + 2) / 6.0;
		else h = ((r - gg) / d + 4) / 6.0;
		h = h - Math.floor(h);
		return new float[] {(float) h, (float) (max <= 0 ? 0 : d / max), (float) max};
	}
}
