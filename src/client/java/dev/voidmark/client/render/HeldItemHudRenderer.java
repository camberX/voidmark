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
	public static final float HEIGHT = 16;
	private static final float MIN_W = 80;

	private HeldItemHudRenderer() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		LocalPlayer player = Minecraft.getInstance().player;
		ItemStack stack = player == null ? ItemStack.EMPTY : player.getInventory().getSelectedItem();
		if ((stack == null || stack.isEmpty()) && !HudLayout.editorOpen()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		Component name = stack == null || stack.isEmpty()
			? Component.literal("Held item")
			: stack.getStyledHoverName();
		float w = Math.max(MIN_W, font.width(name) + 16);
		HudLayout.apply(graphics, font, HudLayout.Id.HELD_ITEM, () -> {
			HudChrome.panel(graphics, 0, 0, w, HEIGHT, 5, Theme.WINDOW, Theme.LINE, Theme.ACCENT);
			GuiDraw.text(graphics, font, name, 8, 3, 0xFFFFFFFF, false);
		});
	}

	public static float drawWidth(Font font) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.getInventory().getSelectedItem().isEmpty()) {
			return MIN_W;
		}
		return Math.max(MIN_W, font.width(player.getInventory().getSelectedItem().getStyledHoverName()) + 16);
	}
}
