package dev.voidmark.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.voidmark.client.config.VoidmarkConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Draws the hovered block's selection shape into the entity-outline buffer so
 * the same post shader that glows mobs also rims the block you are looking at.
 */
public final class BlockOutlineGlow {
	private static final Identifier WHITE = Identifier.fromNamespaceAndPath("minecraft", "textures/block/white_concrete.png");

	private BlockOutlineGlow() {
	}

	public static void init() {
		LevelRenderEvents.AFTER_BLOCK_OUTLINE_EXTRACTION.register((context, hit) -> {
			if (active(context.levelState().blockOutlineRenderState)) {
				context.levelState().haveGlowingEntities = true;
			}
		});
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(context -> {
			BlockOutlineRenderState outline = context.levelState().blockOutlineRenderState;
			if (!active(outline) || context.poseStack() == null) {
				return;
			}
			Minecraft client = Minecraft.getInstance();
			if (client.player == null) {
				return;
			}
			OutlineBufferSource buffers = client.renderBuffers().outlineBufferSource();
			buffers.setColor(color());
			VertexConsumer consumer = buffers.getBuffer(RenderTypes.outline(WHITE));
			Vec3 camera = context.levelState().cameraRenderState.pos;
			BlockPos pos = outline.pos();
			double ox = pos.getX() - camera.x;
			double oy = pos.getY() - camera.y;
			double oz = pos.getZ() - camera.z;
			PoseStack.Pose pose = context.poseStack().last();
			VoxelShape shape = outline.shape();
			if (shape == null || shape.isEmpty()) {
				return;
			}
			shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> emitBox(
				consumer,
				pose,
				(float) (ox + x0),
				(float) (oy + y0),
				(float) (oz + z0),
				(float) (ox + x1),
				(float) (oy + y1),
				(float) (oz + z1)
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

	private static void emitBox(
		VertexConsumer consumer,
		PoseStack.Pose pose,
		float x0,
		float y0,
		float z0,
		float x1,
		float y1,
		float z1
	) {
		quad(consumer, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		quad(consumer, pose, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
		quad(consumer, pose, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
		quad(consumer, pose, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		quad(consumer, pose, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		quad(consumer, pose, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
	}

	private static void quad(
		VertexConsumer consumer,
		PoseStack.Pose pose,
		float x0,
		float y0,
		float z0,
		float x1,
		float y1,
		float z1,
		float x2,
		float y2,
		float z2,
		float x3,
		float y3,
		float z3
	) {
		vertex(consumer, pose, x0, y0, z0, 0f, 0f);
		vertex(consumer, pose, x1, y1, z1, 1f, 0f);
		vertex(consumer, pose, x2, y2, z2, 1f, 1f);
		vertex(consumer, pose, x3, y3, z3, 0f, 1f);
	}

	private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float u, float v) {
		consumer.addVertex(pose, x, y, z).setUv(u, v);
	}
}
