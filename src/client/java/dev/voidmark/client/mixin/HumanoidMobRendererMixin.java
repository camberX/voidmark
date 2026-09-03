package dev.voidmark.client.mixin;

import dev.voidmark.client.item.ItemAppearance;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidMobRenderer.class)
public class HumanoidMobRendererMixin {
	@Inject(method = "extractHumanoidRenderState", at = @At("RETURN"))
	private static void voidmark$wornArmor(
		LivingEntity entity,
		HumanoidRenderState state,
		float tickDelta,
		ItemModelResolver resolver,
		CallbackInfo ci
	) {
		state.headEquipment = ItemAppearance.worn(state.headEquipment, EquipmentSlot.HEAD);
		state.chestEquipment = ItemAppearance.worn(state.chestEquipment, EquipmentSlot.CHEST);
		state.legsEquipment = ItemAppearance.worn(state.legsEquipment, EquipmentSlot.LEGS);
		state.feetEquipment = ItemAppearance.worn(state.feetEquipment, EquipmentSlot.FEET);
	}
}
