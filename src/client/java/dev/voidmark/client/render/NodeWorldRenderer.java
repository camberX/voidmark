package dev.voidmark.client.render;

import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.location.SkyblockLocation;
import dev.voidmark.client.node.EnderNodeTracker;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class NodeWorldRenderer {
	private NodeWorldRenderer() {
	}

	public static void init() {
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> emit());
	}

	public static void close() {
	}

	private static void emit() {
		if (!SkyblockLocation.shouldMarkNodes()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}

		List<EnderNodeTracker.TrackedNode> nodes = EnderNodeTracker.get().snapshot();
		if (nodes.isEmpty()) {
			return;
		}

		VoidmarkConfig config = VoidmarkConfig.get();
		int fill = config.boxFill ? config.fillColor() : 0;
		int stroke = config.boxOutline || config.tracersEnabled ? config.lineColor() : 0;
		GizmoStyle style = config.boxOutline && config.boxFill
			? GizmoStyle.strokeAndFill(stroke, 2.4f, fill)
			: config.boxFill ? GizmoStyle.fill(fill) : GizmoStyle.stroke(stroke, 2.4f);

		Vec3 camera = client.gameRenderer.getMainCamera().position();
		EnderNodeTracker.TrackedNode nearest = EnderNodeTracker.get().nearest(camera);

		for (EnderNodeTracker.TrackedNode node : nodes) {
			GizmoProperties properties = Gizmos.cuboid(node.pos(), style);
			if (config.throughWalls) {
				properties.setAlwaysOnTop();
			}

			String label = GuiDraw.meters(node.distanceTo(camera));
			GizmoProperties text = Gizmos.billboardText(
				label,
				new Vec3(node.x() + 0.5, node.y() + 1.25, node.z() + 0.5),
				TextGizmo.Style.forColorAndCentered(config.lineColor()).withScale(0.22f)
			);
			if (config.throughWalls) {
				text.setAlwaysOnTop();
			}
		}

		if (config.tracersEnabled && nearest != null) {
			Vec3 look = client.player.getViewVector(1.0f);
			Vec3 from = camera.add(look.scale(0.4));
			Vec3 to = new Vec3(nearest.x() + 0.5, nearest.y() + 0.5, nearest.z() + 0.5);
			GizmoProperties tracer = Gizmos.line(from, to, config.lineColor(), 2.0f);
			if (config.throughWalls) {
				tracer.setAlwaysOnTop();
			}
		}
	}
}
