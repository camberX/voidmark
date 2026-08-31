package dev.voidmark.client.mixin;

import dev.voidmark.client.visual.NickHider;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {
	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
	private void voidmark$nick(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
		cir.setReturnValue(NickHider.rewrite(cir.getReturnValue()));
	}
}
