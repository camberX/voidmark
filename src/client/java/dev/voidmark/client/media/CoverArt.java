package dev.voidmark.client.media;

import com.mojang.blaze3d.platform.NativeImage;
import dev.voidmark.Voidmark;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;

/**
 * Loads the current track's album art onto a dynamic GUI texture.
 * Uses a provided URL/path when the player exposes one, otherwise looks the
 * cover up from Deezer / iTunes using the title and artist.
 */
public final class CoverArt {
	private static final Identifier TEXTURE_ID = Voidmark.id("music_cover");
	private static final int MAX_BYTES = 2 * 1024 * 1024;
	private static final int MAX_EDGE = 256;
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(6))
		.build();

	private static volatile String boundKey = "";
	private static volatile boolean ready;
	private static volatile int texSize;
	private static volatile long lastTryNs;
	private static int generation;

	private CoverArt() {
	}

	public static void bind(NowPlaying track) {
		if (track == null || !track.present()) {
			if (!boundKey.isEmpty()) {
				boundKey = "";
				ready = false;
				texSize = 0;
				generation++;
			}
			return;
		}
		String cover = track.hasCover() ? track.cover().trim() : "";
		String title = nullToEmpty(track.title());
		String artist = nullToEmpty(track.artist());
		String album = nullToEmpty(track.album());
		long stamp = stamp(cover);
		String key = cover + "|" + title + "|" + artist + "|" + album + "|" + stamp;
		long now = System.nanoTime();
		if (key.equals(boundKey)) {
			if (ready) {
				return;
			}
			if (now - lastTryNs < 2_000_000_000L) {
				return;
			}
		}
		boundKey = key;
		lastTryNs = now;
		ready = false;
		texSize = 0;
		if (cover.startsWith("data:") && !cover.startsWith("data:image")) {
			generation++;
			return;
		}
		int gen = ++generation;
		String spec = cover;
		Util.nonCriticalIoPool().execute(() -> load(gen, spec, title, artist, album));
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

	private static void load(int gen, String spec, String title, String artist, String album) {
		try {
			byte[] bytes = readSpec(spec);
			if (!looksLikeImage(bytes)) {
				TrackLookup.Hit hit = TrackLookup.resolve(title, artist, album);
				bytes = readSpec(hit.cover());
			}
			if (!looksLikeImage(bytes)) {
				return;
			}
			NativeImage image;
			try {
				image = decode(bytes);
			} catch (Exception exception) {
				Voidmark.LOGGER.debug("Cover art decode failed", exception);
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
					Voidmark.LOGGER.debug("Cover art register failed", exception);
				}
			});
		} catch (Exception exception) {
			Voidmark.LOGGER.debug("Cover art load failed", exception);
		}
	}

	private static NativeImage decode(byte[] bytes) throws Exception {
		try {
			return NativeImage.read(bytes);
		} catch (Exception ignored) {
		}
		BufferedImage buf = ImageIO.read(new ByteArrayInputStream(bytes));
		if (buf == null) {
			throw new IllegalStateException("unreadable image");
		}
		BufferedImage argb = new BufferedImage(buf.getWidth(), buf.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = argb.createGraphics();
		graphics.drawImage(buf, 0, 0, null);
		graphics.dispose();
		ByteArrayOutputStream png = new ByteArrayOutputStream();
		if (!ImageIO.write(argb, "png", png)) {
			throw new IllegalStateException("png encode failed");
		}
		return NativeImage.read(png.toByteArray());
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

	private static byte[] readSpec(String spec) {
		if (spec == null || spec.isBlank()) {
			return new byte[0];
		}
		try {
			return fetch(spec);
		} catch (Exception exception) {
			return new byte[0];
		}
	}

	private static boolean looksLikeImage(byte[] bytes) {
		if (bytes == null || bytes.length < 24) {
			return false;
		}
		int a = bytes[0] & 0xFF;
		int b = bytes[1] & 0xFF;
		return (a == 0xFF && b == 0xD8)
			|| (a == 0x89 && b == 0x50)
			|| (a == 0x47 && b == 0x49)
			|| (a == 0x42 && b == 0x4D)
			|| (a == 0x52 && b == 0x49);
	}

	private static byte[] fetch(String spec) throws Exception {
		String value = expand(spec.trim());
		if (value.startsWith("//")) {
			value = "https:" + value;
		}
		if (value.startsWith("data:image")) {
			return decodeDataUrl(value);
		}
		if (value.startsWith("http://") || value.startsWith("https://")) {
			return getBytes(value);
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

	private static byte[] getBytes(String url) throws Exception {
		HttpResponse<byte[]> response = HTTP.send(imageRequest(url), HttpResponse.BodyHandlers.ofByteArray());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IllegalStateException("HTTP " + response.statusCode());
		}
		byte[] body = response.body();
		if (body.length > MAX_BYTES) {
			throw new IllegalStateException("Cover too large");
		}
		return body;
	}

	private static HttpRequest imageRequest(String url) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(8))
			.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
			.GET();
		if (url.contains("googleusercontent") || url.contains("ytimg") || url.contains("ggpht")) {
			builder.header("Referer", "https://music.youtube.com/");
		}
		return builder.build();
	}

	private static byte[] decodeDataUrl(String value) {
		int comma = value.indexOf(',');
		if (comma < 0 || comma + 1 >= value.length()) {
			throw new IllegalArgumentException("Bad data URL");
		}
		byte[] bytes = Base64.getDecoder().decode(value.substring(comma + 1).replace("\n", "").replace("\r", ""));
		if (bytes.length > MAX_BYTES) {
			throw new IllegalArgumentException("Cover too large");
		}
		return bytes;
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
			} else if (spec.startsWith("http://") || spec.startsWith("https://") || spec.startsWith("//") || spec.startsWith("data:")) {
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
