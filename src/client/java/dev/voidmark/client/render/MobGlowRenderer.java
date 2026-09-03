package dev.voidmark.client.render;

import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Marks matching mobs for the entity-outline buffer. A custom post shader
 * turns that silhouette into a clean outward gradient, not vanilla sobel.
 * <p>
 * A mob glows when its type is selected in the ESP list, or when
 * {@code /vm esp <text>} is set and its nametag (or a hologram nametag
 * riding / floating above it) contains that text.
 */
public final class MobGlowRenderer {
	private static final double MAX_RANGE = 96.0;
	private static final double MAX_RANGE_SQ = MAX_RANGE * MAX_RANGE;
	private static final double HOLOGRAM_RANGE = 3.25;
	private static final Pattern STRIP = Pattern.compile("§.");
	private static List<String> cachedIds;
	private static Set<EntityType<?>> cachedTypes = Set.of();
	private static int nameTick = Integer.MIN_VALUE;
	private static String nameNeedle = "";
	private static Set<Integer> nameIds = Set.of();

	private MobGlowRenderer() {
	}

	public static void init() {
	}

	public static int nearbyCount() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return 0;
		}
		int count = 0;
		for (Entity entity : client.level.entitiesForRendering()) {
			if (isEspTarget(entity, client.player)) {
				count++;
			}
		}
		return count;
	}

	/**
	 * True when this entity should enter Minecraft's outline pass, including
	 * mobs that already have the vanilla GLOWING flag.
	 */
	public static boolean shouldForceGlow(Entity entity) {
		return outlineColor(entity) != 0;
	}

	public static int outlineColor(Entity entity) {
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.mobGlowEnabled || entity == null) {
			return 0;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || entity == client.player) {
			return 0;
		}
		if (!isEspTarget(entity, client.player)) {
			return 0;
		}
		Vec3 camera = client.gameRenderer.getMainCamera().position();
		if (entity.distanceToSqr(camera) > MAX_RANGE_SQ) {
			return 0;
		}
		if (!config.mobGlowThroughWalls && occluded(client, camera, entity.getEyePosition())) {
			return 0;
		}
		return packColor(config);
	}

	public static int packColor(VoidmarkConfig config) {
		float opacity = VoidmarkConfig.clamp(config.mobGlowOpacity, 0.15f, 0.90f);
		int alpha = Math.round(opacity * 255f);
		return (alpha << 24) | (config.mobGlowRgb & 0xFFFFFF);
	}

	public static boolean listed(EntityType<?> type) {
		return type != null && selectedTypes().contains(type);
	}

	public static boolean listed(Entity entity) {
		Minecraft client = Minecraft.getInstance();
		return client.player != null && isEspTarget(entity, client.player);
	}

	private static boolean isEspTarget(Entity entity, Entity player) {
		if (entity == null || entity == player || entity.isRemoved()) {
			return false;
		}
		if (entity instanceof LivingEntity living && !living.isAlive()) {
			return false;
		}
		if (listed(entity.getType())) {
			return true;
		}
		return nametagHit(entity);
	}

	private static Set<EntityType<?>> selectedTypes() {
		List<String> ids = VoidmarkConfig.get().mobGlowIds;
		if (ids == cachedIds) {
			return cachedTypes;
		}
		cachedIds = ids;
		Set<EntityType<?>> types = new HashSet<>();
		if (ids != null) {
			for (String id : ids) {
				EntityType<?> type = MobCatalog.type(id);
				if (type != null) {
					types.add(type);
				}
			}
		}
		cachedTypes = types;
		return types;
	}

	private static boolean nametagHit(Entity entity) {
		VoidmarkConfig config = VoidmarkConfig.get();
		String needle = config.mobGlowName == null ? "" : config.mobGlowName.trim();
		if (needle.isEmpty()) {
			return false;
		}
		refreshNameIds(needle);
		return nameIds.contains(entity.getId());
	}

	private static void refreshNameIds(String needle) {
		Minecraft client = Minecraft.getInstance();
		int tick = client.player == null ? 0 : client.player.tickCount;
		if (tick == nameTick && needle.equals(nameNeedle)) {
			return;
		}
		nameTick = tick;
		nameNeedle = needle;
		if (client.level == null || client.player == null) {
			nameIds = Set.of();
			return;
		}
		String n = needle.toLowerCase(Locale.ROOT);
		Set<Integer> ids = new HashSet<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity == null || entity == client.player) {
				continue;
			}
			if (!nameMatches(entity, n)) {
				continue;
			}
			ids.add(entity.getId());
			linkHologram(client, entity, ids);
		}
		nameIds = ids;
	}

	private static boolean nameMatches(Entity entity, String needle) {
		if (namedText(entity, needle)) {
			return true;
		}
		for (Entity passenger : entity.getPassengers()) {
			if (namedText(passenger, needle)) {
				return true;
			}
		}
		Entity vehicle = entity.getVehicle();
		return vehicle != null && namedText(vehicle, needle);
	}

	private static boolean namedText(Entity entity, String needle) {
		if (textContains(entity.getCustomName(), needle)) {
			return true;
		}
		return entity.hasCustomName() && textContains(entity.getDisplayName(), needle);
	}

	/**
	 * Hypixel often puts the real name on an armor-stand hologram above the mob,
	 * not on the mob itself. Glow the living entity under that hologram too.
	 */
	private static void linkHologram(Minecraft client, Entity hologram, Set<Integer> ids) {
		if (hologram.getType() != EntityType.ARMOR_STAND) {
			return;
		}
		AABB box = hologram.getBoundingBox().inflate(HOLOGRAM_RANGE);
		for (Entity other : client.level.getEntities(hologram, box)) {
			if (other instanceof LivingEntity living
				&& living.isAlive()
				&& other != client.player
				&& other.getType() != EntityType.ARMOR_STAND) {
				ids.add(other.getId());
			}
		}
	}

	private static boolean textContains(Component text, String needle) {
		if (text == null) {
			return false;
		}
		String raw = STRIP.matcher(text.getString()).replaceAll("").toLowerCase(Locale.ROOT);
		return !raw.isEmpty() && raw.contains(needle);
	}

	private static boolean occluded(Minecraft client, Vec3 from, Vec3 to) {
		HitResult hit = client.level.clip(new ClipContext(
			from,
			to,
			ClipContext.Block.VISUAL,
			ClipContext.Fluid.NONE,
			client.player
		));
		if (hit.getType() == HitResult.Type.MISS) {
			return false;
		}
		return hit.getLocation().distanceToSqr(from) + 0.36 < to.distanceToSqr(from);
	}
}
