package dev.voidmark.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmark.Voidmark;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Optional launch gate. When {@code autoUpdate} is on, Minecraft waits here
 * for voidmark.cloud. A newer jar is written into mods, the old one is
 * retired, and this process exits so the next launch loads the new jar.
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
		Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(Voidmark.MOD_ID);
		if (container.isEmpty()) {
			return;
		}
		Path current = jarPath(container.get());
		if (current == null) {
			if (enabled()) {
				log("Auto-update skipped (dev run, not a jar).");
			}
			return;
		}
		Path mods = current.getParent();
		if (mods != null) {
			sweep(mods);
		}
		if (!enabled()) {
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
			log("Found " + remote.version + ". Downloading and closing Minecraft so the new jar can load.");
			Path dest = apply(current, remote);
			if (dest == null) {
				log("Update failed. Continuing with " + installed + ".");
				return;
			}
			log("Updated to " + remote.version + " at " + dest.getFileName() + ". Relaunch Minecraft.");
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

	private static Path apply(Path current, Remote remote) throws Exception {
		Path mods = current.getParent() == null ? null : current.getParent().toAbsolutePath().normalize();
		if (mods == null) {
			return null;
		}
		Path dest = mods.resolve(remote.file).toAbsolutePath().normalize();
		if (!mods.equals(dest.getParent()) || dest.equals(current)) {
			dest = mods.resolve("voidmark-" + remote.version + ".jar");
		}
		if (dest.equals(current)) {
			dest = mods.resolve("voidmark-" + remote.version + "-new.jar");
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
		for (Path old : staleJars(mods, dest)) {
			retire(old);
		}
		return dest;
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

	private static void retire(Path old) {
		try {
			Files.deleteIfExists(old);
			return;
		} catch (Exception ignored) {
		}
		Path retired = old.resolveSibling(old.getFileName().toString() + ".old");
		try {
			Files.move(old, retired, StandardCopyOption.REPLACE_EXISTING);
			retired.toFile().deleteOnExit();
			return;
		} catch (Exception ignored) {
		}
		old.toFile().deleteOnExit();
	}

	private static void sweep(Path mods) {
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(mods, "voidmark*")) {
			for (Path path : stream) {
				String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
				if (name.endsWith(".old") || name.endsWith(".part") || name.endsWith(".jar.old")) {
					try {
						Files.deleteIfExists(path);
					} catch (Exception ignored) {
					}
				}
			}
		} catch (Exception ignored) {
		}
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
}
