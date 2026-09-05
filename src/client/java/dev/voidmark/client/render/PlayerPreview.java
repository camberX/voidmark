package dev.voidmark.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwingAnimationType;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Inventory-style player in the click GUI. {@code graphics.entity} scale is
 * pixels per block (vanilla inventory uses 30), and the clip box is only as
 * large as the model. Both are mapped through the menu scale because the PiP
 * pass ignores the 2D pose. Armor and held items are stripped from the
 * extracted state only — in-world rendering is unchanged.
 */
public final class PlayerPreview {
	private static final float NAME_PAD = 20f;
	private static final float HINT_PAD = 16f;

	private PlayerPreview() {
	}

	public record View(float scale, float cx, float cy, float lift) {
		int sx(float local) {
			return Math.round((local - cx) * scale + cx);
		}

		int sy(float local) {
			return Math.round((local - cy) * scale + cy + lift);
		}
	}

	public record Drawn(float nameX, float nameY) {
	}

	public record Gear(ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet) {
		public static Gear none() {
			return new Gear(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
		}
	}

	public static Drawn draw(
		GuiGraphicsExtractor graphics,
		float x,
		float y,
		float w,
		float h,
		float yaw,
		float pitch,
		View view
	) {
		return draw(graphics, x, y, w, h, yaw, pitch, view, Gear.none(), true);
	}

	public static Drawn drawEquipped(
		GuiGraphicsExtractor graphics,
		float x,
		float y,
		float w,
		float h,
		float yaw,
		float pitch,
		View view,
		Gear gear
	) {
		return draw(graphics, x, y, w, h, yaw, pitch, view, gear == null ? Gear.none() : gear, false);
	}

	public static Drawn draw(
		GuiGraphicsExtractor graphics,
		float x,
		float y,
		float w,
		float h,
		float yaw,
		float pitch,
		View view,
		Gear gear,
		boolean strip
	) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || view == null) {
			return null;
		}
		float scale = Math.max(0.35f, view.scale);
		if (w < 24f || h < 40f) {
			return null;
		}
		EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
		EntityRenderState state = dispatcher.extractEntity(player, 1f);
		state.shadowPieces.clear();
		state.outlineColor = 0;
		state.nameTag = null;
		freeze(state, yaw, pitch, strip ? Gear.none() : gear, strip);
		return paint(graphics, state, x, y, w, h, yaw, pitch, view, scale, 1.5f, 62f);
	}

	public static Drawn drawEntity(
		GuiGraphicsExtractor graphics,
		EntityRenderState state,
		float x,
		float y,
		float w,
		float h,
		float yaw,
		float pitch,
		View view,
		float minBody
	) {
		if (state == null || view == null || w < 16f || h < 24f) {
			return null;
		}
		state.shadowPieces.clear();
		state.outlineColor = 0;
		state.nameTag = null;
		freeze(state, yaw, pitch, Gear.none(), true);
		return paint(graphics, state, x, y, w, h, yaw, pitch, view, Math.max(0.35f, view.scale), minBody, 48f);
	}

	private static Drawn paint(
		GuiGraphicsExtractor graphics,
		EntityRenderState state,
		float x,
		float y,
		float w,
		float h,
		float yaw,
		float pitch,
		View view,
		float scale,
		float minBody,
		float maxSize
	) {
		float body = Math.max(minBody, Math.min(4.5f, state.boundingBoxHeight));
		float size = Math.min(maxSize, Math.max(28f, h * 0.28f));
		float visH = size * body;
		float boxW = Math.min(w - 8f, Math.max(44f, size * 1.35f));
		float boxH = visH + 24f;
		if (boxH > h - NAME_PAD - HINT_PAD) {
			boxH = Math.max(28f, h - NAME_PAD - HINT_PAD);
			size = (boxH - 24f) / body;
			visH = size * body;
		}
		float boxX = x + (w - boxW) * 0.5f;
		float boxY = y + NAME_PAD + Math.max(0f, (h - NAME_PAD - HINT_PAD - boxH) * 0.45f);
		int x0 = view.sx(boxX);
		int y0 = view.sy(boxY);
		int x1 = view.sx(boxX + boxW);
		int y1 = view.sy(boxY + boxH);
		if (x1 <= x0 || y1 <= y0) {
			return null;
		}
		Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf camera = new Quaternionf().rotateX(pitch * ((float) Math.PI / 180f) * 0.35f);
		Vector3f translation = new Vector3f(0f, body * 0.5f, 0f);
		graphics.entity(state, size * scale, translation, pose, camera, x0, y0, x1, y1);
		float headX = boxX + boxW * 0.5f;
		float headTop = boxY + boxH * 0.5f - visH * 0.5f;
		return new Drawn(headX, headTop - 13f);
	}

	/**
	 * Head yaw is applied on top of body yaw in the humanoid model. Keep
	 * {@code yRot} at 0 so the skull turns with the body instead of 2×.
	 */
	private static void freeze(EntityRenderState state, float yaw, float pitch, Gear gear, boolean stripHands) {
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
			if (stripHands || gear == null || gear.head == null || gear.head.isEmpty()) {
				living.headItem.clear();
				living.wornHeadType = null;
				living.wornHeadProfile = null;
			}
		}
		if (state instanceof ArmedEntityRenderState armed && stripHands) {
			armed.rightHandItemStack = ItemStack.EMPTY;
			armed.leftHandItemStack = ItemStack.EMPTY;
			armed.rightHandItemState.clear();
			armed.leftHandItemState.clear();
			armed.rightArmPose = HumanoidModel.ArmPose.EMPTY;
			armed.leftArmPose = HumanoidModel.ArmPose.EMPTY;
			armed.attackTime = 0f;
			armed.swingAnimationType = SwingAnimationType.NONE;
		}
		if (state instanceof HumanoidRenderState humanoid) {
			humanoid.isCrouching = false;
			humanoid.isFallFlying = false;
			humanoid.isVisuallySwimming = false;
			humanoid.isPassenger = false;
			humanoid.isUsingItem = false;
			humanoid.ticksUsingItem = 0f;
			humanoid.swimAmount = 0f;
			humanoid.elytraRotX = 0f;
			humanoid.elytraRotY = 0f;
			humanoid.elytraRotZ = 0f;
			if (gear == null) {
				humanoid.headEquipment = ItemStack.EMPTY;
				humanoid.chestEquipment = ItemStack.EMPTY;
				humanoid.legsEquipment = ItemStack.EMPTY;
				humanoid.feetEquipment = ItemStack.EMPTY;
			} else {
				humanoid.headEquipment = empty(gear.head);
				humanoid.chestEquipment = empty(gear.chest);
				humanoid.legsEquipment = empty(gear.legs);
				humanoid.feetEquipment = empty(gear.feet);
			}
		}
		if (state instanceof AvatarRenderState avatar) {
			avatar.shouldApplyFlyingYRot = false;
			avatar.flyingYRot = 0f;
			avatar.isSpectator = false;
			avatar.heldOnHead.clear();
		}
	}

	private static ItemStack empty(ItemStack stack) {
		return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack;
	}
}
