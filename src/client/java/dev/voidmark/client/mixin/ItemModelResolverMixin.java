package dev.voidmark.client.mixin;

import dev.voidmark.client.item.ItemAppearance;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemModelResolver.class)
public class ItemModelResolverMixin {
	@ModifyVariable(method = "updateForTopItem", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private ItemStack voidmark$visualTop(ItemStack stack) {
		return ItemAppearance.visual(stack);
	}

	@ModifyVariable(method = "shouldPlaySwapAnimation", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private ItemStack voidmark$visualSwap(ItemStack stack) {
		return ItemAppearance.visual(stack);
	}

	@ModifyVariable(method = "swapAnimationScale", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private ItemStack voidmark$visualSwapScale(ItemStack stack) {
		return ItemAppearance.visual(stack);
	}
}
