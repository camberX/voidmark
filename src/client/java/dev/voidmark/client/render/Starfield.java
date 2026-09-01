package dev.voidmark.client.render;

import dev.voidmark.client.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Random;

/**
 * Slow-drifting, twinkling stars clipped to the click-GUI content pane.
 */
public final class Starfield {
	private static final int COUNT = 86;
	private static final Star[] STARS = new Star[COUNT];
	private static final float[] PALETTE = {
		0xF4F7FF,
		0xD7E6FF,
		0xC8F0FF,
		0xFFE9C8,
		0xB8D4FF
	};

	static {
		Random rng = new Random(0x51A4F1E1L);
		for (int i = 0; i < COUNT; i++) {
			boolean bright = rng.nextFloat() < 0.18f;
			STARS[i] = new Star(
				rng.nextFloat(),
				rng.nextFloat(),
				(rng.nextFloat() - 0.28f) * 6.5f,
				(rng.nextFloat() - 0.55f) * 3.4f,
				bright ? 1.15f + rng.nextFloat() * 0.85f : 0.55f + rng.nextFloat() * 0.55f,
				bright ? 0.42f + rng.nextFloat() * 0.38f : 0.18f + rng.nextFloat() * 0.32f,
				rng.nextFloat() * ((float) Math.PI * 2f),
				0.55f + rng.nextFloat() * 1.85f,
				(int) PALETTE[rng.nextInt(PALETTE.length)],
				bright
			);
		}
	}

	private Starfield() {
	}

	public static void draw(
		GuiGraphicsExtractor graphics,
		float x,
		float y,
		float w,
		float h,
		float radius,
		float appear
	) {
		if (appear < 0.04f || w < 12f || h < 12f) {
			return;
		}
		float r = Math.min(radius, Math.min(w, h) / 2f);
		boolean clipped = GuiDraw.scissor(graphics, x, y, w, h);
		float t = System.nanoTime() / 1_000_000_000f;
		for (Star star : STARS) {
			float px = x + wrap(star.nx * w + t * star.vx, w);
			float py = y + wrap(star.ny * h + t * star.vy, h);
			if (!insideRoundRight(px, py, x, y, w, h, r)) {
				continue;
			}
			float twinkle = 0.38f + 0.62f * (0.5f + 0.5f * (float) Math.sin(t * star.freq + star.phase));
			int alpha = Math.round(255f * star.baseA * twinkle * appear * 0.72f);
			int rgb = mixAccent(star.rgb, 0.22f);
			int color = Theme.withAlpha(rgb, alpha);
			if (star.size >= 1.05f) {
				GuiDraw.circle(graphics, px, py, star.size * 0.55f, color);
				if (star.sparkle && twinkle > 0.78f) {
					int spark = Theme.withAlpha(rgb, alpha / 2);
					float arm = star.size * 1.7f;
					GuiDraw.fill(graphics, px - arm, py - 0.35f, arm * 2f, 0.7f, spark);
					GuiDraw.fill(graphics, px - 0.35f, py - arm, 0.7f, arm * 2f, spark);
				}
			} else {
				GuiDraw.fill(graphics, px, py, 1.1f, 1.1f, color);
			}
		}
		drawShootingStar(graphics, x, y, w, h, r, t, appear);
		if (clipped) {
			GuiDraw.disableScissor(graphics);
		}
	}

	private static void drawShootingStar(
		GuiGraphicsExtractor graphics,
		float x,
		float y,
		float w,
		float h,
		float r,
		float t,
		float appear
	) {
		float cycle = 9.5f;
		float u = (t % cycle) / cycle;
		if (u > 0.22f) {
			return;
		}
		float p = u / 0.22f;
		float sx = x + w * 0.08f + p * w * 0.78f;
		float sy = y + h * 0.10f + p * h * 0.22f;
		float fade = (float) Math.sin(p * Math.PI) * appear;
		int rgb = mixAccent(0xF4F7FF, 0.35f);
		for (int i = 0; i < 10; i++) {
			float k = i / 9f;
			float px = sx - k * 18f;
			float py = sy - k * 5.5f;
			if (!insideRoundRight(px, py, x, y, w, h, r)) {
				continue;
			}
			int a = Math.round(200f * fade * (1f - k) * (1f - k));
			if (a < 8) {
				continue;
			}
			float size = 1.35f - k * 0.9f;
			GuiDraw.circle(graphics, px, py, size, Theme.withAlpha(rgb, a));
		}
	}

	private static int mixAccent(int rgb, float t) {
		return Theme.mix(rgb, Theme.ACCENT & 0xFFFFFF, t);
	}

	private static boolean insideRoundRight(float px, float py, float x, float y, float w, float h, float r) {
		if (px < x || py < y || px >= x + w || py >= y + h) {
			return false;
		}
		if (r < 0.75f || px <= x + w - r) {
			return true;
		}
		float cx = x + w - r;
		if (py < y + r) {
			return dist2(px - cx, py - (y + r)) <= r * r;
		}
		if (py > y + h - r) {
			return dist2(px - cx, py - (y + h - r)) <= r * r;
		}
		return true;
	}

	private static float dist2(float dx, float dy) {
		return dx * dx + dy * dy;
	}

	private static float wrap(float value, float max) {
		if (max <= 0f) {
			return 0f;
		}
		float r = value % max;
		return r < 0f ? r + max : r;
	}

	private record Star(
		float nx,
		float ny,
		float vx,
		float vy,
		float size,
		float baseA,
		float phase,
		float freq,
		int rgb,
		boolean sparkle
	) {
	}
}
