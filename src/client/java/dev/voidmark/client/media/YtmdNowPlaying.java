package dev.voidmark.client.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

final class YtmdNowPlaying {
	private static final URI QUERY = URI.create("http://127.0.0.1:9863/query");
	private static final HttpClient CLIENT = HttpClient.newBuilder()
		.connectTimeout(Duration.ofMillis(160))
		.build();
	private static long quietUntil;

	NowPlaying snapshot() {
		long now = System.currentTimeMillis();
		if (now < quietUntil) {
			return NowPlaying.none();
		}
		try {
			HttpRequest request = HttpRequest.newBuilder(QUERY)
				.timeout(Duration.ofMillis(220))
				.GET()
				.build();
			HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				return NowPlaying.none();
			}
			JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
			JsonObject player = object(root, "player");
			JsonObject track = object(root, "track");
			if (!bool(player, "hasSong", true)) {
				return NowPlaying.none();
			}
			String title = text(track, "title", "song", "name");
			if (title.isBlank()) {
				return NowPlaying.none();
			}
			boolean paused = bool(player, "isPaused", false);
			return new NowPlaying(
				title,
				text(track, "author", "artist"),
				text(track, "album"),
				"YouTube Music",
				"ytmd",
				!paused,
				positionMs(player),
				durationMs(player, track),
				System.nanoTime()
			);
		} catch (Exception exception) {
			quietUntil = System.currentTimeMillis() + 4000L;
			return NowPlaying.none();
		}
	}

	static boolean command(String command) {
		try {
			HttpRequest request = HttpRequest.newBuilder(QUERY)
				.timeout(Duration.ofMillis(280))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString("{\"command\":\"" + command + "\"}"))
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
		return root;
	}

	private static long positionMs(JsonObject player) {
		double millis = number(player, "seekbarCurrentPositionMilliSeconds");
		if (millis > 0) {
			return Math.round(millis);
		}
		double value = number(player, "seekbarCurrentPosition", "position");
		return value > 10_000 ? Math.round(value) : Math.round(value * 1000.0);
	}

	private static long durationMs(JsonObject player, JsonObject track) {
		double value = number(player, "songDuration");
		if (value <= 0) {
			value = number(track, "duration", "durationMs");
		}
		return value > 10_000 ? Math.round(value) : Math.round(value * 1000.0);
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
				String value = json.get(key).getAsString().trim();
				if (!value.isBlank()) {
					return value;
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
}
