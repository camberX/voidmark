package dev.voidmark.client.mixin;

import dev.voidmark.client.combat.Hitsound;
import dev.voidmark.client.render.PickupLogRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	@Inject(method = "handleTakeItemEntity", at = @At("HEAD"))
	private void voidmark$pickupLog(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || packet.getPlayerId() != client.player.getId()) {
			return;
		}
		Entity entity = client.level.getEntity(packet.getItemId());
		if (entity instanceof ItemEntity item) {
			PickupLogRenderer.add(item.getItem(), packet.getAmount());
		}
	}

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
