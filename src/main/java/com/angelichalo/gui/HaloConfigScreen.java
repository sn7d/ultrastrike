package com.angelichalo.gui;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.google.gson.Gson;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.angelichalo.config.ColorSpec;
import com.angelichalo.config.HaloConfig;
import com.angelichalo.config.HaloOptions;
import com.angelichalo.config.HaloPresets;
import com.angelichalo.render.HaloColors;
import com.angelichalo.render.HaloState;

/**
 * The settings screen. Every change applies instantly (watch your halo while you edit — the camera swings round to
 * a front view). "Done" saves, "Cancel" restores what you had when you opened it.
 */
public class HaloConfigScreen extends Screen {
	private enum Tab { PRESETS, SHAPE, MOTION, TRAIL, COLORS, EXTRAS, GENERAL }

	private static final int[] PALETTE = {
			0xFF4B55, 0xFF2A00, 0xFF8A00, 0xFFD54F, 0xFFF4D6, 0x7CFF2B, 0x00FF88, 0x3DFFB5,
			0x38E8FF, 0x2F7BFF, 0x7C4DFF, 0xC77DFF, 0xFF9EC7, 0xFF00AA, 0xFFFFFF, 0x101010
	};

	private static Tab lastTab = Tab.PRESETS;
	private static int lastColorTarget = 0;

	private final @Nullable Screen parent;
	private final HaloConfig backup;
	private @Nullable CameraType previousCamera;
	private Tab tab = lastTab;
	private int colorTarget = lastColorTarget; // 0 ring, 1 trail, 2 extras
	private int scroll;
	private int maxScroll;
	private final List<Label> labels = new ArrayList<>();
	private @Nullable Component status;
	private long statusUntil;

	// layout
	private int panelX;
	private int panelW;
	private int contentX;
	private int contentTop;
	private int contentBottom;
	private int colW;
	private int fullW;
	private int columns;

	private record Label(int x, int y, Component text, int color) { }

	/** A cell in the two-column grid. Returns null for headers (drawn as labels). */
	private interface Entry {
		int span();

		@Nullable AbstractWidget create(int x, int y, int w);
	}

	/** Number of tabs (used by the in-game screenshot test). */
	public static int tabCount() {
		return Tab.values().length;
	}

	/** Choose which tab the next opened screen starts on. */
	public static void setStartTab(int index) {
		lastTab = Tab.values()[Math.floorMod(index, Tab.values().length)];
	}

	/** Choose which colour layer (0 ring, 1 trail, 2 extras) the Colours tab edits. */
	public static void setStartColorTarget(int target) {
		lastColorTarget = Math.floorMod(target, 3);
	}

	public HaloConfigScreen(@Nullable Screen parent) {
		super(Component.translatable("angelichalo.gui.title"));
		this.parent = parent;
		this.backup = HaloConfig.get().copy();
	}

	private static HaloConfig cfg() {
		return HaloConfig.get();
	}

	// ------------------------------------------------------------------------------------------------
	// lifecycle

