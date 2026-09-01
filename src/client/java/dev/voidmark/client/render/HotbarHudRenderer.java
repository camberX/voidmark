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
	public static final float HEIGHT = 26;
	private static final int SLOT = 18;
	private static final int GAP = 2;
	private static final int PAD = 4;
	private static final int COLS = 9;

	private HotbarHudRenderer() {
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

		int gridW = COLS * SLOT + (COLS - 1) * GAP;
		int panelW = PAD * 2 + gridW;
		int panelH = PAD * 2 + SLOT;
		int guiW = graphics.guiWidth();
		int guiH = graphics.guiHeight();
		float x = (guiW - panelW) * 0.5f;
		float y = VanillaHud.hotbarTop(guiH);

		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		GuiDraw.panel(graphics, 0, 0, panelW, panelH, 5, Theme.WINDOW, Theme.LINE, Theme.ACCENT);
		for (int col = 0; col < COLS; col++) {
			slot(graphics, font, player, inventory.getItem(col), PAD + col * (SLOT + GAP), PAD, col, col == selected);
		}
		graphics.pose().popMatrix();

		if (showOffhand) {
			float ox = leftHanded ? x + panelW + GAP + 1 : x - SLOT - GAP - PAD * 2 - 1;
			float oy = y;
			graphics.pose().pushMatrix();
			graphics.pose().translate(ox, oy);
			GuiDraw.panel(graphics, 0, 0, SLOT + PAD * 2, panelH, 5, Theme.WINDOW, Theme.LINE);
			slot(graphics, font, player, offhand, PAD, PAD, 40, false);
			graphics.pose().popMatrix();
		}
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
		int fill = selected ? Theme.CARD_HOVER : Theme.TRACK;
		int outline = selected ? Theme.ACCENT : Theme.LINE;
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
