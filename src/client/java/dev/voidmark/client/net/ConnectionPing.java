package dev.voidmark.client.net;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.util.Util;

public final class ConnectionPing {
	private static final long INTERVAL_MS = 2000L;
	private static volatile int pingMs = -1;
	private static volatile long lastSampleAt;
	private static long lastSentAt;
	private static long pendingSentAt;

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
		lastSampleAt = 0L;
		lastSentAt = 0L;
		pendingSentAt = 0L;
	}

	public static void onPong(long sentAtMillis) {
		if (pendingSentAt == 0L || sentAtMillis != pendingSentAt) {
			return;
		}
		pendingSentAt = 0L;
		long rtt = Util.getMillis() - sentAtMillis;
		if (rtt >= 0L && rtt < 10000L) {
			record((int) rtt);
		}
	}

	public static void onKeepAlive(long id) {
		if (get() >= 0 || pendingSentAt != 0L) {
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
		long now = Util.getMillis();
		if (pendingSentAt != 0L && now - pendingSentAt > 10000L) {
			pendingSentAt = 0L;
		}
		if (now - lastSentAt < INTERVAL_MS) {
			return;
		}
		lastSentAt = now;
		pendingSentAt = now;
		client.player.connection.send(new ServerboundPingRequestPacket(now));
	}

	private static void record(int sample) {
		pingMs = sample;
		lastSampleAt = Util.getMillis();
	}
}
