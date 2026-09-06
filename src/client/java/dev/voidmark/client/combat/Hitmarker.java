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
 * Short CoD-style X on the vanilla crosshair after a Voidmark-confirmed hit.
 * Arms are anti-aliased strokes so they stay smooth and sit on the 15x15
 * crosshair sprite center. Size follows {@code hitmarkerScale}; it only
 * fades alpha, not color.
 */
public final class Hitmarker {
	private static final long DURATION_NS = 280_000_000L;
	private static final int CROSSHAIR = 15;
	private static final float INV_SQRT2 = 0.70710677f;

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
		float cx = (graphics.guiWidth() - CROSSHAIR) / 2 + CROSSHAIR * 0.5f;
		float cy = (graphics.guiHeight() - CROSSHAIR) / 2 + CROSSHAIR * 0.5f;
		float inner = 5f * scale;
		float outer = inner + 6f * scale;
		float stroke = Math.max(0.7f, 0.85f * scale);
		float rim = Math.max(0.4f, 0.45f * scale);
		arm(graphics, cx, cy, 1, 1, inner, outer, stroke, rim, ink, fill);
		arm(graphics, cx, cy, -1, 1, inner, outer, stroke, rim, ink, fill);
		arm(graphics, cx, cy, 1, -1, inner, outer, stroke, rim, ink, fill);
		arm(graphics, cx, cy, -1, -1, inner, outer, stroke, rim, ink, fill);
	}

	private static void arm(
		GuiGraphicsExtractor graphics,
		float cx,
		float cy,
		int sx,
		int sy,
		float inner,
		float outer,
		float stroke,
		float rim,
		int ink,
		int fill
	) {
		float ux = sx * INV_SQRT2;
		float uy = sy * INV_SQRT2;
		float x0 = cx + ux * inner;
		float y0 = cy + uy * inner;
		float x1 = cx + ux * outer;
		float y1 = cy + uy * outer;
		GuiDraw.stroke(graphics, x0, y0, x1, y1, stroke + rim * 2f, ink);
		GuiDraw.stroke(graphics, x0, y0, x1, y1, stroke, fill);
	}
}
