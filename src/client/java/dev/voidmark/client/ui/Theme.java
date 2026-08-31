package dev.voidmark.client.ui;

import dev.voidmark.client.config.VoidmarkConfig;

public final class Theme {
	public static int ACCENT = 0xFF2FB5FF;
	public static int ACCENT_DIM = 0xFF1A6FA8;
	public static int WINDOW = 0xFF0B0E14;
	public static int SIDEBAR = 0xB3142032;
	public static int NAV_PILL = 0xE01E5F8C;
	public static int CARD = 0xFF12151C;
	public static int CARD_HOVER = 0xFF171B24;
	public static int LINE = 0xFF1C2430;
	public static int TEXT = 0xFFF2F4F7;
	public static int MUTED = 0xFF8A9AAB;
	public static int HEADER = 0xFFC4CED8;
	public static int TRACK = 0xFF1A222C;
	public static int OFF = 0xFF3D4A58;
	public static int DANGER = 0xFFE8B86D;
	public static int WARN = 0xFFF5C16C;
	public static float WINDOW_RADIUS = 10f;
	public static float PILL_RADIUS = 8f;

	public static final Swatch[] PRESETS = {
		new Swatch("Cyan", 0x2FB5FF),
		new Swatch("Blue", 0x4D8DFF),
		new Swatch("Purple", 0xA78BFA),
		new Swatch("Pink", 0xF472B6),
		new Swatch("Red", 0xFB7185),
		new Swatch("Orange", 0xFB923C),
		new Swatch("Green", 0x34D399),
		new Swatch("White", 0xE5E7EB)
	};

	private Theme() {
	}

	public static void refresh() {
		int rgb = VoidmarkConfig.get().themeAccentRgb & 0xFFFFFF;
		ACCENT = 0xFF000000 | rgb;
		ACCENT_DIM = 0xFF000000 | mix(rgb, 0x0B0E14, 0.45f);
		NAV_PILL = withAlpha(mix(0x152030, rgb, 0.55f), 224);
		SIDEBAR = withAlpha(mix(0x142032, rgb, 0.18f), 179);
	}

	public static void applyPreset(Swatch swatch) {
		VoidmarkConfig config = VoidmarkConfig.get();
		config.themeAccentRgb = swatch.rgb;
		config.themePreset = swatch.name.toLowerCase();
		refresh();
	}

	public static void applyCustom(int rgb) {
		VoidmarkConfig config = VoidmarkConfig.get();
		config.themeAccentRgb = rgb & 0xFFFFFF;
		config.themePreset = "custom";
		refresh();
	}

	public static int withAlpha(int color, int alpha) {
		return (alpha << 24) | (color & 0x00FFFFFF);
	}

	public static int mix(int fromRgb, int toRgb, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int r = lerp((fromRgb >> 16) & 0xFF, (toRgb >> 16) & 0xFF, t);
		int g = lerp((fromRgb >> 8) & 0xFF, (toRgb >> 8) & 0xFF, t);
		int b = lerp(fromRgb & 0xFF, toRgb & 0xFF, t);
		return (r << 16) | (g << 8) | b;
	}

	private static int lerp(int from, int to, float t) {
		return Math.round(from + (to - from) * t);
	}

	public record Swatch(String name, int rgb) {
	}
}
