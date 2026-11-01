package com.angelichalo.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import com.angelichalo.config.ColorSpec;
import com.angelichalo.config.HaloConfig;
import com.angelichalo.config.HaloOptions;
import com.angelichalo.config.HaloPresets;
import com.angelichalo.config.PresetLibrary;
import com.angelichalo.gui.ui.Controls;
import com.angelichalo.gui.ui.Controls.Control;
import com.angelichalo.gui.ui.Ui;
import com.angelichalo.render.HaloColors;
import com.angelichalo.render.HaloImages;
import com.angelichalo.render.HaloState;

/**
 * Custom-drawn, animated settings window with a live 3D effect projector on the right.
 * Every change applies instantly; Save keeps it, Cancel restores what you had when you opened it.
 */
public class HaloConfigScreen extends Screen {
	private enum Tab {
		PRESETS("Presets", "One-click looks"),
		RING("Ring", "Shape and style of the ring"),
		MOTION("Motion", "How the ring moves"),
		TRAIL("Scan Trail", "The fading trail behind the scan"),
		COLOURS("Colours", "Colour studio for every layer"),
		GLOW("Glow", "Soft bloom around every effect"),
		TEXT("Text", "Your own words around the ring"),
		EMBLEM("Emblem", "Project an image under or above you"),
		NAMETAG("Nametag", "Your own name above your head"),
		EXTRAS("Extras", "Particles and reactive effects"),
		GENERAL("General", "Visibility and behaviour"),
		SHARE("Share", "Codes, saved looks and import/export");

		final String title;
		final String subtitle;

		Tab(String title, String subtitle) {
			this.title = title;
			this.subtitle = subtitle;
		}
	}

	private static final String[] SYMBOLS = {
			"★", "☆", "✦", "✧", "✪", "✯", "✰", "⭑", "❖", "◆", "◇", "♦", "●", "○", "◉", "♛", "♚", "♔", "♕", "⚔",
			"⚒", "⛏", "⚚", "❤", "♥", "♡", "❣", "☀", "☼", "☾", "☽", "☁", "⚡", "❄", "☃", "✿", "❀", "❁", "☘", "♣",
			"♠", "♪", "♫", "☯", "☠", "⚜", "✞", "✝", "☩", "∞", "✓", "✔", "✗", "✘", "➤", "➜", "»", "«", "⟡", "✺",
			"❂", "✹", "✵", "⁂", "※", "†", "‡", "§", "¶", "™", "©", "®", "✎", "✉", "⌛", "⚑", "⚐", "☂", "☕", "⚓",
			"♨", "⚛", "☢", "☣", "Ω", "Σ", "λ", "π", "µ", "α", "β", "δ", "∆", "√", "≈", "≠", "≤", "≥", "÷", "×",
			"⇧", "⇩", "⇦", "⇨", "↑", "↓", "←", "→", "▲", "▼", "◀", "▶", "■", "□", "▪", "▫", "░", "▒", "▓", "█"
	};

	private static final int[] PALETTE = {
			0xFF4B55, 0xFF2A00, 0xFF8A00, 0xFFD54F, 0xFFF4D6, 0x7CFF2B, 0x00FF88, 0x3DFFB5,
			0x38E8FF, 0x2F7BFF, 0x7C4DFF, 0xC77DFF, 0xFF9EC7, 0xFF00AA, 0xFFFFFF, 0x101010
	};

	private static Tab lastTab = Tab.PRESETS;
	private static int lastColorTarget = 0;

	private final @Nullable Screen parent;
	private final HaloConfig backup;
	private final HaloProjector projector = new HaloProjector();
	private Tab tab = lastTab;
	private int colorTarget = lastColorTarget; // 0 ring, 1 trail, 2 extras, 3 text, 4 emblem, 5 name, 6 title, 7 icons
	private int symbolTarget; // 0 name, 1 title
	private List<Control> controls = new ArrayList<>();
	private final List<Control> footer = new ArrayList<>();
	private @Nullable Control active;

	// animation state
	private long lastFrame = System.nanoTime();
	private float openT;
	private float tabT = 1f;
	private float indicatorY = -1;
	private final float[] tabHover = new float[Tab.values().length];
	private float scroll;
	private float scrollTarget;
	private float tooltipT;
	private @Nullable Control tooltipFor;
	private String status = "";
	private long statusUntil;
	private String saveName = "";

	// layout
	private int wx0;
	private int wy0;
	private int wx1;
	private int wy1;
	private int sideW;
	private int cx0;
	private int cx1;
	private int contentTop;
	private int contentBottom;
	private int px0;
	private int contentHeight;

	/** Position of a control in the current tab as {x, y, w, h} in GUI pixels (used by the in-game tests). */
	public int[] debugControlRect(int index) {
		Control c = controls.get(index);
		return new int[] {c.x, c.y, c.w, c.height()};
	}

	/** Number of tabs (used by the in-game screenshot test). */
	public static int tabCount() {
		return Tab.values().length;
	}

	/** Choose which tab the next opened screen starts on. */
	public static void setStartTab(int index) {
		lastTab = Tab.values()[Math.floorMod(index, Tab.values().length)];
	}

	/** Choose which colour layer the Colours tab edits (0 ring, 1 trail, 2 extras, 3 text, 4 emblem). */
	public static void setStartColorTarget(int target) {
		lastColorTarget = Math.floorMod(target, 8);
	}

	public HaloConfigScreen(@Nullable Screen parent) {
		super(Component.translatable("angelichalo.gui.title"));
		this.parent = parent;
		this.backup = HaloConfig.get().copy();
	}

	private static HaloConfig cfg() {
		return HaloConfig.get();
	}

	private static String tr(String key) {
		return Component.translatable(key).getString();
	}

	private static String opt(String key) {
		return tr("angelichalo.option." + key);
	}

	private static String tip(String key) {
		return tr("angelichalo.option." + key + ".tip");
	}

	private static String enumName(Enum<?> e) {
		return tr("angelichalo.enum." + e.getDeclaringClass().getSimpleName().toLowerCase(Locale.ROOT) + "." + e.name().toLowerCase(Locale.ROOT));
	}

	// ------------------------------------------------------------------------------------------------
	// lifecycle

