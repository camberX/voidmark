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
 * Size follows {@code hitmarkerScale}; it only fades alpha, not color.
 */
public final class Hitmarker {
	private static final long DURATION_NS = 280_000_000L;

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

	private static long flashAt;

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui || client.screen != null) {
			return;
		}
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.hitmarkerEnabled || flashAt <= 0L) {
			return;
		}
		long age = System.nanoTime() - flashAt;
		if (age < 0L || age > DURATION_NS) {
			return;
		}
		float t = 1f - age / (float) DURATION_NS;
		float scale = VoidmarkConfig.clampHudScale(config.hitmarkerScale);
		int ink = Theme.withAlpha(0x000000, Math.round(255 * t));
		int fill = Theme.withAlpha(0xFFFFFF, Math.round(255 * t));
		float cx = graphics.guiWidth() * 0.5f;
		float cy = graphics.guiHeight() * 0.5f;
		float inner = 5f * scale;
		float len = 6f * scale;
		float stroke = Math.max(1.2f, 1.6f * scale);
		float rim = Math.max(0.8f, 0.9f * scale);
		arm(graphics, cx, cy, 1, 1, inner, len, stroke, rim, ink, fill);
		arm(graphics, cx, cy, -1, 1, inner, len, stroke, rim, ink, fill);
		arm(graphics, cx, cy, 1, -1, inner, len, stroke, rim, ink, fill);
		arm(graphics, cx, cy, -1, -1, inner, len, stroke, rim, ink, fill);
	}

	private static void arm(
		GuiGraphicsExtractor graphics,
		float cx,
		float cy,
		int sx,
		int sy,
		float inner,
		float len,
		float stroke,
		float rim,
		int ink,
		int fill
	) {
		int steps = Math.max(4, Math.round(len + rim * 2f));
		float inkSize = stroke + rim * 2f;
		float inkHalf = inkSize * 0.5f;
		float fillHalf = stroke * 0.5f;
		for (int i = 0; i < steps; i++) {
			float u = inner + len * (i / (float) Math.max(1, steps - 1));
			float x = cx + sx * u;
			float y = cy + sy * u;
			GuiDraw.fill(graphics, x - inkHalf, y - inkHalf, inkSize, inkSize, ink);
		}
		int innerSteps = Math.max(4, Math.round(len));
		for (int i = 0; i < innerSteps; i++) {
			float u = inner + len * (i / (float) Math.max(1, innerSteps - 1));
			float x = cx + sx * u;
			float y = cy + sy * u;
			GuiDraw.fill(graphics, x - fillHalf, y - fillHalf, stroke, stroke, fill);
		}
	}
}
