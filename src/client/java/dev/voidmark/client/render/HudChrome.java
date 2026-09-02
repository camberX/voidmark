package dev.voidmark.client.render;

import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class HudChrome {
	private HudChrome() {
	}

	public static void panel(
		GuiGraphicsExtractor graphics,
		float x,
		float y,
		float w,
		float h,
		float radius,
		int fill,
		int outline,
		int accent
	) {
		GuiDraw.panel(graphics, x, y, w, h, radius, fill, outline, accent);
		if (VoidmarkConfig.get().hudStarfield) {
			Starfield.drawHud(graphics, x, y, w, h, radius);
		}
	}

	public static void panel(
		GuiGraphicsExtractor graphics,
		float x,
		float y,
		float w,
		float h,
		float radius,
		int fill,
		int outline
	) {
		panel(graphics, x, y, w, h, radius, fill, outline, 0);
	}
}
