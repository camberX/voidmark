package dev.voidmark.client.visual;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * Minecraft's cape model is a 10×16×1 box authored for a 64×32 atlas
 * ({@code LayerDefinition} 64×64 with V texture scale 0.5).
 * Uploading a raw photo (for example 352×272) makes the model sample a thin
 * crop of the top-left, which looks like stretched bands.
 *
 * Vanilla / OptiFine templates (64×32 and integer 2:1 scales) are kept as-is.
 * Any other PNG is fitted into the front and back 10×16 slots.
 */
final class CapeAtlas {
	static final int LAYOUT_W = 64;
	static final int LAYOUT_H = 32;
	static final int FACE_U = 12;
	static final int FACE_V = 1;
	static final int FACE_W = 10;
	static final int FACE_H = 16;
	private static final int MAX_SCALE = 16;

	private CapeAtlas() {
	}

	static boolean isVanillaLayout(int width, int height) {
		if (width < LAYOUT_W || height < LAYOUT_H) {
			return false;
		}
		if (width % LAYOUT_W != 0 || height % LAYOUT_H != 0) {
			return false;
		}
		return width / LAYOUT_W == height / LAYOUT_H;
	}

	/**
	 * Takes ownership of {@code src}. Returns a texture the cape model can sample.
	 */
	static NativeImage toAtlas(NativeImage src) {
		return toAtlas(src, null);
	}

	static NativeImage toAtlas(NativeImage src, CapeCrop crop) {
		if (crop == null && isVanillaLayout(src.getWidth(), src.getHeight())) {
			return src;
		}
		try {
			return bake(src, crop);
		} finally {
			src.close();
		}
	}

	static int atlasScale(int width) {
		return Math.max(1, width / LAYOUT_W);
	}

	private static NativeImage bake(NativeImage src, CapeCrop crop) {
		int scale = pickScale(src.getWidth(), src.getHeight());
		NativeImage atlas = new NativeImage(LAYOUT_W * scale, LAYOUT_H * scale, false);
		try {
			atlas.fillRect(0, 0, atlas.getWidth(), atlas.getHeight(), 0xFF000000);

			int fu = 1 * scale;
			int fv = 1 * scale;
			int fw = FACE_W * scale;
			int fh = FACE_H * scale;
			if (crop != null) {
				paintFaceCrop(src, atlas, fu, fv, fw, fh, crop);
			} else {
				int pad = averageBorder(src);
				paintFace(src, atlas, fu, fv, fw, fh, pad);
			}
			atlas.copyRect(fu, fv, (FACE_U - 1) * scale, 0, fw, fh, false, false);
			paintEdges(atlas, scale);
			return atlas;
		} catch (RuntimeException | Error exception) {
			atlas.close();
			throw exception;
		}
	}

	private static int pickScale(int srcW, int srcH) {
		int needed = Math.max(4, Math.min(MAX_SCALE, Math.max(srcW / FACE_W, srcH / FACE_H)));
		return Math.min(MAX_SCALE, Math.max(4, needed));
	}

	private static void paintFace(NativeImage src, NativeImage atlas, int dx, int dy, int dw, int dh, int pad) {
		int sw = src.getWidth();
		int sh = src.getHeight();
		float fit = Math.min(dw / (float) sw, dh / (float) sh);
		float rw = sw * fit;
		float rh = sh * fit;
		float ox = dx + (dw - rw) * 0.5f;
		float oy = dy + (dh - rh) * 0.5f;
		boolean bilinear = fit < 1.0f;
		for (int y = 0; y < dh; y++) {
			for (int x = 0; x < dw; x++) {
				float px = dx + x + 0.5f;
				float py = dy + y + 0.5f;
				if (px < ox || py < oy || px >= ox + rw || py >= oy + rh) {
					atlas.setPixel(dx + x, dy + y, pad);
					continue;
				}
				float sx = (px - ox) / fit - 0.5f;
				float sy = (py - oy) / fit - 0.5f;
				int color = bilinear ? sampleBilinear(src, sx, sy) : sampleNearest(src, sx, sy);
				atlas.setPixel(dx + x, dy + y, color | 0xFF000000);
			}
		}
	}

