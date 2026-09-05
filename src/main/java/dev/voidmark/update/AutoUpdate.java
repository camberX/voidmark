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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Optional launch gate. When {@code autoUpdate} is on, Minecraft waits here
 * for voidmark.cloud. A newer jar replaces the one in mods, then a detached
 * helper relaunches this same command after the process exits.
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
				try {
					Thread.sleep(400);
				} catch (InterruptedException ignored) {
				}
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
		List<String> argv = launchCommand(current, applied.dest, applied.locked);
		if (argv == null || argv.size() < 2) {
			scheduleDelete(applied.locked);
			return false;
		}
		try {
			return windows() ? spawnWindows(argv, applied.locked) : spawnUnix(argv, applied.locked);
		} catch (Exception exception) {
			Voidmark.LOGGER.warn("Could not relaunch after update", exception);
			scheduleDelete(applied.locked);
			return false;
		}
	}

	private static List<String> launchCommand(Path current, Path dest, List<Path> locked) {
		List<String> argv = List.of();
		if (windows()) {
			String raw = windowsCommandLine(ProcessHandle.current().pid());
			if (raw != null && !raw.isBlank()) {
				argv = splitWindowsCommandLine(raw);
			}
			if (argv.size() < 2) {
				argv = processHandleCommand();
			}
		} else {
			argv = linuxCmdline();
			if (argv.size() < 2) {
				argv = processHandleCommand();
			}
		}
		if (argv.size() < 2) {
			argv = reconstructCommand();
		}
		return argv.size() < 2 ? null : rewrite(argv, current, dest, locked);
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

	private static boolean spawnWindows(List<String> argv, List<Path> locked) throws Exception {
		Path script = Path.of(cwd(), "voidmark-relaunch.ps1").toAbsolutePath().normalize();
		writeWindowsHelper(script, argv, locked);
		String child = "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "
			+ cmdQuote(script.toString());
		if (wmiCreate(child, cwd())) {
			log("Relaunch helper detached.");
			return true;
		}
		if (wmicCreate(child, cwd())) {
			log("Relaunch helper detached.");
			return true;
		}
		new ProcessBuilder(
			"cmd.exe",
			"/c",
			"start \"VoidmarkUpdate\" /MIN " + child
		).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
		log("Relaunch helper started.");
		return true;
	}

	private static boolean spawnUnix(List<String> argv, List<Path> locked) throws Exception {
		Path script = Path.of(cwd(), "voidmark-relaunch.sh").toAbsolutePath().normalize();
		writeUnixHelper(script, argv, locked);
		script.toFile().setExecutable(true, false);
		if (start(List.of("setsid", "-f", "sh", script.toString()))) {
			log("Relaunch helper detached.");
			return true;
		}
		new ProcessBuilder("sh", "-c", "nohup sh " + shQuote(script.toString()) + " >/dev/null 2>&1 </dev/null &")
			.redirectOutput(ProcessBuilder.Redirect.DISCARD)
			.redirectError(ProcessBuilder.Redirect.DISCARD)
			.start();
		log("Relaunch helper started.");
		return true;
	}

	private static void writeWindowsHelper(Path script, List<String> argv, List<Path> locked) throws Exception {
		String cwd = cwd();
		StringBuilder body = new StringBuilder();
		body.append("$ErrorActionPreference = 'Continue'\r\n");
		for (Map.Entry<String, String> env : System.getenv().entrySet()) {
			if (env.getKey() == null || env.getKey().startsWith("=")) {
				continue;
			}
			body.append("[Environment]::SetEnvironmentVariable(")
				.append(psSingle(env.getKey())).append(", ")
				.append(psSingle(env.getValue() == null ? "" : env.getValue()))
				.append(", 'Process')\r\n");
		}
		body.append("Start-Sleep -Seconds 2\r\n");
		body.append("Set-Location -LiteralPath ").append(psSingle(cwd)).append("\r\n");
		for (Path path : locked) {
			body.append("Remove-Item -LiteralPath ").append(psSingle(path.toString()))
				.append(" -Force -ErrorAction SilentlyContinue\r\n");
		}
		body.append("$exe = ").append(psSingle(argv.getFirst())).append("\r\n");
		body.append("$mcArgs = @(\r\n");
		for (int i = 1; i < argv.size(); i++) {
			body.append("  ").append(psSingle(argv.get(i)));
			if (i + 1 < argv.size()) {
				body.append(',');
			}
			body.append("\r\n");
		}
		body.append(")\r\n");
		body.append("$logDir = ").append(psSingle(Path.of(cwd, "logs").toString())).append("\r\n");
		body.append("New-Item -ItemType Directory -Force -Path $logDir | Out-Null\r\n");
		body.append("Add-Content -LiteralPath (Join-Path $logDir 'voidmark-update.log') -Value ('relaunch ' + $exe)\r\n");
		body.append("if ($mcArgs.Count -gt 0) { & $exe @mcArgs } else { & $exe }\r\n");
		body.append("Remove-Item -LiteralPath ").append(psSingle(script.toString()))
			.append(" -Force -ErrorAction SilentlyContinue\r\n");
		byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
		byte[] text = body.toString().getBytes(StandardCharsets.UTF_8);
		byte[] out = new byte[bom.length + text.length];
		System.arraycopy(bom, 0, out, 0, bom.length);
		System.arraycopy(text, 0, out, bom.length, text.length);
		Files.write(script, out);
	}

	private static void writeUnixHelper(Path script, List<String> argv, List<Path> locked) throws Exception {
		StringBuilder body = new StringBuilder();
		body.append("#!/bin/sh\n");
		body.append("trap '' HUP\n");
		body.append("sleep 1\n");
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
		for (String arg : argv) {
			body.append(' ').append(shQuote(arg));
		}
		body.append('\n');
		Files.writeString(script, body.toString(), StandardCharsets.UTF_8);
	}

	private static boolean wmiCreate(String commandLine, String directory) {
		String ps = "$r = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{CommandLine="
			+ psSingle(commandLine) + "; CurrentDirectory=" + psSingle(directory)
			+ "}; if ($null -eq $r -or $r.ReturnValue -ne 0) { exit 1 }";
		try {
			Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", ps)
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.redirectError(ProcessBuilder.Redirect.DISCARD)
				.start();
			if (!process.waitFor(8, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return false;
			}
			return process.exitValue() == 0;
		} catch (Exception ignored) {
			return false;
		}
	}

	private static boolean wmicCreate(String commandLine, String directory) {
		try {
			Process process = new ProcessBuilder(
				"wmic",
				"process",
				"call",
				"create",
				commandLine + "," + directory
			).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
			if (!process.waitFor(8, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return false;
			}
			return process.exitValue() == 0;
		} catch (Exception ignored) {
			return false;
		}
	}

	private static boolean start(List<String> command) {
		try {
			Process process = new ProcessBuilder(command)
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.redirectError(ProcessBuilder.Redirect.DISCARD)
				.start();
			if (process.waitFor(400, TimeUnit.MILLISECONDS)) {
				return process.exitValue() == 0;
			}
			return true;
		} catch (Exception ignored) {
			return false;
		}
	}

	private static List<String> splitWindowsCommandLine(String command) {
		List<String> args = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		boolean inQuotes = false;
		int slashes = 0;
		for (int i = 0; i < command.length(); i++) {
			char c = command.charAt(i);
			if (c == '\\') {
				slashes++;
				continue;
			}
			if (c == '"') {
				cur.append("\\".repeat(slashes / 2));
				if (slashes % 2 == 0) {
					inQuotes = !inQuotes;
				} else {
					cur.append('"');
				}
				slashes = 0;
				continue;
			}
			if (slashes > 0) {
				cur.append("\\".repeat(slashes));
				slashes = 0;
			}
			if (!inQuotes && (c == ' ' || c == '\t')) {
				if (cur.length() > 0) {
					args.add(cur.toString());
					cur.setLength(0);
				}
			} else {
				cur.append(c);
			}
		}
		if (slashes > 0) {
			cur.append("\\".repeat(slashes));
		}
		if (cur.length() > 0) {
			args.add(cur.toString());
		}
		return args;
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

	private static String psSingle(String value) {
		return "'" + value.replace("'", "''") + "'";
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
}