	@Override
	protected void init() {
		int ww = Math.min(width - 16, 700);
		int wh = Math.min(height - 16, 400);
		wx0 = (width - ww) / 2;
		wy0 = (height - wh) / 2;
		wx1 = wx0 + ww;
		wy1 = wy0 + wh;
		sideW = ww < 520 ? 92 : 112;
		boolean hasProjector = ww >= 430;
		int pw = hasProjector ? Math.max(150, Math.min(260, (int) (ww * 0.36))) : 0;
		px0 = wx1 - pw - 8;
		cx0 = wx0 + sideW + 14;
		cx1 = hasProjector ? px0 - 12 : wx1 - 12;
		contentTop = wy0 + 40;
		contentBottom = wy1 - 34;
		rebuild();
	}

	private void rebuild() {
		if (active != null) active.mouseUp();
		active = null;
		controls = build(tab);
		footer.clear();
		footer.add(new Controls.Button(() -> "Reset", () -> {
			HaloConfig.set(new HaloConfig());
			flash("Restored the classic look");
			rebuild();
		}, false).tip("Restore every setting to the default classic red ring."));
		footer.add(new Controls.Button(() -> "Cancel", () -> {
			HaloConfig.set(backup.copy());
			close();
		}, false));
		footer.add(new Controls.Button(() -> "Save", () -> {
			HaloConfig.save();
			close();
		}, true));
	}

	private void switchTab(Tab t) {
		if (t == tab) return;
		tab = t;
		lastTab = t;
		scroll = 0;
		scrollTarget = 0;
		tabT = 0f;
		rebuild();
	}

	@Override
	public void onClose() {
		HaloConfig.save();
		close();
	}

	private void close() {
		for (Control c : controls) c.unfocus();
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void flash(String message) {
		status = message;
		statusUntil = System.currentTimeMillis() + 2600;
	}

	// ------------------------------------------------------------------------------------------------
	// input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		int button = event.button();

		Tab[] tabs = Tab.values();
		for (int i = 0; i < tabs.length; i++) {
			int ty = tabY(i);
			if (mx >= wx0 + 6 && mx < wx0 + sideW - 6 && my >= ty && my < ty + 18) {
				switchTab(tabs[i]);
				return true;
			}
		}

		for (Control c : footer) {
			if (c.mouseDown(mx, my, button)) return true;
		}

		if (projector.mouseDown(mx, my)) return true;

		boolean handled = false;
		if (my >= contentTop && my < contentBottom && mx >= cx0 - 4 && mx < cx1 + 4) {
			for (Control c : controls) {
				if (!handled && c.mouseDown(mx, my, button)) {
					active = c;
					handled = true;
				} else if (c.focused() && !c.contains(mx, my)) {
					c.unfocus();
				}
			}
		} else {
			for (Control c : controls) c.unfocus();
		}

		return handled || super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		projector.mouseDrag(event.x(), event.y());
		if (active != null) active.mouseDrag(event.x(), event.y());
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		projector.mouseUp();
		if (active != null) active.mouseUp();
		active = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (projector.scroll(mx, my, sy)) return true;
		if (mx >= cx0 - 4 && mx < cx1 + 8 && my >= contentTop && my < contentBottom) {
			scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget - (float) sy * 24f));
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		for (Control c : controls) {
			if (c.focused() && c.keyPressed(event)) return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		for (Control c : controls) {
			if (c.focused() && c.charTyped(event.codepoint())) return true;
		}
		return super.charTyped(event);
	}

	private float maxScroll() {
		return Math.max(0, contentHeight - (contentBottom - contentTop) + 8);
	}

	private int tabY(int i) {
		int step = Math.max(17, Math.min(21, (wy1 - wy0 - 44 - 40) / Tab.values().length));
		return wy0 + 44 + i * step;
	}

