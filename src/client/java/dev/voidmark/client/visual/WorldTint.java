package dev.voidmark.client.visual;

import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

public final class WorldTint {
	private WorldTint() {
	}

	public static void tintFog(FogData data) {
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.worldTintEnabled || data == null || data.color == null) {
			return;
		}
		mix(data.color, config.worldTintRgb, config.worldTintStrength);
	}

	public static boolean skyTintActive() {
		return VoidmarkConfig.get().skyTintEnabled;
	}

	public static int tintSky(int skyColor) {
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.skyTintEnabled) {
			return skyColor;
		}
		return mixArgb(skyColor, skyRgb(config), config.skyTintStrength);
	}

	public static int skyDiscColor(int fallback) {
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.skyTintEnabled) {
			return fallback;
		}
		return mixArgb(fallback, skyRgb(config), config.skyTintStrength);
	}

	private static int skyRgb(VoidmarkConfig config) {
		return config.matchSkyToWorld ? config.worldTintRgb : config.skyTintRgb;
	}

	public static void tintLightmap(LightmapRenderState state) {
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.worldTintEnabled) {
			return;
		}
		float strength = config.worldTintStrength;
		state.skyLightColor = mix(state.skyLightColor, config.worldTintRgb, strength);
		state.ambientColor = mix(state.ambientColor, config.worldTintRgb, strength * 0.85f);
		state.blockLightTint = mix(state.blockLightTint, config.worldTintRgb, strength * 0.45f);
	}

	public static int mixArgb(int from, int toRgb, float strength) {
		float t = Mth.clamp(strength, 0f, 1f);
		int a = ARGB.alpha(from);
		int r = lerpChannel(ARGB.red(from), (toRgb >> 16) & 0xFF, t);
		int g = lerpChannel(ARGB.green(from), (toRgb >> 8) & 0xFF, t);
		int b = lerpChannel(ARGB.blue(from), toRgb & 0xFF, t);
		return ARGB.color(a == 0 ? 255 : a, r, g, b);
	}

	private static void mix(Vector4f color, int rgb, float strength) {
		float t = Mth.clamp(strength, 0f, 1f);
		color.x = color.x * (1f - t) + ((rgb >> 16) & 0xFF) / 255f * t;
		color.y = color.y * (1f - t) + ((rgb >> 8) & 0xFF) / 255f * t;
		color.z = color.z * (1f - t) + (rgb & 0xFF) / 255f * t;
	}

	private static Vector3f mix(Vector3fc original, int rgb, float strength) {
		float t = Mth.clamp(strength, 0f, 1f);
		float r = ((rgb >> 16) & 0xFF) / 255f;
		float g = ((rgb >> 8) & 0xFF) / 255f;
		float b = (rgb & 0xFF) / 255f;
		if (original == null) {
			return new Vector3f(r, g, b);
		}
		return new Vector3f(
			original.x() * (1f - t) + r * t,
			original.y() * (1f - t) + g * t,
			original.z() * (1f - t) + b * t
		);
	}

	private static int lerpChannel(int from, int to, float t) {
		return Mth.clamp(Math.round(from + (to - from) * t), 0, 255);
	}

	public static int hsvToRgb(float hue, float sat, float value) {
		float h = ((hue % 360f) + 360f) % 360f / 60f;
		int i = (int) Math.floor(h);
		float f = h - i;
		float p = value * (1f - sat);
		float q = value * (1f - f * sat);
		float t = value * (1f - (1f - f) * sat);
		float r;
		float g;
		float b;
		switch (i) {
			case 0 -> { r = value; g = t; b = p; }
			case 1 -> { r = q; g = value; b = p; }
			case 2 -> { r = p; g = value; b = t; }
			case 3 -> { r = p; g = q; b = value; }
			case 4 -> { r = t; g = p; b = value; }
			default -> { r = value; g = p; b = q; }
		}
		return ((Math.round(r * 255) & 0xFF) << 16) | ((Math.round(g * 255) & 0xFF) << 8) | (Math.round(b * 255) & 0xFF);
	}

	public static float[] rgbToHsv(int rgb) {
		float r = ((rgb >> 16) & 0xFF) / 255f;
		float g = ((rgb >> 8) & 0xFF) / 255f;
		float b = (rgb & 0xFF) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float delta = max - min;
		float hue = 0f;
		if (delta > 1.0e-5f) {
			if (max == r) {
				hue = 60f * (((g - b) / delta) % 6f);
			} else if (max == g) {
				hue = 60f * ((b - r) / delta + 2f);
			} else {
				hue = 60f * ((r - g) / delta + 4f);
			}
		}
		if (hue < 0f) {
			hue += 360f;
		}
		float sat = max <= 0f ? 0f : delta / max;
		return new float[]{hue, sat, max};
	}
}
