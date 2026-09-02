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
 * Shared cape shop. Everyone with Voidmark fetches {@code /capes/{uuid}.png}
 * and head tags from the same request. Both load once per player, then again
 * when you join a server or a singleplayer world.
 */
public final class ShopCape {
	private static final int MAX_BYTES = 2 * 1024 * 1024;
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(8))
		.build();
	private static final Map<UUID, Slot> SLOTS = new ConcurrentHashMap<>();
	private static volatile String publishStatus = "";
	private static volatile Boolean allowed;
	private static volatile boolean pendingJoin;
	private static int publishGen;

	private ShopCape() {
	}

	public static void onJoin() {
		pendingJoin = true;
	}

	public static void tick() {
		if (shopUrl().isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (pendingJoin && client.player != null && client.level != null) {
			pendingJoin = false;
			for (Slot slot : SLOTS.values()) {
				slot.fetched = false;
			}
			for (AbstractClientPlayer player : client.level.players()) {
				ensure(player.getUUID(), true);
			}
		}
		UUID self = selfUuid();
		if (self != null) {
			ensure(self, false);
		}
	}

	public static String publishStatus() {
		return publishStatus;
	}

	public static boolean allowed() {
		return Boolean.TRUE.equals(allowed);
	}

	public static String lockLabel() {
		if (shopUrl().isEmpty()) {
			return "No cape server";
		}
		if (allowed == null) {
			return "Checking cape list…";
		}
		return "uuid not whitelisted";
	}

	public static String tag(UUID uuid) {
		if (uuid == null) {
			return "";
		}
		Slot slot = SLOTS.get(uuid);
		if (slot == null) {
			ensure(uuid, false);
			return "";
		}
		return slot.tag == null ? "" : slot.tag;
	}

	public static boolean hasTag(UUID uuid) {
		return !tag(uuid).isBlank();
	}

	private static void setAllowed(boolean listed) {
		boolean was = Boolean.TRUE.equals(allowed);
		allowed = listed;
		if (listed && !was && CustomCape.ready()) {
			publish(CustomCape.png());
		}
	}

	public static PlayerSkin patch(UUID uuid, PlayerSkin skin) {
		if (skin == null || uuid == null) {
			return skin;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && uuid.equals(client.player.getUUID()) && CustomCape.ready() && allowed()) {
			return CustomCape.patch(skin);
		}
		Slot slot = SLOTS.get(uuid);
		if (slot != null && slot.asset != null) {
			return new PlayerSkin(skin.body(), slot.asset, skin.elytra(), skin.model(), skin.secure());
		}
		ensure(uuid, false);
		return skin;
	}

	public static boolean showing(UUID uuid) {
		if (uuid == null) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && uuid.equals(client.player.getUUID()) && CustomCape.ready() && allowed()) {
			return true;
		}
		Slot slot = SLOTS.get(uuid);
		return slot != null && slot.asset != null;
	}

	public static void publish(byte[] png) {
		UUID uuid = selfUuid();
		String base = shopUrl();
		if (uuid == null || base.isEmpty() || png == null || png.length < 24) {
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
		String base = shopUrl();
		if (uuid == null || base.isEmpty()) {
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
		Slot slot = SLOTS.computeIfAbsent(uuid, id -> new Slot());
		synchronized (slot) {
			if (slot.kind == Kind.LOADING) {
				return;
			}
			if (!force && slot.fetched) {
				return;
			}
			slot.kind = Kind.LOADING;
		}
		Util.nonCriticalIoPool().execute(() -> download(uuid, slot));
	}

	private static void download(UUID uuid, Slot slot) {
		try {
			String base = shopUrl();
			if (base.isEmpty()) {
				failKeep(slot);
				return;
			}
			HttpRequest meta = HttpRequest.newBuilder(URI.create(base + "/api/cape/" + uuid))
				.timeout(Duration.ofSeconds(8))
				.header("User-Agent", "Voidmark")
				.GET()
				.build();
			HttpResponse<String> status = HTTP.send(meta, HttpResponse.BodyHandlers.ofString());
			if (status.statusCode() != 200) {
				failKeep(slot);
				return;
			}
			boolean has = status.body().contains("\"has\":true") || status.body().contains("\"has\": true");
			String hash = jsonField(status.body(), "hash");
			String tag = jsonField(status.body(), "tag");
			boolean listed = status.body().contains("\"allowed\":true") || status.body().contains("\"allowed\": true");
			slot.tag = tag;
			if (uuid.equals(selfUuid())) {
				boolean listedCopy = listed;
				Minecraft.getInstance().execute(() -> setAllowed(listedCopy));
			}
			if (!has) {
				missCape(slot);
				return;
			}
			if (!hash.isBlank() && hash.equals(slot.hash) && slot.asset != null) {
				done(slot, Kind.READY);
				return;
			}
			HttpRequest png = HttpRequest.newBuilder(URI.create(base + "/capes/" + uuid + ".png"))
				.timeout(Duration.ofSeconds(10))
				.header("User-Agent", "Voidmark")
				.GET()
				.build();
			HttpResponse<byte[]> response = HTTP.send(png, HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() != 200 || !isPng(response.body())) {
				failKeep(slot);
				return;
			}
			register(uuid, slot, response.body(), hash);
		} catch (Exception exception) {
			failKeep(slot);
		}
	}

	private static void register(UUID uuid, Slot slot, byte[] bytes, String hash) {
		NativeImage image;
		try {
			image = NativeImage.read(bytes);
		} catch (Exception exception) {
			failKeep(slot);
			return;
		}
		NativeImage atlas;
		try {
			atlas = CapeAtlas.toAtlas(image);
		} catch (Exception exception) {
			failKeep(slot);
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
				done(slot, Kind.READY);
			} catch (Exception exception) {
				atlas.close();
				failKeep(slot);
			}
		});
	}

	private static void missCape(Slot slot) {
		slot.asset = null;
		slot.hash = "";
		done(slot, Kind.MISS);
	}

	private static void failKeep(Slot slot) {
		done(slot, slot.asset != null ? Kind.READY : Kind.MISS);
	}

	private static void done(Slot slot, Kind kind) {
		synchronized (slot) {
			slot.kind = kind;
			slot.fetched = true;
		}
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

	private static UUID selfUuid() {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			return client.player.getUUID();
		}
		try {
			return client.getGameProfile().id();
		} catch (Exception ignored) {
			return null;
		}
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
		String tag = "";
		boolean fetched;
	}
}
