package dev.voidmark.client.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.voidmark.Voidmark;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
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
import java.util.zip.GZIPInputStream;

/**
 * Pulls Ender Chest and backpack inventories from
 * {@code https://hypixel.odtheking.com/get/(uuid)}. Fetches when you join a
 * server and when {@code /vm rawmats} runs. Live inventory stays client-side
 * so a profile refresh cannot wipe what you are holding.
 */
public final class SkyblockProfileApi {
	private static final String ENDPOINT = "https://hypixel.odtheking.com/get/";
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
		Minecraft client = Minecraft.getInstance();
		UUID uuid = uuidOf(client);
		if (uuid != null) {
			startFetch(uuid);
		}
	}

	public static void tick(Minecraft client) {
		if (client == null || !force) {
			return;
		}
		UUID uuid = uuidOf(client);
		if (uuid == null) {
			return;
		}
		startFetch(uuid);
	}

	private static void startFetch(UUID uuid) {
		if (uuid == null || !IN_FLIGHT.compareAndSet(false, true)) {
			return;
		}
		force = false;
		status = Status.LOADING;
		error = "";
		tracked = uuid;
		Util.nonCriticalIoPool().execute(() -> fetch(uuid));
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
		Map<String, Long> ender = new HashMap<>();
		Map<String, Long> bags = new HashMap<>();
		JsonObject inventory = object(member, "inventory");
		boolean sawEnder = ingestStorage(inventory, ender, true);
		boolean sawBags = ingestStorage(inventory, bags, false);
		if (!sawEnder) {
			sawEnder = ingestStorage(member, ender, true);
		}
		if (!sawBags) {
			sawBags = ingestStorage(member, bags, false);
		}
		sawEnder |= ingestBlob(inventory, ender, bags, true);
		sawBags |= ingestBlob(inventory, ender, bags, false);
		sawEnder |= ingestBlob(member, ender, bags, true);
		sawBags |= ingestBlob(member, ender, bags, false);
		JsonElement invEl = member.get("inventory");
		if (invEl != null && invEl.isJsonPrimitive()) {
			CompoundTag tag = readNbt(data(invEl));
			if (tag != null) {
				boolean[] found = new boolean[2];
				walkStorageNbt(tag, ender, bags, found);
				sawEnder |= found[0];
				sawBags |= found[1];
			}
		}
		if (!sawEnder && !sawBags) {
			fail("inventory hidden");
			return false;
		}
		ItemStorage.applyApi(sawEnder ? ender : null, sawBags ? bags : null);
		Voidmark.LOGGER.info("Skyblock storage: {} ender item ids, {} backpack item ids", ender.size(), bags.size());
		return true;
	}

	private static boolean ingestStorage(JsonObject parent, Map<String, Long> dest, boolean ender) {
		if (parent == null) {
			return false;
		}
		boolean saw = false;
		if (ender) {
			for (String key : parent.keySet()) {
				if (!enderKey(key)) {
					continue;
				}
				saw |= countPayload(parent.get(key), dest, true);
			}
		} else {
			for (String key : parent.keySet()) {
				if (!backpackKey(key)) {
					continue;
				}
				saw |= countPayload(parent.get(key), dest, true);
			}
		}
		return saw;
	}

	private static boolean ingestBlob(JsonObject parent, Map<String, Long> ender, Map<String, Long> bags, boolean wantEnder) {
		if (parent == null) {
			return false;
		}
		String blob = data(parent);
		if (blob.isEmpty()) {
			return false;
		}
		CompoundTag tag = readNbt(blob);
		if (tag == null) {
			return false;
		}
		boolean[] found = new boolean[2];
		walkStorageNbt(tag, ender, bags, found);
		return wantEnder ? found[0] : found[1];
	}

	private static void walkStorageNbt(CompoundTag tag, Map<String, Long> ender, Map<String, Long> bags, boolean[] found) {
		if (tag == null) {
			return;
		}
		for (String key : tag.keySet()) {
			if (enderKey(key)) {
				ItemStorage.addCounts(tag.get(key), ender, true);
				found[0] = true;
			} else if (backpackKey(key)) {
				ItemStorage.addCounts(tag.get(key), bags, true);
				found[1] = true;
			} else if (tag.get(key) instanceof CompoundTag child) {
				walkStorageNbt(child, ender, bags, found);
			}
		}
	}

	private static boolean countPayload(JsonElement element, Map<String, Long> dest, boolean nestStorage) {
		if (element == null || element.isJsonNull()) {
			return false;
		}
		boolean saw = false;
		String blob = data(element);
		if (!blob.isEmpty()) {
			CompoundTag tag = readNbt(blob);
			if (tag != null) {
				ItemStorage.addCounts(tag, dest, nestStorage);
				saw = true;
			}
		}
		if (element.isJsonArray()) {
			for (JsonElement child : element.getAsJsonArray()) {
				saw |= countPayload(child, dest, nestStorage);
			}
			return saw;
		}
		if (!element.isJsonObject()) {
			return saw;
		}
		JsonObject object = element.getAsJsonObject();
		if (jsonItemId(object) != null) {
			countJsonItem(object, dest);
			return true;
		}
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
			if (key.contains("icon")) {
				continue;
			}
			if ("data".equals(key) || "value".equals(key) || "type".equals(key) || "success".equals(key)) {
				continue;
			}
			saw |= countPayload(entry.getValue(), dest, nestStorage);
		}
		return saw;
	}

	private static void countJsonItem(JsonObject object, Map<String, Long> dest) {
		String id = jsonItemId(object);
		if (id == null) {
			return;
		}
		long count = jsonCount(object);
		if (count > 0L) {
			dest.merge(id, count, Long::sum);
		}
	}

	private static String jsonItemId(JsonObject object) {
		if (object == null) {
			return null;
		}
		JsonObject extra = object(object(object, "tag"), "ExtraAttributes");
		if (extra == null) {
			extra = object(object, "ExtraAttributes");
		}
		if (extra == null) {
			extra = object(object(object, "components"), "minecraft:custom_data");
		}
		if (extra != null) {
			String id = skyblockId(string(extra, "id"));
			if (id != null) {
				return id;
			}
		}
		for (String key : new String[]{"skyblock_id", "item_id", "itemId", "id", "item"}) {
			String id = skyblockId(string(object, key));
			if (id != null) {
				return id;
			}
		}
		return null;
	}

	private static long jsonCount(JsonObject object) {
		for (String key : new String[]{"Count", "count", "stackSize", "amount"}) {
			JsonElement value = object.get(key);
			if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
				return Math.max(0L, value.getAsLong());
			}
		}
		return 1L;
	}

	private static String skyblockId(String raw) {
		if (raw == null || raw.isBlank() || raw.contains(":")) {
			return null;
		}
		String id = SkyblockRecipes.normalize(raw);
		return id.isBlank() ? null : id;
	}

	private static boolean enderKey(String key) {
		if (key == null) {
			return false;
		}
		String lower = key.toLowerCase(Locale.ROOT);
		return lower.contains("ender_chest") || lower.contains("enderchest");
	}

	private static boolean backpackKey(String key) {
		if (key == null) {
			return false;
		}
		String lower = key.toLowerCase(Locale.ROOT);
		if (lower.contains("icon")) {
			return false;
		}
		return lower.contains("backpack_contents")
			|| lower.equals("backpacks")
			|| (lower.contains("backpack") && lower.contains("content"));
	}

	private static CompoundTag readNbt(String data) {
		if (data == null || data.isBlank()) {
			return null;
		}
		String trimmed = data.trim();
		if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
			try {
				return TagParser.parseCompoundFully(trimmed);
			} catch (Exception ignored) {
			}
		}
		byte[] raw = decodeBase64(trimmed);
		if (raw == null || raw.length == 0) {
			return null;
		}
		CompoundTag tag = readGzip(raw);
		if (tag != null) {
			return tag;
		}
		return readUncompressed(raw);
	}

	private static byte[] decodeBase64(String data) {
		String cleaned = data.replaceAll("\\s+", "");
		try {
			return Base64.getDecoder().decode(cleaned);
		} catch (IllegalArgumentException ignored) {
			try {
				return Base64.getUrlDecoder().decode(cleaned);
			} catch (IllegalArgumentException ignoredUrl) {
				try {
					return Base64.getMimeDecoder().decode(data);
				} catch (IllegalArgumentException failed) {
					return null;
				}
			}
		}
	}

	private static CompoundTag readGzip(byte[] raw) {
		try (ByteArrayInputStream in = new ByteArrayInputStream(raw)) {
			return NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
		} catch (Exception ignored) {
			try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(raw));
				 DataInputStream data = new DataInputStream(gzip)) {
				return NbtIo.read(data, NbtAccounter.unlimitedHeap());
			} catch (Exception exception) {
				return null;
			}
		}
	}

	private static CompoundTag readUncompressed(byte[] raw) {
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw))) {
			return NbtIo.read(in, NbtAccounter.unlimitedHeap());
		} catch (Exception ignored) {
			return null;
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
		if (element == null || element.isJsonNull()) {
			return "";
		}
		if (element.isJsonPrimitive()) {
			JsonPrimitive primitive = element.getAsJsonPrimitive();
			return primitive.isString() ? primitive.getAsString() : "";
		}
		if (!element.isJsonObject()) {
			return "";
		}
		JsonObject object = element.getAsJsonObject();
		for (String key : new String[]{"data", "value", "contents", "nbt", "bytes", "item_bytes"}) {
			JsonElement value = object.get(key);
			if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
				String text = value.getAsString();
				if (!text.isBlank()) {
					return text;
				}
			}
		}
		return "";
	}

	private static String string(JsonObject object, String key) {
		if (object == null || key == null || !object.has(key)) {
			return "";
		}
		JsonElement value = object.get(key);
		if (value == null || !value.isJsonPrimitive()) {
			return "";
		}
		JsonPrimitive primitive = value.getAsJsonPrimitive();
		return primitive.isString() ? primitive.getAsString() : primitive.getAsString();
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
		status = ItemStorage.hasApiStorage() ? Status.READY : Status.ERROR;
	}
}