	@Override
	protected void init() {
		HaloConfig c = cfg();

		if (previousCamera == null && minecraft.level != null && c.previewInThirdPerson) {
			previousCamera = minecraft.options.getCameraType();
			minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
		}

		labels.clear();
		panelX = 6;
		// keep the centre of the screen (your character in the selfie preview) uncovered when in a world
		boolean inWorld = minecraft.level != null;
		panelW = inWorld ? Math.max(220, Math.min(372, width / 2 - 24)) : Math.min(372, width - 12);
		panelW = Math.min(panelW, width - 12);
		int tabW = panelW < 300 ? 60 : 70;
		contentX = panelX + tabW + 8;
		fullW = panelX + panelW - 8 - contentX;
		columns = fullW >= 250 ? 2 : 1;
		colW = columns == 2 ? (fullW - 6) / 2 : fullW;
		contentTop = 30;
		contentBottom = height - 32;

		// tabs
		int ty = contentTop;
		for (Tab t : Tab.values()) {
			Button b = Button.builder(Component.translatable("angelichalo.tab." + t.name().toLowerCase(Locale.ROOT)), btn -> {
				tab = t;
				lastTab = t;
				scroll = 0;
				rebuildWidgets();
			}).bounds(panelX + 4, ty, tabW, 20).build();
			b.active = t != tab;
			addRenderableWidget(b);
			ty += 22;
		}

		// grid
		List<Entry> entries = entries(tab);
		int rowH = 24;
		int visibleRows = Math.max(1, (contentBottom - contentTop) / rowH);
		int totalRows = countRows(entries, columns);
		maxScroll = Math.max(0, totalRows - visibleRows);
		scroll = Math.max(0, Math.min(scroll, maxScroll));

		int row = 0;
		int col = 0;
		for (Entry e : entries) {
			int span = Math.min(e.span(), columns);
			if (col + span > columns) {
				row++;
				col = 0;
			}

			int visibleRow = row - scroll;
			if (visibleRow >= 0 && visibleRow < visibleRows) {
				int x = contentX + col * (colW + 6);
				int y = contentTop + visibleRow * rowH;
				int w = span == columns ? fullW : colW;
				AbstractWidget widget = e.create(x, y, w);
				if (widget != null) addRenderableWidget(widget);
			}

			col += span;
			if (col >= columns) {
				row++;
				col = 0;
			}
		}

		// footer
		int fy = height - 26;
		int bw = (panelW - 16) / 3;

		addRenderableWidget(Button.builder(Component.translatable("angelichalo.gui.reset"), b -> {
			HaloConfig fresh = new HaloConfig();
			fresh.previewInThirdPerson = cfg().previewInThirdPerson;
			HaloConfig.set(fresh);
			rebuildWidgets();
		}).bounds(panelX + 4, fy, bw, 20).tooltip(Tooltip.create(Component.translatable("angelichalo.gui.reset.tip"))).build());
		addRenderableWidget(Button.builder(Component.translatable("angelichalo.gui.cancel"), b -> {
			HaloConfig.set(backup.copy());
			closeScreen();
		}).bounds(panelX + 8 + bw, fy, bw, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("angelichalo.gui.done"), b -> {
			HaloConfig.save();
			closeScreen();
		}).bounds(panelX + 12 + bw * 2, fy, bw, 20).build());
	}

	private static int countRows(List<Entry> entries, int columns) {
		int row = 0;
		int col = 0;
		for (Entry e : entries) {
			int span = Math.min(e.span(), columns);
			if (col + span > columns) {
				row++;
				col = 0;
			}
			col += span;
			if (col >= columns) {
				row++;
				col = 0;
			}
		}
		return row + (col > 0 ? 1 : 0);
	}

	@Override
	public void onClose() {
		// ESC behaves like Done
		HaloConfig.save();
		closeScreen();
	}

	private void closeScreen() {
		restoreCamera();
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void removed() {
		restoreCamera();
		super.removed();
	}

	private void restoreCamera() {
		if (previousCamera != null) {
			minecraft.options.setCameraType(previousCamera);
			previousCamera = null;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (maxScroll > 0 && mouseX >= contentX && mouseX <= panelX + panelW && mouseY >= contentTop && mouseY <= contentBottom) {
			int next = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(scrollY)));
			if (next != scroll) {
				scroll = next;
				rebuildWidgets();
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	// ------------------------------------------------------------------------------------------------
	// drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (minecraft.level == null) {
			super.extractBackground(g, mouseX, mouseY, delta);
		}

		// glassy side panel so the world (and your halo) stays visible
		int x1 = panelX + panelW;
		g.fill(panelX, 4, x1, height - 4, 0xC00C0C12);
		g.fill(panelX, 4, x1, 5, 0xFFFFD27A);
		g.fillGradient(panelX, 5, x1, 26, 0x40FFD27A, 0x00FFD27A);
		g.fill(contentX - 5, contentTop - 2, contentX - 4, contentBottom, 0x30FFFFFF);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);

		g.text(font, title, panelX + 8, 11, 0xFFFFE7A0, true);
		Component hint = Component.translatable("angelichalo.gui.hint");
		if (font.width(title) + font.width(hint) + 24 < panelW) {
			g.text(font, hint, panelX + panelW - 6 - font.width(hint), 11, 0xFF9A9AA6, false);
		}

		for (Label l : labels) {
			g.text(font, l.text(), l.x(), l.y(), l.color(), true);
		}

		if (maxScroll > 0) {
			int trackX = panelX + panelW - 4;
			int trackH = contentBottom - contentTop;
			int thumbH = Math.max(12, trackH * 6 / (6 + maxScroll));
			int thumbY = contentTop + (trackH - thumbH) * scroll / maxScroll;
			g.fill(trackX, contentTop, trackX + 2, contentBottom, 0x30FFFFFF);
			g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xC0FFD27A);
		}

