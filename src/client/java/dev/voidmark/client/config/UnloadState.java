package dev.voidmark.client.config;

public final class UnloadState {
	private static boolean unloaded;
	private static boolean worldTint;
	private static boolean skyTint;
	private static boolean fog;
	private static boolean aspect;
	private static boolean markers;

	private UnloadState() {
	}

	public static boolean isUnloaded() {
		return unloaded;
	}

	public static void toggle() {
		if (unloaded) {
			restore();
		} else {
			unload();
		}
	}

	public static void unload() {
		VoidmarkConfig config = VoidmarkConfig.get();
		worldTint = config.worldTintEnabled;
		skyTint = config.skyTintEnabled;
		fog = config.fogEnabled;
		aspect = config.aspectEnabled;
		markers = config.markersEnabled;
		config.worldTintEnabled = false;
		config.skyTintEnabled = false;
		config.fogEnabled = false;
		config.aspectEnabled = false;
		config.markersEnabled = false;
		unloaded = true;
		config.save();
	}

	public static void restore() {
		VoidmarkConfig config = VoidmarkConfig.get();
		config.worldTintEnabled = worldTint;
		config.skyTintEnabled = skyTint;
		config.fogEnabled = fog;
		config.aspectEnabled = aspect;
		config.markersEnabled = markers;
		unloaded = false;
		config.save();
	}

	public static void markDirty() {
		unloaded = false;
	}
}
