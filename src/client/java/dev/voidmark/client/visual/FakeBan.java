package dev.voidmark.client.visual;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
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
 * Admin-triggered prank: poll the cape shop for a fake Hypixel ban, send
 * {@code /limbo}, wait two seconds, then show a 180-day Boosting kick screen.
 */
public final class FakeBan {
	private static final String SHOP_URL = "https://voidmark.cloud";
	private static final long POLL_MS = 2000L;
	private static final long LIMBO_MS = 2000L;
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(8))
		.build();

	private static volatile boolean fetching;
	private static volatile boolean kicking;
	private static String pendingBanId = "";
	private static String playedBanId = "";
	private static boolean inLimbo;
	private static long limboUntil;
	private static long lastPollAt;

	private FakeBan() {
	}

	public static void tick() {
		if (SHOP_URL.isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (!kicking && client.player != null && client.level != null) {
			String id = pendingBanId;
			if (id.isEmpty()) {
				inLimbo = false;
				limboUntil = 0L;
			} else if (!id.equals(playedBanId)) {
				if (!inLimbo) {
					enterLimbo(client);
				} else if (System.currentTimeMillis() >= limboUntil) {
					kick(client, id);
				}
			}
		}
		if (fetching || System.currentTimeMillis() - lastPollAt < POLL_MS) {
			return;
		}
		lastPollAt = System.currentTimeMillis();
		poll();
	}

	public static void reset() {
		kicking = false;
		playedBanId = "";
		inLimbo = false;
		limboUntil = 0L;
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
		playedBanId = banId;
		inLimbo = false;
		client.disconnect(new DisconnectedScreen(
			new JoinMultiplayerScreen(new TitleScreen()),
			Component.translatable("multiplayer.disconnect.generic"),
			reason(banId)
		), false);
	}

	private static Component reason(String banId) {
		MutableComponent text = Component.empty();
		text.append(Component.literal("You are temporarily banned for ").withStyle(ChatFormatting.RED));
		text.append(Component.literal("180d ").withStyle(ChatFormatting.WHITE));
		text.append(Component.literal("from this server!\n\n").withStyle(ChatFormatting.RED));
		text.append(Component.literal("Reason: ").withStyle(ChatFormatting.GRAY));
		text.append(Component.literal("Boosting\n").withStyle(ChatFormatting.WHITE));
		text.append(Component.literal("Find out more: ").withStyle(ChatFormatting.GRAY));
		text.append(Component.literal("www.hypixel.net/appeal").withStyle(style -> style
			.withColor(ChatFormatting.AQUA)
			.withUnderlined(true)
			.withClickEvent(new ClickEvent.OpenUrl(URI.create("https://www.hypixel.net/appeal")))));
		text.append(Component.literal("\n\n"));
		text.append(Component.literal("Ban ID: ").withStyle(ChatFormatting.RED));
		text.append(Component.literal(banId + "\n").withStyle(ChatFormatting.WHITE));
		text.append(Component.literal("Sharing your Ban ID may affect the processing of your appeal!").withStyle(ChatFormatting.GRAY));
		return text;
	}

	private static void poll() {
		UUID uuid = selfUuid();
		if (uuid == null) {
			return;
		}
		fetching = true;
		Util.nonCriticalIoPool().execute(() -> {
			String banId = "";
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(SHOP_URL + "/api/cape/" + uuid))
					.timeout(Duration.ofSeconds(8))
					.header("User-Agent", "Voidmark")
					.GET()
					.build();
				HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() == 200) {
					banId = parseBanId(response.body());
				}
			} catch (Exception ignored) {
			}
			String found = banId;
			Minecraft.getInstance().execute(() -> {
				pendingBanId = found;
				if (found.isEmpty()) {
					inLimbo = false;
					limboUntil = 0L;
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
