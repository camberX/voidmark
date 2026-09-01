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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fills missing artist/cover from Deezer and iTunes only when the match is
 * unambiguous. Title-only hits like two different "Xanny" songs are ignored.
 */
final class TrackLookup {
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(6))
		.build();
	private static final ConcurrentHashMap<String, Hit> CACHE = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<String, Boolean> PENDING = new ConcurrentHashMap<>();

	private TrackLookup() {
	}

	static NowPlaying enrich(NowPlaying track) {
		if (track == null || !track.present()) {
			return track == null ? NowPlaying.none() : track;
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

	static Hit resolve(String title, String artist, String album) {
		String key = key(title, artist, album);
		Hit cached = CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		Hit hit = search(title, artist, album);
		CACHE.put(key, hit);
		PENDING.remove(key);
		return hit;
	}

	private static void request(String key, String title, String artist, String album) {
		if (PENDING.putIfAbsent(key, Boolean.TRUE) != null) {
			return;
		}
		Util.nonCriticalIoPool().execute(() -> {
			try {
				CACHE.putIfAbsent(key, search(title, artist, album));
			} finally {
				PENDING.remove(key);
			}
		});
	}

	private static Hit search(String title, String artist, String album) {
		Hit hit = deezer(title, artist, album);
		if (!hit.usable() && !safe(artist).isBlank()) {
			hit = deezer(artist, title, album);
		}
		if (!hit.usable()) {
			hit = itunes(title, artist, album);
		}
		if (!hit.usable() && !safe(artist).isBlank()) {
			hit = itunes(artist, title, album);
		}
		return hit;
	}

	private static Hit deezer(String title, String artist, String album) {
		String query = query(title, artist, album);
		if (query.length() < 3) {
			return Hit.NONE;
		}
		try {
			String body = get("https://api.deezer.com/search?limit=10&q=" + encode(query));
			JsonObject root = JsonParser.parseString(body).getAsJsonObject();
			if (!root.has("data") || !root.get("data").isJsonArray()) {
				return Hit.NONE;
			}
			return pick(root.getAsJsonArray("data"), title, artist, album, true);
		} catch (Exception exception) {
			return Hit.NONE;
		}
	}

	private static Hit itunes(String title, String artist, String album) {
		String query = query(title, artist, album);
		if (query.length() < 3) {
			return Hit.NONE;
		}
		Hit best = Hit.NONE;
		for (String country : new String[]{"de", "us", "gb", "at", "ch"}) {
			try {
				String body = get("https://itunes.apple.com/search?entity=song&limit=10&country=" + country + "&term=" + encode(query));
				JsonObject root = JsonParser.parseString(body).getAsJsonObject();
				if (!root.has("results") || !root.get("results").isJsonArray()) {
					continue;
				}
				Hit hit = pick(root.getAsJsonArray("results"), title, artist, album, false);
				if (hit.score > best.score) {
					best = hit;
				}
				if (best.score >= 11) {
					return best;
				}
			} catch (Exception ignored) {
			}
		}
		return best;
	}

	private static String query(String title, String artist, String album) {
		StringBuilder out = new StringBuilder(safe(title));
		if (!safe(artist).isBlank()) {
			out.append(' ').append(artist.trim());
		} else if (!safe(album).isBlank()) {
			out.append(' ').append(album.trim());
		}
		return out.toString().trim();
	}

	private static Hit pick(JsonArray results, String title, String artist, String album, boolean deezer) {
		List<Hit> titleHits = new ArrayList<>();
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
			if (!tightTitle(rowTitle, title)) {
				continue;
			}
			int score = 4;
			if (samePerson(rowArtist, artist)) {
				score += 4;
			}
			if (tightTitle(rowAlbum, album) || samePerson(rowAlbum, album)) {
				score += 3;
			}
			titleHits.add(new Hit(rowTitle, rowArtist, rowAlbum, art, score));
		}
		if (titleHits.isEmpty()) {
			return Hit.NONE;
		}
		if (!NowPlaying.placeholder(artist)) {
			Hit best = Hit.NONE;
			for (Hit hit : titleHits) {
				if (samePerson(hit.artist, artist) && hit.score > best.score) {
					best = hit;
				}
			}
			return best.score >= 8 ? best : Hit.NONE;
		}
		Set<String> artists = new LinkedHashSet<>();
		for (Hit hit : titleHits) {
			String name = norm(hit.artist);
			if (!name.isEmpty() && !NowPlaying.placeholder(hit.artist)) {
				artists.add(name);
			}
		}
		if (artists.size() != 1) {
			return Hit.NONE;
		}
		String only = artists.iterator().next();
		Hit best = Hit.NONE;
		for (Hit hit : titleHits) {
			if (norm(hit.artist).equals(only) && hit.score > best.score) {
				best = hit;
			}
		}
		return best;
	}

	private static boolean tightTitle(String row, String query) {
		String r = norm(row);
		String q = norm(query);
		if (r.isEmpty() || q.isEmpty()) {
			return false;
		}
		if (r.equals(q)) {
			return true;
		}
		return r.startsWith(q + " ") || q.startsWith(r + " ");
	}

	private static boolean samePerson(String left, String right) {
		String a = norm(left);
		String b = norm(right);
		if (a.isEmpty() || b.isEmpty()) {
			return false;
		}
		return a.equals(b) || a.contains(b) || b.contains(a);
	}

	private static String key(String title, String artist, String album) {
		return norm(title) + "|" + norm(artist) + "|" + norm(album);
	}

	private static String nested(JsonObject row, String objectKey, String field) {
		if (row == null || !row.has(objectKey) || !row.get(objectKey).isJsonObject()) {
			return "";
		}
		return text(row.getAsJsonObject(objectKey), field);
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

	private static String get(String url) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(8))
			.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
			.GET()
			.build();
		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IllegalStateException("HTTP " + response.statusCode());
		}
		return response.body();
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

		boolean usable() {
			return score >= 4 && !NowPlaying.placeholder(artist);
		}
	}
}
