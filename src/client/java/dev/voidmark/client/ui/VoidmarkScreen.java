package dev.voidmark.client.ui;

import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.location.SkyblockLocation;
import dev.voidmark.client.node.EnderNodeTracker;
import dev.voidmark.client.render.GuiDraw;
import dev.voidmark.client.visual.WorldTint;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

public class VoidmarkScreen extends Screen {
	private static final float MENU_W = 400;
	private static final float MENU_H = 248;
	private static final float SIDEBAR_W = 88;
	private static final float TOOLBAR_H = 22;
	private static final float ROW = 16;
	private static final float COL_GAP = 10;
	private static final float PAD = 8;
	private static final float SAVE_W = 42;
	private static final float ICON_SLOT = 14;

	private enum Group {
		VISUALS("VISUALS"),
		NODES("NODES"),
		MISC("MISCELLANEOUS");

		final String label;

		Group(String label) {
			this.label = label;
		}
	}

	private enum Tab {
		WORLD("World", Group.VISUALS),
		VIEW("View", Group.VISUALS),
		MARKERS("Markers", Group.NODES),
		DISPLAY("Display", Group.NODES),
		STATUS("Status", Group.MISC);

		final String label;
		final Group group;

		Tab(String label, Group group) {
			this.label = label;
			this.group = group;
		}
	}

	private enum PickerTarget {
		WORLD, SKY, NODE
	}

	private final List<Hit> hits = new ArrayList<>();
	private Tab tab = Tab.WORLD;
	private PickerTarget pickerTarget;
	private float pickerHue = 200f;
	private float pickerSat = 0.82f;
	private float pickerVal = 1f;
	private float pickerX;
	private float pickerY;
	private double lastClickY;
	private boolean dropdownOpen;
	private boolean savedFlash;
	private boolean dragging;
	private double dragOffX;
	private double dragOffY;
	private boolean placed;

	private float windowX;
	private float windowY;
	private float windowW = MENU_W;
	private float windowH = MENU_H;

