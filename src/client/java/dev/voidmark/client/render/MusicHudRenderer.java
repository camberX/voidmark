package dev.voidmark.client.render;

import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.media.CoverArt;
import dev.voidmark.client.media.MediaSession;
import dev.voidmark.client.media.NowPlaying;
import dev.voidmark.client.ui.HudEditorScreen;
import dev.voidmark.client.ui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;

public final class MusicHudRenderer {
	public static final float WIDTH = 248;
	public static final float HEIGHT = 54;
	public static final float HEIGHT_IDLE = 36;
	private static final float COVER = 42;
	private static final float COVER_PAD = 6;

	private static final String ICON = "\uE405";
	private static final String PREV = "\uE045";
	private static final String PLAY = "\uE037";
	private static final String PAUSE = "\uE034";
	private static final String NEXT = "\uE044";

	private static Rect prevHit = Rect.EMPTY;
	private static Rect playHit = Rect.EMPTY;
	private static Rect nextHit = Rect.EMPTY;

	private MusicHudRenderer() {
	}

	public static void init() {
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Voidmark.id("music"),
			MusicHudRenderer::extract
		);
	}

	public static float drawWidth() {
		return WIDTH;
	}

	public static float drawHeight() {
		NowPlaying track = MediaSession.current();
		boolean idle = !track.present() && !HudLayout.editorOpen();
		return idle && VoidmarkConfig.get().musicHideIdle ? 0 : (track.present() || HudLayout.editorOpen() ? HEIGHT : HEIGHT_IDLE);
	}

	public static boolean interactive() {
		return Minecraft.getInstance().screen instanceof ChatScreen;
	}

	public static boolean mouseClicked(MouseButtonEvent event) {
		if (event.button() != 0 || !VoidmarkConfig.get().musicHudEnabled) {
			return false;
		}
		if (Minecraft.getInstance().screen instanceof HudEditorScreen) {
			return false;
		}
		if (!interactive()) {
			return false;
		}
		double x = event.x();
		double y = event.y();
		if (prevHit.contains(x, y)) {
			MediaSession.previous();
			return true;
		}
		if (playHit.contains(x, y)) {
			MediaSession.playPause();
			return true;
		}
		if (nextHit.contains(x, y)) {
			MediaSession.next();
			return true;
		}
		return false;
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.options.hideGui) {
			clearHits();
			return;
		}
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.musicHudEnabled) {
			clearHits();
			return;
		}
		NowPlaying track = MediaSession.current();
		if (!track.present() && config.musicHideIdle && !HudLayout.editorOpen()) {
			clearHits();
			return;
		}
		HudLayout.Box box = HudLayout.box(HudLayout.Id.MUSIC, client.font, graphics.guiWidth(), graphics.guiHeight());
		draw(graphics, client.font, box.x(), box.y(), HudLayout.scale(HudLayout.Id.MUSIC), track);
	}

	public static void draw(GuiGraphicsExtractor graphics, Font font, float x, float y, float scale, NowPlaying track) {
		boolean live = track.present();
		float h = live || HudLayout.editorOpen() ? HEIGHT : HEIGHT_IDLE;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		if (scale != 1.0f) {
			graphics.pose().scale(scale, scale);
		}

		HudChrome.panel(graphics, 0, 0, WIDTH, h, 6, Theme.WINDOW, Theme.LINE, Theme.ACCENT);
		float cover = drawCover(graphics, font, track, live, h);
		float textX = COVER_PAD + cover + 6;

		if (!live) {
			GuiDraw.menu(graphics, font, HudLayout.editorOpen() ? "Music" : "Nothing playing", textX, 8, Theme.TEXT);
			GuiDraw.small(graphics, font, ellipsize(font, MediaSession.hint(), WIDTH - textX - 12, true), textX, 20, Theme.MUTED);
			clearHits();
			graphics.pose().popMatrix();
			return;
		}

		String source = track.sourceLabel();
		float sourceW = GuiDraw.smallWidth(font, source);
		float titleMax = WIDTH - textX - sourceW - 16;
		String title = ellipsize(font, track.title(), titleMax, false);
		String artist = ellipsize(font, track.artistLine(), WIDTH - textX - 12, true);
		GuiDraw.menu(graphics, font, title, textX, 6, Theme.TEXT);
		GuiDraw.small(graphics, font, artist, textX, 16, Theme.MUTED);
		GuiDraw.small(graphics, font, source, WIDTH - 8 - sourceW, 6, Theme.ACCENT);

		String clock = track.clockLine();
		float clockW = GuiDraw.smallWidth(font, clock);
		if (clockW < 4f) {
			clockW = GuiDraw.menuWidth(font, clock);
		}
		float barX = textX;
		float barW = Math.max(24f, WIDTH - textX - clockW - 14);
		float barY = 30;
		GuiDraw.rounded(graphics, barX, barY, barW, 3, 1.5f, Theme.TRACK);
		float filled = Math.max(live ? 2f : 0f, barW * track.progress());
		GuiDraw.rounded(graphics, barX, barY, filled, 3, 1.5f, Theme.ACCENT);
		if (GuiDraw.smallWidth(font, clock) >= 4f) {
			GuiDraw.small(graphics, font, clock, barX + barW + 5, barY - 3, Theme.TEXT);
		} else {
			GuiDraw.menu(graphics, font, clock, barX + barW + 5, barY - 5, Theme.TEXT);
		}

		boolean chat = interactive();
		int control = chat ? Theme.TEXT : Theme.MUTED;
		int active = chat ? Theme.ACCENT : Theme.MUTED;
		float cy = 40;
		float playX = (WIDTH - 14) * 0.5f;
		float prevX = playX - 28;
		float nextX = playX + 28;
		GuiDraw.icon(graphics, font, PREV, prevX, cy, control);
		GuiDraw.icon(graphics, font, track.playing() ? PAUSE : PLAY, playX, cy, active);
		GuiDraw.icon(graphics, font, NEXT, nextX, cy, control);
		if (chat) {
			GuiDraw.small(graphics, font, "click", WIDTH - 8 - GuiDraw.smallWidth(font, "click"), 40, Theme.ACCENT);
		}

		prevHit = screenRect(x, y, scale, prevX - 4, cy - 2, 18, 14);
		playHit = screenRect(x, y, scale, playX - 4, cy - 2, 18, 14);
		nextHit = screenRect(x, y, scale, nextX - 4, cy - 2, 18, 14);
		graphics.pose().popMatrix();
	}

	private static float drawCover(GuiGraphicsExtractor graphics, Font font, NowPlaying track, boolean live, float panelH) {
		float size = Math.min(COVER, panelH - COVER_PAD * 2f);
		float x = COVER_PAD;
		float y = (panelH - size) * 0.5f;
		CoverArt.bind(track);
		GuiDraw.rounded(graphics, x, y, size, size, 5, Theme.CARD);
		if (CoverArt.ready()) {
			int tex = CoverArt.size();
			GuiDraw.roundedBlit(graphics, CoverArt.id(), x, y, size, size, 5, tex, Theme.CARD);
			return size;
		}
		GuiDraw.icon(graphics, font, ICON, x + size * 0.5f - 5f, y + size * 0.5f - 5f, live ? Theme.ACCENT : Theme.MUTED);
		return size;
	}

	private static Rect screenRect(float originX, float originY, float scale, float lx, float ly, float lw, float lh) {
		return new Rect(originX + lx * scale, originY + ly * scale, lw * scale, lh * scale);
	}

	private static void clearHits() {
		prevHit = Rect.EMPTY;
		playHit = Rect.EMPTY;
		nextHit = Rect.EMPTY;
	}

	private static String ellipsize(Font font, String value, float max, boolean small) {
		if (value == null || value.isBlank()) {
			return "";
		}
		if ((small ? GuiDraw.smallWidth(font, value) : GuiDraw.menuWidth(font, value)) <= max) {
			return value;
		}
		String trimmed = value;
		while (trimmed.length() > 1) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
			String shown = trimmed + "..";
			if ((small ? GuiDraw.smallWidth(font, shown) : GuiDraw.menuWidth(font, shown)) <= max) {
				return shown;
			}
		}
		return "..";
	}

	private record Rect(float x, float y, float w, float h) {
		private static final Rect EMPTY = new Rect(0, 0, 0, 0);

		boolean contains(double mx, double my) {
			return w > 0 && h > 0 && mx >= x && mx <= x + w && my >= y && my <= y + h;
		}
	}
}
