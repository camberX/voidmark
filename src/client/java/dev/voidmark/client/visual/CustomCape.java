package dev.voidmark.client.visual;

import com.mojang.blaze3d.platform.NativeImage;
import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.awt.FileDialog;
import java.awt.Frame;
import java.util.concurrent.atomic.AtomicReference;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public final class CustomCape {
	public enum Status {
		EMPTY, LOADING, READY, ERROR
	}

	private static final Identifier TEXTURE_ID = Voidmark.id("cape");
	private static final int MAX_BYTES = 2 * 1024 * 1024;
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(8))
		.build();

	private static volatile Status status = Status.EMPTY;
	private static volatile String error = "";
	private static volatile ClientAsset.Texture asset;
	private static volatile int texW;
	private static volatile int texH;
	private static int generation;

	private CustomCape() {
	}

	public static void init() {
		VoidmarkConfig config = VoidmarkConfig.get();
		if (config.capePath != null && !config.capePath.isBlank()) {
			loadPath(Path.of(config.capePath), false);
			return;
		}
		if (config.capeUrl != null && !config.capeUrl.isBlank()) {
			loadUrl(config.capeUrl, false);
		}
	}

	public static Status status() {
		return status;
	}

	public static String error() {
		return error;
	}

	public static String statusLabel() {
		return switch (status) {
			case EMPTY -> "No cape";
			case LOADING -> "Loading…";
			case READY -> "Ready";
			case ERROR -> error.isBlank() ? "Failed" : error;
		};
	}

	public static Identifier textureId() {
		return asset == null ? null : TEXTURE_ID;
	}

	public static int width() {
		return texW;
	}

	public static int height() {
		return texH;
	}

	public static boolean ready() {
		return status == Status.READY && asset != null;
	}

	public static PlayerSkin patch(PlayerSkin skin) {
		if (skin == null || !ready()) {
			return skin;
		}
		return new PlayerSkin(skin.body(), asset, skin.elytra(), skin.model(), skin.secure());
	}

	public static void applyUrl(String url) {
		loadUrl(url, true);
	}

	public static void pickLocal() {
		Minecraft client = Minecraft.getInstance();
		try {
			client.mouseHandler.releaseMouse();
		} catch (Exception ignored) {
		}
		String selected = null;
		boolean nativeOk = false;
		try {
			selected = tinyFdPick();
			nativeOk = true;
		} catch (Throwable exception) {
			Voidmark.LOGGER.warn("Native cape picker failed, trying Explorer", exception);
		}
		if (!nativeOk) {
			try {
				selected = awtPick();
			} catch (Throwable exception) {
				Voidmark.LOGGER.warn("Explorer cape picker failed", exception);
				fail("Can't open explorer");
				return;
			}
		}
		if (selected == null || selected.isBlank()) {
			return;
		}
		loadPath(Path.of(selected), true);
	}

	private static String tinyFdPick() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer filters = stack.mallocPointer(1);
			filters.put(stack.UTF8("*.png"));
			filters.flip();
			return TinyFileDialogs.tinyfd_openFileDialog(
				"Select cape texture",
				System.getProperty("user.home", ""),
				filters,
				"PNG image",
				false
			);
		}
	}

	private static String awtPick() throws Exception {
		AtomicReference<String> selected = new AtomicReference<>();
		AtomicReference<Exception> failure = new AtomicReference<>();
		Thread thread = new Thread(() -> {
			try {
				FileDialog dialog = new FileDialog((Frame) null, "Select cape texture", FileDialog.LOAD);
				dialog.setAlwaysOnTop(true);
				dialog.setFile("*.png");
				dialog.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".png"));
				dialog.setVisible(true);
				String file = dialog.getFile();
				String dir = dialog.getDirectory();
				dialog.dispose();
				if (file != null && dir != null) {
					selected.set(Path.of(dir, file).toString());
				}
			} catch (Exception exception) {
				failure.set(exception);
			}
		}, "voidmark-cape-dialog");
		thread.setDaemon(true);
		thread.start();
		thread.join();
		if (failure.get() != null) {
			throw failure.get();
		}
		return selected.get();
	}

	public static void clear() {
		generation++;
		VoidmarkConfig config = VoidmarkConfig.get();
		config.capeUrl = "";
		config.capePath = "";
		drop();
		status = Status.EMPTY;
		error = "";
	}

	private static void loadUrl(String url, boolean save) {
		String trimmed = url == null ? "" : url.trim();
		VoidmarkConfig config = VoidmarkConfig.get();
		config.capeUrl = trimmed;
		config.capePath = "";
		if (save) {
			config.save();
		}
		if (trimmed.isEmpty()) {
			drop();
			status = Status.EMPTY;
			error = "";
			return;
		}
		int gen = ++generation;
		status = Status.LOADING;
		error = "";
		Util.nonCriticalIoPool().execute(() -> {
			try {
				URI uri = URI.create(trimmed);
				String scheme = uri.getScheme();
				if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
					failOn(gen, "Need http(s) URL");
					return;
				}
				HttpRequest request = HttpRequest.newBuilder(uri)
					.timeout(Duration.ofSeconds(10))
					.header("User-Agent", "Voidmark/1.1")
					.GET()
					.build();
				HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
				if (response.statusCode() < 200 || response.statusCode() >= 300) {
					failOn(gen, "HTTP " + response.statusCode());
					return;
				}
				register(gen, response.body());
			} catch (IllegalArgumentException exception) {
				failOn(gen, "Invalid URL");
			} catch (Exception exception) {
				failOn(gen, "Download failed");
			}
		});
	}

	private static void loadPath(Path path, boolean save) {
		VoidmarkConfig config = VoidmarkConfig.get();
		config.capePath = path.toAbsolutePath().toString();
		config.capeUrl = "";
		if (save) {
			config.save();
		}
		int gen = ++generation;
		status = Status.LOADING;
		error = "";
		Util.nonCriticalIoPool().execute(() -> {
			try {
				if (!Files.isRegularFile(path)) {
					failOn(gen, "Missing file");
					return;
				}
				if (Files.size(path) > MAX_BYTES) {
					failOn(gen, "Too large");
					return;
				}
				register(gen, Files.readAllBytes(path));
			} catch (Exception exception) {
				failOn(gen, "Can't read file");
			}
		});
	}

	private static void register(int gen, byte[] bytes) {
		if (bytes.length < 8 || bytes.length > MAX_BYTES) {
			failOn(gen, "Bad size");
			return;
		}
		if ((bytes[0] & 0xFF) != 0x89 || bytes[1] != 0x50 || bytes[2] != 0x4E || bytes[3] != 0x47) {
			failOn(gen, "Not a PNG");
			return;
		}
		NativeImage image;
		try {
			image = NativeImage.read(bytes);
		} catch (Exception exception) {
			failOn(gen, "Bad PNG");
			return;
		}
		if (image.getWidth() < 16 || image.getHeight() < 16 || image.getWidth() > 1024 || image.getHeight() > 1024) {
			image.close();
			failOn(gen, "Bad dimensions");
			return;
		}
		Minecraft.getInstance().execute(() -> {
			if (gen != generation) {
				image.close();
				return;
			}
			try {
				DynamicTexture texture = new DynamicTexture(() -> "voidmark-cape", image);
				Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, texture);
				asset = new ClientAsset.ResourceTexture(TEXTURE_ID, TEXTURE_ID);
				texW = image.getWidth();
				texH = image.getHeight();
				status = Status.READY;
				error = "";
			} catch (Exception exception) {
				image.close();
				fail("Register failed");
			}
		});
	}

	private static void failOn(int gen, String message) {
		Minecraft.getInstance().execute(() -> {
			if (gen == generation) {
				fail(message);
			}
		});
	}

	private static void fail(String message) {
		drop();
		status = Status.ERROR;
		error = message;
	}

	private static void drop() {
		asset = null;
		texW = 0;
		texH = 0;
	}
}
