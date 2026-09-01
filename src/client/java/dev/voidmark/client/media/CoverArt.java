package dev.voidmark.client.media;

import com.mojang.blaze3d.platform.NativeImage;
import dev.voidmark.Voidmark;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Loads the current track's album art onto a dynamic GUI texture.
 * Accepts http(s) URLs, {@code file:} URIs, and local paths (Windows SMTC dump).
 */
public final class CoverArt {
	private static final Identifier TEXTURE_ID = Voidmark.id("music_cover");
	private static final int MAX_BYTES = 2 * 1024 * 1024;
	private static final int MAX_EDGE = 256;
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(4))
		.build();

	private static volatile String boundKey = "";
	private static volatile boolean ready;
	private static volatile int texSize;
	private static volatile long lastTryNs;
	private static int generation;

	private CoverArt() {
	}

	public static void bind(NowPlaying track) {
		String cover = track == null || !track.hasCover() ? "" : track.cover().trim();
		String title = track == null ? "" : nullToEmpty(track.title());
		String artist = track == null ? "" : nullToEmpty(track.artist());
		long stamp = stamp(cover);
		String key = cover + "|" + title + "|" + artist + "|" + stamp;
		long now = System.nanoTime();
		if (key.equals(boundKey)) {
			if (ready || cover.isEmpty()) {
				return;
			}
			if (now - lastTryNs < 1_000_000_000L) {
				return;
			}
		}
		boundKey = key;
		lastTryNs = now;
		ready = false;
		texSize = 0;
		if (cover.isEmpty() || cover.startsWith("data:")) {
			generation++;
			return;
		}
		int gen = ++generation;
		Util.nonCriticalIoPool().execute(() -> load(gen, cover));
	}

	public static boolean ready() {
		return ready && texSize > 0;
	}

	public static Identifier id() {
		return TEXTURE_ID;
	}

	public static int size() {
		return texSize;
	}

	private static void load(int gen, String spec) {
		try {
			byte[] bytes = fetch(spec);
			if (bytes.length < 24 || bytes.length > MAX_BYTES) {
				return;
			}
			NativeImage image;
			try {
				image = NativeImage.read(bytes);
			} catch (Exception exception) {
				return;
			}
			NativeImage square = square(image);
			if (square != image) {
				image.close();
			}
			Minecraft.getInstance().execute(() -> {
				if (gen != generation) {
					square.close();
					return;
				}
				try {
					DynamicTexture texture = new DynamicTexture(() -> "voidmark-cover", square);
					Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, texture);
					texSize = square.getWidth();
					ready = true;
				} catch (Exception exception) {
					square.close();
					ready = false;
					texSize = 0;
				}
			});
		} catch (Exception ignored) {
		}
	}

	private static NativeImage square(NativeImage src) {
		int width = src.getWidth();
		int height = src.getHeight();
		if (width < 1 || height < 1) {
			return src;
		}
		int side = Math.min(width, height);
		int sx = (width - side) / 2;
		int sy = (height - side) / 2;
		int dest = Math.min(MAX_EDGE, side);
		NativeImage out = new NativeImage(dest, dest, false);
		if (sx == 0 && sy == 0 && side == dest) {
			out.copyFrom(src);
			return out;
		}
		src.resizeSubRectTo(sx, sy, side, side, out);
		return out;
	}

	private static byte[] fetch(String spec) throws Exception {
		String value = expand(spec.trim());
		if (value.startsWith("//")) {
			value = "https:" + value;
		}
		if (value.startsWith("http://") || value.startsWith("https://")) {
			HttpRequest request = HttpRequest.newBuilder(URI.create(value))
				.timeout(Duration.ofSeconds(8))
				.header("User-Agent", "Mozilla/5.0 Voidmark/1.1")
				.GET()
				.build();
			HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IllegalStateException("HTTP " + response.statusCode());
			}
			return response.body();
		}
		Path path;
		if (value.regionMatches(true, 0, "file:", 0, 5)) {
			path = Path.of(URI.create(value));
		} else {
			path = Path.of(value);
		}
		if (!Files.isRegularFile(path) || Files.size(path) > MAX_BYTES) {
			throw new IllegalStateException("Missing cover file");
		}
		return Files.readAllBytes(path);
	}

	private static String expand(String url) {
		return url.replace("{w}", "300").replace("{h}", "300").replace("{c}", "ffffff");
	}

	private static long stamp(String spec) {
		if (spec == null || spec.isBlank()) {
			return 0L;
		}
		try {
			Path path;
			if (spec.regionMatches(true, 0, "file:", 0, 5)) {
				path = Path.of(URI.create(spec.trim()));
			} else if (spec.startsWith("http://") || spec.startsWith("https://") || spec.startsWith("//")) {
				return 0L;
			} else {
				path = Path.of(spec.trim());
			}
			if (Files.isRegularFile(path)) {
				return Files.getLastModifiedTime(path).toMillis();
			}
		} catch (Exception ignored) {
		}
		return 0L;
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
