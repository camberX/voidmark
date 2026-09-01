package dev.voidmark.client.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmark.Voidmark;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class WindowsNowPlaying {
	private static final long TICKS_PER_MS = 10_000L;

	private Process process;
	private Path jsonPath;
	private volatile NowPlaying latest = NowPlaying.none();
	private volatile String lastError = "";
	private Thread reader;

	void start() {
		if (!MediaKeys.windows()) {
			return;
		}
		stop();
		try {
			Path script = extractScript();
			jsonPath = FabricLoader.getInstance().getConfigDir().resolve("voidmark-nowplaying.json");
			ProcessBuilder builder = new ProcessBuilder(
				shell(),
				"-NoLogo",
				"-NoProfile",
				"-NonInteractive",
				"-ExecutionPolicy", "Bypass",
				"-File", script.toAbsolutePath().toString(),
				jsonPath.toAbsolutePath().toString()
			);
			builder.redirectError(ProcessBuilder.Redirect.DISCARD);
			process = builder.start();
			reader = new Thread(() -> pump(process), "voidmark-nowplaying");
			reader.setDaemon(true);
			reader.start();
		} catch (Exception exception) {
			Voidmark.LOGGER.warn("Could not start Windows now-playing", exception);
			lastError = "powershell";
			latest = NowPlaying.none();
		}
	}

	void stop() {
		Process running = process;
		process = null;
		if (running != null) {
			running.destroyForcibly();
		}
		reader = null;
	}

	NowPlaying snapshot() {
		NowPlaying fromFile = readFile();
		if (fromFile.present()) {
			latest = fromFile;
			return fromFile;
		}
		return latest;
	}

	String error() {
		return lastError;
	}

	boolean alive() {
		return process != null && process.isAlive();
	}

	private NowPlaying readFile() {
		if (jsonPath == null || !Files.isRegularFile(jsonPath)) {
			return NowPlaying.none();
		}
		try {
			String text = Files.readString(jsonPath, StandardCharsets.UTF_8).trim();
			NowPlaying parsed = parse(text);
			return parsed == null ? NowPlaying.none() : parsed;
		} catch (Exception exception) {
			return NowPlaying.none();
		}
	}

	private void pump(Process running) {
		try (InputStream raw = running.getInputStream()) {
			byte[] peek = raw.readNBytes(2);
			Charset charset = StandardCharsets.UTF_8;
			if (peek.length == 2 && (peek[0] & 0xFF) == 0xFF && (peek[1] & 0xFF) == 0xFE) {
				charset = StandardCharsets.UTF_16LE;
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(new SequenceWithPrefix(raw, peek), charset));
			String line;
			while ((line = reader.readLine()) != null) {
				if (running != process) {
					break;
				}
				NowPlaying parsed = parse(line.trim());
				if (parsed != null) {
					latest = parsed;
				}
			}
		} catch (Exception ignored) {
		}
		if (process == running) {
			latest = NowPlaying.none();
		}
	}

	private NowPlaying parse(String line) {
		int brace = line == null ? -1 : line.indexOf('{');
		if (brace < 0) {
			return null;
		}
		line = line.substring(brace);
		try {
			JsonObject json = JsonParser.parseString(line).getAsJsonObject();
			if (!json.has("ok") || !json.get("ok").getAsBoolean()) {
				lastError = text(json, "err");
				return NowPlaying.none();
			}
			String title = text(json, "title");
			if (title.isBlank()) {
				lastError = "empty-title";
				return NowPlaying.none();
			}
			lastError = "";
			long durationMs = firstPositive(
				millisField(json, "durationMs"),
				ticksToMs(json, "duration")
			);
			long positionMs = millisField(json, "positionMs");
			if (positionMs <= 0L) {
				positionMs = ticksToMs(json, "position");
			}
			boolean playing = true;
			if (json.has("playing") && !json.get("playing").isJsonNull()) {
				try {
					playing = json.get("playing").getAsBoolean();
				} catch (Exception ignored) {
					try {
						playing = json.get("playing").getAsDouble() != 0d;
					} catch (Exception ignoredAgain) {
						playing = true;
					}
				}
			}
			return NowPlaying.fromSmtc(
				title,
				text(json, "artist"),
				text(json, "album"),
				text(json, "albumArtist"),
				text(json, "subtitle"),
				text(json, "app"),
				text(json, "kind"),
				text(json, "art"),
				playing,
				positionMs,
				durationMs
			);
		} catch (Exception exception) {
			return null;
		}
	}

	private static long firstPositive(long... values) {
		for (long value : values) {
			if (value > 0L) {
				return value;
			}
		}
		return 0L;
	}

	private static long millisField(JsonObject json, String key) {
		if (!json.has(key) || json.get(key).isJsonNull()) {
			return 0L;
		}
		try {
			double raw = json.get(key).getAsDouble();
			if (raw <= 0d) {
				return 0L;
			}
			return Math.round(raw);
		} catch (Exception exception) {
			return 0L;
		}
	}

	private static long ticksToMs(JsonObject json, String key) {
		if (!json.has(key) || json.get(key).isJsonNull()) {
			return 0L;
		}
		try {
			double raw = json.get(key).getAsDouble();
			if (raw <= 0d) {
				return 0L;
			}
			// TimeSpan ticks: 10_000 per millisecond. Values this large are ticks.
			if (raw >= 10_000_000d) {
				return Math.round(raw / TICKS_PER_MS);
			}
			return Math.round(raw);
		} catch (Exception exception) {
			return 0L;
		}
	}

	private static String text(JsonObject json, String key) {
		if (!json.has(key) || json.get(key).isJsonNull()) {
			return "";
		}
		try {
			return json.get(key).getAsString().trim();
		} catch (Exception exception) {
			return "";
		}
	}

	private static Path extractScript() throws Exception {
		Path target = FabricLoader.getInstance().getConfigDir().resolve("voidmark-nowplaying.ps1");
		try (InputStream stream = WindowsNowPlaying.class.getResourceAsStream("/assets/voidmark/media/nowplaying.ps1")) {
			if (stream == null) {
				throw new IllegalStateException("Missing nowplaying.ps1");
			}
			Files.createDirectories(target.getParent());
			Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
		}
		return target;
	}

	private static String shell() {
		String root = System.getenv("SystemRoot");
		if (root == null || root.isBlank()) {
			root = "C:\\Windows";
		}
		String programFiles = System.getenv().getOrDefault("ProgramFiles", "C:\\Program Files");
		Path pwsh = Path.of(programFiles, "PowerShell", "7", "pwsh.exe");
		if (Files.isRegularFile(pwsh)) {
			return pwsh.toString();
		}
		Path windows = Path.of(root, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
		if (Files.isRegularFile(windows)) {
			return windows.toString();
		}
		return "powershell.exe";
	}

	private static final class SequenceWithPrefix extends InputStream {
		private final InputStream inner;
		private final byte[] prefix;
		private int at;

		private SequenceWithPrefix(InputStream inner, byte[] prefix) {
			this.inner = inner;
			this.prefix = prefix == null ? new byte[0] : prefix;
		}

		@Override
		public int read() throws java.io.IOException {
			if (at < prefix.length) {
				return prefix[at++] & 0xFF;
			}
			return inner.read();
		}

		@Override
		public int read(byte[] buffer, int off, int len) throws java.io.IOException {
			if (at < prefix.length) {
				int n = Math.min(len, prefix.length - at);
				System.arraycopy(prefix, at, buffer, off, n);
				at += n;
				if (n == len) {
					return n;
				}
				int more = inner.read(buffer, off + n, len - n);
				return more < 0 ? n : n + more;
			}
			return inner.read(buffer, off, len);
		}
	}
}
