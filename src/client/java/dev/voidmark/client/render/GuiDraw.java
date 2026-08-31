package dev.voidmark.client.render;

import dev.voidmark.Voidmark;
import dev.voidmark.client.ui.MenuFont;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Locale;

public final class GuiDraw {
	private static final Identifier CIRCLE = Voidmark.id("textures/gui/circle.png");
	private static final int CIRCLE_TEX = 64;
	private static final int CIRCLE_HALF = 32;

	private GuiDraw() {
	}

	public static void fill(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int color) {
		if (w <= 0 || h <= 0) {
			return;
		}
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

	public static void circle(GuiGraphicsExtractor graphics, float cx, float cy, float radius, int color) {
		if (radius <= 0) {
			return;
		}
		float d = radius * 2f;
		graphics.pose().pushMatrix();
		graphics.pose().translate(cx - radius, cy - radius);
		graphics.pose().scale(d, d);
		graphics.blit(RenderPipelines.GUI_TEXTURED, CIRCLE, 0, 0, 0f, 0f, 1, 1, CIRCLE_TEX, CIRCLE_TEX, CIRCLE_TEX, CIRCLE_TEX, color);
		graphics.pose().popMatrix();
	}

	private static void corner(GuiGraphicsExtractor graphics, float x, float y, float radius, float u, float v, int color) {
		if (radius <= 0) {
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(radius, radius);
		graphics.blit(RenderPipelines.GUI_TEXTURED, CIRCLE, 0, 0, u, v, 1, 1, CIRCLE_HALF, CIRCLE_HALF, CIRCLE_TEX, CIRCLE_TEX, color);
		graphics.pose().popMatrix();
	}

	public static void rounded(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int color) {
		float r = Math.min(radius, Math.min(w, h) / 2f);
		if (r < 0.75f) {
			fill(graphics, x, y, w, h, color);
			return;
		}
		fill(graphics, x + r, y, w - 2f * r, h, color);
		fill(graphics, x, y + r, r, h - 2f * r, color);
		fill(graphics, x + w - r, y + r, r, h - 2f * r, color);
		corner(graphics, x, y, r, 0f, 0f, color);
		corner(graphics, x + w - r, y, r, CIRCLE_HALF, 0f, color);
		corner(graphics, x, y + h - r, r, 0f, CIRCLE_HALF, color);
		corner(graphics, x + w - r, y + h - r, r, CIRCLE_HALF, CIRCLE_HALF, color);
	}

	public static void roundLeft(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int color) {
		float r = Math.min(radius, Math.min(w, h) / 2f);
		fill(graphics, x + r, y, w - r, h, color);
		fill(graphics, x, y + r, r, h - 2f * r, color);
		corner(graphics, x, y, r, 0f, 0f, color);
		corner(graphics, x, y + h - r, r, 0f, CIRCLE_HALF, color);
	}

	public static void roundRight(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int color) {
		float r = Math.min(radius, Math.min(w, h) / 2f);
		fill(graphics, x, y, w - r, h, color);
		fill(graphics, x + w - r, y + r, r, h - 2f * r, color);
		corner(graphics, x + w - r, y, r, CIRCLE_HALF, 0f, color);
		corner(graphics, x + w - r, y + h - r, r, CIRCLE_HALF, CIRCLE_HALF, color);
	}

	public static void panel(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int fill, int outline) {
		rounded(graphics, x, y, w, h, radius, outline);
		rounded(graphics, x + 1, y + 1, w - 2, h - 2, Math.max(0.5f, radius - 1f), fill);
	}

	public static void text(GuiGraphicsExtractor graphics, Font font, String value, float x, float y, int color, boolean shadow) {
		text(graphics, font, Component.literal(value), x, y, color, shadow);
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

	public static void text(GuiGraphicsExtractor graphics, Font font, Component value, float x, float y, int color, boolean shadow) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.text(font, value, 0, 0, color, shadow);
		graphics.pose().popMatrix();
	}

	public static void menu(GuiGraphicsExtractor graphics, Font font, String value, float x, float y, int color) {
		text(graphics, font, MenuFont.body(value), x, y - 1.0f, color, false);
	}

	public static void small(GuiGraphicsExtractor graphics, Font font, String value, float x, float y, int color) {
		text(graphics, font, MenuFont.small(value), x, y - 1.0f, color, false);
	}

	public static void title(GuiGraphicsExtractor graphics, Font font, String value, float x, float y, int color) {
		text(graphics, font, MenuFont.title(value), x, y - 1.0f, color, false);
	}

	public static void icon(GuiGraphicsExtractor graphics, Font font, String glyph, float x, float y, int color) {
		text(graphics, font, MenuFont.icon(glyph), x, y - 0.5f, color, false);
	}

	public static float middle(float y, float height) {
		return y + (height - 9.0f) * 0.5f;
	}

	public static int iconWidth(Font font, String glyph) {
		return font.width(MenuFont.icon(glyph));
	}

	public static int menuWidth(Font font, String value) {
		return font.width(MenuFont.body(value));
	}

	public static int smallWidth(Font font, String value) {
		return font.width(MenuFont.small(value));
	}

	public static int titleWidth(Font font, String value) {
		return font.width(MenuFont.title(value));
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
		circle(graphics, cx, cy, radius, color);
	}

	public static void pill(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int color) {
		rounded(graphics, x, y, w, h, h / 2f, color);
	}

	public static void hline(GuiGraphicsExtractor graphics, float x, float y, float w, int color) {
		fill(graphics, x, y, w, 1, color);
	}
}
