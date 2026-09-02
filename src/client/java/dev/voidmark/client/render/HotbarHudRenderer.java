package dev.voidmark.client.render;

import dev.voidmark.client.ui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class HotbarHudRenderer {
	private static final int SLOT = 18;
	private static final int GAP = 2;
	private static final int PAD = 4;
	private static final int COLS = 9;
	public static final float HEIGHT = PAD * 2 + SLOT;
	public static final float WIDTH = PAD * 2 + COLS * SLOT + (COLS - 1) * GAP;

	private HotbarHudRenderer() {
	}

	public static float drawWidth() {
		return WIDTH;
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		Inventory inventory = player.getInventory();
		Font font = client.font;
		int selected = inventory.getSelectedSlot();
		ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
		boolean leftHanded = player.getMainArm() == HumanoidArm.LEFT;
		boolean showOffhand = !offhand.isEmpty();

		HudLayout.apply(graphics, font, HudLayout.Id.HOTBAR, () -> {
			HudChrome.panel(graphics, 0, 0, WIDTH, HEIGHT, 5, Theme.WINDOW, Theme.LINE, Theme.ACCENT);
			for (int col = 0; col < COLS; col++) {
				slot(graphics, font, player, inventory.getItem(col), PAD + col * (SLOT + GAP), PAD, col, col == selected);
			}
			if (showOffhand) {
				float ox = leftHanded ? WIDTH + GAP : -(SLOT + PAD * 2) - GAP;
				GuiDraw.panel(graphics, ox, 0, SLOT + PAD * 2, HEIGHT, 5, Theme.HUD_WINDOW, Theme.HUD_LINE);
				slot(graphics, font, player, offhand, Math.round(ox) + PAD, PAD, 40, false);
			}
		});
	}

	private static void slot(
		GuiGraphicsExtractor graphics,
		Font font,
		LocalPlayer player,
		ItemStack stack,
		int x,
		int y,
		int seed,
		boolean selected
	) {
		int fill = selected ? Theme.HUD_CARD_HOVER : Theme.HUD_TRACK;
		int outline = selected ? Theme.ACCENT : Theme.HUD_LINE;
		GuiDraw.panel(graphics, x, y, SLOT, SLOT, 3, fill, outline);
		if (stack == null || stack.isEmpty()) {
			return;
		}
		int ix = x + 1;
		int iy = y + 1;
		graphics.item(player, stack, ix, iy, seed);
		graphics.itemDecorations(font, stack, ix, iy);
	}
}
