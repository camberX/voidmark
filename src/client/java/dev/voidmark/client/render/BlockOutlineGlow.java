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

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Draws the hovered block's selection <em>wire</em> into the entity-outline
 * buffer so the glow follows every edge, including the inner ones, instead of
 * only the outer silhouette.
 */
public final class BlockOutlineGlow {
	private static final Identifier WHITE = Identifier.fromNamespaceAndPath("minecraft", "textures/block/white_concrete.png");
	private static final float THICK = 0.038f;

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
			Set<Long> corners = new LinkedHashSet<>();
			shape.forAllEdges((x0, y0, z0, x1, y1, z1) -> {
				emitEdge(
					consumer,
					pose,
					(float) (ox + x0),
					(float) (oy + y0),
					(float) (oz + z0),
					(float) (ox + x1),
					(float) (oy + y1),
					(float) (oz + z1)
				);
				corners.add(pack(x0, y0, z0));
				corners.add(pack(x1, y1, z1));
			});
			for (long key : corners) {
				float x = unpackX(key);
				float y = unpackY(key);
				float z = unpackZ(key);
				emitBox(
					consumer,
					pose,
					(float) (ox + x) - THICK,
					(float) (oy + y) - THICK,
					(float) (oz + z) - THICK,
					(float) (ox + x) + THICK,
					(float) (oy + y) + THICK,
					(float) (oz + z) + THICK
				);
			}
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
		float opacity = VoidmarkConfig.clamp(config.mobGlowOpacity, 0.15f, 0.90f);
		int alpha = Math.round(opacity * 255f);
		return (alpha << 24) | (config.mobGlowRgb & 0xFFFFFF);
	}

	private static void emitEdge(
		VertexConsumer consumer,
		PoseStack.Pose pose,
		float ax,
		float ay,
		float az,
		float bx,
		float by,
		float bz
	) {
		float dx = bx - ax;
		float dy = by - ay;
		float dz = bz - az;
		float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1.0e-5f) {
			return;
		}
		dx /= len;
		dy /= len;
		dz /= len;
		float ux = Math.abs(dy) < 0.99f ? 0f : 1f;
		float uy = Math.abs(dy) < 0.99f ? 1f : 0f;
		float uz = 0f;
		float n1x = dy * uz - dz * uy;
		float n1y = dz * ux - dx * uz;
		float n1z = dx * uy - dy * ux;
		float n1len = (float) Math.sqrt(n1x * n1x + n1y * n1y + n1z * n1z);
		if (n1len < 1.0e-5f) {
			return;
		}
		n1x = n1x / n1len * THICK;
		n1y = n1y / n1len * THICK;
		n1z = n1z / n1len * THICK;
		float n2x = (dy * n1z - dz * n1y);
		float n2y = (dz * n1x - dx * n1z);
		float n2z = (dx * n1y - dy * n1x);
		float n2len = (float) Math.sqrt(n2x * n2x + n2y * n2y + n2z * n2z);
		if (n2len < 1.0e-5f) {
			return;
		}
		n2x = n2x / n2len * THICK;
		n2y = n2y / n2len * THICK;
		n2z = n2z / n2len * THICK;
		emitBoxCorners(
			consumer,
			pose,
			ax + n1x + n2x, ay + n1y + n2y, az + n1z + n2z,
			ax + n1x - n2x, ay + n1y - n2y, az + n1z - n2z,
			ax - n1x - n2x, ay - n1y - n2y, az - n1z - n2z,
			ax - n1x + n2x, ay - n1y + n2y, az - n1z + n2z,
			bx + n1x + n2x, by + n1y + n2y, bz + n1z + n2z,
			bx + n1x - n2x, by + n1y - n2y, bz + n1z - n2z,
			bx - n1x - n2x, by - n1y - n2y, bz - n1z - n2z,
			bx - n1x + n2x, by - n1y + n2y, bz - n1z + n2z
		);
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
		emitBoxCorners(
			consumer,
			pose,
			x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0,
			x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0
		);
	}

	/** Eight corners: a0–a3 around one end, b0–b3 around the other, matching winding. */
	private static void emitBoxCorners(
		VertexConsumer consumer,
		PoseStack.Pose pose,
		float a0x, float a0y, float a0z,
		float a1x, float a1y, float a1z,
		float a2x, float a2y, float a2z,
		float a3x, float a3y, float a3z,
		float b0x, float b0y, float b0z,
		float b1x, float b1y, float b1z,
		float b2x, float b2y, float b2z,
		float b3x, float b3y, float b3z
	) {
		quad(consumer, pose, a0x, a0y, a0z, a1x, a1y, a1z, a2x, a2y, a2z, a3x, a3y, a3z);
		quad(consumer, pose, b0x, b0y, b0z, b3x, b3y, b3z, b2x, b2y, b2z, b1x, b1y, b1z);
		quad(consumer, pose, a0x, a0y, a0z, b0x, b0y, b0z, b1x, b1y, b1z, a1x, a1y, a1z);
		quad(consumer, pose, a1x, a1y, a1z, b1x, b1y, b1z, b2x, b2y, b2z, a2x, a2y, a2z);
		quad(consumer, pose, a2x, a2y, a2z, b2x, b2y, b2z, b3x, b3y, b3z, a3x, a3y, a3z);
		quad(consumer, pose, a3x, a3y, a3z, b3x, b3y, b3z, b0x, b0y, b0z, a0x, a0y, a0z);
	}

	private static void quad(
		VertexConsumer consumer,
		PoseStack.Pose pose,
		float x0, float y0, float z0,
		float x1, float y1, float z1,
		float x2, float y2, float z2,
		float x3, float y3, float z3
	) {
		vertex(consumer, pose, x0, y0, z0, 0f, 0f);
		vertex(consumer, pose, x1, y1, z1, 1f, 0f);
		vertex(consumer, pose, x2, y2, z2, 1f, 1f);
		vertex(consumer, pose, x3, y3, z3, 0f, 1f);
	}

	private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float u, float v) {
		consumer.addVertex(pose, x, y, z).setUv(u, v);
	}

	private static long pack(double x, double y, double z) {
		int ix = Math.round((float) (x * 256.0));
		int iy = Math.round((float) (y * 256.0));
		int iz = Math.round((float) (z * 256.0));
		return (ix & 0x3FFFFL) | ((iy & 0x3FFFFL) << 18) | ((iz & 0x3FFFFL) << 36);
	}

	private static float unpackX(long key) {
		return (shortCoord(key) ) / 256.0f;
	}

	private static float unpackY(long key) {
		return (shortCoord(key >> 18)) / 256.0f;
	}

	private static float unpackZ(long key) {
		return (shortCoord(key >> 36)) / 256.0f;
	}

	private static int shortCoord(long bits) {
		int value = (int) (bits & 0x3FFFFL);
		if ((value & 0x20000) != 0) {
			value |= ~0x3FFFF;
		}
		return value;
	}
}
