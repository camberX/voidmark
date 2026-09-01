package dev.voidmark.client.item;

import dev.voidmark.Voidmark;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public final class SkyblockRecipes {
	public record Recipe(String id, long output, Map<String, Long> ingredients, String type) {
	}

	private static final Map<String, Recipe> BY_ID = new HashMap<>();
	private static final Map<String, Map<String, Long>> RAW_CACHE = new HashMap<>();
	private static boolean loaded;

	private SkyblockRecipes() {
	}

	public static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try (InputStream raw = SkyblockRecipes.class.getResourceAsStream("/assets/voidmark/skyblock_recipes.tsv.gz")) {
			if (raw == null) {
				Voidmark.LOGGER.warn("Skyblock recipe catalog is missing");
				return;
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					Recipe recipe = parse(line);
					if (recipe != null) {
						BY_ID.put(recipe.id, recipe);
					}
				}
			}
			Voidmark.LOGGER.info("Loaded {} Skyblock recipes", BY_ID.size());
		} catch (Exception exception) {
			Voidmark.LOGGER.warn("Could not read Skyblock recipes", exception);
		}
	}

	public static Recipe get(String id) {
		load();
		if (id == null || id.isBlank()) {
			return null;
		}
		return BY_ID.get(normalize(id));
	}

	public static boolean has(String id) {
		return get(id) != null;
	}

	public static Map<String, Long> expand(String id) {
		return expand(id, 1L);
	}

	public static Map<String, Long> expand(String id, long quantity) {
		load();
		if (id == null || id.isBlank() || quantity <= 0L) {
			return Map.of();
		}
		Map<String, Long> out = new LinkedHashMap<>();
		walk(normalize(id), quantity, out, new HashMap<>());
		return out;
	}

	private static void walk(String id, long quantity, Map<String, Long> out, Map<String, Integer> stack) {
		if (quantity <= 0L) {
			return;
		}
		int depth = stack.getOrDefault(id, 0);
		if (depth > 0 || stack.size() > 32) {
			out.merge(id, quantity, Long::sum);
			return;
		}
		Recipe recipe = BY_ID.get(id);
		if (recipe == null || recipe.ingredients.isEmpty()) {
			out.merge(id, quantity, Long::sum);
			return;
		}
		String cacheKey = id;
		Map<String, Long> oneCraft = RAW_CACHE.get(cacheKey);
		if (oneCraft == null) {
			stack.put(id, 1);
			Map<String, Long> craft = new LinkedHashMap<>();
			for (Map.Entry<String, Long> ingredient : recipe.ingredients.entrySet()) {
				walk(ingredient.getKey(), ingredient.getValue(), craft, stack);
			}
			stack.remove(id);
			oneCraft = Collections.unmodifiableMap(craft);
			RAW_CACHE.put(cacheKey, oneCraft);
		}
		long crafts = (quantity + recipe.output - 1L) / recipe.output;
		for (Map.Entry<String, Long> leaf : oneCraft.entrySet()) {
			out.merge(leaf.getKey(), leaf.getValue() * crafts, Long::sum);
		}
	}

	public static String normalize(String raw) {
		if (raw == null) {
			return "";
		}
		String id = raw.trim();
		int colon = id.indexOf(':');
		if (colon >= 0) {
			String prefix = id.substring(0, colon).toLowerCase(Locale.ROOT);
			if (prefix.equals("sb") || prefix.equals("skyblock") || prefix.equals("minecraft")) {
				id = id.substring(colon + 1);
			}
		}
		return id.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
	}

	private static Recipe parse(String line) {
		String[] parts = line.split("\t", -1);
		if (parts.length < 3 || parts[0].isBlank() || parts[2].isBlank()) {
			return null;
		}
		String id = normalize(parts[0]);
		long output = 1L;
		try {
			output = Math.max(1L, Long.parseLong(parts[1].trim()));
		} catch (NumberFormatException ignored) {
		}
		Map<String, Long> ingredients = new LinkedHashMap<>();
		for (String piece : parts[2].split(",")) {
			if (piece.isBlank()) {
				continue;
			}
			int at = piece.lastIndexOf(':');
			String name = at < 0 ? piece : piece.substring(0, at);
			long count = 1L;
			if (at >= 0) {
				try {
					count = Long.parseLong(piece.substring(at + 1).trim());
				} catch (NumberFormatException ignored) {
					count = 1L;
				}
			}
			name = normalize(name);
			if (!name.isBlank() && count > 0L) {
				ingredients.merge(name, count, Long::sum);
			}
		}
		if (ingredients.isEmpty()) {
			return null;
		}
		String type = parts.length > 3 ? parts[3].trim() : "crafting";
		return new Recipe(id, output, Map.copyOf(ingredients), type);
	}
}
