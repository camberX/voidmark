package dev.voidmark.client.net;

import dev.voidmark.client.node.EnderNodeTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;

public final class ClientPackets {
	private ClientPackets() {
	}

	public static void onReceived(Packet<?> packet) {
		if (!(packet instanceof ClientboundLevelParticlesPacket particles)) {
			return;
		}

		ParticleOptions options = particles.getParticle();
		double x = particles.getX();
		double y = particles.getY();
		double z = particles.getZ();

		Minecraft.getInstance().execute(() ->
			EnderNodeTracker.get().onParticle(x, y, z, options.getType())
		);
	}
}
