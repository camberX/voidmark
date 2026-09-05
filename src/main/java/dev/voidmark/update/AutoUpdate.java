package dev.voidmark.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmark.Voidmark;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.io.InputStream;
import java.io.Reader;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Optional launch gate. When {@code autoUpdate} is on, Minecraft waits here
 * for voidmark.cloud. A newer jar replaces the one in mods, then a helper
 * relaunches this same command after the process exits.
 */
public final class AutoUpdate implements PreLaunchEntrypoint {
	private static final String SHOP = "https://voidmark.cloud";
	private static final String META = SHOP + "/api/mod";
	private static final String DOWNLOAD = SHOP + "/download";
	private static final String GITHUB_META =
		"https://raw.githubusercontent.com/camberX/voidmark/main/web/public/mod/latest.json";
	private static final long MAX_BYTES = 12L * 1024L * 1024L;

	@Override
	public void onPreLaunch() {
		if (!enabled()) {
			return;
		}
		Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(Voidmark.MOD_ID);
		if (container.isEmpty()) {
			return;
		}
		Path current = jarPath(container.get());
		if (current == null) {
			log("Auto-update skipped (dev run, not a jar).");
			return;
		}
		String installed = container.get().getMetadata().getVersion().getFriendlyString();
		log("Checking voidmark.cloud for a newer jar (you have " + installed + ")…");
		try {
			Remote remote = fetchRemote();
			if (remote == null) {
				log("No update info. Continuing launch.");
				return;
			}
			if (compare(remote.version, installed) <= 0) {
				log("Already up to date (" + installed + ").");
				return;
			}
			log("Found " + remote.version + ". Downloading and relaunching into the new jar.");
			Applied applied = apply(current, remote);
			if (applied == null) {
				log("Update failed. Continuing with " + installed + ".");
				return;
			}
			if (relaunch(current, applied)) {
				log("Updated to " + remote.version + " at " + applied.dest.getFileName() + ". Relaunching.");
			} else {
				log("Updated to " + remote.version + " at " + applied.dest.getFileName() + ". Relaunch Minecraft.");
			}
			System.exit(0);
		} catch (Exception exception) {
			log("Update check failed: " + exception.getMessage());
			Voidmark.LOGGER.warn("Auto-update failed", exception);
		}
	}

