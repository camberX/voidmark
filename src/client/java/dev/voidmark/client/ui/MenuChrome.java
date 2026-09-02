package dev.voidmark.client.ui;

import dev.voidmark.client.render.GuiDraw;
import dev.voidmark.client.render.Starfield;
import dev.voidmark.client.visual.FakeBanScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

/**
 * Voidmark look for vanilla menus (world list, server list, options, pause, …).
 * Inventory, chat, and Voidmark's own screens stay as they are.
 */
public final class MenuChrome {
	private MenuChrome() {
	}

	public static boolean enabled() {
		Minecraft client = Minecraft.getInstance();
		return client != null && applies(client.screen);
	}

	public static boolean applies(Screen screen) {
		if (screen == null) {
			return false;
		}
		if (screen instanceof VoidmarkTitleScreen
			|| screen instanceof VoidmarkScreen
			|| screen instanceof ItemEditScreen
			|| screen instanceof HudEditorScreen
			|| screen instanceof FakeBanScreen) {
			return false;
		}
		if (screen.isInGameUi() || screen instanceof AbstractContainerScreen || screen instanceof ChatScreen) {
			return false;
		}
		return true;
	}

	public static boolean outOfWorld() {
		Minecraft client = Minecraft.getInstance();
		return client != null && client.level == null;
	}

	public static void sky(GuiGraphicsExtractor graphics, int width, int height) {
		Theme.refresh();
		int top = 0xFF05070D;
		int bot = 0xFF000000 | Theme.mix(0x0B0E14, Theme.ACCENT & 0xFFFFFF, 0.06f);
		GuiDraw.fillGradient(graphics, 0, 0, width, height, top, bot);
		try {
			Starfield.drawSky(graphics, width, height);
		} catch (Throwable ignored) {
		}
	}

	public static void headerFooter(GuiGraphicsExtractor graphics, int width, int height) {
		GuiDraw.fill(graphics, 0, 0, width, 48, 0x66000000);
		GuiDraw.fill(graphics, 0, height - 36, width, 36, 0x88000000);
		GuiDraw.fill(graphics, 0, 47, width, 1, Theme.withAlpha(Theme.ACCENT, 80));
		GuiDraw.fill(graphics, 0, height - 36, width, 1, Theme.withAlpha(Theme.LINE, 200));
	}

	public static Component bodyLabel(Component message, boolean active) {
		Theme.refresh();
		int color = (active ? Theme.TEXT : Theme.MUTED) & 0xFFFFFF;
		return message.copy().withStyle(MenuFont.BODY.withColor(color));
	}

	public static Component titleLabel(Component message) {
		Theme.refresh();
		return message.copy().withStyle(MenuFont.BODY.withColor(Theme.HEADER & 0xFFFFFF));
	}

	public static void button(GuiGraphicsExtractor graphics, AbstractWidget widget) {
		Theme.refresh();
		float alpha = widget.getAlpha();
		boolean hover = widget.active && widget.isHoveredOrFocused();
		boolean compact = widget.getWidth() < 40 || widget.getHeight() < 18;
		float radius = compact ? 4f : Math.min(7f, widget.getHeight() * 0.42f);
		int fillRgb = Theme.mix(Theme.CARD, Theme.CARD_HOVER, hover ? 1f : 0f);
		int fill = Theme.withAlpha(fillRgb, Math.round((((Theme.CARD >>> 24) & 0xFF) + (hover ? 18 : 0)) * alpha));
		int outline = fade(hover ? Theme.ACCENT : Theme.LINE, alpha);
		int accent = !compact && widget.active ? fade(Theme.ACCENT, alpha) : 0;
		GuiDraw.panel(
			graphics,
			widget.getX(),
			widget.getY(),
			widget.getWidth(),
			widget.getHeight(),
			radius,
			fill,
			outline,
			accent
		);
	}

	public static void field(GuiGraphicsExtractor graphics, AbstractWidget widget) {
		float alpha = widget.getAlpha();
		boolean focus = widget.isFocused();
		int fill = Theme.withAlpha(Theme.PANEL, Math.round(220 * alpha));
		int outline = fade(focus ? Theme.ACCENT : Theme.LINE, alpha);
		GuiDraw.panel(
			graphics,
			widget.getX(),
			widget.getY(),
			widget.getWidth(),
			widget.getHeight(),
			5f,
			fill,
			outline,
			focus ? fade(Theme.ACCENT, alpha) : 0
		);
	}

	public static void slider(GuiGraphicsExtractor graphics, AbstractWidget widget, double value) {
		float alpha = widget.getAlpha();
		int x = widget.getX();
		int y = widget.getY();
		int w = widget.getWidth();
		int h = widget.getHeight();
		boolean hover = widget.active && widget.isHoveredOrFocused();
		GuiDraw.panel(
			graphics,
			x,
			y,
			w,
			h,
			6f,
			Theme.withAlpha(Theme.CARD, Math.round(230 * alpha)),
			fade(hover ? Theme.ACCENT : Theme.LINE, alpha),
			0
		);
		float trackY = y + h * 0.5f - 1.5f;
		GuiDraw.rounded(graphics, x + 8, trackY, Math.max(8, w - 16), 3, 1.5f, fade(Theme.TRACK, alpha));
		float t = (float) Math.max(0d, Math.min(1d, value));
		float filled = (w - 16) * t;
		if (filled > 1f) {
			GuiDraw.rounded(graphics, x + 8, trackY, filled, 3, 1.5f, fade(Theme.ACCENT, alpha));
		}
		float handleW = 8f;
		float handleH = Math.max(10f, h - 6f);
		float hx = x + 6 + t * (w - 14 - handleW);
		float hy = y + (h - handleH) * 0.5f;
		GuiDraw.rounded(graphics, hx, hy, handleW, handleH, 3f, fade(hover ? Theme.ACCENT : Theme.TEXT, alpha));
	}

	public static void listPanel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		GuiDraw.panel(graphics, x, y, w, h, 8f, Theme.withAlpha(Theme.WINDOW_SOLID, 160), Theme.LINE, 0);
	}

	public static void listSeparators(GuiGraphicsExtractor graphics, int x, int y, int w, int bottom) {
		GuiDraw.fill(graphics, x, y - 1, w, 1, Theme.withAlpha(Theme.ACCENT, 70));
		GuiDraw.fill(graphics, x, bottom, w, 1, Theme.withAlpha(Theme.LINE, 200));
	}

	public static void selection(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		GuiDraw.panel(
			graphics,
			x,
			y,
			w,
			h,
			5f,
			Theme.withAlpha(Theme.CARD_HOVER, 210),
			Theme.ACCENT,
			Theme.ACCENT
		);
	}

	public static void scrollbar(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int thumbY, int thumbH, boolean active) {
		GuiDraw.rounded(graphics, x, y, w, h, 3f, Theme.withAlpha(Theme.TRACK, 200));
		if (active && thumbH > 0) {
			GuiDraw.rounded(graphics, x + 1, thumbY, Math.max(2, w - 2), thumbH, 3f, Theme.ACCENT);
		}
	}

	private static int fade(int color, float alpha) {
		int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
		return Theme.withAlpha(color, a);
	}
}
