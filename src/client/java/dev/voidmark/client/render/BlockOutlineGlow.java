package dev.voidmark.client.render;

import dev.voidmark.client.config.VoidmarkConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Colored filled silhouette on the hovered block. Drawn as gizmos so looking
 * at a block does not start the entity-outline glow post shader.
 */
public final class BlockOutlineGlow {
	private BlockOutlineGlow() {
	}

	public static void init() {
		LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> !active(outline));
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
			BlockOutlineRenderState outline = context.levelState().blockOutlineRenderState;
			if (!active(outline)) {
				return;
			}
			VoxelShape shape = outline.shape();
			BlockPos pos = outline.pos();
			int fill = color();
			int line = 0xFF000000 | (VoidmarkConfig.get().blockOutlineRgb & 0xFFFFFF);
			GizmoStyle style = GizmoStyle.strokeAndFill(line, 2.0f, fill);
			shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> Gizmos.cuboid(
				new AABB(
					pos.getX() + x0,
					pos.getY() + y0,
					pos.getZ() + z0,
					pos.getX() + x1,
					pos.getY() + y1,
					pos.getZ() + z1
				).inflate(0.002),
				style
			));
		});
	}

	public static boolean enabled() {
		return VoidmarkConfig.get().blockOutlineGlow;
	}

	private static boolean active(BlockOutlineRenderState outline) {
		return enabled() && outline != null && outline.shape() != null && !outline.shape().isEmpty();
	}

	public static int color() {
		VoidmarkConfig config = VoidmarkConfig.get();
		float opacity = VoidmarkConfig.clamp(config.blockOutlineOpacity, 0.15f, 0.90f);
		int alpha = Math.round(opacity * 255f);
		return (alpha << 24) | (config.blockOutlineRgb & 0xFFFFFF);
	}
}
