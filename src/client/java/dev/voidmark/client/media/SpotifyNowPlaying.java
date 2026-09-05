package dev.voidmark.client.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Spotify Web API now-playing. PKCE login, then
 * {@code /v1/me/player/currently-playing} plus play/pause/skip.
 */
public final class SpotifyNowPlaying {
	private static final String AUTHORIZE = "https://accounts.spotify.com/authorize";
	private static final String TOKEN = "https://accounts.spotify.com/api/token";
	private static final String PLAYER = "https://api.spotify.com/v1/me/player";
	private static final String CURRENT = PLAYER + "/currently-playing";
	private static final String REDIRECT = "http://127.0.0.1:43821/callback";
	private static final String[] CLIENT_ID_URLS = {
		"https://raw.githubusercontent.com/camberX/voidmark/main/web/public/spotify.json",
		"https://cdn.jsdelivr.net/gh/camberX/voidmark@main/web/public/spotify.json",
		"https://cdn.statically.io/gh/camberX/voidmark/main/web/public/spotify.json",
		"https://raw.githubusercontent.com/camberX/voidmark/main/web/public/mod/latest.json",
		"https://voidmark.cloud/api/spotify"
	};
	private static final String BUILT_IN_CLIENT_ID = readBuiltInClientId();
	private static final int REDIRECT_PORT = 43821;
	private static final String SCOPES = "user-read-currently-playing user-read-playback-state user-modify-playback-state";
	private static final long POLL_MS = 900L;
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(8))
		.build();

	private static final AtomicBoolean connecting = new AtomicBoolean(false);
	private static volatile String shopClientId = "";
	private static volatile String lastError = "";
	private static volatile NowPlaying cached = NowPlaying.none();
	private static volatile long lastPollMs;
	private static HttpServer loginServer;
	private static String codeVerifier = "";
	private static String loginState = "";

	private SpotifyNowPlaying() {
	}

	public static boolean connected() {
		String refresh = VoidmarkConfig.get().spotifyRefreshToken;
		return refresh != null && !refresh.isBlank();
	}

	public static boolean connecting() {
		return connecting.get();
	}

	public static String status() {
		if (connecting.get()) {
			return "Waiting…";
		}
		if (connected()) {
			return "Connected";
		}
		if (lastError != null && !lastError.isBlank()) {
			return lastError;
		}
		return "Connect";
	}

	public static void prefetch() {
		Thread thread = new Thread(SpotifyNowPlaying::clientId, "voidmark-spotify-id");
		thread.setDaemon(true);
		thread.start();
	}

	public static void toggleLogin() {
		if (connected()) {
			disconnect();
			return;
		}
		connect();
	}

	public static void disconnect() {
		stopLogin();
		VoidmarkConfig config = VoidmarkConfig.get();
		config.spotifyRefreshToken = "";
		config.spotifyAccessToken = "";
		config.spotifyAccessExpiresAt = 0L;
		config.save();
		cached = NowPlaying.none();
		lastError = "";
	}

	public static void connect() {
		adoptClipboardClientId();
		if (connecting.getAndSet(true)) {
			return;
		}
		Thread thread = new Thread(() -> {
			try {
				startLogin();
			} catch (Exception exception) {
				lastError = "Login failed";
				connecting.set(false);
				stopLogin();
			}
		}, "voidmark-spotify-login");
		thread.setDaemon(true);
		thread.start();
	}

	static NowPlaying snapshot() {
		if (!connected()) {
			return NowPlaying.none();
		}
		long now = System.currentTimeMillis();
		if (now - lastPollMs < POLL_MS) {
			return cached;
		}
		lastPollMs = now;
		NowPlaying next = fetchCurrent();
		cached = next;
		return next;
	}

	static boolean control(String action) {
		if (!connected()) {
			return false;
		}
		String token = accessToken();
		if (token.isBlank()) {
			return false;
		}
		try {
			HttpRequest.Builder builder = HttpRequest.newBuilder()
				.timeout(Duration.ofSeconds(6))
				.header("Authorization", "Bearer " + token);
			switch (action == null ? "" : action) {
				case "next" -> builder.uri(URI.create(PLAYER + "/next")).POST(HttpRequest.BodyPublishers.noBody());
				case "prev" -> builder.uri(URI.create(PLAYER + "/previous")).POST(HttpRequest.BodyPublishers.noBody());
				case "toggle" -> {
					NowPlaying track = snapshot();
					if (track.playing()) {
						builder.uri(URI.create(PLAYER + "/pause")).PUT(HttpRequest.BodyPublishers.noBody());
					} else {
						builder.uri(URI.create(PLAYER + "/play")).PUT(HttpRequest.BodyPublishers.noBody());
					}
				}
				default -> {
					return false;
				}
			}
			HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
			int code = response.statusCode();
			if (code == 401 && refreshAccess()) {
				return control(action);
			}
			if (code == 204 || (code >= 200 && code < 300)) {
				lastPollMs = 0L;
				return true;
			}
			return false;
		} catch (Exception ignored) {
			return false;
		}
	}

	private static void startLogin() throws Exception {
		String clientId = clientId();
		if (clientId.isBlank()) {
			lastError = "Need Client ID";
			connecting.set(false);
			return;
		}
		codeVerifier = randomUrl(64);
		loginState = randomUrl(16);
		String challenge = challenge(codeVerifier);
		stopLogin();
		loginServer = HttpServer.create(new InetSocketAddress("127.0.0.1", REDIRECT_PORT), 0);
		loginServer.createContext("/callback", exchange -> {
			try {
				URI uri = exchange.getRequestURI();
				String query = uri.getRawQuery() == null ? "" : uri.getRawQuery();
				String code = param(query, "code");
				String state = param(query, "state");
				String error = param(query, "error");
				String html;
				if (!error.isBlank()) {
					lastError = "Denied";
					html = page("Spotify login was cancelled.");
				} else if (code.isBlank() || !loginState.equals(state)) {
					lastError = "Bad callback";
					html = page("Spotify sent a bad login response.");
				} else if (exchangeCode(clientId, code)) {
					lastError = "";
					html = page("Spotify is connected. You can close this tab.");
				} else {
					lastError = "Token failed";
					html = page("Could not finish Spotify login.");
				}
				byte[] body = html.getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
			} catch (Exception ignored) {
				lastError = "Login failed";
			} finally {
				try {
					exchange.close();
				} catch (Exception ignored) {
				}
				connecting.set(false);
				stopLogin();
			}
		});
		loginServer.start();
		String url = AUTHORIZE
			+ "?client_id=" + enc(clientId)
			+ "&response_type=code"
			+ "&redirect_uri=" + enc(REDIRECT)
			+ "&scope=" + enc(SCOPES)
			+ "&code_challenge_method=S256"
			+ "&code_challenge=" + enc(challenge)
			+ "&state=" + enc(loginState);
		try {
			Util.getPlatform().openUri(URI.create(url));
		} catch (Exception ignored) {
			throw new IllegalStateException("browser");
		}
		Thread stop = new Thread(() -> {
			try {
				Thread.sleep(120_000L);
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
			}
			if (connecting.get()) {
				lastError = "Timed out";
				connecting.set(false);
				stopLogin();
			}
		}, "voidmark-spotify-timeout");
		stop.setDaemon(true);
		stop.start();
	}

	private static boolean exchangeCode(String clientId, String code) {
		String body = "grant_type=authorization_code"
			+ "&code=" + enc(code)
			+ "&redirect_uri=" + enc(REDIRECT)
			+ "&client_id=" + enc(clientId)
			+ "&code_verifier=" + enc(codeVerifier);
		return applyTokenResponse(postForm(TOKEN, body));
	}

	private static boolean refreshAccess() {
		VoidmarkConfig config = VoidmarkConfig.get();
		if (config.spotifyRefreshToken == null || config.spotifyRefreshToken.isBlank()) {
			return false;
		}
		String clientId = clientId();
		if (clientId.isBlank()) {
			return false;
		}
		String body = "grant_type=refresh_token"
			+ "&refresh_token=" + enc(config.spotifyRefreshToken)
			+ "&client_id=" + enc(clientId);
		JsonObject json = postForm(TOKEN, body);
		if (json == null) {
			return false;
		}
		if (json.has("error")) {
			disconnect();
			lastError = "Reconnect Spotify";
			return false;
		}
		return applyTokenResponse(json);
	}

	private static boolean applyTokenResponse(JsonObject json) {
		if (json == null) {
			return false;
		}
		String access = text(json, "access_token");
		if (access.isBlank()) {
			return false;
		}
		VoidmarkConfig config = VoidmarkConfig.get();
		config.spotifyAccessToken = access;
		String refresh = text(json, "refresh_token");
		if (!refresh.isBlank()) {
			config.spotifyRefreshToken = refresh;
		}
		long expires = Math.round(number(json, "expires_in"));
		config.spotifyAccessExpiresAt = System.currentTimeMillis() + Math.max(30L, expires - 30L) * 1000L;
		config.save();
		return true;
	}

	private static String accessToken() {
		VoidmarkConfig config = VoidmarkConfig.get();
		if (config.spotifyAccessToken != null
			&& !config.spotifyAccessToken.isBlank()
			&& config.spotifyAccessExpiresAt > System.currentTimeMillis() + 5_000L) {
			return config.spotifyAccessToken;
		}
		if (!refreshAccess()) {
			return "";
		}
		return VoidmarkConfig.get().spotifyAccessToken == null ? "" : VoidmarkConfig.get().spotifyAccessToken;
	}

	private static NowPlaying fetchCurrent() {
		String token = accessToken();
		if (token.isBlank()) {
			return NowPlaying.none();
		}
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(CURRENT + "?additional_types=track,episode"))
				.timeout(Duration.ofSeconds(6))
				.header("Authorization", "Bearer " + token)
				.header("Accept", "application/json")
				.GET()
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			int code = response.statusCode();
			if (code == 204) {
				return NowPlaying.none();
			}
			if (code == 401 && refreshAccess()) {
				return fetchCurrent();
			}
			if (code < 200 || code >= 300) {
				return NowPlaying.none();
			}
			return parseCurrent(response.body());
		} catch (Exception ignored) {
			return NowPlaying.none();
		}
	}

	private static NowPlaying parseCurrent(String body) {
		if (body == null || body.isBlank()) {
			return NowPlaying.none();
		}
		try {
			JsonObject root = JsonParser.parseString(body).getAsJsonObject();
			JsonObject item = object(root, "item");
			if (item.size() == 0) {
				return NowPlaying.none();
			}
			String title = text(item, "name");
			if (title.isBlank()) {
				return NowPlaying.none();
			}
			String artist = artists(item);
			if (artist.isBlank()) {
				artist = text(object(item, "show"), "name");
			}
			JsonObject album = object(item, "album");
			return NowPlaying.sampled(
				title,
				artist,
				text(album, "name"),
				"Spotify",
				"spotify",
				cover(album, item),
				bool(root, "is_playing", true),
				Math.round(number(root, "progress_ms")),
				Math.round(number(item, "duration_ms"))
			);
		} catch (Exception ignored) {
			return NowPlaying.none();
		}
	}

	private static void adoptClipboardClientId() {
		if (!sanitizeClientId(VoidmarkConfig.get().spotifyClientId).isBlank()) {
			return;
		}
		try {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft == null || minecraft.keyboardHandler == null) {
				return;
			}
			rememberClientId(minecraft.keyboardHandler.getClipboard());
		} catch (Exception ignored) {
		}
	}

	private static synchronized String clientId() {
		String saved = sanitizeClientId(VoidmarkConfig.get().spotifyClientId);
		if (!saved.isBlank()) {
			return saved;
		}
		String builtIn = sanitizeClientId(BUILT_IN_CLIENT_ID);
		if (!builtIn.isBlank()) {
			return rememberClientId(builtIn);
		}
		if (shopClientId != null && !shopClientId.isBlank()) {
			return shopClientId;
		}
		for (String url : CLIENT_ID_URLS) {
			String remote = fetchClientId(url);
			if (!remote.isBlank()) {
				return rememberClientId(remote);
			}
		}
		return "";
	}

	private static String fetchClientId(String url) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(4))
				.header("Accept", "application/json")
				.header("User-Agent", "Voidmark")
				.GET()
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null) {
				return "";
			}
			JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
			return sanitizeClientId(text(json, "clientId", "client_id", "spotifyClientId"));
		} catch (Exception ignored) {
			return "";
		}
	}

	private static String rememberClientId(String raw) {
		String id = sanitizeClientId(raw);
		if (id.isBlank()) {
			return "";
		}
		shopClientId = id;
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!id.equals(sanitizeClientId(config.spotifyClientId))) {
			config.spotifyClientId = id;
			config.save();
		}
		return id;
	}

	private static String sanitizeClientId(String raw) {
		if (raw == null) {
			return "";
		}
		String id = raw.trim();
		int slash = Math.max(id.lastIndexOf('/'), id.lastIndexOf('='));
		if (slash >= 0 && slash + 1 < id.length()) {
			id = id.substring(slash + 1).trim();
		}
		return id.matches("[0-9A-Za-z]{32}") ? id : "";
	}

	private static String readBuiltInClientId() {
		try (InputStream in = SpotifyNowPlaying.class.getResourceAsStream("/spotify.json")) {
			if (in == null) {
				return "";
			}
			JsonObject json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
			return sanitizeClientId(text(json, "clientId", "client_id", "spotifyClientId"));
		} catch (Exception ignored) {
			return "";
		}
	}

	private static JsonObject postForm(String url, String body) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.header("Accept", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.body() == null || response.body().isBlank()) {
				return null;
			}
			JsonElement el = JsonParser.parseString(response.body());
			return el.isJsonObject() ? el.getAsJsonObject() : null;
		} catch (Exception ignored) {
			return null;
		}
	}

	private static synchronized void stopLogin() {
		if (loginServer == null) {
			return;
		}
		try {
			loginServer.stop(0);
		} catch (Exception ignored) {
		}
		loginServer = null;
	}

	private static String page(String message) {
		return "<!doctype html><html><body style=\"font-family:sans-serif;background:#0b0e14;color:#f4f7ff;padding:40px\">"
			+ "<p>" + message + "</p></body></html>";
	}

	private static String param(String query, String key) {
		if (query == null || query.isBlank()) {
			return "";
		}
		for (String part : query.split("&")) {
			int at = part.indexOf('=');
			if (at <= 0) {
				continue;
			}
			if (!key.equals(decode(part.substring(0, at)))) {
				continue;
			}
			return decode(part.substring(at + 1));
		}
		return "";
	}

	private static String decode(String value) {
		return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
	}

	private static String enc(String value) {
		return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
	}

	private static String randomUrl(int bytes) {
		byte[] raw = new byte[bytes];
		new SecureRandom().nextBytes(raw);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
	}

	private static String challenge(String verifier) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
		} catch (Exception exception) {
			return "";
		}
	}

	private static JsonObject object(JsonObject root, String key) {
		if (root != null && root.has(key) && root.get(key).isJsonObject()) {
			return root.getAsJsonObject(key);
		}
		return new JsonObject();
	}

	private static String artists(JsonObject item) {
		if (item == null || !item.has("artists") || !item.get("artists").isJsonArray()) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		JsonArray array = item.getAsJsonArray("artists");
		for (JsonElement el : array) {
			if (!el.isJsonObject()) {
				continue;
			}
			String name = text(el.getAsJsonObject(), "name");
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

	private static String cover(JsonObject album, JsonObject item) {
		String fromAlbum = image(album);
		if (!fromAlbum.isBlank()) {
			return fromAlbum;
		}
		return image(item);
	}

	private static String image(JsonObject json) {
		if (json == null || !json.has("images") || !json.get("images").isJsonArray()) {
			return "";
		}
		String best = "";
		int bestArea = -1;
		for (JsonElement el : json.getAsJsonArray("images")) {
			if (!el.isJsonObject()) {
				continue;
			}
			JsonObject image = el.getAsJsonObject();
			String url = text(image, "url");
			if (url.isBlank()) {
				continue;
			}
			int area = (int) (number(image, "width") * number(image, "height"));
			if (area >= bestArea) {
				bestArea = area;
				best = url;
			}
		}
		return best;
	}

	private static boolean bool(JsonObject json, String key, boolean fallback) {
		if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
			return fallback;
		}
		try {
			return json.get(key).getAsBoolean();
		} catch (Exception ignored) {
			return fallback;
		}
	}

	private static String text(JsonObject json, String... keys) {
		if (json == null) {
			return "";
		}
		for (String key : keys) {
			if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
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

	private static double number(JsonObject json, String key) {
		if (json == null || !json.has(key) || !json.get(key).isJsonPrimitive()) {
			return 0d;
		}
		try {
			return json.get(key).getAsDouble();
		} catch (Exception ignored) {
			return 0d;
		}
	}
}
