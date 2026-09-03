package dev.voidmark.client.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.voidmark.client.visual.NickHider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

public final class ItemText {
	private static ItemText clipboard = empty();

	private final Component name;
	private final Component itemName;
	private final ItemLore lore;

	private ItemText(Component name, Component itemName, ItemLore lore) {
		this.name = name;
		this.itemName = itemName;
		this.lore = lore == null ? ItemLore.EMPTY : lore;
	}

	public static ItemText empty() {
		return new ItemText(null, null, ItemLore.EMPTY);
	}

	public static ItemText clipboard() {
		return clipboard;
	}

	public static void setClipboard(ItemText text) {
		clipboard = text == null ? empty() : text;
	}

	public static ItemText capture(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return empty();
		}
		boolean prior = ItemAppearance.suppress();
		try {
			Component name = stack.get(DataComponents.CUSTOM_NAME);
			if (name == null) {
				name = stack.getHoverName();
			}
			Component itemName = stack.get(DataComponents.ITEM_NAME);
			ItemLore lore = stack.get(DataComponents.LORE);
			return new ItemText(name, itemName, lore);
		} finally {
			ItemAppearance.resume(prior);
		}
	}

	public static ItemText fromJson(String nameJson, String itemNameJson, String loreJson) {
		Component name = decodeComponent(nameJson);
		Component itemName = decodeComponent(itemNameJson);
		ItemLore lore = decodeLore(loreJson);
		if (name == null && itemName == null && (lore == null || lore.lines().isEmpty())) {
			return empty();
		}
		return new ItemText(name, itemName, lore);
	}

	public static ItemText fromLegacy(String nameRaw, List<String> loreLines) {
		Component name = nameRaw == null || nameRaw.isBlank() ? null : styled(nameRaw, true);
		List<Component> lines = new ArrayList<>();
		if (loreLines != null) {
			int max = Math.min(loreLines.size(), ItemLore.MAX_LINES);
			for (int i = 0; i < max; i++) {
				String line = loreLines.get(i);
				lines.add(line == null || line.isEmpty() ? Component.empty() : styled(line, false));
			}
		}
		ItemLore lore = lines.isEmpty() ? ItemLore.EMPTY : new ItemLore(lines);
		if (name == null && lore.lines().isEmpty()) {
			return empty();
		}
		return new ItemText(name, null, lore);
	}

	public static boolean hasLore(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		boolean prior = ItemAppearance.suppress();
		try {
			ItemLore lore = stack.get(DataComponents.LORE);
			return lore != null && !lore.lines().isEmpty();
		} finally {
			ItemAppearance.resume(prior);
		}
	}

	public static Component styled(String raw, boolean itemName) {
		if (raw == null || raw.isEmpty()) {
			return Component.empty();
		}
		Component parsed = NickHider.parseLegacy(raw.replace('§', '&'));
		if (itemName && parsed instanceof MutableComponent mutable) {
			return mutable.withStyle(style -> style.withItalic(false));
		}
		return parsed;
	}

	public boolean present() {
		return name != null || itemName != null || (lore != null && !lore.lines().isEmpty());
	}

	public String label() {
		if (name != null) {
			String plain = name.getString();
			if (!plain.isBlank()) {
				return plain;
			}
		}
		if (itemName != null) {
			String plain = itemName.getString();
			if (!plain.isBlank()) {
				return plain;
			}
		}
		int lines = lore == null ? 0 : lore.lines().size();
		return lines == 0 ? "" : lines + " lore lines";
	}

	public Component name() {
		return name;
	}

	public ItemLore lore() {
		return lore;
	}

	public void apply(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		if (name != null) {
			stack.set(DataComponents.CUSTOM_NAME, name);
		} else {
			stack.remove(DataComponents.CUSTOM_NAME);
		}
		if (itemName != null) {
			stack.set(DataComponents.ITEM_NAME, itemName);
		}
		stack.set(DataComponents.LORE, lore == null ? ItemLore.EMPTY : lore);
	}

	public String nameJson() {
		return encodeComponent(name);
	}

	public String itemNameJson() {
		return encodeComponent(itemName);
	}

	public String loreJson() {
		return encodeLore(lore);
	}

	private static String encodeComponent(Component component) {
		if (component == null) {
			return "";
		}
		return ComponentSerialization.CODEC
			.encodeStart(JsonOps.INSTANCE, component)
			.result()
			.map(JsonElement::toString)
			.orElse("");
	}

	private static Component decodeComponent(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			JsonElement element = JsonParser.parseString(json);
			return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, element).result().orElse(null);
		} catch (RuntimeException ignored) {
			return Component.literal(json);
		}
	}

	private static String encodeLore(ItemLore lore) {
		if (lore == null || lore.lines().isEmpty()) {
			return "";
		}
		return ItemLore.CODEC
			.encodeStart(JsonOps.INSTANCE, lore)
			.result()
			.map(JsonElement::toString)
			.orElse("");
	}

	private static ItemLore decodeLore(String json) {
		if (json == null || json.isBlank()) {
			return ItemLore.EMPTY;
		}
		try {
			JsonElement element = JsonParser.parseString(json);
			return ItemLore.CODEC.parse(JsonOps.INSTANCE, element).result().orElse(ItemLore.EMPTY);
		} catch (RuntimeException ignored) {
			return ItemLore.EMPTY;
		}
	}
}
