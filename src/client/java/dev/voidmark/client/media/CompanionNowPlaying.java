package dev.voidmark.client.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

final class CompanionNowPlaying {
	private static final HttpClient CLIENT = HttpClient.newBuilder()
		.connectTimeout(Duration.ofMillis(120))
		.build();
	private static final List<String> ENDPOINTS = List.of(
		"http://127.0.0.1:26558/api/v1/song",
		"http://127.0.0.1:26559/api/v1/song",
		"http://127.0.0.1:9863/query",
		"http://127.0.0.1:10767/api/v1/playback/now-playing",
		"http://127.0.0.1:26558/api/v1/now-playing",
		"http://127.0.0.1:31333/api/v1/song",
		"http://127.0.0.1:1870/api/v1/song"
	);

	private String live;
	private String kind;
	private long quietUntil;

	NowPlaying snapshot() {
		long now = System.currentTimeMillis();
		if (live != null) {
			NowPlaying track = fetch(live);
			if (track.present()) {
				return track;
			}
			live = null;
			kind = null;
		}
		if (now < quietUntil) {
			return NowPlaying.none();
		}
		for (String endpoint : ENDPOINTS) {
			NowPlaying track = fetch(endpoint);
			if (track.present()) {
				live = endpoint;
				kind = kindOf(endpoint);
				return track;
			}
		}
		quietUntil = now + 2500L;
		return NowPlaying.none();
	}

	boolean control(String action) {
		if (live == null) {
			return false;
		}
		if ("ytmd".equals(kind)) {
			String command = switch (action) {
				case "next" -> "track-next";
				case "prev" -> "track-previous";
				default -> "track-playPause";
			};
			return post(live, "{\"command\":\"" + command + "\"}");
		}
		String path = switch (action) {
			case "next" -> "/api/v1/next";
			case "prev" -> "/api/v1/previous";
			default -> "/api/v1/toggle-play";
		};
		try {
			URI base = URI.create(live);
			URI target = new URI(base.getScheme(), null, base.getHost(), base.getPort(), path, null, null);
			return getOk(target) || post(target.toString(), "{}");
		} catch (Exception exception) {
			return false;
		}
	}

