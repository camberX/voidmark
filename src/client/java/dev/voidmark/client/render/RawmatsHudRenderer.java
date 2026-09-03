package dev.voidmark.client.render;

import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.item.ItemStorage;
import dev.voidmark.client.item.RawmatsTracker;
import dev.voidmark.client.item.SkyblockProfileApi;
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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

public final class RawmatsHudRenderer {
	public static final float WIDTH = 236;
	private static final float PAD = 7;
	private static final float HEAD = 28;
	private static final float ROW = 18;
	private static final float ROW_NOTE = 26;
	private static final float ICON = 16;
	private static final int MAX_ROWS = 10;
	private static Rect modeHit = Rect.EMPTY;

	private RawmatsHudRenderer() {
	}

	public static void init() {
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Voidmark.id("rawmats"),
			RawmatsHudRenderer::extract
		);
	}

	public static float drawWidth() {
		return WIDTH;
	}

	public static float drawHeight() {
		RawmatsTracker.Snapshot snap = RawmatsTracker.snapshot();
		if (!snap.present() && !HudLayout.editorOpen()) {
			return 0;
		}
		return heightOf(snap);
	}

	public static boolean mouseClicked(MouseButtonEvent event) {
		if (event.button() != 0 || !VoidmarkConfig.get().rawmatsHudEnabled) {
			return false;
		}
		if (Minecraft.getInstance().screen instanceof HudEditorScreen) {
			return false;
		}
		if (!(Minecraft.getInstance().screen instanceof ChatScreen)) {
			return false;
		}
		if (modeHit.contains(event.x(), event.y())) {
			VoidmarkConfig config = VoidmarkConfig.get();
			config.cycleRawmatsMode();
			config.save();
			return true;
		}
		return false;
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.options.hideGui) {
			modeHit = Rect.EMPTY;
			return;
		}
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.rawmatsHudEnabled) {
			modeHit = Rect.EMPTY;
			return;
		}
		RawmatsTracker.Snapshot snap = RawmatsTracker.snapshot();
		if (!snap.present() && !HudLayout.editorOpen()) {
			modeHit = Rect.EMPTY;
			return;
		}
		HudLayout.Box box = HudLayout.box(HudLayout.Id.RAWMATS, client.font, graphics.guiWidth(), graphics.guiHeight());
		draw(graphics, client, box.x(), box.y(), HudLayout.scale(HudLayout.Id.RAWMATS), snap);
	}

	public static void draw(GuiGraphicsExtractor graphics, Minecraft client, float x, float y, float scale, RawmatsTracker.Snapshot snap) {
		Font font = client.font;
		float h = heightOf(snap);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		if (scale != 1.0f) {
			graphics.pose().scale(scale, scale);
		}

		HudChrome.panel(graphics, 0, 0, WIDTH, h, 6, Theme.WINDOW, Theme.LINE);
		GuiDraw.small(graphics, font, "RAW MATS", PAD + 4, PAD + 1, Theme.ACCENT);
		boolean chat = client.screen instanceof ChatScreen;
		String mode = VoidmarkConfig.get().rawmatsModeLabel();
		float modeW = GuiDraw.smallWidth(font, mode);
		int modeColor = chat ? Theme.ACCENT : Theme.MUTED;
		GuiDraw.small(graphics, font, mode, WIDTH - PAD - modeW, PAD + 1, modeColor);
		modeHit = new Rect(x + (WIDTH - PAD - modeW) * scale, y + PAD * scale, (modeW + 8) * scale, 12 * scale);

		if (!snap.present()) {
			GuiDraw.menu(graphics, font, "No item tracked", PAD + 4, PAD + 12, Theme.TEXT);
			GuiDraw.small(graphics, font, "/vm rawmats <id>", PAD + 4, PAD + 24, Theme.MUTED);
			graphics.pose().popMatrix();
			return;
		}

		String title = ellipsize(font, snap.name(), WIDTH - PAD * 2 - 72, false);
		GuiDraw.menu(graphics, font, title, PAD + 4, PAD + 12, Theme.TEXT);
		String tally = snap.complete() + "/" + snap.total();
		String pct = Math.round(snap.progress() * 100f) + "%";
		String right = tally + "  " + pct;
		GuiDraw.small(graphics, font, right, WIDTH - PAD - GuiDraw.smallWidth(font, right), PAD + 12, Theme.MUTED);

		List<RawmatsTracker.Line> lines = snap.lines();
		if (lines.isEmpty()) {
			GuiDraw.small(graphics, font, snap.recipe() ? "No ingredients" : "No craft recipe", PAD + 4, HEAD + 2, Theme.MUTED);
			graphics.pose().popMatrix();
			return;
		}

		LocalPlayer player = client.player;
		int shown = Math.min(MAX_ROWS, lines.size());
		float rowY = HEAD;
		for (int i = 0; i < shown; i++) {
			RawmatsTracker.Line line = lines.get(i);
			row(graphics, font, player, line, PAD, rowY, i);
			rowY += rowHeight(line);
		}
		if (lines.size() > MAX_ROWS) {
			GuiDraw.small(graphics, font, "+" + (lines.size() - MAX_ROWS) + " more", PAD + 4, rowY + 1, Theme.MUTED);
			rowY += 12;
		}
		String hint = storageHint(snap);
		if (hint != null) {
			GuiDraw.small(graphics, font, hint, PAD + 4, rowY + 1, Theme.MUTED);
		}
		graphics.pose().popMatrix();
	}

	private static void row(
		GuiGraphicsExtractor graphics,
		Font font,
		LocalPlayer player,
		RawmatsTracker.Line line,
		float x,
		float y,
		int seed
	) {
		ItemStack stack = line.icon();
		boolean note = line.hasNote();
		float iconY = note ? y + 5 : y + 1;
		GuiDraw.rounded(graphics, x, iconY, ICON, ICON, 3, Theme.HUD_TRACK);
		if (stack != null && !stack.isEmpty() && player != null) {
			graphics.item(player, stack, Math.round(x), Math.round(iconY), 200 + seed);
		}
		float textX = x + ICON + 4;
		String amount = format(line.have()) + "/" + format(line.need());
		float amountW = GuiDraw.smallWidth(font, amount);
		float nameW = WIDTH - textX - amountW - PAD - 8;
		String name = ellipsize(font, line.name(), nameW, true);
		int nameColor = line.done() ? Theme.ACCENT : Theme.TEXT;
		GuiDraw.small(graphics, font, name, textX, y + 1, nameColor);
		GuiDraw.small(graphics, font, amount, WIDTH - PAD - amountW, y + 1, line.done() ? Theme.ACCENT : Theme.MUTED);
		if (note) {
			String used = ellipsize(font, line.note(), WIDTH - textX - PAD - 4, true);
			GuiDraw.small(graphics, font, used, textX, y + 10, Theme.MUTED);
		}
		float barX = textX;
		float barW = WIDTH - textX - PAD;
		float barY = note ? y + 20 : y + 12;
		GuiDraw.rounded(graphics, barX, barY, barW, 2.5f, 1.2f, Theme.HUD_TRACK);
		float filled = Math.max(line.have() > 0L ? 2f : 0f, barW * line.progress());
		GuiDraw.rounded(graphics, barX, barY, filled, 2.5f, 1.2f, line.done() ? Theme.ACCENT : Theme.ACCENT_DIM);
	}

	private static float heightOf(RawmatsTracker.Snapshot snap) {
		if (!snap.present()) {
			return 44;
		}
		List<RawmatsTracker.Line> lines = snap.lines();
		int shown = Math.min(MAX_ROWS, Math.max(1, lines.size()));
		float rows = 0f;
		if (lines.isEmpty()) {
			rows = ROW;
		} else {
			for (int i = 0; i < shown; i++) {
				rows += rowHeight(lines.get(i));
			}
		}
		boolean extra = lines.size() > MAX_ROWS;
		boolean hint = storageHint(snap) != null;
		return HEAD + PAD + rows + (extra ? 12 : 0) + (hint ? 12 : 0) + PAD;
	}

	private static float rowHeight(RawmatsTracker.Line line) {
		return line.hasNote() ? ROW_NOTE : ROW;
	}

	private static String storageHint(RawmatsTracker.Snapshot snap) {
		if (ItemStorage.hasApiStorage()) {
			return null;
		}
		SkyblockProfileApi.Status status = SkyblockProfileApi.status();
		if (status == SkyblockProfileApi.Status.LOADING || status == SkyblockProfileApi.Status.IDLE) {
			return "Loading Ender Chest and backpacks";
		}
		if (status == SkyblockProfileApi.Status.ERROR) {
			return "Couldn't load Ender Chest / backpacks";
		}
		if (!snap.sawEnder() || !snap.sawBackpack()) {
			return "Open Ender Chest and backpacks to count them";
		}
		return null;
	}

	static String format(long value) {
		if (value < 1_000L) {
			return Long.toString(value);
		}
		if (value < 10_000L) {
			return String.format(Locale.ROOT, "%.1fk", value / 1000d);
		}
		if (value < 1_000_000L) {
			return (value / 1000L) + "k";
		}
		if (value < 10_000_000L) {
			return String.format(Locale.ROOT, "%.1fm", value / 1_000_000d);
		}
		return (value / 1_000_000L) + "m";
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
