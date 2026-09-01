package dev.voidmark.client.item;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Counts Skyblock items in the live inventory plus the last-seen
 * Ender Chest / backpack pages. Backpack NBT on items is read when present.
 */
public final class ItemStorage {
	private static final EquipmentSlot[] ARMOR = {
		EquipmentSlot.HEAD,
		EquipmentSlot.CHEST,
		EquipmentSlot.LEGS,
		EquipmentSlot.FEET,
		EquipmentSlot.OFFHAND
	};
	private static final String[] CONTENT_KEYS = {
		"backpack_contents",
		"greater_backpack_contents",
		"jumbo_backpack_contents",
		"container",
		"ender_chest_contents",
		"personal_compact_0",
		"personal_compact_1",
		"personal_compact_2",
		"personal_deletor_0",
		"personal_deletor_1"
	};

	private static final Map<String, Map<String, Long>> PAGES = new HashMap<>();
	private static boolean sawEnder;
	private static boolean sawBackpack;

	private ItemStorage() {
	}

	public static void tick(Minecraft client) {
		if (client == null || client.player == null) {
			return;
		}
		Screen screen = client.screen;
		if (!(screen instanceof AbstractContainerScreen<?> container)) {
			return;
		}
		String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
		String kind = kind(title);
		if (kind == null) {
			return;
		}
		AbstractContainerMenu menu = container.getMenu();
		if (menu == null) {
			return;
		}
		Map<String, Long> counts = new HashMap<>();
		int end = Math.max(0, menu.slots.size() - 36);
		for (int i = 0; i < end; i++) {
			addSlot(menu.slots.get(i), counts, 0);
		}
		String key = kind + ":" + title.trim().toLowerCase(Locale.ROOT);
		PAGES.put(key, counts);
		if ("ender".equals(kind)) {
			sawEnder = true;
		}
		if ("backpack".equals(kind)) {
			sawBackpack = true;
		}
	}

	public static void clearPages() {
		PAGES.clear();
		sawEnder = false;
		sawBackpack = false;
	}

	public static boolean sawEnder() {
		return sawEnder;
	}

	public static boolean sawBackpack() {
		return sawBackpack;
	}

	public static Map<String, Long> counts(Player player) {
		Map<String, Long> out = new HashMap<>();
		if (player == null) {
			return out;
		}
		Inventory inventory = player.getInventory();
		int size = inventory.getContainerSize();
		for (int i = 0; i < size; i++) {
			addStack(inventory.getItem(i), out, 0);
		}
		for (EquipmentSlot slot : ARMOR) {
			addStack(player.getItemBySlot(slot), out, 0);
		}
		for (Map<String, Long> page : PAGES.values()) {
			for (Map.Entry<String, Long> entry : page.entrySet()) {
				out.merge(entry.getKey(), entry.getValue(), Long::sum);
			}
		}
		return out;
	}

	private static void addSlot(Slot slot, Map<String, Long> out, int depth) {
		if (slot == null) {
			return;
		}
		addStack(slot.getItem(), out, depth);
	}

	private static void addStack(ItemStack stack, Map<String, Long> out, int depth) {
		if (stack == null || stack.isEmpty() || depth > 4) {
			return;
		}
		String id = idOf(stack);
		if (id != null) {
			out.merge(id, (long) Math.max(1, stack.getCount()), Long::sum);
			ItemIds.remember(stack);
		}
		addNested(extra(stack), out, depth + 1);
	}

	private static void addNested(CompoundTag extra, Map<String, Long> out, int depth) {
		if (extra == null || extra.isEmpty() || depth > 6) {
			return;
		}
		for (String key : CONTENT_KEYS) {
			addTag(extra.get(key), out, depth);
		}
		for (String key : extra.keySet()) {
			String lower = key.toLowerCase(Locale.ROOT);
			if (lower.contains("content") || lower.contains("container") || lower.contains("inventory") || lower.equals("items")) {
				addTag(extra.get(key), out, depth);
			}
		}
	}

