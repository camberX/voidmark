package dev.voidmark.client.mixin;

import dev.voidmark.client.visual.FakeBan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public class ConnectScreenMixin {
	@Inject(method = "startConnecting", at = @At("HEAD"), cancellable = true)
	private static void voidmark$blockBannedReconnect(
		Screen parent,
		Minecraft client,
		ServerAddress address,
		ServerData data,
		boolean quickPlay,
		TransferState transfer,
		CallbackInfo ci
	) {
		if (FakeBan.rejectConnect(parent, data)) {
			ci.cancel();
		}
	}
}
