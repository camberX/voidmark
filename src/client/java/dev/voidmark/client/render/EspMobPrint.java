package dev.voidmark.client.render;

import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.item.ItemIds;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * After {@code /vm esp} sees one named mob, remember its type and armor so
 * other copies can glow at render distance before their hologram appears.
 */
public final class EspMobPrint {
	private static final EquipmentSlot[] SLOTS = {
		EquipmentSlot.HEAD,
		EquipmentSlot.CHEST,
		EquipmentSlot.LEGS,
		EquipmentSlot.FEET,
		EquipmentSlot.MAINHAND,
		EquipmentSlot.OFFHAND
	};
	private static final int MAX_PER_NEEDLE = 8;
	private static final int MAX_TOTAL = 64;

	public static final class Saved {
		public String needle = "";
		public String type = "";
		public boolean baby;
		public List<String> gear = new ArrayList<>();
	}

	private EspMobPrint() {
	}

	public static void learn(String needle, LivingEntity entity) {
		Saved sample = capture(needle, entity);
		if (sample == null) {
			return;
		}
		VoidmarkConfig config = VoidmarkConfig.get();
		if (config.mobGlowPrints == null) {
			config.mobGlowPrints = new ArrayList<>();
		}
		for (Saved existing : config.mobGlowPrints) {
			if (same(existing, sample)) {
				return;
			}
		}
		List<Saved> next = new ArrayList<>();
		int forNeedle = 0;
		for (Saved existing : config.mobGlowPrints) {
			if (needle.equals(existing.needle)) {
				forNeedle++;
				if (forNeedle >= MAX_PER_NEEDLE) {
					continue;
				}
			}
			next.add(existing);
		}
		next.add(sample);
		while (next.size() > MAX_TOTAL) {
			next.remove(0);
		}
		config.mobGlowPrints = next;
		config.save();
	}

	public static boolean matches(String needle, LivingEntity entity) {
		VoidmarkConfig config = VoidmarkConfig.get();
		if (config.mobGlowPrints == null || config.mobGlowPrints.isEmpty()) {
			return false;
		}
		Saved candidate = capture(needle, entity);
		if (candidate == null) {
			return false;
		}
		for (Saved sample : config.mobGlowPrints) {
			if (needle.equals(sample.needle) && looksLike(sample, candidate)) {
				return true;
			}
		}
		return false;
	}

	public static int learned(String needle) {
		VoidmarkConfig config = VoidmarkConfig.get();
		if (config.mobGlowPrints == null) {
			return 0;
		}
		int n = 0;
		for (Saved sample : config.mobGlowPrints) {
			if (needle.equals(sample.needle)) {
				n++;
			}
		}
		return n;
	}

	private static Saved capture(String needle, LivingEntity entity) {
		if (needle == null || needle.isBlank() || entity == null) {
			return null;
		}
		Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		if (type == null || entity.getType() == EntityType.PLAYER) {
			return null;
		}
		Saved sample = new Saved();
		sample.needle = needle.toLowerCase(Locale.ROOT).trim();
		sample.type = type.toString();
		sample.baby = entity.isBaby();
		sample.gear = new ArrayList<>();
		for (EquipmentSlot slot : SLOTS) {
			sample.gear.add(slot.getSerializedName() + "=" + gearOf(entity.getItemBySlot(slot)));
		}
		return sample;
	}

	private static String gearOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		String skyblock = ItemIds.skyblockId(stack);
		Identifier item = BuiltInRegistries.ITEM.getKey(stack.getItem());
		String id = skyblock != null ? "sb:" + skyblock : item == null ? "unknown" : item.toString();
		DyedItemColor dyed = stack.get(DataComponents.DYED_COLOR);
		if (dyed != null) {
			id += "#" + Integer.toHexString(dyed.rgb() & 0xFFFFFF);
		}
		Identifier model = stack.get(DataComponents.ITEM_MODEL);
		if (model != null) {
			id += "@" + model;
		}
		return id.toLowerCase(Locale.ROOT);
	}

	private static boolean same(Saved left, Saved right) {
		return looksLike(left, right) && Objects.equals(left.needle, right.needle);
	}

	private static boolean looksLike(Saved sample, Saved candidate) {
		if (sample == null || candidate == null || !sample.type.equals(candidate.type) || sample.baby != candidate.baby) {
			return false;
		}
		if (sample.gear == null || candidate.gear == null || sample.gear.size() != candidate.gear.size()) {
			return sample.gear == null || sample.gear.isEmpty();
		}
		for (int i = 0; i < sample.gear.size(); i++) {
			String want = value(sample.gear.get(i));
			String have = value(candidate.gear.get(i));
			if (!want.equals(have)) {
				return false;
			}
		}
		return true;
	}

	private static String value(String entry) {
		if (entry == null) {
			return "";
		}
		int eq = entry.indexOf('=');
		return eq < 0 ? entry : entry.substring(eq + 1);
	}
}