	public VoidmarkScreen() {
		super(Component.literal("Voidmark"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (minecraft.level != null) {
			extractBlurredBackground(graphics);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		hits.clear();
		Font font = minecraft.font;
		layout();

		GuiDraw.fill(graphics, 0, 0, width, height, 0x14000000);
		GuiDraw.rounded(graphics, windowX + 1, windowY + 2, windowW, windowH, Theme.WINDOW_RADIUS, 0x66000000);
		GuiDraw.roundLeft(graphics, windowX, windowY, SIDEBAR_W, windowH, Theme.WINDOW_RADIUS, Theme.SIDEBAR);
		GuiDraw.roundRight(graphics, windowX + SIDEBAR_W, windowY, windowW - SIDEBAR_W, windowH, Theme.WINDOW_RADIUS, Theme.WINDOW);
		GuiDraw.fill(graphics, windowX + SIDEBAR_W, windowY + 8, 1, windowH - 16, 0x3318A0C8);

		drawSidebar(graphics, font, mouseX, mouseY);
		drawToolbar(graphics, font, mouseX, mouseY);
		drawColumns(graphics, font, mouseX, mouseY);
		if (dropdownOpen) {
			drawDropdown(graphics, font);
		}
		if (pickerTarget != null) {
			drawPicker(graphics, font);
		}
	}

	private void layout() {
		windowW = Math.min(MENU_W, Math.max(1, width - 16));
		windowH = Math.min(MENU_H, Math.max(1, height - 16));
		if (!placed) {
			windowX = (width - windowW) / 2f;
			windowY = (height - windowH) / 2f;
			placed = true;
		}
		windowX = Mth.clamp(windowX, 4, Math.max(4, width - windowW - 4));
		windowY = Mth.clamp(windowY, 4, Math.max(4, height - windowH - 4));
	}

	private float contentX() {
		return windowX + SIDEBAR_W + PAD;
	}

	private float contentW() {
		return windowW - SIDEBAR_W - PAD * 2;
	}

	private float colW() {
		return (contentW() - COL_GAP) / 2f;
	}

	private void drawSidebar(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		GuiDraw.title(graphics, font, "VOIDMARK", windowX + 10, windowY + 8, Theme.TEXT);
		GuiDraw.rounded(graphics, windowX + 10, windowY + 20, 16, 2, 1, Theme.ACCENT);
		hits.add(new Hit(windowX, windowY, SIDEBAR_W, 26, mx -> startDrag(mx, lastClickY), true));

		float y = windowY + 30;
		Group last = null;
		for (Tab value : Tab.values()) {
			if (value.group != last) {
				y += 5;
				GuiDraw.small(graphics, font, value.group.label, windowX + 10, y, Theme.HEADER);
				y += 10;
				last = value.group;
			}

			boolean active = tab == value;
			boolean hovered = GuiDraw.hovered(mouseX, mouseY, windowX + 6, y, SIDEBAR_W - 12, 16);
			if (active) {
				GuiDraw.rounded(graphics, windowX + 6, y, SIDEBAR_W - 12, 16, 8, Theme.NAV_PILL);
			} else if (hovered) {
				GuiDraw.rounded(graphics, windowX + 6, y, SIDEBAR_W - 12, 16, 8, 0x18FFFFFF);
			}
			int iconColor = active ? Theme.TEXT : Theme.ACCENT;
			int labelColor = active ? Theme.TEXT : Theme.MUTED;
			float labelY = GuiDraw.middle(y, 16);
			GuiDraw.icon(graphics, font, tabGlyph(value), windowX + 11, labelY, iconColor);
			GuiDraw.menu(graphics, font, value.label, windowX + 24, labelY, labelColor);
			hits.add(new Hit(windowX + 6, y, SIDEBAR_W - 12, 16, () -> {
				tab = value;
				pickerTarget = null;
				dropdownOpen = false;
			}));
			y += 17;
		}

		float footY = windowY + windowH - 22;
		GuiDraw.circle(graphics, windowX + 16, footY + 10, 5, Theme.ACCENT);
		GuiDraw.small(graphics, font, "VOIDMARK", windowX + 24, footY, Theme.TEXT);
		GuiDraw.small(graphics, font, "v" + modVersion(), windowX + 24, footY + 9, Theme.ACCENT);
	}

	private static String tabGlyph(Tab value) {
		return switch (value) {
			case WORLD -> MenuFont.GLOBE;
			case VIEW -> MenuFont.EYE;
			case MARKERS -> MenuFont.CUBE;
			case DISPLAY -> MenuFont.MONITOR;
			case STATUS -> MenuFont.SIGNAL;
		};
	}

	private void drawToolbar(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float x = contentX();
		float y = windowY + 5;
		float w = contentW();
		GuiDraw.hline(graphics, x, windowY + TOOLBAR_H - 1, w, Theme.LINE);
		hits.add(new Hit(windowX + SIDEBAR_W, windowY, windowW - SIDEBAR_W, TOOLBAR_H, mx -> startDrag(mx, lastClickY), true));

		float labelY = GuiDraw.middle(y, 14);
		boolean saveHover = GuiDraw.hovered(mouseX, mouseY, x, y, SAVE_W, 14);
		GuiDraw.panel(graphics, x, y, SAVE_W, 14, 5, savedFlash || saveHover ? Theme.CARD_HOVER : Theme.CARD, Theme.LINE);
		GuiDraw.icon(graphics, font, MenuFont.SAVE, x + 5, labelY, Theme.ACCENT);
		GuiDraw.menu(graphics, font, "Save", x + 16, labelY, Theme.TEXT);
		hits.add(new Hit(x, y, SAVE_W, 14, () -> {
			VoidmarkConfig.get().save();
			savedFlash = true;
		}));

		float comboX = x + SAVE_W + 4;
		float comboW = Math.min(92, w * 0.42f);
		String comboLabel = tab == Tab.VIEW ? aspectLabel(VoidmarkConfig.get().aspectRatio) : "Global";
		boolean comboHover = GuiDraw.hovered(mouseX, mouseY, comboX, y, comboW, 14);
		GuiDraw.panel(graphics, comboX, y, comboW, 14, 5, comboHover || dropdownOpen ? Theme.CARD_HOVER : Theme.CARD, Theme.LINE);
		GuiDraw.menu(graphics, font, comboLabel, comboX + 6, labelY, Theme.TEXT);
		GuiDraw.icon(graphics, font, MenuFont.CHEVRON, comboX + comboW - 11, labelY, Theme.MUTED);
		hits.add(new Hit(comboX, y, comboW, 14, () -> dropdownOpen = !dropdownOpen));

		float iconX = x + w - ICON_SLOT * 3;
		GuiDraw.icon(graphics, font, MenuFont.SETTINGS, iconX + 3, labelY, Theme.MUTED);
		GuiDraw.icon(graphics, font, MenuFont.BELL, iconX + ICON_SLOT + 3, labelY, Theme.MUTED);
		GuiDraw.icon(graphics, font, MenuFont.SEARCH, iconX + ICON_SLOT * 2 + 3, labelY, Theme.MUTED);
	}

	private void drawDropdown(GuiGraphicsExtractor graphics, Font font) {
		float comboX = contentX() + SAVE_W + 4;
		float comboW = Math.min(92, contentW() * 0.42f);
		float y = windowY + 20;
		String[] items = tab == Tab.VIEW ? new String[]{"Native", "16:10", "4:3", "5:4"} : new String[]{"Global"};
		float h = items.length * 14 + 4;
		GuiDraw.panel(graphics, comboX, y, comboW, h, 6, 0xFF0B1118, Theme.LINE);
		for (int i = 0; i < items.length; i++) {
			float iy = y + 2 + i * 14;
			boolean active = items[i].equals(tab == Tab.VIEW ? aspectLabel(VoidmarkConfig.get().aspectRatio) : "Global");
			if (active) {
				GuiDraw.rounded(graphics, comboX + 2, iy, comboW - 4, 14, 4, Theme.withAlpha(Theme.ACCENT, 28));
			}
			GuiDraw.circle(graphics, comboX + 8, iy + 7, 2.4f, active ? Theme.ACCENT : Theme.OFF);
			GuiDraw.menu(graphics, font, items[i], comboX + 14, GuiDraw.middle(iy, 14), active ? Theme.ACCENT : Theme.TEXT);
			int index = i;
			hits.add(new Hit(comboX, iy, comboW, 14, () -> {
				if (tab == Tab.VIEW) {
					float[] values = {1.00f, 0.90f, 0.75f, 0.70f};
					VoidmarkConfig config = VoidmarkConfig.get();
					config.aspectEnabled = true;
					config.aspectRatio = values[index];
				}
				dropdownOpen = false;
			}));
		}
	}

	private void drawColumns(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float left = contentX();
		float top = windowY + TOOLBAR_H + 6;
		float col = colW();
		VoidmarkConfig config = VoidmarkConfig.get();

		switch (tab) {
			case WORLD -> {
				float y = group(graphics, font, left, top, col, "Main");
				y = toggle(graphics, font, left, y, col, mouseX, mouseY, "World tint", config.worldTintEnabled, v -> config.worldTintEnabled = v, config.worldTintRgb, PickerTarget.WORLD);
				slider(graphics, font, left, y, col, "Strength", String.format(Locale.ROOT, "%.0f", config.worldTintStrength * 100), config.worldTintStrength, v -> config.worldTintStrength = v);

				y = group(graphics, font, left + col + COL_GAP, top, col, "Skybox");
				y = toggle(graphics, font, left + col + COL_GAP, y, col, mouseX, mouseY, "Skybox tint", config.skyTintEnabled, v -> config.skyTintEnabled = v, config.matchSkyToWorld ? -1 : config.skyTintRgb, config.matchSkyToWorld ? null : PickerTarget.SKY);
				y = toggle(graphics, font, left + col + COL_GAP, y, col, mouseX, mouseY, "Match world", config.matchSkyToWorld, v -> config.matchSkyToWorld = v, -1, null);
				slider(graphics, font, left + col + COL_GAP, y, col, "Strength", String.format(Locale.ROOT, "%.0f", config.skyTintStrength * 100), config.skyTintStrength, v -> config.skyTintStrength = v);
			}
			case VIEW -> {
				float y = group(graphics, font, left, top, col, "Camera");
				y = toggle(graphics, font, left, y, col, mouseX, mouseY, "Aspect ratio", config.aspectEnabled, v -> config.aspectEnabled = v, -1, null);
				slider(graphics, font, left, y, col, "Aspect", aspectLabel(config.aspectRatio), (config.aspectRatio - 0.50f) / 0.70f, v -> config.aspectRatio = VoidmarkConfig.clamp(0.50f + v * 0.70f, 0.50f, 1.20f));

				y = group(graphics, font, left + col + COL_GAP, top, col, "Info");
				GuiDraw.menu(graphics, font, "Below Native stretches", left + col + COL_GAP + 2, y + 2, Theme.MUTED);
				GuiDraw.menu(graphics, font, "horizontally, like 4:3.", left + col + COL_GAP + 2, y + 14, Theme.MUTED);
			}
			case MARKERS -> {
				float y = group(graphics, font, left, top, col, "Main");
				y = toggle(graphics, font, left, y, col, mouseX, mouseY, "Enable", config.markersEnabled, v -> config.markersEnabled = v, -1, null);
				y = toggle(graphics, font, left, y, col, mouseX, mouseY, "Only in The End", config.onlyInTheEnd, v -> config.onlyInTheEnd = v, -1, null);
				y = toggle(graphics, font, left, y, col, mouseX, mouseY, "Force enable", config.forceEnable, v -> config.forceEnable = v, -1, null);
				slider(graphics, font, left, y, col, "Scan radius", config.scanRadius + "m", (config.scanRadius - 16) / 64f, v -> config.scanRadius = VoidmarkConfig.clamp(16 + Math.round(v * 64f), 16, 80));

				y = group(graphics, font, left + col + COL_GAP, top, col, "Detection");
				y = toggle(graphics, font, left + col + COL_GAP, y, col, mouseX, mouseY, "Block scan", config.blockScan, v -> config.blockScan = v, -1, null);
				toggle(graphics, font, left + col + COL_GAP, y, col, mouseX, mouseY, "Particle hints", config.particleDetection, v -> config.particleDetection = v, -1, null);
			}
			case DISPLAY -> {
				float y = group(graphics, font, left, top, col, "Esp");
				y = toggle(graphics, font, left, y, col, mouseX, mouseY, "Filled box", config.boxFill, v -> config.boxFill = v, -1, null);
				y = toggle(graphics, font, left, y, col, mouseX, mouseY, "Outline", config.boxOutline, v -> config.boxOutline = v, -1, null);
				y = toggle(graphics, font, left, y, col, mouseX, mouseY, "Tracer", config.tracersEnabled, v -> config.tracersEnabled = v, -1, null);
				y = toggle(graphics, font, left, y, col, mouseX, mouseY, "Through walls", config.throughWalls, v -> config.throughWalls = v, -1, null);
				slider(graphics, font, left, y, col, "Fill opacity", Math.round(config.fillOpacity * 100) + "%", (config.fillOpacity - 0.08f) / 0.77f, v -> config.fillOpacity = VoidmarkConfig.clamp(0.08f + v * 0.77f, 0.08f, 0.85f));

				y = group(graphics, font, left + col + COL_GAP, top, col, "Hud");
				y = toggle(graphics, font, left + col + COL_GAP, y, col, mouseX, mouseY, "HUD", config.hudEnabled, v -> config.hudEnabled = v, -1, null);
				colorRow(graphics, font, left + col + COL_GAP, y, col, "Marker color", config.colorRgb, PickerTarget.NODE);
			}
			case STATUS -> {
				float y = group(graphics, font, left, top, col, "Server");
				y = readout(graphics, font, left, y, col, "Hypixel", SkyblockLocation.onHypixel);
				y = readout(graphics, font, left, y, col, "Skyblock", SkyblockLocation.inSkyblock);
				readout(graphics, font, left, y, col, "The End", SkyblockLocation.inTheEnd);

				y = group(graphics, font, left + col + COL_GAP, top, col, "Nodes");
				String area = SkyblockLocation.area.isEmpty() ? "Unknown" : SkyblockLocation.area;
				GuiDraw.menu(graphics, font, area, left + col + COL_GAP + 2, y + 2, Theme.TEXT);
				GuiDraw.menu(graphics, font, EnderNodeTracker.get().count() + " tracked", left + col + COL_GAP + 2, y + 14, Theme.MUTED);
				GuiDraw.menu(graphics, font, "Right Shift", left + col + COL_GAP + 2, y + 28, Theme.HEADER);
			}
		}
	}

	private float group(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, String title) {
		GuiDraw.small(graphics, font, title, x, y, Theme.HEADER);
		float tw = GuiDraw.smallWidth(font, title) + 5;
		GuiDraw.rounded(graphics, x + tw, y + 4, Math.max(8, w - tw), 1, 1, 0xFF243444);
		return y + 13;
	}

	private float toggle(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, int mouseX, int mouseY, String label, boolean value, Consumer<Boolean> setter, int rgb, PickerTarget colorTarget) {
		boolean hovered = GuiDraw.hovered(mouseX, mouseY, x, y, w, ROW);
		if (hovered) {
			GuiDraw.fill(graphics, x - 2, y, w + 2, ROW, 0x08FFFFFF);
		}
		float labelY = GuiDraw.middle(y, ROW);
		GuiDraw.menu(graphics, font, label, x + 1, labelY, Theme.TEXT);

		float trackW = 22;
		float trackH = 11;
		float tx = x + w - trackW;
		float ty = y + (ROW - trackH) / 2f;
		if (colorTarget != null && rgb >= 0) {
			GuiDraw.icon(graphics, font, MenuFont.PALETTE, tx - 15, labelY, 0xFF000000 | rgb);
			hits.add(new Hit(tx - 17, y + 1, 14, 14, () -> openPicker(colorTarget, rgb, tx - 18, y + 16)));
		}
		GuiDraw.pill(graphics, tx, ty, trackW, trackH, value ? Theme.ACCENT : Theme.TRACK);
		float knob = value ? tx + trackW - 6 : tx + 6;
		GuiDraw.circle(graphics, knob, ty + trackH / 2f, 4.6f, value ? Theme.TEXT : Theme.OFF);
		hits.add(new Hit(x, y, w - (colorTarget != null ? 20 : 0), ROW, () -> setter.accept(!value)));
		return y + ROW;
	}

	private float slider(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, String label, String valueText, float progress, Consumer<Float> setter) {
		float labelY = GuiDraw.middle(y, ROW);
		GuiDraw.menu(graphics, font, label, x + 1, labelY, Theme.TEXT);
		int valueWidth = GuiDraw.menuWidth(font, valueText);
		GuiDraw.menu(graphics, font, valueText, x + w - valueWidth, labelY, Theme.TEXT);
		float barX = x + GuiDraw.menuWidth(font, label) + 8;
		float barW = Math.max(24, w - GuiDraw.menuWidth(font, label) - valueWidth - 16);
		float barY = y + 7;
		float t = Mth.clamp(progress, 0f, 1f);
		GuiDraw.pill(graphics, barX, barY, barW, 3, Theme.TRACK);
		GuiDraw.pill(graphics, barX, barY, Math.max(3, barW * t), 3, Theme.ACCENT);
		GuiDraw.circle(graphics, barX + barW * t, barY + 1.5f, 3.6f, Theme.ACCENT);
		hits.add(new Hit(barX - 2, y, barW + 4, ROW, mx -> setter.accept(Mth.clamp((float) ((mx - barX) / barW), 0f, 1f)), true));
		return y + ROW;
	}

	private float colorRow(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, String label, int rgb, PickerTarget target) {
		float labelY = GuiDraw.middle(y, ROW);
		GuiDraw.menu(graphics, font, label, x + 1, labelY, Theme.TEXT);
		GuiDraw.rounded(graphics, x + w - 28, y + 3, 10, 10, 3, 0xFF000000 | rgb);
		GuiDraw.icon(graphics, font, MenuFont.PALETTE, x + w - 14, labelY, Theme.ACCENT);
		hits.add(new Hit(x + w - 32, y, 32, ROW, () -> openPicker(target, rgb, x + w - 118, y + ROW)));
		return y + ROW;
	}

	private float readout(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, String label, boolean on) {
		float labelY = GuiDraw.middle(y, ROW);
		GuiDraw.menu(graphics, font, label, x + 1, labelY, Theme.TEXT);
		GuiDraw.menu(graphics, font, on ? "ON" : "OFF", x + w - GuiDraw.menuWidth(font, on ? "ON" : "OFF"), labelY, on ? Theme.ACCENT : Theme.MUTED);
		return y + ROW;
	}

	private void drawPicker(GuiGraphicsExtractor graphics, Font font) {
		float x = pickerX;
		float y = pickerY;
		float w = 118;
		float h = 108;
		GuiDraw.panel(graphics, x, y, w, h, 8, 0xFF0B1118, Theme.ACCENT);
		GuiDraw.menu(graphics, font, "Color", x + 6, y + 4, Theme.MUTED);

		float svX = x + 6;
		float svY = y + 16;
		float svW = w - 12;
		float svH = 62;
		for (int row = 0; row < 10; row++) {
			for (int col = 0; col < 12; col++) {
				int rgb = WorldTint.hsvToRgb(pickerHue, col / 11f, 1f - row / 9f);
				GuiDraw.fill(graphics, svX + col * (svW / 12f), svY + row * (svH / 10f), svW / 12f + 0.4f, svH / 10f + 0.4f, 0xFF000000 | rgb);
			}
		}
		hits.add(new Hit(svX, svY, svW, svH, mx -> {
			pickerSat = Mth.clamp((float) ((mx - svX) / svW), 0f, 1f);
			pickerVal = Mth.clamp(1f - (float) ((lastClickY - svY) / svH), 0f, 1f);
			commitPicker();
		}, true));

		float hueY = svY + svH + 4;
		for (int i = 0; i < 24; i++) {
			int rgb = WorldTint.hsvToRgb(i * 15f, 1f, 1f);
			GuiDraw.fill(graphics, svX + i * (svW / 24f), hueY, svW / 24f + 0.4f, 6, 0xFF000000 | rgb);
		}
		hits.add(new Hit(svX, hueY, svW, 6, mx -> {
			pickerHue = Mth.clamp((float) ((mx - svX) / svW) * 360f, 0f, 359f);
			commitPicker();
		}, true));

		int current = WorldTint.hsvToRgb(pickerHue, pickerSat, pickerVal);
		GuiDraw.rounded(graphics, x + w - 14, y + 5, 8, 8, 3, 0xFF000000 | current);
		GuiDraw.menu(graphics, font, hex(current), x + 6, y + h - 12, Theme.MUTED);
	}

	private void openPicker(PickerTarget target, int rgb, float x, float y) {
		pickerTarget = target;
		dropdownOpen = false;
		float[] hsv = WorldTint.rgbToHsv(rgb);
		pickerHue = hsv[0];
		pickerSat = hsv[1];
		pickerVal = hsv[2];
		pickerX = Mth.clamp(x, windowX + SIDEBAR_W + 4, windowX + windowW - 122);
		pickerY = Mth.clamp(y, windowY + TOOLBAR_H, windowY + windowH - 112);
	}

	private void commitPicker() {
		applyColor(pickerTarget, WorldTint.hsvToRgb(pickerHue, pickerSat, pickerVal));
	}

	private void applyColor(PickerTarget target, int rgb) {
		VoidmarkConfig config = VoidmarkConfig.get();
		int packed = rgb & 0xFFFFFF;
		switch (target) {
			case WORLD -> config.worldTintRgb = packed;
			case SKY -> config.skyTintRgb = packed;
			case NODE -> config.colorRgb = packed;
		}
	}

	private void startDrag(double mx, double my) {
		dragging = true;
		dragOffX = mx - windowX;
		dragOffY = my - windowY;
	}

	private static String aspectLabel(float ratio) {
		if (Math.abs(ratio - 1.00f) < 0.02f) {
			return "Native";
		}
		if (Math.abs(ratio - 0.75f) < 0.02f) {
			return "4:3";
		}
		if (Math.abs(ratio - 0.70f) < 0.02f) {
			return "5:4";
		}
		if (Math.abs(ratio - 0.90f) < 0.02f) {
			return "16:10";
		}
		return Math.round(ratio * 100) + "%";
	}

	private static String hex(int rgb) {
		return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
	}

	private static String modVersion() {
		return FabricLoader.getInstance()
			.getModContainer("voidmark")
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("1.1.3");
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (event.button() != 0) {
			return super.mouseClicked(event, doubled);
		}
		savedFlash = false;
		lastClickY = event.y();
		dragging = false;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(event.x(), event.y())) {
				hit.click(event.x());
				return true;
			}
		}
		if (pickerTarget != null && !GuiDraw.hovered(event.x(), event.y(), pickerX, pickerY, 118, 108)) {
			pickerTarget = null;
			return true;
		}
		if (dropdownOpen) {
			dropdownOpen = false;
			return true;
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (event.button() == 0 && dragging) {
			windowX = (float) (event.x() - dragOffX);
			windowY = (float) (event.y() - dragOffY);
			return true;
		}
		if (event.button() == 0) {
			lastClickY = event.y();
			for (int i = hits.size() - 1; i >= 0; i--) {
				Hit hit = hits.get(i);
				if (hit.drag && hit.contains(event.x(), event.y())) {
					hit.click(event.x());
					return true;
				}
			}
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		VoidmarkConfig.get().save();
		return super.mouseReleased(event);
	}

	@Override
	public void onClose() {
		VoidmarkConfig.get().save();
		super.onClose();
	}

	private static final class Hit {
		final float x, y, w, h;
		final Runnable click;
		final DoubleConsumer dragClick;
		final boolean drag;

		Hit(float x, float y, float w, float h, Runnable click) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.click = click;
			this.dragClick = null;
			this.drag = false;
		}

		Hit(float x, float y, float w, float h, DoubleConsumer dragClick, boolean drag) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.click = null;
			this.dragClick = dragClick;
			this.drag = drag;
		}

		boolean contains(double mx, double my) {
			return mx >= x && mx <= x + w && my >= y && my <= y + h;
		}

		void click(double mx) {
			if (dragClick != null) {
				dragClick.accept(mx);
			} else if (click != null) {
				click.run();
			}
		}
	}
}
