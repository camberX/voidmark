package dev.voidmark.client.render;

import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.mining.ChestEsp;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class ChestEspRenderer {
	private ChestEspRenderer() {
	}

	public static void init() {
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> emit());
	}

	private static void emit() {
		if (!ChestEsp.active()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		VoidmarkConfig config = VoidmarkConfig.get();
		int rgb = config.chestEspRgb & 0xFFFFFF;
		int fill = (Math.round(0.34f * 255f) << 24) | rgb;
		int line = 0xFF000000 | rgb;
		boolean through = config.chestEspThroughWalls;
		GizmoStyle chestStyle = GizmoStyle.strokeAndFill(line, 2.2f, fill);
		GizmoStyle critStyle = GizmoStyle.strokeAndFill(line, 1.6f, fill);

		List<ChestEsp.Mark> chests = ChestEsp.get().chests();
		for (ChestEsp.Mark chest : chests) {
			GizmoProperties box = Gizmos.cuboid(chest.pos, chestStyle);
			if (through) {
				box.setAlwaysOnTop();
			}
		}

		List<ChestEsp.Mark> crits = ChestEsp.get().crits();
		for (ChestEsp.Mark crit : crits) {
			AABB cube = new AABB(crit.x, crit.y, crit.z, crit.x, crit.y, crit.z).inflate(0.11);
			GizmoProperties box = Gizmos.cuboid(cube, critStyle);
			if (through) {
				box.setAlwaysOnTop();
			}
		}

		if (!config.chestEspTracers) {
			return;
		}
		Vec3 camera = client.gameRenderer.getMainCamera().position();
		ChestEsp.Mark nearest = ChestEsp.get().nearestChest(camera);
		if (nearest == null) {
			return;
		}
		Vec3 look = client.player.getViewVector(1.0f);
		Vec3 from = camera.add(look.scale(0.4));
		GizmoProperties tracer = Gizmos.line(from, new Vec3(nearest.x, nearest.y, nearest.z), line, 2.0f);
		if (through) {
			tracer.setAlwaysOnTop();
		}
	}
}
