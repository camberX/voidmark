package dev.voidmark.client.item;

import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RawmatsTracker {
	private static final String[] CRIMSON = {"HOT_", "BURNING_", "FIERY_", "INFERNAL_"};

	public record Line(String id, String name, ItemStack icon, long have, long need) {
		public boolean done() {
			return have >= need && need > 0L;
		}

		public float progress() {
			if (need <= 0L) {
				return 0f;
			}
			return Math.min(1f, have / (float) need);
		}
	}

	public record Snapshot(
		String id,
		String name,
		ItemStack icon,
		List<Line> lines,
		long complete,
		long total,
		boolean recipe,
		boolean sawEnder,
		boolean sawBackpack
	) {
		public static Snapshot none() {
			return new Snapshot("", "", ItemStack.EMPTY, List.of(), 0L, 0L, false, false, false);
		}

		public boolean present() {
			return id != null && !id.isBlank();
		}

		public float progress() {
			if (lines.isEmpty()) {
				return 0f;
			}
			double sum = 0d;
			for (Line line : lines) {
				sum += line.progress();
			}
			return (float) (sum / lines.size());
		}
	}

	private RawmatsTracker() {
	}

	public static void init() {
		SkyblockRecipes.load();
	}

	public static void tick(Minecraft client) {
		ItemStorage.tick(client);
		SkyblockProfileApi.tick(client);
	}

	public static boolean tracking() {
		String id = VoidmarkConfig.get().rawmatsItemId;
		return id != null && !id.isBlank();
	}

	public static void clear() {
		VoidmarkConfig config = VoidmarkConfig.get();
		config.rawmatsItemId = "";
		config.save();
	}

	public static String set(String query) {
		String id = SkyblockRecipes.normalize(query);
		if (id.isBlank()) {
			return "";
		}
		VoidmarkConfig config = VoidmarkConfig.get();
		config.rawmatsItemId = id;
		config.rawmatsHudEnabled = true;
		config.save();
		SkyblockProfileApi.refresh();
		return id;
	}

	public static Snapshot snapshot() {
		String id = SkyblockRecipes.normalize(VoidmarkConfig.get().rawmatsItemId);
		if (id.isBlank()) {
			return Snapshot.none();
		}
		SkyblockRecipes.load();
		SkyblockRecipes.Expand expand = VoidmarkConfig.get().rawmatsEnchanted
			? SkyblockRecipes.Expand.ENCHANTED
			: SkyblockRecipes.Expand.RAW;
		Map<String, Long> need = SkyblockRecipes.expand(id, 1L, expand);
		boolean recipe = SkyblockRecipes.has(id);
		if (need.isEmpty()) {
			need = new LinkedHashMap<>();
			need.put(id, 1L);
		}
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;
		Map<String, Long> owned = player == null ? Map.of() : ItemStorage.counts(player);
		Map<String, Long> have = credit(id, need, owned, expand);
		List<Line> lines = new ArrayList<>();
		for (Map.Entry<String, Long> entry : need.entrySet()) {
			long required = entry.getValue();
			if (required <= 0L) {
				continue;
			}
			String leaf = entry.getKey();
			lines.add(new Line(leaf, nameOf(leaf), iconOf(leaf), have.getOrDefault(leaf, 0L), required));
		}
		lines.sort(Comparator
			.comparing((Line line) -> line.done())
			.thenComparing(line -> line.have() <= 0L ? 0 : 1)
			.thenComparing(Line::name, String.CASE_INSENSITIVE_ORDER));
		long complete = 0L;
		for (Line line : lines) {
			if (line.done()) {
				complete++;
			}
		}
		return new Snapshot(
			id,
			nameOf(id),
			iconOf(id),
			List.copyOf(lines),
			complete,
			lines.size(),
			recipe,
			ItemStorage.sawEnder(),
			ItemStorage.sawBackpack()
		);
	}

	private static Map<String, Long> credit(
		String target,
		Map<String, Long> need,
		Map<String, Long> owned,
		SkyblockRecipes.Expand expand
	) {
		Map<String, Long> have = new HashMap<>();
		Map<String, Long> leftover = new HashMap<>();
		for (Map.Entry<String, Long> entry : owned.entrySet()) {
			String id = entry.getKey();
			long count = entry.getValue();
			if (count <= 0L || sameItem(id, target)) {
				continue;
			}
			boolean direct = false;
			for (String match : countsAs(id)) {
				if (need.containsKey(match)) {
					have.merge(match, count, Long::sum);
					direct = true;
					break;
				}
			}
			if (direct) {
				continue;
			}
			Map<String, Long> leaves = SkyblockRecipes.has(id)
				? SkyblockRecipes.expand(id, count, expand)
				: Map.of(id, count);
			if (leaves.size() != 1) {
				continue;
			}
			Map.Entry<String, Long> leaf = leaves.entrySet().iterator().next();
			if (need.containsKey(leaf.getKey())) {
				have.merge(leaf.getKey(), leaf.getValue(), Long::sum);
			} else if (expand == SkyblockRecipes.Expand.ENCHANTED && ingredientOfNeed(leaf.getKey(), need)) {
				leftover.merge(leaf.getKey(), leaf.getValue(), Long::sum);
			}
		}
		if (expand == SkyblockRecipes.Expand.ENCHANTED) {
			craftUp(need, leftover, have);
		}
		return have;
	}

	/** True when {@code id} is a raw ingredient of a needed compact, e.g. cobble → enchanted cobble. */
	private static boolean ingredientOfNeed(String id, Map<String, Long> need) {
		for (String leaf : need.keySet()) {
			SkyblockRecipes.Recipe recipe = SkyblockRecipes.get(leaf);
			if (recipe == null) {
				continue;
			}
			if (recipe.ingredients().size() == 1 && recipe.ingredients().containsKey(id)) {
				return true;
			}
		}
		return false;
	}

	private static void craftUp(Map<String, Long> need, Map<String, Long> leftover, Map<String, Long> have) {
		for (String leaf : need.keySet()) {
			SkyblockRecipes.Recipe recipe = SkyblockRecipes.get(leaf);
			if (recipe == null || recipe.ingredients().isEmpty()) {
				continue;
			}
			long crafts = Long.MAX_VALUE;
			for (Map.Entry<String, Long> ingredient : recipe.ingredients().entrySet()) {
				if (need.containsKey(ingredient.getKey())) {
					crafts = 0L;
					break;
				}
				long avail = leftover.getOrDefault(ingredient.getKey(), 0L);
				crafts = Math.min(crafts, avail / ingredient.getValue());
			}
			if (crafts <= 0L || crafts == Long.MAX_VALUE) {
				continue;
			}
			have.merge(leaf, crafts * recipe.output(), Long::sum);
			for (Map.Entry<String, Long> ingredient : recipe.ingredients().entrySet()) {
				leftover.merge(ingredient.getKey(), -crafts * ingredient.getValue(), Long::sum);
			}
		}
	}

	private static boolean sameItem(String left, String right) {
		if (left.equals(right)) {
			return true;
		}
		return stripStar(left).equals(stripStar(right));
	}

	private static List<String> countsAs(String id) {
		List<String> out = new ArrayList<>();
		out.add(id);
		String star = stripStar(id);
		if (!star.equals(id)) {
			out.add(star);
		}
		for (int i = 0; i < CRIMSON.length; i++) {
			if (!id.startsWith(CRIMSON[i])) {
				continue;
			}
			String rest = id.substring(CRIMSON[i].length());
			for (int j = 0; j < i; j++) {
				out.add(CRIMSON[j] + rest);
			}
			out.add(rest);
			break;
		}
		return out;
	}

	private static String stripStar(String id) {
		return id.startsWith("STARRED_") ? id.substring(8) : id;
	}

	static String nameOf(String id) {
		SkyblockItems.Entry entry = SkyblockItems.get(id);
		if (entry != null && entry.name() != null && !entry.name().isBlank()) {
			return entry.name();
		}
		String pretty = id.replace('_', ' ').toLowerCase(Locale.ROOT);
		if (pretty.isEmpty()) {
			return id;
		}
		StringBuilder out = new StringBuilder(pretty.length());
		boolean cap = true;
		for (int i = 0; i < pretty.length(); i++) {
			char c = pretty.charAt(i);
			if (cap && c >= 'a' && c <= 'z') {
				out.append((char) (c - 32));
				cap = false;
			} else {
				out.append(c);
				cap = c == ' ';
			}
		}
		return out.toString();
	}

	private static ItemStack iconOf(String id) {
		ItemIds.Preview preview = ItemIds.resolve("sb:" + id);
		if (preview.stack() != null && !preview.stack().isEmpty()) {
			return preview.stack();
		}
		return ItemIds.resolve("minecraft:" + id.toLowerCase(Locale.ROOT)).stack();
	}
}
