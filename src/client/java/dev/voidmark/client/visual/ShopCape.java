package dev.voidmark.client.visual;

import com.mojang.blaze3d.platform.NativeImage;
import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared cape shop. Everyone with Voidmark fetches {@code /capes/{uuid}.png}.
 * Changing a cape in the menu uploads it so other clients pick it up.
 */
public final class ShopCape {
	private static final int MAX_BYTES = 2 * 1024 * 1024;
	private static final long MISS_MS = 3_000L;
	private static final long READY_MS = 2_000L;
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(8))
		.build();
	private static final Map<UUID, Slot> SLOTS = new ConcurrentHashMap<>();
	private static volatile String publishStatus = "";
	private static int publishGen;

	private ShopCape() {
	}

	public static void tick() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || shopUrl().isEmpty()) {
			return;
		}
		for (AbstractClientPlayer player : client.level.players()) {
			ensure(player.getUUID(), false);
		}
	}

	public static String publishStatus() {
		return publishStatus;
	}

	public static PlayerSkin patch(UUID uuid, PlayerSkin skin) {
		if (skin == null || uuid == null) {
			return skin;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && uuid.equals(client.player.getUUID()) && CustomCape.ready()) {
			return CustomCape.patch(skin);
		}
		Slot slot = SLOTS.get(uuid);
		if (slot == null || slot.kind != Kind.READY || slot.asset == null) {
			ensure(uuid, false);
			return skin;
		}
		return new PlayerSkin(skin.body(), slot.asset, skin.elytra(), skin.model(), skin.secure());
	}

	public static boolean showing(UUID uuid) {
		if (uuid == null) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && uuid.equals(client.player.getUUID()) && CustomCape.ready()) {
			return true;
		}
		Slot slot = SLOTS.get(uuid);
		return slot != null && slot.kind == Kind.READY && slot.asset != null;
	}

	public static void publish(byte[] png) {
		UUID uuid = selfUuid();
		String key = shopKey();
		String base = shopUrl();
		if (uuid == null || key.isEmpty() || base.isEmpty() || png == null || png.length < 24) {
			publishStatus = key.isEmpty() ? "Set a shop key to sync this cape to everyone." : "";
			return;
		}
		int gen = ++publishGen;
		publishStatus = "Publishing cape…";
		Util.nonCriticalIoPool().execute(() -> {
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/cape"))
					.timeout(Duration.ofSeconds(12))
					.header("User-Agent", "Voidmark")
					.header("X-UUID", uuid.toString())
					.header("X-Key", key)
					.PUT(HttpRequest.BodyPublishers.ofByteArray(png))
					.build();
				HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
				Minecraft.getInstance().execute(() -> {
					if (gen != publishGen) {
						return;
					}
					if (response.statusCode() < 200 || response.statusCode() >= 300) {
						publishStatus = publishError(response.body(), response.statusCode());
						return;
					}
					String token = jsonField(response.body(), "token");
					if (!token.isBlank()) {
						VoidmarkConfig config = VoidmarkConfig.get();
						config.capeShopKey = token;
						config.save();
					}
					publishStatus = "Cape is live for everyone.";
					ensure(uuid, true);
				});
			} catch (Exception exception) {
				Minecraft.getInstance().execute(() -> {
					if (gen == publishGen) {
						publishStatus = "Publish failed";
					}
				});
			}
		});
	}

	public static void unpublish() {
		UUID uuid = selfUuid();
		String key = shopKey();
		String base = shopUrl();
		if (uuid == null || key.isEmpty() || base.isEmpty()) {
			return;
		}
		int gen = ++publishGen;
		publishStatus = "Removing shop cape…";
		Util.nonCriticalIoPool().execute(() -> {
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/cape"))
					.timeout(Duration.ofSeconds(12))
					.header("User-Agent", "Voidmark")
					.header("X-UUID", uuid.toString())
					.header("X-Key", key)
					.DELETE()
					.build();
				HTTP.send(request, HttpResponse.BodyHandlers.discarding());
				Minecraft.getInstance().execute(() -> {
					if (gen != publishGen) {
						return;
					}
					SLOTS.remove(uuid);
					publishStatus = "Shop cape removed.";
				});
			} catch (Exception exception) {
				Minecraft.getInstance().execute(() -> {
					if (gen == publishGen) {
						publishStatus = "Could not remove shop cape";
					}
				});
			}
		});
	}

	private static void ensure(UUID uuid, boolean force) {
		if (uuid == null || shopUrl().isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		Slot slot = SLOTS.computeIfAbsent(uuid, id -> new Slot());
		synchronized (slot) {
			if (slot.kind == Kind.LOADING) {
				return;
			}
			if (!force && slot.nextCheck > now) {
				return;
			}
			slot.kind = Kind.LOADING;
		}
		Util.nonCriticalIoPool().execute(() -> download(uuid, slot, force));
	}

	private static void download(UUID uuid, Slot slot, boolean force) {
		try {
			String base = shopUrl();
			if (base.isEmpty()) {
				miss(slot);
				return;
			}
			HttpRequest meta = HttpRequest.newBuilder(URI.create(base + "/api/cape/" + uuid))
				.timeout(Duration.ofSeconds(8))
				.header("User-Agent", "Voidmark")
				.GET()
				.build();
			HttpResponse<String> status = HTTP.send(meta, HttpResponse.BodyHandlers.ofString());
			if (status.statusCode() != 200) {
				miss(slot);
				return;
			}
			boolean has = status.body().contains("\"has\":true") || status.body().contains("\"has\": true");
			String hash = jsonField(status.body(), "hash");
			if (!has) {
				miss(slot);
				return;
			}
			if (!force && !hash.isBlank() && hash.equals(slot.hash) && slot.asset != null) {
				slot.kind = Kind.READY;
				slot.nextCheck = System.currentTimeMillis() + READY_MS;
				return;
			}
			HttpRequest png = HttpRequest.newBuilder(URI.create(base + "/capes/" + uuid + ".png"))
				.timeout(Duration.ofSeconds(10))
				.header("User-Agent", "Voidmark")
				.GET()
				.build();
			HttpResponse<byte[]> response = HTTP.send(png, HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() != 200 || !isPng(response.body())) {
				miss(slot);
				return;
			}
			register(uuid, slot, response.body(), hash);
		} catch (Exception exception) {
			miss(slot);
		}
	}

	private static void register(UUID uuid, Slot slot, byte[] bytes, String hash) {
		NativeImage image;
		try {
			image = NativeImage.read(bytes);
		} catch (Exception exception) {
			miss(slot);
			return;
		}
		NativeImage atlas;
		try {
			atlas = CapeAtlas.toAtlas(image);
		} catch (Exception exception) {
			miss(slot);
			return;
		}
		String safeHash = hash == null || hash.isBlank() ? Long.toHexString(System.currentTimeMillis()) : hash;
		Minecraft.getInstance().execute(() -> {
			try {
				Identifier id = Voidmark.id("shop_cape/" + uuid.toString().replace("-", "") + "_" + safeHash);
				DynamicTexture texture = new DynamicTexture(() -> "voidmark-shop-cape-" + uuid, atlas);
				Minecraft.getInstance().getTextureManager().register(id, texture);
				slot.asset = new ClientAsset.ResourceTexture(id, id);
				slot.hash = hash == null ? "" : hash;
				slot.kind = Kind.READY;
				slot.nextCheck = System.currentTimeMillis() + READY_MS;
			} catch (Exception exception) {
				atlas.close();
				miss(slot);
			}
		});
	}

	private static void miss(Slot slot) {
		slot.kind = Kind.MISS;
		slot.asset = null;
		slot.hash = "";
		slot.nextCheck = System.currentTimeMillis() + MISS_MS;
	}

	private static boolean isPng(byte[] bytes) {
		return bytes != null && bytes.length >= 24 && bytes.length <= MAX_BYTES
			&& (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47;
	}

	private static String shopUrl() {
		String url = VoidmarkConfig.get().capeServerUrl;
		if (url == null) {
			return "";
		}
		return url.endsWith("/") ? url.substring(0, url.length() - 1).trim() : url.trim();
	}

	private static String shopKey() {
		String key = VoidmarkConfig.get().capeShopKey;
		return key == null ? "" : key.trim();
	}

	private static UUID selfUuid() {
		Minecraft client = Minecraft.getInstance();
		return client.player == null ? null : client.player.getUUID();
	}

	private static String jsonField(String body, String field) {
		if (body == null) {
			return "";
		}
		String needle = "\"" + field + "\"";
		int at = body.indexOf(needle);
		if (at < 0) {
			return "";
		}
		int colon = body.indexOf(':', at + needle.length());
		int start = body.indexOf('"', colon + 1);
		if (colon < 0 || start < 0) {
			return "";
		}
		int end = body.indexOf('"', start + 1);
		return end < 0 ? "" : body.substring(start + 1, end);
	}

	private static String publishError(String body, int status) {
		String message = jsonField(body, "error");
		return message.isBlank() ? "Publish HTTP " + status : message;
	}

	private enum Kind {
		LOADING, READY, MISS
	}

	private static final class Slot {
		Kind kind = Kind.MISS;
		ClientAsset.Texture asset;
		String hash = "";
		long nextCheck;
	}
}