		if (status != null && System.currentTimeMillis() < statusUntil) {
			g.text(font, status, panelX + 8, height - 38, 0xFF7CFF8A, true);
		}
	}

	private void flash(Component message) {
		status = message;
		statusUntil = System.currentTimeMillis() + 2500;
	}

	// ------------------------------------------------------------------------------------------------
	// entry factories

	private Entry header(String key) {
		return new Entry() {
			@Override
			public int span() {
				return 2;
			}

			@Override
			public @Nullable AbstractWidget create(int x, int y, int w) {
				labels.add(new Label(x, y + 8, Component.translatable(key), 0xFFFFD27A));
				return null;
			}
		};
	}

	private Entry slider(String key, double min, double max, double step, DoubleSupplier get, DoubleConsumer set, String fmt) {
		return new Entry() {
			@Override
			public int span() {
				return 1;
			}

			@Override
			public AbstractWidget create(int x, int y, int w) {
				HaloWidgets.Slider s = new HaloWidgets.Slider(x, y, w, Component.translatable("angelichalo.option." + key),
						min, max, step, get, set, v -> String.format(Locale.ROOT, fmt, v));
				s.setTooltip(Tooltip.create(Component.translatable("angelichalo.option." + key + ".tip")));
				return s;
			}
		};
	}

	private Entry intSlider(String key, int min, int max, IntSupplier get, IntConsumer set) {
		return slider(key, min, max, 1, get::getAsInt, v -> set.accept((int) Math.round(v)), "%.0f");
	}

	private Entry toggle(String key, BooleanSupplier get, Consumer<Boolean> set) {
		return new Entry() {
			@Override
			public int span() {
				return 1;
			}

			@Override
			public AbstractWidget create(int x, int y, int w) {
				CycleButton<Boolean> b = CycleButton.onOffBuilder(get.getAsBoolean())
						.create(x, y, w, 20, Component.translatable("angelichalo.option." + key), (btn, v) -> {
							set.accept(v);
						});
				b.setTooltip(Tooltip.create(Component.translatable("angelichalo.option." + key + ".tip")));
				return b;
			}
		};
	}

	private <E extends Enum<E>> Entry choice(String key, E[] values, Supplier<E> get, Consumer<E> set, boolean rebuild) {
		return new Entry() {
			@Override
			public int span() {
				return 1;
			}

			@Override
			public AbstractWidget create(int x, int y, int w) {
				String enumKey = "angelichalo.enum." + values[0].getDeclaringClass().getSimpleName().toLowerCase(Locale.ROOT) + ".";
				CycleButton<E> b = CycleButton.<E>builder(v -> Component.translatable(enumKey + v.name().toLowerCase(Locale.ROOT)), get.get())
						.withValues(values)
						.create(x, y, w, 20, Component.translatable("angelichalo.option." + key), (btn, v) -> {
							set.accept(v);
							if (rebuild) rebuildWidgets();
						});
				b.setTooltip(Tooltip.create(Component.translatable("angelichalo.option." + key + ".tip")));
				return b;
			}
		};
	}

	private Entry button(String key, int span, Runnable action) {
		return new Entry() {
			@Override
			public int span() {
				return span;
			}

			@Override
			public AbstractWidget create(int x, int y, int w) {
				return Button.builder(Component.translatable(key), b -> action.run()).bounds(x, y, w, 20).build();
			}
		};
	}

	/** Full-width row: label, swatch, hex field and hue bar. */
	private Entry color(String key, IntSupplier get, IntConsumer set) {
		return new Entry() {
			@Override
			public int span() {
				return 2;
			}

			@Override
			public @Nullable AbstractWidget create(int x, int y, int w) {
				Component label = Component.translatable("angelichalo.option." + key);
				int labelW = w >= 230 ? 62 : 0;
				if (labelW > 0) labels.add(new Label(x, y + 6, label, 0xFFE0E0E8));

				int sx = x + labelW;
				addRenderableWidget(new HaloWidgets.Preview(sx, y + 2, 16, 16, u -> 0xFF000000 | get.getAsInt()));

				EditBox hex = new EditBox(font, sx + 20, y + 1, 58, 18, label);
				hex.setMaxLength(7);
				hex.setTooltip(Tooltip.create(label));
				hex.setValue(String.format("#%06X", get.getAsInt()));
				hex.setResponder(text -> {
					String t = text.startsWith("#") ? text.substring(1) : text;
					if (t.length() == 6) {
						try {
							set.accept(Integer.parseInt(t, 16));
						} catch (NumberFormatException ignored) {
							// keep typing
						}
					}
				});
				addRenderableWidget(hex);

				int hx = sx + 84;
				return new HaloWidgets.HueBar(hx, y + 4, Math.max(20, x + w - hx - 2), 12, get, v -> {
					set.accept(v);
					hex.setValue(String.format("#%06X", v));
				});
			}
		};
	}

	/** Palette: left click = primary, right click = secondary. */
	private Entry palette(IntConsumer primary, IntConsumer secondary) {
		return new Entry() {
			@Override
			public int span() {
				return 2;
			}

			@Override
			public @Nullable AbstractWidget create(int x, int y, int w) {
				int size = Math.max(10, Math.min(18, (w - 15) / PALETTE.length - 2));
				for (int i = 0; i < PALETTE.length; i++) {
					int rgb = PALETTE[i];
					HaloWidgets.Swatch s = new HaloWidgets.Swatch(x + i * (size + 2), y + 2, size, rgb, v -> {
						primary.accept(v);
						rebuildWidgets();
					}, v -> {
						secondary.accept(v);
						rebuildWidgets();
					});
					s.setTooltip(Tooltip.create(Component.translatable("angelichalo.gui.palette.tip")));
					addRenderableWidget(s);
				}
				return null;
			}
		};
	}

	/** Animated strip showing how the colour spec looks around the ring right now. */
	private Entry preview(Supplier<ColorSpec> spec) {
		return new Entry() {
			@Override
			public int span() {
				return 2;
			}

			@Override
			public AbstractWidget create(int x, int y, int w) {
				int lw = w >= 230 ? 62 : 0;
				if (lw > 0) labels.add(new Label(x, y + 6, Component.translatable("angelichalo.gui.preview"), 0xFFE0E0E8));
				return new HaloWidgets.Preview(x + lw, y + 3, w - lw - 2, 14,
						u -> HaloColors.color(spec.get(), u, u, HaloState.now(), 0.5 + 0.5 * Math.sin(HaloState.now()), 1.0));
			}
		};
	}

	// ------------------------------------------------------------------------------------------------
	// tabs

	private List<Entry> entries(Tab t) {
		List<Entry> e = new ArrayList<>();

		switch (t) {
			case PRESETS -> {
				e.add(header("angelichalo.section.presets"));
				for (HaloPresets.Preset p : HaloPresets.ALL) {
					e.add(new Entry() {
						@Override
						public int span() {
							return 1;
						}

						@Override
						public AbstractWidget create(int x, int y, int w) {
							addRenderableWidget(new HaloWidgets.Preview(x + 3, y + 5, 10, 10, u -> 0xFF000000 | p.swatch()));
							Button b = Button.builder(Component.translatable("angelichalo.preset." + p.id()), btn -> {
								HaloConfig.set(p.build(cfg()));
								flash(Component.translatable("angelichalo.msg.preset", Component.translatable("angelichalo.preset." + p.id())));
								rebuildWidgets();
							}).bounds(x + 16, y, w - 16, 20)
									.tooltip(Tooltip.create(Component.translatable("angelichalo.preset." + p.id() + ".tip")))
									.build();
							return b;
						}
					});
				}
				e.add(header("angelichalo.section.share"));
				e.add(button("angelichalo.gui.copy_code", 1, this::copyCode));
				e.add(button("angelichalo.gui.paste_code", 1, this::pasteCode));
			}
			case SHAPE -> {
				HaloConfig c = cfg();
				e.add(header("angelichalo.section.shape"));
				e.add(choice("style", HaloOptions.Style.values(), () -> cfg().style, v -> cfg().style = v, true));
				e.add(slider("radius", 0.2, 16, 0.05, () -> cfg().radius, v -> cfg().radius = v, "%.2f"));
				e.add(slider("line_width", 0.5, 12, 0.25, () -> cfg().lineWidth, v -> cfg().lineWidth = v, "%.2f px"));
				e.add(slider("height_offset", -1, 3, 0.01, () -> cfg().heightOffset, v -> cfg().heightOffset = v, "%.2f"));
				e.add(intSlider("segments", 16, 360, () -> cfg().segments, v -> cfg().segments = v));
				e.add(slider("spin_speed", -720, 720, 5, () -> cfg().spinSpeed, v -> cfg().spinSpeed = v, "%.0f°/s"));
				e.add(slider("tilt", -60, 60, 1, () -> cfg().tilt, v -> cfg().tilt = v, "%.0f°"));
				e.add(intSlider("ring_count", 1, 8, () -> cfg().ringCount, v -> cfg().ringCount = v));
				e.add(slider("ring_spacing", 0.02, 1, 0.01, () -> cfg().ringSpacing, v -> cfg().ringSpacing = v, "%.2f"));

				e.add(header("angelichalo.section.style_options"));
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
					default -> e.add(header("angelichalo.section.no_style_options"));
				}
			}
			case MOTION -> {
				HaloConfig c = cfg();
				e.add(header("angelichalo.section.motion"));
				e.add(choice("motion", HaloOptions.Motion.values(), () -> cfg().motion, v -> cfg().motion = v, true));
				e.add(button("angelichalo.gui.goto_trail", 1, () -> {
					tab = Tab.TRAIL;
					lastTab = tab;
					scroll = 0;
					rebuildWidgets();
				}));

				switch (c.motion) {
					case SCAN -> {
						e.add(header("angelichalo.section.scan"));
						e.add(slider("scan_speed", 0.02, 4, 0.01, () -> cfg().scanSpeed, v -> cfg().scanSpeed = v, "%.2f/s"));
						e.add(choice("scan_easing", HaloOptions.Easing.values(), () -> cfg().scanEasing, v -> cfg().scanEasing = v, false));
						e.add(choice("scan_direction", HaloOptions.ScanDirection.values(), () -> cfg().scanDirection, v -> cfg().scanDirection = v, false));
						e.add(slider("scan_pause", 0, 3, 0.05, () -> cfg().scanPause, v -> cfg().scanPause = v, "%.2f s"));
						e.add(slider("scan_min", 0, 1.2, 0.01, () -> cfg().scanMin, v -> cfg().scanMin = v, "%.2f"));
						e.add(slider("scan_max", 0, 1.2, 0.01, () -> cfg().scanMax, v -> cfg().scanMax = v, "%.2f"));
						e.add(toggle("scan_contour", () -> cfg().scanContour, v -> cfg().scanContour = v));
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
					default -> e.add(header("angelichalo.section.static_hint"));
				}
			}
			case TRAIL -> {
				e.add(header("angelichalo.section.trail"));
				if (cfg().motion != HaloOptions.Motion.SCAN) {
					e.add(header("angelichalo.section.trail_needs_scan"));
					e.add(button("angelichalo.gui.enable_scan", 2, () -> {
						cfg().motion = HaloOptions.Motion.SCAN;
						rebuildWidgets();
					}));
				}
				e.add(toggle("trail_enabled", () -> cfg().trailEnabled, v -> cfg().trailEnabled = v));
				e.add(choice("trail_style", HaloOptions.TrailStyle.values(), () -> cfg().trailStyle, v -> cfg().trailStyle = v, false));
				e.add(slider("trail_length", 0.05, 2, 0.01, () -> cfg().trailLength, v -> cfg().trailLength = v, "%.2f s"));
				e.add(intSlider("trail_echoes", 1, 12, () -> cfg().trailEchoes, v -> cfg().trailEchoes = v));
				e.add(toggle("trail_match_ring", () -> cfg().trailMatchRing, v -> {
					cfg().trailMatchRing = v;
					rebuildWidgets();
				}));
				e.add(button("angelichalo.gui.edit_trail_colors", 1, () -> {
					colorTarget = 1;
					lastColorTarget = 1;
					tab = Tab.COLORS;
					lastTab = tab;
					scroll = 0;
					rebuildWidgets();
				}));
				e.add(preview(() -> cfg().trailMatchRing ? cfg().ringColor : cfg().trailColor));
			}
			case COLORS -> {
				e.add(header("angelichalo.section.colors"));
				e.add(new Entry() {
					@Override
					public int span() {
						return 2;
					}

					@Override
					public AbstractWidget create(int x, int y, int w) {
						CycleButton<Integer> b = CycleButton.<Integer>builder(
										v -> Component.translatable("angelichalo.gui.color_target." + v), colorTarget)
								.withValues(0, 1, 2)
								.create(x, y, w, 20, Component.translatable("angelichalo.gui.color_target"), (btn, v) -> {
									colorTarget = v;
									lastColorTarget = v;
									rebuildWidgets();
								});
						return b;
					}
				});
				e.add(choice("color_mode", HaloOptions.ColorMode.values(), () -> spec().mode, v -> spec().mode = v, false));
				e.add(slider("opacity", 0.05, 1, 0.01, () -> spec().opacity, v -> spec().opacity = v, "%.2f"));
				e.add(color("primary", () -> spec().primary, v -> spec().primary = v));
				e.add(color("secondary", () -> spec().secondary, v -> spec().secondary = v));
				e.add(palette(v -> spec().primary = v, v -> spec().secondary = v));
				e.add(preview(this::spec));
				e.add(slider("color_speed", 0, 4, 0.01, () -> spec().speed, v -> spec().speed = v, "%.2f"));
				e.add(intSlider("color_repeats", 1, 12, () -> spec().repeats, v -> spec().repeats = v));
				e.add(slider("saturation", 0, 1, 0.01, () -> spec().saturation, v -> spec().saturation = v, "%.2f"));
				e.add(slider("brightness", 0.1, 1, 0.01, () -> spec().brightness, v -> spec().brightness = v, "%.2f"));
				e.add(button("angelichalo.gui.swap_colors", 1, () -> {
					ColorSpec s = spec();
					int p = s.primary;
					s.primary = s.secondary;
					s.secondary = p;
					rebuildWidgets();
				}));
				e.add(button("angelichalo.gui.random_colors", 1, () -> {
					ColorSpec s = spec();
					double h = Math.random();
					s.primary = HaloColors.hsv(h, 0.8, 1.0);
					s.secondary = HaloColors.hsv(h + 0.35 + Math.random() * 0.3, 0.8, 1.0);
					rebuildWidgets();
				}));
			}
			case EXTRAS -> {
				e.add(header("angelichalo.section.embers"));
				e.add(toggle("embers", () -> cfg().embers, v -> cfg().embers = v));
				e.add(intSlider("ember_count", 1, 128, () -> cfg().emberCount, v -> cfg().emberCount = v));
				e.add(slider("ember_rise", 0.1, 4, 0.05, () -> cfg().emberRise, v -> cfg().emberRise = v, "%.2f"));
				e.add(slider("ember_size", 0.01, 0.3, 0.005, () -> cfg().emberSize, v -> cfg().emberSize = v, "%.3f"));
				e.add(header("angelichalo.section.helix"));
				e.add(toggle("helix", () -> cfg().helix, v -> cfg().helix = v));
				e.add(intSlider("helix_strands", 1, 6, () -> cfg().helixStrands, v -> cfg().helixStrands = v));
				e.add(slider("helix_speed", 0.05, 4, 0.05, () -> cfg().helixSpeed, v -> cfg().helixSpeed = v, "%.2f"));
				e.add(slider("helix_radius", 0.1, 3, 0.05, () -> cfg().helixRadius, v -> cfg().helixRadius = v, "%.2f"));
				e.add(header("angelichalo.section.reactive"));
				e.add(toggle("jump_rings", () -> cfg().jumpRings, v -> cfg().jumpRings = v));
				e.add(slider("jump_ring_size", 0.5, 8, 0.1, () -> cfg().jumpRingSize, v -> cfg().jumpRingSize = v, "%.1f"));
				e.add(toggle("speed_reactive", () -> cfg().speedReactive, v -> cfg().speedReactive = v));
				e.add(slider("speed_reactive_amount", 0, 1.5, 0.01, () -> cfg().speedReactiveAmount, v -> cfg().speedReactiveAmount = v, "%.2f"));
				e.add(toggle("damage_flash", () -> cfg().damageFlash, v -> cfg().damageFlash = v));
				e.add(color("damage_color", () -> cfg().damageColor, v -> cfg().damageColor = v));
				e.add(button("angelichalo.gui.test_jump", 1, () -> {
					if (minecraft.player != null) HaloState.simulateJump(minecraft.player.position());
				}));
				e.add(button("angelichalo.gui.test_damage", 1, HaloState::simulateDamage));
				e.add(button("angelichalo.gui.edit_extra_colors", 2, () -> {
					colorTarget = 2;
					lastColorTarget = 2;
					tab = Tab.COLORS;
					lastTab = tab;
					scroll = 0;
					rebuildWidgets();
				}));
			}
			case GENERAL -> {
				e.add(header("angelichalo.section.general"));
				e.add(toggle("enabled", () -> cfg().enabled, v -> cfg().enabled = v));
				e.add(toggle("through_walls", () -> cfg().throughWalls, v -> cfg().throughWalls = v));
				e.add(slider("hidden_opacity", 0, 1, 0.01, () -> cfg().hiddenOpacity, v -> cfg().hiddenOpacity = v, "%.2f"));
				e.add(toggle("show_first_person", () -> cfg().showInFirstPerson, v -> cfg().showInFirstPerson = v));
				e.add(toggle("show_third_person", () -> cfg().showInThirdPerson, v -> cfg().showInThirdPerson = v));
				e.add(toggle("hide_sneaking", () -> cfg().hideWhenSneaking, v -> cfg().hideWhenSneaking = v));
				e.add(toggle("hide_invisible", () -> cfg().hideWhenInvisible, v -> cfg().hideWhenInvisible = v));
				e.add(toggle("preview_third_person", () -> cfg().previewInThirdPerson, v -> {
					cfg().previewInThirdPerson = v;
					if (!v) restoreCamera();
				}));
				e.add(header("angelichalo.section.privacy"));
			}
		}

		return e;
	}

	private ColorSpec spec() {
		HaloConfig c = cfg();
		return switch (colorTarget) {
			case 1 -> c.trailColor;
			case 2 -> c.extrasColor;
			default -> c.ringColor;
		};
	}

	// ------------------------------------------------------------------------------------------------
	// share codes

	private static final String CODE_PREFIX = "AH1:";

	private void copyCode() {
		String json = cfg().toJson();
		String code = CODE_PREFIX + Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
		minecraft.keyboardHandler.setClipboard(code);
		flash(Component.translatable("angelichalo.msg.copied"));
	}

	private void pasteCode() {
		String text = minecraft.keyboardHandler.getClipboard().trim();

		if (!text.startsWith(CODE_PREFIX)) {
			flash(Component.translatable("angelichalo.msg.bad_code"));
			return;
		}

		try {
			String json = new String(Base64.getDecoder().decode(text.substring(CODE_PREFIX.length())), StandardCharsets.UTF_8);
			HaloConfig imported = new Gson().fromJson(json, HaloConfig.class);
			if (imported == null) throw new IllegalArgumentException("empty");
			imported.previewInThirdPerson = cfg().previewInThirdPerson;
			HaloConfig.set(imported);
			flash(Component.translatable("angelichalo.msg.pasted"));
			rebuildWidgets();
		} catch (RuntimeException e) {
			flash(Component.translatable("angelichalo.msg.bad_code"));
		}
	}
}
