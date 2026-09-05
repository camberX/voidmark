package dev.voidmark.client.combat;

import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.render.GuiDraw;
import dev.voidmark.client.ui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Short CoD-style X on the crosshair after a Voidmark-confirmed hit.
 */
public final class Hitmarker {
	private static final long DURATION_NS = 280_000_000L;
	private static long flashAt;

	private Hitmarker() {
	}

	public static void init() {
		HudElementRegistry.attachElementAfter(
			VanillaHudElements.CROSSHAIR,
			Voidmark.id("hitmarker"),
			Hitmarker::extract
		);
	}

	public static void flash() {
		flashAt = System.nanoTime();
	}

	public static void reset() {
		flashAt = 0L;
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui || client.screen != null) {
			return;
		}
		if (!VoidmarkConfig.get().hitmarkerEnabled || flashAt <= 0L) {
			return;
		}
		long age = System.nanoTime() - flashAt;
		if (age < 0L || age > DURATION_NS) {
			return;
		}
		float t = 1f - age / (float) DURATION_NS;
		float expand = (1f - t) * 2.5f;
		int ink = Theme.withAlpha(0x101218, Math.round(200 * t));
		int fill = Theme.withAlpha(0xF4F7FF, Math.round(255 * t));
		float cx = graphics.guiWidth() * 0.5f;
		float cy = graphics.guiHeight() * 0.5f;
		float inner = 5f + expand;
		float len = 6f;
		arm(graphics, cx, cy, 1, 1, inner, len, ink, fill);
		arm(graphics, cx, cy, -1, 1, inner, len, ink, fill);
		arm(graphics, cx, cy, 1, -1, inner, len, ink, fill);
		arm(graphics, cx, cy, -1, -1, inner, len, ink, fill);
	}

	private static void arm(
		GuiGraphicsExtractor graphics,
		float cx,
		float cy,
		int sx,
		int sy,
		float inner,
		float len,
		int ink,
		int fill
	) {
		for (int i = 0; i < len; i++) {
			float x = cx + sx * (inner + i);
			float y = cy + sy * (inner + i);
			GuiDraw.fill(graphics, x - 1.2f, y - 1.2f, 2.4f, 2.4f, ink);
			GuiDraw.fill(graphics, x - 0.7f, y - 0.7f, 1.4f, 1.4f, fill);
		}
	}
}
