package dev.voidmark.client.render;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Every living mob in the vanilla entity registry, minus players and armor stands.
 */
public final class MobCatalog {
	public static final String DEFAULT_ID = "minecraft:zombie";

	private static List<Entry> cache;

	private MobCatalog() {
	}

	public static List<Entry> all() {
		if (cache == null) {
			cache = build();
		}
		return cache;
	}

	public static List<Entry> filtered(String query) {
		List<Entry> all = all();
		String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) {
			return all;
		}
		List<Entry> out = new ArrayList<>();
		for (Entry entry : all) {
			if (entry.name.toLowerCase(Locale.ROOT).contains(q) || entry.id.toString().contains(q)) {
				out.add(entry);
			}
		}
		return out;
	}

	public static EntityType<?> type(String id) {
		Identifier key = parseId(id);
		if (key == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(key)) {
			return null;
		}
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(key);
		return isListable(type) ? type : null;
	}

	public static String normalizeId(String id) {
		return type(id) == null ? DEFAULT_ID : parseId(id).toString();
	}

	public static String displayName(String id) {
		EntityType<?> type = type(id);
		if (type == null) {
			return "None";
		}
		return label(type);
	}

	public static Identifier parseId(String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		return Identifier.tryParse(id.trim().toLowerCase(Locale.ROOT));
	}

	private static List<Entry> build() {
		List<Entry> out = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
			if (!isListable(type)) {
				continue;
			}
			out.add(new Entry(id, label(type), type));
		}
		out.sort(Comparator.comparing(entry -> entry.name.toLowerCase(Locale.ROOT)));
		return List.copyOf(out);
	}

	private static boolean isListable(EntityType<?> type) {
		if (type == null) {
			return false;
		}
		Class<? extends Entity> base = type.getBaseClass();
		if (base == null || !LivingEntity.class.isAssignableFrom(base)) {
			return false;
		}
		if (Player.class.isAssignableFrom(base) || Avatar.class.isAssignableFrom(base)) {
			return false;
		}
		return !ArmorStand.class.isAssignableFrom(base);
	}

	private static String label(EntityType<?> type) {
		String name = type.getDescription().getString();
		if (name == null || name.isBlank() || name.startsWith("entity.")) {
			return type.toShortString();
		}
		return name;
	}

	public record Entry(Identifier id, String name, EntityType<?> type) {
	}
}
