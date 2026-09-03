package dev.voidmark.client.render;

import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.ui.Theme;
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
		boolean right = accentTowardRight(graphics, x, y, w);
		GuiDraw.panel(graphics, x, y, w, h, radius, Theme.HUD_WINDOW, Theme.HUD_LINE, accent, right);
		if (VoidmarkConfig.get().hudStarfield && w >= 72f && h >= 22f) {
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

	/** Rail sits on the side closer to the screen edge. */
	private static boolean accentTowardRight(GuiGraphicsExtractor graphics, float x, float y, float w) {
		var pose = graphics.pose();
		float left = pose.m00() * x + pose.m10() * y + pose.m20();
		float right = pose.m00() * (x + w) + pose.m10() * y + pose.m20();
		return (left + right) * 0.5f > graphics.guiWidth() * 0.5f;
	}
}