	// ------------------------------------------------------------------------------------------------
	// drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (minecraft.level != null) {
			extractBlurredBackground(g);
		} else {
			super.extractBackground(g, mouseX, mouseY, delta);
		}
		float a = Ui.easeOutCubic(openT);
		g.fillGradient(0, 0, width, height, Ui.withAlpha(0xA0050608, a), Ui.withAlpha(0xD0050608, a));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;
		openT = Math.min(1f, openT + dt / 0.28f);
		tabT = Math.min(1f, tabT + dt / 0.22f);
		scroll = Ui.approach(scroll, scrollTarget, dt, 18f);

		super.extractRenderState(g, mouseX, mouseY, delta);

		float open = Ui.easeOutCubic(openT);
		Ui.alpha = open;
		g.pose().pushMatrix();
		g.pose().translate(0, (1f - open) * 14f);

		drawWindow(g, mouseX, mouseY, dt);

		g.pose().popMatrix();
		Ui.alpha = 1f;
	}

	private void drawWindow(GuiGraphicsExtractor g, int mx, int my, float dt) {
		Ui.round(g, wx0 - 3, wy0 - 1, wx1 + 3, wy1 + 5, 10, 0x40000000);
		Ui.round(g, wx0, wy0, wx1, wy1, 8, Ui.BG_WINDOW);
		Ui.round(g, wx0, wy0, wx0 + sideW, wy1, 8, Ui.BG_SIDEBAR);
		Ui.fill(g, wx0 + sideW - 8, wy0, wx0 + sideW, wy1, Ui.BG_SIDEBAR);
		Ui.fill(g, wx0 + sideW, wy0 + 8, wx0 + sideW + 1, wy1 - 8, Ui.LINE);
		Ui.gradientH(g, wx0 + 8, wy0, (wx0 + wx1) / 2, wy0 + 1, Ui.withAlpha(Ui.ACCENT, 0.0f), Ui.ACCENT);
		Ui.gradientH(g, (wx0 + wx1) / 2, wy0, wx1 - 8, wy0 + 1, Ui.ACCENT, Ui.withAlpha(Ui.ACCENT_2, 0.0f));
		Ui.roundOutline(g, wx0, wy0, wx1, wy1, 8, 0x1CFFFFFF);

		drawSidebar(g, mx, my, dt);
		drawContent(g, mx, my, dt);

		if (px0 < wx1 - 40 && cx1 < px0) {
			projector.setFocusHead(tab == Tab.NAMETAG);
			projector.draw(g, px0, wy0 + 12, wx1 - 10, wy1 - 12, dt, mx, my);
		}

		drawTooltip(g, mx, my, dt);
	}

	private void drawSidebar(GuiGraphicsExtractor g, int mx, int my, float dt) {
		int bx = wx0 + 16;
		int by = wy0 + 19;
		double t = HaloState.now();
		for (int i = 0; i < 28; i++) {
			double a = i / 28.0 * Math.PI * 2 + t * 0.8;
			int px = bx + (int) Math.round(Math.cos(a) * 7);
			int py = by + (int) Math.round(Math.sin(a) * 2.6);
			Ui.fill(g, px, py, px + 1, py + 1, HaloColors.hsv(0.11 + 0.05 * Math.sin(a), 0.55, 1.0) | 0xFF000000);
		}
		Ui.text(g, "Angelic Halo", wx0 + 28, wy0 + 12, Ui.TEXT, Ui.FONT_TITLE);
		Ui.text(g, "v1.0 · client-side", wx0 + 29, wy0 + 26, Ui.TEXT_DIM);

		Tab[] tabs = Tab.values();
		int sel = tab.ordinal();
		float targetY = tabY(sel);
		indicatorY = indicatorY < 0 ? targetY : Ui.approach(indicatorY, targetY, dt, 16f);
		int iy = Math.round(indicatorY);
		Ui.round(g, wx0 + 6, iy, wx0 + sideW - 6, iy + 18, 5, 0x1AFFD27A);
		Ui.round(g, wx0 + 6, iy + 4, wx0 + 8, iy + 14, 1, Ui.ACCENT);

		for (int i = 0; i < tabs.length; i++) {
			int ty = tabY(i);
			boolean over = mx >= wx0 + 6 && mx < wx0 + sideW - 6 && my >= ty && my < ty + 18;
			tabHover[i] = Ui.approach(tabHover[i], over ? 1f : 0f, dt, 14f);
			if (i != sel && tabHover[i] > 0.01f) {
				Ui.round(g, wx0 + 6, ty, wx0 + sideW - 6, ty + 18, 5, Ui.withAlpha(0x12FFFFFF, tabHover[i]));
			}
			int col = i == sel ? Ui.ACCENT : Ui.mix(Ui.TEXT_MUTED, Ui.TEXT, tabHover[i]);
			int shift = Math.round(tabHover[i] * 2);
			Ui.text(g, tabs[i].title, wx0 + 15 + shift, ty + 5, col, i == sel ? Ui.FONT_BOLD : Ui.FONT);
		}

		if (System.currentTimeMillis() < statusUntil) {
			float k = Math.min(1f, (statusUntil - System.currentTimeMillis()) / 400f);
			Ui.text(g, Ui.fit(status, sideW - 16), wx0 + 10, wy1 - 32, Ui.withAlpha(Ui.SUCCESS, k));
		}
		Ui.text(g, "Esc saves & closes", wx0 + 10, wy1 - 16, Ui.TEXT_DIM);
	}

	private void drawContent(GuiGraphicsExtractor g, int mx, int my, float dt) {
		float te = Ui.easeOutCubic(tabT);
		float baseAlpha = Ui.alpha;

		Ui.text(g, tab.title, cx0, wy0 + 11, Ui.TEXT, Ui.FONT_TITLE);
		Ui.text(g, tab.subtitle, cx0, wy0 + 25, Ui.TEXT_MUTED);

		int w = cx1 - cx0;
		int y = contentTop - Math.round(scroll);
		int total = 0;
		g.enableScissor(cx0 - 6, contentTop - 2, cx1 + 6, contentBottom);
		Ui.alpha = baseAlpha * te;
		int slide = Math.round((1f - te) * 10f);
		for (Control c : controls) {
			c.x = cx0 + slide;
			c.y = y;
			c.w = w;
			int h = c.height();
			if (y + h >= contentTop - 4 && y <= contentBottom + 4) {
				c.draw(g, mx, my, dt);
			}
			y += h + 5;
			total += h + 5;
		}
		g.disableScissor();
		Ui.alpha = baseAlpha;
		contentHeight = total;
		scrollTarget = Math.min(scrollTarget, maxScroll());

		Ui.gradientV(g, cx0 - 6, contentTop - 2, cx1 + 6, contentTop + 6, Ui.BG_WINDOW, 0x000E0F15);
		Ui.gradientV(g, cx0 - 6, contentBottom - 8, cx1 + 6, contentBottom, 0x000E0F15, Ui.BG_WINDOW);

		float max = maxScroll();
		if (max > 0) {
			int trackH = contentBottom - contentTop;
			int thumb = Math.max(18, (int) (trackH * trackH / (float) (trackH + max)));
			int ty = contentTop + Math.round((trackH - thumb) * (scroll / max));
			Ui.round(g, cx1 + 6, ty, cx1 + 8, ty + thumb, 1, 0x50FFD27A);
		}

		int fw = 64;
		int fy = wy1 - 26;
		footer.get(0).x = cx0;
		footer.get(0).y = fy;
		footer.get(0).w = 56;
		footer.get(1).x = cx1 - fw * 2 - 6;
		footer.get(1).y = fy;
		footer.get(1).w = fw;
		footer.get(2).x = cx1 - fw;
		footer.get(2).y = fy;
		footer.get(2).w = fw;
		for (Control c : footer) c.draw(g, mx, my, dt);
	}

	private void drawTooltip(GuiGraphicsExtractor g, int mx, int my, float dt) {
		Control over = null;
		if (my >= contentTop && my < contentBottom) {
			for (Control c : controls) {
				if (c.contains(mx, my)) {
					over = c instanceof Controls.Row row ? row.hovered(mx, my) : c;
					break;
				}
			}
		}
		for (Control c : footer) if (c.contains(mx, my)) over = c;

		if (over != tooltipFor) {
			tooltipFor = over;
			tooltipT = 0;
		}
		if (over == null || over.tooltip == null || over.tooltip.isBlank() || active != null) return;
		tooltipT += dt;
		float a = Math.min(1f, (tooltipT - 0.45f) / 0.15f);
		if (a <= 0) return;

		List<String> lines = wrap(over.tooltip, 180);
		int tw = 0;
		for (String l : lines) tw = Math.max(tw, Ui.width(l));
		int th = lines.size() * 11 + 8;
		int tx = Math.min(mx + 10, width - tw - 14);
		int ty = Math.min(my + 12, height - th - 6);
		float base = Ui.alpha;
		Ui.alpha = base * a;
		Ui.round(g, tx, ty, tx + tw + 12, ty + th, 5, 0xF2181A24);
		Ui.roundOutline(g, tx, ty, tx + tw + 12, ty + th, 5, 0x40FFD27A);
		for (int i = 0; i < lines.size(); i++) Ui.text(g, lines.get(i), tx + 6, ty + 5 + i * 11, Ui.TEXT);
		Ui.alpha = base;
	}

	private static List<String> wrap(String text, int maxW) {
		List<String> out = new ArrayList<>();
		for (String para : text.split("\n")) {
			StringBuilder line = new StringBuilder();
			for (String word : para.split(" ")) {
				String trial = line.isEmpty() ? word : line + " " + word;
				if (Ui.width(trial) > maxW && !line.isEmpty()) {
					out.add(line.toString());
					line = new StringBuilder(word);
				} else {
					line = new StringBuilder(trial);
				}
			}
			out.add(line.toString());
		}
		return out;
	}

	// ------------------------------------------------------------------------------------------------
	// control factories

	private Control slider(String key, double min, double max, double step, java.util.function.DoubleSupplier get,
			java.util.function.DoubleConsumer set, String fmt) {
		return new Controls.Slider(opt(key), min, max, step, get, set, v -> String.format(Locale.ROOT, fmt, v)).tip(tip(key));
	}

	private Control intSlider(String key, int min, int max, java.util.function.IntSupplier get, java.util.function.IntConsumer set) {
		return slider(key, min, max, 1, get::getAsInt, v -> set.accept((int) Math.round(v)), "%.0f");
	}

	private Control toggle(String key, java.util.function.BooleanSupplier get, Consumer<Boolean> set) {
		return new Controls.Toggle(opt(key), get, set).tip(tip(key));
	}

	private <E extends Enum<E>> Control choice(String key, E[] values, Supplier<E> get, Consumer<E> set, boolean rebuild) {
		return new Controls.Choice<E>(opt(key), Arrays.asList(values), get, v -> {
			set.accept(v);
			if (rebuild) minecraft.execute(this::rebuild);
		}, HaloConfigScreen::enumName).tip(tip(key));
	}

	private Control button(String label, Runnable action) {
		return new Controls.Button(() -> label, action, false);
	}

	private Control header(String title) {
		return new Controls.Header(title, null);
	}

	private Control note(String text) {
		return new Controls.Note(() -> text, Ui.TEXT_MUTED);
	}

	/** Icon grid with a "None" card first. */
	private Control iconPicker(Supplier<String> get, Consumer<String> set) {
		return new Controls.CardGrid<String>(() -> {
			List<String> items = new ArrayList<>();
			items.add("");
			items.addAll(HaloImages.ICONS.list());
			return items;
		}, k -> k.equals(get.get()), set,
				(g, k, x, y, w, h, hover, sel) -> {
					Ui.round(g, x, y, x + w, y + h, 5, Ui.mix(Ui.BG_FIELD, Ui.BG_FIELD_HOVER, Math.max(hover, sel ? 1 : 0)));
					if (k.isEmpty()) {
						Ui.textCentered(g, "None", x + w / 2, y + h / 2 - 4, sel ? Ui.ACCENT : Ui.TEXT_MUTED, Ui.FONT);
					} else {
						int s = h - 8;
						Controls.image(g, HaloImages.ICONS.textureFor(k), x + (w - s) / 2, y + 4, s,
								sel ? 0xFFFFD27A : Ui.mix(0xFFB8BBC8, 0xFFFFFFFF, hover));
					}
					if (sel) Ui.roundOutline(g, x, y, x + w, y + h, 5, Ui.withAlpha(Ui.ACCENT, 0.7f));
				}, 26, 26);
	}

	private ColorSpec spec() {
		HaloConfig c = cfg();
		return switch (colorTarget) {
			case 1 -> c.trailColor;
			case 2 -> c.extrasColor;
			case 3 -> c.textColor;
			case 4 -> c.emblemColor;
			case 5 -> c.tagColor;
			case 6 -> c.tagTitleColor;
			case 7 -> c.tagIconColor;
			default -> c.ringColor;
		};
	}

	private void colorStudio(List<Control> e, Supplier<ColorSpec> spec) {
		e.add(choice("color_mode", HaloOptions.ColorMode.values(), () -> spec.get().mode, v -> spec.get().mode = v, false));
		e.add(new Controls.ColorRow(opt("primary"), () -> spec.get().primary, v -> spec.get().primary = v));
		e.add(new Controls.ColorRow(opt("secondary"), () -> spec.get().secondary, v -> spec.get().secondary = v));
		e.add(new Controls.Palette(PALETTE, v -> spec.get().primary = v, v -> spec.get().secondary = v));
		e.add(new Controls.Strip(tr("angelichalo.gui.preview"),
				i -> HaloColors.color(spec.get(), i / 1000.0, i / 1000.0, HaloState.now(), 0.5 + 0.5 * Math.sin(HaloState.now()), 1.0)));
		e.add(slider("opacity", 0.05, 1, 0.01, () -> spec.get().opacity, v -> spec.get().opacity = v, "%.2f"));
		e.add(slider("color_speed", 0, 4, 0.01, () -> spec.get().speed, v -> spec.get().speed = v, "%.2f"));
		e.add(intSlider("color_repeats", 1, 12, () -> spec.get().repeats, v -> spec.get().repeats = v));
		e.add(slider("saturation", 0, 1, 0.01, () -> spec.get().saturation, v -> spec.get().saturation = v, "%.2f"));
		e.add(slider("brightness", 0.1, 1, 0.01, () -> spec.get().brightness, v -> spec.get().brightness = v, "%.2f"));
		e.add(new Controls.Row(
				button(tr("angelichalo.gui.swap_colors"), () -> {
					ColorSpec s = spec.get();
					int p = s.primary;
					s.primary = s.secondary;
					s.secondary = p;
				}),
				button(tr("angelichalo.gui.random_colors"), () -> {
					ColorSpec s = spec.get();
					double h = Math.random();
					s.primary = HaloColors.hsv(h, 0.75, 1.0);
					s.secondary = HaloColors.hsv(h + 0.3 + Math.random() * 0.35, 0.75, 1.0);
				})));
	}

	// ------------------------------------------------------------------------------------------------
	// tabs

	private List<Control> build(Tab t) {
		List<Control> e = new ArrayList<>();

		switch (t) {
			case PRESETS -> {
				e.add(new Controls.CardGrid<HaloPresets.Preset>(() -> HaloPresets.ALL, p -> false, p -> {
					HaloConfig.set(p.build(cfg()));
					flash("Applied " + tr("angelichalo.preset." + p.id()));
				}, (g, p, x, y, w, h, hover, sel) -> {
					int lift = Math.round(hover);
					Ui.round(g, x, y - lift, x + w, y + h - lift, 6, Ui.mix(Ui.BG_FIELD, Ui.BG_FIELD_HOVER, hover));
					int sw = p.swatch();
					Ui.gradientH(g, x + 6, y + 5 - lift, x + w - 6, y + 7 - lift, 0xFF000000 | sw, 0x00000000 | sw);
					Ui.text(g, Ui.fit(tr("angelichalo.preset." + p.id()), w - 12), x + 7, y + 11 - lift, Ui.TEXT, Ui.FONT_BOLD);
					Ui.text(g, Ui.fit(tr("angelichalo.preset." + p.id() + ".tip"), w - 12), x + 7, y + 23 - lift, Ui.TEXT_MUTED);
					if (hover > 0.01f) Ui.roundOutline(g, x, y - lift, x + w, y + h - lift, 6, Ui.withAlpha(0xFF000000 | sw, 0.6f * hover));
				}, 38, 130));
				e.add(note("Presets replace the look but keep your visibility settings. Fine-tune anything afterwards."));
			}
			case RING -> {
				HaloConfig c = cfg();
				e.add(choice("style", HaloOptions.Style.values(), () -> cfg().style, v -> cfg().style = v, true));
				e.add(slider("radius", 0.2, 16, 0.05, () -> cfg().radius, v -> cfg().radius = v, "%.2f"));
				e.add(slider("line_width", 0.5, 12, 0.25, () -> cfg().lineWidth, v -> cfg().lineWidth = v, "%.2f px"));
				e.add(slider("height_offset", -1, 3, 0.01, () -> cfg().heightOffset, v -> cfg().heightOffset = v, "%.2f"));
				e.add(slider("spin_speed", -720, 720, 5, () -> cfg().spinSpeed, v -> cfg().spinSpeed = v, "%.0f°/s"));
				e.add(slider("tilt", -60, 60, 1, () -> cfg().tilt, v -> cfg().tilt = v, "%.0f°"));
				e.add(intSlider("segments", 16, 360, () -> cfg().segments, v -> cfg().segments = v));
				e.add(intSlider("ring_count", 1, 8, () -> cfg().ringCount, v -> cfg().ringCount = v));
				e.add(slider("ring_spacing", 0.02, 1, 0.01, () -> cfg().ringSpacing, v -> cfg().ringSpacing = v, "%.2f"));
				e.add(header(enumName(c.style) + " options"));
				switch (c.style) {
					case GLOW_LINE, LIGHTNING -> {
						e.add(slider("glow_width", 2, 40, 0.5, () -> cfg().glowWidth, v -> cfg().glowWidth = v, "%.1f px"));
						e.add(slider("glow_strength", 0, 1, 0.01, () -> cfg().glowStrength, v -> cfg().glowStrength = v, "%.2f"));
						if (c.style == HaloOptions.Style.LIGHTNING) {
							e.add(slider("lightning_intensity", 0, 1, 0.01, () -> cfg().lightningIntensity, v -> cfg().lightningIntensity = v, "%.2f"));
						}
					}
					case AURA -> e.add(slider("aura_height", 0.05, 3, 0.05, () -> cfg().auraHeight, v -> cfg().auraHeight = v, "%.2f"));
					case DASHED -> {
						e.add(intSlider("dash_count", 2, 96, () -> cfg().dashCount, v -> cfg().dashCount = v));
						e.add(slider("dash_fill", 0.1, 0.9, 0.01, () -> cfg().dashFill, v -> cfg().dashFill = v, "%.2f"));
					}
					case ORBS -> {
						e.add(intSlider("orb_count", 1, 64, () -> cfg().orbCount, v -> cfg().orbCount = v));
						e.add(slider("orb_size", 0.02, 0.6, 0.01, () -> cfg().orbSize, v -> cfg().orbSize = v, "%.2f"));
						e.add(slider("glow_strength", 0, 1, 0.01, () -> cfg().glowStrength, v -> cfg().glowStrength = v, "%.2f"));
					}
					case SONAR -> e.add(slider("sonar_speed", 0.05, 3, 0.05, () -> cfg().sonarSpeed, v -> cfg().sonarSpeed = v, "%.2f/s"));
					case CROWN -> {
						e.add(intSlider("crown_spikes", 3, 32, () -> cfg().crownSpikes, v -> cfg().crownSpikes = v));
						e.add(slider("crown_height", 0.05, 1.5, 0.01, () -> cfg().crownHeight, v -> cfg().crownHeight = v, "%.2f"));
						e.add(slider("glow_strength", 0, 1, 0.01, () -> cfg().glowStrength, v -> cfg().glowStrength = v, "%.2f"));
					}
					case GALAXY -> {
						e.add(intSlider("galaxy_arms", 1, 8, () -> cfg().galaxyArms, v -> cfg().galaxyArms = v));
						e.add(slider("glow_strength", 0, 1, 0.01, () -> cfg().glowStrength, v -> cfg().glowStrength = v, "%.2f"));
					}
					default -> e.add(note("This style has no extra options. Try Glow Line, Crown or Galaxy."));
				}
			}
			case MOTION -> {
				HaloConfig c = cfg();
				e.add(choice("motion", HaloOptions.Motion.values(), () -> cfg().motion, v -> cfg().motion = v, true));
				switch (c.motion) {
					case SCAN -> {
						e.add(slider("scan_speed", 0.02, 4, 0.01, () -> cfg().scanSpeed, v -> cfg().scanSpeed = v, "%.2f/s"));
						e.add(choice("scan_easing", HaloOptions.Easing.values(), () -> cfg().scanEasing, v -> cfg().scanEasing = v, false));
						e.add(choice("scan_direction", HaloOptions.ScanDirection.values(), () -> cfg().scanDirection, v -> cfg().scanDirection = v, false));
						e.add(slider("scan_pause", 0, 3, 0.05, () -> cfg().scanPause, v -> cfg().scanPause = v, "%.2f s"));
						e.add(slider("scan_min", 0, 1.2, 0.01, () -> cfg().scanMin, v -> cfg().scanMin = v, "%.2f"));
						e.add(slider("scan_max", 0, 1.2, 0.01, () -> cfg().scanMax, v -> cfg().scanMax = v, "%.2f"));
						e.add(toggle("scan_contour", () -> cfg().scanContour, v -> cfg().scanContour = v));
						e.add(button("Scan trail settings →", () -> switchTab(Tab.TRAIL)));
					}
					case BREATHE -> {
						e.add(slider("breathe_amount", 0, 0.8, 0.01, () -> cfg().breatheAmount, v -> cfg().breatheAmount = v, "%.2f"));
						e.add(slider("breathe_speed", 0.05, 4, 0.05, () -> cfg().breatheSpeed, v -> cfg().breatheSpeed = v, "%.2f/s"));
					}
					case HALO -> {
						e.add(slider("halo_radius", 0.1, 2, 0.01, () -> cfg().haloRadius, v -> cfg().haloRadius = v, "%.2f"));
						e.add(slider("halo_height", 0, 2, 0.01, () -> cfg().haloHeight, v -> cfg().haloHeight = v, "%.2f"));
						e.add(slider("halo_bob", 0, 0.5, 0.01, () -> cfg().haloBob, v -> cfg().haloBob = v, "%.2f"));
					}
					case RIPPLE -> {
						e.add(slider("ripple_speed", 0.05, 4, 0.05, () -> cfg().rippleSpeed, v -> cfg().rippleSpeed = v, "%.2f/s"));
						e.add(intSlider("ripple_count", 1, 8, () -> cfg().rippleCount, v -> cfg().rippleCount = v));
					}
					case WAVE -> {
						e.add(slider("wave_amplitude", 0, 1, 0.01, () -> cfg().waveAmplitude, v -> cfg().waveAmplitude = v, "%.2f"));
						e.add(intSlider("wave_frequency", 1, 16, () -> cfg().waveFrequency, v -> cfg().waveFrequency = v));
						e.add(slider("wave_speed", -6, 6, 0.1, () -> cfg().waveSpeed, v -> cfg().waveSpeed = v, "%.1f/s"));
					}
					case GYRO -> {
						e.add(slider("gyro_angle", 0, 60, 1, () -> cfg().gyroAngle, v -> cfg().gyroAngle = v, "%.0f°"));
						e.add(slider("gyro_speed", 0.05, 4, 0.05, () -> cfg().gyroSpeed, v -> cfg().gyroSpeed = v, "%.2f/s"));
					}
					case HEARTBEAT -> {
						e.add(slider("heart_rate", 30, 200, 1, () -> cfg().heartRate, v -> cfg().heartRate = v, "%.0f bpm"));
						e.add(slider("breathe_amount", 0, 0.8, 0.01, () -> cfg().breatheAmount, v -> cfg().breatheAmount = v, "%.2f"));
					}
					default -> e.add(note("The ring stays where you put it. Try Scan, Halo, Ripple, Gyro or Heartbeat."));
				}
			}
			case TRAIL -> {
				if (cfg().motion != HaloOptions.Motion.SCAN) {
					e.add(new Controls.Note(() -> "The trail follows the Scan motion.", Ui.ACCENT));
					e.add(button("Switch motion to Scan", () -> {
						cfg().motion = HaloOptions.Motion.SCAN;
						rebuild();
					}));
				}
				e.add(toggle("trail_enabled", () -> cfg().trailEnabled, v -> cfg().trailEnabled = v));
				e.add(choice("trail_style", HaloOptions.TrailStyle.values(), () -> cfg().trailStyle, v -> cfg().trailStyle = v, false));
				e.add(slider("trail_length", 0.05, 2, 0.01, () -> cfg().trailLength, v -> cfg().trailLength = v, "%.2f s"));
				e.add(intSlider("trail_echoes", 1, 12, () -> cfg().trailEchoes, v -> cfg().trailEchoes = v));
				e.add(toggle("trail_match_ring", () -> cfg().trailMatchRing, v -> {
					cfg().trailMatchRing = v;
					minecraft.execute(this::rebuild);
				}));
				if (!cfg().trailMatchRing) {
					e.add(header("Trail colours"));
					colorStudio(e, () -> cfg().trailColor);
				}
			}
			case COLOURS -> {
				List<Integer> targets = List.of(0, 1, 2, 3, 4, 5, 6, 7);
				String[] names = {"Ring", "Scan trail", "Extras", "Text", "Emblem", "Nametag", "Nametag title", "Nametag icons"};
				e.add(new Controls.Choice<Integer>("Editing", targets, () -> colorTarget, v -> {
					colorTarget = v;
					lastColorTarget = v;
				}, v -> names[v]).tip("Every layer has its own full colour studio."));
				colorStudio(e, this::spec);
			}
			case GLOW -> {
				e.add(toggle("bloom", () -> cfg().bloom, v -> cfg().bloom = v));
				e.add(slider("bloom_strength", 0, 1.5, 0.01, () -> cfg().bloomStrength, v -> cfg().bloomStrength = v, "%.2f"));
				e.add(slider("bloom_radius", 4, 48, 0.5, () -> cfg().bloomRadius, v -> cfg().bloomRadius = v, "%.1f px"));
				e.add(header("Per layer"));
				e.add(slider("ring_bloom", 0, 2, 0.01, () -> cfg().ringBloom, v -> cfg().ringBloom = v, "%.2f×"));
				e.add(slider("trail_bloom", 0, 2, 0.01, () -> cfg().trailBloom, v -> cfg().trailBloom = v, "%.2f×"));
				e.add(slider("text_bloom", 0, 2, 0.01, () -> cfg().textBloom, v -> cfg().textBloom = v, "%.2f×"));
				e.add(slider("emblem_bloom", 0, 2, 0.01, () -> cfg().emblemBloom, v -> cfg().emblemBloom = v, "%.2f×"));
				e.add(slider("extras_bloom", 0, 2, 0.01, () -> cfg().extrasBloom, v -> cfg().extrasBloom = v, "%.2f×"));
				e.add(header("With shaderpacks"));
				e.add(toggle("bloom_with_shaders", () -> cfg().bloomWithShaders, v -> cfg().bloomWithShaders = v));
				e.add(slider("shader_color_boost", 1, 2.5, 0.05, () -> cfg().shaderColorBoost, v -> cfg().shaderColorBoost = v, "%.2f×"));
				e.add(note("Bloom is additive light, so it shines brightest at night and over dark blocks. The Classic preset keeps it off so it matches the reference exactly. With a shaderpack on, the pack's own bloom takes over unless you enable the toggle above."));
			}
			case TEXT -> {
				e.add(toggle("text_enabled", () -> cfg().textEnabled, v -> cfg().textEnabled = v));
				e.add(new Controls.TextField(opt("text"), () -> cfg().text, v -> cfg().text = v, 48, cp -> cp >= 32 && cp != 127)
						.tip(tip("text")));
				e.add(choice("text_mode", HaloOptions.TextMode.values(), () -> cfg().textMode, v -> cfg().textMode = v, false));
				e.add(slider("text_size", 0.2, 4, 0.05, () -> cfg().textSize, v -> cfg().textSize = v, "%.2f"));
				e.add(slider("text_offset", -2, 4, 0.05, () -> cfg().textOffset, v -> cfg().textOffset = v, "%.2f"));
				e.add(slider("text_height", -1, 4, 0.05, () -> cfg().textHeight, v -> cfg().textHeight = v, "%.2f"));
				e.add(slider("text_speed", -360, 360, 5, () -> cfg().textSpeed, v -> cfg().textSpeed = v, "%.0f°/s"));
				e.add(intSlider("text_repeat", 1, 8, () -> cfg().textRepeat, v -> cfg().textRepeat = v));
				e.add(slider("text_spacing", 0.5, 3, 0.05, () -> cfg().textSpacing, v -> cfg().textSpacing = v, "%.2f"));
				e.add(toggle("text_shadow", () -> cfg().textShadow, v -> cfg().textShadow = v));
				e.add(toggle("text_smooth_font", () -> cfg().textSmoothFont, v -> cfg().textSmoothFont = v));
				e.add(header("Text colours"));
				colorStudio(e, () -> cfg().textColor);
			}
			case EMBLEM -> {
				e.add(toggle("emblem_enabled", () -> cfg().emblemEnabled, v -> cfg().emblemEnabled = v));
				e.add(new Controls.CardGrid<String>(HaloImages::available, k -> k.equals(cfg().emblem), k -> cfg().emblem = k,
						(g, k, x, y, w, h, hover, sel) -> {
							Ui.round(g, x, y, x + w, y + h, 6, Ui.mix(Ui.BG_FIELD, Ui.BG_FIELD_HOVER, Math.max(hover, sel ? 1 : 0)));
							int s = h - 18;
							Controls.image(g, HaloImages.texture(k), x + (w - s) / 2, y + 3, s,
									sel ? 0xFFFFD27A : Ui.mix(0xFFB8BBC8, 0xFFFFFFFF, hover));
							Ui.textCentered(g, Ui.fit(HaloImages.displayName(k), w - 6), x + w / 2, y + h - 12,
									sel ? Ui.ACCENT : Ui.TEXT_MUTED, Ui.FONT);
							if (sel) Ui.roundOutline(g, x, y, x + w, y + h, 6, Ui.withAlpha(Ui.ACCENT, 0.7f));
						}, 62, 54));
				e.add(new Controls.Note(() -> {
					String err = HaloImages.error(cfg().emblem);
					return err != null ? "Couldn't load that image: " + err
							: "Add your own: drop PNG files into config/angelichalo/images and they appear here automatically.";
				}, Ui.TEXT_MUTED));
				e.add(choice("emblem_mode", HaloOptions.EmblemMode.values(), () -> cfg().emblemMode, v -> cfg().emblemMode = v, false));
				e.add(slider("emblem_size", 0.2, 12, 0.05, () -> cfg().emblemSize, v -> cfg().emblemSize = v, "%.2f"));
				e.add(slider("emblem_spin", -360, 360, 5, () -> cfg().emblemSpin, v -> cfg().emblemSpin = v, "%.0f°/s"));
				e.add(slider("emblem_height", -1, 4, 0.01, () -> cfg().emblemHeight, v -> cfg().emblemHeight = v, "%.2f"));
				e.add(slider("emblem_pulse", 0, 1, 0.01, () -> cfg().emblemPulse, v -> cfg().emblemPulse = v, "%.2f"));
				e.add(header("Emblem colours"));
				colorStudio(e, () -> cfg().emblemColor);
			}
			case NAMETAG -> {
				e.add(toggle("tag_enabled", () -> cfg().tagEnabled, v -> cfg().tagEnabled = v));
				e.add(new Controls.TextField(opt("tag_text"), () -> cfg().tagText, v -> cfg().tagText = v, 64, cp -> cp >= 32 && cp != 127)
						.tip(tip("tag_text")));
				e.add(new Controls.TextField(opt("tag_title"), () -> cfg().tagTitle, v -> cfg().tagTitle = v, 64, cp -> cp >= 32 && cp != 127)
						.tip(tip("tag_title")));
				e.add(toggle("tag_title_enabled", () -> cfg().tagTitleEnabled, v -> cfg().tagTitleEnabled = v));
				e.add(note("Leave the name empty to show your username. Formatting codes work too: &a…&f colours, &#FF77AA any colour, &l bold, &o italic, &n underline, &m strikethrough, &k magic, &r reset."));
				e.add(header("Symbols"));
				e.add(new Controls.Choice<Integer>("Insert into", List.of(0, 1), () -> symbolTarget, v -> symbolTarget = v,
						v -> v == 0 ? "Name" : "Title"));
				e.add(new Controls.SymbolGrid(SYMBOLS, sym -> {
					HaloConfig c = cfg();
					if (symbolTarget == 0) {
						if (c.tagText.codePointCount(0, c.tagText.length()) < 64) c.tagText = c.tagText + sym;
					} else if (c.tagTitle.codePointCount(0, c.tagTitle.length()) < 64) {
						c.tagTitle = c.tagTitle + sym;
					}
				}));
				e.add(header("Icons"));
				e.add(new Controls.Note(() -> "Left of your name", Ui.TEXT));
				e.add(iconPicker(() -> cfg().tagIconLeft, v -> cfg().tagIconLeft = v));
				e.add(new Controls.Note(() -> "Right of your name", Ui.TEXT));
				e.add(iconPicker(() -> cfg().tagIconRight, v -> cfg().tagIconRight = v));
				e.add(new Controls.Note(() -> "Add your own: drop PNG files into config/angelichalo/icons and they appear here automatically.", Ui.TEXT_MUTED));
				e.add(header("Look"));
				e.add(slider("tag_scale", 0.3, 4, 0.05, () -> cfg().tagScale, v -> cfg().tagScale = v, "%.2f×"));
				e.add(slider("tag_height", -1, 3, 0.05, () -> cfg().tagHeight, v -> cfg().tagHeight = v, "%.2f"));
				e.add(slider("tag_icon_size", 0.5, 3, 0.05, () -> cfg().tagIconSize, v -> cfg().tagIconSize = v, "%.2f×"));
				e.add(slider("tag_glow", 0, 2, 0.01, () -> cfg().tagGlow, v -> cfg().tagGlow = v, "%.2f"));
				e.add(toggle("tag_background", () -> cfg().tagBackground, v -> cfg().tagBackground = v));
				e.add(slider("tag_background_opacity", 0, 1, 0.01, () -> cfg().tagBackgroundOpacity, v -> cfg().tagBackgroundOpacity = v, "%.2f"));
				e.add(toggle("tag_shadow", () -> cfg().tagShadow, v -> cfg().tagShadow = v));
				e.add(toggle("tag_bold", () -> cfg().tagBold, v -> cfg().tagBold = v));
				e.add(toggle("tag_smooth_font", () -> cfg().tagSmoothFont, v -> cfg().tagSmoothFont = v));
				e.add(toggle("tag_through_walls", () -> cfg().tagThroughWalls, v -> cfg().tagThroughWalls = v));
				e.add(header("Name colours"));
				colorStudio(e, () -> cfg().tagColor);
				e.add(header("Title colours"));
				colorStudio(e, () -> cfg().tagTitleColor);
				e.add(header("Icon colours"));
				colorStudio(e, () -> cfg().tagIconColor);
				e.add(new Controls.Note(() -> "Only you see this nametag. It doesn't change your real name, and nothing is sent to the server.", Ui.SUCCESS));
			}
			case EXTRAS -> {
				e.add(header(tr("angelichalo.section.embers")));
				e.add(toggle("embers", () -> cfg().embers, v -> cfg().embers = v));
				e.add(intSlider("ember_count", 1, 128, () -> cfg().emberCount, v -> cfg().emberCount = v));
				e.add(slider("ember_rise", 0.1, 4, 0.05, () -> cfg().emberRise, v -> cfg().emberRise = v, "%.2f"));
				e.add(slider("ember_size", 0.01, 0.3, 0.005, () -> cfg().emberSize, v -> cfg().emberSize = v, "%.3f"));
				e.add(header(tr("angelichalo.section.helix")));
				e.add(toggle("helix", () -> cfg().helix, v -> cfg().helix = v));
				e.add(intSlider("helix_strands", 1, 6, () -> cfg().helixStrands, v -> cfg().helixStrands = v));
				e.add(slider("helix_speed", 0.05, 4, 0.05, () -> cfg().helixSpeed, v -> cfg().helixSpeed = v, "%.2f"));
				e.add(slider("helix_radius", 0.1, 3, 0.05, () -> cfg().helixRadius, v -> cfg().helixRadius = v, "%.2f"));
				e.add(header(tr("angelichalo.section.reactive")));
				e.add(toggle("jump_rings", () -> cfg().jumpRings, v -> cfg().jumpRings = v));
				e.add(slider("jump_ring_size", 0.5, 8, 0.1, () -> cfg().jumpRingSize, v -> cfg().jumpRingSize = v, "%.1f"));
				e.add(toggle("speed_reactive", () -> cfg().speedReactive, v -> cfg().speedReactive = v));
				e.add(slider("speed_reactive_amount", 0, 1.5, 0.01, () -> cfg().speedReactiveAmount, v -> cfg().speedReactiveAmount = v, "%.2f"));
				e.add(toggle("damage_flash", () -> cfg().damageFlash, v -> cfg().damageFlash = v));
				e.add(new Controls.ColorRow(opt("damage_color"), () -> cfg().damageColor, v -> cfg().damageColor = v));
				e.add(new Controls.Row(
						button(tr("angelichalo.gui.test_jump"), () -> {
							if (minecraft.player != null) HaloState.simulateJump(minecraft.player.position());
						}),
						button(tr("angelichalo.gui.test_damage"), HaloState::simulateDamage)));
				e.add(header("Extras colours"));
				colorStudio(e, () -> cfg().extrasColor);
			}
			case GENERAL -> {
				e.add(toggle("enabled", () -> cfg().enabled, v -> cfg().enabled = v));
				e.add(toggle("through_walls", () -> cfg().throughWalls, v -> cfg().throughWalls = v));
				e.add(slider("hidden_opacity", 0, 1, 0.01, () -> cfg().hiddenOpacity, v -> cfg().hiddenOpacity = v, "%.2f"));
				e.add(toggle("show_first_person", () -> cfg().showInFirstPerson, v -> cfg().showInFirstPerson = v));
				e.add(toggle("show_third_person", () -> cfg().showInThirdPerson, v -> cfg().showInThirdPerson = v));
				e.add(toggle("hide_sneaking", () -> cfg().hideWhenSneaking, v -> cfg().hideWhenSneaking = v));
				e.add(toggle("hide_invisible", () -> cfg().hideWhenInvisible, v -> cfg().hideWhenInvisible = v));
				e.add(new Controls.Note(() -> "100% client-side visuals: no packets, no game changes, nothing sent to the server.", Ui.SUCCESS));
			}
			case SHARE -> {
				e.add(header("Share codes"));
				e.add(note("A share code is your entire look in one line of text. Send it to a friend, or paste theirs."));
				e.add(new Controls.Row(
						new Controls.Button(() -> "Copy my code", () -> {
							minecraft.keyboardHandler.setClipboard(PresetLibrary.toCode(cfg()));
							flash("Share code copied");
						}, true),
						button("Paste a code", () -> {
							HaloConfig imported = PresetLibrary.fromCode(minecraft.keyboardHandler.getClipboard());
							if (imported == null) {
								flash("Clipboard has no halo code");
							} else {
								HaloConfig.set(imported);
								flash("Code applied");
							}
						})));
				e.add(header("Saved looks"));
				e.add(new Controls.TextField("Name", () -> saveName, v -> saveName = v, 32,
						cp -> Character.isLetterOrDigit(cp) || cp == ' ' || cp == '-' || cp == '_'));
				e.add(new Controls.Button(() -> "Save current look", () -> {
					String name = saveName.isBlank() ? "My halo" : saveName;
					if (PresetLibrary.save(name, cfg())) {
						flash("Saved \"" + PresetLibrary.safeName(name) + "\"");
						rebuild();
					} else {
						flash("Couldn't save");
					}
				}, true));
				List<String> saved = PresetLibrary.list();
				if (saved.isEmpty()) {
					e.add(note("Nothing saved yet. Saved looks live in config/angelichalo/presets as .json files, which you can share too."));
				}
				for (String name : saved) {
					e.add(new Controls.Row(
							new Controls.Note(() -> name, Ui.TEXT),
							button("Load", () -> {
								HaloConfig loaded = PresetLibrary.load(name);
								if (loaded != null) {
									HaloConfig.set(loaded);
									flash("Loaded \"" + name + "\"");
								}
							}),
							button("Delete", () -> {
								PresetLibrary.delete(name);
								flash("Deleted \"" + name + "\"");
								rebuild();
							})));
				}
			}
		}

		return e;
	}
}
