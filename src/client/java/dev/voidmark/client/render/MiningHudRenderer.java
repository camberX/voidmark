package dev.voidmark.client.render;

import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.mining.MiningTracker;
import dev.voidmark.client.ui.Anim;
import dev.voidmark.client.ui.MenuFont;
import dev.voidmark.client.ui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class MiningHudRenderer {
	public static final float WIDTH = 148;
	private static final float PAD = 5;
	private static final float HEAD = 12;
	private static final float BAR = 2.2f;
	private static final float ROW = 11;

	private MiningHudRenderer() {
	}

	public static void init() {
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Voidmark.id("mining"),
			MiningHudRenderer::extract
		);
	}

	public static float drawWidth() {
		return WIDTH;
	}

	public static float drawHeight() {
		MiningTracker.Snapshot snap = MiningTracker.snapshot();
		if (!snap.present() && !HudLayout.editorOpen()) {
			return 0;
		}
		return heightOf(snap);
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return;
		}
		MiningTracker.Snapshot snap = MiningTracker.snapshot();
		if (snap.alertT() > 0f) {
			drawAlert(graphics, client.font, graphics.guiWidth(), graphics.guiHeight(), snap);
		}
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.miningHudEnabled) {
			return;
		}
		if (!snap.present() && !HudLayout.editorOpen()) {
			return;
		}
		HudLayout.Box box = HudLayout.box(HudLayout.Id.MINING, client.font, graphics.guiWidth(), graphics.guiHeight());
		draw(graphics, client.font, box.x(), box.y(), HudLayout.scale(HudLayout.Id.MINING), snap);
	}

	public static void draw(GuiGraphicsExtractor graphics, Font font, float x, float y, float scale, MiningTracker.Snapshot snap) {
		float h = heightOf(snap);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		if (scale != 1.0f) {
			graphics.pose().scale(scale, scale);
		}

		HudChrome.panel(graphics, 0, 0, WIDTH, h, 5, Theme.WINDOW, Theme.LINE, Theme.ACCENT);
		String ability = clip(font, snap.ability(), WIDTH - PAD * 2 - 36);
		GuiDraw.small(graphics, font, ability, PAD + 2, PAD, Theme.TEXT);
		int readyColor = snap.abilityReady() ? Theme.ACCENT : Theme.MUTED;
		GuiDraw.small(
			graphics,
			font,
			snap.abilityLabel(),
			WIDTH - PAD - GuiDraw.smallWidth(font, snap.abilityLabel()),
			PAD,
			readyColor
		);
		float barX = PAD + 2;
		float barW = WIDTH - PAD * 2 - 2;
		float barY = PAD + HEAD;
		GuiDraw.rounded(graphics, barX, barY, barW, BAR, 1.1f, Theme.HUD_TRACK);
		float filled = Math.max(snap.abilityReady() ? barW : 0f, barW * snap.abilityProgress());
		if (filled > 0.4f) {
			GuiDraw.rounded(graphics, barX, barY, filled, BAR, 1.1f, snap.abilityReady() ? Theme.ACCENT : Theme.ACCENT_DIM);
		}

		List<MiningTracker.Commission> commissions = snap.commissions();
		float rowY = PAD + HEAD + BAR + 4;
		if (commissions.isEmpty()) {
			GuiDraw.small(graphics, font, "No commissions", PAD + 2, rowY, Theme.MUTED);
		} else {
			for (MiningTracker.Commission commission : commissions) {
				String name = clip(font, commission.name(), WIDTH - PAD * 2 - 28);
				GuiDraw.small(graphics, font, name, PAD + 2, rowY, commission.done() ? Theme.ACCENT : Theme.TEXT);
				GuiDraw.small(
					graphics,
					font,
					commission.progress(),
					WIDTH - PAD - GuiDraw.smallWidth(font, commission.progress()),
					rowY,
					commission.done() ? Theme.ACCENT : Theme.MUTED
				);
				rowY += ROW;
			}
		}
		graphics.pose().popMatrix();
	}

	private static void drawAlert(GuiGraphicsExtractor graphics, Font font, int guiW, int guiH, MiningTracker.Snapshot snap) {
		float t = snap.alertT();
		float fade = t > 0.75f ? 1f : t / 0.75f;
		Component title = alertLine("READY");
		Component name = alertLine(snap.alertName());
		float w = Math.max(132, font.width(name) + 24);
		float h = 36;
		float x = (guiW - w) * 0.5f;
		float y = guiH * 0.28f;
		int pane = Anim.fade(Theme.HUD_WINDOW, fade);
		int accent = Anim.fade(Theme.ACCENT, fade);
		graphics.pose().pushMatrix();
		GuiDraw.rounded(graphics, x, y, w, h, 6, pane);
		GuiDraw.text(graphics, font, title, x + (w - font.width(title)) * 0.5f, y + 6, accent, false);
		GuiDraw.text(graphics, font, name, x + (w - font.width(name)) * 0.5f, y + 18, accent, false);
		graphics.pose().popMatrix();
	}

	private static Component alertLine(String value) {
		return MenuFont.body(value == null ? "" : value).copy().withStyle(style -> style.withBold(true));
	}

	private static float heightOf(MiningTracker.Snapshot snap) {
		int rows = Math.max(1, snap.commissions().size());
		return PAD + HEAD + BAR + 4 + rows * ROW + PAD;
	}

	private static String clip(Font font, String value, float max) {
		if (value == null || value.isBlank()) {
			return "";
		}
		if (GuiDraw.smallWidth(font, value) <= max) {
			return value;
		}
		String trimmed = value;
		while (trimmed.length() > 1 && GuiDraw.smallWidth(font, trimmed + "..") > max) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + "..";
	}
}