	private static boolean enabled() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("voidmark.json");
		if (!Files.isRegularFile(path)) {
			return false;
		}
		try (Reader reader = Files.newBufferedReader(path)) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			return json.has("autoUpdate") && json.get("autoUpdate").getAsBoolean();
		} catch (Exception ignored) {
			return false;
		}
	}

	private static Path jarPath(ModContainer container) {
		List<Path> paths = container.getOrigin().getPaths();
		if (paths == null || paths.isEmpty()) {
			return null;
		}
		Path path = paths.getFirst();
		if (path == null || !Files.isRegularFile(path)) {
			return null;
		}
		String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return name.endsWith(".jar") ? path.toAbsolutePath().normalize() : null;
	}

	private static Remote fetchRemote() {
		HttpClient http = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(6))
			.build();
		JsonObject json = getJson(http, META, 12);
		if (json == null) {
			json = getJson(http, GITHUB_META, 12);
		}
		if (json == null || !json.has("version")) {
			return null;
		}
		String version = json.get("version").getAsString().trim();
		if (version.isEmpty()) {
			return null;
		}
		String file = json.has("file") ? json.get("file").getAsString().trim() : "voidmark-" + version + ".jar";
		if (file.isBlank() || file.contains("/") || file.contains("\\") || !file.endsWith(".jar")) {
			file = "voidmark-" + version + ".jar";
		}
		String download = DOWNLOAD;
		if (json.has("url")) {
			String url = json.get("url").getAsString().trim();
			if (url.startsWith("https://voidmark.cloud/")) {
				download = url;
			} else if (url.startsWith("/")) {
				download = SHOP + url;
			}
		}
		List<String> urls = new ArrayList<>();
		urls.add(download);
		urls.add(SHOP + "/voidmark.jar");
		urls.add("https://raw.githubusercontent.com/camberX/voidmark/main/web/public/mod/" + file);
		urls.add("https://raw.githubusercontent.com/camberX/voidmark/main/web/public/mod/voidmark.jar");
		return new Remote(version, file, urls);
	}

	private static JsonObject getJson(HttpClient http, String url, int timeoutSec) {
		try {
			HttpResponse<String> response = http.send(
				request(url, timeoutSec).build(),
				HttpResponse.BodyHandlers.ofString()
			);
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				return null;
			}
			return JsonParser.parseString(response.body()).getAsJsonObject();
		} catch (Exception ignored) {
			return null;
		}
	}

	private static Applied apply(Path current, Remote remote) throws Exception {
		Path mods = current.getParent() == null ? null : current.getParent().toAbsolutePath().normalize();
		if (mods == null) {
			return null;
		}
		Path dest = mods.resolve(remote.file).toAbsolutePath().normalize();
		if (!mods.equals(dest.getParent())) {
			dest = mods.resolve("voidmark-" + remote.version + ".jar");
		}
		Path part = dest.resolveSibling(dest.getFileName() + ".part");
		Files.deleteIfExists(part);
		HttpClient http = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(8))
			.build();
		boolean downloaded = false;
		for (String url : remote.urls) {
			if (download(http, url, part)) {
				downloaded = true;
				break;
			}
		}
		if (!downloaded || !validJar(part)) {
			Files.deleteIfExists(part);
			return null;
		}
		try {
			Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (Exception ignored) {
			Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING);
		}
		List<Path> locked = new ArrayList<>();
		for (Path old : staleJars(mods, dest)) {
			try {
				Files.deleteIfExists(old);
			} catch (Exception ignored) {
				locked.add(old);
			}
		}
		return new Applied(dest, locked);
	}

	private static boolean download(HttpClient http, String url, Path part) {
		try {
			HttpResponse<InputStream> response = http.send(
				request(url, 90).header("Accept", "application/java-archive,application/octet-stream,*/*").build(),
				HttpResponse.BodyHandlers.ofInputStream()
			);
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				return false;
			}
			try (InputStream in = response.body()) {
				Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
			}
			if (Files.size(part) <= 64 || Files.size(part) > MAX_BYTES) {
				Files.deleteIfExists(part);
				return false;
			}
			return true;
		} catch (Exception ignored) {
			try {
				Files.deleteIfExists(part);
			} catch (Exception ignoredToo) {
			}
			return false;
		}
	}

	private static HttpRequest.Builder request(String url, int timeoutSec) {
		return HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(timeoutSec))
			.header("User-Agent", "Voidmark-AutoUpdate")
			.GET();
	}

	private static boolean validJar(Path path) {
		try (InputStream in = Files.newInputStream(path)) {
			byte[] head = in.readNBytes(4);
			return head.length >= 4 && head[0] == 'P' && head[1] == 'K';
		} catch (Exception ignored) {
			return false;
		}
	}

	private static List<Path> staleJars(Path mods, Path keep) throws Exception {
		List<Path> stale = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(mods, "voidmark*.jar")) {
			for (Path path : stream) {
				Path absolute = path.toAbsolutePath().normalize();
				if (!absolute.equals(keep)) {
					stale.add(absolute);
				}
			}
		}
		return stale;
	}

	private static boolean relaunch(Path current, Applied applied) {
		Launch launch = launchCommand(current, applied.dest, applied.locked);
		if (launch == null) {
			scheduleDelete(applied.locked);
			return false;
		}
		try {
			return windows() ? spawnWindows(launch, applied.locked) : spawnUnix(launch, applied.locked);
		} catch (Exception exception) {
			Voidmark.LOGGER.warn("Could not relaunch after update", exception);
			scheduleDelete(applied.locked);
			return false;
		}
	}

	private static Launch launchCommand(Path current, Path dest, List<Path> locked) {
		List<String> argv = linuxCmdline();
		if (argv.size() < 2) {
			argv = processHandleCommand();
		}
		if (argv.size() < 2) {
			argv = reconstructCommand();
		}
		if (argv.size() >= 2) {
			return new Launch(rewrite(argv, current, dest, locked), null);
		}
		if (windows()) {
			String raw = windowsCommandLine(ProcessHandle.current().pid());
			if (raw != null && !raw.isBlank()) {
				return new Launch(List.of(), rewrite(raw, current, dest, locked));
			}
		}
		return null;
	}

	private static List<String> linuxCmdline() {
		Path cmdline = Path.of("/proc/self/cmdline");
		if (!Files.isReadable(cmdline)) {
			return List.of();
		}
		try {
			byte[] raw = Files.readAllBytes(cmdline);
			List<String> args = new ArrayList<>();
			int start = 0;
			for (int i = 0; i <= raw.length; i++) {
				if (i == raw.length || raw[i] == 0) {
					if (i > start) {
						args.add(new String(raw, start, i - start, StandardCharsets.UTF_8));
					}
					start = i + 1;
				}
			}
			return args;
		} catch (Exception ignored) {
			return List.of();
		}
	}

	private static List<String> processHandleCommand() {
		ProcessHandle.Info info = ProcessHandle.current().info();
		Optional<String> command = info.command();
		Optional<String[]> arguments = info.arguments();
		if (command.isEmpty() || arguments.isEmpty()) {
			return List.of();
		}
		List<String> argv = new ArrayList<>();
		argv.add(command.get());
		argv.addAll(List.of(arguments.get()));
		return argv;
	}

	private static List<String> reconstructCommand() {
		String exe = javaBinary();
		String main = System.getProperty("sun.java.command");
		if (exe == null || main == null || main.isBlank()) {
			return List.of();
		}
		List<String> argv = new ArrayList<>();
		argv.add(exe);
		argv.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
		String cp = System.getProperty("java.class.path");
		if (cp != null && !cp.isBlank()) {
			argv.add("-cp");
			argv.add(cp);
		}
		for (String part : main.split(" ")) {
			if (!part.isBlank()) {
				argv.add(part);
			}
		}
		return argv.size() >= 2 ? argv : List.of();
	}

	private static String javaBinary() {
		boolean win = windows();
		Path home = Path.of(System.getProperty("java.home", ""), "bin");
		Path preferred = home.resolve(win ? "javaw.exe" : "java");
		if (Files.isRegularFile(preferred)) {
			return preferred.toAbsolutePath().normalize().toString();
		}
		Path fallback = home.resolve(win ? "java.exe" : "java");
		if (Files.isRegularFile(fallback)) {
			return fallback.toAbsolutePath().normalize().toString();
		}
		return ProcessHandle.current().info().command().orElse(null);
	}

	private static String windowsCommandLine(long pid) {
		try {
			Process process = new ProcessBuilder(
				"powershell",
				"-NoProfile",
				"-Command",
				"(Get-CimInstance Win32_Process -Filter \"ProcessId=" + pid + "\").CommandLine"
			).redirectError(ProcessBuilder.Redirect.DISCARD).start();
			String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
			if (!process.waitFor(4, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return null;
			}
			return out.isEmpty() ? null : out;
		} catch (Exception ignored) {
			return null;
		}
	}

	private static List<String> rewrite(List<String> argv, Path current, Path dest, List<Path> locked) {
		List<String> out = new ArrayList<>(argv.size());
		for (String arg : argv) {
			out.add(rewrite(arg, current, dest, locked));
		}
		return out;
	}

	private static String rewrite(String text, Path current, Path dest, List<Path> locked) {
		List<Path> olds = new ArrayList<>();
		olds.add(current);
		olds.addAll(locked);
		olds.sort(Comparator.comparingInt((Path path) -> path.toString().length()).reversed());
		String destAbs = dest.toAbsolutePath().normalize().toString();
		String destName = dest.getFileName().toString();
		String rewritten = text;
		for (Path old : olds) {
			if (old == null) {
				continue;
			}
			String abs = old.toAbsolutePath().normalize().toString();
			String name = old.getFileName().toString();
			if (!abs.isEmpty() && rewritten.contains(abs)) {
				rewritten = rewritten.replace(abs, destAbs);
			} else if (!name.isEmpty() && rewritten.contains(name)) {
				rewritten = rewritten.replace(name, destName);
			}
		}
		return rewritten;
	}

	private static boolean spawnWindows(Launch launch, List<Path> locked) throws Exception {
		Path script = Files.createTempFile("voidmark-relaunch-", ".cmd");
		String cwd = cwd();
		StringBuilder body = new StringBuilder();
		body.append("@echo off\r\n");
		body.append("setlocal\r\n");
		body.append(":wait\r\n");
		body.append("timeout /t 1 /nobreak >nul\r\n");
		body.append("tasklist /FI \"PID eq ").append(ProcessHandle.current().pid())
			.append("\" 2>nul | find \"").append(ProcessHandle.current().pid()).append("\" >nul && goto wait\r\n");
		for (Path path : locked) {
			body.append("del /f /q ").append(cmdQuote(path.toString())).append(" 2>nul\r\n");
		}
		body.append("cd /d ").append(cmdQuote(cwd)).append("\r\n");
		body.append("start \"\" /D ").append(cmdQuote(cwd)).append(' ');
		if (!launch.argv.isEmpty()) {
			for (int i = 0; i < launch.argv.size(); i++) {
				if (i > 0) {
					body.append(' ');
				}
				body.append(cmdQuote(launch.argv.get(i)));
			}
		} else {
			body.append(launch.raw);
		}
		body.append("\r\n");
		body.append("del \"%~f0\"\r\n");
		Files.writeString(script, body.toString(), StandardCharsets.UTF_8);
		new ProcessBuilder("cmd", "/c", "start \"\" /min " + cmdQuote(script.toString()))
			.redirectOutput(ProcessBuilder.Redirect.DISCARD)
			.redirectError(ProcessBuilder.Redirect.DISCARD)
			.start();
		return true;
	}

	private static boolean spawnUnix(Launch launch, List<Path> locked) throws Exception {
		if (launch.argv.isEmpty()) {
			scheduleDelete(locked);
			return false;
		}
		Path script = Files.createTempFile("voidmark-relaunch-", ".sh");
		StringBuilder body = new StringBuilder();
		body.append("#!/bin/sh\n");
		body.append("while kill -0 ").append(ProcessHandle.current().pid())
			.append(" 2>/dev/null; do sleep 0.15; done\n");
		if (!locked.isEmpty()) {
			body.append("rm -f");
			for (Path path : locked) {
				body.append(' ').append(shQuote(path.toString()));
			}
			body.append('\n');
		}
		body.append("cd ").append(shQuote(cwd())).append('\n');
		body.append("rm -f ").append(shQuote(script.toString())).append('\n');
		body.append("exec");
		for (String arg : launch.argv) {
			body.append(' ').append(shQuote(arg));
		}
		body.append('\n');
		Files.writeString(script, body.toString(), StandardCharsets.UTF_8);
		script.toFile().setExecutable(true, false);
		new ProcessBuilder("sh", "-c", "nohup sh " + shQuote(script.toString()) + " >/dev/null 2>&1 &")
			.redirectOutput(ProcessBuilder.Redirect.DISCARD)
			.redirectError(ProcessBuilder.Redirect.DISCARD)
			.start();
		return true;
	}

	private static void scheduleDelete(List<Path> paths) {
		if (paths == null || paths.isEmpty()) {
			return;
		}
		long pid = ProcessHandle.current().pid();
		try {
			ProcessBuilder builder;
			if (windows()) {
				StringBuilder cmd = new StringBuilder("ping 127.0.0.1 -n 4 >nul");
				for (Path path : paths) {
					cmd.append(" & del /f /q ").append(cmdQuote(path.toString()));
				}
				builder = new ProcessBuilder("cmd", "/c", cmd.toString());
			} else {
				StringBuilder cmd = new StringBuilder("while kill -0 ");
				cmd.append(pid).append(" 2>/dev/null; do sleep 0.2; done; rm -f");
				for (Path path : paths) {
					cmd.append(' ').append(shQuote(path.toString()));
				}
				builder = new ProcessBuilder("sh", "-c", cmd.toString());
			}
			builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
			builder.redirectError(ProcessBuilder.Redirect.DISCARD);
			builder.start();
		} catch (Exception exception) {
			Voidmark.LOGGER.warn("Could not schedule old jar cleanup", exception);
		}
	}

	private static String cwd() {
		return Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize().toString();
	}

	private static boolean windows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}

	private static String cmdQuote(String value) {
		return '"' + value.replace("\"", "\"\"") + '"';
	}

	private static String shQuote(String value) {
		return "'" + value.replace("'", "'\\''") + "'";
	}

	private static int compare(String left, String right) {
		int[] a = parts(left);
		int[] b = parts(right);
		int n = Math.max(a.length, b.length);
		for (int i = 0; i < n; i++) {
			int av = i < a.length ? a[i] : 0;
			int bv = i < b.length ? b[i] : 0;
			if (av != bv) {
				return Integer.compare(av, bv);
			}
		}
		return 0;
	}

	private static int[] parts(String version) {
		String[] bits = version == null ? new String[0] : version.split("[^0-9]+");
		int[] out = new int[Math.max(1, bits.length)];
		int n = 0;
		for (String bit : bits) {
			if (bit.isEmpty()) {
				continue;
			}
			try {
				out[n++] = Integer.parseInt(bit);
			} catch (NumberFormatException ignored) {
			}
		}
		if (n == out.length) {
			return out;
		}
		int[] trimmed = new int[n];
		System.arraycopy(out, 0, trimmed, 0, n);
		return trimmed;
	}

	private static void log(String message) {
		String line = "Voidmark | " + message;
		System.out.println(line);
		Voidmark.LOGGER.info(message);
	}

	private record Remote(String version, String file, List<String> urls) {
	}

	private record Applied(Path dest, List<Path> locked) {
	}

	private record Launch(List<String> argv, String raw) {
	}
}
