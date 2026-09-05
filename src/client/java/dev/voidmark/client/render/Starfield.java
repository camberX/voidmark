package dev.voidmark.client.render;

import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Random;

/**
 * Slow-drifting, twinkling stars for the click-GUI pane, title-screen sky, and HUD chrome.
 * HUD stars keep the same wrap/twinkle/palette, but draw as pixel fills instead of texture blits.
 */
public final class Starfield {
	private static final float[] PALETTE = {
		0xF4F7FF,
		0xD7E6FF,
		0xC8F0FF,
		0xFFE9C8,
		0xB8D4FF
	};
	private static final Star[] PANE = bake(86, 0x51A4F1E1L, 6.5f, 3.4f);
	private static final Star[] SKY = bake(160, 0xC0FFEE11L, 9.5f, 4.8f);
	private static final float[] PANE_TWINKLE = new float[PANE.length];
	private static int tintedAccent = Integer.MIN_VALUE;
	private static float frameT;
	private static long frameNs;
	private static float twinkleT = Float.NaN;

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
		if (!VoidmarkConfig.get().menuStarfield) {
			return;
		}
		try {
			paint(graphics, PANE, x, y, w, h, radius, appear, 0.72f, 18f, true, false, false);
		} catch (Throwable ignored) {
		}
	}

	public static void drawHud(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius) {
		try {
			paint(graphics, PANE, x, y, w, h, radius, 0.88f, 0.55f, 12f, false, true, true);
		} catch (Throwable ignored) {
		}
	}

	public static void drawSky(GuiGraphicsExtractor graphics, float w, float h) {
		try {
			paint(graphics, SKY, 0f, 0f, w, h, 0f, 1f, 0.92f, Math.max(22f, w * 0.045f), true, false, false);
			paint(graphics, PANE, 0f, 0f, w, h, 0f, 1f, 0.55f, 18f, false, false, false);
		} catch (Throwable ignored) {
		}
	}

	private static void paint(
		GuiGraphicsExtractor graphics,
		Star[] stars,
		float x,
		float y,
		float w,
		float h,
		float radius,
		float appear,
		float alphaScale,
		float trail,
		boolean shooting,
		boolean roundAll,
		boolean hud
	) {
		if (stars == null || stars.length == 0 || appear < 0.04f || w < 12f || h < 12f) {
			return;
		}
		float r = Math.min(radius, Math.min(w, h) / 2f);
		boolean clipped = GuiDraw.scissor(graphics, x, y, w, h);
		float t = frameTime();
		ensureTint();
		if (hud && stars == PANE) {
			ensureTwinkle(t);
		}
		for (int i = 0; i < stars.length; i++) {
			Star star = stars[i];
			float px = x + wrap(star.nx * w + t * star.vx, w);
			float py = y + wrap(star.ny * h + t * star.vy, h);
			if (!insideRound(px, py, x, y, w, h, r, roundAll)) {
				continue;
			}
			float twinkle;
			if (hud && stars == PANE) {
				twinkle = PANE_TWINKLE[i];
			} else {
				twinkle = 0.38f + 0.62f * (0.5f + 0.5f * (float) Math.sin(t * star.freq + star.phase));
			}
			int alpha = Math.round(255f * star.baseA * twinkle * appear * alphaScale);
			if (alpha < 3) {
				continue;
			}
			int color = Theme.withAlpha(star.tint, alpha);
			if (star.size >= 1.05f) {
				float rad = star.size * 0.55f;
				if (hud) {
					GuiDraw.fill(graphics, px - rad, py - rad, rad * 2f, rad * 2f, color);
				} else {
					GuiDraw.circle(graphics, px, py, rad, color);
				}
				if (star.sparkle && twinkle > 0.78f) {
					int spark = Theme.withAlpha(star.tint, alpha / 2);
					float arm = star.size * 1.7f;
					GuiDraw.fill(graphics, px - arm, py - 0.35f, arm * 2f, 0.7f, spark);
					GuiDraw.fill(graphics, px - 0.35f, py - arm, 0.7f, arm * 2f, spark);
				}
			} else {
				GuiDraw.fill(graphics, px, py, 1.1f, 1.1f, color);
			}
		}
		if (shooting) {
			drawShootingStar(graphics, x, y, w, h, r, t, appear, trail, roundAll);
		}
		if (clipped) {
			GuiDraw.disableScissor(graphics);
		}
	}

	private static Star[] bake(int count, long seed, float driftX, float driftY) {
		Random rng = new Random(seed);
		Star[] stars = new Star[count];
		float[] palette = PALETTE;
		int paletteLen = palette == null || palette.length == 0 ? 0 : palette.length;
		for (int i = 0; i < count; i++) {
			boolean bright = rng.nextFloat() < 0.18f;
			int rgb = paletteLen == 0 ? 0xF4F7FF : (int) palette[rng.nextInt(paletteLen)];
			stars[i] = new Star(
				rng.nextFloat(),
				rng.nextFloat(),
				(rng.nextFloat() - 0.28f) * driftX,
				(rng.nextFloat() - 0.55f) * driftY,
				bright ? 1.15f + rng.nextFloat() * 0.85f : 0.55f + rng.nextFloat() * 0.55f,
				bright ? 0.42f + rng.nextFloat() * 0.38f : 0.18f + rng.nextFloat() * 0.32f,
				rng.nextFloat() * ((float) Math.PI * 2f),
				0.55f + rng.nextFloat() * 1.85f,
				rgb,
				bright
			);
		}
		return stars;
	}

	private static void drawShootingStar(
		GuiGraphicsExtractor graphics,
		float x,
		float y,
		float w,
		float h,
		float r,
		float t,
		float appear,
		float trail,
		boolean roundAll
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
		int rgb = Theme.mix(0xF4F7FF, Theme.ACCENT & 0xFFFFFF, 0.35f);
		for (int i = 0; i < 10; i++) {
			float k = i / 9f;
			float px = sx - k * trail;
			float py = sy - k * trail * 0.30f;
			if (!insideRound(px, py, x, y, w, h, r, roundAll)) {
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

	private static void ensureTint() {
		int accent = Theme.ACCENT;
		if (accent == tintedAccent) {
			return;
		}
		tintedAccent = accent;
		int to = accent & 0xFFFFFF;
		tint(PANE, to, 0.22f);
		tint(SKY, to, 0.22f);
	}

	private static void tint(Star[] stars, int accentRgb, float mix) {
		for (Star star : stars) {
			star.tint = Theme.mix(star.rgb, accentRgb, mix);
		}
	}

	private static void ensureTwinkle(float t) {
		if (t == twinkleT) {
			return;
		}
		twinkleT = t;
		for (int i = 0; i < PANE.length; i++) {
			Star star = PANE[i];
			PANE_TWINKLE[i] = 0.38f + 0.62f * (0.5f + 0.5f * (float) Math.sin(t * star.freq + star.phase));
		}
	}

	private static float frameTime() {
		long ns = System.nanoTime();
		if (ns - frameNs < 2_000_000L && frameT != 0f) {
			return frameT;
		}
		frameNs = ns;
		frameT = ns * 1e-9f;
		return frameT;
	}

	private static boolean insideRound(float px, float py, float x, float y, float w, float h, float r, boolean all) {
		if (px < x || py < y || px >= x + w || py >= y + h) {
			return false;
		}
		if (r < 0.75f) {
			return true;
		}
		if (px < x + r && py < y + r) {
			return !all || dist2(px - (x + r), py - (y + r)) <= r * r;
		}
		if (px > x + w - r && py < y + r) {
			return dist2(px - (x + w - r), py - (y + r)) <= r * r;
		}
		if (px < x + r && py > y + h - r) {
			return !all || dist2(px - (x + r), py - (y + h - r)) <= r * r;
		}
		if (px > x + w - r && py > y + h - r) {
			return dist2(px - (x + w - r), py - (y + h - r)) <= r * r;
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

	private static final class Star {
		final float nx;
		final float ny;
		final float vx;
		final float vy;
		final float size;
		final float baseA;
		final float phase;
		final float freq;
		final int rgb;
		final boolean sparkle;
		int tint;

		Star(
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
			this.nx = nx;
			this.ny = ny;
			this.vx = vx;
			this.vy = vy;
			this.size = size;
			this.baseA = baseA;
			this.phase = phase;
			this.freq = freq;
			this.rgb = rgb;
			this.sparkle = sparkle;
			this.tint = rgb;
		}
	}
}
