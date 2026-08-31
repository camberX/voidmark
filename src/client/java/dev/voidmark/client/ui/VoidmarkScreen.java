package dev.voidmark.client.ui;

import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.location.SkyblockLocation;
import dev.voidmark.client.node.EnderNodeTracker;
import dev.voidmark.client.render.GuiDraw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class VoidmarkScreen extends Screen {
	private static final int[] SWATCHES = {
		0xC084FC, 0xE879F9, 0x38BDF8, 0x34D399, 0xFBBF24, 0xFB7185
	};

	private enum Tab {
		NODES("Nodes"),
		DISPLAY("Display"),
		STATUS("Status");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private Tab tab = Tab.NODES;
	private float panelX;
	private float panelY;
	private final float panelW = 680;
	private final float panelH = 412;
	private final float sidebar = 188;

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
		Font font = minecraft.font;
		panelX = (width - panelW) / 2f;
		panelY = (height - panelH) / 2f;

		GuiDraw.fillGradient(graphics, 0, 0, width, height, 0xCC05050A, 0xE0000000);
		GuiDraw.fill(graphics, panelX, panelY, panelW, panelH, 0xF312121A);
		GuiDraw.border(graphics, panelX, panelY, panelW, panelH, 0x22FFFFFF, 1);
		GuiDraw.fill(graphics, panelX, panelY, 3, panelH, accent());

		drawSidebar(graphics, font, mouseX, mouseY);
		drawHeader(graphics, font);
		drawBody(graphics, font, mouseX, mouseY);
	}

	private void drawSidebar(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		GuiDraw.fill(graphics, panelX, panelY, sidebar, panelH, 0xFF0B0B10);
		GuiDraw.fill(graphics, panelX + sidebar, panelY, 1, panelH, 0x14FFFFFF);

		GuiDraw.text(graphics, font, "VOIDMARK", panelX + 22, panelY + 26, 1.15f, 0xFFF8F8FF, false);
		GuiDraw.text(graphics, font, "Skyblock  ·  26.1.2", panelX + 22, panelY + 44, 0xFF73738C, false);
		GuiDraw.fill(graphics, panelX + 22, panelY + 64, 48, 2, accent());

		float y = panelY + 92;
		for (Tab value : Tab.values()) {
			boolean active = tab == value;
			boolean hovered = GuiDraw.hovered(mouseX, mouseY, panelX + 14, y, sidebar - 28, 32);
			int bg = active ? 0x3324D0C8 : hovered ? 0x18FFFFFF : 0x00000000;
			GuiDraw.fill(graphics, panelX + 14, y, sidebar - 28, 32, bg);
			if (active) {
				GuiDraw.fill(graphics, panelX + 14, y + 6, 2, 20, accent());
			}
			int color = active ? 0xFFF4F4F5 : 0xFFA1A1AA;
			GuiDraw.text(graphics, font, value.label, panelX + 28, y + 11, color, false);
			y += 40;
		}

		GuiDraw.text(graphics, font, "/voidmark  ·  RSHIFT", panelX + 22, panelY + panelH - 28, 0xFF52525B, false);
	}

	private void drawHeader(GuiGraphicsExtractor graphics, Font font) {
		float x = panelX + sidebar + 28;
		GuiDraw.text(graphics, font, tab.label, x, panelY + 24, 1.2f, 0xFFF8F8FF, false);
		String subtitle = switch (tab) {
			case NODES -> "Highlight Ender Nodes on the End Island.";
			case DISPLAY -> "HUD, tracers, and marker color.";
			case STATUS -> "Live Skyblock location and scan state.";
		};
		GuiDraw.text(graphics, font, subtitle, x, panelY + 44, 0xFF8B8B9C, false);
	}

	private void drawBody(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float x = panelX + sidebar + 28;
		float y = panelY + 78;
		float w = panelW - sidebar - 56;

		VoidmarkConfig config = VoidmarkConfig.get();
		switch (tab) {
			case NODES -> {
				y = toggle(graphics, font, mouseX, mouseY, x, y, w, "Enable markers", "Draw boxes on magenta terracotta nodes.", config.markersEnabled);
				y = toggle(graphics, font, mouseX, mouseY, x, y, w, "Only in The End", "Stay quiet outside the End Island.", config.onlyInTheEnd);
				y = toggle(graphics, font, mouseX, mouseY, x, y, w, "Scan nearby blocks", "Walk the loaded island for purple clay.", config.blockScan);
				y = toggle(graphics, font, mouseX, mouseY, x, y, w, "Particle hints", "Catch portal dust Hypixel emits from nodes.", config.particleDetection);
				y = toggle(graphics, font, mouseX, mouseY, x, y, w, "Force enable", "Use in singleplayer to test with terracotta.", config.forceEnable);
				slider(graphics, font, mouseX, mouseY, x, y, w, "Scan radius", config.scanRadius + "m", (config.scanRadius - 16) / 64f);
			}
			case DISPLAY -> {
				y = toggle(graphics, font, mouseX, mouseY, x, y, w, "HUD", "Node count and nearest heading.", config.hudEnabled);
				y = toggle(graphics, font, mouseX, mouseY, x, y, w, "Filled box", "Translucent volume over each node.", config.boxFill);
				y = toggle(graphics, font, mouseX, mouseY, x, y, w, "Outline", "Wireframe around each node.", config.boxOutline);
				y = toggle(graphics, font, mouseX, mouseY, x, y, w, "Tracer", "Line to the closest node.", config.tracersEnabled);
				y = toggle(graphics, font, mouseX, mouseY, x, y, w, "Through walls", "Keep markers visible behind terrain.", config.throughWalls);
				y = slider(graphics, font, mouseX, mouseY, x, y, w, "Fill opacity", Math.round(config.fillOpacity * 100) + "%", config.fillOpacity);
				swatches(graphics, font, mouseX, mouseY, x, y, config.colorRgb);
			}
			case STATUS -> drawStatus(graphics, font, x, y, w);
		}
	}

	private void drawStatus(GuiGraphicsExtractor graphics, Font font, float x, float y, float w) {
		statusRow(graphics, font, x, y, w, "Hypixel", SkyblockLocation.onHypixel);
		statusRow(graphics, font, x, y + 36, w, "Skyblock", SkyblockLocation.inSkyblock);
		statusRow(graphics, font, x, y + 72, w, "The End", SkyblockLocation.inTheEnd);
		GuiDraw.fill(graphics, x, y + 118, w, 64, 0xFF16161F);
		GuiDraw.text(graphics, font, "Area", x + 16, y + 130, 0xFF73738C, false);
		String area = SkyblockLocation.area.isEmpty() ? "Unknown" : SkyblockLocation.area;
		GuiDraw.text(graphics, font, area, x + 16, y + 146, 0xFFF4F4F5, false);
		GuiDraw.text(graphics, font, EnderNodeTracker.get().count() + " nodes tracked", x + 16, y + 162, 0xFFA1A1AA, false);
	}

	private void statusRow(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, String label, boolean on) {
		GuiDraw.fill(graphics, x, y, w, 32, 0xFF16161F);
		GuiDraw.fill(graphics, x, y, 3, 32, on ? 0xFF34D399 : 0xFF52525B);
		GuiDraw.text(graphics, font, label, x + 16, y + 11, 0xFFF4F4F5, false);
		GuiDraw.text(graphics, font, on ? "YES" : "NO", x + w - 40, y + 11, on ? 0xFF34D399 : 0xFF71717A, false);
	}

	private float toggle(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, float w, String title, String hint, boolean value) {
		boolean hovered = GuiDraw.hovered(mouseX, mouseY, x, y, w, 38);
		GuiDraw.fill(graphics, x, y, w, 38, hovered ? 0xFF1A1A24 : 0xFF15151C);
		GuiDraw.text(graphics, font, title, x + 14, y + 7, 0xFFF4F4F5, false);
		GuiDraw.text(graphics, font, hint, x + 14, y + 21, 0xFF71717A, false);

		float sx = x + w - 44;
		float sy = y + 14;
		GuiDraw.fill(graphics, sx, sy, 28, 12, value ? withAlpha(accent(), 180) : 0xFF3F3F46);
		float knob = value ? sx + 16 : sx + 2;
		GuiDraw.fill(graphics, knob, sy - 2, 10, 16, value ? accent() : 0xFFD4D4D8);
		return y + 44;
	}

	private float slider(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, float w, String title, String value, float progress) {
		boolean hovered = GuiDraw.hovered(mouseX, mouseY, x, y, w, 42);
		GuiDraw.fill(graphics, x, y, w, 42, hovered ? 0xFF1A1A24 : 0xFF15151C);
		GuiDraw.text(graphics, font, title, x + 14, y + 8, 0xFFF4F4F5, false);
		GuiDraw.text(graphics, font, value, x + w - 14 - font.width(value), y + 8, 0xFFA1A1AA, false);
		float barX = x + 14;
		float barW = w - 28;
		GuiDraw.fill(graphics, barX, y + 28, barW, 3, 0xFF3F3F46);
		GuiDraw.fill(graphics, barX, y + 28, barW * Mth.clamp(progress, 0f, 1f), 3, accent());
		return y + 50;
	}

	private void swatches(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, int selected) {
		GuiDraw.fill(graphics, x, y, panelW - sidebar - 56, 54, 0xFF15151C);
		GuiDraw.text(graphics, font, "Marker color", x + 14, y + 8, 0xFFF4F4F5, false);
		float sx = x + 14;
		float sy = y + 26;
		for (int swatch : SWATCHES) {
			boolean active = swatch == selected;
			GuiDraw.fill(graphics, sx, sy, 18, 18, 0xFF000000 | swatch);
			if (active) {
				GuiDraw.border(graphics, sx - 2, sy - 2, 22, 22, 0xFFFFFFFF, 1);
			}
			sx += 26;
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (event.button() != 0) {
			return super.mouseClicked(event, doubled);
		}

		double mouseX = event.x();
		double mouseY = event.y();
		float y = panelY + 92;
		for (Tab value : Tab.values()) {
			if (GuiDraw.hovered(mouseX, mouseY, panelX + 14, y, sidebar - 28, 32)) {
				tab = value;
				return true;
			}
			y += 40;
		}

		VoidmarkConfig config = VoidmarkConfig.get();
		float x = panelX + sidebar + 28;
		float w = panelW - sidebar - 56;
		float bodyY = panelY + 78;

		switch (tab) {
			case NODES -> {
				if (clickToggle(mouseX, mouseY, x, bodyY, w)) {
					config.markersEnabled = !config.markersEnabled;
					return true;
				}
				bodyY += 44;
				if (clickToggle(mouseX, mouseY, x, bodyY, w)) {
					config.onlyInTheEnd = !config.onlyInTheEnd;
					return true;
				}
				bodyY += 44;
				if (clickToggle(mouseX, mouseY, x, bodyY, w)) {
					config.blockScan = !config.blockScan;
					return true;
				}
				bodyY += 44;
				if (clickToggle(mouseX, mouseY, x, bodyY, w)) {
					config.particleDetection = !config.particleDetection;
					return true;
				}
				bodyY += 44;
				if (clickToggle(mouseX, mouseY, x, bodyY, w)) {
					config.forceEnable = !config.forceEnable;
					return true;
				}
				bodyY += 44;
				if (GuiDraw.hovered(mouseX, mouseY, x, bodyY, w, 42)) {
					float progress = (float) ((mouseX - (x + 14)) / (w - 28));
					config.scanRadius = VoidmarkConfig.clamp(16 + Math.round(progress * 64), 16, 80);
					return true;
				}
			}
			case DISPLAY -> {
				if (clickToggle(mouseX, mouseY, x, bodyY, w)) {
					config.hudEnabled = !config.hudEnabled;
					return true;
				}
				bodyY += 44;
				if (clickToggle(mouseX, mouseY, x, bodyY, w)) {
					config.boxFill = !config.boxFill;
					return true;
				}
				bodyY += 44;
				if (clickToggle(mouseX, mouseY, x, bodyY, w)) {
					config.boxOutline = !config.boxOutline;
					return true;
				}
				bodyY += 44;
				if (clickToggle(mouseX, mouseY, x, bodyY, w)) {
					config.tracersEnabled = !config.tracersEnabled;
					return true;
				}
				bodyY += 44;
				if (clickToggle(mouseX, mouseY, x, bodyY, w)) {
					config.throughWalls = !config.throughWalls;
					return true;
				}
				bodyY += 44;
				if (GuiDraw.hovered(mouseX, mouseY, x, bodyY, w, 42)) {
					float progress = (float) ((mouseX - (x + 14)) / (w - 28));
					config.fillOpacity = VoidmarkConfig.clamp(progress, 0.08f, 0.85f);
					return true;
				}
				bodyY += 50;
				float sx = x + 14;
				float sy = bodyY + 26;
				for (int swatch : SWATCHES) {
					if (GuiDraw.hovered(mouseX, mouseY, sx, sy, 18, 18)) {
						config.colorRgb = swatch;
						return true;
					}
					sx += 26;
				}
			}
			case STATUS -> {
			}
		}

		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (event.button() == 0) {
			mouseClicked(event, false);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		VoidmarkConfig.get().save();
		super.onClose();
	}

	private boolean clickToggle(double mouseX, double mouseY, float x, float y, float w) {
		return GuiDraw.hovered(mouseX, mouseY, x, y, w, 38);
	}

	private int accent() {
		return 0xFF000000 | (VoidmarkConfig.get().colorRgb & 0xFFFFFF);
	}

	private static int withAlpha(int color, int alpha) {
		return (alpha << 24) | (color & 0xFFFFFF);
	}
}
