package dev.voidmark.client.item;

import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a base Skyblock tooltip into a maxed one: recombobulated rarity,
 * dungeon stars + master stars, filled gemstone slots, hot-potato stats,
 * and typical max enchants — only the pieces that lore already implies.
 */
public final class SkyblockMaxed {
	private static final String[] RARITY = {
		"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY SPECIAL", "ULTIMATE"
	};
	private static final String[] RARITY_COLOR = {
		"§f", "§a", "§9", "§5", "§6", "§d", "§b", "§c", "§c", "§4"
	};
	private static final Pattern STAT = Pattern.compile("(§7(?:Damage|Strength|Crit Chance|Crit Damage|Health|Defense|Intelligence|Speed|Ferocity|Magic Find|True Defense|Attack Speed|Sea Creature Chance|Mining Speed|Mining Fortune|Farming Fortune|Foraging Fortune|Pristine|Vitality|Mending|Ability Damage): §[0-9a-f])(\\+?)(\\d+)(%?)");
	private static final Pattern RARITY_LINE = Pattern.compile("§[0-9a-fk-or]§l(.+)$");

	private SkyblockMaxed() {
	}

	public static ItemText apply(String name, List<String> lore) {
		List<String> lines = new ArrayList<>();
		if (lore != null) {
			lines.addAll(lore);
		}
		String rarity = rarityLine(lines);
		Kind kind = kind(rarity);
		boolean dungeon = rarity != null && rarity.toUpperCase(Locale.ROOT).contains("DUNGEON");
		boolean gems = hasGemLine(lines);
		boolean stats = hasStatLines(lines);
		String nextName = maxName(name, dungeon, rarity);
		if (rarity != null) {
			recomb(lines);
		}
		if (gems) {
			fillGems(lines);
		}
		if (stats) {
			bumpStats(lines, kind);
		}
		if (kind != Kind.OTHER && !alreadyEnchanted(lines)) {
			insertEnchants(lines, kind);
		}
		return ItemText.fromLegacy(nextName, lines);
	}

	public static boolean supports(List<String> lore) {
		String rarity = rarityLine(lore);
		return rarity != null
			|| hasGemLine(lore)
			|| hasStatLines(lore)
			|| (rarity != null && rarity.toUpperCase(Locale.ROOT).contains("DUNGEON"));
	}

	private static String maxName(String name, boolean dungeon, String rarity) {
		String raw = name == null ? "" : name;
		String stripped = raw
			.replaceAll("§[0-9a-fk-or]✪", "")
			.replaceAll("✪", "")
			.replaceAll("[➊➋➌➍➎]", "")
			.replaceAll(" +$", "");
		if (rarity != null) {
			int from = rarityIndex(rarity);
			if (from >= 0 && from + 1 < RARITY.length) {
				stripped = recolor(stripped, RARITY_COLOR[from + 1]);
			}
		}
		if (dungeon) {
			stripped = stripped + " §6✪✪✪✪✪§c➊➋➌➍➎";
		}
		return stripped;
	}

	private static String recolor(String name, String color) {
		if (name.isEmpty()) {
			return color;
		}
		if (name.charAt(0) == '§' && name.length() >= 2) {
			return color + name.substring(2);
		}
		return color + name;
	}

	private static void recomb(List<String> lines) {
		int index = rarityIndexIn(lines);
		if (index < 0) {
			return;
		}
		String line = lines.get(index);
		int from = rarityIndex(line);
		if (from < 0 || from + 1 >= RARITY.length) {
			return;
		}
		String next = RARITY[from + 1];
		String color = RARITY_COLOR[from + 1];
		lines.set(index, line.replaceFirst("§[0-9a-fk-or]§l", color + "§l").replace(RARITY[from], next));
	}

