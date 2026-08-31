package dev.voidmark.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class HudStats {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	private HudStats() {
	}

	public static int fps() {
		return Minecraft.getInstance().getFps();
	}

	public static int ping() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.player.connection == null) {
			return -1;
		}
		PlayerInfo info = client.player.connection.getPlayerInfo(client.player.getUUID());
		return info == null ? -1 : info.getLatency();
	}

	public static String time() {
		return LocalTime.now().format(TIME);
	}

	public static String playerName() {
		Minecraft client = Minecraft.getInstance();
		String name = client.getGameProfile().name();
		if (name == null || name.isBlank()) {
			return "Player";
		}
		return name;
	}
}