	private static void addTag(Tag tag, Map<String, Long> out, int depth) {
		if (tag == null || depth > 8) {
			return;
		}
		if (tag instanceof CompoundTag compound) {
			if (looksLikeItem(compound)) {
				String id = itemId(compound);
				if (id != null) {
					out.merge(id, (long) itemCount(compound), Long::sum);
				}
				addNested(compound.getCompoundOrEmpty("tag").getCompoundOrEmpty("ExtraAttributes"), out, depth + 1);
				addNested(compound.getCompoundOrEmpty("ExtraAttributes"), out, depth + 1);
				addNested(compound.getCompoundOrEmpty("components").getCompoundOrEmpty("minecraft:custom_data"), out, depth + 1);
				return;
			}
			for (String key : compound.keySet()) {
				addTag(compound.get(key), out, depth + 1);
			}
			return;
		}
		if (tag instanceof ListTag list) {
			for (Tag child : list) {
				addTag(child, out, depth + 1);
			}
		}
	}

	private static boolean looksLikeItem(CompoundTag tag) {
		if (tag.contains("id") || tag.contains("Count") || tag.contains("count")) {
			return true;
		}
		return !tag.getCompoundOrEmpty("tag").getCompoundOrEmpty("ExtraAttributes").isEmpty()
			|| !tag.getCompoundOrEmpty("ExtraAttributes").isEmpty();
	}

	private static String itemId(CompoundTag tag) {
		String id = ItemIdsRead.id(tag.getCompoundOrEmpty("tag").getCompoundOrEmpty("ExtraAttributes"));
		if (id != null) {
			return id;
		}
		id = ItemIdsRead.id(tag.getCompoundOrEmpty("ExtraAttributes"));
		if (id != null) {
			return id;
		}
		id = ItemIdsRead.id(tag.getCompoundOrEmpty("components").getCompoundOrEmpty("minecraft:custom_data"));
		if (id != null) {
			return id;
		}
		id = ItemIdsRead.id(tag);
		if (id != null && !id.contains(":")) {
			return id;
		}
		return null;
	}

	private static int itemCount(CompoundTag tag) {
		int count = intOr(tag, "Count", 0);
		if (count <= 0) {
			count = intOr(tag, "count", 0);
		}
		return Math.max(1, count);
	}

	private static int intOr(CompoundTag tag, String key, int fallback) {
		try {
			return (int) Math.round(tag.getDoubleOr(key, fallback));
		} catch (Exception exception) {
			return fallback;
		}
	}

	public static String idOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		String skyblock = ItemIds.skyblockId(stack);
		if (skyblock != null) {
			return SkyblockRecipes.normalize(skyblock);
		}
		Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (key == null || "air".equals(key.getPath())) {
			return null;
		}
		return key.getPath().toUpperCase(Locale.ROOT);
	}

	private static CompoundTag extra(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return new CompoundTag();
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) {
			return new CompoundTag();
		}
		CompoundTag tag = data.copyTag();
		CompoundTag extra = tag.getCompoundOrEmpty("ExtraAttributes");
		if (!extra.isEmpty()) {
			return extra;
		}
		CompoundTag bukkit = tag.getCompoundOrEmpty("PublicBukkitValues");
		if (!bukkit.isEmpty()) {
			return bukkit;
		}
		return tag;
	}

	private static String kind(String title) {
		String lower = title == null ? "" : title.toLowerCase(Locale.ROOT);
		if (lower.contains("ender chest") || lower.contains("enderchest")) {
			return "ender";
		}
		if (lower.contains("backpack")) {
			return "backpack";
		}
		if (lower.equals("storage") || lower.startsWith("storage ")) {
			return "storage";
		}
		return null;
	}

	private static final class ItemIdsRead {
		private static String id(CompoundTag tag) {
			if (tag == null || tag.isEmpty()) {
				return null;
			}
			String id = tag.getStringOr("id", "");
			if (!id.isBlank() && !id.contains(":")) {
				return SkyblockRecipes.normalize(id);
			}
			Tag value = tag.get("id");
			if (value != null) {
				String text = value.asString().orElse("").trim();
				if (!text.isEmpty() && !text.contains(":")) {
					return SkyblockRecipes.normalize(text);
				}
			}
			return null;
		}
	}
}
