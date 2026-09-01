package dev.voidmark.client.item;

import dev.voidmark.Voidmark;
import net.minecraft.resources.Identifier;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public final class SkyblockItems {
	public record Entry(
		String id,
		Identifier item,
		String name,
		String rarity,
		Identifier model,
		String skinHash,
		int dyeRgb
	) {
	}

	private static final Map<String, Entry> BY_ID = new HashMap<>();
	private static boolean loaded;

	private SkyblockItems() {
	}

	public static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try (InputStream raw = SkyblockItems.class.getResourceAsStream("/assets/voidmark/skyblock_items.tsv.gz")) {
			if (raw == null) {
				Voidmark.LOGGER.warn("Skyblock item catalog is missing");
				return;
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.isEmpty()) {
						continue;
					}
					Entry entry = parse(line);
					if (entry != null) {
						BY_ID.put(entry.id, entry);
					}
				}
			}
			Voidmark.LOGGER.info("Loaded {} Skyblock item ids", BY_ID.size());
		} catch (Exception exception) {
			Voidmark.LOGGER.warn("Could not read Skyblock item catalog", exception);
		}
	}

	public static Entry get(String id) {
		load();
		if (id == null || id.isBlank()) {
			return null;
		}
		return BY_ID.get(id.trim().toUpperCase(Locale.ROOT));
	}

	public static boolean has(String id) {
		return get(id) != null;
	}

	public static List<String> suggest(String prefix, int limit) {
		load();
		if (prefix == null || prefix.isBlank()) {
			return List.of();
		}
		String needle = prefix.trim().toUpperCase(Locale.ROOT);
		List<String> prefixHits = new ArrayList<>();
		List<String> containsHits = new ArrayList<>();
		for (String id : BY_ID.keySet()) {
			if (id.startsWith(needle)) {
				prefixHits.add(id);
			} else if (id.contains(needle)) {
				containsHits.add(id);
			}
		}
		Collections.sort(prefixHits);
		Collections.sort(containsHits);
		List<String> out = new ArrayList<>(limit);
		for (String id : prefixHits) {
			if (out.size() >= limit) {
				break;
			}
			out.add(id);
		}
		for (String id : containsHits) {
			if (out.size() >= limit) {
				break;
			}
			out.add(id);
		}
		return out;
	}

	private static Entry parse(String line) {
		String[] parts = line.split("\t", -1);
		if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
			return null;
		}
		String id = parts[0].trim().toUpperCase(Locale.ROOT);
		Identifier item = identifier(parts[1]);
		if (item == null) {
			item = Identifier.withDefaultNamespace("paper");
		}
		String name = parts.length > 2 && !parts[2].isBlank() ? parts[2] : id;
		String rarity = parts.length > 3 ? parts[3] : "";
		Identifier model = parts.length > 4 ? identifier(parts[4]) : null;
		String skin = parts.length > 5 ? parts[5].trim() : "";
		int dye = -1;
		if (parts.length > 6 && parts[6].length() == 6) {
			try {
				dye = Integer.parseInt(parts[6], 16);
			} catch (NumberFormatException ignored) {
				dye = -1;
			}
		}
		return new Entry(id, item, name, rarity, model, skin, dye);
	}

	private static Identifier identifier(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String value = raw.trim();
		if (!value.contains(":")) {
			value = "minecraft:" + value;
		}
		return Identifier.tryParse(value.toLowerCase(Locale.ROOT));
	}
}
