package dev.voidmark.client.item;

import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.Equippable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hypixel wardrobe is a 6-row chest titled {@code (1/3) Armor Sets}:
 * each column is one armor set (helmet / chest / legs / boots), with
 * equip and paging on the bottom rim.
 */
public final class WardrobeMenus {
	public record Piece(int slot, ItemStack stack) {
		public Piece copy() {
			return new Piece(slot, copyStack(stack));
		}
	}

	public record ArmorSet(
		int index,
		int slot,
		ItemStack helmet,
		ItemStack chest,
		ItemStack legs,
		ItemStack boots,
		ItemStack icon,
		boolean selected,
		boolean locked,
		String name
	) {
		public ArmorSet copy() {
			return new ArmorSet(
				index,
				slot,
				copyStack(helmet),
				copyStack(chest),
				copyStack(legs),
				copyStack(boots),
				copyStack(icon),
				selected,
				locked,
				name
			);
		}

		public boolean hasArmor() {
			return !empty(helmet) || !empty(chest) || !empty(legs) || !empty(boots);
		}
	}

	public record Snapshot(String title, String page, List<ArmorSet> sets, Piece next, Piece prev, Piece close) {
		public static Snapshot empty() {
			return new Snapshot("Armor Sets", "", List.of(), null, null, null);
		}

		public Snapshot copy() {
			List<ArmorSet> copied = new ArrayList<>(sets.size());
			for (ArmorSet set : sets) {
				copied.add(set.copy());
			}
			return new Snapshot(
				title,
				page,
				List.copyOf(copied),
				next == null ? null : next.copy(),
				prev == null ? null : prev.copy(),
				close == null ? null : close.copy()
			);
		}

		public boolean hasSets() {
			return sets != null && !sets.isEmpty();
		}

		public ArmorSet selected() {
			if (sets == null) {
				return null;
			}
			for (ArmorSet set : sets) {
				if (set.selected()) {
					return set;
				}
			}
			for (ArmorSet set : sets) {
				if (set.hasArmor()) {
					return set;
				}
			}
			return sets.isEmpty() ? null : sets.getFirst();
		}
	}

	private static final Pattern PAGE = Pattern.compile("\\((\\d+)\\s*/\\s*(\\d+)\\)");
	/** Hypixel titles: {@code (1/3) Armor Sets}, older {@code Wardrobe (1/2)}. */
	private static final Pattern TITLE = Pattern.compile(
		"[(\\uFF08]\\s*\\d+\\s*[/\\u2044\\u2215]\\s*\\d+\\s*[)\\uFF09]\\s*(?:Armor Sets|Wardrobe)"
			+ "|(?:Armor Sets|Wardrobe)\\s*[(\\uFF08]\\s*\\d+\\s*[/\\u2044\\u2215]\\s*\\d+\\s*[)\\uFF09]"
			+ "|^\\s*(?:Armor Sets|Wardrobe)\\s*$",
		Pattern.CASE_INSENSITIVE
	);
	private static final int COLS = 9;

	private WardrobeMenus() {
	}

	public static boolean enabled() {
		return VoidmarkConfig.get().wardrobeMenuEnabled;
	}

	public static boolean matches(Component title) {
		return matches(title == null ? "" : title.getString());
	}

	public static boolean matches(String title) {
		String plain = strip(title);
		return !plain.isEmpty() && TITLE.matcher(plain).find();
	}

	public static Snapshot read(AbstractContainerMenu menu, Component title) {
		if (menu == null) {
			return Snapshot.empty();
		}
		String raw = title == null ? "" : title.getString();
		int chest = Math.max(0, menu.slots.size() - 36);
		int rows = chest / COLS;
		Piece next = null;
		Piece prev = null;
		Piece close = null;
		ArmorSet[] columns = new ArmorSet[COLS];
		for (int col = 0; col < COLS; col++) {
			ItemStack helmet = ItemStack.EMPTY;
			ItemStack chestPiece = ItemStack.EMPTY;
			ItemStack legs = ItemStack.EMPTY;
			ItemStack boots = ItemStack.EMPTY;
			int action = col;
			boolean selected = false;
			boolean locked = false;
			boolean useful = false;
			String name = "Set " + (col + 1);
			for (int row = 0; row < rows; row++) {
				int index = col + row * COLS;
				if (index < 0 || index >= chest) {
					continue;
				}
				ItemStack stack = stackAt(menu, index);
				if (stack.isEmpty()) {
					continue;
				}
				String blob = blob(stack);
				if (isLocked(stack, blob)) {
					locked = true;
					action = index;
					useful = true;
					name = nameOf(stack).isBlank() ? name : nameOf(stack);
					continue;
				}
				if (isClose(stack, blob)) {
					close = new Piece(index, stack);
					continue;
				}
				if (isNext(stack, blob)) {
					next = new Piece(index, stack);
					continue;
				}
				if (isPrev(stack, blob)) {
					prev = new Piece(index, stack);
					continue;
				}
				if (LoadoutsMenus.isFiller(stack) && row >= 4) {
					continue;
				}
				Kind kind = armorKind(stack, blob);
				if (kind == Kind.HELMET && helmet.isEmpty()) {
					helmet = stack;
				} else if (kind == Kind.CHEST && chestPiece.isEmpty()) {
					chestPiece = stack;
				} else if (kind == Kind.LEGS && legs.isEmpty()) {
					legs = stack;
				} else if (kind == Kind.BOOTS && boots.isEmpty()) {
					boots = stack;
				} else if (row >= 4) {
					action = index;
					name = nameOf(stack).isBlank() ? name : nameOf(stack);
				} else if (helmet.isEmpty()) {
					helmet = stack;
				}
				selected = selected || selected(stack, nameOf(stack));
				useful = true;
				if (row >= 4) {
					action = index;
				} else if (action == col) {
					action = index;
				}
			}
			if (!useful) {
				continue;
			}
			ItemStack icon = firstArmor(helmet, chestPiece, legs, boots);
			columns[col] = new ArmorSet(
				col,
				action,
				helmet,
				chestPiece,
				legs,
				boots,
				icon,
				selected,
				locked,
				name
			);
		}
		List<ArmorSet> sets = new ArrayList<>();
		for (ArmorSet set : columns) {
			if (set != null) {
				sets.add(set);
			}
		}
		return new Snapshot(
			raw.isBlank() ? "Armor Sets" : strip(raw),
			pageLabel(raw),
			List.copyOf(sets),
			next,
			prev,
			close
		);
	}

