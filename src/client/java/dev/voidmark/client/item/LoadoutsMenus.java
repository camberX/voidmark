package dev.voidmark.client.item;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.Equippable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hypixel {@code /loadouts} is a 6-row chest: gear preview on the left,
 * a 3×4 loadout grid in columns 6–8 (ice / pet icons live there), paging
 * on the rim.
 */
public final class LoadoutsMenus {
	public enum Kind {
		HELMET,
		CHEST,
		LEGS,
		BOOTS,
		NECKLACE,
		CLOAK,
		BELT,
		GLOVES,
		PET,
		POWER,
		TUNING,
		HOTM,
		HOTF,
		LOADOUT,
		NEXT,
		PREV,
		CLOSE,
		OTHER
	}

	public record Piece(int slot, ItemStack stack, Kind kind, String name, boolean selected) {
		public Piece copy() {
			return new Piece(slot, copyStack(stack), kind, name, selected);
		}
	}

	public record Snapshot(
		String title,
		String page,
		List<Piece> loadouts,
		List<Piece> contents,
		Piece next,
		Piece prev,
		Piece close,
		ItemStack helmet,
		ItemStack chest,
		ItemStack legs,
		ItemStack boots,
		ItemStack pet,
		Component petName
	) {
		public static Snapshot empty() {
			return new Snapshot(
				"Loadouts",
				"",
				List.of(),
				List.of(),
				null,
				null,
				null,
				ItemStack.EMPTY,
				ItemStack.EMPTY,
				ItemStack.EMPTY,
				ItemStack.EMPTY,
				ItemStack.EMPTY,
				Component.empty()
			);
		}

		public Snapshot copy() {
			return new Snapshot(
				title,
				page,
				copyPieces(loadouts),
				copyPieces(contents),
				next == null ? null : next.copy(),
				prev == null ? null : prev.copy(),
				close == null ? null : close.copy(),
				copyStack(helmet),
				copyStack(chest),
				copyStack(legs),
				copyStack(boots),
				copyStack(pet),
				petName
			);
		}

		public boolean hasLoadouts() {
			return loadouts != null && !loadouts.isEmpty();
		}
	}

