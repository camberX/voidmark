package dev.voidmark.client.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmark.Voidmark;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class WindowsNowPlaying {
	private static final long TICKS_PER_MS = 10_000L;

	private Process process;
	private volatile NowPlaying latest = NowPlaying.none();
	private Thread reader;

	void start() {
		if (!MediaKeys.windows()) {
			return;
		}
		stop();
		try {
			Path script = extractScript();
			ProcessBuilder builder = new ProcessBuilder(
				"powershell.exe",
				"-NoProfile",
				"-NonInteractive",
				"-ExecutionPolicy", "Bypass",
				"-File", script.toAbsolutePath().toString()
			);
			builder.redirectError(ProcessBuilder.Redirect.DISCARD);
			process = builder.start();
			reader = new Thread(() -> pump(process), "voidmark-nowplaying");
			reader.setDaemon(true);
			reader.start();
		} catch (Exception exception) {
			Voidmark.LOGGER.debug("Could not start Windows now-playing", exception);
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
		return latest;
	}

	boolean alive() {
		return process != null && process.isAlive();
	}

	private void pump(Process running) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(running.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (running != process) {
					break;
				}
				NowPlaying parsed = parse(line);
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

	private static NowPlaying parse(String line) {
		if (line == null || line.isBlank() || !line.startsWith("{")) {
			return null;
		}
		try {
			JsonObject json = JsonParser.parseString(line).getAsJsonObject();
			if (!json.has("ok") || !json.get("ok").getAsBoolean()) {
				return NowPlaying.none();
			}
			String title = text(json, "title");
			if (title.isBlank()) {
				return NowPlaying.none();
			}
			return new NowPlaying(
				title,
				text(json, "artist"),
				text(json, "album"),
				text(json, "app"),
				"windows",
				json.has("playing") && json.get("playing").getAsBoolean(),
				ticksToMs(json, "position"),
				ticksToMs(json, "duration"),
				System.nanoTime()
			);
		} catch (Exception exception) {
			return null;
		}
	}

	private static long ticksToMs(JsonObject json, String key) {
		if (!json.has(key)) {
			return 0L;
		}
		try {
			return Math.max(0L, json.get(key).getAsLong() / TICKS_PER_MS);
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
}
