package dev.voidmark.client.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmark.Voidmark;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pulls Ender Chest and backpack inventories from
 * {@code https://hypixel.odtheking.com/get/(uuid)}. Live inventory stays
 * client-side so a profile refresh cannot wipe what you are holding.
 */
public final class SkyblockProfileApi {
	private static final String ENDPOINT = "https://hypixel.odtheking.com/get/";
	private static final long REFRESH_MS = 90_000L;
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(10))
		.build();

	public enum Status {
		IDLE, LOADING, READY, ERROR
	}

	private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean(false);
	private static volatile UUID tracked;
	private static volatile long lastFetchMs;
	private static volatile boolean force;
	private static volatile Status status = Status.IDLE;
	private static volatile String error = "";

	private SkyblockProfileApi() {
	}

	public static Status status() {
		return status;
	}

	public static String error() {
		return error == null ? "" : error;
	}

	public static void refresh() {
		force = true;
		lastFetchMs = 0L;
	}

	public static void tick(Minecraft client) {
		if (client == null || !RawmatsTracker.tracking()) {
			return;
		}
		UUID uuid = uuidOf(client);
		if (uuid == null) {
			return;
		}
		if (tracked != null && !tracked.equals(uuid)) {
			lastFetchMs = 0L;
		}
		tracked = uuid;
		long now = System.currentTimeMillis();
		if (!force && ItemStorage.hasApiStorage() && now - lastFetchMs < REFRESH_MS) {
			return;
		}
		if (!IN_FLIGHT.compareAndSet(false, true)) {
			return;
		}
		force = false;
		status = Status.LOADING;
		error = "";
		UUID fetchId = uuid;
		Util.nonCriticalIoPool().execute(() -> fetch(fetchId));
	}

	private static void fetch(UUID uuid) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT + uuid))
				.timeout(Duration.ofSeconds(20))
				.header("User-Agent", "Voidmark/" + Voidmark.MOD_ID)
				.GET()
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				fail("HTTP " + response.statusCode());
				return;
			}
			JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
			if (root.has("success") && root.get("success").isJsonPrimitive() && !root.get("success").getAsBoolean()) {
				fail("profile lookup failed");
				return;
			}
			JsonObject member = memberOf(root, uuid);
			if (member == null) {
				fail("no Skyblock profile");
				return;
			}
			if (!applyMember(member)) {
				return;
			}
			status = Status.READY;
			error = "";
			lastFetchMs = System.currentTimeMillis();
		} catch (Exception exception) {
			fail(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
			Voidmark.LOGGER.warn("Skyblock storage lookup failed", exception);
		} finally {
			IN_FLIGHT.set(false);
		}
	}

	private static boolean applyMember(JsonObject member) {
		JsonObject inventory = object(member, "inventory");
		if (inventory == null) {
			fail("inventory hidden");
			return false;
		}
		boolean hasEnder = inventory.has("ender_chest_contents");
		boolean hasBags = inventory.has("backpack_contents");
		Map<String, Long> ender = new HashMap<>();
		Map<String, Long> bags = new HashMap<>();
		if (hasEnder) {
			countData(data(inventory.get("ender_chest_contents")), ender, false);
		}
		if (hasBags) {
			JsonObject backpacks = object(inventory, "backpack_contents");
			if (backpacks != null) {
				for (Map.Entry<String, JsonElement> entry : backpacks.entrySet()) {
					countData(data(entry.getValue()), bags, true);
				}
			}
		}
		ItemStorage.applyApi(hasEnder ? ender : null, hasBags ? bags : null);
		return true;
	}

	private static void countData(String data, Map<String, Long> out, boolean nestStorage) {
		if (data == null || data.isBlank()) {
			return;
		}
		CompoundTag tag = readNbt(data);
		if (tag != null) {
			ItemStorage.addCounts(tag, out, nestStorage);
		}
	}

	private static CompoundTag readNbt(String data) {
		try {
			byte[] raw = Base64.getDecoder().decode(data);
			try (ByteArrayInputStream in = new ByteArrayInputStream(raw)) {
				return NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
			}
		} catch (Exception ignored) {
			try {
				byte[] raw = Base64.getMimeDecoder().decode(data);
				try (ByteArrayInputStream in = new ByteArrayInputStream(raw)) {
					return NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
				}
			} catch (Exception exception) {
				Voidmark.LOGGER.warn("Could not decode Skyblock storage NBT");
				return null;
			}
		}
	}

	private static JsonObject memberOf(JsonObject root, UUID uuid) {
		if (!root.has("profiles") || !root.get("profiles").isJsonArray()) {
			return null;
		}
		JsonObject selected = null;
		JsonObject fallback = null;
		String compact = compact(uuid);
		for (JsonElement element : root.getAsJsonArray("profiles")) {
			if (element == null || !element.isJsonObject()) {
				continue;
			}
			JsonObject profile = element.getAsJsonObject();
			JsonObject member = memberIn(profile, compact);
			if (member == null) {
				continue;
			}
			if (fallback == null) {
				fallback = member;
			}
			if (bool(profile, "selected")) {
				selected = member;
				break;
			}
		}
		return selected != null ? selected : fallback;
	}

	private static JsonObject memberIn(JsonObject profile, String compact) {
		JsonObject members = object(profile, "members");
		if (members == null) {
			return null;
		}
		JsonObject direct = object(members, compact);
		if (direct != null) {
			return direct;
		}
		for (Map.Entry<String, JsonElement> entry : members.entrySet()) {
			if (compact(entry.getKey()).equals(compact) && entry.getValue() != null && entry.getValue().isJsonObject()) {
				return entry.getValue().getAsJsonObject();
			}
		}
		return null;
	}

	private static String data(JsonElement element) {
		if (element == null || !element.isJsonObject()) {
			return "";
		}
		JsonElement value = element.getAsJsonObject().get("data");
		if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
			return "";
		}
		return value.getAsString();
	}

	private static JsonObject object(JsonObject parent, String key) {
		if (parent == null || key == null || !parent.has(key)) {
			return null;
		}
		JsonElement value = parent.get(key);
		return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
	}

	private static boolean bool(JsonObject object, String key) {
		if (object == null || !object.has(key)) {
			return false;
		}
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsBoolean();
	}

	private static String compact(UUID uuid) {
		return uuid.toString().replace("-", "").toLowerCase(Locale.ROOT);
	}

	private static String compact(String value) {
		return value == null ? "" : value.replace("-", "").toLowerCase(Locale.ROOT);
	}

	private static UUID uuidOf(Minecraft client) {
		Player player = client.player;
		if (player != null) {
			return player.getUUID();
		}
		if (client.getUser() != null) {
			return client.getUser().getProfileId();
		}
		return null;
	}

	private static void fail(String message) {
		error = message == null || message.isBlank() ? "lookup failed" : message;
		if (!ItemStorage.hasApiStorage()) {
			lastFetchMs = System.currentTimeMillis() - REFRESH_MS + 15_000L;
			status = Status.ERROR;
		} else {
			status = Status.READY;
			lastFetchMs = System.currentTimeMillis();
		}
	}
}
