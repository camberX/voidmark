package dev.voidmark.client.ui;

public final class Theme {
	public static final int ACCENT = 0xFF2FB5FF;
	public static final int ACCENT_DIM = 0xFF1A6FA8;
	public static final int WINDOW = 0xFF0B0E14;
	public static final int SIDEBAR = 0xB3142032;
	public static final int NAV_PILL = 0xE01E5F8C;
	public static final int CARD = 0xFF12151C;
	public static final int CARD_HOVER = 0xFF171B24;
	public static final int LINE = 0xFF1C2430;
	public static final int TEXT = 0xFFF2F4F7;
	public static final int MUTED = 0xFF8A9AAB;
	public static final int HEADER = 0xFFC4CED8;
	public static final int TRACK = 0xFF1A222C;
	public static final int OFF = 0xFF3D4A58;
	public static final int DANGER = 0xFF5EEAD4;
	public static final float WINDOW_RADIUS = 10f;
	public static final float PILL_RADIUS = 8f;

	private Theme() {
	}

	public static int withAlpha(int color, int alpha) {
		return (alpha << 24) | (color & 0x00FFFFFF);
	}
}