	private static void fillGems(List<String> lines) {
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			if (!gemLine(line)) {
				continue;
			}
			String filled = line.replace("§8[", "§d[").replace("§7[", "§d[");
			filled = filled.replace("§8]", "§d]").replace("§7]", "§d]");
			lines.set(i, filled);
			return;
		}
	}

	private static void bumpStats(List<String> lines, Kind kind) {
		for (int i = 0; i < lines.size(); i++) {
			Matcher matcher = STAT.matcher(lines.get(i));
			if (!matcher.find()) {
				continue;
			}
			String label = matcher.group(1);
			int value = Integer.parseInt(matcher.group(3));
			String pct = matcher.group(4) == null ? "" : matcher.group(4);
			String key = ChatFormatting.stripFormatting(label).toLowerCase(Locale.ROOT);
			int extra = extra(key, kind);
			if (extra == 0) {
				continue;
			}
			lines.set(i, matcher.replaceFirst(Matcher.quoteReplacement(label + "+" + (value + extra) + pct)));
		}
	}

	private static int extra(String key, Kind kind) {
		if (key.contains("damage") || key.contains("strength")) {
			return kind == Kind.ARMOR ? 10 : 30;
		}
		if (key.contains("health")) {
			return 60;
		}
		if (key.contains("defense")) {
			return 30;
		}
		if (key.contains("intelligence") || key.contains("crit")) {
			return 10;
		}
		return 0;
	}

	private static void insertEnchants(List<String> lines, Kind kind) {
		int at = insertAt(lines);
		List<String> extra = enchants(kind);
		if (extra.isEmpty()) {
			return;
		}
		if (at > 0 && !lines.get(at - 1).isBlank()) {
			lines.add(at, "");
			at++;
		}
		lines.addAll(at, extra);
		if (at + extra.size() < lines.size() && !lines.get(at + extra.size()).isBlank()) {
			lines.add(at + extra.size(), "");
		}
	}

	private static int insertAt(List<String> lines) {
		int lastStat = -1;
		for (int i = 0; i < lines.size(); i++) {
			String plain = plain(lines.get(i));
			if (STAT.matcher(lines.get(i)).find() || gemLine(lines.get(i)) || plain.startsWith("Gear Score:")) {
				lastStat = i;
			}
		}
		if (lastStat >= 0) {
			return lastStat + 1;
		}
		int rarity = rarityIndexIn(lines);
		return rarity < 0 ? lines.size() : Math.max(0, rarity - 1);
	}

	private static List<String> enchants(Kind kind) {
		return switch (kind) {
			case SWORD -> List.of(
				"§9Champion X, Critical VII, Cubism V, Ender Slayer VII",
				"§9Execute V, First Strike IV, Giant Killer VII, Lethality VI",
				"§9Looting V, Scavenger V, Sharpness VII, Syphon V",
				"§9Thunderlord VII, Titan Killer VII, Vampirism VI, Venomous VI",
				"§d§lUltimate Wise V"
			);
			case BOW -> List.of(
				"§9Chance V, Cubism V, Dragon Tracer V, Flame II",
				"§9Impaling III, Infinite Quiver X, Overload V, Piercing I",
				"§9Power VII, Snipe IV, Telekinesis I",
				"§d§lUltimate Fatal Tempo V"
			);
			case ARMOR -> List.of(
				"§9Growth VII, Protection VII, Thorns III, Rejuvenate V",
				"§9True Protection I",
				"§d§lLast Stand V"
			);
			case TOOL -> List.of(
				"§9Efficiency X, Fortune IV, Pristine V, Silk Touch I",
				"§d§lUltimate Wise V"
			);
			case FISHING -> List.of(
				"§9Angler VI, Blessing VI, Caster VI, Frail VI",
				"§9Luck of the Sea VI, Lure VI, Magnet VI, Spiked Hook VI",
				"§d§lFlash V"
			);
			default -> List.of();
		};
	}

	private static boolean alreadyEnchanted(List<String> lines) {
		for (String line : lines) {
			String plain = plain(line).toLowerCase(Locale.ROOT);
			if (plain.contains("sharpness") || plain.contains("protection") || plain.contains("growth vii")
				|| plain.contains("power vii") || plain.contains("efficiency") || plain.contains("ultimate wise")
				|| plain.contains("angler")) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasStatLines(List<String> lines) {
		for (String line : lines) {
			if (STAT.matcher(line).find()) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasGemLine(List<String> lines) {
		for (String line : lines) {
			if (gemLine(line)) {
				return true;
			}
		}
		return false;
	}

	private static boolean gemLine(String line) {
		String plain = plain(line).toLowerCase(Locale.ROOT);
		return plain.startsWith("gemstones:")
			|| plain.startsWith("gemstone slots:")
			|| plain.startsWith("unlocked gemstone slots:")
			|| plain.contains("gemstone slot");
	}

	private static String rarityLine(List<String> lines) {
		int index = rarityIndexIn(lines);
		return index < 0 ? null : lines.get(index);
	}

	private static int rarityIndexIn(List<String> lines) {
		for (int i = lines.size() - 1; i >= 0; i--) {
			String plain = plain(lines.get(i)).toUpperCase(Locale.ROOT);
			for (String rarity : RARITY) {
				if (plain.contains(rarity) && RARITY_LINE.matcher(lines.get(i)).find()) {
					return i;
				}
			}
		}
		return -1;
	}

	private static int rarityIndex(String line) {
		String plain = plain(line).toUpperCase(Locale.ROOT);
		int best = -1;
		int bestLen = 0;
		for (int i = 0; i < RARITY.length; i++) {
			if (plain.contains(RARITY[i]) && RARITY[i].length() > bestLen) {
				best = i;
				bestLen = RARITY[i].length();
			}
		}
		return best;
	}

	private static Kind kind(String rarity) {
		if (rarity == null) {
			return Kind.OTHER;
		}
		String plain = plain(rarity).toUpperCase(Locale.ROOT);
		if (plain.contains("SWORD") || plain.contains("DAGGER") || plain.contains("KATANA") || plain.contains("CLEAVER")) {
			return Kind.SWORD;
		}
		if (plain.contains("BOW") || plain.contains("SHORTBOW")) {
			return Kind.BOW;
		}
		if (plain.contains("HELMET") || plain.contains("CHESTPLATE") || plain.contains("LEGGINGS")
			|| plain.contains("BOOTS") || plain.contains("ARMOR")) {
			return Kind.ARMOR;
		}
		if (plain.contains("PICKAXE") || plain.contains("DRILL") || plain.contains("GAUNTLET") || plain.contains("SHOVEL")
			|| plain.contains("AXE") || plain.contains("HOE")) {
			return Kind.TOOL;
		}
		if (plain.contains("ROD") || plain.contains("FISHING")) {
			return Kind.FISHING;
		}
		return Kind.OTHER;
	}

	private static String plain(String line) {
		return ChatFormatting.stripFormatting(line == null ? "" : line.replace('&', '§'));
	}

	private enum Kind {
		SWORD, BOW, ARMOR, TOOL, FISHING, OTHER
	}
}
