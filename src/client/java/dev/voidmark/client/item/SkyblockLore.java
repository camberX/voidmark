package dev.voidmark.client.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmark.Voidmark;
import net.minecraft.util.Util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hypixel Skyblock item name and lore from the NotEnoughUpdates item repo.
 * Type an item name in {@code /vm edit}; Voidmark fills the tooltip from here
 * when you have not held a live copy of that item.
 */
public final class SkyblockLore {
	private static final String[] URLS = {
		"https://cdn.jsdelivr.net/gh/NotEnoughUpdates/NotEnoughUpdates-REPO@master/items/%s.json",
		"https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/items/%s.json"
	};
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(8))
		.build();
	private static final Map<String, Snapshot> READY = new ConcurrentHashMap<>();
	private static final Map<String, Boolean> PENDING = new ConcurrentHashMap<>();

	public record Snapshot(String name, List<String> lore) {
		boolean present() {
			return (name != null && !name.isBlank()) || (lore != null && !lore.isEmpty());
		}

		ItemText text(boolean maxed) {
			if (!present()) {
				return ItemText.empty();
			}
			return maxed ? SkyblockMaxed.apply(name, lore) : ItemText.fromLegacy(name, lore);
		}
	}

	private SkyblockLore() {
	}

	public static ItemText get(String id) {
		return get(id, false);
	}

	public static ItemText get(String id, boolean maxed) {
		Snapshot snapshot = snapshot(id);
		return snapshot == null ? null : snapshot.text(maxed);
	}

	public static Snapshot snapshot(String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		return READY.get(norm(id));
	}

	public static boolean loading(String id) {
		if (id == null || id.isBlank()) {
			return false;
		}
		String key = norm(id);
		return PENDING.containsKey(key) && !READY.containsKey(key);
	}

	public static boolean failed(String id) {
		Snapshot snapshot = snapshot(id);
		return snapshot != null && !snapshot.present();
	}

	public static void request(String id) {
		if (id == null || id.isBlank()) {
			return;
		}
		String key = norm(id);
		if (READY.containsKey(key) || PENDING.putIfAbsent(key, Boolean.TRUE) != null) {
			return;
		}
		Util.nonCriticalIoPool().execute(() -> fetch(key));
	}

	private static void fetch(String id) {
		try {
			for (String template : URLS) {
				Snapshot snapshot = download(template.formatted(id));
				if (snapshot != null && snapshot.present()) {
					READY.put(id, snapshot);
					return;
				}
			}
			READY.put(id, new Snapshot("", List.of()));
		} catch (Exception exception) {
			READY.put(id, new Snapshot("", List.of()));
			Voidmark.LOGGER.warn("Skyblock lore lookup failed for {}", id, exception);
		} finally {
			PENDING.remove(id);
		}
	}

	private static Snapshot download(String url) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(12))
			.header("User-Agent", "Voidmark/" + Voidmark.MOD_ID)
			.GET()
			.build();
		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			return null;
		}
		JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
		String name = string(root, "displayname");
		if (name.isBlank()) {
			name = string(root, "internalname");
		}
		List<String> lines = new ArrayList<>();
		JsonArray lore = root.getAsJsonArray("lore");
		if (lore != null) {
			for (JsonElement element : lore) {
				if (element == null || !element.isJsonPrimitive()) {
					lines.add("");
					continue;
				}
				lines.add(element.getAsString());
			}
		}
		return new Snapshot(name, lines);
	}

	private static String string(JsonObject root, String key) {
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive()) {
			return "";
		}
		String value = element.getAsString();
		return value == null ? "" : value;
	}

	private static String norm(String id) {
		String raw = id.trim();
		if (raw.toLowerCase(Locale.ROOT).startsWith("sb:")) {
			raw = raw.substring(3);
		}
		return raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
	}
}
