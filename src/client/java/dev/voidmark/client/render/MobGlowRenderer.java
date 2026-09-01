package dev.voidmark.client.render;

import dev.voidmark.client.config.VoidmarkConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Soft outward bloom around the selected mob type. Built from stacked
 * translucent AABB shells — not the vanilla glowing outline.
 */
public final class MobGlowRenderer {
	private static final int SHELLS = 10;
	private static final int MAX_MOBS = 64;
	private static final double MAX_RANGE = 96.0;

	private MobGlowRenderer() {
	}

	public static void init() {
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> emit());
	}

	public static int nearbyCount() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return 0;
		}
		EntityType<?> type = MobCatalog.type(VoidmarkConfig.get().mobGlowId);
		if (type == null) {
			return 0;
		}
		int count = 0;
		for (Entity entity : client.level.entitiesForRendering()) {
			if (matches(entity, type, client.player)) {
				count++;
			}
		}
		return count;
	}

	private static void emit() {
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.mobGlowEnabled) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		EntityType<?> type = MobCatalog.type(config.mobGlowId);
		if (type == null) {
			return;
		}

		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = client.gameRenderer.getMainCamera().position();
		List<Entity> mobs = collect(client, type, camera);
		if (mobs.isEmpty()) {
			return;
		}

		int rgb = config.mobGlowRgb & 0xFFFFFF;
		float size = VoidmarkConfig.clamp(config.mobGlowSize, 0.12f, 1.20f);
		float opacity = VoidmarkConfig.clamp(config.mobGlowOpacity, 0.15f, 0.90f);
		boolean throughWalls = config.mobGlowThroughWalls;
		float pulse = 0.88f + 0.12f * Mth.sin((float) (System.nanoTime() / 1_000_000_000.0 * 2.35));

		for (Entity entity : mobs) {
			AABB box = interpolatedBox(entity, partial);
			drawBloom(box, rgb, opacity, size * pulse, throughWalls);
		}
	}

	private static List<Entity> collect(Minecraft client, EntityType<?> type, Vec3 camera) {
		List<Entity> mobs = new ArrayList<>();
		double maxSq = MAX_RANGE * MAX_RANGE;
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!matches(entity, type, client.player)) {
				continue;
			}
			if (entity.distanceToSqr(camera) > maxSq) {
				continue;
			}
			mobs.add(entity);
		}
		if (mobs.size() > MAX_MOBS) {
			mobs.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(camera)));
			return mobs.subList(0, MAX_MOBS);
		}
		return mobs;
	}

	private static boolean matches(Entity entity, EntityType<?> type, Entity player) {
		if (entity == null || entity == player || entity.isRemoved() || !entity.isAlive()) {
			return false;
		}
		return entity.getType() == type;
	}

	private static AABB interpolatedBox(Entity entity, float partial) {
		Vec3 pos = entity.getPosition(partial);
		return entity.getBoundingBox().move(
			pos.x - entity.getX(),
			pos.y - entity.getY(),
			pos.z - entity.getZ()
		);
	}

	private static void drawBloom(AABB box, int rgb, float opacity, float size, boolean throughWalls) {
		for (int i = SHELLS; i >= 1; i--) {
			float t = i / (float) SHELLS;
			float falloff = (1f - t);
			falloff *= falloff;
			int alpha = Math.round(opacity * (0.16f + 0.62f * falloff) * 255f);
			if (alpha <= 0) {
				continue;
			}
			int fill = (alpha << 24) | rgb;
			AABB shell = box.inflate(size * t);
			GizmoProperties properties = Gizmos.cuboid(shell, GizmoStyle.fill(fill));
			if (throughWalls) {
				properties.setAlwaysOnTop();
			}
		}

		int coreAlpha = Math.round(opacity * 0.22f * 255f);
		GizmoProperties core = Gizmos.cuboid(
			box.inflate(0.03),
			GizmoStyle.fill((coreAlpha << 24) | rgb)
		);
		if (throughWalls) {
			core.setAlwaysOnTop();
		}
	}
}
