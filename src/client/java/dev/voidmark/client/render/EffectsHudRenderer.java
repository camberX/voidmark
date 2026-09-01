package dev.voidmark.client.render;

import dev.voidmark.client.ui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.List;

public final class EffectsHudRenderer {
	private static final float CHIP_H = 16;
	private static final float ICON = 12;
	private static final float MIN_W = 72;
	private static final int MAX = 8;

	private EffectsHudRenderer() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		List<MobEffectInstance> effects = visible();
		if (effects.isEmpty() && !HudLayout.editorOpen()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		float boxW = drawWidth(font);
		HudLayout.apply(graphics, font, HudLayout.Id.EFFECTS, () -> {
			if (effects.isEmpty()) {
				GuiDraw.panel(graphics, 0, 0, boxW, CHIP_H, 5, Theme.WINDOW, Theme.LINE, Theme.ACCENT);
				GuiDraw.small(graphics, font, "EFFECTS", 8, 4, Theme.MUTED);
				return;
			}
			float y = 0;
			for (MobEffectInstance instance : effects) {
				float w = chipWidth(font, instance);
				drawChip(graphics, font, instance, boxW - w, y, w);
				y += CHIP_H + 3;
			}
		});
	}

	public static float drawWidth(Font font) {
		float max = MIN_W;
		for (MobEffectInstance instance : visible()) {
			max = Math.max(max, chipWidth(font, instance));
		}
		return max;
	}

	public static float drawHeight() {
		int n = Math.max(1, visible().size());
		return n * CHIP_H + (n - 1) * 3;
	}

	public static float stackHeight() {
		int n = visible().size();
		if (n == 0) {
			return 0;
		}
		return n * CHIP_H + (n - 1) * 3;
	}

	private static List<MobEffectInstance> visible() {
		LocalPlayer player = Minecraft.getInstance().player;
		List<MobEffectInstance> out = new ArrayList<>();
		if (player == null) {
			return out;
		}
		for (MobEffectInstance instance : player.getActiveEffects()) {
			if (instance.showIcon()) {
				out.add(instance);
			}
			if (out.size() >= MAX) {
				break;
			}
		}
		return out;
	}

	private static float chipWidth(Font font, MobEffectInstance instance) {
		MobEffect effect = instance.getEffect().value();
		Component name = effect.getDisplayName();
		String extra = extra(instance);
		return ICON + 14 + font.width(name) + GuiDraw.smallWidth(font, extra);
	}

	private static void drawChip(GuiGraphicsExtractor graphics, Font font, MobEffectInstance instance, float x, float y, float w) {
		MobEffect effect = instance.getEffect().value();
		Component name = effect.getDisplayName();
		String extra = extra(instance);
		int outline = effect.getCategory() == MobEffectCategory.HARMFUL ? Theme.DANGER : Theme.ACCENT;
		GuiDraw.panel(graphics, x, y, w, CHIP_H, 5, Theme.WINDOW, Theme.LINE, outline);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + 6, y + 2);
		graphics.pose().scale(ICON / 18f, ICON / 18f);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(instance.getEffect()), 0, 0, 18, 18);
		graphics.pose().popMatrix();
		float tx = x + 6 + ICON + 3;
		GuiDraw.text(graphics, font, name, tx, y + 3, 0xFFFFFFFF, false);
		if (!extra.isEmpty()) {
			GuiDraw.small(graphics, font, extra, tx + font.width(name), y + 4, Theme.MUTED);
		}
	}

	private static String extra(MobEffectInstance instance) {
		String amp = instance.getAmplifier() > 0 ? " " + roman(instance.getAmplifier() + 1) : "";
		String time = duration(instance);
		return amp + (time.isEmpty() ? "" : "  " + time);
	}

	private static String duration(MobEffectInstance instance) {
		if (instance.isInfiniteDuration()) {
			return "∞";
		}
		int sec = Mth.floor(instance.getDuration() / 20f);
		if (sec < 0) {
			return "";
		}
		return String.format(java.util.Locale.ROOT, "%d:%02d", sec / 60, sec % 60);
	}

	private static String roman(int value) {
		return switch (value) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			case 6 -> "VI";
			case 7 -> "VII";
			case 8 -> "VIII";
			case 9 -> "IX";
			case 10 -> "X";
			default -> Integer.toString(value);
		};
	}
}
