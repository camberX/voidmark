package dev.voidmark.client.render;

import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.mining.MiningTracker;
import dev.voidmark.client.ui.Anim;
import dev.voidmark.client.ui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

public final class MiningHudRenderer {
	public static final float WIDTH = 196;
	private static final float PAD = 7;
	private static final float HEAD = 26;
	private static final float ABILITY = 28;
	private static final float ROW = 16;

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

		GuiDraw.panel(graphics, 0, 0, WIDTH, h, 6, Theme.WINDOW, Theme.LINE, Theme.ACCENT);
		GuiDraw.small(graphics, font, "MINING", PAD + 4, PAD + 1, Theme.ACCENT);
		String area = snap.area() == null || snap.area().isBlank() ? "Skyblock" : snap.area();
		String areaShown = clip(font, area, 88);
		GuiDraw.small(graphics, font, areaShown, WIDTH - PAD - GuiDraw.smallWidth(font, areaShown), PAD + 1, Theme.MUTED);

		GuiDraw.small(graphics, font, "ABILITY", PAD + 4, PAD + 12, Theme.MUTED);
		String ability = clip(font, snap.ability(), WIDTH - PAD * 2 - 48);
		GuiDraw.menu(graphics, font, ability, PAD + 4, PAD + 20, Theme.TEXT);
		int readyColor = snap.abilityReady() ? Theme.ACCENT : Theme.MUTED;
		GuiDraw.small(graphics, font, snap.abilityLabel(), WIDTH - PAD - GuiDraw.smallWidth(font, snap.abilityLabel()), PAD + 20, readyColor);
		float barX = PAD + 4;
		float barW = WIDTH - PAD * 2 - 4;
		float barY = PAD + 33;
		GuiDraw.rounded(graphics, barX, barY, barW, 2.6f, 1.2f, Theme.TRACK);
		float filled = Math.max(snap.abilityReady() ? barW : 0f, barW * snap.abilityProgress());
		if (filled > 0.5f) {
			GuiDraw.rounded(graphics, barX, barY, filled, 2.6f, 1.2f, snap.abilityReady() ? Theme.ACCENT : Theme.ACCENT_DIM);
		}

		List<MiningTracker.Commission> commissions = snap.commissions();
		float rowY = HEAD + ABILITY;
		if (commissions.isEmpty()) {
			GuiDraw.small(graphics, font, "No commissions", PAD + 4, rowY + 2, Theme.MUTED);
		} else {
			for (MiningTracker.Commission commission : commissions) {
				String name = clip(font, commission.name(), WIDTH - PAD * 2 - 40);
				GuiDraw.small(graphics, font, name, PAD + 4, rowY, commission.done() ? Theme.ACCENT : Theme.TEXT);
				GuiDraw.small(
					graphics,
					font,
					commission.progress(),
					WIDTH - PAD - GuiDraw.smallWidth(font, commission.progress()),
					rowY,
					commission.done() ? Theme.ACCENT : Theme.MUTED
				);
				GuiDraw.rounded(graphics, barX, rowY + 10, barW, 2.4f, 1.2f, Theme.TRACK);
				float amount = Math.max(commission.fraction() > 0f ? 2f : 0f, barW * commission.fraction());
				if (amount > 0.5f) {
					GuiDraw.rounded(graphics, barX, rowY + 10, amount, 2.4f, 1.2f, commission.done() ? Theme.ACCENT : Theme.ACCENT_DIM);
				}
				rowY += ROW;
			}
		}
		graphics.pose().popMatrix();
	}

	private static void drawAlert(GuiGraphicsExtractor graphics, Font font, int guiW, int guiH, MiningTracker.Snapshot snap) {
		float t = snap.alertT();
		float fade = t > 0.75f ? 1f : t / 0.75f;
		String title = "ABILITY READY";
		String name = snap.alertName();
		float w = Math.max(168, GuiDraw.menuWidth(font, name) + 28);
		float h = 44;
		float x = (guiW - w) * 0.5f;
		float y = guiH * 0.28f;
		int pane = Anim.fade(Theme.WINDOW, fade);
		int line = Anim.fade(Theme.ACCENT, fade);
		int text = Anim.fade(Theme.TEXT, fade);
		int accent = Anim.fade(Theme.ACCENT, fade);
		graphics.pose().pushMatrix();
		GuiDraw.panel(graphics, x, y, w, h, 8, pane, line, accent);
		GuiDraw.small(graphics, font, title, x + (w - GuiDraw.smallWidth(font, title)) * 0.5f, y + 8, accent);
		GuiDraw.menu(graphics, font, name, x + (w - GuiDraw.menuWidth(font, name)) * 0.5f, y + 22, text);
		graphics.pose().popMatrix();
	}

	private static float heightOf(MiningTracker.Snapshot snap) {
		int rows = Math.max(1, snap.commissions().size());
		return PAD + HEAD + ABILITY + rows * ROW + PAD - 2;
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
