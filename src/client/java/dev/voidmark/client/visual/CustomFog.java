package dev.voidmark.client.visual;

import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;

public final class CustomFog {
	private CustomFog() {
	}

	public static void apply(FogData data, Camera camera, int renderDistanceChunks) {
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.fogEnabled || data == null || camera == null) {
			return;
		}

		FogType type = camera.getFluidInCamera();
		if (type == FogType.WATER || type == FogType.LAVA || type == FogType.POWDER_SNOW) {
			return;
		}

		float view = Math.max(16f, renderDistanceChunks * 16f);
		float startFrac = Mth.clamp(config.fogStart, 0f, 0.95f);
		float endFrac = Mth.clamp(Math.max(startFrac + 0.04f, config.fogEnd), 0.05f, 1f);
		float start = view * startFrac;
		float end = view * endFrac;

		data.environmentalStart = start;
		data.environmentalEnd = end;
		data.renderDistanceStart = start;
		data.renderDistanceEnd = end;
		data.skyEnd = end;
		data.cloudEnd = end;

		if (data.color != null) {
			int rgb = fogRgb(config);
			float density = Mth.clamp(config.fogDensity, 0f, 1f);
			data.color.set(
				((rgb >> 16) & 0xFF) / 255f,
				((rgb >> 8) & 0xFF) / 255f,
				(rgb & 0xFF) / 255f,
				density
			);
		}
	}

	public static int fogRgb(VoidmarkConfig config) {
		return config.matchFogToWorld ? config.worldTintRgb : config.fogRgb;
	}
}
