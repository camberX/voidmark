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
				.timeout(Duration.ofMillis(500))
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
			JsonObject video = object(root, "video");
			if (player.has("hasSong") && !bool(player, "hasSong", true)) {
				return NowPlaying.none();
			}
			String title = firstNonBlank(
				join(track, "title", "song", "name", "videoTitle"),
				join(root, "title", "song", "name", "videoTitle"),
				join(info, "name", "title"),
				join(video, "title", "name")
			);
			if (title.isBlank()) {
				return NowPlaying.none();
			}
			String artist = firstNonBlank(
				join(track, "author", "artist", "artists", "artistsName", "artistName"),
				join(root, "author", "artist", "artists", "artistsName", "artistName"),
				join(info, "artistName", "artist", "artists"),
				join(video, "author", "artist", "artists")
			);
			boolean paused = bool(player, "isPaused", false) || bool(root, "isPaused", false);
			if (root.has("playing")) {
				paused = !bool(root, "playing", true);
			}
			String app = kindOf(endpoint).equals("ytmd") ? "YouTube Music Desktop" : "YouTube Music";
			String videoId = firstNonBlank(
				join(root, "videoId"),
				join(track, "videoId"),
				join(video, "id", "videoId"),
				videoIdFromUrl(join(root, "url", "videoUrl", "link"))
			);
			return new NowPlaying(
				title,
				artist,
				firstNonBlank(join(track, "album"), join(root, "album"), join(info, "albumName", "album"), join(video, "album")),
				app,
				kindOf(endpoint),
				cover(track, root, info, video, videoId),
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
		long ms = roundPositive(number(
			player,
			"seekbarCurrentPositionMilliSeconds",
			"elapsedMilliSeconds",
			"positionMs",
			"currentTimeMs"
		));
		if (ms <= 0L) {
			ms = roundPositive(number(root, "elapsedMilliSeconds", "positionMs", "currentTimeMs"));
		}
		if (ms <= 0L) {
			ms = secondsToMs(number(
				player,
				"seekbarCurrentPosition",
				"elapsedSeconds",
				"currentTime",
				"position"
			));
		}
		if (ms <= 0L) {
			ms = secondsToMs(number(
				root,
				"elapsedSeconds",
				"position",
				"currentTime",
				"seekbarCurrentPosition",
				"currentPlaybackTime"
			));
		}
		return ms;
	}

	private static long durationMs(JsonObject player, JsonObject track, JsonObject root) {
		long ms = roundPositive(number(
			player,
			"songDurationMilliSeconds",
			"durationMs",
			"durationInMillis"
		));
		if (ms <= 0L) {
			ms = roundPositive(number(track, "durationMs", "durationInMillis", "lengthMs"));
		}
		if (ms <= 0L) {
			ms = roundPositive(number(root, "durationMs", "durationInMillis"));
		}
		if (ms <= 0L) {
			ms = secondsToMs(number(player, "songDuration", "duration", "durationSeconds"));
		}
		if (ms <= 0L) {
			ms = secondsToMs(number(track, "duration", "durationSeconds", "length"));
		}
		if (ms <= 0L) {
			ms = secondsToMs(number(root, "duration", "durationSeconds", "songDuration"));
		}
		return ms;
	}

	private static long secondsToMs(double value) {
		if (value <= 0d) {
			return 0L;
		}
		if (value >= 10_000d) {
			return Math.round(value);
		}
		return Math.round(value * 1000.0);
	}

	private static long roundPositive(double value) {
		return value > 0d ? Math.round(value) : 0L;
	}

	private static String artists(JsonObject json) {
		if (json == null) {
			return "";
		}
		return join(json, "artists", "author");
	}

	private static String join(JsonObject json, String... keys) {
		if (json == null) {
			return "";
		}
		for (String key : keys) {
			if (!json.has(key) || json.get(key).isJsonNull()) {
				continue;
			}
			JsonElement el = json.get(key);
			try {
				if (el.isJsonPrimitive()) {
					String value = el.getAsString().trim();
					if (!value.isBlank()) {
						return value;
					}
				} else if (el.isJsonArray()) {
					String value = artistsFrom(el.getAsJsonArray());
					if (!value.isBlank()) {
						return value;
					}
				} else if (el.isJsonObject()) {
					String value = text(el.getAsJsonObject(), "name", "text", "title");
					if (!value.isBlank()) {
						return value;
					}
				}
			} catch (Exception ignored) {
			}
		}
		return "";
	}

	private static String artistsFrom(JsonArray array) {
		StringBuilder out = new StringBuilder();
		for (JsonElement el : array) {
			String name = "";
			if (el.isJsonPrimitive()) {
				name = el.getAsString();
			} else if (el.isJsonObject()) {
				name = text(el.getAsJsonObject(), "name", "text", "title");
			}
			if (name == null || name.isBlank()) {
				continue;
			}
			if (out.length() > 0) {
				out.append(", ");
			}
			out.append(name.trim());
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

	private static String cover(JsonObject track, JsonObject root, JsonObject info, JsonObject video, String videoId) {
		return firstNonBlank(
			ytimg(videoId),
			join(track, "imageSrc", "thumbnailUrl", "thumbnail", "cover", "albumArt", "image", "albumCover", "coverUrl", "artworkUrl"),
			join(root, "imageSrc", "thumbnailUrl", "thumbnail", "cover", "albumArt", "image", "albumCover", "coverUrl", "artworkUrl"),
			join(info, "artworkUrl", "cover", "imageSrc", "thumbnailUrl"),
			join(video, "imageSrc", "thumbnailUrl", "cover"),
			artworkUrl(track),
			artworkUrl(root),
			artworkUrl(info),
			thumbnailUrl(track),
			thumbnailUrl(root),
			thumbnailUrl(video),
			ytimg(videoId)
		);
	}

	private static String thumbnailUrl(JsonObject json) {
		if (json == null || !json.has("thumbnails") || !json.get("thumbnails").isJsonArray()) {
			return "";
		}
		String best = "";
		int bestArea = -1;
		for (JsonElement el : json.getAsJsonArray("thumbnails")) {
			if (el.isJsonPrimitive()) {
				String url = el.getAsString().trim();
				if (url.startsWith("http") || url.startsWith("data:")) {
					return url;
				}
				continue;
			}
			if (!el.isJsonObject()) {
				continue;
			}
			JsonObject obj = el.getAsJsonObject();
			String url = text(obj, "url", "src", "uri");
			if (url.isBlank()) {
				continue;
			}
			int area = (int) (number(obj, "width") * number(obj, "height"));
			if (area >= bestArea) {
				bestArea = area;
				best = url;
			}
		}
		return best;
	}

	private static String ytimg(String videoId) {
		if (videoId == null || !videoId.matches("[A-Za-z0-9_-]{11}")) {
			return "";
		}
		return "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
	}

	private static String videoIdFromUrl(String url) {
		if (url == null || url.isBlank()) {
			return "";
		}
		int v = url.indexOf("v=");
		if (v >= 0 && v + 2 < url.length()) {
			String id = url.substring(v + 2);
			int amp = id.indexOf('&');
			if (amp >= 0) {
				id = id.substring(0, amp);
			}
			int hash = id.indexOf('#');
			if (hash >= 0) {
				id = id.substring(0, hash);
			}
			return id.trim();
		}
		int last = url.lastIndexOf('/');
		if (last >= 0 && last + 1 < url.length()) {
			String id = url.substring(last + 1);
			int q = id.indexOf('?');
			if (q >= 0) {
				id = id.substring(0, q);
			}
			return id.trim();
		}
		return "";
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
