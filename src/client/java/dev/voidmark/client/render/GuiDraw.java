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

	public static void knob(GuiGraphicsExtractor graphics, float cx, float cy, float radius, int color) {
		fill(graphics, cx - radius, cy - radius * 0.62f, radius * 2f, radius * 1.24f, color);
		fill(graphics, cx - radius * 0.62f, cy - radius, radius * 1.24f, radius * 2f, color);
	}

	public static void pill(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int color) {
		fill(graphics, x + h * 0.18f, y, w - h * 0.36f, h, color);
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
		knob(graphics, x + 4, y + 5, 4.2f, 0xFF2FB5FF);
		knob(graphics, x + 8, y + 4, 3.6f, 0xFF34D399);
		knob(graphics, x + 6.5f, y + 8, 3.2f, 0xFF000000 | rgb);
	}

	public static void chevron(GuiGraphicsExtractor graphics, float x, float y, int color) {
		fill(graphics, x, y, 5, 1, color);
		fill(graphics, x + 1, y + 1, 3, 1, color);
		fill(graphics, x + 2, y + 2, 1, 1, color);
	}

	public static void floppy(GuiGraphicsExtractor graphics, float x, float y, int color) {
		fill(graphics, x, y, 8, 8, color);
		fill(graphics, x + 2, y + 1, 4, 3, 0xFF070B10);
		fill(graphics, x + 1, y + 5, 6, 2, 0xFF070B10);
	}

	public static void gear(GuiGraphicsExtractor graphics, float x, float y, int color) {
		knob(graphics, x + 4, y + 4, 3.4f, color);
		fill(graphics, x + 3, y, 2, 8, color);
		fill(graphics, x, y + 3, 8, 2, color);
	}

	public static void bell(GuiGraphicsExtractor graphics, float x, float y, int color) {
		knob(graphics, x + 4, y + 3, 3, color);
		fill(graphics, x + 1, y + 4, 6, 2, color);
		fill(graphics, x + 3, y + 7, 2, 1, color);
	}

	public static void search(GuiGraphicsExtractor graphics, float x, float y, int color) {
		knob(graphics, x + 3, y + 3, 3, color);
		fill(graphics, x + 2, y + 2, 2, 2, 0xFF070B10);
		fill(graphics, x + 5, y + 5, 3, 2, color);
	}

	public static void iconWorld(GuiGraphicsExtractor graphics, float x, float y, int color) {
		knob(graphics, x + 4, y + 4, 4, color);
		fill(graphics, x + 3, y + 1, 2, 6, 0xFF080C12);
	}

	public static void iconView(GuiGraphicsExtractor graphics, float x, float y, int color) {
		fill(graphics, x + 1, y + 2, 7, 5, color);
		fill(graphics, x + 2, y + 3, 5, 3, 0xFF080C12);
	}

	public static void iconBox(GuiGraphicsExtractor graphics, float x, float y, int color) {
		border(graphics, x + 1, y + 1, 7, 7, color, 1);
	}

	public static void iconHud(GuiGraphicsExtractor graphics, float x, float y, int color) {
		fill(graphics, x + 1, y + 6, 7, 1, color);
		knob(graphics, x + 4, y + 3, 2.6f, color);
	}

	public static void iconStatus(GuiGraphicsExtractor graphics, float x, float y, int color) {
		fill(graphics, x + 1, y + 6, 1, 1, color);
		fill(graphics, x + 3, y + 4, 1, 3, color);
		fill(graphics, x + 5, y + 2, 1, 5, color);
		fill(graphics, x + 7, y + 1, 1, 6, color);
	}
}
