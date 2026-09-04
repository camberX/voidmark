package dev.voidmark.client.net;

import dev.voidmark.client.combat.Hitsound;
import dev.voidmark.client.node.EnderNodeTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;

public final class ClientPackets {
	private ClientPackets() {
	}

	public static void onReceived(Packet<?> packet) {
		if (packet instanceof ClientboundPongResponsePacket pong) {
			ConnectionPing.onPong(pong.time());
			return;
		}
		if (packet instanceof ClientboundKeepAlivePacket keepAlive) {
			ConnectionPing.onKeepAlive(keepAlive.getId());
			return;
		}
		if (packet instanceof ClientboundLevelParticlesPacket particles) {
			ParticleOptions options = particles.getParticle();
			double x = particles.getX();
			double y = particles.getY();
			double z = particles.getZ();
			Minecraft.getInstance().execute(() ->
				EnderNodeTracker.get().onParticle(x, y, z, options.getType())
			);
		}
		if (packet instanceof ClientboundHurtAnimationPacket hurt) {
			int id = hurt.id();
			Minecraft.getInstance().execute(() -> Hitsound.onHurt(id));
		}
		if (packet instanceof ClientboundDamageEventPacket damage) {
			int id = damage.entityId();
			int cause = damage.sourceCauseId();
			Minecraft.getInstance().execute(() -> Hitsound.onDamage(id, cause));
		}
		if (packet instanceof ClientboundAnimatePacket animate) {
			int action = animate.getAction();
			if (action == ClientboundAnimatePacket.CRITICAL_HIT || action == ClientboundAnimatePacket.MAGIC_CRITICAL_HIT) {
				int id = animate.getId();
				Minecraft.getInstance().execute(() -> Hitsound.onHurt(id));
			}
		}
		EspNamePackets.onPacket(packet);
	}
}
