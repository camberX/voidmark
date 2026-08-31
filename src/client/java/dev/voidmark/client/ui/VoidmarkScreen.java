package dev.voidmark.client.ui;

import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.location.SkyblockLocation;
import dev.voidmark.client.node.EnderNodeTracker;
import dev.voidmark.client.render.GuiDraw;
import dev.voidmark.client.visual.WorldTint;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerSkin;

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
	private static final float CARD_PAD = 8;
	private static final float CARD_HEAD = 20;
	private static final float SAVE_W = 42;
	private static final float ICON_SLOT = 14;
	private static final float PICKER_W = 132;
	private static final float PICKER_H = 122;

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
		FOG("Fog", Group.VISUALS),
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
		WORLD, SKY, FOG, NODE
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
		GuiDraw.roundRight(graphics, windowX + SIDEBAR_W + 1, windowY + 2, windowW - SIDEBAR_W, windowH, Theme.WINDOW_RADIUS, 0x66000000);
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
		GuiDraw.small(graphics, font, "v" + modVersion(), windowX + 10 + GuiDraw.titleWidth(font, "VOIDMARK") + 3, windowY + 10, Theme.ACCENT);
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

		float footY = windowY + windowH - 20;
		int face = 14;
		int faceX = Math.round(windowX + 10);
		int faceY = Math.round(footY);
		PlayerSkin skin = playerSkin();
		if (skin != null && skin.body() != null) {
			PlayerFaceExtractor.extractRenderState(graphics, skin, faceX, faceY, face);
		} else {
			GuiDraw.rounded(graphics, faceX, faceY, face, face, 3, Theme.ACCENT);
		}
		float nameX = windowX + 10 + face + 4;
		GuiDraw.menu(graphics, font, fitName(font, playerName(), (int) (SIDEBAR_W - 18 - face)), nameX, GuiDraw.middle(footY, face), Theme.TEXT);
	}

	private String playerName() {
		String name = minecraft.getGameProfile().name();
		if (name == null || name.isBlank()) {
			return "Player";
		}
		return name;
	}

	private PlayerSkin playerSkin() {
		if (minecraft.player != null) {
			return minecraft.player.getSkin();
		}
		return minecraft.getSkinManager().createLookup(minecraft.getGameProfile(), true).get();
	}

	private static String fitName(Font font, String name, int maxWidth) {
		if (GuiDraw.menuWidth(font, name) <= maxWidth) {
			return name;
		}
		String trimmed = name;
		while (trimmed.length() > 1 && GuiDraw.menuWidth(font, trimmed + "..") > maxWidth) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + "..";
	}

	private static String tabGlyph(Tab value) {
		return switch (value) {
			case WORLD -> MenuFont.GLOBE;
			case VIEW -> MenuFont.EYE;
			case FOG -> MenuFont.CLOUD;
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
		float right = left + col + COL_GAP;
		float ix = innerX(left);
		float rx = innerX(right);
		float iw = innerW(col);
		VoidmarkConfig config = VoidmarkConfig.get();

		switch (tab) {
			case WORLD -> {
				float y = featureCard(graphics, font, left, top, col, cardHeight(3), "Blocks");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "World tint", config.worldTintEnabled, v -> config.worldTintEnabled = v);
				y = colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Color", config.worldTintRgb, PickerTarget.WORLD);
				slider(graphics, font, ix, y, iw, "Strength", String.format(Locale.ROOT, "%.0f", config.worldTintStrength * 100), config.worldTintStrength, v -> config.worldTintStrength = v);

				y = featureCard(graphics, font, right, top, col, cardHeight(4), "Skybox");
				y = toggle(graphics, font, rx, y, iw, mouseX, mouseY, "Skybox tint", config.skyTintEnabled, v -> config.skyTintEnabled = v);
				y = toggle(graphics, font, rx, y, iw, mouseX, mouseY, "Match world", config.matchSkyToWorld, v -> config.matchSkyToWorld = v);
				int skyPreview = config.matchSkyToWorld ? config.worldTintRgb : config.skyTintRgb;
				y = colorRow(graphics, font, rx, y, iw, mouseX, mouseY, "Color", skyPreview, PickerTarget.SKY);
				slider(graphics, font, rx, y, iw, "Strength", String.format(Locale.ROOT, "%.0f", config.skyTintStrength * 100), config.skyTintStrength, v -> config.skyTintStrength = v);
			}
			case VIEW -> {
				float y = featureCard(graphics, font, left, top, col, cardHeight(2), "Camera");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Aspect ratio", config.aspectEnabled, v -> config.aspectEnabled = v);
				slider(graphics, font, ix, y, iw, "Aspect", aspectLabel(config.aspectRatio), (config.aspectRatio - 0.50f) / 0.70f, v -> config.aspectRatio = VoidmarkConfig.clamp(0.50f + v * 0.70f, 0.50f, 1.20f));

				y = featureCard(graphics, font, right, top, col, CARD_HEAD + 32 + CARD_PAD, "Info");
				GuiDraw.menu(graphics, font, "Below Native stretches", rx, y + 2, Theme.MUTED);
				GuiDraw.menu(graphics, font, "horizontally, like 4:3.", rx, y + 14, Theme.MUTED);
			}
			case FOG -> {
				float y = featureCard(graphics, font, left, top, col, cardHeight(3), "Main");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Custom fog", config.fogEnabled, v -> config.fogEnabled = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Match world", config.matchFogToWorld, v -> config.matchFogToWorld = v);
				int fogPreview = config.matchFogToWorld ? config.worldTintRgb : config.fogRgb;
				colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Color", fogPreview, PickerTarget.FOG);

				y = featureCard(graphics, font, right, top, col, cardHeight(3), "Distance");
				y = slider(graphics, font, rx, y, iw, "Start", String.format(Locale.ROOT, "%.0f%%", config.fogStart * 100), config.fogStart / 0.95f, v -> config.fogStart = VoidmarkConfig.clamp(v * 0.95f, 0f, 0.95f));
				y = slider(graphics, font, rx, y, iw, "End", String.format(Locale.ROOT, "%.0f%%", config.fogEnd * 100), (config.fogEnd - 0.05f) / 0.95f, v -> config.fogEnd = VoidmarkConfig.clamp(0.05f + v * 0.95f, 0.05f, 1f));
				slider(graphics, font, rx, y, iw, "Density", String.format(Locale.ROOT, "%.0f", config.fogDensity * 100), config.fogDensity, v -> config.fogDensity = v);
			}
			case MARKERS -> {
				float y = featureCard(graphics, font, left, top, col, cardHeight(4), "Main");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Enable", config.markersEnabled, v -> config.markersEnabled = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Only in The End", config.onlyInTheEnd, v -> config.onlyInTheEnd = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Force enable", config.forceEnable, v -> config.forceEnable = v);
				slider(graphics, font, ix, y, iw, "Scan radius", config.scanRadius + "m", (config.scanRadius - 16) / 64f, v -> config.scanRadius = VoidmarkConfig.clamp(16 + Math.round(v * 64f), 16, 80));

				y = featureCard(graphics, font, right, top, col, cardHeight(2), "Detection");
				y = toggle(graphics, font, rx, y, iw, mouseX, mouseY, "Block scan", config.blockScan, v -> config.blockScan = v);
				toggle(graphics, font, rx, y, iw, mouseX, mouseY, "Particle hints", config.particleDetection, v -> config.particleDetection = v);
			}
			case DISPLAY -> {
				float y = featureCard(graphics, font, left, top, col, cardHeight(5), "Esp");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Filled box", config.boxFill, v -> config.boxFill = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Outline", config.boxOutline, v -> config.boxOutline = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Tracer", config.tracersEnabled, v -> config.tracersEnabled = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Through walls", config.throughWalls, v -> config.throughWalls = v);
				slider(graphics, font, ix, y, iw, "Fill opacity", Math.round(config.fillOpacity * 100) + "%", (config.fillOpacity - 0.08f) / 0.77f, v -> config.fillOpacity = VoidmarkConfig.clamp(0.08f + v * 0.77f, 0.08f, 0.85f));

				y = featureCard(graphics, font, right, top, col, cardHeight(2), "Hud");
				y = toggle(graphics, font, rx, y, iw, mouseX, mouseY, "HUD", config.hudEnabled, v -> config.hudEnabled = v);
				colorRow(graphics, font, rx, y, iw, mouseX, mouseY, "Marker color", config.colorRgb, PickerTarget.NODE);
			}
			case STATUS -> {
				float y = featureCard(graphics, font, left, top, col, cardHeight(3), "Server");
				y = readout(graphics, font, ix, y, iw, "Hypixel", SkyblockLocation.onHypixel);
				y = readout(graphics, font, ix, y, iw, "Skyblock", SkyblockLocation.inSkyblock);
				readout(graphics, font, ix, y, iw, "The End", SkyblockLocation.inTheEnd);

				y = featureCard(graphics, font, right, top, col, CARD_HEAD + 42 + CARD_PAD, "Nodes");
				String area = SkyblockLocation.area.isEmpty() ? "Unknown" : SkyblockLocation.area;
				GuiDraw.menu(graphics, font, area, rx, y + 2, Theme.TEXT);
				GuiDraw.menu(graphics, font, EnderNodeTracker.get().count() + " tracked", rx, y + 14, Theme.MUTED);
				GuiDraw.menu(graphics, font, "Right Shift", rx, y + 28, Theme.HEADER);
			}
		}
	}

	private static float innerX(float cardX) {
		return cardX + CARD_PAD;
	}

	private static float innerW(float cardW) {
		return cardW - CARD_PAD * 2;
	}

	private static float cardHeight(int rows) {
		return CARD_HEAD + rows * ROW + CARD_PAD;
	}

	private float featureCard(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, float h, String title) {
		GuiDraw.panel(graphics, x, y, w, h, Math.min(14f, h / 2f), Theme.CARD, Theme.LINE);
		GuiDraw.small(graphics, font, title, x + CARD_PAD, y + 5, Theme.HEADER);
		GuiDraw.hline(graphics, x + CARD_PAD, y + 16, w - CARD_PAD * 2, Theme.LINE);
		return y + CARD_HEAD;
	}

	private float toggle(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, int mouseX, int mouseY, String label, boolean value, Consumer<Boolean> setter) {
		boolean hovered = GuiDraw.hovered(mouseX, mouseY, x, y, w, ROW);
		if (hovered) {
			GuiDraw.rounded(graphics, x - 3, y, w + 6, ROW, 6, 0x08FFFFFF);
		}
		float labelY = GuiDraw.middle(y, ROW);
		GuiDraw.menu(graphics, font, label, x + 1, labelY, Theme.TEXT);

		float trackW = 22;
		float trackH = 11;
		float tx = x + w - trackW;
		float ty = y + (ROW - trackH) / 2f;
		GuiDraw.pill(graphics, tx, ty, trackW, trackH, value ? Theme.ACCENT : Theme.TRACK);
		float knob = value ? tx + trackW - 6 : tx + 6;
		GuiDraw.circle(graphics, knob, ty + trackH / 2f, 4.6f, value ? Theme.TEXT : Theme.OFF);
		hits.add(new Hit(x, y, w, ROW, () -> setter.accept(!value)));
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

	private float colorRow(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, int mouseX, int mouseY, String label, int rgb, PickerTarget target) {
		float labelY = GuiDraw.middle(y, ROW);
		GuiDraw.menu(graphics, font, label, x + 1, labelY, Theme.TEXT);
		float pw = 18;
		float ph = 10;
		float px = x + w - pw;
		float py = y + (ROW - ph) / 2f;
		boolean hover = GuiDraw.hovered(mouseX, mouseY, px - 1, y, pw + 2, ROW);
		GuiDraw.rounded(graphics, px - 1, py - 1, pw + 2, ph + 2, 3, hover ? Theme.ACCENT : Theme.LINE);
		GuiDraw.rounded(graphics, px, py, pw, ph, 2, 0xFF000000 | rgb);
		hits.add(new Hit(px - 2, y, pw + 4, ROW, () -> {
			if (target == PickerTarget.SKY) {
				VoidmarkConfig.get().matchSkyToWorld = false;
			}
			if (target == PickerTarget.FOG) {
				VoidmarkConfig.get().matchFogToWorld = false;
			}
			openPicker(target, rgb, px - PICKER_W + pw, y + ROW + 2);
		}));
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
		float w = PICKER_W;
		float h = PICKER_H;
		GuiDraw.panel(graphics, x, y, w, h, 8, 0xFF0B1118, Theme.ACCENT);
		GuiDraw.menu(graphics, font, "Color", x + 6, y + 5, Theme.MUTED);

		int current = WorldTint.hsvToRgb(pickerHue, pickerSat, pickerVal);
		GuiDraw.rounded(graphics, x + w - 22, y + 5, 14, 10, 3, Theme.LINE);
		GuiDraw.rounded(graphics, x + w - 21, y + 6, 12, 8, 2, 0xFF000000 | current);

		float svX = x + 6;
		float svY = y + 20;
		float svW = w - 12;
		float svH = 68;
		GuiDraw.hsvSquare(graphics, svX, svY, svW, svH, pickerHue);
		float cursorX = svX + pickerSat * svW;
		float cursorY = svY + (1f - pickerVal) * svH;
		GuiDraw.circle(graphics, cursorX, cursorY, 3.6f, 0xFF000000);
		GuiDraw.circle(graphics, cursorX, cursorY, 2.6f, 0xFFFFFFFF);
		GuiDraw.circle(graphics, cursorX, cursorY, 1.6f, 0xFF000000 | current);
		hits.add(new Hit(svX, svY, svW, svH, mx -> {
			pickerSat = Mth.clamp((float) ((mx - svX) / svW), 0f, 1f);
			pickerVal = Mth.clamp(1f - (float) ((lastClickY - svY) / svH), 0f, 1f);
			commitPicker();
		}, true));

		float hueY = svY + svH + 4;
		float hueH = 6;
		GuiDraw.hueBar(graphics, svX, hueY, svW, hueH);
		float hueMark = svX + (pickerHue / 360f) * svW;
		GuiDraw.fill(graphics, hueMark - 1.2f, hueY - 1, 2.4f, hueH + 2, 0xFF000000);
		GuiDraw.fill(graphics, hueMark - 0.5f, hueY - 1, 1f, hueH + 2, 0xFFFFFFFF);
		hits.add(new Hit(svX, hueY, svW, hueH, mx -> {
			pickerHue = Mth.clamp((float) ((mx - svX) / svW) * 360f, 0f, 359f);
			commitPicker();
		}, true));

		GuiDraw.menu(graphics, font, hex(current), x + 6, y + h - 12, Theme.MUTED);
	}

	private void openPicker(PickerTarget target, int rgb, float x, float y) {
		pickerTarget = target;
		dropdownOpen = false;
		float[] hsv = WorldTint.rgbToHsv(rgb);
		pickerHue = hsv[0];
		pickerSat = hsv[1];
		pickerVal = hsv[2];
		pickerX = Mth.clamp(x, windowX + SIDEBAR_W + 4, windowX + windowW - PICKER_W - 4);
		pickerY = Mth.clamp(y, windowY + TOOLBAR_H, windowY + windowH - PICKER_H - 4);
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
			case FOG -> config.fogRgb = packed;
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
			.orElse("1.1.17");
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
		if (pickerTarget != null && !GuiDraw.hovered(event.x(), event.y(), pickerX, pickerY, PICKER_W, PICKER_H)) {
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
		WorldTint.syncChunkMeshes(minecraft);
		return super.mouseReleased(event);
	}

	@Override
	public void onClose() {
		VoidmarkConfig.get().save();
		WorldTint.syncChunkMeshes(minecraft);
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
