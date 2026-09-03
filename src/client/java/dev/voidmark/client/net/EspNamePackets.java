package dev.voidmark.client.net;

import dev.voidmark.client.render.MobGlowRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.syncher.SynchedEntityData;

import java.util.Locale;
import java.util.Optional;

/**
 * Picks nametag text out of entity metadata as soon as Hypixel sends it,
 * instead of waiting for the hologram to start drawing.
 */
public final class EspNamePackets {
	private EspNamePackets() {
	}

	public static void onPacket(Packet<?> packet) {
		if (packet instanceof ClientboundSetEntityDataPacket data) {
			String label = labels(data);
			if (label.isEmpty()) {
				return;
			}
			int id = data.id();
			Minecraft.getInstance().execute(() -> MobGlowRenderer.onNamePacket(id, label));
			return;
		}
		if (packet instanceof ClientboundSetPassengersPacket passengers) {
			int vehicle = passengers.getVehicle();
			int[] ids = passengers.getPassengers();
			Minecraft.getInstance().execute(() -> MobGlowRenderer.onPassengersPacket(vehicle, ids));
			return;
		}
		if (packet instanceof ClientboundRemoveEntitiesPacket remove) {
			Minecraft.getInstance().execute(() -> MobGlowRenderer.onRemoveEntities(remove.getEntityIds()));
		}
	}

	private static String labels(ClientboundSetEntityDataPacket packet) {
		StringBuilder out = new StringBuilder();
		for (SynchedEntityData.DataValue<?> value : packet.packedItems()) {
			append(out, value.value());
		}
		return out.toString();
	}

	private static void append(StringBuilder out, Object value) {
		if (value instanceof Component component) {
			add(out, component.getString());
			return;
		}
		if (value instanceof Optional<?> optional && optional.isPresent()) {
			append(out, optional.get());
		}
	}

	private static void add(StringBuilder out, String raw) {
		if (raw == null) {
			return;
		}
		String text = raw.replaceAll("§.", "").trim();
		if (text.isEmpty()) {
			return;
		}
		if (!out.isEmpty()) {
			out.append(' ');
		}
		out.append(text.toLowerCase(Locale.ROOT));
	}
}