	private static final Pattern PAGE = Pattern.compile("\\((\\d+)\\s*/\\s*(\\d+)\\)");
	/** Hypixel chest title: {@code (1/3) Loadouts}. The page numbers change. */
	private static final Pattern TITLE = Pattern.compile(
		"[(\\uFF08]\\s*\\d+\\s*[/\\u2044\\u2215]\\s*\\d+\\s*[)\\uFF09]\\s*Loadouts",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern PET_NAME = Pattern.compile("\\[\\s*lvl\\s*\\d+\\s*]\\s*(.+)", Pattern.CASE_INSENSITIVE);
	private static final int COLS = 9;
	private static final int LOADOUT_COL0 = 5;
	private static final int LOADOUT_COLS = 3;
	private static final int LOADOUT_ROW0 = 1;
	private static final int LOADOUT_ROWS = 4;

	private LoadoutsMenus() {
	}

	public static boolean enabled() {
		return VoidmarkConfig.get().loadoutsMenuEnabled;
	}

	public static boolean matches(Component title) {
		return matches(plain(title));
	}

	public static boolean matches(String title) {
		String plain = strip(title);
		if (plain.isEmpty()) {
			return false;
		}
		return TITLE.matcher(plain).find();
	}

	private static String plain(Component title) {
		if (title == null) {
			return "";
		}
		return strip(title.getString());
	}

	public static Snapshot read(AbstractContainerMenu menu, Component title) {
		if (menu == null) {
			return Snapshot.empty();
		}
		String raw = title == null ? "" : title.getString();
		String page = pageLabel(raw);
		int chest = Math.max(0, menu.slots.size() - 36);
		List<Piece> loadouts = new ArrayList<>();
		List<Piece> contents = new ArrayList<>();
		Piece next = null;
		Piece prev = null;
		Piece close = null;
		ItemStack helmet = ItemStack.EMPTY;
		ItemStack chestPiece = ItemStack.EMPTY;
		ItemStack legs = ItemStack.EMPTY;
		ItemStack boots = ItemStack.EMPTY;
		ItemStack pet = ItemStack.EMPTY;
		for (int i = 0; i < chest; i++) {
			Slot slot = menu.slots.get(i);
			ItemStack stack = slot.getItem();
			if (stack == null || stack.isEmpty()) {
				if (loadoutIndex(i, chest)) {
					loadouts.add(new Piece(i, ItemStack.EMPTY, Kind.LOADOUT, emptyName(loadouts.size() + 1), false));
				}
				continue;
			}
			if (!loadoutIndex(i, chest) && isFiller(stack)) {
				continue;
			}
			Kind kind = classify(stack, i, chest);
			String name = nameOf(stack);
			boolean selected = selected(stack, name);
			Piece piece = new Piece(i, stack, kind, name, selected);
			switch (kind) {
				case LOADOUT -> loadouts.add(piece);
				case NEXT -> next = piece;
				case PREV -> prev = piece;
				case CLOSE -> close = piece;
				default -> {
					contents.add(piece);
					if (kind == Kind.HELMET && helmet.isEmpty()) {
						helmet = stack;
					} else if (kind == Kind.CHEST && chestPiece.isEmpty()) {
						chestPiece = stack;
					} else if (kind == Kind.LEGS && legs.isEmpty()) {
						legs = stack;
					} else if (kind == Kind.BOOTS && boots.isEmpty()) {
						boots = stack;
					} else if (kind == Kind.PET && pet.isEmpty()) {
						pet = stack;
					}
				}
			}
		}
		if (pet.isEmpty()) {
			for (Piece piece : contents) {
				if (looksLikePet(piece.name(), piece.stack())) {
					pet = piece.stack();
					break;
				}
			}
		}
		if (pet.isEmpty()) {
			for (Piece piece : loadouts) {
				if (piece.selected() && looksLikePet(piece.name(), piece.stack())) {
					pet = piece.stack();
					break;
				}
			}
		}
		if (pet.isEmpty()) {
			for (Piece piece : loadouts) {
				if (looksLikePet(piece.name(), piece.stack())) {
					pet = piece.stack();
					break;
				}
			}
		}
		return new Snapshot(
			raw.isBlank() ? "Loadouts" : strip(raw),
			page,
			List.copyOf(loadouts),
			List.copyOf(contents),
			next,
			prev,
			close,
			helmet,
			chestPiece,
			legs,
			boots,
			pet,
			petName(pet)
		);
	}

	private static Kind classify(ItemStack stack, int slot, int chest) {
		String blob = blob(stack);
		if (isClose(stack, blob)) {
			return Kind.CLOSE;
		}
		if (isNext(stack, blob)) {
			return Kind.NEXT;
		}
		if (isPrev(stack, blob)) {
			return Kind.PREV;
		}
		if (loadoutIndex(slot, chest)) {
			return Kind.LOADOUT;
		}
		if (looksLikePet(blob, stack)) {
			return Kind.PET;
		}
		if (contains(blob, "heart of the mountain", "hotm tree", "hotm slot")) {
			return Kind.HOTM;
		}
		if (contains(blob, "heart of the forest", "hotf tree", "hotf slot")) {
			return Kind.HOTF;
		}
		if (contains(blob, "tuning point", "magical power tuning", "tuning template")) {
			return Kind.TUNING;
		}
		if (contains(blob, "accessory power", "power stone", "selected power")) {
			return Kind.POWER;
		}
		if (contains(blob, "necklace", "pendant", "talisman")) {
			return Kind.NECKLACE;
		}
		if (contains(blob, "cloak") && !contains(blob, "power")) {
			return Kind.CLOAK;
		}
		if (contains(blob, "belt")) {
			return Kind.BELT;
		}
		if (contains(blob, "glove", "gauntlet", "bracelet")) {
			return Kind.GLOVES;
		}
		Kind armor = armorSlot(stack, blob);
		if (armor != null) {
			return armor;
		}
		return Kind.OTHER;
	}

	private static Kind armorSlot(ItemStack stack, String blob) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable != null) {
			return switch (equippable.slot()) {
				case HEAD -> Kind.HELMET;
				case CHEST -> Kind.CHEST;
				case LEGS -> Kind.LEGS;
				case FEET -> Kind.BOOTS;
				default -> null;
			};
		}
		if (contains(blob, "helmet", " circlet", "crown", "hood", "mask", "hat")) {
			return Kind.HELMET;
		}
		if (contains(blob, "chestplate", "tunic", "chestplate")) {
			return Kind.CHEST;
		}
		if (contains(blob, "leggings", "pants")) {
			return Kind.LEGS;
		}
		if (contains(blob, "boots")) {
			return Kind.BOOTS;
		}
		return null;
	}

	private static boolean loadoutIndex(int slot, int chest) {
		if (slot < 0 || slot >= chest) {
			return false;
		}
		int col = slot % COLS;
		int row = slot / COLS;
		return col >= LOADOUT_COL0
			&& col < LOADOUT_COL0 + LOADOUT_COLS
			&& row >= LOADOUT_ROW0
			&& row < LOADOUT_ROW0 + LOADOUT_ROWS;
	}

	private static boolean isClose(ItemStack stack, String blob) {
		if (stack.is(Items.BARRIER) || stack.is(Items.ARROW) && contains(blob, "close", "go back", "back")) {
			return contains(blob, "close", "go back", "exit") || stack.is(Items.BARRIER);
		}
		return stack.is(Items.BARRIER) || contains(blob, "close menu", "go back", "click to close");
	}

	private static boolean isNext(ItemStack stack, String blob) {
		return contains(blob, "next page", "next →", "→") && !contains(blob, "previous");
	}

	private static boolean isPrev(ItemStack stack, String blob) {
		return contains(blob, "previous page", "prev page", "←") && !contains(blob, "next page");
	}

	private static boolean selected(ItemStack stack, String name) {
		if (Boolean.TRUE.equals(stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE))) {
			return true;
		}
		String blob = blob(stack);
		return contains(blob, "selected", "currently equipped", "currently active", "this loadout is", "equipped!")
			|| name.contains("✔")
			|| name.contains("✓");
	}

	public static boolean isFiller(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return true;
		}
		if (stack.is(Items.GLASS_PANE)
			|| stack.is(Items.GRAY_STAINED_GLASS_PANE)
			|| stack.is(Items.LIGHT_GRAY_STAINED_GLASS_PANE)
			|| stack.is(Items.BLACK_STAINED_GLASS_PANE)
			|| stack.is(Items.WHITE_STAINED_GLASS_PANE)
			|| stack.is(Items.BROWN_STAINED_GLASS_PANE)) {
			return true;
		}
		String id = ItemIds.skyblockId(stack);
		if (id != null && (id.contains("STAINED_GLASS") || id.endsWith("_GLASS_PANE") || id.equals("GLASS_PANE"))) {
			return true;
		}
		String blob = blob(stack);
		if (contains(blob, "empty slot", "no item", "not equipped", "no accessory", "empty equipment")) {
			return true;
		}
		String name = nameOf(stack).toLowerCase(Locale.ROOT);
		return name.isBlank() || name.equals("empty");
	}

	public static boolean looksLikePet(String blob, ItemStack stack) {
		if (blob != null && PET_NAME.matcher(blob).find()) {
			return true;
		}
		if (contains(blob, "[lvl", " summoned pet", "left-click to summon", "click to summon")) {
			return true;
		}
		String id = ItemIds.skyblockId(stack);
		return id != null && (id.equals("PET") || id.endsWith("_PET"));
	}

	public static String petType(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		String info = extraString(stack, "petInfo");
		if (!info.isBlank()) {
			try {
				JsonObject json = JsonParser.parseString(info).getAsJsonObject();
				if (json.has("type")) {
					return json.get("type").getAsString().trim().toUpperCase(Locale.ROOT);
				}
			} catch (RuntimeException ignored) {
			}
		}
		String name = nameOf(stack);
		Matcher match = PET_NAME.matcher(name);
		if (match.find()) {
			return match.group(1).trim().toUpperCase(Locale.ROOT).replace(' ', '_');
		}
		String id = ItemIds.skyblockId(stack);
		if (id != null && id.endsWith("_PET")) {
			return id.substring(0, id.length() - 4);
		}
		return "";
	}

	public static Component petName(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return Component.empty();
		}
		boolean prior = ItemAppearance.suppress();
		try {
			Component custom = stack.get(DataComponents.CUSTOM_NAME);
			if (custom != null && !custom.getString().isBlank()) {
				return custom;
			}
			return stack.getHoverName();
		} finally {
			ItemAppearance.resume(prior);
		}
	}

	private static String extraString(ItemStack stack, String key) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) {
			return "";
		}
		CompoundTag tag = data.copyTag();
		String value = tag.getStringOr(key, "");
		if (!value.isBlank()) {
			return value;
		}
		return tag.getCompoundOrEmpty("ExtraAttributes").getStringOr(key, "");
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

	private static String emptyName(int index) {
		return "Loadout " + (index + 1);
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

	private static List<Piece> copyPieces(List<Piece> pieces) {
		if (pieces == null || pieces.isEmpty()) {
			return List.of();
		}
		List<Piece> out = new ArrayList<>(pieces.size());
		for (Piece piece : pieces) {
			out.add(piece.copy());
		}
		return List.copyOf(out);
	}

	private static ItemStack copyStack(ItemStack stack) {
		return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
	}
}
