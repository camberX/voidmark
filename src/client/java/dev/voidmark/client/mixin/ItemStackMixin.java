package dev.voidmark.client.mixin;

import dev.voidmark.client.item.ItemAppearance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ItemStack.class)
public class ItemStackMixin {
	@Inject(method = "getCustomName", at = @At("HEAD"), cancellable = true)
	private void voidmark$customName(CallbackInfoReturnable<Component> cir) {
		ItemStack shown = ItemAppearance.named((ItemStack) (Object) this);
		if (shown != (Object) this) {
			cir.setReturnValue(shown.getCustomName());
		}
	}

	@Inject(method = "getHoverName", at = @At("HEAD"), cancellable = true)
	private void voidmark$hoverName(CallbackInfoReturnable<Component> cir) {
		ItemStack shown = ItemAppearance.named((ItemStack) (Object) this);
		if (shown != (Object) this) {
			cir.setReturnValue(shown.getHoverName());
		}
	}

	@Inject(method = "getStyledHoverName", at = @At("HEAD"), cancellable = true)
	private void voidmark$styledHoverName(CallbackInfoReturnable<Component> cir) {
		ItemStack shown = ItemAppearance.named((ItemStack) (Object) this);
		if (shown != (Object) this) {
			cir.setReturnValue(shown.getStyledHoverName());
		}
	}

	@Inject(method = "getTooltipLines", at = @At("HEAD"), cancellable = true)
	private void voidmark$tooltip(
		Item.TooltipContext context,
		Player player,
		TooltipFlag flag,
		CallbackInfoReturnable<List<Component>> cir
	) {
		ItemStack shown = ItemAppearance.named((ItemStack) (Object) this);
		if (shown != (Object) this) {
			cir.setReturnValue(shown.getTooltipLines(context, player, flag));
		}
	}
}
