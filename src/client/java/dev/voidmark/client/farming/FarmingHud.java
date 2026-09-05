package dev.voidmark.client.farming;

import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.item.ItemAppearance;
import dev.voidmark.client.render.GuiDraw;
import dev.voidmark.client.ui.MenuFont;
import dev.voidmark.client.ui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.Locale;

/**
 * Yaw and pitch next to the crosshair while the held item's lore
 * contains {@code FARMING TOOL}.
 */
public final class FarmingHud {
	private static final String MARKER = "farming tool";

	private FarmingHud() {
	}

	public static void init() {
		HudElementRegistry.attachElementAfter(
			VanillaHudElements.CROSSHAIR,
			Voidmark.id("farming_yaw"),
			FarmingHud::extract
		);
	}

	public static boolean holdingTool(Player player) {
		return player != null && isFarmingTool(player.getMainHandItem());
	}

	public static String yawLabel(Player player) {
		return format(Mth.wrapDegrees(player.getYRot()));
	}

	public static String pitchLabel(Player player) {
		return format(player.getXRot());
	}

	public static boolean isFarmingTool(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		boolean prior = ItemAppearance.suppress();
		try {
			ItemLore lore = stack.get(DataComponents.LORE);
			if (lore == null) {
				return false;
			}
			for (Component line : lore.lines()) {
				if (plain(line).contains(MARKER)) {
					return true;
				}
			}
			return false;
		} finally {
			ItemAppearance.resume(prior);
		}
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui || client.screen != null) {
			return;
		}
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.farmingYawPitch || !holdingTool(client.player)) {
			return;
		}
		draw(graphics, client.font, client.player, graphics.guiWidth() * 0.5f, graphics.guiHeight() * 0.5f);
	}

	private static void draw(GuiGraphicsExtractor graphics, Font font, Player player, float cx, float cy) {
		float scale = VoidmarkConfig.clampHudScale(VoidmarkConfig.get().farmingYawPitchScale);
		String yaw = yawLabel(player);
		String pitch = pitchLabel(player);
		float x = cx + 12f * scale;
		float y = cy - 9f * scale;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		if (scale != 1.0f) {
			graphics.pose().scale(scale, scale);
		}
		GuiDraw.small(graphics, font, "Yaw", 0, 0, Theme.MUTED);
		GuiDraw.hud(graphics, font, MenuFont.body(yaw), 22, 0, Theme.TEXT);
		GuiDraw.small(graphics, font, "Pitch", 0, 10, Theme.MUTED);
		GuiDraw.hud(graphics, font, MenuFont.body(pitch), 22, 10, Theme.TEXT);
		graphics.pose().popMatrix();
	}

	private static String format(float value) {
		return String.format(Locale.ROOT, "%+.1f", value);
	}

	private static String plain(Component line) {
		if (line == null) {
			return "";
		}
		return line.getString().replaceAll("§.", "").toLowerCase(Locale.ROOT);
	}
}
