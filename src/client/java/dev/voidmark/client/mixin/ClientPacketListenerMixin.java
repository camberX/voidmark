package dev.voidmark.client.mixin;

import dev.voidmark.client.combat.Hitsound;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	@Inject(method = "handleGameEvent", at = @At("HEAD"), cancellable = true)
	private void voidmark$skipDelayedArrowPing(ClientboundGameEventPacket packet, CallbackInfo ci) {
		if (packet.getEvent() != ClientboundGameEventPacket.PLAY_ARROW_HIT_SOUND) {
			return;
		}
		if (Hitsound.suppressVanillaArrowPing()) {
			ci.cancel();
		}
	}
}
