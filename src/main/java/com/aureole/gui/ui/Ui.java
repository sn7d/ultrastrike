package com.aureole.gui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import com.aureole.Aureole;

/** Theme, typography and drawing primitives for the custom settings UI. */
public final class Ui {
	private Ui() { }

	// palette
	public static final int BG_WINDOW = 0xF20E0F15;
	public static final int BG_SIDEBAR = 0xFF0A0B10;
	public static final int BG_FIELD = 0xFF181A23;
	public static final int BG_FIELD_HOVER = 0xFF20232F;
	public static final int LINE = 0x1FFFFFFF;
	public static final int TEXT = 0xFFECECF4;
	public static final int TEXT_MUTED = 0xFF8D90A2;
	public static final int TEXT_DIM = 0xFF5D6072;
	public static final int ACCENT = 0xFFFFD27A;
	public static final int ACCENT_2 = 0xFFA58BFF;
	public static final int SUCCESS = 0xFF7CF0A8;
	public static final int DANGER = 0xFFFF6B7A;

	public static final FontDescription FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath(Aureole.MOD_ID, "ui"));
	public static final FontDescription FONT_BOLD = new FontDescription.Resource(Identifier.fromNamespaceAndPath(Aureole.MOD_ID, "ui_bold"));
	public static final FontDescription FONT_TITLE = new FontDescription.Resource(Identifier.fromNamespaceAndPath(Aureole.MOD_ID, "ui_title"));

	/** Global opacity used for the open/close animation. */
	public static float alpha = 1f;

	public static Font font() {
		return Minecraft.getInstance().font;
	}

	public static MutableComponent styled(String s, FontDescription f) {
		return Component.literal(s).withStyle(st -> st.withFont(f));
	}

	public static MutableComponent styled(Component c, FontDescription f) {
		return c.copy().withStyle(st -> st.withFont(f));
	}

	public static int width(String s) {
		return font().width(styled(s, FONT));
	}

	public static int width(String s, FontDescription f) {
		return font().width(styled(s, f));
	}

	public static void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
		g.text(font(), styled(s, FONT), x, y, a(color), false);
	}

	public static void text(GuiGraphicsExtractor g, String s, int x, int y, int color, FontDescription f) {
		g.text(font(), styled(s, f), x, y, a(color), false);
	}

	public static void textRight(GuiGraphicsExtractor g, String s, int right, int y, int color) {
		text(g, s, right - width(s), y, color);
	}

	public static void textCentered(GuiGraphicsExtractor g, String s, int cx, int y, int color, FontDescription f) {
		text(g, s, cx - width(s, f) / 2, y, color, f);
	}

	/** Shortens a string with an ellipsis to fit a width. */
	public static String fit(String s, int maxW) {
		if (width(s) <= maxW) return s;
		String e = "…";
		int end = s.length();
		while (end > 0 && width(s.substring(0, end) + e) > maxW) end--;
		return s.substring(0, Math.max(0, end)) + e;
	}

	/** Applies the global alpha to an ARGB colour. */
	public static int a(int argb) {
		if (alpha >= 0.999f) return argb;
		int al = (int) (((argb >>> 24) & 0xFF) * alpha);
		return (al << 24) | (argb & 0xFFFFFF);
	}

	public static int withAlpha(int argb, float mul) {
		int al = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, mul)));
		return (al << 24) | (argb & 0xFFFFFF);
	}

	public static int mix(int c1, int c2, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int a = Math.round(((c1 >>> 24) & 0xFF) + (((c2 >>> 24) & 0xFF) - ((c1 >>> 24) & 0xFF)) * t);
		int r = Math.round(((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
		int gg = Math.round(((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
		int b = Math.round((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
		return (a << 24) | (r << 16) | (gg << 8) | b;
	}

	public static void fill(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
		if (x1 <= x0 || y1 <= y0) return;
		g.fill(x0, y0, x1, y1, a(color));
	}

	/** Rounded rectangle approximated with horizontal spans (crisp at GUI scale). */
	public static void round(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int r, int color) {
		int w = x1 - x0;
		int h = y1 - y0;
		if (w <= 0 || h <= 0) return;
		r = Math.min(r, Math.min(w, h) / 2);
		if (r <= 0) {
			fill(g, x0, y0, x1, y1, color);
			return;
		}

		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
			fill(g, x0 + inset, y0 + i, x1 - inset, y0 + i + 1, color);
			fill(g, x0 + inset, y1 - i - 1, x1 - inset, y1 - i, color);
		}

		fill(g, x0, y0 + r, x1, y1 - r, color);
	}

	/** Rounded outline, 1px. */
	public static void roundOutline(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int r, int color) {
		fill(g, x0 + r, y0, x1 - r, y0 + 1, color);
		fill(g, x0 + r, y1 - 1, x1 - r, y1, color);
		fill(g, x0, y0 + r, x0 + 1, y1 - r, color);
		fill(g, x1 - 1, y0 + r, x1, y1 - r, color);

		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
			int next = i + 1 < r ? (int) Math.round(r - Math.sqrt(Math.max(0, r * r - (r - i - 1.5) * (r - i - 1.5)))) : 0;
			int span = Math.max(1, inset - next);
			fill(g, x0 + inset - span + 1, y0 + i, x0 + inset + 1, y0 + i + 1, color);
			fill(g, x1 - inset - 1, y0 + i, x1 - inset + span - 1, y0 + i + 1, color);
			fill(g, x0 + inset - span + 1, y1 - i - 1, x0 + inset + 1, y1 - i, color);
			fill(g, x1 - inset - 1, y1 - i - 1, x1 - inset + span - 1, y1 - i, color);
		}
	}

	public static void gradientV(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int top, int bottom) {
		if (x1 <= x0 || y1 <= y0) return;
		g.fillGradient(x0, y0, x1, y1, a(top), a(bottom));
	}

	public static void gradientH(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int left, int right) {
		int w = x1 - x0;
		if (w <= 0) return;
		int steps = Math.min(w, 48);
		for (int i = 0; i < steps; i++) {
			int a0 = x0 + w * i / steps;
			int a1 = x0 + w * (i + 1) / steps;
			fill(g, a0, y0, a1, y1, mix(left, right, (i + 0.5f) / steps));
		}
	}

	/** Frame-rate independent exponential approach. */
	public static float approach(float current, float target, float dt, float rate) {
		return current + (target - current) * (1f - (float) Math.exp(-rate * dt));
	}

	public static float easeOutCubic(float t) {
		t = Math.max(0f, Math.min(1f, t));
		float q = 1f - t;
		return 1f - q * q * q;
	}

	public static float easeOutBack(float t) {
		t = Math.max(0f, Math.min(1f, t));
		float c1 = 1.70158f;
		float c3 = c1 + 1;
		return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
	}
}
