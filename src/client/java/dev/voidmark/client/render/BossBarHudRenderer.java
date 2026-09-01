package dev.voidmark.client.render;

import dev.voidmark.client.mixin.BossHealthOverlayAccessor;
import dev.voidmark.client.ui.Theme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class BossBarHudRenderer {
	private static final float BAR_W = 184;
	private static final float BAR_H = 16;
	private static final int MAX = 5;

	private BossBarHudRenderer() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		var events = ((BossHealthOverlayAccessor) client.gui.getBossOverlay()).voidmark$events();
		if (events == null || events.isEmpty()) {
			return;
		}
		Font font = client.font;
		float x = (graphics.guiWidth() - BAR_W) * 0.5f;
		float y = 8;
		int drawn = 0;
		for (LerpingBossEvent event : events.values()) {
			draw(graphics, font, x, y, event);
			y += BAR_H + 4;
			drawn++;
			if (drawn >= MAX) {
				break;
			}
		}
	}

	private static void draw(GuiGraphicsExtractor graphics, Font font, float x, float y, LerpingBossEvent event) {
		float t = Mth.clamp(event.getProgress(), 0f, 1f);
		int fill = barColor(event);
		GuiDraw.panel(graphics, x, y, BAR_W, BAR_H, 5, Theme.WINDOW, Theme.LINE, fill);
		if (t > 0.01f) {
			GuiDraw.rounded(graphics, x + 8, y + BAR_H - 4, Math.max(2f, (BAR_W - 16f) * t), 2.5f, 1.2f, fill);
		}
		Component name = event.getName();
		float nx = x + (BAR_W - font.width(name)) * 0.5f;
		GuiDraw.text(graphics, font, name, nx, y + 2, 0xFFFFFFFF, false);
	}

	private static int barColor(LerpingBossEvent event) {
		ChatFormatting formatting = event.getColor().getFormatting();
		Integer rgb = formatting == null ? null : formatting.getColor();
		if (rgb == null) {
			return Theme.ACCENT;
		}
		return 0xFF000000 | rgb;
	}
}
