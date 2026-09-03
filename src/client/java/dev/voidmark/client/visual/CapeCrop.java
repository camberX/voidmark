package dev.voidmark.client.visual;

import net.minecraft.util.Mth;

/**
 * Normalized 10:16 window on a source photo. {@code x,y,w,h} are fractions of
 * the source width/height. Default is a cover crop so the cape face is filled.
 */
public final class CapeCrop {
	public static final float ASPECT = CapeAtlas.FACE_W / (float) CapeAtlas.FACE_H;

	public float x;
	public float y;
	public float w;
	public float h;

	public CapeCrop(float x, float y, float w, float h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
	}

	public static CapeCrop cover(int srcW, int srcH) {
		float srcAspect = srcW / (float) Math.max(1, srcH);
		CapeCrop crop = new CapeCrop(0f, 0f, 1f, 1f);
		if (srcAspect > ASPECT) {
			crop.h = 1f;
			crop.w = ASPECT / srcAspect;
			crop.x = (1f - crop.w) * 0.5f;
			crop.y = 0f;
		} else {
			crop.w = 1f;
			crop.h = srcAspect / ASPECT;
			crop.x = 0f;
			crop.y = (1f - crop.h) * 0.5f;
		}
		return crop;
	}

	public void pan(float dx, float dy, int srcW, int srcH) {
		x += dx;
		y += dy;
		clamp(srcW, srcH);
	}

	public void zoom(float factor, float pivotX, float pivotY, int srcW, int srcH) {
		factor = Mth.clamp(factor, 0.5f, 1.8f);
		x = pivotX - (pivotX - x) * factor;
		y = pivotY - (pivotY - y) * factor;
		w *= factor;
		h *= factor;
		clamp(srcW, srcH);
	}

	public void clamp(int srcW, int srcH) {
		srcW = Math.max(1, srcW);
		srcH = Math.max(1, srcH);
		float srcAspect = srcW / (float) srcH;
		float normAspect = ASPECT / srcAspect;
		CapeCrop cover = cover(srcW, srcH);
		float minW = Math.max(10f / srcW, cover.w * 0.12f);
		w = Mth.clamp(w, minW, cover.w);
		h = w / normAspect;
		if (h > cover.h) {
			h = cover.h;
			w = h * normAspect;
		}
		x = Mth.clamp(x, 0f, Math.max(0f, 1f - w));
		y = Mth.clamp(y, 0f, Math.max(0f, 1f - h));
	}
}
