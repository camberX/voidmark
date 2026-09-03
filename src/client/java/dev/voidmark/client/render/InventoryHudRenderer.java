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

	public static float drawWidth() {
		return PAD * 2 + COLS * SLOT + (COLS - 1) * GAP;
	}

	public static float drawHeight() {
		return metrics().panelH;
	}

	public static float defaultX(int guiW, float drawW, String anchor) {
		float margin = HudLayout.MARGIN;
		return switch (anchor) {
			case "top_left", "bottom_left" -> margin;
			default -> guiW - drawW - margin;
		};
	}

	public static float defaultY(int guiH, float drawH, String anchor) {
		float margin = HudLayout.MARGIN;
		float hotbar = 24;
		return switch (anchor) {
			case "top_left" -> margin + (VoidmarkConfig.get().watermarkEnabled ? WatermarkRenderer.HEIGHT + 8 : 0);
			case "top_right" -> margin;
			default -> guiH - drawH - hotbar;
		};
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return;
		}
		if (client.screen instanceof AbstractContainerScreen<?> && !HudLayout.editorOpen()) {
			return;
		}
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.inventoryHudEnabled) {
			return;
		}
		HudLayout.Box box = HudLayout.box(HudLayout.Id.INVENTORY, client.font, graphics.guiWidth(), graphics.guiHeight());
		draw(graphics, client, config, box.x(), box.y());
	}

	private static void draw(GuiGraphicsExtractor graphics, Minecraft client, VoidmarkConfig config, float x, float y) {
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		Inventory inventory = player.getInventory();
		Font font = client.font;
		float scale = HudLayout.scale(HudLayout.Id.INVENTORY);
		Metrics layout = metrics();

		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		if (scale != 1.0f) {
			graphics.pose().scale(scale, scale);
		}

		HudChrome.panel(graphics, 0, 0, layout.panelW, layout.panelH, 6, Theme.WINDOW, Theme.LINE);
		GuiDraw.small(graphics, font, "INVENTORY", PAD + 4, PAD + 1, Theme.ACCENT);
		if (config.inventoryHudCount) {
			String filled = filledLabel(player, inventory);
			GuiDraw.small(graphics, font, filled, layout.panelW - PAD - GuiDraw.smallWidth(font, filled), PAD + 1, Theme.MUTED);
		}

		int gridX = PAD;
		if (config.inventoryHudArmor) {
			for (int i = 0; i < ARMOR.length; i++) {
				ItemStack stack = player.getItemBySlot(ARMOR[i]);
				slot(graphics, font, player, stack, gridX + i * (SLOT + GAP), layout.armorY, 100 + i, ARMOR_MARK[i], false);
			}
			slot(graphics, font, player, player.getItemBySlot(EquipmentSlot.OFFHAND), gridX + 5 * (SLOT + GAP), layout.armorY, 104, "O", false);
		}

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < COLS; col++) {
				int index = 9 + row * COLS + col;
				slot(graphics, font, player, inventory.getItem(index), gridX + col * (SLOT + GAP), layout.mainY + row * (SLOT + GAP), index, null, false);
			}
		}

		if (config.inventoryHudHotbar) {
			GuiDraw.hline(graphics, gridX, layout.hotbarY - 3, layout.gridW, Theme.HUD_LINE);
			int selected = inventory.getSelectedSlot();
			for (int col = 0; col < COLS; col++) {
				slot(graphics, font, player, inventory.getItem(col), gridX + col * (SLOT + GAP), layout.hotbarY, col, null, col == selected);
			}
		}

		graphics.pose().popMatrix();
	}

	private static Metrics metrics() {
		VoidmarkConfig config = VoidmarkConfig.get();
		int gridW = COLS * SLOT + (COLS - 1) * GAP;
		int panelW = PAD * 2 + gridW;
		int y = PAD + HEAD;
		int armorY = y;
		if (config.inventoryHudArmor) {
			y += SLOT + 5;
		}
		int mainY = y;
		y += 3 * (SLOT + GAP) - GAP;
		int hotbarY = y;
		if (config.inventoryHudHotbar) {
			y += 5 + SLOT;
			hotbarY = y - SLOT;
		}
		int panelH = y + PAD;
		return new Metrics(panelW, panelH, gridW, armorY, mainY, hotbarY);
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
		int fill = selected ? Theme.HUD_CARD_HOVER : Theme.HUD_TRACK;
		int outline = selected ? Theme.ACCENT : Theme.HUD_LINE;
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
		for (int i = 0; i < 36; i++) {
			if (!inventory.getItem(i).isEmpty()) {
				filled++;
			}
		}
		for (EquipmentSlot slot : ARMOR) {
			if (!player.getItemBySlot(slot).isEmpty()) {
				filled++;
			}
		}
		if (!player.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) {
			filled++;
		}
		return filled + "/41";
	}

	private record Metrics(int panelW, int panelH, int gridW, int armorY, int mainY, int hotbarY) {
	}
}
