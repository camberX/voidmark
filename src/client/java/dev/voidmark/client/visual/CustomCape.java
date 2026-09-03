package dev.voidmark.client.visual;

import com.mojang.blaze3d.platform.NativeImage;
import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.ui.CapeCreatorScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
import java.io.InputStream;
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
	private static final int MAX_SRC = 4096;
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(8))
		.build();

	private static volatile Status status = Status.EMPTY;
	private static volatile String error = "";
	private static volatile ClientAsset.Texture asset;
	private static volatile int texW;
	private static volatile int texH;
	private static volatile int srcW;
	private static volatile int srcH;
	private static volatile boolean fitted;
	private static volatile boolean cropped;
	private static volatile byte[] lastPng = new byte[0];
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
			loadUrl(config.capeUrl, false, false);
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
			case READY -> cropped ? "Cropped " + srcW + "×" + srcH : fitted ? "Fitted " + srcW + "×" + srcH : "Template " + srcW + "×" + srcH;
			case ERROR -> error.isBlank() ? "Failed" : error;
		};
	}

	public static String layoutHint() {
		return switch (status) {
			case EMPTY -> "Any PNG. Crop photos onto the cape.";
			case LOADING -> "Downloading…";
			case READY -> cropped
				? "Cropped onto the cape from " + srcW + "×" + srcH + "."
				: fitted ? "Fitted onto the cape from " + srcW + "×" + srcH + "." : "Vanilla 64×32 cape atlas.";
			case ERROR -> "";
		};
	}

	public static int faceU() {
		return CapeAtlas.FACE_U * CapeAtlas.atlasScale(texW);
	}

	public static int faceV() {
		return CapeAtlas.FACE_V * CapeAtlas.atlasScale(texW);
	}

	public static int faceW() {
		return CapeAtlas.FACE_W * CapeAtlas.atlasScale(texW);
	}

	public static int faceH() {
		return CapeAtlas.FACE_H * CapeAtlas.atlasScale(texW);
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

	public static byte[] png() {
		return lastPng;
	}

	public static PlayerSkin patch(PlayerSkin skin) {
		if (skin == null || !ready()) {
			return skin;
		}
		return new PlayerSkin(skin.body(), asset, skin.elytra(), skin.model(), skin.secure());
	}

	public static void applyUrl(String url) {
		if (!ShopCape.allowed()) {
			fail("uuid not whitelisted");
			return;
		}
		loadUrl(url, true, true);
	}

	public static void pickLocal() {
		pickFile(false);
	}

	public static void pickCreate() {
		pickFile(true);
	}

	public static void applyCrop(byte[] source, CapeCrop crop) {
		if (!ShopCape.allowed()) {
			fail("uuid not whitelisted");
			return;
		}
		if (source == null || crop == null) {
			fail("Bad crop");
			return;
		}
		int gen = ++generation;
		status = Status.LOADING;
		error = "";
		Util.nonCriticalIoPool().execute(() -> {
			NativeImage image;
			try {
				image = NativeImage.read(source);
			} catch (Exception exception) {
				failOn(gen, "Bad image");
				return;
			}
			int originalW = image.getWidth();
			int originalH = image.getHeight();
			crop.clamp(originalW, originalH);
			NativeImage atlas;
			try {
				atlas = CapeAtlas.toAtlas(image, crop);
			} catch (Exception exception) {
				failOn(gen, "Can't convert cape");
				return;
			}
			byte[] png;
			try {
				png = encodePng(atlas);
			} catch (Exception exception) {
				atlas.close();
				failOn(gen, "Can't save cape");
				return;
			}
			install(gen, atlas, png, originalW, originalH, false, true, true);
		});
	}

	private static void pickFile(boolean create) {
		if (!ShopCape.allowed()) {
			fail("uuid not whitelisted");
			return;
		}
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
		Path path = Path.of(selected);
		if (!create) {
			loadPath(path, true);
			return;
		}
		Util.nonCriticalIoPool().execute(() -> {
			try {
				if (!Files.isRegularFile(path)) {
					failOn(generation, "Missing file");
					return;
				}
				if (Files.size(path) > MAX_BYTES) {
					failOn(generation, "Too large");
					return;
				}
				byte[] bytes = Files.readAllBytes(path);
				NativeImage image = NativeImage.read(bytes);
				int width = image.getWidth();
				int height = image.getHeight();
				boolean vanilla = CapeAtlas.isVanillaLayout(width, height);
				image.close();
				if (width < 16 || height < 16 || width > MAX_SRC || height > MAX_SRC) {
					failOn(generation, "Bad dimensions");
					return;
				}
				Minecraft.getInstance().execute(() -> {
					if (vanilla) {
						loadPath(path, true);
						return;
					}
					Screen parent = Minecraft.getInstance().screen;
					Minecraft.getInstance().setScreen(new CapeCreatorScreen(parent, bytes));
				});
			} catch (Exception exception) {
				failOn(generation, "Can't read file");
			}
		});
	}

	private static String tinyFdPick() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer filters = stack.mallocPointer(2);
			filters.put(stack.UTF8("*.png"));
			filters.put(stack.UTF8("*.jpg"));
			filters.flip();
			return TinyFileDialogs.tinyfd_openFileDialog(
				"Select cape image",
				System.getProperty("user.home", ""),
				filters,
				"PNG or JPEG",
				false
			);
		}
	}

	private static String awtPick() throws Exception {
		AtomicReference<String> selected = new AtomicReference<>();
		AtomicReference<Exception> failure = new AtomicReference<>();
		Thread thread = new Thread(() -> {
			try {
				FileDialog dialog = new FileDialog((Frame) null, "Select cape image", FileDialog.LOAD);
				dialog.setAlwaysOnTop(true);
				dialog.setFile("*.png;*.jpg;*.jpeg");
				dialog.setFilenameFilter((dir, name) -> {
					String lower = name.toLowerCase();
					return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
				});
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
		lastPng = new byte[0];
		ShopCape.unpublish();
	}

	private static void loadUrl(String url, boolean save, boolean cropIfNeeded) {
		String trimmed = url == null ? "" : url.trim();
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!cropIfNeeded) {
			config.capeUrl = trimmed;
			config.capePath = "";
			if (save) {
				config.save();
			}
		}
		if (trimmed.isEmpty()) {
			drop();
			status = Status.EMPTY;
			error = "";
			return;
		}
		int gen = ++generation;
		if (!cropIfNeeded) {
			status = Status.LOADING;
			error = "";
		}
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
				HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
				if (response.statusCode() < 200 || response.statusCode() >= 300) {
					failOn(gen, "HTTP " + response.statusCode());
					return;
				}
				byte[] body;
				try (InputStream in = response.body()) {
					body = in == null ? new byte[0] : in.readAllBytes();
				}
				if (cropIfNeeded && openCreatorIfNeeded(gen, body)) {
					return;
				}
				if (cropIfNeeded) {
					config.capeUrl = trimmed;
					config.capePath = "";
					if (save) {
						config.save();
					}
				}
				register(gen, body, save);
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
				register(gen, Files.readAllBytes(path), save);
			} catch (Exception exception) {
				failOn(gen, "Can't read file");
			}
		});
	}

	private static void register(int gen, byte[] bytes, boolean publish) {
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
		if (image.getWidth() < 16 || image.getHeight() < 16 || image.getWidth() > MAX_SRC || image.getHeight() > MAX_SRC) {
			image.close();
			failOn(gen, "Bad dimensions");
			return;
		}
		int originalW = image.getWidth();
		int originalH = image.getHeight();
		boolean needsFit = !CapeAtlas.isVanillaLayout(originalW, originalH);
		NativeImage atlas;
		try {
			atlas = CapeAtlas.toAtlas(image);
		} catch (Exception exception) {
			failOn(gen, "Can't convert cape");
			return;
		}
		install(gen, atlas, bytes, originalW, originalH, needsFit, false, publish);
	}

	private static boolean openCreatorIfNeeded(int gen, byte[] bytes) {
		if (bytes.length < 8 || bytes.length > MAX_BYTES) {
			return false;
		}
		NativeImage image;
		try {
			image = NativeImage.read(bytes);
		} catch (Exception exception) {
			return false;
		}
		int width = image.getWidth();
		int height = image.getHeight();
		boolean vanilla = CapeAtlas.isVanillaLayout(width, height);
		image.close();
		if (vanilla || width < 16 || height < 16 || width > MAX_SRC || height > MAX_SRC) {
			return false;
		}
		Minecraft.getInstance().execute(() -> {
			if (gen != generation) {
				return;
			}
			Screen parent = Minecraft.getInstance().screen;
			Minecraft.getInstance().setScreen(new CapeCreatorScreen(parent, bytes));
		});
		return true;
	}

	private static void install(
		int gen,
		NativeImage atlas,
		byte[] png,
		int originalW,
		int originalH,
		boolean fittedImage,
		boolean croppedImage,
		boolean publish
	) {
		Minecraft.getInstance().execute(() -> {
			if (gen != generation) {
				atlas.close();
				return;
			}
			try {
				if (croppedImage) {
					Path stored = FabricLoader.getInstance().getConfigDir().resolve("voidmark-cape.png");
					Files.write(stored, png);
					VoidmarkConfig config = VoidmarkConfig.get();
					config.capePath = stored.toAbsolutePath().toString();
					config.capeUrl = "";
					config.save();
				}
				DynamicTexture texture = new DynamicTexture(() -> "voidmark-cape", atlas);
				Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, texture);
				asset = new ClientAsset.ResourceTexture(TEXTURE_ID, TEXTURE_ID);
				texW = atlas.getWidth();
				texH = atlas.getHeight();
				srcW = originalW;
				srcH = originalH;
				fitted = fittedImage;
				cropped = croppedImage;
				lastPng = png;
				status = Status.READY;
				error = "";
				if (publish) {
					ShopCape.publish(png);
				}
			} catch (Exception exception) {
				atlas.close();
				fail("Register failed");
			}
		});
	}

	private static byte[] encodePng(NativeImage image) throws java.io.IOException {
		Path tmp = Files.createTempFile("voidmark-cape", ".png");
		try {
			image.writeToFile(tmp);
			return Files.readAllBytes(tmp);
		} finally {
			Files.deleteIfExists(tmp);
		}
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
		srcW = 0;
		srcH = 0;
		fitted = false;
		cropped = false;
	}
}
