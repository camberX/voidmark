package dev.voidmark.client.mixin;

import dev.voidmark.client.visual.FakeBan;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public class ConnectScreenMixin {
	@Inject(method = "updateStatus", at = @At("TAIL"))
	private void voidmark$joiningWorld(Component status, CallbackInfo ci) {
		FakeBan.onConnectStatus(status);
	}
}