	private NowPlaying fetch(String endpoint) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
				.timeout(Duration.ofMillis(180))
				.GET()
				.build();
			HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				return NowPlaying.none();
			}
			return parse(response.body(), endpoint);
		} catch (Exception exception) {
			return NowPlaying.none();
		}
	}

	private NowPlaying parse(String body, String endpoint) {
		if (body == null || body.isBlank()) {
			return NowPlaying.none();
		}
		char start = body.trim().charAt(0);
		if (start != '{' && start != '[') {
			return NowPlaying.none();
		}
		try {
			JsonElement rootEl = JsonParser.parseString(body);
			if (!rootEl.isJsonObject()) {
				return NowPlaying.none();
			}
			JsonObject root = rootEl.getAsJsonObject();
			JsonObject player = object(root, "player");
			JsonObject track = object(root, "track");
			JsonObject info = object(root, "info");
			if (player.has("hasSong") && !bool(player, "hasSong", true)) {
				return NowPlaying.none();
			}
			String title = firstNonBlank(
				text(track, "title", "song", "name", "videoTitle"),
				text(root, "title", "song", "name", "videoTitle"),
				text(info, "name", "title")
			);
			if (title.isBlank()) {
				return NowPlaying.none();
			}
			String artist = firstNonBlank(
				text(track, "author", "artist", "artistsName", "artistName"),
				text(root, "author", "artist", "artistsName", "artistName"),
				text(info, "artistName", "artist"),
				artists(root),
				artists(track),
				artists(info)
			);
			boolean paused = bool(player, "isPaused", false) || bool(root, "isPaused", false);
			if (root.has("playing")) {
				paused = !bool(root, "playing", true);
			}
			String app = kindOf(endpoint).equals("ytmd") ? "YouTube Music Desktop" : "YouTube Music";
			return new NowPlaying(
				title,
				artist,
				firstNonBlank(text(track, "album"), text(root, "album"), text(info, "albumName", "album")),
				app,
				kindOf(endpoint),
				cover(track, root, info),
				!paused,
				positionMs(player, root),
				durationMs(player, track, root),
				System.nanoTime()
			);
		} catch (Exception exception) {
			return NowPlaying.none();
		}
	}

	private static String kindOf(String endpoint) {
		if (endpoint.contains(":9863")) {
			return "ytmd";
		}
		if (endpoint.contains(":10767")) {
			return "cider";
		}
		return "ytm";
	}

	private static boolean getOk(URI uri) {
		try {
			HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofMillis(250))
				.GET()
				.build();
			HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
			return response.statusCode() >= 200 && response.statusCode() < 300;
		} catch (Exception exception) {
			return false;
		}
	}

	private static boolean post(String uri, String json) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
				.timeout(Duration.ofMillis(250))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json))
				.build();
			HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
			return response.statusCode() >= 200 && response.statusCode() < 300;
		} catch (Exception exception) {
			return false;
		}
	}

	private static JsonObject object(JsonObject root, String key) {
		if (root != null && root.has(key) && root.get(key).isJsonObject()) {
			return root.getAsJsonObject(key);
		}
		return new JsonObject();
	}

	private static long positionMs(JsonObject player, JsonObject root) {
		double millis = number(player, "seekbarCurrentPositionMilliSeconds");
		if (millis <= 0) {
			millis = number(root, "elapsedSeconds", "position", "seekbarCurrentPosition", "currentTime");
			if (millis > 0 && millis < 10_000) {
				millis *= 1000.0;
			}
		}
		return Math.max(0L, Math.round(millis));
	}

	private static long durationMs(JsonObject player, JsonObject track, JsonObject root) {
		double value = number(player, "songDuration");
		if (value <= 0) {
			value = number(track, "duration", "durationMs", "durationSeconds");
		}
		if (value <= 0) {
			value = number(root, "duration", "durationMs", "durationSeconds", "songDuration");
		}
		if (value > 0 && value < 10_000) {
			value *= 1000.0;
		}
		return Math.max(0L, Math.round(value));
	}

	private static String artists(JsonObject json) {
		if (json == null || !json.has("artists") || !json.get("artists").isJsonArray()) {
			return "";
		}
		JsonArray array = json.getAsJsonArray("artists");
		StringBuilder out = new StringBuilder();
		for (JsonElement el : array) {
			String name = "";
			if (el.isJsonPrimitive()) {
				name = el.getAsString();
			} else if (el.isJsonObject()) {
				name = text(el.getAsJsonObject(), "name", "text");
			}
			if (name.isBlank()) {
				continue;
			}
			if (out.length() > 0) {
				out.append(", ");
			}
			out.append(name);
		}
		return out.toString();
	}

	private static boolean bool(JsonObject json, String key, boolean fallback) {
		if (json == null || !json.has(key)) {
			return fallback;
		}
		try {
			return json.get(key).getAsBoolean();
		} catch (Exception exception) {
			return fallback;
		}
	}

	private static String text(JsonObject json, String... keys) {
		if (json == null) {
			return "";
		}
		for (String key : keys) {
			if (!json.has(key) || json.get(key).isJsonNull()) {
				continue;
			}
			try {
				if (json.get(key).isJsonPrimitive()) {
					String value = json.get(key).getAsString().trim();
					if (!value.isBlank()) {
						return value;
					}
				}
			} catch (Exception ignored) {
			}
		}
		return "";
	}

	private static double number(JsonObject json, String... keys) {
		if (json == null) {
			return 0d;
		}
		for (String key : keys) {
			if (json.has(key) && json.get(key).isJsonPrimitive()) {
				try {
					return json.get(key).getAsDouble();
				} catch (Exception ignored) {
				}
			}
		}
		return 0d;
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return "";
	}

	private static String cover(JsonObject track, JsonObject root, JsonObject info) {
		return firstNonBlank(
			text(track, "imageSrc", "thumbnailUrl", "thumbnail", "cover", "albumArt", "image", "albumCover", "coverUrl", "artworkUrl"),
			text(root, "imageSrc", "thumbnailUrl", "thumbnail", "cover", "albumArt", "image", "albumCover", "coverUrl", "artworkUrl"),
			text(info, "artworkUrl", "cover", "imageSrc", "thumbnailUrl"),
			artworkUrl(track),
			artworkUrl(root),
			artworkUrl(info)
		);
	}

	private static String artworkUrl(JsonObject json) {
		if (json == null || !json.has("artwork")) {
			return "";
		}
		try {
			if (json.get("artwork").isJsonPrimitive()) {
				return json.get("artwork").getAsString().trim();
			}
			if (json.get("artwork").isJsonObject()) {
				return text(json.getAsJsonObject("artwork"), "url", "src", "uri");
			}
		} catch (Exception ignored) {
		}
		return "";
	}
}
