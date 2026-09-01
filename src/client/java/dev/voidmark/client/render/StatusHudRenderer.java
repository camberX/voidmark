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
	public static final float BAR_W = 81;
	public static final float BAR_H = 12;
	public static final float XP_H = 8;
	public static final float XP_BOX_H = 18;

	private StatusHudRenderer() {
	}

	public static float xpWidth() {
		return HotbarHudRenderer.WIDTH;
	}

	public static void extractHealth(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!VanillaHud.survivalBars() && !HudLayout.editorOpen()) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		float health = player == null ? 20f : player.getHealth();
		float max = player == null ? 20f : Math.max(1f, player.getMaxHealth());
		float absorption = player == null ? 0f : player.getAbsorptionAmount();
		float total = max + Math.max(0f, absorption);
		int color = health / max <= 0.25f ? Theme.DANGER : health / max <= 0.5f ? Theme.WARN : Theme.ACCENT;
		String value = format(health) + (absorption > 0.05f ? "+" + format(absorption) : "");
		placedBar(graphics, HudLayout.Id.HEALTH, "HP", value, (health + absorption) / total, color);
	}

	public static void extractHunger(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if ((!VanillaHud.survivalBars() || VanillaHud.mount() != null) && !HudLayout.editorOpen()) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		int hunger = player == null ? 20 : player.getFoodData().getFoodLevel();
		placedBar(graphics, HudLayout.Id.HUNGER, "FOOD", hunger + "", hunger / 20f, Theme.WARN);
	}

	public static void extractArmor(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		LocalPlayer player = Minecraft.getInstance().player;
		int armor = player == null ? 0 : player.getArmorValue();
		if (armor <= 0 && !HudLayout.editorOpen()) {
			return;
		}
		if (!VanillaHud.survivalBars() && !HudLayout.editorOpen()) {
			return;
		}
		placedBar(graphics, HudLayout.Id.ARMOR, "ARMOR", armor + "", Math.min(1f, armor / 20f), Theme.HEADER);
	}

	public static void extractAir(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			if (!HudLayout.editorOpen()) {
				return;
			}
			placedBar(graphics, HudLayout.Id.AIR, "AIR", "100%", 1f, Theme.ACCENT);
			return;
		}
		if (!VanillaHud.survivalBars() && !HudLayout.editorOpen()) {
			return;
		}
		int air = player.getAirSupply();
		int max = Math.max(1, player.getMaxAirSupply());
		if (air >= max && !HudLayout.editorOpen()) {
			return;
		}
		float t = Mth.clamp(air / (float) max, 0f, 1f);
		placedBar(graphics, HudLayout.Id.AIR, "AIR", Math.round(t * 100) + "%", t, Theme.ACCENT);
	}

	public static void extractMount(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		LivingEntity mount = VanillaHud.mount();
		if (mount == null && !HudLayout.editorOpen()) {
			return;
		}
		if (!VanillaHud.survivalBars() && !HudLayout.editorOpen()) {
			return;
		}
		float health = mount == null ? 20f : mount.getHealth();
		float max = mount == null ? 20f : Math.max(1f, mount.getMaxHealth());
		placedBar(graphics, HudLayout.Id.MOUNT, "MOUNT", format(health), health / max, Theme.ACCENT_DIM);
	}

	public static void extractExperience(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!VanillaHud.hasExperience() && !HudLayout.editorOpen()) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		Font font = Minecraft.getInstance().font;
		float t = player == null ? 0f : Mth.clamp(player.experienceProgress, 0f, 1f);
		int level = player == null ? 0 : player.experienceLevel;
		HudLayout.apply(graphics, font, HudLayout.Id.EXPERIENCE, () -> {
			float w = xpWidth();
			float barY = XP_BOX_H - XP_H;
			GuiDraw.panel(graphics, 0, barY, w, XP_H, 4, Theme.WINDOW, Theme.LINE);
			if (t > 0.01f) {
				GuiDraw.rounded(graphics, 1, barY + 1, Math.max(2f, (w - 2f) * t), XP_H - 2, 3, Theme.ACCENT);
			}
			String text = Integer.toString(level);
			float tx = (w - GuiDraw.titleWidth(font, text)) * 0.5f;
			GuiDraw.title(graphics, font, text, tx, 1, Theme.ACCENT);
		});
	}

	public static void extractExperienceLevel(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		// Level is drawn with the XP bar so both stay in one editor box.
	}

	private static void placedBar(GuiGraphicsExtractor graphics, HudLayout.Id id, String label, String value, float t, int fill) {
		Font font = Minecraft.getInstance().font;
		HudLayout.apply(graphics, font, id, () -> bar(graphics, font, 0, 0, label, value, t, fill));
	}

	private static void bar(GuiGraphicsExtractor graphics, Font font, float x, float y, String label, String value, float t, int fill) {
		t = Mth.clamp(t, 0f, 1f);
		GuiDraw.panel(graphics, x, y, BAR_W, BAR_H, 4, Theme.WINDOW, Theme.LINE);
		if (t > 0.01f) {
			GuiDraw.rounded(graphics, x + 1, y + 1, Math.max(2f, (BAR_W - 2f) * t), BAR_H - 2, 3, fill);
		}
		GuiDraw.small(graphics, font, label, x + 4, y + 2, Theme.TEXT);
		GuiDraw.small(graphics, font, value, x + BAR_W - 4 - GuiDraw.smallWidth(font, value), y + 2, Theme.TEXT);
	}

	private static String format(float value) {
		if (Math.abs(value - Math.round(value)) < 0.05f) {
			return Integer.toString(Math.round(value));
		}
		return String.format(java.util.Locale.ROOT, "%.1f", value);
	}
}
