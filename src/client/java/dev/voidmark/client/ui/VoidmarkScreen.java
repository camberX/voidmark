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

	private static final int[] SWATCHES = {
		0x2FB5FF, 0x5B8CFF, 0xA78BFA, 0x34D399, 0xFBBF24, 0xFB7185, 0xFFFFFF, 0x111827
	};

	private final List<Hit> hits = new ArrayList<>();
	private Tab tab = Tab.WORLD;
	private PickerTarget pickerTarget;
	private float pickerHue = 200f;
	private float pickerSat = 0.82f;
	private float pickerVal = 1f;
	private double lastClickY;

	private float windowX;
	private float windowY;
	private float windowW = 940;
	private float windowH = 540;
	private float sidebar = 168;
	private float preview = 214;

	public VoidmarkScreen() {
		super(Component.literal("Voidmark"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		extractTransparentBackground(graphics);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		hits.clear();
		Font font = minecraft.font;
		layout();

		GuiDraw.fill(graphics, 0, 0, width, height, 0xB0000000);
		GuiDraw.rounded(graphics, windowX, windowY, windowW, windowH, Theme.WINDOW);
		GuiDraw.border(graphics, windowX, windowY, windowW, windowH, 0xFF15202C, 1);

		drawSidebar(graphics, font, mouseX, mouseY);
		drawHeader(graphics, font);
		drawColumns(graphics, font, mouseX, mouseY);
		drawPreview(graphics, font);
		if (pickerTarget != null) {
			drawPicker(graphics, font, mouseX, mouseY);
		}
	}

	private void layout() {
		windowW = Math.min(960, Math.max(720, width - 24));
		windowH = Math.min(560, Math.max(420, height - 24));
		windowX = (width - windowW) / 2f;
		windowY = (height - windowH) / 2f;
		sidebar = Math.max(154, Math.min(178, windowW * 0.18f));
		preview = Math.max(188, Math.min(230, windowW * 0.23f));
	}

	private void drawSidebar(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		GuiDraw.fill(graphics, windowX, windowY, sidebar, windowH, Theme.SIDEBAR);
		GuiDraw.fill(graphics, windowX + sidebar, windowY, 1, windowH, Theme.LINE);

		GuiDraw.text(graphics, font, "VOIDMARK", windowX + 18, windowY + 18, 1.12f, Theme.TEXT, false);
		GuiDraw.text(graphics, font, "skyblock", windowX + 18, windowY + 34, Theme.MUTED, false);
		GuiDraw.fill(graphics, windowX + 18, windowY + 52, 34, 2, Theme.ACCENT);

		float y = windowY + 68;
		Group last = null;
		for (Tab value : Tab.values()) {
			if (value.group != last) {
				y += 8;
				GuiDraw.text(graphics, font, value.group.label, windowX + 18, y, Theme.HEADER, false);
				y += 16;
				last = value.group;
			}

			boolean active = tab == value;
			boolean hovered = GuiDraw.hovered(mouseX, mouseY, windowX + 8, y, sidebar - 16, 26);
			if (active) {
				GuiDraw.rounded(graphics, windowX + 8, y, sidebar - 16, 26, Theme.withAlpha(Theme.ACCENT, 28));
				GuiDraw.fill(graphics, windowX + 8, y + 6, 2, 14, Theme.ACCENT);
			} else if (hovered) {
				GuiDraw.rounded(graphics, windowX + 8, y, sidebar - 16, 26, 0x12FFFFFF);
			}
			drawTabIcon(graphics, value, windowX + 22, y + 8, active ? Theme.ACCENT : Theme.MUTED);
			GuiDraw.text(graphics, font, value.label, windowX + 38, y + 8, active ? Theme.ACCENT : Theme.TEXT, false);
			hits.add(new Hit(windowX + 8, y, sidebar - 16, 26, () -> {
				tab = value;
				pickerTarget = null;
			}));
			y += 28;
		}

		GuiDraw.hline(graphics, windowX + 16, windowY + windowH - 44, sidebar - 32, Theme.LINE);
		GuiDraw.text(graphics, font, "VOIDMARK", windowX + 18, windowY + windowH - 32, Theme.TEXT, false);
		GuiDraw.text(graphics, font, "v" + modVersion(), windowX + 18, windowY + windowH - 20, Theme.ACCENT, false);
	}

	private void drawTabIcon(GuiGraphicsExtractor graphics, Tab value, float x, float y, int color) {
		switch (value) {
			case WORLD -> GuiDraw.iconWorld(graphics, x, y, color);
			case VIEW -> GuiDraw.iconView(graphics, x, y, color);
			case MARKERS -> GuiDraw.iconBox(graphics, x, y, color);
			case DISPLAY -> GuiDraw.iconHud(graphics, x, y, color);
			case STATUS -> GuiDraw.iconStatus(graphics, x, y, color);
		}
	}

	private void drawHeader(GuiGraphicsExtractor graphics, Font font) {
		float x = windowX + sidebar + 22;
		GuiDraw.text(graphics, font, tab.label, x, windowY + 16, 1.15f, Theme.TEXT, false);
		GuiDraw.text(graphics, font, headerHint(), x, windowY + 34, Theme.MUTED, false);
		GuiDraw.hline(graphics, x, windowY + 54, windowW - sidebar - preview - 40, Theme.LINE);
	}

	private String headerHint() {
		return switch (tab) {
			case WORLD -> "World and skybox color, the CS-client look.";
			case VIEW -> "Stretched aspect ratio, same idea as 4:3 on 16:9.";
			case MARKERS -> "Ender Node scan and Hypixel End Island filter.";
			case DISPLAY -> "Boxes, tracers, HUD, marker color.";
			case STATUS -> "Live Skyblock location and tracked nodes.";
		};
	}

	private void drawColumns(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float left = windowX + sidebar + 22;
		float top = windowY + 66;
		float colW = (windowW - sidebar - preview - 56) / 2f;
		float gap = 14;
		VoidmarkConfig config = VoidmarkConfig.get();

		switch (tab) {
			case WORLD -> {
				float y = section(graphics, font, left, top, colW, "Main");
				y = toggle(graphics, font, left, y, colW, mouseX, mouseY, "World tint", config.worldTintEnabled, v -> config.worldTintEnabled = v);
				y = slider(graphics, font, left, y, colW, mouseX, mouseY, "Strength", String.format(Locale.ROOT, "%.0f", config.worldTintStrength * 100), config.worldTintStrength, v -> config.worldTintStrength = v);
				colorRow(graphics, font, left, y, colW, mouseX, mouseY, "Tint color", config.worldTintRgb, PickerTarget.WORLD);

				y = section(graphics, font, left + colW + gap, top, colW, "Skybox");
				y = toggle(graphics, font, left + colW + gap, y, colW, mouseX, mouseY, "Skybox tint", config.skyTintEnabled, v -> config.skyTintEnabled = v);
				y = toggle(graphics, font, left + colW + gap, y, colW, mouseX, mouseY, "Match world color", config.matchSkyToWorld, v -> config.matchSkyToWorld = v);
				y = slider(graphics, font, left + colW + gap, y, colW, mouseX, mouseY, "Strength", String.format(Locale.ROOT, "%.0f", config.skyTintStrength * 100), config.skyTintStrength, v -> config.skyTintStrength = v);
				if (!config.matchSkyToWorld) {
					colorRow(graphics, font, left + colW + gap, y, colW, mouseX, mouseY, "Sky color", config.skyTintRgb, PickerTarget.SKY);
				}
			}
			case VIEW -> {
				float y = section(graphics, font, left, top, colW, "Camera");
				y = toggle(graphics, font, left, y, colW, mouseX, mouseY, "Aspect ratio", config.aspectEnabled, v -> config.aspectEnabled = v);
				y = slider(graphics, font, left, y, colW, mouseX, mouseY, "Aspect", aspectLabel(config.aspectRatio), (config.aspectRatio - 0.50f) / 0.70f, v -> config.aspectRatio = VoidmarkConfig.clamp(0.50f + v * 0.70f, 0.50f, 1.20f));
				presetRow(graphics, font, left, y, colW, mouseX, mouseY);

				y = section(graphics, font, left + colW + gap, top, colW, "Presets");
				GuiDraw.text(graphics, font, "Lower than Native stretches the world", left + colW + gap + 12, y + 10, Theme.MUTED, false);
				GuiDraw.text(graphics, font, "horizontally, like 4:3 on a 16:9 panel.", left + colW + gap + 12, y + 24, Theme.MUTED, false);
				GuiDraw.text(graphics, font, "This is view-only. It does not change hitboxes.", left + colW + gap + 12, y + 46, Theme.MUTED, false);
			}
			case MARKERS -> {
				float y = section(graphics, font, left, top, colW, "Main");
				y = toggle(graphics, font, left, y, colW, mouseX, mouseY, "Enable markers", config.markersEnabled, v -> config.markersEnabled = v);
				y = toggle(graphics, font, left, y, colW, mouseX, mouseY, "Only in The End", config.onlyInTheEnd, v -> config.onlyInTheEnd = v);
				y = toggle(graphics, font, left, y, colW, mouseX, mouseY, "Force enable", config.forceEnable, v -> config.forceEnable = v);
				slider(graphics, font, left, y, colW, mouseX, mouseY, "Scan radius", config.scanRadius + "m", (config.scanRadius - 16) / 64f, v -> config.scanRadius = VoidmarkConfig.clamp(16 + Math.round(v * 64f), 16, 80));

				y = section(graphics, font, left + colW + gap, top, colW, "Detection");
				y = toggle(graphics, font, left + colW + gap, y, colW, mouseX, mouseY, "Block scan", config.blockScan, v -> config.blockScan = v);
				toggle(graphics, font, left + colW + gap, y, colW, mouseX, mouseY, "Particle hints", config.particleDetection, v -> config.particleDetection = v);
			}
			case DISPLAY -> {
				float y = section(graphics, font, left, top, colW, "Esp");
				y = toggle(graphics, font, left, y, colW, mouseX, mouseY, "Filled box", config.boxFill, v -> config.boxFill = v);
				y = toggle(graphics, font, left, y, colW, mouseX, mouseY, "Outline", config.boxOutline, v -> config.boxOutline = v);
				y = toggle(graphics, font, left, y, colW, mouseX, mouseY, "Tracer", config.tracersEnabled, v -> config.tracersEnabled = v);
				y = toggle(graphics, font, left, y, colW, mouseX, mouseY, "Through walls", config.throughWalls, v -> config.throughWalls = v);
				slider(graphics, font, left, y, colW, mouseX, mouseY, "Fill opacity", Math.round(config.fillOpacity * 100) + "%", (config.fillOpacity - 0.08f) / 0.77f, v -> config.fillOpacity = VoidmarkConfig.clamp(0.08f + v * 0.77f, 0.08f, 0.85f));

				y = section(graphics, font, left + colW + gap, top, colW, "Hud");
				y = toggle(graphics, font, left + colW + gap, y, colW, mouseX, mouseY, "HUD", config.hudEnabled, v -> config.hudEnabled = v);
				colorRow(graphics, font, left + colW + gap, y, colW, mouseX, mouseY, "Marker color", config.colorRgb, PickerTarget.NODE);
			}
			case STATUS -> drawStatus(graphics, font, left, top, colW * 2 + gap);
		}
	}

	private void drawStatus(GuiGraphicsExtractor graphics, Font font, float x, float y, float w) {
		statusChip(graphics, font, x, y, w, "Hypixel", SkyblockLocation.onHypixel);
		statusChip(graphics, font, x, y + 36, w, "Skyblock", SkyblockLocation.inSkyblock);
		statusChip(graphics, font, x, y + 72, w, "The End", SkyblockLocation.inTheEnd);
		GuiDraw.rounded(graphics, x, y + 118, w, 78, Theme.CARD);
		GuiDraw.text(graphics, font, "Area", x + 14, y + 130, Theme.MUTED, false);
		String area = SkyblockLocation.area.isEmpty() ? "Unknown" : SkyblockLocation.area;
		GuiDraw.text(graphics, font, area, x + 14, y + 146, Theme.TEXT, false);
		GuiDraw.text(graphics, font, EnderNodeTracker.get().count() + " nodes tracked", x + 14, y + 164, Theme.MUTED, false);
		GuiDraw.text(graphics, font, "/voidmark  ·  Right Shift", x + 14, y + 180, Theme.HEADER, false);
	}

	private void statusChip(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, String label, boolean on) {
		GuiDraw.rounded(graphics, x, y, w, 32, Theme.CARD);
		GuiDraw.fill(graphics, x, y + 4, 3, 24, on ? Theme.ACCENT : Theme.OFF);
		GuiDraw.text(graphics, font, label, x + 16, y + 11, Theme.TEXT, false);
		GuiDraw.text(graphics, font, on ? "ON" : "OFF", x + w - 40, y + 11, on ? Theme.ACCENT : Theme.MUTED, false);
	}

	private void drawPreview(GuiGraphicsExtractor graphics, Font font) {
		float x = windowX + windowW - preview;
		float y = windowY;
		GuiDraw.fill(graphics, x, y, preview, windowH, 0xFF060A0E);
		GuiDraw.fill(graphics, x, y, 1, windowH, Theme.LINE);
		GuiDraw.text(graphics, font, previewTitle(), x + 16, y + 16, Theme.MUTED, false);

		VoidmarkConfig config = VoidmarkConfig.get();
		float boxX = x + 16;
		float boxY = y + 40;
		float boxW = preview - 32;
		float boxH = windowH - 86;

		GuiDraw.rounded(graphics, boxX, boxY, boxW, boxH, 0xFF0A1016);
		GuiDraw.border(graphics, boxX, boxY, boxW, boxH, Theme.LINE, 1);

		int sky = 0xFF000000 | (config.matchSkyToWorld ? config.worldTintRgb : config.skyTintRgb);
		int world = 0xFF000000 | config.worldTintRgb;
		if (tab == Tab.WORLD || tab == Tab.VIEW) {
			float skyH = boxH * 0.42f;
			GuiDraw.fillGradient(graphics, boxX + 8, boxY + 8, boxW - 16, skyH, Theme.withAlpha(sky, 230), Theme.withAlpha(world, 70));
			GuiDraw.fill(graphics, boxX + 8, boxY + 8 + skyH, boxW - 16, boxH * 0.38f, Theme.withAlpha(world, Math.round(50 + config.worldTintStrength * 140)));
			float stretch = tab == Tab.VIEW && config.aspectEnabled ? config.aspectRatio : 1f;
			GuiDraw.figure(graphics, boxX + 8, boxY + 36, boxW - 16, boxH - 70, stretch, Theme.ACCENT, 0xFF1C2A36);
			GuiDraw.text(graphics, font, tab == Tab.VIEW ? aspectLabel(config.aspectRatio) : hex(config.worldTintRgb), boxX + 14, boxY + boxH - 20, Theme.MUTED, false);
		} else {
			int count = EnderNodeTracker.get().count();
			GuiDraw.text(graphics, font, String.valueOf(count), boxX + 16, boxY + 28, 2.1f, Theme.ACCENT, false);
			GuiDraw.text(graphics, font, "ender nodes", boxX + 16, boxY + 70, Theme.TEXT, false);
			GuiDraw.text(graphics, font, SkyblockLocation.inTheEnd ? "The End" : (SkyblockLocation.inSkyblock ? "Skyblock" : "not in skyblock"), boxX + 16, boxY + 88, Theme.MUTED, false);
			for (int i = 0; i < 3; i++) {
				GuiDraw.border(graphics, boxX + 24 + i * 18, boxY + 130 + i * 12, 36, 36, Theme.withAlpha(0xFF000000 | config.colorRgb, 200 - i * 50), 1);
			}
		}
	}

	private String previewTitle() {
		return switch (tab) {
			case WORLD -> "WORLD COLOR";
			case VIEW -> "ASPECT";
			default -> "NODES";
		};
	}

	private float section(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, String title) {
		GuiDraw.text(graphics, font, title, x + 2, y, Theme.HEADER, false);
		GuiDraw.hline(graphics, x, y + 14, w, Theme.LINE);
		return y + 18;
	}

	private float toggle(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, int mouseX, int mouseY, String label, boolean value, Consumer<Boolean> setter) {
		boolean hovered = GuiDraw.hovered(mouseX, mouseY, x, y, w, 28);
		if (hovered) {
			GuiDraw.fill(graphics, x, y, w, 28, 0x0AFFFFFF);
		}
		GuiDraw.text(graphics, font, label, x + 10, y + 8, Theme.TEXT, false);

		float trackW = 32;
		float trackH = 14;
		float tx = x + w - trackW - 10;
		float ty = y + 7;
		GuiDraw.pill(graphics, tx, ty, trackW, trackH, value ? Theme.ACCENT : Theme.TRACK);
		float knob = value ? tx + trackW - 8 : tx + 8;
		GuiDraw.knob(graphics, knob, ty + trackH / 2f, 6.4f, value ? Theme.TEXT : Theme.OFF);
		hits.add(new Hit(x, y, w, 28, () -> setter.accept(!value)));
		GuiDraw.hline(graphics, x, y + 28, w, Theme.LINE);
		return y + 30;
	}

	private float slider(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, int mouseX, int mouseY, String label, String valueText, float progress, Consumer<Float> setter) {
		GuiDraw.text(graphics, font, label, x + 10, y + 6, Theme.TEXT, false);
		GuiDraw.text(graphics, font, valueText, x + w - 12 - font.width(valueText), y + 6, Theme.ACCENT, false);
		float barX = x + 10;
		float barY = y + 24;
		float barW = w - 20;
		float t = Mth.clamp(progress, 0f, 1f);
		GuiDraw.fill(graphics, barX, barY, barW, 2, Theme.TRACK);
		GuiDraw.fill(graphics, barX, barY, barW * t, 2, Theme.ACCENT);
		GuiDraw.knob(graphics, barX + barW * t, barY + 1, 4.6f, Theme.TEXT);
		hits.add(new Hit(x, y, w, 34, mx -> setter.accept(Mth.clamp((float) ((mx - barX) / barW), 0f, 1f)), true));
		GuiDraw.hline(graphics, x, y + 34, w, Theme.LINE);
		return y + 36;
	}

	private void colorRow(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, int mouseX, int mouseY, String label, int rgb, PickerTarget target) {
		GuiDraw.text(graphics, font, label, x + 10, y + 6, Theme.TEXT, false);
		GuiDraw.text(graphics, font, hex(rgb), x + w - 54 - font.width(hex(rgb)), y + 6, Theme.MUTED, false);
		GuiDraw.fill(graphics, x + w - 48, y + 4, 16, 16, 0xFF000000 | rgb);
		GuiDraw.border(graphics, x + w - 48, y + 4, 16, 16, Theme.ACCENT, 1);
		GuiDraw.palette(graphics, x + w - 28, y + 3, rgb);
		hits.add(new Hit(x + w - 52, y + 2, 42, 22, () -> openPicker(target, rgb)));

		float sx = x + 10;
		float sy = y + 26;
		for (int swatch : SWATCHES) {
			boolean active = swatch == (rgb & 0xFFFFFF);
			GuiDraw.fill(graphics, sx, sy, 14, 14, 0xFF000000 | swatch);
			if (active) {
				GuiDraw.border(graphics, sx - 1, sy - 1, 16, 16, Theme.ACCENT, 1);
			}
			int captured = swatch;
			hits.add(new Hit(sx, sy, 14, 14, () -> applyColor(target, captured)));
			sx += 18;
		}
		GuiDraw.hline(graphics, x, y + 46, w, Theme.LINE);
	}

	private void presetRow(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, int mouseX, int mouseY) {
		String[] names = {"Native", "16:10", "4:3", "5:4"};
		float[] values = {1.00f, 0.90f, 0.75f, 0.70f};
		float px = x + 10;
		for (int i = 0; i < names.length; i++) {
			boolean active = Math.abs(VoidmarkConfig.get().aspectRatio - values[i]) < 0.02f;
			float bw = 52;
			GuiDraw.rounded(graphics, px, y + 8, bw, 20, active ? Theme.withAlpha(Theme.ACCENT, 40) : Theme.CARD);
			GuiDraw.text(graphics, font, names[i], px + 7, y + 13, active ? Theme.ACCENT : Theme.MUTED, false);
			float captured = values[i];
			hits.add(new Hit(px, y + 8, bw, 20, () -> {
				VoidmarkConfig config = VoidmarkConfig.get();
				config.aspectEnabled = true;
				config.aspectRatio = captured;
			}));
			px += 58;
		}
	}

	private void drawPicker(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float x = windowX + windowW - preview + 10;
		float y = windowY + windowH - 176;
		float w = preview - 20;
		float h = 158;
		GuiDraw.fill(graphics, x, y, w, h, 0xFF0B1118);
		GuiDraw.border(graphics, x, y, w, h, Theme.ACCENT, 1);
		GuiDraw.text(graphics, font, "Color", x + 8, y + 6, Theme.MUTED, false);

		float svX = x + 8;
		float svY = y + 22;
		float svW = w - 16;
		float svH = 90;
		for (int row = 0; row < 12; row++) {
			for (int col = 0; col < 14; col++) {
				float sat = col / 13f;
				float val = 1f - row / 11f;
				int rgb = WorldTint.hsvToRgb(pickerHue, sat, val);
				GuiDraw.fill(graphics, svX + col * (svW / 14f), svY + row * (svH / 12f), svW / 14f + 0.4f, svH / 12f + 0.4f, 0xFF000000 | rgb);
			}
		}
		hits.add(new Hit(svX, svY, svW, svH, mx -> {
			pickerSat = Mth.clamp((float) ((mx - svX) / svW), 0f, 1f);
			pickerVal = Mth.clamp(1f - (float) ((lastClickY - svY) / svH), 0f, 1f);
			commitPicker();
		}, true));

		float hueY = svY + svH + 8;
		for (int i = 0; i < 32; i++) {
			int rgb = WorldTint.hsvToRgb(i * (360f / 32f), 1f, 1f);
			GuiDraw.fill(graphics, svX + i * (svW / 32f), hueY, svW / 32f + 0.4f, 8, 0xFF000000 | rgb);
		}
		hits.add(new Hit(svX, hueY, svW, 8, mx -> {
			pickerHue = Mth.clamp((float) ((mx - svX) / svW) * 360f, 0f, 359f);
			commitPicker();
		}, true));

		int current = WorldTint.hsvToRgb(pickerHue, pickerSat, pickerVal);
		GuiDraw.fill(graphics, x + w - 18, y + 8, 10, 10, 0xFF000000 | current);
		GuiDraw.text(graphics, font, hex(current), x + 8, y + h - 16, Theme.MUTED, false);
	}

	private void openPicker(PickerTarget target, int rgb) {
		pickerTarget = target;
		float[] hsv = WorldTint.rgbToHsv(rgb);
		pickerHue = hsv[0];
		pickerSat = hsv[1];
		pickerVal = hsv[2];
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
			.orElse("1.1.0");
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (event.button() != 0) {
			return super.mouseClicked(event, doubled);
		}
		lastClickY = event.y();
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(event.x(), event.y())) {
				hit.click(event.x());
				return true;
			}
		}
		if (pickerTarget != null && !GuiDraw.hovered(event.x(), event.y(), windowX + windowW - preview + 10, windowY + windowH - 176, preview - 20, 158)) {
			pickerTarget = null;
			return true;
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
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
