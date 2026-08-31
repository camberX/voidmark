package dev.voidmark.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;

public final class GuiDraw {
	private GuiDraw() {
	}

	public static void fill(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int color) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(w, h);
		graphics.fill(0, 0, 1, 1, color);
		graphics.pose().popMatrix();
	}

	public static void fillGradient(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int top, int bottom) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(w, h);
		graphics.fillGradient(0, 0, 1, 1, top, bottom);
		graphics.pose().popMatrix();
	}

	public static void border(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int color, float thickness) {
		fill(graphics, x, y, w, thickness, color);
		fill(graphics, x, y + h - thickness, w, thickness, color);
		fill(graphics, x, y + thickness, thickness, h - thickness * 2, color);
		fill(graphics, x + w - thickness, y + thickness, thickness, h - thickness * 2, color);
	}

	public static void text(GuiGraphicsExtractor graphics, Font font, String value, float x, float y, int color, boolean shadow) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.text(font, value, 0, 0, color, shadow);
		graphics.pose().popMatrix();
	}

	public static void text(GuiGraphicsExtractor graphics, Font font, String value, float x, float y, float scale, int color, boolean shadow) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		if (scale != 1.0f) {
			graphics.pose().scale(scale, scale);
		}
		graphics.text(font, value, 0, 0, color, shadow);
		graphics.pose().popMatrix();
	}

	public static boolean hovered(double mouseX, double mouseY, float x, float y, float w, float h) {
		return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
	}

	public static String compass(float deltaYaw) {
		float wrapped = wrapDegrees(deltaYaw);
		float abs = Math.abs(wrapped);
		if (abs < 18) {
			return "ahead";
		}
		if (abs > 162) {
			return "behind";
		}
		return wrapped < 0 ? "left" : "right";
	}

	public static String meters(double distance) {
		if (distance < 10) {
			return String.format(Locale.ROOT, "%.1fm", distance);
		}
		return String.format(Locale.ROOT, "%.0fm", distance);
	}

	public static float wrapDegrees(float value) {
		float wrapped = value % 360.0f;
		if (wrapped >= 180.0f) {
			wrapped -= 360.0f;
		}
		if (wrapped < -180.0f) {
			wrapped += 360.0f;
		}
		return wrapped;
	}

	public static float lerp(float from, float to, float delta) {
		return from + (to - from) * delta;
	}

	public static void knob(GuiGraphicsExtractor graphics, float cx, float cy, float radius, int color) {
		fill(graphics, cx - radius, cy - radius * 0.62f, radius * 2f, radius * 1.24f, color);
		fill(graphics, cx - radius * 0.62f, cy - radius, radius * 1.24f, radius * 2f, color);
	}

	public static void pill(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int color) {
		fill(graphics, x + h * 0.15f, y, w - h * 0.3f, h, color);
		knob(graphics, x + h * 0.45f, y + h * 0.5f, h * 0.5f, color);
		knob(graphics, x + w - h * 0.45f, y + h * 0.5f, h * 0.5f, color);
	}

	public static void hline(GuiGraphicsExtractor graphics, float x, float y, float w, int color) {
		fill(graphics, x, y, w, 1, color);
	}

	public static void rounded(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int color) {
		fill(graphics, x + 2, y, w - 4, h, color);
		fill(graphics, x, y + 2, w, h - 4, color);
		fill(graphics, x + 1, y + 1, w - 2, h - 2, color);
	}

	public static void palette(GuiGraphicsExtractor graphics, float x, float y, int rgb) {
		knob(graphics, x + 5, y + 6, 6, 0xFF2FB5FF);
		knob(graphics, x + 10, y + 5, 5, 0xFF34D399);
		knob(graphics, x + 8, y + 10, 4.5f, 0xFF000000 | rgb);
	}

	public static void plus(GuiGraphicsExtractor graphics, float cx, float cy, float size, int color) {
		fill(graphics, cx - 0.7f, cy - size, 1.4f, size * 2f, color);
		fill(graphics, cx - size, cy - 0.7f, size * 2f, 1.4f, color);
	}

	public static void bone(GuiGraphicsExtractor graphics, float cx, float cy, float radius, int ring) {
		knob(graphics, cx, cy, radius, ring);
		knob(graphics, cx, cy, radius * 0.42f, 0xFFF8FBFF);
		plus(graphics, cx, cy, radius * 0.38f, ring);
	}

	public static void figure(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float stretch, int accent, int body) {
		float widthScale = 2f - stretch;
		float cx = x + w / 2f;
		float headR = 11f;
		float torsoW = 22f * widthScale;
		float torsoH = h * 0.28f;
		float headY = y + 18;
		float torsoY = headY + 16;
		knob(graphics, cx, headY, headR, body);
		fill(graphics, cx - torsoW / 2f, torsoY, torsoW, torsoH, body);
		float armY = torsoY + 6;
		float armW = 10f * widthScale;
		float armH = h * 0.22f;
		fill(graphics, cx - torsoW / 2f - armW + 2, armY, armW, armH, body);
		fill(graphics, cx + torsoW / 2f - 2, armY, armW, armH, body);
		float hipY = torsoY + torsoH - 2;
		float legW = 9f * widthScale;
		float legH = h * 0.28f;
		fill(graphics, cx - 5 * widthScale - legW / 2f, hipY, legW, legH, body);
		fill(graphics, cx + 5 * widthScale - legW / 2f, hipY, legW, legH, body);

		bone(graphics, cx, headY, 7, accent);
		bone(graphics, cx, torsoY + 8, 6, accent);
		bone(graphics, cx - torsoW / 2f - 2, armY + 2, 5, accent);
		bone(graphics, cx + torsoW / 2f + 2, armY + 2, 5, accent);
		bone(graphics, cx, hipY + 4, 6, accent);
		bone(graphics, cx - 8 * widthScale, hipY + legH * 0.55f, 5, accent);
		bone(graphics, cx + 8 * widthScale, hipY + legH * 0.55f, 5, accent);
	}

	public static void iconWorld(GuiGraphicsExtractor graphics, float x, float y, int color) {
		knob(graphics, x + 5, y + 5, 5, color);
		fill(graphics, x + 4, y + 1, 2, 8, color);
	}

	public static void iconView(GuiGraphicsExtractor graphics, float x, float y, int color) {
		fill(graphics, x + 1, y + 2, 9, 6, color);
		fill(graphics, x + 3, y + 3, 5, 4, 0xFF080C12);
	}

	public static void iconBox(GuiGraphicsExtractor graphics, float x, float y, int color) {
		border(graphics, x + 1, y + 1, 8, 8, color, 1);
		fill(graphics, x + 3, y + 3, 4, 4, color);
	}

	public static void iconHud(GuiGraphicsExtractor graphics, float x, float y, int color) {
		fill(graphics, x + 1, y + 7, 8, 2, color);
		knob(graphics, x + 5, y + 4, 3.2f, color);
	}

	public static void iconStatus(GuiGraphicsExtractor graphics, float x, float y, int color) {
		fill(graphics, x + 1, y + 7, 2, 2, color);
		fill(graphics, x + 4, y + 5, 2, 4, color);
		fill(graphics, x + 7, y + 2, 2, 7, color);
	}
}
