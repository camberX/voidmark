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
	public int colorRgb = 0xC084FC;

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

	public static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	public static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}
}
