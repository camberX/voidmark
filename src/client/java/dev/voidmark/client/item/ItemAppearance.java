package dev.voidmark.client.item;

import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class ItemAppearance {
	private static final List<Skin> SKINS = new ArrayList<>();
	private static final ThreadLocal<Boolean> APPLYING = ThreadLocal.withInitial(() -> false);

	private ItemAppearance() {
	}

	public static boolean suppress() {
		boolean prior = Boolean.TRUE.equals(APPLYING.get());
		APPLYING.set(true);
		return prior;
	}

	public static void resume(boolean prior) {
		APPLYING.set(prior);
	}

	public static ItemStack visual(ItemStack stack) {
		if (stack == null || stack.isEmpty() || SKINS.isEmpty() || Boolean.TRUE.equals(APPLYING.get())) {
			return stack;
		}
		Skin skin = find(stack);
		if (skin == null || skin.display.isEmpty()) {
			return stack;
		}
		if (skin.display.getCount() != stack.getCount()) {
			skin.display.setCount(Math.max(1, stack.getCount()));
		}
		return skin.display;
	}

	public static ItemStack named(ItemStack stack) {
		return stack;
	}

	public static String displayId(ItemStack stack) {
		Skin skin = find(stack);
		return skin == null ? null : skin.displayId;
	}

	public static void set(Player player, ItemStack real, ItemStack display, String displayId) {
		if (player == null || real == null || real.isEmpty() || display == null || display.isEmpty()) {
			return;
		}
		String canonical = displayId == null ? "" : displayId.trim();
		if (canonical.isEmpty() || canonical.equalsIgnoreCase(ItemIds.idOf(real))) {
			revertModel(player, real);
			return;
		}
		boolean offhand = isOffhand(player, real);
		int slot = offhand ? Inventory.SLOT_OFFHAND : player.getInventory().getSelectedSlot();
		String key = keyOf(real, offhand, slot);
		Skin existing = byKey(key);
		boolean changed = existing == null || !canonical.equalsIgnoreCase(existing.displayId);
		if (!changed) {
			return;
		}
		if (existing == null) {
			existing = new Skin();
			SKINS.add(existing);
		}
		existing.key = key;
		existing.originalId = ItemIds.idOf(real);
		existing.displayId = canonical;
		existing.slot = slot;
		existing.offhand = offhand;
		existing.display = display.copy();
		persist();
		playSwap(player, offhand);
	}

	public static void clear(Player player, ItemStack real) {
		if (real == null || real.isEmpty()) {
			return;
		}
		boolean offhand = player != null && isOffhand(player, real);
		int slot = player == null ? 0 : (offhand ? Inventory.SLOT_OFFHAND : player.getInventory().getSelectedSlot());
		String key = keyOf(real, offhand, slot);
		String uuid = ItemIds.uuidOf(real);
		boolean removed = SKINS.removeIf(skin ->
			skin.key.equals(key) || (uuid != null && skin.key.equals("uuid:" + uuid))
		);
		if (removed) {
			persist();
			if (player != null) {
				playSwap(player, offhand);
			}
		}
	}

	public static void revertModel(Player player, ItemStack real) {
		clear(player, real);
	}

	public static void reload() {
		SKINS.clear();
		VoidmarkConfig config = VoidmarkConfig.get();
		if (config.itemSkins == null) {
			config.itemSkins = new ArrayList<>();
			return;
		}
		for (VoidmarkConfig.ItemSkin entry : config.itemSkins) {
			if (entry == null || entry.displayId == null || entry.displayId.isBlank()) {
				continue;
			}
			ItemIds.Preview preview;
			try {
				preview = ItemIds.resolve(entry.displayId);
			} catch (RuntimeException ignored) {
				continue;
			}
			ItemStack display;
			if (preview.kind() == ItemIds.Kind.VANILLA || preview.kind() == ItemIds.Kind.SKYBLOCK) {
				if (preview.stack() == null || preview.stack().isEmpty()) {
					continue;
				}
				display = preview.stack().copy();
			} else {
				continue;
			}
			Skin skin = new Skin();
			skin.key = entry.key == null ? "" : entry.key;
			skin.originalId = entry.originalId == null ? "" : entry.originalId;
			skin.displayId = preview.canonical();
			skin.slot = entry.slot;
			skin.offhand = entry.offhand;
			skin.display = display;
			SKINS.add(skin);
		}
	}

	/**
	 * Reskins only apply to the local player's own stacks. Chest GUIs like HOTM
	 * have huge Skyblock NBT; never copy tags or walk lore on those items.
	 */
	private static Skin find(ItemStack stack) {
		if (SKINS.isEmpty()) {
			return null;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || !ours(client.player, stack)) {
			return null;
		}
		String uuid = ItemIds.uuidOf(stack);
		if (uuid != null) {
			Skin byUuid = byKey("uuid:" + uuid);
			if (byUuid != null) {
				return byUuid;
			}
		}
		Player player = client.player;
		for (Skin skin : SKINS) {
			ItemStack bound = bound(player, skin);
			if (bound == stack) {
				return stillValid(bound, skin) ? skin : null;
			}
		}
		return null;
	}

	private static boolean ours(Player player, ItemStack stack) {
		Inventory inventory = player.getInventory();
		int size = inventory.getContainerSize();
		for (int i = 0; i < size; i++) {
			if (inventory.getItem(i) == stack) {
				return true;
			}
		}
		for (EquipmentSlot slot : ARMOR) {
			if (player.getItemBySlot(slot) == stack) {
				return true;
			}
		}
		return player.getOffhandItem() == stack;
	}

	private static boolean stillValid(ItemStack bound, Skin skin) {
		if (bound == null || bound.isEmpty()) {
			return false;
		}
		if (skin.key.startsWith("uuid:")) {
			String uuid = ItemIds.uuidOf(bound);
			return uuid != null && skin.key.equals("uuid:" + uuid);
		}
		return skin.originalId.isEmpty() || skin.originalId.equalsIgnoreCase(ItemIds.idOf(bound));
	}

	private static final EquipmentSlot[] ARMOR = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private static ItemStack bound(Player player, Skin skin) {
		Inventory inventory = player.getInventory();
		ItemStack held;
		if (skin.offhand) {
			held = inventory.getItem(Inventory.SLOT_OFFHAND);
		} else if (skin.slot >= 0 && skin.slot <= 8) {
			held = inventory.getItem(skin.slot);
		} else {
			held = ItemStack.EMPTY;
		}
		if (!held.isEmpty() && stillValid(held, skin)) {
			return held;
		}
		for (EquipmentSlot slot : ARMOR) {
			ItemStack worn = player.getItemBySlot(slot);
			if (!worn.isEmpty() && stillValid(worn, skin)) {
				return worn;
			}
		}
		return held;
	}

	private static boolean isOffhand(Player player, ItemStack real) {
		ItemStack main = player.getMainHandItem();
		if (main == real) {
			return false;
		}
		ItemStack off = player.getOffhandItem();
		return off == real || (!off.isEmpty() && main.isEmpty());
	}

	private static String keyOf(ItemStack real, boolean offhand, int slot) {
		String uuid = ItemIds.uuidOf(real);
		if (uuid != null) {
			return "uuid:" + uuid;
		}
		String id = ItemIds.idOf(real);
		return offhand ? "offhand:" + id : "slot:" + slot + ":" + id;
	}

	private static Skin byKey(String key) {
		for (Skin skin : SKINS) {
			if (skin.key.equals(key)) {
				return skin;
			}
		}
		return null;
	}

	private static void persist() {
		VoidmarkConfig config = VoidmarkConfig.get();
		List<VoidmarkConfig.ItemSkin> out = new ArrayList<>();
		Iterator<Skin> iterator = SKINS.iterator();
		while (iterator.hasNext()) {
			Skin skin = iterator.next();
			if (skin.displayId == null || skin.displayId.isBlank()) {
				iterator.remove();
				continue;
			}
			VoidmarkConfig.ItemSkin entry = new VoidmarkConfig.ItemSkin();
			entry.key = skin.key;
			entry.displayId = skin.displayId;
			entry.originalId = skin.originalId;
			entry.slot = skin.slot;
			entry.offhand = skin.offhand;
			out.add(entry);
		}
		config.itemSkins = out;
		config.save();
	}

	private static void playSwap(Player player, boolean offhand) {
		Minecraft client = Minecraft.getInstance();
		if (client.gameRenderer == null || client.gameRenderer.itemInHandRenderer == null) {
			return;
		}
		if (player != client.player) {
			return;
		}
		client.gameRenderer.itemInHandRenderer.itemUsed(offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
	}

	private static final class Skin {
		String key = "";
		String originalId = "";
		String displayId = "";
		int slot;
		boolean offhand;
		ItemStack display = ItemStack.EMPTY;
	}
}
