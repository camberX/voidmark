package dev.voidmark.client.render;

import dev.voidmark.client.ui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class HeldItemHudRenderer {
	private HeldItemHudRenderer() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		ItemStack stack = player.getInventory().getSelectedItem();
		if (stack == null || stack.isEmpty()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		Component name = stack.getStyledHoverName();
		float w = font.width(name) + 16;
		float h = 16;
		float x = (graphics.guiWidth() - w) * 0.5f;
		float y = VanillaHud.hotbarTop(graphics.guiHeight()) - 46;
		GuiDraw.panel(graphics, x, y, w, h, 5, Theme.WINDOW, Theme.LINE, Theme.ACCENT);
		GuiDraw.text(graphics, font, name, x + 8, y + 3, 0xFFFFFFFF, false);
	}
}
