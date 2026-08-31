package dev.voidmark.client.render;

import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.net.ConnectionPing;
import dev.voidmark.client.visual.NickHider;
import net.minecraft.client.Minecraft;

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
		if (client.player == null || client.isLocalServer()) {
			return -1;
		}
		return ConnectionPing.get();
	}

	public static String pingLabel() {
		if (Minecraft.getInstance().isLocalServer()) {
			return "singleplayer";
		}
		int ping = ping();
		return ping < 0 ? "…" : ping + " ms";
	}

	public static String time() {
		return LocalTime.now().format(TIME);
	}

	public static String playerName() {
		if (VoidmarkConfig.get().nickEnabled) {
			String nick = NickHider.plainNick();
			return nick.isBlank() ? "" : nick;
		}
		Minecraft client = Minecraft.getInstance();
		String name = client.getGameProfile().name();
		if (name == null || name.isBlank()) {
			return "Player";
		}
		return name;
	}
}
