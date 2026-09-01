package dev.voidmark.client.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Util;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fills missing artist/cover from iTunes, then Deezer, then MusicBrainz.
 * One in-flight search per track; quota errors are retried later instead of
 * being cached as a miss.
 */
final class TrackLookup {
	private static final String BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
	private static final String APP_UA = "Voidmark/1.1.49 (https://github.com/Noamm9/NoammAddons)";
	private static final String YTM_SONGS = "EgWKAQIIAWoKEAkQBRAKEAMQBA==";
	private static final long RETRY_MS = 45_000L;
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(6))
		.build();
	private static final ConcurrentHashMap<String, Hit> CACHE = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<String, Long> RETRY_AFTER = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<String, Boolean> PENDING = new ConcurrentHashMap<>();

	private TrackLookup() {
	}

	static NowPlaying enrich(NowPlaying track) {
		if (track == null || !track.present()) {
			return track == null ? NowPlaying.none() : track;
		}
		if (track.hasCover() && !NowPlaying.placeholder(track.artist()) && !NowPlaying.sameName(track.artist(), track.album())) {
			return track;
		}
		String key = key(track.title(), track.artist(), track.album());
		Hit hit = CACHE.get(key);
		if (hit == null) {
			request(key, track.title(), track.artist(), track.album());
			return track;
		}
		if (!hit.usable()) {
			return track;
		}
		return track.withCatalog(hit.title, hit.artist, hit.album, hit.cover);
	}

	static Hit peek(String title, String artist, String album) {
		Hit hit = CACHE.get(key(title, artist, album));
		return hit == null ? Hit.NONE : hit;
	}

	static void ensure(String title, String artist, String album) {
		request(key(title, artist, album), title, artist, album);
	}

	private static void request(String key, String title, String artist, String album) {
		if (CACHE.containsKey(key)) {
			return;
		}
		Long wait = RETRY_AFTER.get(key);
		if (wait != null && System.currentTimeMillis() < wait) {
			return;
		}
		if (PENDING.putIfAbsent(key, Boolean.TRUE) != null) {
			return;
		}
		Util.nonCriticalIoPool().execute(() -> {
			try {
				Hit hit = search(title, artist, album);
				if (hit.retry()) {
					RETRY_AFTER.put(key, System.currentTimeMillis() + RETRY_MS);
					return;
				}
				CACHE.putIfAbsent(key, hit);
				RETRY_AFTER.remove(key);
			} finally {
				PENDING.remove(key);
			}
		});
	}

	private static Hit search(String title, String artist, String album) {
		String rawPerson = safe(artist);
		final String person = NowPlaying.placeholder(rawPerson) || NowPlaying.sameName(rawPerson, album)
			? ""
			: rawPerson;
		boolean limited = false;
		Hit hit = trySource(() -> youtubeMusic(title, person, album));
		if (hit.usable()) {
			return hit;
		}
		limited |= hit.retry();
		hit = trySource(() -> itunes(title, person));
		if (hit.usable()) {
			return hit;
		}
		limited |= hit.retry();
		hit = trySource(() -> deezer(title, person));
		if (hit.usable()) {
			return hit;
		}
		limited |= hit.retry();
		hit = trySource(() -> musicbrainz(title, person));
		if (hit.usable()) {
			return hit;
		}
		limited |= hit.retry();
		return limited ? Hit.RETRY : Hit.NONE;
	}

	private static Hit trySource(Source source) {
		try {
			return source.lookup();
		} catch (LimitedException limited) {
			return Hit.RETRY;
		} catch (Exception exception) {
			return Hit.NONE;
		}
	}

	@FunctionalInterface
	private interface Source {
		Hit lookup();
	}

	private static Hit youtubeMusic(String title, String artist, String album) {
		String query = (safe(title) + " " + safe(album) + " " + safe(artist)).trim();
		if (query.length() < 2) {
			return Hit.NONE;
		}
		JsonObject client = new JsonObject();
		client.addProperty("clientName", "WEB_REMIX");
		client.addProperty("clientVersion", "1.20241209.01.00");
		client.addProperty("hl", "en");
		JsonObject context = new JsonObject();
		context.add("client", client);
		JsonObject payload = new JsonObject();
		payload.add("context", context);
		payload.addProperty("query", query);
		payload.addProperty("params", YTM_SONGS);
		String body = post("https://music.youtube.com/youtubei/v1/search?prettyPrint=false", payload.toString());
		JsonObject root = JsonParser.parseString(body).getAsJsonObject();
		List<JsonObject> items = new ArrayList<>();
		collectRenderers(root, items);
		Hit best = Hit.NONE;
		for (JsonObject item : items) {
			String rowTitle = flexText(item, 0);
			String meta = flexText(item, 1);
			String rowArtist = "";
			String rowAlbum = "";
			for (String part : meta.split("\\s+[•·|]\\s+")) {
				String piece = part.trim();
				if (piece.isEmpty() || piece.matches("\\d+:\\d{2}") || piece.toLowerCase(Locale.ROOT).contains("play")) {
					continue;
				}
				if (rowArtist.isEmpty()) {
					rowArtist = piece;
				} else if (rowAlbum.isEmpty()) {
					rowAlbum = piece;
				}
			}
			if (rowTitle.isBlank() || NowPlaying.placeholder(rowArtist)) {
				continue;
			}
			String videoId = findVideoId(item);
			String art = videoId.isEmpty() ? "" : "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
			int score = matchScore(rowTitle, rowArtist, title, artist);
			if (!safe(album).isBlank() && NowPlaying.titlesClose(rowAlbum, album)) {
				score += 4;
			}
			if (score > best.score && !art.isBlank()) {
				best = new Hit(rowTitle, rowArtist, rowAlbum, art, score);
			}
		}
		return best.usable() ? best : Hit.NONE;
	}

	private static void collectRenderers(JsonElement el, List<JsonObject> out) {
		if (el == null || el.isJsonNull()) {
			return;
		}
		if (el.isJsonObject()) {
			JsonObject object = el.getAsJsonObject();
			if (object.has("musicResponsiveListItemRenderer") && object.get("musicResponsiveListItemRenderer").isJsonObject()) {
				out.add(object.getAsJsonObject("musicResponsiveListItemRenderer"));
			}
			for (var entry : object.entrySet()) {
				collectRenderers(entry.getValue(), out);
			}
		} else if (el.isJsonArray()) {
			for (JsonElement child : el.getAsJsonArray()) {
				collectRenderers(child, out);
			}
		}
	}

	private static String flexText(JsonObject item, int index) {
		if (item == null || !item.has("flexColumns") || !item.get("flexColumns").isJsonArray()) {
			return "";
		}
		JsonArray cols = item.getAsJsonArray("flexColumns");
		if (index < 0 || index >= cols.size() || !cols.get(index).isJsonObject()) {
			return "";
		}
		JsonObject col = cols.get(index).getAsJsonObject();
		if (col.has("musicResponsiveListItemFlexColumnRenderer")) {
			col = col.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer");
		}
		return runs(col);
	}

	private static String runs(JsonObject object) {
		if (object == null) {
			return "";
		}
		JsonObject text = object.has("text") && object.get("text").isJsonObject() ? object.getAsJsonObject("text") : object;
		if (!text.has("runs") || !text.get("runs").isJsonArray()) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		for (JsonElement el : text.getAsJsonArray("runs")) {
			if (el.isJsonObject() && el.getAsJsonObject().has("text") && el.getAsJsonObject().get("text").isJsonPrimitive()) {
				out.append(el.getAsJsonObject().get("text").getAsString());
			}
		}
		return out.toString().trim();
	}

	private static String findVideoId(JsonElement el) {
		if (el == null || el.isJsonNull()) {
			return "";
		}
		if (el.isJsonObject()) {
			JsonObject object = el.getAsJsonObject();
			if (object.has("videoId") && object.get("videoId").isJsonPrimitive()) {
				String id = object.get("videoId").getAsString().trim();
				if (id.matches("[A-Za-z0-9_-]{11}")) {
					return id;
				}
			}
			for (var entry : object.entrySet()) {
				String found = findVideoId(entry.getValue());
				if (!found.isEmpty()) {
					return found;
				}
			}
		} else if (el.isJsonArray()) {
			for (JsonElement child : el.getAsJsonArray()) {
				String found = findVideoId(child);
				if (!found.isEmpty()) {
					return found;
				}
			}
		}
		return "";
	}

	private static Hit deezer(String title, String artist) {
		String query = query(title, artist);
		if (query.length() < 3) {
			return Hit.NONE;
		}
		String body = get("https://api.deezer.com/search?limit=8&q=" + encode(query));
		JsonObject root = JsonParser.parseString(body).getAsJsonObject();
		if (root.has("error") && root.get("error").isJsonObject()) {
			JsonObject error = root.getAsJsonObject("error");
			int code = error.has("code") && error.get("code").isJsonPrimitive() ? error.get("code").getAsInt() : 0;
			if (code == 4 || code == 700) {
				return Hit.RETRY;
			}
			return Hit.NONE;
		}
		if (!root.has("data") || !root.get("data").isJsonArray()) {
			return Hit.NONE;
		}
		return best(root.getAsJsonArray("data"), title, artist, true);
	}

	private static Hit itunes(String title, String artist) {
		String query = query(title, artist);
		if (query.length() < 3) {
			return Hit.NONE;
		}
		String body = get("https://itunes.apple.com/search?entity=song&media=music&limit=8&term=" + encode(query));
		JsonObject root = JsonParser.parseString(body).getAsJsonObject();
		if (!root.has("results") || !root.get("results").isJsonArray()) {
			return Hit.NONE;
		}
		return best(root.getAsJsonArray("results"), title, artist, false);
	}

	private static Hit musicbrainz(String title, String artist) {
		String track = safe(title);
		if (track.length() < 2) {
			return Hit.NONE;
		}
		String q = "recording:\"" + track + "\"";
		String person = safe(artist);
		if (!person.isBlank()) {
			q += " AND artist:\"" + person + "\"";
		}
		String body = get("https://musicbrainz.org/ws/2/recording/?limit=5&fmt=json&query=" + encode(q));
		JsonObject root = JsonParser.parseString(body).getAsJsonObject();
		if (!root.has("recordings") || !root.get("recordings").isJsonArray()) {
			return Hit.NONE;
		}
		Hit best = Hit.NONE;
		for (JsonElement el : root.getAsJsonArray("recordings")) {
			if (!el.isJsonObject()) {
				continue;
			}
			JsonObject row = el.getAsJsonObject();
			String rowTitle = text(row, "title");
			String rowArtist = creditName(row);
			String cover = releaseCover(row);
			if (cover.isBlank()) {
				continue;
			}
			int score = matchScore(rowTitle, rowArtist, title, artist);
			if (score > best.score) {
				best = new Hit(rowTitle, rowArtist, "", cover, score);
			}
		}
		return best.usable() ? best : Hit.NONE;
	}

	private static String query(String title, String artist) {
		return (safe(title) + " " + safe(artist)).trim();
	}

	private static Hit best(JsonArray results, String title, String artist, boolean deezer) {
		Hit best = Hit.NONE;
		for (JsonElement el : results) {
			if (!el.isJsonObject()) {
				continue;
			}
			JsonObject row = el.getAsJsonObject();
			String rowTitle;
			String rowArtist;
			String rowAlbum;
			String art;
			if (deezer) {
				rowTitle = text(row, "title", "title_short");
				rowArtist = nested(row, "artist", "name");
				rowAlbum = nested(row, "album", "title");
				art = firstNonBlank(
					nested(row, "album", "cover_medium"),
					nested(row, "album", "cover_big"),
					nested(row, "album", "cover")
				);
			} else {
				rowTitle = text(row, "trackName", "collectionName");
				rowArtist = text(row, "artistName");
				rowAlbum = text(row, "collectionName");
				art = text(row, "artworkUrl100", "artworkUrl60");
				if (!art.isBlank()) {
					art = art.replace("100x100bb", "300x300bb").replace("60x60bb", "300x300bb");
				}
			}
			int score = matchScore(rowTitle, rowArtist, title, artist);
			if (score > best.score) {
				best = new Hit(rowTitle, rowArtist, rowAlbum, art, score);
			}
		}
		return best.usable() ? best : Hit.NONE;
	}

	private static int matchScore(String rowTitle, String rowArtist, String title, String artist) {
		String rt = norm(rowTitle);
		String ra = norm(rowArtist);
		String t = norm(title);
		String a = norm(artist);
		if (rt.isEmpty()) {
			return 0;
		}
		int score = 0;
		if (!t.isEmpty() && (rt.equals(t) || rt.contains(t) || t.contains(rt))) {
			score += 4;
		}
		if (!a.isEmpty() && (ra.equals(a) || ra.contains(a) || a.contains(ra))) {
			score += 4;
		}
		if (!t.isEmpty() && (ra.equals(t) || ra.contains(t) || t.contains(ra))) {
			score += 3;
		}
		if (!a.isEmpty() && (rt.equals(a) || rt.contains(a) || a.contains(rt))) {
			score += 3;
		}
		if (a.isEmpty() && !t.isEmpty() && (rt.contains(t) || t.contains(rt) || ra.contains(t))) {
			score += 2;
		}
		return score;
	}

	private static String key(String title, String artist, String album) {
		return norm(title) + "|" + norm(album);
	}

	private static String nested(JsonObject row, String objectKey, String field) {
		if (row == null || !row.has(objectKey) || !row.get(objectKey).isJsonObject()) {
			return "";
		}
		return text(row.getAsJsonObject(objectKey), field);
	}

	private static String creditName(JsonObject recording) {
		if (recording == null || !recording.has("artist-credit") || !recording.get("artist-credit").isJsonArray()) {
			return "";
		}
		for (JsonElement el : recording.getAsJsonArray("artist-credit")) {
			if (!el.isJsonObject()) {
				continue;
			}
			JsonObject credit = el.getAsJsonObject();
			String name = text(credit, "name");
			if (name.isBlank()) {
				name = nested(credit, "artist", "name");
			}
			if (!name.isBlank()) {
				return name;
			}
		}
		return "";
	}

	private static String releaseCover(JsonObject recording) {
		if (recording == null || !recording.has("releases") || !recording.get("releases").isJsonArray()) {
			return "";
		}
		for (JsonElement el : recording.getAsJsonArray("releases")) {
			if (!el.isJsonObject()) {
				continue;
			}
			String id = text(el.getAsJsonObject(), "id");
			if (!id.isBlank()) {
				return "https://coverartarchive.org/release/" + id + "/front-250";
			}
		}
		return "";
	}

	private static String text(JsonObject json, String... keys) {
		if (json == null) {
			return "";
		}
		for (String key : keys) {
			if (!json.has(key) || json.get(key).isJsonNull() || !json.get(key).isJsonPrimitive()) {
				continue;
			}
			String value = json.get(key).getAsString().trim();
			if (!value.isBlank()) {
				return value;
			}
		}
		return "";
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return "";
	}

	private static String get(String url) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(8))
				.header("User-Agent", userAgent(url))
				.GET()
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			int code = response.statusCode();
			if (code == 429 || code == 503) {
				throw new LimitedException();
			}
			if (code < 200 || code >= 300) {
				throw new IllegalStateException("HTTP " + code);
			}
			return response.body();
		} catch (LimitedException limited) {
			throw limited;
		} catch (IllegalStateException illegal) {
			throw illegal;
		} catch (Exception exception) {
			throw new LimitedException();
		}
	}

	private static String post(String url, String json) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(10))
				.header("User-Agent", BROWSER_UA)
				.header("Content-Type", "application/json")
				.header("Origin", "https://music.youtube.com")
				.header("Referer", "https://music.youtube.com/")
				.POST(HttpRequest.BodyPublishers.ofString(json))
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			int code = response.statusCode();
			if (code == 429 || code == 503) {
				throw new LimitedException();
			}
			if (code < 200 || code >= 300) {
				throw new IllegalStateException("HTTP " + code);
			}
			return response.body();
		} catch (LimitedException limited) {
			throw limited;
		} catch (IllegalStateException illegal) {
			throw illegal;
		} catch (Exception exception) {
			throw new LimitedException();
		}
	}

	private static String userAgent(String url) {
		if (url.contains("musicbrainz.org") || url.contains("coverartarchive.org")) {
			return APP_UA;
		}
		return BROWSER_UA;
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static String norm(String value) {
		if (value == null) {
			return "";
		}
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
	}

	private static String safe(String value) {
		return value == null ? "" : value.trim();
	}

	record Hit(String title, String artist, String album, String cover, int score) {
		private static final Hit NONE = new Hit("", "", "", "", 0);
		private static final Hit RETRY = new Hit("", "", "", "", -1);

		boolean usable() {
			return score >= 3 && !NowPlaying.placeholder(artist);
		}

		boolean retry() {
			return score < 0;
		}
	}

	private static final class LimitedException extends RuntimeException {
	}
}
