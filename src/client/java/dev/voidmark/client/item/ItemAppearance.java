package dev.voidmark.client.item;

import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

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

	public static ItemStack named(ItemStack stack) {
		if (stack == null || stack.isEmpty() || Boolean.TRUE.equals(APPLYING.get())) {
			return stack;
		}
		Skin skin = find(stack);
		if (skin == null || !skin.textOverride || skin.display.isEmpty()) {
			return stack;
		}
		ItemText.untiltOn(skin.display);
		return skin.display;
	}

	public static String displayId(ItemStack stack) {
		Skin skin = find(stack);
		return skin == null ? null : skin.displayId;
	}

	public static boolean maxed(ItemStack stack) {
		Skin skin = find(stack);
		return skin != null && skin.maxed;
	}

	public static void setMaxed(ItemStack stack, boolean maxed) {
		Skin skin = find(stack);
		if (skin == null || skin.maxed == maxed) {
			return;
		}
		skin.maxed = maxed;
		persist();
	}

	/**
	 * Stack to draw as worn armor or a skull. Uses {@link #visual}, then retargets
	 * {@code EQUIPPABLE} to {@code slot} when the copied item has an armor asset
	 * on the wrong slot (catalog pieces often sit on paper).
	 */
	public static ItemStack worn(ItemStack stack, EquipmentSlot slot) {
		ItemStack visual = visual(stack);
		if (visual == null || visual.isEmpty() || slot == null || slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
			return visual;
		}
		if (slot == EquipmentSlot.HEAD && visual.get(DataComponents.PROFILE) != null) {
			return visual;
		}
		Equippable eq = visual.get(DataComponents.EQUIPPABLE);
		if (eq == null || eq.assetId().isEmpty() || eq.slot() == slot) {
			return visual;
		}
		Equippable.Builder builder = Equippable.builder(slot)
			.setEquipSound(eq.equipSound())
			.setDispensable(eq.dispensable())
			.setSwappable(eq.swappable())
			.setDamageOnHurt(eq.damageOnHurt())
			.setEquipOnInteract(eq.equipOnInteract())
			.setCanBeSheared(eq.canBeSheared())
			.setShearingSound(eq.shearingSound());
		eq.assetId().ifPresent(builder::setAsset);
		eq.cameraOverlay().ifPresent(builder::setCameraOverlay);
		eq.allowedEntities().ifPresent(builder::setAllowedEntities);
		ItemStack copy = visual.copy();
		copy.set(DataComponents.EQUIPPABLE, builder.build());
		return copy;
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
		ItemText overlay = existing.textOverride ? ItemText.capture(existing.display) : ItemText.empty();
		existing.key = key;
		existing.originalId = ItemIds.idOf(real);
		existing.displayId = canonical;
		existing.slot = slot;
		existing.offhand = offhand;
		existing.display = display.copy();
		existing.loreSource = "";
		if (overlay.present() && !canonical.toLowerCase(java.util.Locale.ROOT).startsWith("sb:")) {
			overlay.apply(existing.display);
			existing.textOverride = true;
		} else {
			existing.textOverride = false;
		}
		persist();
		playSwap(player, offhand);
	}

	public static void applyText(Player player, ItemStack real, ItemText text) {
		applyText(player, real, text, "");
	}

	public static void applyText(Player player, ItemStack real, ItemText text, String sourceId) {
		if (player == null || real == null || real.isEmpty() || text == null || !text.present()) {
			return;
		}
		boolean offhand = isOffhand(player, real);
		int slot = offhand ? Inventory.SLOT_OFFHAND : player.getInventory().getSelectedSlot();
		String key = keyOf(real, offhand, slot);
		Skin existing = byKey(key);
		if (existing == null) {
			existing = find(real);
		}
		String source = sourceId == null ? "" : sourceId;
		if (existing != null && existing.textOverride && !source.isEmpty() && source.equals(existing.loreSource)) {
			return;
		}
		if (existing == null) {
			existing = new Skin();
			existing.key = key;
			existing.originalId = ItemIds.idOf(real);
			existing.displayId = ItemIds.idOf(real);
			existing.slot = slot;
			existing.offhand = offhand;
			existing.display = real.copy();
			SKINS.add(existing);
		} else if (existing.display == null || existing.display.isEmpty()) {
			existing.display = real.copy();
		}
		existing.slot = slot;
		existing.offhand = offhand;
		existing.key = key;
		text.apply(existing.display);
		existing.textOverride = true;
		existing.loreSource = source;
		existing.maxed = source.endsWith("#max");
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
		if (real == null || real.isEmpty()) {
			return;
		}
		Skin skin = find(real);
		if (skin == null) {
			return;
		}
		if (!skin.textOverride) {
			clear(player, real);
			return;
		}
		String original = ItemIds.idOf(real);
		if (original.equalsIgnoreCase(skin.displayId)) {
			return;
		}
		ItemText overlay = ItemText.capture(skin.display);
		skin.display = real.copy();
		overlay.apply(skin.display);
		skin.displayId = original;
		skin.originalId = original;
		persist();
		if (player != null) {
			playSwap(player, isOffhand(player, real));
		}
	}

	public static void reload() {
		SKINS.clear();
		VoidmarkConfig config = VoidmarkConfig.get();
		if (config.itemClipboardName == null) {
			config.itemClipboardName = "";
		}
		if (config.itemClipboardItemName == null) {
			config.itemClipboardItemName = "";
		}
		if (config.itemClipboardLore == null) {
			config.itemClipboardLore = "";
		}
		ItemText.setClipboard(ItemText.fromJson(
			config.itemClipboardName,
			config.itemClipboardItemName,
			config.itemClipboardLore
		));
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
			ItemText overlay = ItemText.fromJson(
				entry.nameJson == null ? "" : entry.nameJson,
				entry.itemNameJson == null ? "" : entry.itemNameJson,
				entry.loreJson == null ? "" : entry.loreJson
			);
			if (overlay.present()) {
				overlay.apply(skin.display);
				skin.textOverride = true;
			}
			skin.maxed = entry.maxed;
			SKINS.add(skin);
		}
	}

	public static void persistClipboard() {
		VoidmarkConfig config = VoidmarkConfig.get();
		ItemText text = ItemText.clipboard();
		config.itemClipboardName = text.nameJson();
		config.itemClipboardItemName = text.itemNameJson();
		config.itemClipboardLore = text.loreJson();
		config.save();
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
		Player player = client.player;
		for (Skin skin : SKINS) {
			ItemStack bound = bound(player, skin);
			if (bound == stack) {
				return stillValid(bound, skin) ? skin : null;
			}
		}
		for (Skin skin : SKINS) {
			ItemStack bound = bound(player, skin);
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
			entry.maxed = skin.maxed;
			if (skin.textOverride && !skin.display.isEmpty()) {
				ItemText overlay = ItemText.capture(skin.display);
				entry.nameJson = overlay.nameJson();
				entry.itemNameJson = overlay.itemNameJson();
				entry.loreJson = overlay.loreJson();
			}
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
		boolean textOverride;
		boolean maxed;
		String loreSource = "";
		ItemStack display = ItemStack.EMPTY;
	}
}
