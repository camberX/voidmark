package dev.voidmark.client.item;

import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
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

	public static ItemStack visual(ItemStack stack) {
		if (stack == null || stack.isEmpty() || Boolean.TRUE.equals(APPLYING.get())) {
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
			clear(player, real);
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
		if (changed) {
			persist();
			playSwap(player, offhand);
		}
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
			ItemIds.Preview preview = ItemIds.resolve(entry.displayId);
			if (preview.kind() != ItemIds.Kind.VANILLA && preview.kind() != ItemIds.Kind.SKYBLOCK) {
				continue;
			}
			Skin skin = new Skin();
			skin.key = entry.key == null ? "" : entry.key;
			skin.originalId = entry.originalId == null ? "" : entry.originalId;
			skin.displayId = preview.canonical();
			skin.slot = entry.slot;
			skin.offhand = entry.offhand;
			skin.display = preview.stack().copy();
			SKINS.add(skin);
		}
	}

	private static Skin find(ItemStack stack) {
		String uuid = ItemIds.uuidOf(stack);
		if (uuid != null) {
			Skin byUuid = byKey("uuid:" + uuid);
			if (byUuid != null) {
				return byUuid;
			}
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return null;
		}
		Inventory inventory = client.player.getInventory();
		for (Skin skin : SKINS) {
			ItemStack bound = bound(inventory, skin);
			if (bound == stack) {
				return stillValid(bound, skin) ? skin : null;
			}
		}
		for (Skin skin : SKINS) {
			ItemStack bound = bound(inventory, skin);
			if (!bound.isEmpty() && stillValid(bound, skin) && ItemStack.isSameItemSameComponents(bound, stack)) {
				return skin;
			}
		}
		return null;
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

	private static ItemStack bound(Inventory inventory, Skin skin) {
		if (skin.offhand) {
			return inventory.getItem(Inventory.SLOT_OFFHAND);
		}
		if (skin.slot < 0 || skin.slot > 8) {
			return ItemStack.EMPTY;
		}
		return inventory.getItem(skin.slot);
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