	private enum Kind {
		HELMET,
		CHEST,
		LEGS,
		BOOTS,
		OTHER
	}

	private static Kind armorKind(ItemStack stack, String blob) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable != null) {
			return switch (equippable.slot()) {
				case HEAD -> Kind.HELMET;
				case CHEST -> Kind.CHEST;
				case LEGS -> Kind.LEGS;
				case FEET -> Kind.BOOTS;
				default -> Kind.OTHER;
			};
		}
		if (contains(blob, "helmet", " circlet", "crown", "hood", "mask", "hat")) {
			return Kind.HELMET;
		}
		if (contains(blob, "chestplate", "tunic")) {
			return Kind.CHEST;
		}
		if (contains(blob, "leggings", "pants")) {
			return Kind.LEGS;
		}
		if (contains(blob, "boots")) {
			return Kind.BOOTS;
		}
		return Kind.OTHER;
	}

	private static ItemStack firstArmor(ItemStack helmet, ItemStack chest, ItemStack legs, ItemStack boots) {
		if (!empty(helmet)) {
			return helmet;
		}
		if (!empty(chest)) {
			return chest;
		}
		if (!empty(legs)) {
			return legs;
		}
		if (!empty(boots)) {
			return boots;
		}
		return ItemStack.EMPTY;
	}

	private static ItemStack stackAt(AbstractContainerMenu menu, int index) {
		if (index < 0 || index >= menu.slots.size()) {
			return ItemStack.EMPTY;
		}
		Slot slot = menu.slots.get(index);
		ItemStack stack = slot.getItem();
		return stack == null ? ItemStack.EMPTY : stack;
	}

	private static boolean isClose(ItemStack stack, String blob) {
		return stack.is(Items.BARRIER) && contains(blob, "close", "go back", "exit", "close menu")
			|| contains(blob, "close menu", "click to close", "go back");
	}

	private static boolean isNext(ItemStack stack, String blob) {
		return contains(blob, "next page", "next →", "→") && !contains(blob, "previous");
	}

	private static boolean isPrev(ItemStack stack, String blob) {
		return contains(blob, "previous page", "prev page", "←") && !contains(blob, "next page");
	}

	private static boolean isLocked(ItemStack stack, String blob) {
		return contains(blob, "locked", "unlock this", "requires rank", "not unlocked", "purchase this slot")
			|| stack.is(Items.COAL) && contains(blob, "slot");
	}

	private static boolean selected(ItemStack stack, String name) {
		if (Boolean.TRUE.equals(stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE))) {
			return true;
		}
		String blob = blob(stack);
		return contains(blob, "equipped", "currently wearing", "this is your", "active armor", "selected")
			|| name.contains("✔")
			|| name.contains("✓");
	}

	private static String blob(ItemStack stack) {
		boolean prior = ItemAppearance.suppress();
		try {
			StringBuilder out = new StringBuilder(nameOf(stack));
			ItemLore lore = stack.get(DataComponents.LORE);
			if (lore != null) {
				for (Component line : lore.lines()) {
					out.append(' ').append(line.getString());
				}
			}
			return strip(out.toString()).toLowerCase(Locale.ROOT);
		} finally {
			ItemAppearance.resume(prior);
		}
	}

	private static String nameOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		boolean prior = ItemAppearance.suppress();
		try {
			return strip(stack.getHoverName().getString());
		} finally {
			ItemAppearance.resume(prior);
		}
	}

	private static String pageLabel(String title) {
		Matcher match = PAGE.matcher(strip(title));
		if (match.find()) {
			return match.group(1) + "/" + match.group(2);
		}
		return "";
	}

	private static boolean contains(String blob, String... needles) {
		if (blob == null || blob.isEmpty()) {
			return false;
		}
		for (String needle : needles) {
			if (blob.contains(needle)) {
				return true;
			}
		}
		return false;
	}

	private static String strip(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		return value.replaceAll("§.", "").replace('\u00A0', ' ').trim();
	}

	private static boolean empty(ItemStack stack) {
		return stack == null || stack.isEmpty() || LoadoutsMenus.isFiller(stack);
	}

	private static ItemStack copyStack(ItemStack stack) {
		return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
	}
}
