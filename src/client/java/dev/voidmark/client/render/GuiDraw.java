package dev.voidmark.client.render;

import dev.voidmark.Voidmark;
import dev.voidmark.client.ui.MenuFont;
import dev.voidmark.client.visual.WorldTint;
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

	/** Smooth HSV saturation/value square: 1px columns, vertical value gradient per column. */
	public static void hsvSquare(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float hue) {
		int cols = Math.max(1, Math.round(w));
		float cw = w / cols;
		for (int i = 0; i < cols; i++) {
			float sat = cols == 1 ? 1f : i / (float) (cols - 1);
			int top = 0xFF000000 | WorldTint.hsvToRgb(hue, sat, 1f);
			int bot = 0xFF000000 | WorldTint.hsvToRgb(hue, sat, 0f);
			fillGradient(graphics, x + i * cw, y, cw + 0.35f, h, top, bot);
		}
	}

	/** Smooth hue strip: 1px columns of full-saturation color. */
	public static void hueBar(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		int cols = Math.max(1, Math.round(w));
		float cw = w / cols;
		for (int i = 0; i < cols; i++) {
			float hue = cols == 1 ? 0f : (i / (float) (cols - 1)) * 360f;
			fill(graphics, x + i * cw, y, cw + 0.35f, h, 0xFF000000 | WorldTint.hsvToRgb(hue, 1f, 1f));
		}
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

	public static void blit(GuiGraphicsExtractor graphics, Identifier id, float x, float y, float w, float h, float u, float v, int regionW, int regionH, int texW, int texH) {
		if (w <= 0 || h <= 0) {
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(w, h);
		graphics.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, u, v, 1, 1, regionW, regionH, texW, texH);
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
		panel(graphics, x, y, w, h, radius, fill, outline, 0);
	}

	public static void panel(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int fill, int outline, int accent) {
		rounded(graphics, x, y, w, h, radius, outline);
		rounded(graphics, x + 1, y + 1, w - 2, h - 2, Math.max(0.5f, radius - 1f), fill);
		if ((accent & 0xFF000000) != 0) {
			accentLeft(graphics, x, y, h, radius, 3f, accent);
		}
	}

	/**
	 * Vertical left rail of the panel's full height. The outer edge follows the
	 * rounded silhouette; nothing is drawn along the top or bottom edges.
	 */
	public static void accentLeft(
		GuiGraphicsExtractor graphics,
		float x,
		float y,
		float h,
		float radius,
		float thickness,
		int accent
	) {
		float t = Math.max(1.5f, thickness);
		float r = Math.min(radius, h * 0.5f);
		if (r < 0.75f) {
			fill(graphics, x, y, t, h, accent);
			return;
		}
		// Keep the strip narrower than the radius so the rail never runs onto the top/bottom.
		float strip = Math.min(t, Math.max(1f, r - 0.35f));
		float mid = h - 2f * r;
		if (mid > 0.5f) {
			fill(graphics, x, y + r, strip, mid, accent);
		}
		cornerBand(graphics, x, y, r, strip, 0f, 0f, accent);
		cornerBand(graphics, x, y + h - r, r, strip, 0f, CIRCLE_HALF, accent);
	}

	/** Left {@code thickness} pixels of a quarter-circle so the rail follows the arc without wrapping. */
	private static void cornerBand(
		GuiGraphicsExtractor graphics,
		float x,
		float y,
		float radius,
		float thickness,
		float u,
		float v,
		int color
	) {
		if (radius <= 0 || thickness <= 0) {
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(thickness, radius);
		int regionU = Math.max(1, Math.round((thickness / radius) * CIRCLE_HALF));
		graphics.blit(
			RenderPipelines.GUI_TEXTURED,
			CIRCLE,
			0,
			0,
			u,
			v,
			1,
			1,
			regionU,
			CIRCLE_HALF,
			CIRCLE_TEX,
			CIRCLE_TEX,
			color
		);
		graphics.pose().popMatrix();
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
		text(graphics, font, MenuFont.icon(glyph), x, y + 0.5f, color, false);
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
