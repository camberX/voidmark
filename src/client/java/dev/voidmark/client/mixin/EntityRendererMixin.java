package dev.voidmark.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.render.MobGlowRenderer;
import dev.voidmark.client.render.NametagRenderer;
import dev.voidmark.client.visual.NickHider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
	/**
	 * Bypass frustum culling for entities that should glow through walls,
	 * so the outline renders even when the entity is behind geometry.
	 */
	@Inject(method = "affectedByCulling", at = @At("HEAD"), cancellable = true)
	private void voidmark$disableCulling(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		VoidmarkConfig config = VoidmarkConfig.get();
		if (config.mobGlowEnabled && config.mobGlowThroughWalls && MobGlowRenderer.listed(entity.getType())) {
			Minecraft client = Minecraft.getInstance();
			if (client.player != null && entity != client.player) {
				cir.setReturnValue(false);
			}
		}
	}

	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
		at = @At("RETURN")
	)
	private void voidmark$nickTag(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
		if (VoidmarkConfig.get().mobGlowEnabled) {
			int glow = MobGlowRenderer.outlineColor(entity);
			if (glow != 0) {
				state.outlineColor = glow;
			}
		}
		if (NametagRenderer.hidingVanilla(entity)) {
			state.nameTag = null;
			state.scoreText = null;
			return;
		}
		if (state.nameTag != null) {
			state.nameTag = NickHider.rewrite(state.nameTag);
		}
	}

	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Entity;D)Z", at = @At("HEAD"), cancellable = true)
	private void voidmark$hideName(Entity entity, double dist, CallbackInfoReturnable<Boolean> cir) {
		if (NametagRenderer.hidingVanilla(entity)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(
		method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void voidmark$hideNameDisplay(
		EntityRenderState state,
		PoseStack pose,
		SubmitNodeCollector collector,
		CameraRenderState camera,
		int yOffset,
		CallbackInfo ci
	) {
		if (NametagRenderer.hidingVanillaState(state)) {
			ci.cancel();
		}
	}
}
