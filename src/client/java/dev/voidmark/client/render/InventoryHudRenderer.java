package dev.voidmark.client.render;

import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.ui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class InventoryHudRenderer {
	private static final int SLOT = 18;
	private static final int GAP = 2;
	private static final int PAD = 7;
	private static final int HEAD = 14;
	private static final int COLS = 9;
	private static final EquipmentSlot[] ARMOR = {
		EquipmentSlot.HEAD,
		EquipmentSlot.CHEST,
		EquipmentSlot.LEGS,
		EquipmentSlot.FEET
	};
	private static final String[] ARMOR_MARK = {"H", "C", "L", "B"};

	private InventoryHudRenderer() {
	}

	public static void init() {
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Voidmark.id("inventory"),
			InventoryHudRenderer::extract
		);
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return;
		}
		if (client.screen instanceof AbstractContainerScreen<?>) {
			return;
		}
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.inventoryHudEnabled) {
			return;
		}
		draw(graphics, client, config);
	}

	private static void draw(GuiGraphicsExtractor graphics, Minecraft client, VoidmarkConfig config) {
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		Inventory inventory = player.getInventory();
		Font font = client.font;
		float scale = config.inventoryHudScale;
		int gridW = COLS * SLOT + (COLS - 1) * GAP;
		int panelW = PAD * 2 + gridW;
		int armorY = PAD + HEAD;
		int mainY = armorY + SLOT + 5;
		int hotbarY = mainY + 3 * (SLOT + GAP) - GAP + 5;
		int panelH = hotbarY + SLOT + PAD;
		float drawW = panelW * scale;
		float drawH = panelH * scale;
		float x = originX(graphics.guiWidth(), drawW, config.inventoryHudAnchor);
		float y = originY(graphics.guiHeight(), drawH, config.inventoryHudAnchor);

		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		if (scale != 1.0f) {
			graphics.pose().scale(scale, scale);
		}

		GuiDraw.panel(graphics, 0, 0, panelW, panelH, 6, Theme.withAlpha(Theme.WINDOW, 230), Theme.LINE);
		GuiDraw.rounded(graphics, 1, 1, 3, panelH - 2, 1.5f, Theme.ACCENT);
		GuiDraw.small(graphics, font, "INVENTORY", PAD + 4, PAD + 1, Theme.ACCENT);
		String filled = filledLabel(player, inventory);
		GuiDraw.small(graphics, font, filled, panelW - PAD - GuiDraw.smallWidth(font, filled), PAD + 1, Theme.MUTED);

		int gridX = PAD;
		for (int i = 0; i < ARMOR.length; i++) {
			ItemStack stack = player.getItemBySlot(ARMOR[i]);
			slot(graphics, font, player, stack, gridX + i * (SLOT + GAP), armorY, 100 + i, ARMOR_MARK[i], false);
		}
		slot(graphics, font, player, player.getItemBySlot(EquipmentSlot.OFFHAND), gridX + 5 * (SLOT + GAP), armorY, 104, "O", false);

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < COLS; col++) {
				int index = 9 + row * COLS + col;
				slot(graphics, font, player, inventory.getItem(index), gridX + col * (SLOT + GAP), mainY + row * (SLOT + GAP), index, null, false);
			}
		}

		GuiDraw.hline(graphics, gridX, hotbarY - 3, gridW, Theme.LINE);
		int selected = inventory.getSelectedSlot();
		for (int col = 0; col < COLS; col++) {
			slot(graphics, font, player, inventory.getItem(col), gridX + col * (SLOT + GAP), hotbarY, col, null, col == selected);
		}

		graphics.pose().popMatrix();
	}

	private static void slot(
		GuiGraphicsExtractor graphics,
		Font font,
		LocalPlayer player,
		ItemStack stack,
		int x,
		int y,
		int seed,
		String emptyMark,
		boolean selected
	) {
		int fill = selected ? Theme.CARD_HOVER : Theme.TRACK;
		int outline = selected ? Theme.ACCENT : Theme.LINE;
		GuiDraw.panel(graphics, x, y, SLOT, SLOT, 3, fill, outline);
		if (stack == null || stack.isEmpty()) {
			if (emptyMark != null) {
				GuiDraw.small(
					graphics,
					font,
					emptyMark,
					x + (SLOT - GuiDraw.smallWidth(font, emptyMark)) * 0.5f,
					y + 4,
					Theme.OFF
				);
			}
			return;
		}
		int ix = x + 1;
		int iy = y + 1;
		graphics.item(player, stack, ix, iy, seed);
		graphics.itemDecorations(font, stack, ix, iy);
	}

	private static String filledLabel(LocalPlayer player, Inventory inventory) {
		int filled = 0;
		int total = 36;
		for (int i = 0; i < 36; i++) {
			if (!inventory.getItem(i).isEmpty()) {
				filled++;
			}
		}
		for (EquipmentSlot slot : ARMOR) {
			total++;
			if (!player.getItemBySlot(slot).isEmpty()) {
				filled++;
			}
		}
		total++;
		if (!player.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) {
			filled++;
		}
		return filled + "/" + total;
	}

	private static float originX(int guiW, float drawW, String anchor) {
		float margin = 8;
		return switch (anchor) {
			case "top_left", "bottom_left" -> margin;
			default -> guiW - drawW - margin;
		};
	}

	private static float originY(int guiH, float drawH, String anchor) {
		float margin = 8;
		float hotbar = 24;
		return switch (anchor) {
			case "top_left" -> margin + WatermarkRenderer.occupiedHeight();
			case "top_right" -> margin;
			default -> guiH - drawH - hotbar;
		};
	}
}
