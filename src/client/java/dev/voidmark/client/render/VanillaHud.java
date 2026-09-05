package dev.voidmark.client.render;

import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.ui.LoadoutsScreen;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PlayerRideableJumping;

public final class VanillaHud {
	private VanillaHud() {
	}

	public static void init() {
		HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR, original -> (graphics, delta) -> {
			if (custom(VoidmarkConfig.get().hudHotbar) && !spectator()) {
				HotbarHudRenderer.extract(graphics, delta);
			} else {
				original.extractRenderState(graphics, delta);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, original -> (graphics, delta) -> {
			if (custom(VoidmarkConfig.get().hudHealth)) {
				StatusHudRenderer.extractHealth(graphics, delta);
			} else {
				original.extractRenderState(graphics, delta);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, original -> (graphics, delta) -> {
			if (custom(VoidmarkConfig.get().hudHunger)) {
				StatusHudRenderer.extractHunger(graphics, delta);
			} else {
				original.extractRenderState(graphics, delta);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.ARMOR_BAR, original -> (graphics, delta) -> {
			if (custom(VoidmarkConfig.get().hudArmor)) {
				StatusHudRenderer.extractArmor(graphics, delta);
			} else {
				original.extractRenderState(graphics, delta);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.AIR_BAR, original -> (graphics, delta) -> {
			if (custom(VoidmarkConfig.get().hudAir)) {
				StatusHudRenderer.extractAir(graphics, delta);
			} else {
				original.extractRenderState(graphics, delta);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.MOUNT_HEALTH, original -> (graphics, delta) -> {
			if (custom(VoidmarkConfig.get().hudMountHealth)) {
				StatusHudRenderer.extractMount(graphics, delta);
			} else {
				original.extractRenderState(graphics, delta);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.INFO_BAR, original -> (graphics, delta) -> {
			if (custom(VoidmarkConfig.get().hudExperience) && !jumpBar()) {
				StatusHudRenderer.extractExperience(graphics, delta);
			} else {
				original.extractRenderState(graphics, delta);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.EXPERIENCE_LEVEL, original -> (graphics, delta) -> {
			if (custom(VoidmarkConfig.get().hudExperience) && !jumpBar()) {
				StatusHudRenderer.extractExperienceLevel(graphics, delta);
			} else {
				original.extractRenderState(graphics, delta);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, original -> (graphics, delta) -> {
			if (custom(VoidmarkConfig.get().hudScoreboard)) {
				ScoreboardHudRenderer.extract(graphics, delta);
			} else {
				original.extractRenderState(graphics, delta);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.BOSS_BAR, original -> (graphics, delta) -> {
			if (custom(VoidmarkConfig.get().hudBossBar)) {
				BossBarHudRenderer.extract(graphics, delta);
			} else {
				original.extractRenderState(graphics, delta);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.MOB_EFFECTS, original -> (graphics, delta) -> {
			if (custom(VoidmarkConfig.get().hudEffects)) {
				EffectsHudRenderer.extract(graphics, delta);
			} else {
				original.extractRenderState(graphics, delta);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.HELD_ITEM_TOOLTIP, original -> (graphics, delta) -> {
			if (custom(VoidmarkConfig.get().hudHeldItem)) {
				HeldItemHudRenderer.extract(graphics, delta);
			} else {
				original.extractRenderState(graphics, delta);
			}
		});
	}

	public static boolean hidden() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return true;
		}
		return (client.screen instanceof AbstractContainerScreen<?> || client.screen instanceof LoadoutsScreen)
			&& !HudLayout.editorOpen();
	}

	public static boolean spectator() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.isSpectator();
	}

	public static boolean survivalBars() {
		Minecraft client = Minecraft.getInstance();
		return client.gameMode != null && client.gameMode.canHurtPlayer() && !spectator();
	}

	public static boolean hasExperience() {
		Minecraft client = Minecraft.getInstance();
		return client.gameMode != null && client.gameMode.hasExperience() && !spectator();
	}

	public static LivingEntity mount() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return null;
		}
		Entity vehicle = player.getVehicle();
		while (vehicle != null) {
			if (vehicle instanceof LivingEntity living && living.showVehicleHealth() && living.getMaxHealth() > 1f) {
				return living;
			}
			vehicle = vehicle.getVehicle();
		}
		return null;
	}

	public static float hotbarTop(int guiH) {
		if (VoidmarkConfig.get().hudHotbar && !spectator()) {
			return guiH - HotbarHudRenderer.HEIGHT - 3;
		}
		return guiH - 22;
	}

	private static boolean custom(boolean enabled) {
		return enabled && !hidden();
	}

	private static boolean jumpBar() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return false;
		}
		Entity vehicle = player.getControlledVehicle();
		return vehicle instanceof PlayerRideableJumping jumping && jumping.canJump();
	}
}
