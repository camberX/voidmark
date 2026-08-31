package dev.voidmark.client.net;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.util.Util;

public final class ConnectionPing {
	private static volatile int pingMs = -1;
	private static volatile long lastSampleAt;
	private static int ticks;

	private ConnectionPing() {
	}

	public static int get() {
		if (pingMs < 0) {
			return -1;
		}
		if (Util.getMillis() - lastSampleAt > 15000L) {
			return -1;
		}
		return pingMs;
	}

	public static void reset() {
		pingMs = -1;
		ticks = 0;
	}

	public static void onPong(long sentAtMillis) {
		long rtt = Util.getMillis() - sentAtMillis;
		if (rtt >= 0L && rtt < 10000L) {
			record((int) rtt);
		}
	}

	public static void onKeepAlive(long id) {
		if (get() >= 0) {
			return;
		}
		long delay = Util.getMillis() - id;
		if (delay >= 0L && delay < 5000L) {
			record((int) delay);
		}
	}

	public static void tick(Minecraft client) {
		if (client.player == null || client.player.connection == null || client.isLocalServer()) {
			return;
		}
		if (++ticks < 40) {
			return;
		}
		ticks = 0;
		client.player.connection.send(new ServerboundPingRequestPacket(Util.getMillis()));
	}

	private static void record(int sample) {
		int previous = pingMs;
		pingMs = previous < 0 ? sample : (previous * 3 + sample) / 4;
		lastSampleAt = Util.getMillis();
	}
}
