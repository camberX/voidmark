package dev.voidmark.client.item;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import dev.voidmark.client.location.SkyblockLocation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ItemIds {
	public enum Kind {
		EMPTY,
		VANILLA,
		SKYBLOCK,
		UNKNOWN
	}

	public record Preview(ItemStack stack, String canonical, Kind kind, String title) {
		public static Preview empty() {
			return new Preview(ItemStack.EMPTY, "", Kind.EMPTY, "Nothing");
		}

		public static Preview unknown(String canonical) {
			return new Preview(ItemStack.EMPTY, canonical, Kind.UNKNOWN, "Unknown id");
		}
	}

	private static final Map<String, ItemStack> SEEN = new HashMap<>();

	private ItemIds() {
	}

	public static boolean componentsReady() {
		try {
			Item paper = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("paper"));
			return paper != null && !new ItemStack(paper).isEmpty();
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	public static ItemStack held(Player player) {
		if (player == null) {
			return ItemStack.EMPTY;
		}
		ItemStack main = player.getMainHandItem();
		if (main != null && !main.isEmpty()) {
			return main;
		}
		ItemStack off = player.getOffhandItem();
		return off == null ? ItemStack.EMPTY : off;
	}

	public static void rememberInventory(Player player) {
		if (player == null) {
			return;
		}
		remember(player.getMainHandItem());
		remember(player.getOffhandItem());
		Inventory inventory = player.getInventory();
		int size = inventory.getContainerSize();
		for (int i = 0; i < size; i++) {
			remember(inventory.getItem(i));
		}
	}

	public static void remember(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		String id = skyblockId(stack);
		if (id != null) {
			SEEN.put(id, stack.copy());
		}
	}

	public static String idOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		String skyblock = skyblockId(stack);
		if (skyblock != null) {
			return "sb:" + skyblock;
		}
		Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return key == null ? "minecraft:air" : key.toString();
	}

	public static String uuidOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) {
			return null;
		}
		CompoundTag tag = data.copyTag();
		String uuid = readUuid(tag);
		if (uuid != null) {
			return uuid;
		}
		uuid = readUuid(tag.getCompoundOrEmpty("ExtraAttributes"));
		if (uuid != null) {
			return uuid;
		}
		return readUuid(tag.getCompoundOrEmpty("PublicBukkitValues"));
	}

	public static String skyblockId(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) {
			return null;
		}
		CompoundTag tag = data.copyTag();
		String id = readId(tag);
		if (id != null) {
			return id;
		}
		id = readId(tag.getCompoundOrEmpty("ExtraAttributes"));
		if (id != null) {
			return id;
		}
		return readId(tag.getCompoundOrEmpty("PublicBukkitValues"));
	}

	public static Preview resolve(String query) {
		String raw = query == null ? "" : query.trim();
		if (raw.isEmpty()) {
			return Preview.empty();
		}
		String lower = raw.toLowerCase(Locale.ROOT);
		if (lower.startsWith("sb:") || lower.startsWith("skyblock:")) {
			String id = raw.substring(lower.startsWith("sb:") ? 3 : 9).trim();
			return skyblock(id);
		}
		if (looksLikeSkyblockId(raw) && SkyblockItems.has(raw)) {
			return skyblock(raw);
		}
		return vanilla(raw);
	}

	public static List<String> suggest(String query, int limit) {
		String raw = query == null ? "" : query.trim();
		if (raw.isEmpty()) {
			return List.of();
		}
		String lower = raw.toLowerCase(Locale.ROOT);
		if (lower.startsWith("sb:") || lower.startsWith("skyblock:")) {
			String prefix = raw.substring(lower.startsWith("sb:") ? 3 : 9).trim();
			if (prefix.length() < 1) {
				return List.of();
			}
			List<String> out = new ArrayList<>();
			for (String id : SkyblockItems.suggest(prefix, limit)) {
				out.add("sb:" + id);
			}
			return out;
		}
		String path = lower.startsWith("minecraft:") ? lower.substring(10) : lower;
		if (path.length() < 2) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
			String full = id.toString();
			if (full.equals("minecraft:air")) {
				continue;
			}
			if (full.startsWith("minecraft:" + path) || id.getPath().startsWith(path)) {
				out.add(full);
			}
		}
		out.sort(String::compareTo);
		if (out.size() > limit) {
			return new ArrayList<>(out.subList(0, limit));
		}
		return out;
	}

	private static Preview vanilla(String raw) {
		String path = raw.trim();
		if (path.toLowerCase(Locale.ROOT).startsWith("minecraft:")) {
			path = path.substring(10);
		}
		path = path.toLowerCase(Locale.ROOT).replace(' ', '_');
		if (path.isEmpty() || path.equals("air")) {
			return Preview.empty();
		}
		Identifier id = Identifier.tryBuild("minecraft", path);
		if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
			return Preview.unknown("minecraft:" + path);
		}
		Item item = BuiltInRegistries.ITEM.getValue(id);
		try {
			ItemStack stack = new ItemStack(item);
			return new Preview(stack, id.toString(), Kind.VANILLA, stack.getHoverName().getString());
		} catch (RuntimeException exception) {
			return Preview.unknown(id.toString());
		}
	}

	private static Preview skyblock(String raw) {
		String id = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
		if (id.isEmpty()) {
			return Preview.unknown("sb:");
		}
		String canonical = "sb:" + id;
		ItemStack seen = SEEN.get(id);
		if (seen != null && !seen.isEmpty()) {
			return new Preview(seen.copy(), canonical, Kind.SKYBLOCK, seen.getHoverName().getString());
		}
		SkyblockItems.Entry entry = SkyblockItems.get(id);
		if (entry == null) {
			return Preview.unknown(canonical);
		}
		ItemStack catalog;
		try {
			catalog = fromCatalog(entry);
		} catch (RuntimeException ignored) {
			return Preview.unknown(canonical);
		}
		if (catalog == null || catalog.isEmpty()) {
			return Preview.unknown(canonical);
		}
		return new Preview(catalog, canonical, Kind.SKYBLOCK, entry.name());
	}

	private static ItemStack fromCatalog(SkyblockItems.Entry entry) {
		Identifier itemId = entry.item();
		Item item = BuiltInRegistries.ITEM.containsKey(itemId)
			? BuiltInRegistries.ITEM.getValue(itemId)
			: BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("paper"));
		ItemStack stack;
		try {
			stack = new ItemStack(item);
			CompoundTag tag = new CompoundTag();
			tag.putString("id", entry.id());
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			stack.set(DataComponents.CUSTOM_NAME, Component.literal(entry.name()).withStyle(tierColor(entry.rarity())));
			Rarity rarity = vanillaRarity(entry.rarity());
			if (rarity != Rarity.COMMON) {
				stack.set(DataComponents.RARITY, rarity);
			}
			if (entry.dyeRgb() >= 0) {
				stack.set(DataComponents.DYED_COLOR, new DyedItemColor(entry.dyeRgb()));
			}
			if (entry.model() != null && shouldApplyModel(entry.model())) {
				stack.set(DataComponents.ITEM_MODEL, entry.model());
			}
			if (entry.skinHash() != null && !entry.skinHash().isBlank()) {
				applySkull(stack, entry.id(), entry.skinHash());
			}
			return stack;
		} catch (RuntimeException exception) {
			return ItemStack.EMPTY;
		}
	}

	private static boolean shouldApplyModel(Identifier model) {
		if ("minecraft".equals(model.getNamespace())) {
			return true;
		}
		return SkyblockLocation.onHypixel;
	}

	private static void applySkull(ItemStack stack, String id, String hash) {
		String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + hash + "\"}}}";
		String value = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
		PropertyMap properties = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", value)));
		UUID uuid = UUID.nameUUIDFromBytes(("sb:" + id).getBytes(StandardCharsets.UTF_8));
		GameProfile profile = new GameProfile(uuid, "Voidmark", properties);
		stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
	}

	private static ChatFormatting tierColor(String rarity) {
		if (rarity == null) {
			return ChatFormatting.WHITE;
		}
		return switch (rarity.toUpperCase(Locale.ROOT)) {
			case "UNCOMMON" -> ChatFormatting.GREEN;
			case "RARE" -> ChatFormatting.BLUE;
			case "EPIC" -> ChatFormatting.DARK_PURPLE;
			case "LEGENDARY" -> ChatFormatting.GOLD;
			case "MYTHIC" -> ChatFormatting.LIGHT_PURPLE;
			case "DIVINE" -> ChatFormatting.AQUA;
			case "SPECIAL", "VERY_SPECIAL" -> ChatFormatting.RED;
			case "ULTIMATE" -> ChatFormatting.DARK_RED;
			default -> ChatFormatting.WHITE;
		};
	}

	private static Rarity vanillaRarity(String rarity) {
		if (rarity == null) {
			return Rarity.COMMON;
		}
		return switch (rarity.toUpperCase(Locale.ROOT)) {
			case "UNCOMMON" -> Rarity.UNCOMMON;
			case "RARE" -> Rarity.RARE;
			case "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY_SPECIAL", "ULTIMATE" -> Rarity.EPIC;
			default -> Rarity.COMMON;
		};
	}

	private static boolean looksLikeSkyblockId(String raw) {
		if (raw.contains(":")) {
			return false;
		}
		int letters = 0;
		int upper = 0;
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c >= 'A' && c <= 'Z') {
				letters++;
				upper++;
			} else if (c >= 'a' && c <= 'z') {
				letters++;
			} else if (c != '_' && c != '-' && (c < '0' || c > '9')) {
				return false;
			}
		}
		return letters > 0 && upper * 2 >= letters;
	}

	private static String readId(CompoundTag tag) {
		if (tag == null || tag.isEmpty()) {
			return null;
		}
		String id = tag.getStringOr("id", "");
		if (!id.isBlank()) {
			return id.trim().toUpperCase(Locale.ROOT);
		}
		Tag value = tag.get("id");
		if (value != null) {
			String text = value.asString().orElse("").trim();
			if (!text.isEmpty()) {
				return text.toUpperCase(Locale.ROOT);
			}
		}
		return null;
	}

	private static String readUuid(CompoundTag tag) {
		if (tag == null || tag.isEmpty()) {
			return null;
		}
		for (String key : new String[]{"uuid", "UUID", "uid"}) {
			String value = tag.getStringOr(key, "");
			if (!value.isBlank()) {
				return value.trim();
			}
		}
		return null;
	}
}
