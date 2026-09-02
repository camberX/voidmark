package dev.voidmark.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Pose;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PlayerPreview {
	private PlayerPreview() {
	}

	public static boolean draw(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float yaw, float pitch) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return false;
		}
		int x0 = Math.round(x);
		int y0 = Math.round(y);
		int x1 = Math.round(x + w);
		int y1 = Math.round(y + h);
		if (x1 <= x0 || y1 <= y0) {
			return false;
		}
		EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
		EntityRenderState state = dispatcher.extractEntity(player, 1f);
		state.shadowPieces.clear();
		state.outlineColor = 0;
		freeze(state, yaw, pitch);
		float size = Math.min(w, h) * 0.72f;
		Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf camera = new Quaternionf().rotateX(pitch * ((float) Math.PI / 180f) * 0.35f);
		Vector3f translation = new Vector3f(0f, state.boundingBoxHeight * 0.5f, 0f);
		graphics.entity(state, size, translation, pose, camera, x0, y0, x1, y1);
		return true;
	}

	/**
	 * Head yaw is applied on top of body yaw in the humanoid model. Keep
	 * {@code yRot} at 0 so the skull turns with the body instead of 2×.
	 */
	private static void freeze(EntityRenderState state, float yaw, float pitch) {
		if (state instanceof LivingEntityRenderState living) {
			living.pose = Pose.STANDING;
			living.bodyRot = 180f + yaw;
			living.yRot = 0f;
			living.xRot = pitch;
			living.scale = 1f;
			living.walkAnimationPos = 0f;
			living.walkAnimationSpeed = 0f;
			living.isAutoSpinAttack = false;
			living.hasRedOverlay = false;
		}
		if (state instanceof HumanoidRenderState humanoid) {
			humanoid.isCrouching = false;
			humanoid.isFallFlying = false;
			humanoid.isVisuallySwimming = false;
			humanoid.isPassenger = false;
			humanoid.swimAmount = 0f;
			humanoid.elytraRotX = 0f;
			humanoid.elytraRotY = 0f;
			humanoid.elytraRotZ = 0f;
		}
		if (state instanceof AvatarRenderState avatar) {
			avatar.shouldApplyFlyingYRot = false;
			avatar.flyingYRot = 0f;
			avatar.isSpectator = false;
		}
	}
}
