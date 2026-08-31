package dev.voidmark.client.visual;

import com.mojang.blaze3d.vertex.QuadInstance;
import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class WorldTint {
	private static int lastMeshKey = Integer.MIN_VALUE;
	private static Boolean sodiumLoaded;
	private static boolean lastLightmapActive;

	private WorldTint() {
	}

	public static boolean sodiumLoaded() {
		if (sodiumLoaded == null) {
			sodiumLoaded = FabricLoader.getInstance().isModLoaded("sodium");
		}
		return sodiumLoaded;
	}

	public static boolean shaderTintActive() {
		VoidmarkConfig config = VoidmarkConfig.get();
		return config.worldTintEnabled && sodiumLoaded() && !config.worldTintUsesLightmap();
	}

	public static boolean lightmapTintActive() {
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.worldTintEnabled) {
			return false;
		}
		return config.worldTintUsesLightmap() || !sodiumLoaded();
	}

	public static boolean shouldRefreshLightmap() {
		boolean active = lightmapTintActive();
		boolean leaving = lastLightmapActive && !active;
		lastLightmapActive = active;
		return active || leaving;
	}

	public static int shaderRgb() {
		return VoidmarkConfig.get().worldTintRgb;
	}

	public static float shaderStrength() {
		if (!shaderTintActive()) {
			return 0f;
		}
		return Mth.clamp(VoidmarkConfig.get().worldTintStrength, 0f, 1f);
	}

	public static String injectTerrainFragmentSource(String src) {
		if (src == null || src.contains("u_WorldTint")) {
			return src;
		}
		String withUniform = src.contains("uniform sampler2D u_BlockTex; // The block texture")
			? src.replace("uniform sampler2D u_BlockTex; // The block texture", "uniform sampler2D u_BlockTex; // The block texture\nuniform vec4 u_WorldTint;")
			: src.replace("uniform sampler2D u_BlockTex;", "uniform sampler2D u_BlockTex;\nuniform vec4 u_WorldTint;");
		String tinted = withUniform.contains("color *= v_Color;")
			? withUniform.replace("color *= v_Color;", "color *= v_Color;\n    color.rgb = mix(color.rgb, u_WorldTint.rgb, u_WorldTint.a);")
			: withUniform;
		if (!tinted.contains("mix(color.rgb, u_WorldTint.rgb, u_WorldTint.a)")) {
			Voidmark.LOGGER.warn("Could not inject world tint into Sodium terrain shader");
			return src;
		}
		return tinted;
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
		if (!lightmapTintActive()) {
			return;
		}
		VoidmarkConfig config = VoidmarkConfig.get();
		state.needsUpdate = true;
		float strength = config.worldTintStrength;
		int rgb = config.worldTintRgb;
		state.skyLightColor = apply(state.skyLightColor, rgb, strength);
		state.ambientColor = apply(state.ambientColor, rgb, strength);
		state.blockLightTint = apply(state.blockLightTint, rgb, strength);
	}

	public static void tintQuad(QuadInstance quad) {
		if (sodiumLoaded() || !lightmapTintActive()) {
			return;
		}
		VoidmarkConfig config = VoidmarkConfig.get();
		if (quad == null) {
			return;
		}
		float strength = config.worldTintStrength;
		int tint = config.worldTintRgb;
		for (int i = 0; i < 4; i++) {
			quad.setColor(i, mixArgb(quad.getColor(i), tint, strength));
		}
	}

	public static void syncChunkMeshes(Minecraft client) {
		VoidmarkConfig config = VoidmarkConfig.get();
		int key = config.worldTintEnabled
			? (1 << 24) | (config.worldTintRgb & 0xFFFFFF) | (Math.round(config.worldTintStrength * 127f) << 25) | (config.worldTintUsesLightmap() ? (1 << 31) : 0)
			: 0;
		if (key == lastMeshKey) {
			return;
		}
		lastMeshKey = key;
		if (sodiumLoaded()) {
			return;
		}
		if (client != null && client.levelRenderer != null) {
			client.levelRenderer.allChanged();
		}
	}

	public static int mixArgb(int from, int toRgb, float strength) {
		float t = Mth.clamp(strength, 0f, 1f);
		int a = ARGB.alpha(from);
		int r = lerpChannel(ARGB.red(from), (toRgb >> 16) & 0xFF, t);
		int g = lerpChannel(ARGB.green(from), (toRgb >> 8) & 0xFF, t);
		int b = lerpChannel(ARGB.blue(from), toRgb & 0xFF, t);
		return ARGB.color(a == 0 ? 255 : a, r, g, b);
	}

	private static Vector3f apply(Vector3fc original, int rgb, float strength) {
		float t = Mth.clamp(strength, 0f, 1f);
		float tr = ((rgb >> 16) & 0xFF) / 255f;
		float tg = ((rgb >> 8) & 0xFF) / 255f;
		float tb = (rgb & 0xFF) / 255f;
		if (original == null) {
			return new Vector3f(tr, tg, tb);
		}
		float mixedX = original.x() * (1f - t) + tr * t;
		float mixedY = original.y() * (1f - t) + tg * t;
		float mixedZ = original.z() * (1f - t) + tb * t;
		float filterR = 1f - t + tr * t;
		float filterG = 1f - t + tg * t;
		float filterB = 1f - t + tb * t;
		return new Vector3f(mixedX * filterR, mixedY * filterG, mixedZ * filterB);
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
