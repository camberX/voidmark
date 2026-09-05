package dev.voidmark.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 3D player wearing a loadout plus a matching Skyblock pet, when one exists.
 */
public final class LoadoutPreview {
	private static final Map<String, EntityType<?>> PETS = pets();
	private static Entity petEntity;
	private static EntityType<?> petType;

	private LoadoutPreview() {
	}

	public static PlayerPreview.Drawn player(
		GuiGraphicsExtractor graphics,
		float x,
		float y,
		float w,
		float h,
		float yaw,
		float pitch,
		PlayerPreview.View view,
		ItemStack helmet,
		ItemStack chest,
		ItemStack legs,
		ItemStack boots
	) {
		return PlayerPreview.drawEquipped(
			graphics,
			x,
			y,
			w,
			h,
			yaw,
			pitch,
			view,
			new PlayerPreview.Gear(helmet, chest, legs, boots)
		);
	}

	public static PlayerPreview.Drawn pet(
		GuiGraphicsExtractor graphics,
		float x,
		float y,
		float w,
		float h,
		float yaw,
		float pitch,
		PlayerPreview.View view,
		String type
	) {
		Entity entity = petEntity(type);
		if (entity == null || view == null) {
			return null;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			entity.setPos(client.player.getX(), client.player.getY(), client.player.getZ());
		}
		entity.setYRot(180f + yaw);
		entity.setXRot(pitch);
		EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
		EntityRenderState state = dispatcher.extractEntity(entity, 1f);
		return PlayerPreview.drawEntity(graphics, state, x, y, w, h, yaw, pitch, view, 0.7f);
	}

	private static Entity petEntity(String type) {
		EntityType<?> mapped = PETS.get(canon(type));
		if (mapped == null) {
			clear();
			return null;
		}
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (level == null) {
			clear();
			return null;
		}
		if (petEntity == null || petType != mapped || petEntity.level() != level) {
			clear();
			try {
				petEntity = mapped.create(level, EntitySpawnReason.LOAD);
			} catch (RuntimeException ignored) {
				petEntity = null;
			}
			petType = mapped;
		}
		return petEntity;
	}

	public static void clear() {
		petEntity = null;
		petType = null;
	}

	private static String canon(String type) {
		if (type == null) {
			return "";
		}
		return type.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace("-", "_");
	}

	private static Map<String, EntityType<?>> pets() {
		Map<String, EntityType<?>> map = new HashMap<>();
		put(map, EntityType.ALLAY, "ALLAY", "SPIRIT");
		put(map, EntityType.ARMADILLO, "ARMADILLO");
		put(map, EntityType.BAT, "BAT");
		put(map, EntityType.BEE, "BEE");
		put(map, EntityType.BLAZE, "BLAZE", "PHOENIX", "BAL");
		put(map, EntityType.CAT, "BLACK_CAT", "CAT");
		put(map, EntityType.CAVE_SPIDER, "CAVE_SPIDER", "TARANTULA");
		put(map, EntityType.CHICKEN, "CHICKEN");
		put(map, EntityType.COW, "COW");
		put(map, EntityType.CREEPER, "CREEPER");
		put(map, EntityType.DOLPHIN, "DOLPHIN", "BLUE_WHALE");
		put(map, EntityType.ENDERMAN, "ENDERMAN");
		put(map, EntityType.ENDERMITE, "ENDERMITE", "SCATHA", "MITE");
		put(map, EntityType.ENDER_DRAGON, "ENDER_DRAGON", "DRAGON", "GOLDEN_DRAGON");
		put(map, EntityType.FOX, "FOX");
		put(map, EntityType.FROG, "FROG");
		put(map, EntityType.GHAST, "GHAST");
		put(map, EntityType.GLOW_SQUID, "GLOW_SQUID", "JELLYFISH");
		put(map, EntityType.GOAT, "GOAT", "REINDEER");
		put(map, EntityType.GUARDIAN, "GUARDIAN", "FLYING_FISH");
		put(map, EntityType.HOGLIN, "HOGLIN", "KUUDRA");
		put(map, EntityType.HORSE, "HORSE", "SKELETON_HORSE");
		put(map, EntityType.IRON_GOLEM, "GOLEM", "IRON_GOLEM", "MITHRIL_GOLEM");
		put(map, EntityType.MAGMA_CUBE, "MAGMA_CUBE");
		put(map, EntityType.MOOSHROOM, "MOOSHROOM", "MUSHROOM_COW");
		put(map, EntityType.OCELOT, "OCELOT", "TIGER", "LION");
		put(map, EntityType.PANDA, "PANDA", "MONKEY");
		put(map, EntityType.PARROT, "PARROT", "GRIFFIN");
		put(map, EntityType.PHANTOM, "PHANTOM");
		put(map, EntityType.PIG, "PIG");
		put(map, EntityType.ZOMBIFIED_PIGLIN, "PIGMAN", "ZOMBIE_PIGMAN", "ZOMBIFIED_PIGLIN");
		put(map, EntityType.RABBIT, "RABBIT");
		put(map, EntityType.RAVAGER, "ELEPHANT", "RAVAGER");
		put(map, EntityType.SHEEP, "SHEEP");
		put(map, EntityType.SILVERFISH, "SILVERFISH", "ROCK", "SNAIL");
		put(map, EntityType.SKELETON, "SKELETON");
		put(map, EntityType.SLIME, "SLIME");
		put(map, EntityType.SNOW_GOLEM, "SNOWMAN", "SNOW_GOLEM", "BABY_YETI", "YETI");
		put(map, EntityType.SPIDER, "SPIDER");
		put(map, EntityType.SQUID, "SQUID");
		put(map, EntityType.TURTLE, "TURTLE");
		put(map, EntityType.VEX, "VEX");
		put(map, EntityType.VILLAGER, "JERRY", "VILLAGER");
		put(map, EntityType.WITCH, "WITCH");
		put(map, EntityType.WITHER_SKELETON, "WITHER_SKELETON");
		put(map, EntityType.WOLF, "WOLF", "HOUND");
		put(map, EntityType.ZOMBIE, "ZOMBIE", "GHOUL");
		put(map, EntityType.CAMEL, "GIRAFFE", "CAMEL");
		put(map, EntityType.ELDER_GUARDIAN, "MEGALODON", "ELDER_GUARDIAN");
		return map;
	}

	private static void put(Map<String, EntityType<?>> map, EntityType<?> type, String... keys) {
		for (String key : keys) {
			map.put(key, type);
		}
	}
}
