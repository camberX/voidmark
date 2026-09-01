package dev.voidmark.client.render;

import dev.voidmark.client.ui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;

public final class StatusHudRenderer {
	private static final float BAR_W = 81;
	private static final float BAR_H = 12;
	private static final float XP_H = 8;

	private StatusHudRenderer() {
	}

	public static void extractHealth(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!VanillaHud.survivalBars()) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		float health = player.getHealth();
		float max = Math.max(1f, player.getMaxHealth());
		float absorption = player.getAbsorptionAmount();
		float total = max + Math.max(0f, absorption);
		int color = health / max <= 0.25f ? Theme.DANGER : health / max <= 0.5f ? Theme.WARN : Theme.ACCENT;
		String value = format(health) + (absorption > 0.05f ? "+" + format(absorption) : "");
		bar(graphics, leftX(graphics.guiWidth()), rowY(graphics.guiHeight(), 0), "HP", value, (health + absorption) / total, color);
	}

	public static void extractHunger(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!VanillaHud.survivalBars() || VanillaHud.mount() != null) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		FoodData food = player.getFoodData();
		int hunger = food.getFoodLevel();
		bar(graphics, rightX(graphics.guiWidth()), rowY(graphics.guiHeight(), 0), "FOOD", hunger + "", hunger / 20f, Theme.WARN);
	}

	public static void extractArmor(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!VanillaHud.survivalBars()) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		int armor = player.getArmorValue();
		if (armor <= 0) {
			return;
		}
		bar(graphics, leftX(graphics.guiWidth()), rowY(graphics.guiHeight(), 1), "ARMOR", armor + "", Math.min(1f, armor / 20f), Theme.HEADER);
	}

	public static void extractAir(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!VanillaHud.survivalBars()) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		int air = player.getAirSupply();
		int max = Math.max(1, player.getMaxAirSupply());
		if (air >= max) {
			return;
		}
		float t = Mth.clamp(air / (float) max, 0f, 1f);
		bar(graphics, rightX(graphics.guiWidth()), rowY(graphics.guiHeight(), 1), "AIR", Math.round(t * 100) + "%", t, Theme.ACCENT);
	}

	public static void extractMount(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!VanillaHud.survivalBars()) {
			return;
		}
		LivingEntity mount = VanillaHud.mount();
		if (mount == null) {
			return;
		}
		float health = mount.getHealth();
		float max = Math.max(1f, mount.getMaxHealth());
		bar(graphics, rightX(graphics.guiWidth()), rowY(graphics.guiHeight(), 0), "MOUNT", format(health), health / max, Theme.ACCENT_DIM);
	}

	public static void extractExperience(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!VanillaHud.hasExperience()) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		int guiW = graphics.guiWidth();
		int guiH = graphics.guiHeight();
		float w = 182;
		float x = (guiW - w) * 0.5f;
		float y = VanillaHud.hotbarTop(guiH) - XP_H - 3;
		float t = Mth.clamp(player.experienceProgress, 0f, 1f);
		GuiDraw.panel(graphics, x, y, w, XP_H, 4, Theme.WINDOW, Theme.LINE);
		if (t > 0.01f) {
			GuiDraw.rounded(graphics, x + 1, y + 1, Math.max(2f, (w - 2f) * t), XP_H - 2, 3, Theme.ACCENT);
		}
	}

	public static void extractExperienceLevel(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!VanillaHud.hasExperience()) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		String level = Integer.toString(player.experienceLevel);
		float y = VanillaHud.hotbarTop(graphics.guiHeight()) - XP_H - 12;
		float x = (graphics.guiWidth() - GuiDraw.smallWidth(font, level)) * 0.5f;
		GuiDraw.small(graphics, font, level, x, y, Theme.ACCENT);
	}

	private static void bar(GuiGraphicsExtractor graphics, float x, float y, String label, String value, float t, int fill) {
		Font font = Minecraft.getInstance().font;
		t = Mth.clamp(t, 0f, 1f);
		GuiDraw.panel(graphics, x, y, BAR_W, BAR_H, 4, Theme.WINDOW, Theme.LINE);
		if (t > 0.01f) {
			GuiDraw.rounded(graphics, x + 1, y + 1, Math.max(2f, (BAR_W - 2f) * t), BAR_H - 2, 3, fill);
		}
		GuiDraw.small(graphics, font, label, x + 4, y + 2, Theme.TEXT);
		GuiDraw.small(graphics, font, value, x + BAR_W - 4 - GuiDraw.smallWidth(font, value), y + 2, Theme.TEXT);
	}

	private static float leftX(int guiW) {
		return guiW * 0.5f - 91;
	}

	private static float rightX(int guiW) {
		return guiW * 0.5f + 91 - BAR_W;
	}

	private static float rowY(int guiH, int row) {
		return VanillaHud.hotbarTop(guiH) - 16 - row * (BAR_H + 2);
	}

	private static String format(float value) {
		if (Math.abs(value - Math.round(value)) < 0.05f) {
			return Integer.toString(Math.round(value));
		}
		return String.format(java.util.Locale.ROOT, "%.1f", value);
	}
}
