package dev.voidmark.client.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmark.Voidmark;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Windows SMTC Spotify helper, same clock and process model as ForageKit.
 */
public final class SpotifySmtc {
	private static final SpotifySmtc INSTANCE = new SpotifySmtc();
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "voidmark-spotify");
		thread.setDaemon(true);
		return thread;
	});
	private static final ExecutorService COVER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "voidmark-spotify-art");
		thread.setDaemon(true);
		return thread;
	});
	private static final Pattern FEATURED = Pattern.compile(
		"(?i)(?:\\(|\\[|-\\s*)(?:feat\\.?|ft\\.?|featuring|with)\\s+([^\\)\\]]+)"
	);
	private static final Pattern FEATURED_TAIL = Pattern.compile("(?i)\\b(?:feat\\.?|ft\\.?|featuring)\\s+(.+)$");
	private static final Pattern CREDIT_SPLIT = Pattern.compile("\\s*(?:,|;|&|/|\\+|\\band\\b|\\bx\\b)\\s*", Pattern.CASE_INSENSITIVE);
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(5))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();
	private static final String HTTP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

	private final AtomicReference<Track> track = new AtomicReference<>(Track.NONE);
	private volatile Process process;
	private volatile boolean started;
	private volatile boolean available = true;
	private long nextStartAt;
	private volatile String lastTrackKey = "";
	private long lastSmtcPos = -1L;
	private String lastArtQuery = "";
	private long lastArtAttempt;

	public record Track(
		boolean active,
		boolean playing,
		String title,
		String artist,
		String album,
		long positionMs,
		long durationMs,
		long sampledAt,
		String coverUrl
	) {
		public static final Track NONE = new Track(false, false, "", "", "", 0, 0, 0, "");

		public long displayPositionMs() {
			if (!this.playing || this.durationMs <= 0) {
				return this.positionMs;
			}
			long elapsed = System.currentTimeMillis() - this.sampledAt;
			return Math.min(this.durationMs, this.positionMs + Math.max(0, elapsed));
		}

		public float progress() {
			if (this.durationMs <= 0) {
				return 0f;
			}
			return Math.max(0f, Math.min(1f, this.displayPositionMs() / (float) this.durationMs));
		}
	}

	private SpotifySmtc() {
	}

	public static void tick(boolean enabled) {
		INSTANCE.tickEnabled(enabled);
	}

	public static NowPlaying current() {
		return INSTANCE.asNowPlaying();
	}

	private void tickEnabled(boolean enabled) {
		if (!enabled || !this.available) {
			if (!enabled) {
				this.stop();
			}
			return;
		}
		if (this.started) {
			if (this.process != null && !this.process.isAlive()) {
				this.started = false;
				this.nextStartAt = System.currentTimeMillis() + 2500L;
			}
			return;
		}
		if (System.currentTimeMillis() < this.nextStartAt) {
			return;
		}
		this.started = true;
		WORKER.execute(this::startProcess);
	}

	private void stop() {
		this.started = false;
		this.lastTrackKey = "";
		this.lastSmtcPos = -1L;
		this.lastArtQuery = "";
		Process running = this.process;
		this.process = null;
		if (running != null && running.isAlive()) {
			running.destroy();
		}
		this.track.set(Track.NONE);
	}

	private NowPlaying asNowPlaying() {
		Track value = this.track.get();
		if (value == null || !value.active() || value.title() == null || value.title().isBlank()) {
			return NowPlaying.none();
		}
		return new NowPlaying(
			value.title(),
			value.artist() == null ? "" : value.artist(),
			value.album() == null ? "" : value.album(),
			"Spotify",
			"spotify",
			value.coverUrl() == null ? "" : value.coverUrl(),
			value.playing(),
			value.positionMs(),
			value.durationMs(),
			value.sampledAt(),
			value.positionMs()
		);
	}

	private void startProcess() {
		if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
			this.track.set(Track.NONE);
			this.available = false;
			this.started = false;
			return;
		}
		try {
			Path script = this.writeScript();
			ProcessBuilder builder = new ProcessBuilder(
				powershell(),
				"-NoLogo",
				"-STA",
				"-NoProfile",
				"-ExecutionPolicy", "Bypass",
				"-File",
				script.toAbsolutePath().toString()
			);
			builder.environment().put("POWERSHELL_TELEMETRY_OPTOUT", "1");
			Process started = builder.start();
			this.process = started;
			try {
				OutputStream stdin = started.getOutputStream();
				if (stdin != null) {
					stdin.close();
				}
			} catch (Exception ignored) {
			}
			Thread errors = new Thread(() -> drain(started.getErrorStream()), "voidmark-spotify-err");
			errors.setDaemon(true);
			errors.start();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					this.consume(sanitize(line));
				}
			}
		} catch (Exception exception) {
			Voidmark.LOGGER.debug("Spotify helper stopped", exception);
			this.track.set(Track.NONE);
			this.nextStartAt = System.currentTimeMillis() + 2500L;
		} finally {
			this.process = null;
			this.started = false;
		}
	}

	private Path writeScript() throws Exception {
		Path script = Path.of(System.getProperty("java.io.tmpdir"), "voidmark-nowplaying.ps1");
		try (InputStream in = SpotifySmtc.class.getResourceAsStream("/windows_nowplaying.ps1")) {
			if (in == null) {
				throw new IllegalStateException("Spotify helper script is missing from the mod jar");
			}
			Files.write(script, in.readAllBytes());
		}
		return script;
	}

	private static void drain(InputStream stream) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (!line.isBlank()) {
					Voidmark.LOGGER.debug("Spotify helper: {}", line.trim());
				}
			}
		} catch (Exception ignored) {
		}
	}

	private static String powershell() {
		String root = System.getenv("SystemRoot");
		if (root == null || root.isBlank()) {
			root = "C:\\Windows";
		}
		Path exe = Path.of(root, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
		return Files.isRegularFile(exe) ? exe.toString() : "powershell.exe";
	}

	private static String sanitize(String line) {
		return line.replace("\uFEFF", "").replace("\0", "").trim();
	}

	private void consume(String line) {
		if (line.isEmpty() || !line.startsWith("FK")) {
			return;
		}
		if (line.equals("FK")) {
			this.clearTrack();
			return;
		}
		String[] parts = line.split("\t", -1);
		if (parts.length < 5) {
			return;
		}
		this.apply(
			"Playing".equalsIgnoreCase(parts[1]),
			parts[2],
			parts[3],
			parts.length > 7 ? parts[7] : "",
			parts[4],
			parts.length > 5 ? parseLong(parts[5]) : 0L,
			parts.length > 6 ? parseLong(parts[6]) : 0L,
			parts.length > 8 ? parts[8] : ""
		);
	}

	private void clearTrack() {
		this.track.set(Track.NONE);
		this.lastTrackKey = "";
		this.lastSmtcPos = -1L;
		this.lastArtQuery = "";
	}

	private void apply(boolean playing, String title, String smtcArtist, String albumArtist, String album, long incomingPos, long duration, String smtcCover) {
		String trackKey = title + "|" + smtcArtist + "|" + album;
		Track previous = this.track.get();
		long now = System.currentTimeMillis();
		boolean sameTrack = previous.active() && trackKey.equals(this.lastTrackKey);
		String cover = sameTrack ? previous.coverUrl() : "";
		if (cover.isBlank() && smtcCover != null && !smtcCover.isBlank()) {
			cover = smtcCover.trim();
		}
		String artist = mergeCredits(smtcArtist, albumArtist, title);
		if (sameTrack && richerCredits(previous.artist(), artist)) {
			artist = previous.artist();
		}
		artist = String.join(", ", creditNames(artist));

		long position = incomingPos;
		long sampledAt = now;
		if (sameTrack && playing && previous.playing()) {
			boolean seeked = this.lastSmtcPos >= 0L && Math.abs(incomingPos - this.lastSmtcPos) >= 2000L;
			if (!seeked) {
				position = previous.positionMs();
				sampledAt = previous.sampledAt();
			}
		}
		if (sameTrack && duration <= 0L && previous.durationMs() > 0L) {
			duration = previous.durationMs();
		}

		String query = (smtcArtist + " " + title).trim().toLowerCase(Locale.ROOT);
		boolean due = !query.isEmpty() && (!query.equals(this.lastArtQuery) || (cover.isBlank() && now - this.lastArtAttempt > 15_000L));
		if (due) {
			this.lastArtQuery = query;
			this.lastArtAttempt = now;
			final String lookupArtist = primaryArtist(smtcArtist.isBlank() ? artist : smtcArtist);
			final String lookupAlbum = album;
			COVER.execute(() -> this.fetchMetadata(query, title, lookupArtist, lookupAlbum));
		}

		this.lastSmtcPos = incomingPos;
		this.track.set(new Track(true, playing, title, artist, album, position, duration, sampledAt, cover));
		this.lastTrackKey = trackKey;
	}

	private static long parseLong(String value) {
		try {
			return Long.parseLong(value.trim());
		} catch (NumberFormatException ignored) {
			return 0L;
		}
	}

	private void fetchMetadata(String queryKey, String title, String artist, String album) {
		if (title.isBlank()) {
			return;
		}
		String cover = "";
		String credits = artist;
		try {
			String term = (artist + " " + title).trim();
			JsonObject itunes = json("https://itunes.apple.com/search?media=music&entity=song&limit=25&term=" + URLEncoder.encode(term, StandardCharsets.UTF_8));
			if (itunes != null && itunes.has("results") && !itunes.getAsJsonArray("results").isEmpty()) {
				JsonObject song = bestItunes(itunes.getAsJsonArray("results"), title, artist, album);
				if (song != null) {
					if (song.has("artistName") && !song.get("artistName").isJsonNull()) {
						credits = mergeCredits(credits, "", title);
						credits = appendCredits(credits, song.get("artistName").getAsString());
					}
					if (song.has("artworkUrl100") && !song.get("artworkUrl100").isJsonNull()) {
						cover = downloadCover(song.get("artworkUrl100").getAsString().replace("100x100bb", "600x600bb"));
					}
				}
			}
		} catch (Exception ignored) {
		}
		if (cover.isBlank()) {
			try {
				cover = deezerCover(title, artist, album);
			} catch (Exception ignored) {
			}
		}
		if (!looksCollaborative(credits)) {
			try {
				credits = appendCredits(credits, deezerCredits(title, artist));
			} catch (Exception ignored) {
			}
		}
		this.patchMetadata(queryKey, credits, cover);
	}

	private String downloadCover(String imageUrl) throws Exception {
		if (imageUrl == null || imageUrl.isBlank()) {
			return "";
		}
		Path file = Files.createTempFile("voidmark-cover-", ".jpg");
		file.toFile().deleteOnExit();
		HttpResponse<Path> response = HTTP.send(
			HttpRequest.newBuilder(URI.create(imageUrl))
				.timeout(Duration.ofSeconds(8))
				.header("User-Agent", HTTP_UA)
				.GET()
				.build(),
			HttpResponse.BodyHandlers.ofFile(file)
		);
		if (response.statusCode() < 200 || response.statusCode() >= 300 || Files.size(file) < 64L) {
			Files.deleteIfExists(file);
			return "";
		}
		return file.toAbsolutePath().toString();
	}

	private String deezerCover(String title, String artist, String album) throws Exception {
		JsonObject search = json("https://api.deezer.com/search?limit=15&q=" + URLEncoder.encode("track:\"" + title + "\" artist:\"" + artist + "\"", StandardCharsets.UTF_8));
		if (search == null || !search.has("data") || search.getAsJsonArray("data").isEmpty()) {
			search = json("https://api.deezer.com/search?limit=15&q=" + URLEncoder.encode(title + " " + artist, StandardCharsets.UTF_8));
		}
		if (search == null || !search.has("data") || search.getAsJsonArray("data").isEmpty()) {
			return "";
		}
		JsonObject row = bestDeezer(search.getAsJsonArray("data"), title, artist, album);
		if (row == null || !row.has("album") || !row.get("album").isJsonObject()) {
			return "";
		}
		JsonObject albumJson = row.getAsJsonObject("album");
		String url = text(albumJson, "cover_big");
		if (url.isBlank()) {
			url = text(albumJson, "cover_medium");
		}
		if (url.isBlank()) {
			url = text(albumJson, "cover");
		}
		return downloadCover(url);
	}

	private void patchMetadata(String queryKey, String credits, String cover) {
		Track current = this.track.get();
		if (!current.active() || !queryKey.equals(this.lastArtQuery)) {
			return;
		}
		String artist = richerCredits(credits, current.artist()) ? credits : current.artist();
		artist = String.join(", ", creditNames(artist));
		String art = !cover.isBlank() ? cover : current.coverUrl();
		if (artist.equals(current.artist()) && art.equals(current.coverUrl())) {
			return;
		}
		this.track.set(new Track(
			current.active(),
			current.playing(),
			current.title(),
			artist,
			current.album(),
			current.positionMs(),
			current.durationMs(),
			current.sampledAt(),
			art
		));
	}

	private static JsonObject bestItunes(JsonArray results, String title, String artist, String album) {
		JsonObject best = null;
		int bestScore = 0;
		for (JsonElement element : results) {
			JsonObject song = element.getAsJsonObject();
			int score = matchScore(text(song, "trackName"), text(song, "artistName"), text(song, "collectionName"), title, artist, album);
			if (score > bestScore) {
				bestScore = score;
				best = song;
			}
		}
		return bestScore >= 100 ? best : null;
	}

	private static JsonObject bestDeezer(JsonArray results, String title, String artist, String album) {
		JsonObject best = null;
		int bestScore = 0;
		for (JsonElement element : results) {
			JsonObject row = element.getAsJsonObject();
			String rowArtist = row.has("artist") && row.get("artist").isJsonObject() ? text(row.getAsJsonObject("artist"), "name") : "";
			String rowAlbum = row.has("album") && row.get("album").isJsonObject() ? text(row.getAsJsonObject("album"), "title") : "";
			String rowTitle = text(row, "title_short");
			if (rowTitle.isBlank()) {
				rowTitle = text(row, "title");
			}
			int score = matchScore(rowTitle, rowArtist, rowAlbum, title, artist, album);
			if (score > bestScore) {
				bestScore = score;
				best = row;
			}
		}
		return bestScore >= 100 ? best : null;
	}

	private static int matchScore(String gotTitle, String gotArtist, String gotAlbum, String wantTitle, String wantArtist, String wantAlbum) {
		String title = coreTitle(gotTitle);
		String needTitle = coreTitle(wantTitle);
		if (title.isEmpty() || needTitle.isEmpty()) {
			return 0;
		}
		int score = 0;
		if (title.equals(needTitle)) {
			score += 100;
		} else if (title.startsWith(needTitle) || needTitle.startsWith(title)) {
			score += 70;
		} else if (title.contains(needTitle) || needTitle.contains(title)) {
			score += 40;
		} else {
			return 0;
		}
		String artist = normalize(gotArtist);
		String needArtist = normalize(primaryArtist(wantArtist));
		if (!needArtist.isEmpty() && !artist.isEmpty()) {
			if (artist.equals(needArtist) || artist.contains(needArtist) || needArtist.contains(artist.split(" ")[0])) {
				score += 50;
			} else if (artistTokensOverlap(artist, needArtist)) {
				score += 25;
			} else {
				score -= 40;
			}
		}
		String album = coreTitle(gotAlbum);
		String needAlbum = coreTitle(wantAlbum);
		if (!needAlbum.isEmpty() && !album.isEmpty()) {
			if (album.equals(needAlbum) || album.contains(needAlbum) || needAlbum.contains(album)) {
				score += 40;
			}
		}
		return score;
	}

	private static boolean artistTokensOverlap(String left, String right) {
		for (String token : left.split(" ")) {
			if (token.length() > 2 && right.contains(token)) {
				return true;
			}
		}
		return false;
	}

	private static String primaryArtist(String artist) {
		List<String> names = creditNames(artist);
		return names.isEmpty() ? (artist == null ? "" : artist.trim()) : names.get(0);
	}

	private static String coreTitle(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		String text = value
			.replaceAll("(?i)\\s*[\\(\\[][^\\)\\]]*(?:feat\\.?|ft\\.?|featuring|with|from|official|audio|video|lyric|remaster|radio edit|slowed)[^\\)\\]]*[\\)\\]]", "")
			.replaceAll("(?i)\\s+-\\s+(?:feat\\.?|ft\\.?|remastered|radio edit|slowed).*$", "")
			.trim();
		return normalize(text);
	}

	private String deezerCredits(String title, String artist) throws Exception {
		JsonObject search = json("https://api.deezer.com/search?limit=5&q=" + URLEncoder.encode(title + " " + artist, StandardCharsets.UTF_8));
		if (search == null || !search.has("data") || search.getAsJsonArray("data").isEmpty()) {
			return "";
		}
		String wantTitle = normalize(title);
		long id = -1L;
		for (JsonElement element : search.getAsJsonArray("data")) {
			JsonObject row = element.getAsJsonObject();
			if (normalize(text(row, "title")).equals(wantTitle) || normalize(text(row, "title_short")).equals(wantTitle)) {
				id = row.get("id").getAsLong();
				break;
			}
		}
		if (id < 0L) {
			id = search.getAsJsonArray("data").get(0).getAsJsonObject().get("id").getAsLong();
		}
		JsonObject track = json("https://api.deezer.com/track/" + id);
		if (track == null) {
			return "";
		}
		if (track.has("contributors") && track.get("contributors").isJsonArray()) {
			List<String> names = new ArrayList<>();
			for (JsonElement element : track.getAsJsonArray("contributors")) {
				String name = text(element.getAsJsonObject(), "name");
				if (!name.isBlank() && names.stream().noneMatch(existing -> existing.equalsIgnoreCase(name))) {
					names.add(name);
				}
			}
			if (!names.isEmpty()) {
				return String.join(", ", names);
			}
		}
		if (track.has("artist") && track.get("artist").isJsonObject()) {
			return text(track.getAsJsonObject("artist"), "name");
		}
		return "";
	}

	private JsonObject json(String url) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(6))
			.header("User-Agent", HTTP_UA)
			.GET()
			.build();
		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			return null;
		}
		return JsonParser.parseString(response.body()).getAsJsonObject();
	}

	private static String mergeCredits(String artist, String albumArtist, String title) {
		return appendCredits(appendCredits(artist, albumArtist), featuredCredits(title));
	}

	private static String featuredCredits(String title) {
		if (title == null || title.isBlank()) {
			return "";
		}
		Matcher featured = FEATURED.matcher(title);
		String raw = featured.find() ? featured.group(1) : null;
		if (raw == null) {
			Matcher tail = FEATURED_TAIL.matcher(title);
			if (tail.find()) {
				raw = tail.group(1);
			}
		}
		if (raw == null) {
			return "";
		}
		raw = raw.replaceAll("(?i)\\s*[\\)\\]]\\s*$", "").trim();
		if (isSingleArtistWithThe(raw)) {
			return raw;
		}
		List<String> names = new ArrayList<>();
		for (String part : CREDIT_SPLIT.split(raw)) {
			addCredit(names, part);
		}
		return String.join(", ", names);
	}

	private static String appendCredits(String base, String extra) {
		if (extra == null || extra.isBlank() || extra.equalsIgnoreCase("various artists") || extra.equalsIgnoreCase("va")) {
			return String.join(", ", creditNames(base));
		}
		List<String> names = creditNames(base);
		if (isSingleArtistWithThe(extra)) {
			addCredit(names, extra);
		} else if (extra.contains("&") || extra.contains(",") || extra.toLowerCase(Locale.ROOT).contains(" and ") || extra.toLowerCase(Locale.ROOT).contains(" x ")) {
			for (String part : CREDIT_SPLIT.split(extra)) {
				addCredit(names, part);
			}
		} else {
			addCredit(names, extra);
		}
		return String.join(", ", names);
	}

	private static List<String> creditNames(String list) {
		List<String> names = new ArrayList<>();
		if (list == null || list.isBlank()) {
			return names;
		}
		for (String part : list.split(",")) {
			addCredit(names, part);
		}
		return names;
	}

	private static void addCredit(List<String> names, String raw) {
		String name = raw == null ? "" : raw.trim();
		if (name.isBlank() || name.equalsIgnoreCase("the") || name.equalsIgnoreCase("various artists") || name.equalsIgnoreCase("va")) {
			return;
		}
		if (names.stream().anyMatch(existing -> existing.equalsIgnoreCase(name))) {
			return;
		}
		names.add(name);
	}

	private static boolean isSingleArtistWithThe(String extra) {
		if (extra == null || extra.contains("&")) {
			return false;
		}
		int comma = extra.indexOf(',');
		if (comma < 0 || comma != extra.lastIndexOf(',')) {
			return false;
		}
		String left = extra.substring(0, comma).trim();
		String right = extra.substring(comma + 1).trim();
		return !left.isEmpty() && !left.contains(" ") && right.matches("(?i)the\\s+\\S.*");
	}

	private static boolean richerCredits(String candidate, String current) {
		if (candidate == null || candidate.isBlank()) {
			return false;
		}
		if (current == null || current.isBlank()) {
			return true;
		}
		return creditNames(candidate).size() > creditNames(current).size();
	}

	private static boolean looksCollaborative(String artist) {
		if (artist == null || artist.isBlank()) {
			return false;
		}
		return artist.contains(",") || artist.contains("&") || artist.toLowerCase(Locale.ROOT).contains(" and ") || artist.toLowerCase(Locale.ROOT).contains(" x ");
	}

	private static String text(JsonObject json, String key) {
		return json != null && json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
	}

	private static String normalize(String value) {
		if (value == null) {
			return "";
		}
		return value.toLowerCase(Locale.ROOT)
			.replace("\u2019", "")
			.replace("\u2018", "")
			.replace("\u02BC", "")
			.replace("'", "")
			.replace("`", "")
			.replaceAll("[^a-z0-9]+", " ")
			.replaceAll("\\s+", " ")
			.trim();
	}
}
