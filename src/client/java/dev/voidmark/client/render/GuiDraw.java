package dev.voidmark.client.render;

import dev.voidmark.Voidmark;
import dev.voidmark.client.ui.MenuFont;
import dev.voidmark.client.visual.WorldTint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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

	public static boolean scissor(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		int x0 = Math.round(x);
		int y0 = Math.round(y);
		int x1 = Math.round(x + w);
		int y1 = Math.round(y + h);
		if (x1 <= x0 || y1 <= y0) {
			return false;
		}
		graphics.enableScissor(x0, y0, x1, y1);
		return true;
	}

	public static void disableScissor(GuiGraphicsExtractor graphics) {
		graphics.disableScissor();
	}

	public static void fill(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int color) {
		if (w <= 0 || h <= 0 || (color >>> 24) == 0) {
			return;
		}
		if (menuSmooth()) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(x, y);
			graphics.pose().scale(w, h);
			graphics.fill(0, 0, 1, 1, color);
			graphics.pose().popMatrix();
			return;
		}
		// Integer quads stay in one GUI batch. Pose scale on every rect is what
		// made a full HUD set hitch; menus still use the smooth path above.
		int x0 = (int) Math.floor(x);
		int y0 = (int) Math.floor(y);
		int x1 = Math.max(x0 + 1, (int) Math.ceil(x + w));
		int y1 = Math.max(y0 + 1, (int) Math.ceil(y + h));
		graphics.fill(x0, y0, x1, y1, color);
	}

	/**
	 * Click GUI / title / pause / options: the old translate+scale fill so
	 * rounded chrome meets under 90%/75% menu scale. Inventory and chat keep
	 * the cheap HUD path.
	 */
	private static boolean menuSmooth() {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return false;
		}
		Screen screen = client.screen;
		if (screen == null) {
			return false;
		}
		if (screen.isInGameUi() || screen instanceof ChatScreen || screen instanceof AbstractContainerScreen) {
			return false;
		}
		return true;
	}

	/** 18px item well: 1px outline, flat fill. Rounded panels are too expensive per slot. */
	public static void well(GuiGraphicsExtractor graphics, float x, float y, float size, int fill, int outline) {
		if (size <= 2f) {
			return;
		}
		fill(graphics, x, y, size, size, outline);
		fill(graphics, x + 1f, y + 1f, size - 2f, size - 2f, fill);
	}

	public static void fillGradient(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int top, int bottom) {
		if (w <= 0 || h <= 0) {
			return;
		}
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
		if (radius <= 0 || (color >>> 24) < 2) {
			return;
		}
		if (radius <= 1.05f) {
			fill(graphics, cx - radius, cy - radius, radius * 2f, radius * 2f, color);
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
		graphics.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, u, v, 1, 1, regionW, regionH, texW, texH, 0xFFFFFFFF);
		graphics.pose().popMatrix();
	}

	/**
	 * Blits a square texture then paints the four corner ears so the photo
	 * follows the same rounded silhouette as {@link #rounded}.
	 */
	public static void roundedBlit(
		GuiGraphicsExtractor graphics,
		Identifier id,
		float x,
		float y,
		float w,
		float h,
		float radius,
		int texSize,
		int earColor
	) {
		blit(graphics, id, x, y, w, h, 0f, 0f, texSize, texSize, texSize, texSize);
		paintRoundedEars(graphics, x, y, w, h, radius, earColor);
	}

	private static void paintRoundedEars(
		GuiGraphicsExtractor graphics,
		float x,
		float y,
		float w,
		float h,
		float radius,
		int color
	) {
		float r = Math.min(radius, Math.min(w, h) / 2f);
		if (r < 0.75f) {
			return;
		}
		int rows = Math.max(8, Math.round(r * 4f));
		float rowH = r / rows;
		for (int i = 0; i < rows; i++) {
			float ly = i * rowH;
			float dy = r - (ly + rowH * 0.5f);
			float chord = (float) Math.sqrt(Math.max(0f, r * r - dy * dy));
			float ear = r - chord;
			if (ear <= 0.02f) {
				continue;
			}
			float top = y + ly;
			float bottom = y + h - ly - rowH;
			fill(graphics, x, top, ear, rowH + 0.2f, color);
			fill(graphics, x + w - ear, top, ear, rowH + 0.2f, color);
			fill(graphics, x, bottom, ear, rowH + 0.2f, color);
			fill(graphics, x + w - ear, bottom, ear, rowH + 0.2f, color);
		}
	}

	public static void rounded(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int color) {
		if (w <= 0 || h <= 0 || (color >>> 24) == 0) {
			return;
		}
		float r = Math.min(radius, Math.min(w, h) / 2f);
		if (r < 0.75f) {
			fill(graphics, x, y, w, h, color);
			return;
		}
		fill(graphics, x + r, y, w - 2f * r, h, color);
		if (menuSmooth()) {
			fill(graphics, x, y + r, r, h - 2f * r, color);
			fill(graphics, x + w - r, y + r, r, h - 2f * r, color);
		} else {
			sideStrip(graphics, x, y, w, h, r, color, true);
			sideStrip(graphics, x, y, w, h, r, color, false);
		}
		corner(graphics, x, y, r, 0f, 0f, color);
		corner(graphics, x + w - r, y, r, CIRCLE_HALF, 0f, color);
		corner(graphics, x, y + h - r, r, 0f, CIRCLE_HALF, color);
		corner(graphics, x + w - r, y + h - r, r, CIRCLE_HALF, CIRCLE_HALF, color);
	}

	public static void roundLeft(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int color) {
		float r = Math.min(radius, Math.min(w, h) / 2f);
		fill(graphics, x + r, y, w - r, h, color);
		if (menuSmooth()) {
			fill(graphics, x, y + r, r, h - 2f * r, color);
		} else {
			sideStrip(graphics, x, y, w, h, r, color, true);
		}
		corner(graphics, x, y, r, 0f, 0f, color);
		corner(graphics, x, y + h - r, r, 0f, CIRCLE_HALF, color);
	}

	public static void roundRight(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int color) {
		float r = Math.min(radius, Math.min(w, h) / 2f);
		fill(graphics, x, y, w - r, h, color);
		if (menuSmooth()) {
			fill(graphics, x + w - r, y + r, r, h - 2f * r, color);
		} else {
			sideStrip(graphics, x, y, w, h, r, color, false);
		}
		corner(graphics, x + w - r, y, r, CIRCLE_HALF, 0f, color);
		corner(graphics, x + w - r, y + h - r, r, CIRCLE_HALF, CIRCLE_HALF, color);
	}

	/**
	 * Middle band of a rounded rect. Integer fills plus the click GUI's 90%/75%
	 * pose left a 1px column at {@code x+r} / {@code x+w-r} (outline showing
	 * through the card). Overlap the center in the side band only — not into
	 * the corner squares — so the seam closes without a per-rect pose.
	 */
	private static void sideStrip(
		GuiGraphicsExtractor graphics,
		float x,
		float y,
		float w,
		float h,
		float r,
		int color,
		boolean left
	) {
		float overlap = 2f;
		float stripH = h - 2f * r;
		if (stripH <= 0f) {
			return;
		}
		if (left) {
			fill(graphics, x, y + r, r + overlap, stripH, color);
		} else {
			fill(graphics, x + w - r - overlap, y + r, r + overlap, stripH, color);
		}
	}

	public static void panel(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int fill, int outline) {
		panel(graphics, x, y, w, h, radius, fill, outline, 0);
	}

	public static void panel(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int fill, int outline, int accent) {
		panel(graphics, x, y, w, h, radius, fill, outline, accent, false);
	}

	public static void panel(
		GuiGraphicsExtractor graphics,
		float x,
		float y,
		float w,
		float h,
		float radius,
		int fill,
		int outline,
		int accent,
		boolean accentRight
	) {
		rounded(graphics, x, y, w, h, radius, outline);
		rounded(graphics, x + 1, y + 1, w - 2, h - 2, Math.max(0.5f, radius - 1f), fill);
		if ((accent & 0xFF000000) != 0) {
			if (accentRight) {
				accentRight(graphics, x, y, w, h, radius, 3f, accent);
			} else {
				accentLeft(graphics, x, y, h, radius, 3f, accent);
			}
		}
	}

	/**
	 * Settings / subsetting popover: pane fill first, then a hairline stroke.
	 * Fill-first matters because the pane color is translucent — drawing the
	 * accent as a full underlay would tint the whole sheet.
	 */
	public static void sheet(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int fill, int outline) {
		rounded(graphics, x, y, w, h, radius, fill);
		roundedOutline(graphics, x, y, w, h, radius, outline, 0.5f);
	}

	public static void roundedOutline(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int color, float thickness) {
		if ((color & 0xFF000000) == 0 || w <= 0 || h <= 0) {
			return;
		}
		float t = Math.max(0.5f, thickness);
		float r = Math.min(radius, Math.min(w, h) / 2f);
		if (r < 0.75f) {
			border(graphics, x, y, w, h, color, t);
			return;
		}
		fill(graphics, x + r, y, w - 2f * r, t, color);
		fill(graphics, x + r, y + h - t, w - 2f * r, t, color);
		fill(graphics, x, y + r, t, h - 2f * r, color);
		fill(graphics, x + w - t, y + r, t, h - 2f * r, color);
		cornerArc(graphics, x + r, y + r, r, t, Math.PI, Math.PI * 1.5, color);
		cornerArc(graphics, x + w - r, y + r, r, t, Math.PI * 1.5, Math.PI * 2.0, color);
		cornerArc(graphics, x + w - r, y + h - r, r, t, 0.0, Math.PI * 0.5, color);
		cornerArc(graphics, x + r, y + h - r, r, t, Math.PI * 0.5, Math.PI, color);
	}

	private static void cornerArc(
		GuiGraphicsExtractor graphics,
		float cx,
		float cy,
		float radius,
		float thickness,
		double a0,
		double a1,
		int color
	) {
		int steps = Math.max(10, Math.round(radius * 4f));
		float mid = radius - thickness * 0.5f;
		float size = thickness;
		for (int i = 0; i <= steps; i++) {
			double a = a0 + (a1 - a0) * (i / (double) steps);
			float px = cx + (float) Math.cos(a) * mid;
			float py = cy + (float) Math.sin(a) * mid;
			fill(graphics, px - size * 0.5f, py - size * 0.5f, size, size, color);
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

	/**
	 * Vertical right rail of the panel's full height. Mirrors {@link #accentLeft} so a HUD
	 * pane on the right edge can keep its rail against the screen.
	 */
	public static void accentRight(
		GuiGraphicsExtractor graphics,
		float x,
		float y,
		float w,
		float h,
		float radius,
		float thickness,
		int accent
	) {
		float t = Math.max(1.5f, thickness);
		float r = Math.min(radius, h * 0.5f);
		if (r < 0.75f) {
			fill(graphics, x + w - t, y, t, h, accent);
			return;
		}
		float strip = Math.min(t, Math.max(1f, r - 0.35f));
		float mid = h - 2f * r;
		if (mid > 0.5f) {
			fill(graphics, x + w - strip, y + r, strip, mid, accent);
		}
		int regionU = Math.max(1, Math.round((strip / r) * CIRCLE_HALF));
		float u = CIRCLE_TEX - regionU;
		cornerBand(graphics, x + w - strip, y, r, strip, u, 0f, accent);
		cornerBand(graphics, x + w - strip, y + h - r, r, strip, u, CIRCLE_HALF, accent);
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
		graphics.text(font, value, Math.round(x), Math.round(y), color, shadow);
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
		graphics.text(font, value, Math.round(x), Math.round(y), color, shadow);
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

	public static void brand(GuiGraphicsExtractor graphics, Font font, String value, float x, float y, int color) {
		text(graphics, font, MenuFont.brand(value), x, y - 1.0f, color, false);
	}

	public static void brandSmall(GuiGraphicsExtractor graphics, Font font, String value, float x, float y, int color) {
		text(graphics, font, MenuFont.brandSmall(value), x, y - 1.0f, color, false);
	}

	public static void hud(GuiGraphicsExtractor graphics, Font font, Component value, float x, float y, int color) {
		text(graphics, font, MenuFont.applyBody(value), x, y, color, false);
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

	public static int brandWidth(Font font, String value) {
		return font.width(MenuFont.brand(value));
	}

	public static int brandSmallWidth(Font font, String value) {
		return font.width(MenuFont.brandSmall(value));
	}

	public static String ellipsize(Font font, String value, float max, boolean small) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		int full = small ? smallWidth(font, value) : menuWidth(font, value);
		if (full <= max) {
			return value;
		}
		int lo = 1;
		int hi = value.length();
		String best = "..";
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			String shown = value.substring(0, mid) + "..";
			int width = small ? smallWidth(font, shown) : menuWidth(font, shown);
			if (width <= max) {
				best = shown;
				lo = mid + 1;
			} else {
				hi = mid - 1;
			}
		}
		return best;
	}

	public static int hudWidth(Font font, Component value) {
		return font.width(MenuFont.applyBody(value));
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
