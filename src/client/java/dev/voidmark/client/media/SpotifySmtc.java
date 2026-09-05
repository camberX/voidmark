package dev.voidmark.client.media;

import dev.voidmark.Voidmark;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Windows SMTC Spotify via a PowerShell helper. No Spotify account.
 */
public final class SpotifySmtc {
	private static final long RESTART_MS = 2_500L;
	private static final long SEEK_MS = 2_000L;
	private static final long COVER_WAIT_MS = 15_000L;
	private static final String SCRIPT_NAME = "voidmark-nowplaying.ps1";
	private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

	private static final AtomicReference<NowPlaying> CURRENT = new AtomicReference<>(NowPlaying.none());
	private static final AtomicBoolean DISABLED = new AtomicBoolean(!WINDOWS);

	private static Process process;
	private static Thread pump;
	private static volatile long lastStartMs;
	private static volatile long coverEmptySinceMs;
	private static volatile String coverQuery = "";
	private static volatile String lastTrackKey = "";
	private static volatile long lastSmtcPos = -1L;

	private SpotifySmtc() {
	}

	public static NowPlaying current() {
		NowPlaying value = CURRENT.get();
		return value == null || !value.present() ? NowPlaying.none() : value;
	}

	public static void tick(boolean enabled) {
		if (DISABLED.get()) {
			return;
		}
		if (!enabled) {
			stop();
			return;
		}
		if (alive()) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastStartMs < RESTART_MS) {
			return;
		}
		start();
	}

	private static synchronized boolean alive() {
		return process != null && process.isAlive();
	}

	private static synchronized void start() {
		lastStartMs = System.currentTimeMillis();
		try {
			Path script = copyScript();
			ProcessBuilder builder = new ProcessBuilder(
				powershell(),
				"-NoLogo",
				"-STA",
				"-NoProfile",
				"-ExecutionPolicy",
				"Bypass",
				"-File",
				script.toAbsolutePath().toString()
			);
			builder.environment().put("POWERSHELL_TELEMETRY_OPTOUT", "1");
			Process next = builder.start();
			try {
				if (next.getOutputStream() != null) {
					next.getOutputStream().close();
				}
			} catch (Exception ignored) {
			}
			process = next;
			drain(next.getErrorStream(), true);
			pump = new Thread(() -> pump(next), "voidmark-spotify");
			pump.setDaemon(true);
			pump.start();
		} catch (Exception exception) {
			Voidmark.LOGGER.debug("Spotify SMTC helper failed to start", exception);
			stop();
		}
	}

	private static synchronized void stop() {
		Process running = process;
		process = null;
		if (running != null && running.isAlive()) {
			running.destroyForcibly();
		}
		CURRENT.set(NowPlaying.none());
		coverQuery = "";
		coverEmptySinceMs = 0L;
		lastTrackKey = "";
		lastSmtcPos = -1L;
	}

	private static void pump(Process running) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(running.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (running != process) {
					break;
				}
				applyLine(line);
			}
		} catch (Exception ignored) {
		}
	}

	private static void drain(InputStream stream, boolean log) {
		Thread thread = new Thread(() -> {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (log && !line.isBlank()) {
						Voidmark.LOGGER.debug("Spotify SMTC: {}", line);
					}
				}
			} catch (Exception ignored) {
			}
		}, "voidmark-spotify-err");
		thread.setDaemon(true);
		thread.start();
	}

	private static Path copyScript() throws Exception {
		Path dest = Path.of(System.getProperty("java.io.tmpdir"), SCRIPT_NAME);
		try (InputStream in = SpotifySmtc.class.getResourceAsStream("/windows_nowplaying.ps1")) {
			if (in == null) {
				throw new IllegalStateException("missing windows_nowplaying.ps1");
			}
			Files.write(dest, in.readAllBytes());
		}
		return dest;
	}

	private static void applyLine(String raw) {
		if (raw == null) {
			return;
		}
		String line = raw.startsWith("\uFEFF") ? raw.substring(1) : raw;
		if (!line.startsWith("FK")) {
			return;
		}
		if (line.equals("FK") || line.equals("FK\t")) {
			CURRENT.set(NowPlaying.none());
			coverQuery = "";
			coverEmptySinceMs = 0L;
			lastTrackKey = "";
			lastSmtcPos = -1L;
			return;
		}
		String[] parts = line.split("\t", -1);
		if (parts.length < 5) {
			return;
		}
		apply(
			"Playing".equalsIgnoreCase(parts[1]),
			unquote(parts[2]),
			unquote(parts.length > 3 ? parts[3] : ""),
			unquote(parts.length > 7 ? parts[7] : ""),
			unquote(parts.length > 4 ? parts[4] : ""),
			parts.length > 5 ? parseLong(parts[5]) : 0L,
			parts.length > 6 ? parseLong(parts[6]) : 0L,
			unquote(parts.length > 8 ? parts[8] : "")
		);
	}

	private static void apply(
		boolean playing,
		String title,
		String smtcArtist,
		String albumArtist,
		String album,
		long incomingPos,
		long durationMs,
		String coverPath
	) {
		if (title == null || title.isBlank()) {
			CURRENT.set(NowPlaying.none());
			lastTrackKey = "";
			lastSmtcPos = -1L;
			return;
		}
		NowPlaying previous = current();
		String trackKey = title + "|" + smtcArtist + "|" + album;
		boolean same = previous.present() && trackKey.equals(lastTrackKey);
		String credits = mergeArtists(smtcArtist, albumArtist, feats(title), same ? previous.artist() : "");
		long duration = durationMs > 0L ? durationMs : (same ? previous.durationMs() : 0L);
		long position = incomingPos < 0L ? 0L : incomingPos;
		long sampledAt = System.nanoTime();
		if (same && playing && previous.playing()) {
			boolean seeked = lastSmtcPos >= 0L && Math.abs(incomingPos - lastSmtcPos) >= SEEK_MS;
			if (!seeked) {
				position = previous.positionMs();
				sampledAt = previous.sampledAtNanos();
			}
		}
		String cover = coverPath == null ? "" : coverPath.trim();
		if (cover.isBlank() && same && previous.hasCover()) {
			cover = previous.cover();
		}
		NowPlaying next = NowPlaying.fromSmtc(
			title,
			credits,
			album,
			albumArtist,
			"",
			"Spotify",
			"spotify",
			cover,
			playing,
			position,
			duration
		).withTimeline(position, duration, sampledAt, incomingPos);
		next = lookupCover(next);
		lastSmtcPos = incomingPos;
		lastTrackKey = trackKey;
		CURRENT.set(next);
	}

	private static NowPlaying lookupCover(NowPlaying track) {
		if (track.hasCover()) {
			coverQuery = "";
			coverEmptySinceMs = 0L;
			return track;
		}
		String key = track.title() + "|" + track.artist();
		long now = System.currentTimeMillis();
		if (!key.equals(coverQuery)) {
			coverQuery = key;
			coverEmptySinceMs = now;
			TrackLookup.ensure(track.title(), track.artist(), track.album());
			return overlayLookup(track);
		}
		if (now - coverEmptySinceMs >= COVER_WAIT_MS) {
			TrackLookup.ensure(track.title(), track.artist(), track.album());
		}
		return overlayLookup(track);
	}

	private static NowPlaying overlayLookup(NowPlaying track) {
		TrackLookup.Hit hit = TrackLookup.peek(track.title(), track.artist(), track.album());
		if (!hit.usable()) {
			return track;
		}
		return track.withCatalog(hit.title(), hit.artist(), hit.album(), hit.cover(), hit.durationMs());
	}

	private static String mergeArtists(String artist, String albumArtist, List<String> feats, String previous) {
		LinkedHashSet<String> names = new LinkedHashSet<>();
		addNames(names, artist);
		addNames(names, albumArtist);
		for (String feat : feats) {
			addNames(names, feat);
		}
		if (names.isEmpty()) {
			return previous == null ? "" : previous;
		}
		String merged = String.join(", ", names);
		if (previous != null && !previous.isBlank() && nameCount(merged) < nameCount(previous)) {
			return previous;
		}
		return merged;
	}

	private static void addNames(Set<String> names, String raw) {
		if (raw == null || raw.isBlank()) {
			return;
		}
		for (String part : raw.split("\\s*(?:,|&|(?<=\\s)(?:x|and)(?=\\s))\\s*", -1)) {
			String name = part.trim();
			if (name.isEmpty() || NowPlaying.placeholder(name)) {
				continue;
			}
			boolean seen = false;
			for (String have : names) {
				if (NowPlaying.sameName(have, name)) {
					seen = true;
					break;
				}
			}
			if (!seen) {
				names.add(name);
			}
		}
	}

	private static List<String> feats(String title) {
		List<String> out = new ArrayList<>();
		if (title == null) {
			return out;
		}
		int open = title.toLowerCase(Locale.ROOT).indexOf("(feat.");
		if (open < 0) {
			open = title.toLowerCase(Locale.ROOT).indexOf("(ft.");
		}
		if (open < 0) {
			return out;
		}
		int close = title.indexOf(')', open);
		if (close <= open) {
			return out;
		}
		String inner = title.substring(open, close);
		int dot = inner.indexOf('.');
		String body = inner.substring(dot + 1).trim();
		for (String part : body.split("\\s*(?:,|&|(?<=\\s)(?:x|and)(?=\\s))\\s*", -1)) {
			if (!part.isBlank()) {
				out.add(part.trim());
			}
		}
		return out;
	}

	private static int nameCount(String artist) {
		if (artist == null || artist.isBlank()) {
			return 0;
		}
		return artist.split("\\s*,\\s*", -1).length;
	}

	private static String unquote(String value) {
		if (value == null) {
			return "";
		}
		String text = value.trim();
		if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
			return text.substring(1, text.length() - 1);
		}
		return text;
	}

	private static String powershell() {
		String root = System.getenv("SystemRoot");
		if (root == null || root.isBlank()) {
			root = "C:\\Windows";
		}
		Path exe = Path.of(root, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
		return Files.isRegularFile(exe) ? exe.toString() : "powershell.exe";
	}

	private static long parseLong(String value) {
		try {
			return Long.parseLong(value.trim());
		} catch (Exception ignored) {
			return 0L;
		}
	}

}
