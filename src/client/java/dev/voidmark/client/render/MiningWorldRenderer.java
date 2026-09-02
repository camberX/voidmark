package dev.voidmark.client.render;

import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.mining.EfficientMiner;
import dev.voidmark.client.mining.TitaniumTracker;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;

import java.util.List;

public final class MiningWorldRenderer {
	private MiningWorldRenderer() {
	}

	public static void init() {
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> emit());
	}

	private static void emit() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		VoidmarkConfig config = VoidmarkConfig.get();
		drawTitanium(config);
		drawEfficientMiner(client, config);
	}

	private static void drawTitanium(VoidmarkConfig config) {
		if (!TitaniumTracker.active()) {
			return;
		}
		List<BlockPos> blocks = TitaniumTracker.get().snapshot();
		if (blocks.isEmpty()) {
			return;
		}
		boolean through = config.titaniumEspThroughWalls;
		GizmoStyle style = style(config.titaniumEspRgb, 0.38f, 2.2f);
		for (BlockPos pos : blocks) {
			box(pos, style, through);
		}
	}

	private static void drawEfficientMiner(Minecraft client, VoidmarkConfig config) {
		List<EfficientMiner.Target> extras = EfficientMiner.extras(client);
		if (extras.isEmpty()) {
			return;
		}
		boolean through = config.efficientMinerThroughWalls;
		int rgb = config.efficientMinerRgb;
		GizmoStyle sure = style(rgb, 0.42f, 2.6f);
		GizmoStyle maybe = style(rgb, 0.22f, 1.8f);
		GizmoStyle pool = style(rgb, 0.10f, 1.2f);
		for (EfficientMiner.Target target : extras) {
			GizmoStyle box = switch (target.kind()) {
				case SURE -> sure;
				case CHANCE -> maybe;
				case POOL -> pool;
			};
			box(target.pos(), box, through);
		}
	}

	private static GizmoStyle style(int rgb, float fill, float stroke) {
		int fillColor = (Math.round(fill * 255f) << 24) | (rgb & 0xFFFFFF);
		int line = 0xFF000000 | (rgb & 0xFFFFFF);
		return GizmoStyle.strokeAndFill(line, stroke, fillColor);
	}

	private static void box(BlockPos pos, GizmoStyle style, boolean throughWalls) {
		GizmoProperties properties = Gizmos.cuboid(pos, style);
		if (throughWalls) {
			properties.setAlwaysOnTop();
		}
	}
}
