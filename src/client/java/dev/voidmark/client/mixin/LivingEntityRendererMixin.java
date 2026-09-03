package dev.voidmark.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.voidmark.client.item.ItemAppearance;
import dev.voidmark.client.render.ChamsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
	@Redirect(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"
		)
	)
	private ItemStack voidmark$visualEquip(LivingEntity entity, EquipmentSlot slot) {
		return ItemAppearance.worn(entity.getItemBySlot(slot), slot);
	}

	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At("HEAD")
	)
	private void voidmark$chamsBegin(
		LivingEntityRenderState state,
		PoseStack pose,
		SubmitNodeCollector collector,
		CameraRenderState camera,
		CallbackInfo ci
	) {
		ChamsRenderer.begin(state);
	}

	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At("RETURN")
	)
	private void voidmark$chamsEnd(
		LivingEntityRenderState state,
		PoseStack pose,
		SubmitNodeCollector collector,
		CameraRenderState camera,
		CallbackInfo ci
	) {
		ChamsRenderer.end();
	}
}