	private static void paintFaceCrop(NativeImage src, NativeImage atlas, int dx, int dy, int dw, int dh, CapeCrop crop) {
		float x0 = crop.x * src.getWidth();
		float y0 = crop.y * src.getHeight();
		float cw = crop.w * src.getWidth();
		float ch = crop.h * src.getHeight();
		boolean bilinear = cw > dw || ch > dh;
		for (int y = 0; y < dh; y++) {
			for (int x = 0; x < dw; x++) {
				float sx = x0 + (x + 0.5f) / dw * cw - 0.5f;
				float sy = y0 + (y + 0.5f) / dh * ch - 0.5f;
				int color = bilinear ? sampleBilinear(src, sx, sy) : sampleNearest(src, sx, sy);
				atlas.setPixel(dx + x, dy + y, color | 0xFF000000);
			}
		}
	}

	private static void paintEdges(NativeImage atlas, int scale) {
		int fu = 1 * scale;
		int fv = 1 * scale;
		int fw = FACE_W * scale;
		int fh = FACE_H * scale;
		for (int y = 0; y < fh; y++) {
			int left = atlas.getPixel(fu, fv + y);
			int right = atlas.getPixel(fu + fw - 1, fv + y);
			for (int x = 0; x < scale; x++) {
				atlas.setPixel(x, fv + y, left);
				atlas.setPixel(11 * scale + x, fv + y, right);
			}
		}
		for (int x = 0; x < fw; x++) {
			int top = atlas.getPixel(fu + x, fv);
			int bottom = atlas.getPixel(fu + x, fv + fh - 1);
			for (int y = 0; y < scale; y++) {
				atlas.setPixel(fu + x, y, top);
				atlas.setPixel(11 * scale + x, y, bottom);
			}
		}
	}

	private static int averageBorder(NativeImage src) {
		int w = src.getWidth();
		int h = src.getHeight();
		long r = 0;
		long g = 0;
		long b = 0;
		int n = 0;
		for (int x = 0; x < w; x++) {
			int top = src.getPixel(x, 0);
			int bottom = src.getPixel(x, h - 1);
			r += ((top >>> 16) & 255) + ((bottom >>> 16) & 255);
			g += ((top >>> 8) & 255) + ((bottom >>> 8) & 255);
			b += (top & 255) + (bottom & 255);
			n += 2;
		}
		for (int y = 1; y < h - 1; y++) {
			int left = src.getPixel(0, y);
			int right = src.getPixel(w - 1, y);
			r += ((left >>> 16) & 255) + ((right >>> 16) & 255);
			g += ((left >>> 8) & 255) + ((right >>> 8) & 255);
			b += (left & 255) + (right & 255);
			n += 2;
		}
		if (n == 0) {
			return 0xFF000000;
		}
		return 0xFF000000 | ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
	}

	private static int sampleNearest(NativeImage src, float x, float y) {
		int sx = clamp((int) Math.floor(x + 0.5f), 0, src.getWidth() - 1);
		int sy = clamp((int) Math.floor(y + 0.5f), 0, src.getHeight() - 1);
		return src.getPixel(sx, sy);
	}

	private static int sampleBilinear(NativeImage src, float x, float y) {
		int x0 = clamp((int) Math.floor(x), 0, src.getWidth() - 1);
		int y0 = clamp((int) Math.floor(y), 0, src.getHeight() - 1);
		int x1 = clamp(x0 + 1, 0, src.getWidth() - 1);
		int y1 = clamp(y0 + 1, 0, src.getHeight() - 1);
		float tx = x - (float) Math.floor(x);
		float ty = y - (float) Math.floor(y);
		int c00 = src.getPixel(x0, y0);
		int c10 = src.getPixel(x1, y0);
		int c01 = src.getPixel(x0, y1);
		int c11 = src.getPixel(x1, y1);
		return mix(mix(c00, c10, tx), mix(c01, c11, tx), ty);
	}

	private static int mix(int a, int b, float t) {
		t = t < 0f ? 0f : Math.min(1f, t);
		int ar = (a >>> 16) & 255;
		int ag = (a >>> 8) & 255;
		int ab = a & 255;
		int br = (b >>> 16) & 255;
		int bg = (b >>> 8) & 255;
		int bb = b & 255;
		int r = Math.round(ar + (br - ar) * t);
		int g = Math.round(ag + (bg - ag) * t);
		int bl = Math.round(ab + (bb - ab) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
