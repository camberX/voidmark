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

	public static Entry match(String query) {
		load();
		if (query == null || query.isBlank()) {
			return null;
		}
		String raw = query.trim();
		Entry byId = get(raw.replace(' ', '_'));
		if (byId != null) {
			return byId;
		}
		String needle = raw.toLowerCase(Locale.ROOT);
		Entry exact = null;
		int exacts = 0;
		for (Entry entry : BY_ID.values()) {
			if (entry.name != null && entry.name.toLowerCase(Locale.ROOT).equals(needle)) {
				exact = entry;
				exacts++;
			}
		}
		return exacts == 1 ? exact : null;
	}

	public static List<String> suggest(String prefix, int limit) {
		load();
		if (prefix == null || prefix.isBlank()) {
			return List.of();
		}
		String idNeedle = prefix.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
		String nameNeedle = prefix.trim().toLowerCase(Locale.ROOT);
		List<String> idPrefix = new ArrayList<>();
		List<String> namePrefix = new ArrayList<>();
		List<String> idContains = new ArrayList<>();
		List<String> nameContains = new ArrayList<>();
		for (Entry entry : BY_ID.values()) {
			String id = entry.id;
			String name = entry.name == null ? "" : entry.name.toLowerCase(Locale.ROOT);
			if (id.startsWith(idNeedle)) {
				idPrefix.add(id);
			} else if (!name.isEmpty() && name.startsWith(nameNeedle)) {
				namePrefix.add(id);
			} else if (id.contains(idNeedle)) {
				idContains.add(id);
			} else if (!name.isEmpty() && name.contains(nameNeedle)) {
				nameContains.add(id);
			}
		}
		Collections.sort(idPrefix);
		Collections.sort(namePrefix);
		Collections.sort(idContains);
		Collections.sort(nameContains);
		List<String> out = new ArrayList<>(limit);
		addAll(out, idPrefix, limit);
		addAll(out, namePrefix, limit);
		addAll(out, idContains, limit);
		addAll(out, nameContains, limit);
		return out;
	}

	private static void addAll(List<String> out, List<String> ids, int limit) {
		for (String id : ids) {
			if (out.size() >= limit) {
				return;
			}
			if (!out.contains(id)) {
				out.add(id);
			}
		}
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
