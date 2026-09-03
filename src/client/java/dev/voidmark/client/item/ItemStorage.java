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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Live inventory plus Ender Chest / backpack counts from the Skyblock profile API.
 * <p>
 * API storage is a snapshot. Pulling a stack into your inventory would count it
 * twice if we added live inventory on top of that snapshot. While a bag or
 * Ender Chest is open, slot deltas update an adjustment so moved items stay
 * counted once. Armor is part of {@link Inventory#getContainerSize()} and is
 * not counted a second time.
 */
public final class ItemStorage {
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
	private static volatile Map<String, Long> apiEnder = Map.of();
	private static volatile Map<String, Long> apiBackpack = Map.of();
	private static volatile boolean apiEnderReady;
	private static volatile boolean apiBackpackReady;
	private static final Map<String, Long> enderAdjust = new HashMap<>();
	private static final Map<String, Long> backpackAdjust = new HashMap<>();
	private static String openKind;
	private static String openTitle = "";
	private static Map<String, Long> openCounts = Map.of();

	private ItemStorage() {
	}

	public static void tick(Minecraft client) {
		if (client == null || client.player == null) {
			resetOpen();
			return;
		}
		Screen screen = client.screen;
		if (!(screen instanceof AbstractContainerScreen<?> container)) {
			resetOpen();
			return;
		}
		String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
		String kind = kind(title);
		if (kind == null) {
			resetOpen();
			return;
		}
		AbstractContainerMenu menu = container.getMenu();
		if (menu == null) {
			resetOpen();
			return;
		}
		Map<String, Long> counts = new HashMap<>();
		int end = Math.max(0, menu.slots.size() - 36);
		for (int i = 0; i < end; i++) {
			addSlot(menu.slots.get(i), counts, 0, true);
		}
		if (tracksMoves(kind) && kind.equals(openKind) && title.equals(openTitle)) {
			nudge(adjust(kind), openCounts, counts);
		}
		openKind = kind;
		openTitle = title;
		openCounts = counts;
		PAGES.put(kind + ":" + title.trim().toLowerCase(Locale.ROOT), counts);
	}

	public static void applyApi(Map<String, Long> ender, Map<String, Long> backpacks) {
		if (ender != null) {
			apiEnder = Map.copyOf(ender);
			apiEnderReady = true;
			enderAdjust.clear();
		}
		if (backpacks != null) {
			apiBackpack = Map.copyOf(backpacks);
			apiBackpackReady = true;
			backpackAdjust.clear();
		}
	}

	public static boolean hasApiStorage() {
		return apiEnderReady || apiBackpackReady;
	}

	public static boolean sawEnder() {
		return apiEnderReady || hasPage("ender");
	}

	public static boolean sawBackpack() {
		return apiBackpackReady || hasPage("backpack");
	}

	public static Map<String, Long> counts(Player player) {
		Map<String, Long> out = new HashMap<>();
		if (player == null) {
			return out;
		}
		Inventory inventory = player.getInventory();
		int size = inventory.getContainerSize();
		for (int i = 0; i < size; i++) {
			addStack(inventory.getItem(i), out, 0, false);
		}
		Minecraft client = Minecraft.getInstance();
		if (client.screen instanceof AbstractContainerScreen<?> container) {
			AbstractContainerMenu menu = container.getMenu();
			if (menu != null) {
				addStack(menu.getCarried(), out, 0, false);
			}
		}
		if (apiEnderReady) {
			mergeAdjusted(out, apiEnder, enderAdjust);
		} else {
			addPages(out, "ender");
		}
		if (apiBackpackReady) {
			mergeAdjusted(out, apiBackpack, backpackAdjust);
		} else {
			addPages(out, "backpack");
		}
		if (!apiEnderReady && !apiBackpackReady) {
			addPages(out, "storage");
		}
		return out;
	}

	public static void addCounts(Tag tag, Map<String, Long> out, boolean nestStorage) {
		addTag(tag, out, 0, nestStorage);
	}

	private static void resetOpen() {
		openKind = null;
		openTitle = "";
		openCounts = Map.of();
	}

	private static boolean tracksMoves(String kind) {
		return "ender".equals(kind) || "backpack".equals(kind);
	}

	private static Map<String, Long> adjust(String kind) {
		return "ender".equals(kind) ? enderAdjust : backpackAdjust;
	}

	/** Positive delta = items left the container (usually into inventory). */
	private static void nudge(Map<String, Long> adjust, Map<String, Long> previous, Map<String, Long> current) {
		Set<String> ids = new HashSet<>();
		ids.addAll(previous.keySet());
		ids.addAll(current.keySet());
		for (String id : ids) {
			long delta = previous.getOrDefault(id, 0L) - current.getOrDefault(id, 0L);
			if (delta != 0L) {
				adjust.merge(id, delta, Long::sum);
			}
		}
	}

	private static void mergeAdjusted(Map<String, Long> out, Map<String, Long> snapshot, Map<String, Long> adjust) {
		Set<String> ids = new HashSet<>();
		ids.addAll(snapshot.keySet());
		ids.addAll(adjust.keySet());
		for (String id : ids) {
			long n = snapshot.getOrDefault(id, 0L) - adjust.getOrDefault(id, 0L);
			if (n > 0L) {
				out.merge(id, n, Long::sum);
			}
		}
	}

	private static void addPages(Map<String, Long> out, String kind) {
		String prefix = kind + ":";
		for (Map.Entry<String, Map<String, Long>> page : PAGES.entrySet()) {
			if (!page.getKey().startsWith(prefix)) {
				continue;
			}
			merge(out, page.getValue());
		}
	}

	private static boolean hasPage(String kind) {
		String prefix = kind + ":";
		for (String key : PAGES.keySet()) {
			if (key.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	private static void merge(Map<String, Long> out, Map<String, Long> extra) {
		if (extra == null || extra.isEmpty()) {
			return;
		}
		for (Map.Entry<String, Long> entry : extra.entrySet()) {
			out.merge(entry.getKey(), entry.getValue(), Long::sum);
		}
	}

	private static void addSlot(Slot slot, Map<String, Long> out, int depth, boolean nestStorage) {
		if (slot == null) {
			return;
		}
		addStack(slot.getItem(), out, depth, nestStorage);
	}

	private static void addStack(ItemStack stack, Map<String, Long> out, int depth, boolean nestStorage) {
		if (stack == null || stack.isEmpty() || depth > 4) {
			return;
		}
		String id = idOf(stack);
		if (id != null) {
			out.merge(id, (long) Math.max(1, stack.getCount()), Long::sum);
			ItemIds.remember(stack);
		}
		addNested(extra(stack), out, depth + 1, nestStorage);
	}

	private static void addNested(CompoundTag extra, Map<String, Long> out, int depth, boolean nestStorage) {
		if (extra == null || extra.isEmpty() || depth > 6) {
			return;
		}
		for (String key : CONTENT_KEYS) {
			if (!nestStorage && storageKey(key)) {
				continue;
			}
			addTag(extra.get(key), out, depth, nestStorage);
		}
		for (String key : extra.keySet()) {
			if (!nestStorage && storageKey(key)) {
				continue;
			}
			String lower = key.toLowerCase(Locale.ROOT);
			if (lower.contains("content") || lower.contains("container") || lower.contains("inventory") || lower.equals("items")) {
				addTag(extra.get(key), out, depth, nestStorage);
			}
		}
	}

	private static void addTag(Tag tag, Map<String, Long> out, int depth, boolean nestStorage) {
		if (tag == null || depth > 8) {
			return;
		}
		if (tag instanceof CompoundTag compound) {
			if (looksLikeItem(compound)) {
				String id = itemId(compound);
				long count = itemCount(compound);
				if (id != null && count > 0L) {
					out.merge(id, count, Long::sum);
				}
				addNested(compound.getCompoundOrEmpty("tag").getCompoundOrEmpty("ExtraAttributes"), out, depth + 1, nestStorage);
				addNested(compound.getCompoundOrEmpty("ExtraAttributes"), out, depth + 1, nestStorage);
				addNested(customData(compound), out, depth + 1, nestStorage);
				return;
			}
			for (String key : compound.keySet()) {
				if (!nestStorage && storageKey(key)) {
					continue;
				}
				addTag(compound.get(key), out, depth + 1, nestStorage);
			}
			return;
		}
		if (tag instanceof ListTag list) {
			for (Tag child : list) {
				addTag(child, out, depth + 1, nestStorage);
			}
		}
	}

	private static boolean storageKey(String key) {
		if (key == null) {
			return false;
		}
		String lower = key.toLowerCase(Locale.ROOT);
		return lower.contains("backpack") || lower.contains("ender_chest") || lower.contains("enderchest");
	}

	private static boolean looksLikeItem(CompoundTag tag) {
		if (tag.contains("Count") || tag.contains("count")) {
			return true;
		}
		if (tag.contains("components") || tag.contains("tag")) {
			return true;
		}
		return !tag.getCompoundOrEmpty("tag").getCompoundOrEmpty("ExtraAttributes").isEmpty()
			|| !tag.getCompoundOrEmpty("ExtraAttributes").isEmpty();
	}

	private static String itemId(CompoundTag tag) {
		String id = ItemIdsRead.id(extraAttributes(tag));
		if (id != null) {
			return id;
		}
		id = ItemIdsRead.id(customData(tag));
		if (id != null) {
			return id;
		}
		id = ItemIdsRead.id(tag);
		if (id != null && !id.contains(":")) {
			return id;
		}
		return null;
	}

	private static CompoundTag extraAttributes(CompoundTag tag) {
		CompoundTag extra = tag.getCompoundOrEmpty("tag").getCompoundOrEmpty("ExtraAttributes");
		if (!extra.isEmpty()) {
			return extra;
		}
		return tag.getCompoundOrEmpty("ExtraAttributes");
	}

	private static CompoundTag customData(CompoundTag tag) {
		CompoundTag fromItem = tag.getCompoundOrEmpty("components");
		CompoundTag fromTag = tag.getCompoundOrEmpty("tag").getCompoundOrEmpty("components");
		CompoundTag components = fromItem.isEmpty() ? fromTag : fromItem;
		if (components.isEmpty()) {
			return new CompoundTag();
		}
		CompoundTag direct = components.getCompoundOrEmpty("minecraft:custom_data");
		if (!direct.isEmpty()) {
			return direct;
		}
		for (String key : components.keySet()) {
			String lower = key.toLowerCase(Locale.ROOT);
			if (!lower.contains("custom_data") && !lower.equals("customdata")) {
				continue;
			}
			CompoundTag nested = components.getCompoundOrEmpty(key);
			if (!nested.isEmpty()) {
				return nested;
			}
			String snbt = components.getStringOr(key, "");
			if (!snbt.isBlank() && snbt.startsWith("{")) {
				try {
					return net.minecraft.nbt.TagParser.parseCompoundFully(snbt);
				} catch (Exception ignored) {
				}
			}
		}
		return new CompoundTag();
	}

	private static long itemCount(CompoundTag tag) {
		if (tag.contains("Count")) {
			return Math.max(0, intOr(tag, "Count", 0));
		}
		if (tag.contains("count")) {
			return Math.max(0, intOr(tag, "count", 0));
		}
		return 1L;
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
