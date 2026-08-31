package dev.voidmark.client.mixin;

import dev.voidmark.client.visual.CustomCape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("RETURN")
	)
	private void voidmark$showCustomCape(Avatar entity, AvatarRenderState state, float tickDelta, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || entity != client.player || !CustomCape.ready()) {
			return;
		}
		state.showCape = true;
		state.skin = CustomCape.patch(state.skin);
	}
}
