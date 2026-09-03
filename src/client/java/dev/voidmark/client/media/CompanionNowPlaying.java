package dev.voidmark.client.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmark.client.config.VoidmarkConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class CompanionNowPlaying {
	private static final HttpClient CLIENT = HttpClient.newBuilder()
		.connectTimeout(Duration.ofMillis(250))
		.build();
	private static final String AUTH_ID = "voidmark";
	private static final long AUTH_COOLDOWN_MS = 45_000L;

	private String live;
	private String kind;
	private long quietUntil;
	private volatile boolean awaitingAuth;
	private volatile long lastAuthAskMs;
	private volatile boolean authInFlight;

	boolean awaitingAuth() {
		return awaitingAuth;
	}

	String statusHint() {
		if (awaitingAuth) {
			return "Allow Voidmark in YouTube Music";
		}
		return "YOUTUBE MUSIC";
	}

	NowPlaying snapshot() {
		long now = System.currentTimeMillis();
		if (live != null) {
			Fetch result = fetch(live);
			if (result.track().present()) {
				return result.track();
			}
			if (result.reached()) {
				return NowPlaying.none();
			}
			live = null;
			kind = null;
		}
		if (now < quietUntil) {
			return NowPlaying.none();
		}
		for (String endpoint : endpoints()) {
			Fetch result = fetch(endpoint);
			if (result.track().present()) {
				live = endpoint;
				kind = kindOf(endpoint);
				return result.track();
			}
			if (result.reached()) {
				live = endpoint;
				kind = kindOf(endpoint);
				return NowPlaying.none();
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

	private Fetch fetch(String endpoint) {
		try {
			HttpResponse<String> response = sendGet(endpoint, ytmSong(endpoint) ? 4_000L : 600L);
			int code = response.statusCode();
			if (code == 401 && ytmSong(endpoint)) {
				awaitingAuth = true;
				VoidmarkConfig config = VoidmarkConfig.get();
				if (config.musicApiToken != null && !config.musicApiToken.isBlank()) {
					config.musicApiToken = "";
					config.save();
				}
				askAuth(originOf(endpoint));
				return Fetch.idle();
			}
			if (code == 204) {
				awaitingAuth = false;
				return Fetch.idle();
			}
			if (code < 200 || code >= 300) {
				return Fetch.miss();
			}
			awaitingAuth = false;
			NowPlaying track = parse(response.body(), endpoint);
			if (!track.present()) {
				return Fetch.idle();
			}
			return Fetch.ok(track);
		} catch (Exception exception) {
			return Fetch.miss();
		}
	}

	private HttpResponse<String> sendGet(String endpoint, long timeoutMs) throws Exception {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
			.timeout(Duration.ofMillis(timeoutMs))
			.GET();
		authorize(builder);
		return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
	}

	private void authorize(HttpRequest.Builder builder) {
		String token = VoidmarkConfig.get().musicApiToken;
		if (token != null && !token.isBlank()) {
			builder.header("Authorization", "Bearer " + token.trim());
		}
	}

	private void askAuth(String origin) {
		if (origin == null || origin.isBlank() || authInFlight) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastAuthAskMs < AUTH_COOLDOWN_MS && (VoidmarkConfig.get().musicApiToken == null || VoidmarkConfig.get().musicApiToken.isBlank())) {
			return;
		}
		lastAuthAskMs = now;
		authInFlight = true;
		Thread thread = new Thread(() -> {
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(origin + "/auth/" + AUTH_ID))
					.timeout(Duration.ofSeconds(90))
					.POST(HttpRequest.BodyPublishers.noBody())
					.build();
				HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() < 200 || response.statusCode() >= 300) {
					return;
				}
				String token = accessToken(response.body());
				if (token.isBlank()) {
					return;
				}
				VoidmarkConfig config = VoidmarkConfig.get();
				config.musicApiToken = token;
				config.save();
			} catch (Exception ignored) {
			} finally {
				authInFlight = false;
			}
		}, "voidmark-ytm-auth");
		thread.setDaemon(true);
		thread.start();
	}

	private static String accessToken(String body) {
		if (body == null || body.isBlank()) {
			return "";
		}
		try {
			JsonElement root = JsonParser.parseString(body);
			if (!root.isJsonObject()) {
				return "";
			}
			JsonObject json = root.getAsJsonObject();
			return firstNonBlank(text(json, "accessToken"), text(json, "token"), text(json, "access_token"));
		} catch (Exception exception) {
			return "";
		}
	}

	private static List<String> endpoints() {
		LinkedHashSet<String> out = new LinkedHashSet<>();
		int custom = VoidmarkConfig.get().musicApiPort;
		if (custom > 0 && custom <= 65535) {
			addYtm(out, custom);
		}
		addYtm(out, 26538);
		addYtm(out, 26558);
		addYtm(out, 26559);
		out.add("http://127.0.0.1:9863/query");
		out.add("http://127.0.0.1:10767/api/v1/playback/now-playing");
		out.add("http://127.0.0.1:31333/api/v1/song");
		out.add("http://127.0.0.1:1870/api/v1/song");
		return List.copyOf(out);
	}

	private static void addYtm(Set<String> out, int port) {
		out.add("http://127.0.0.1:" + port + "/api/v1/song");
	}

	private static boolean ytmSong(String endpoint) {
		return endpoint != null && endpoint.contains("/api/v1/song") && !endpoint.contains(":10767");
	}

	private static String originOf(String endpoint) {
		try {
			URI uri = URI.create(endpoint);
			int port = uri.getPort();
			String host = uri.getHost();
			if (host == null || host.isBlank()) {
				return "";
			}
			if (port > 0) {
				return uri.getScheme() + "://" + host + ":" + port;
			}
			return uri.getScheme() + "://" + host;
		} catch (Exception exception) {
			return "";
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
			if (ytmSong(endpoint)) {
				return parseYtmSong(root, endpoint);
			}
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
				join(track, "artist", "artists", "artistsName", "artistName", "author"),
				join(root, "artist", "artists", "artistsName", "artistName", "author"),
				join(info, "artistName", "artist", "artists"),
				join(video, "artist", "artists", "author")
			);
			boolean paused = paused(player, root);
			String app = kindOf(endpoint).equals("ytmd") ? "YouTube Music Desktop" : "YouTube Music";
			String videoId = firstNonBlank(
				join(root, "videoId"),
				join(track, "videoId"),
				join(video, "id", "videoId"),
				videoIdFromUrl(join(root, "url", "videoUrl", "link"))
			);
			long position = positionMs(player, root);
			return NowPlaying.sampled(
				title,
				artist,
				firstNonBlank(join(track, "album"), join(root, "album"), join(info, "albumName", "album"), join(video, "album")),
				app,
				kindOf(endpoint),
				cover(track, root, info, video, videoId),
				!paused,
				position,
				durationMs(player, track, root)
			);
		} catch (Exception exception) {
			return NowPlaying.none();
		}
	}

	private static NowPlaying parseYtmSong(JsonObject root, String endpoint) {
		String title = text(root, "title");
		if (title.isBlank()) {
			return NowPlaying.none();
		}
		String artist = firstNonBlank(text(root, "artist"), join(root, "artists", "author"));
		String album = text(root, "album");
		String videoId = firstNonBlank(text(root, "videoId"), videoIdFromUrl(text(root, "url")));
		String imageSrc = preferJpeg(text(root, "imageSrc"));
		boolean paused = bool(root, "isPaused", false);
		long position = positionMs(new JsonObject(), root);
		long duration = durationMs(new JsonObject(), new JsonObject(), root);
		String art = firstNonBlank(
			imageSrc,
			preferJpeg(join(root, "thumbnailUrl", "thumbnail", "cover", "albumArt", "artworkUrl")),
			artworkUrl(root),
			thumbnailUrl(root),
			ytimg(videoId)
		);
		return NowPlaying.sampled(
			title,
			artist,
			album,
			"YouTube Music",
			kindOf(endpoint),
			art,
			!paused,
			position,
			duration
		);
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

	private boolean getOk(URI uri) {
		try {
			HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofMillis(800))
				.GET();
			authorize(builder);
			HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
			return response.statusCode() >= 200 && response.statusCode() < 300;
		} catch (Exception exception) {
			return false;
		}
	}

	private boolean post(String uri, String json) {
		try {
			HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri))
				.timeout(Duration.ofMillis(800))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json));
			authorize(builder);
			HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
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
		Long ms = optionalMillis(
			player,
			"seekbarCurrentPositionMilliSeconds",
			"elapsedMilliSeconds",
			"positionMs",
			"currentTimeMs",
			"progressMs",
			"playbackPositionMs"
		);
		if (ms == null) {
			ms = optionalMillis(
				root,
				"elapsedMilliSeconds",
				"positionMs",
				"currentTimeMs",
				"progressMs",
				"playbackPositionMs"
			);
		}
		if (ms == null) {
			ms = optionalSeconds(
				player,
				"seekbarCurrentPosition",
				"elapsedSeconds",
				"currentTime",
				"position",
				"progress",
				"playbackTime",
				"currentPlaybackTime"
			);
		}
		if (ms == null) {
			ms = optionalSeconds(
				root,
				"elapsedSeconds",
				"position",
				"currentTime",
				"seekbarCurrentPosition",
				"currentPlaybackTime",
				"progress",
				"playbackTime"
			);
		}
		return ms == null ? -1L : Math.max(0L, ms);
	}

	private static boolean paused(JsonObject player, JsonObject root) {
		if (has(root, "playing")) {
			return !bool(root, "playing", true);
		}
		if (has(player, "playing")) {
			return !bool(player, "playing", true);
		}
		if (has(root, "isPlaying")) {
			return !bool(root, "isPlaying", true);
		}
		if (has(player, "isPlaying")) {
			return !bool(player, "isPlaying", true);
		}
		if (bool(player, "isPaused", false)
			|| bool(root, "isPaused", false)
			|| bool(player, "paused", false)
			|| bool(root, "paused", false)) {
			return true;
		}
		String status = firstNonBlank(
			join(player, "status", "playbackStatus", "playerState"),
			join(root, "status", "playbackStatus", "playerState")
		).toLowerCase();
		if (status.contains("pause") || status.contains("stop")) {
			return true;
		}
		if (status.contains("play")) {
			return false;
		}
		return false;
	}

	private static boolean has(JsonObject json, String key) {
		return json != null && json.has(key) && !json.get(key).isJsonNull();
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

	private static Long optionalMillis(JsonObject json, String... keys) {
		Double raw = firstNumber(json, keys);
		if (raw == null) {
			return null;
		}
		return Math.round(Math.max(0d, raw));
	}

	private static Long optionalSeconds(JsonObject json, String... keys) {
		Double raw = firstNumber(json, keys);
		if (raw == null) {
			return null;
		}
		if (raw <= 0d) {
			return 0L;
		}
		if (raw >= 10_000d) {
			return Math.round(raw);
		}
		return Math.round(raw * 1000.0);
	}

	private static Double firstNumber(JsonObject json, String... keys) {
		if (json == null) {
			return null;
		}
		for (String key : keys) {
			if (json.has(key) && json.get(key).isJsonPrimitive()) {
				try {
					return json.get(key).getAsDouble();
				} catch (Exception ignored) {
				}
			}
		}
		return null;
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
		if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
			return fallback;
		}
		try {
			JsonElement el = json.get(key);
			if (!el.isJsonPrimitive()) {
				return fallback;
			}
			var primitive = el.getAsJsonPrimitive();
			if (primitive.isBoolean()) {
				return primitive.getAsBoolean();
			}
			if (primitive.isNumber()) {
				return primitive.getAsDouble() != 0d;
			}
			String raw = primitive.getAsString().trim().toLowerCase();
			if (raw.equals("true") || raw.equals("playing") || raw.equals("1") || raw.equals("yes")) {
				return true;
			}
			if (raw.equals("false") || raw.equals("paused") || raw.equals("stopped") || raw.equals("0") || raw.equals("no")) {
				return false;
			}
			return fallback;
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
			preferJpeg(join(track, "imageSrc", "thumbnailUrl", "thumbnail", "cover", "albumArt", "image", "albumCover", "coverUrl", "artworkUrl")),
			preferJpeg(join(root, "imageSrc", "thumbnailUrl", "thumbnail", "cover", "albumArt", "image", "albumCover", "coverUrl", "artworkUrl")),
			preferJpeg(join(info, "artworkUrl", "cover", "imageSrc", "thumbnailUrl")),
			preferJpeg(join(video, "imageSrc", "thumbnailUrl", "cover")),
			preferJpeg(artworkUrl(track)),
			preferJpeg(artworkUrl(root)),
			preferJpeg(artworkUrl(info)),
			preferJpeg(thumbnailUrl(track)),
			preferJpeg(thumbnailUrl(root)),
			preferJpeg(thumbnailUrl(video)),
			ytimg(videoId)
		);
	}

	private static String preferJpeg(String url) {
		if (url == null || url.isBlank()) {
			return "";
		}
		String value = url.trim();
		if (value.contains("googleusercontent") || value.contains("ggpht")) {
			value = value.replace("-rw", "-rj").replace(".webp", ".jpg");
			value = value.replaceAll("=w\\d+-h\\d+", "=w300-h300");
		}
		return value;
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

	private record Fetch(NowPlaying track, boolean reached) {
		static Fetch miss() {
			return new Fetch(NowPlaying.none(), false);
		}

		static Fetch idle() {
			return new Fetch(NowPlaying.none(), true);
		}

		static Fetch ok(NowPlaying track) {
			return new Fetch(track, true);
		}
	}
}
