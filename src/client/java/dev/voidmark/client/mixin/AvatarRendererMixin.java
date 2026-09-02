package dev.voidmark.client.mixin;

import dev.voidmark.client.render.NametagRenderer;
import dev.voidmark.client.visual.ShopCape;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("RETURN")
	)
	private void voidmark$showCustomCape(Avatar entity, AvatarRenderState state, float tickDelta, CallbackInfo ci) {
		if (entity == null || state.skin == null) {
			return;
		}
		state.skin = ShopCape.patch(entity.getUUID(), state.skin);
		if (ShopCape.showing(entity.getUUID())) {
			state.showCape = true;
		}
	}

	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("HEAD"), cancellable = true)
	private void voidmark$hideNametag(Avatar entity, double dist, CallbackInfoReturnable<Boolean> cir) {
		if (NametagRenderer.hidingVanilla(entity)) {
			cir.setReturnValue(false);
		}
	}
}
