package dev.voidmark.client.render;

import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.ui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

public final class WatermarkRenderer {
	public static final float HEIGHT = 18;
	private static final String DEV_TAG = "DEV";
	private static final float DEV_GAP = 3f;

	private WatermarkRenderer() {
	}

	public static void init() {
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Voidmark.id("watermark"),
			WatermarkRenderer::extract
		);
	}

	public static float occupiedHeight() {
		VoidmarkConfig config = VoidmarkConfig.get();
		return config.watermarkEnabled ? HEIGHT * HudLayout.scale(HudLayout.Id.WATERMARK) + 8 : 0;
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return;
		}
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.watermarkEnabled) {
			return;
		}
		HudLayout.Box box = HudLayout.box(HudLayout.Id.WATERMARK, client.font, graphics.guiWidth(), graphics.guiHeight());
		draw(graphics, client.font, box.x(), box.y(), HudLayout.scale(HudLayout.Id.WATERMARK));
	}

	public static void draw(GuiGraphicsExtractor graphics, Font font, float x, float y, float scale) {
		List<String> parts = new ArrayList<>();
		parts.add("VOIDMARK");
		VoidmarkConfig config = VoidmarkConfig.get();
		if (config.watermarkFps) {
			parts.add(HudStats.fps() + " fps");
		}
		if (config.watermarkPing) {
			parts.add(HudStats.pingLabel());
		}
		if (config.watermarkTime) {
			parts.add(HudStats.time());
		}
		if (config.watermarkName) {
			parts.add(HudStats.playerName());
		}

		float pad = 7;
		float gap = 8;
		float w = pad * 2 + brandExtra(font);
		for (int i = 0; i < parts.size(); i++) {
			w += GuiDraw.menuWidth(font, parts.get(i));
			if (i + 1 < parts.size()) {
				w += gap + 1;
			}
		}

		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		if (scale != 1.0f) {
			graphics.pose().scale(scale, scale);
		}

		GuiDraw.panel(graphics, 0, 0, w, HEIGHT, 5, Theme.WINDOW, Theme.LINE);
		GuiDraw.rounded(graphics, 1, 1, 3, HEIGHT - 2, 1.5f, Theme.ACCENT);

		float cx = pad + 2;
		float textY = GuiDraw.middle(0, HEIGHT);
		for (int i = 0; i < parts.size(); i++) {
			int color = i == 0 ? Theme.ACCENT : Theme.TEXT;
			GuiDraw.menu(graphics, font, parts.get(i), cx, textY, color);
			cx += GuiDraw.menuWidth(font, parts.get(i));
			if (i == 0) {
				cx += DEV_GAP;
				GuiDraw.small(graphics, font, DEV_TAG, cx, textY, Theme.WARN);
				cx += GuiDraw.smallWidth(font, DEV_TAG);
			}
			if (i + 1 < parts.size()) {
				cx += gap / 2f;
				GuiDraw.fill(graphics, cx, 4, 1, HEIGHT - 8, Theme.LINE);
				cx += gap / 2f + 1;
			}
		}
		graphics.pose().popMatrix();
	}

	public static float width(Font font) {
		VoidmarkConfig config = VoidmarkConfig.get();
		float w = 16 + brandExtra(font);
		w += GuiDraw.menuWidth(font, "VOIDMARK");
		if (config.watermarkFps) {
			w += 9 + GuiDraw.menuWidth(font, "000 fps");
		}
		if (config.watermarkPing) {
			w += 9 + GuiDraw.menuWidth(font, "000 ms");
		}
		if (config.watermarkTime) {
			w += 9 + GuiDraw.menuWidth(font, "00:00");
		}
		if (config.watermarkName) {
			w += 9 + GuiDraw.menuWidth(font, HudStats.playerName());
		}
		return w;
	}

	private static float brandExtra(Font font) {
		return DEV_GAP + GuiDraw.smallWidth(font, DEV_TAG);
	}
}
