package dev.voidmark.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.voidmark.Voidmark;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VoidmarkConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("voidmark.json");
	private static VoidmarkConfig instance = new VoidmarkConfig();

	public boolean markersEnabled = true;
	public boolean hudEnabled = true;
	public boolean tracersEnabled = true;
	public boolean boxFill = true;
	public boolean boxOutline = true;
	public boolean throughWalls = true;
	public boolean onlyInTheEnd = true;
	public boolean forceEnable = false;
	public boolean particleDetection = true;
	public boolean blockScan = true;
	public int scanRadius = 48;
	public float fillOpacity = 0.32f;
	public int colorRgb = 0x2FB5FF;
	public boolean worldTintEnabled = false;
	public int worldTintRgb = 0x2FB5FF;
	public float worldTintStrength = 0.70f;
	public String worldTintMode = "shader";
	public boolean skyTintEnabled = false;
	public int skyTintRgb = 0x1B4F8A;
	public float skyTintStrength = 0.70f;
	public boolean matchSkyToWorld = true;
	public boolean fogEnabled = false;
	public int fogRgb = 0x8EC8FF;
	public float fogStart = 0.12f;
	public float fogEnd = 0.72f;
	public float fogDensity = 1.0f;
	public boolean matchFogToWorld = false;
	public boolean aspectEnabled = false;
	public float aspectRatio = 1.0f;
	public int themeAccentRgb = 0x2FB5FF;
	public int themePaneRgb = 0x0B0E14;
	public String themePreset = "cyan";
	public boolean uiAnimations = true;
	public boolean watermarkEnabled = true;
	public boolean watermarkFps = true;
	public boolean watermarkPing = true;
	public boolean watermarkTime = true;
	public boolean watermarkName = false;
	public boolean inventoryHudEnabled = true;
	public String inventoryHudAnchor = "bottom_right";
	public float inventoryHudScale = 1.0f;
	public String capeUrl = "";
	public String capePath = "";
	public boolean nickEnabled = false;
	public String nick = "";

	private VoidmarkConfig() {
	}

	public static VoidmarkConfig get() {
		return instance;
	}

	public static void load() {
		if (!Files.isRegularFile(PATH)) {
			instance.save();
			return;
		}

		try (Reader reader = Files.newBufferedReader(PATH)) {
			VoidmarkConfig loaded = GSON.fromJson(reader, VoidmarkConfig.class);
			if (loaded != null) {
				loaded.scanRadius = clamp(loaded.scanRadius, 16, 80);
				loaded.fillOpacity = clamp(loaded.fillOpacity, 0.08f, 0.85f);
				loaded.worldTintStrength = clamp(loaded.worldTintStrength, 0f, 1f);
				loaded.worldTintMode = normalizeWorldTintMode(loaded.worldTintMode);
				loaded.skyTintStrength = clamp(loaded.skyTintStrength, 0f, 1f);
				loaded.fogStart = clamp(loaded.fogStart, 0f, 0.95f);
				loaded.fogEnd = clamp(loaded.fogEnd, 0.05f, 1f);
				loaded.fogDensity = clamp(loaded.fogDensity, 0f, 1f);
				loaded.aspectRatio = clamp(loaded.aspectRatio, 0.50f, 1.20f);
				boolean legacyTheme = loaded.themePreset == null || loaded.themePreset.isBlank();
				if (legacyTheme) {
					loaded.themeAccentRgb = 0x2FB5FF;
					loaded.themePaneRgb = 0x0B0E14;
					loaded.themePreset = "cyan";
					loaded.uiAnimations = true;
					loaded.watermarkEnabled = true;
					loaded.watermarkFps = true;
					loaded.watermarkPing = true;
					loaded.watermarkTime = true;
				} else {
					loaded.themeAccentRgb = loaded.themeAccentRgb & 0xFFFFFF;
					if (loaded.themeAccentRgb == 0) {
						loaded.themeAccentRgb = 0x2FB5FF;
					}
					loaded.themePaneRgb = loaded.themePaneRgb & 0xFFFFFF;
					if (loaded.themePaneRgb == 0) {
						loaded.themePaneRgb = 0x0B0E14;
					}
				}
				if (loaded.capeUrl == null) {
					loaded.capeUrl = "";
				}
				if (loaded.capePath == null) {
					loaded.capePath = "";
				}
				if (loaded.nick == null) {
					loaded.nick = "";
				}
				loaded.inventoryHudAnchor = normalizeInventoryHudAnchor(loaded.inventoryHudAnchor);
				loaded.inventoryHudScale = clamp(loaded.inventoryHudScale, 0.70f, 1.40f);
				instance = loaded;
			}
		} catch (Exception exception) {
			Voidmark.LOGGER.warn("Could not read voidmark.json, using defaults", exception);
			instance = new VoidmarkConfig();
		}
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException exception) {
			Voidmark.LOGGER.warn("Could not write voidmark.json", exception);
		}
	}

	public int fillColor() {
		int alpha = Math.round(fillOpacity * 255.0f);
		return (alpha << 24) | (colorRgb & 0xFFFFFF);
	}

	public int lineColor() {
		return 0xFF000000 | (colorRgb & 0xFFFFFF);
	}

	public boolean worldTintUsesLightmap() {
		return "lightmap".equalsIgnoreCase(worldTintMode);
	}

	public void cycleWorldTintMode() {
		worldTintMode = worldTintUsesLightmap() ? "shader" : "lightmap";
	}

	public String worldTintModeLabel() {
		return worldTintUsesLightmap() ? "Lightmap" : "Shader";
	}

	public void cycleInventoryHudAnchor() {
		inventoryHudAnchor = switch (inventoryHudAnchor) {
			case "top_left" -> "top_right";
			case "top_right" -> "bottom_right";
			case "bottom_right" -> "bottom_left";
			default -> "top_left";
		};
	}

	public String inventoryHudAnchorLabel() {
		return switch (inventoryHudAnchor) {
			case "top_left" -> "Top left";
			case "top_right" -> "Top right";
			case "bottom_left" -> "Bottom left";
			default -> "Bottom right";
		};
	}

	public static String normalizeInventoryHudAnchor(String anchor) {
		if (anchor == null) {
			return "bottom_right";
		}
		return switch (anchor) {
			case "top_left", "top_right", "bottom_left", "bottom_right" -> anchor;
			default -> "bottom_right";
		};
	}

	public static String normalizeWorldTintMode(String mode) {
		return "lightmap".equalsIgnoreCase(mode) ? "lightmap" : "shader";
	}

	public static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	public static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}
}
