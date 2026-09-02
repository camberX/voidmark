package dev.voidmark.client.render;

import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Marks matching mobs for the entity-outline buffer. A custom post shader
 * turns that silhouette into a clean outward gradient, not vanilla sobel.
 */
public final class MobGlowRenderer {
	private static final double MAX_RANGE = 96.0;
	private static final double MAX_RANGE_SQ = MAX_RANGE * MAX_RANGE;
	private static List<String> cachedIds;
	private static Set<EntityType<?>> cachedTypes = Set.of();

	private MobGlowRenderer() {
	}

	public static void init() {
	}

	public static int nearbyCount() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return 0;
		}
		Set<EntityType<?>> types = selectedTypes();
		if (types.isEmpty()) {
			return 0;
		}
		int count = 0;
		for (Entity entity : client.level.entitiesForRendering()) {
			if (matches(entity, types, client.player)) {
				count++;
			}
		}
		return count;
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
		Set<EntityType<?>> types = selectedTypes();
		if (types.isEmpty() || !matches(entity, types, client.player)) {
			return 0;
		}
		Vec3 camera = client.gameRenderer.getMainCamera().position();
		if (entity.distanceToSqr(camera) > MAX_RANGE_SQ) {
			return 0;
		}
		if (!config.mobGlowThroughWalls && occluded(client, camera, entity.getEyePosition())) {
			return 0;
		}
		float opacity = VoidmarkConfig.clamp(config.mobGlowOpacity, 0.15f, 0.90f);
		int alpha = Math.round(opacity * 255f);
		return (alpha << 24) | (config.mobGlowRgb & 0xFFFFFF);
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

	static boolean matches(Entity entity, Set<EntityType<?>> types, Entity player) {
		if (entity == null || entity == player || entity.isRemoved() || !entity.isAlive()) {
			return false;
		}
		return types.contains(entity.getType());
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
