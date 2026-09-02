package dev.voidmark.client.render;

import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.mining.TitaniumTracker;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
		drawTitanium(VoidmarkConfig.get());
	}

	private static void drawTitanium(VoidmarkConfig config) {
		if (!TitaniumTracker.active()) {
			return;
		}
		List<BlockPos> blocks = TitaniumTracker.get().snapshot();
		if (blocks.isEmpty()) {
			return;
		}
		Set<Long> occupied = new HashSet<>(blocks.size() * 2);
		for (BlockPos pos : blocks) {
			occupied.add(pos.asLong());
		}
		boolean through = config.titaniumEspThroughWalls;
		int rgb = config.titaniumEspRgb & 0xFFFFFF;
		int fill = (Math.round(0.38f * 255f) << 24) | rgb;
		int line = 0xFF000000 | rgb;
		GizmoStyle fillStyle = GizmoStyle.fill(fill);
		for (Direction dir : Direction.values()) {
			meshFaces(occupied, dir, fillStyle, through);
		}
		Set<Edge> edges = new HashSet<>();
		for (long packed : occupied) {
			int x = BlockPos.getX(packed);
			int y = BlockPos.getY(packed);
			int z = BlockPos.getZ(packed);
			collectEdges(edges, x, y, z);
		}
		for (Edge edge : edges) {
			if (!outline(occupied, edge)) {
				continue;
			}
			GizmoProperties properties = Gizmos.line(edge.from(), edge.to(), line, 2.2f);
			if (through) {
				properties.setAlwaysOnTop();
			}
		}
	}

	/**
	 * Greedy-mesh exposed faces so neighboring polished diorite fills as one
	 * volume. Shared faces are skipped.
	 */
	private static void meshFaces(Set<Long> occupied, Direction dir, GizmoStyle style, boolean through) {
		int dx = dir.getStepX();
		int dy = dir.getStepY();
		int dz = dir.getStepZ();
		Set<Long> faces = new HashSet<>();
		for (long packed : occupied) {
			int x = BlockPos.getX(packed);
			int y = BlockPos.getY(packed);
			int z = BlockPos.getZ(packed);
			if (!occupied.contains(BlockPos.asLong(x + dx, y + dy, z + dz))) {
				faces.add(packed);
			}
		}
		while (!faces.isEmpty()) {
			long packed = faces.iterator().next();
			int x = BlockPos.getX(packed);
			int y = BlockPos.getY(packed);
			int z = BlockPos.getZ(packed);
			switch (dir.getAxis()) {
				case X -> emitXFace(faces, x, y, z, dir, style, through);
				case Y -> emitYFace(faces, x, y, z, dir, style, through);
				case Z -> emitZFace(faces, x, y, z, dir, style, through);
			}
		}
	}

	private static void emitXFace(Set<Long> faces, int x, int y, int z, Direction dir, GizmoStyle style, boolean through) {
		int y0 = y;
		int y1 = y;
		while (faces.contains(BlockPos.asLong(x, y0 - 1, z))) {
			y0--;
		}
		while (faces.contains(BlockPos.asLong(x, y1 + 1, z))) {
			y1++;
		}
		int z0 = z;
		int z1 = z;
		while (filledYZ(faces, x, y0, y1, z0 - 1)) {
			z0--;
		}
		while (filledYZ(faces, x, y0, y1, z1 + 1)) {
			z1++;
		}
		for (int by = y0; by <= y1; by++) {
			for (int bz = z0; bz <= z1; bz++) {
				faces.remove(BlockPos.asLong(x, by, bz));
			}
		}
		emit(x, y0, z0, x + 1, y1 + 1, z1 + 1, dir, style, through);
	}

	private static void emitYFace(Set<Long> faces, int x, int y, int z, Direction dir, GizmoStyle style, boolean through) {
		int x0 = x;
		int x1 = x;
		while (faces.contains(BlockPos.asLong(x0 - 1, y, z))) {
			x0--;
		}
		while (faces.contains(BlockPos.asLong(x1 + 1, y, z))) {
			x1++;
		}
		int z0 = z;
		int z1 = z;
		while (filledXZ(faces, x0, x1, y, z0 - 1)) {
			z0--;
		}
		while (filledXZ(faces, x0, x1, y, z1 + 1)) {
			z1++;
		}
		for (int bx = x0; bx <= x1; bx++) {
			for (int bz = z0; bz <= z1; bz++) {
				faces.remove(BlockPos.asLong(bx, y, bz));
			}
		}
		emit(x0, y, z0, x1 + 1, y + 1, z1 + 1, dir, style, through);
	}

	private static void emitZFace(Set<Long> faces, int x, int y, int z, Direction dir, GizmoStyle style, boolean through) {
		int x0 = x;
		int x1 = x;
		while (faces.contains(BlockPos.asLong(x0 - 1, y, z))) {
			x0--;
		}
		while (faces.contains(BlockPos.asLong(x1 + 1, y, z))) {
			x1++;
		}
		int y0 = y;
		int y1 = y;
		while (filledXY(faces, x0, x1, y0 - 1, z)) {
			y0--;
		}
		while (filledXY(faces, x0, x1, y1 + 1, z)) {
			y1++;
		}
		for (int bx = x0; bx <= x1; bx++) {
			for (int by = y0; by <= y1; by++) {
				faces.remove(BlockPos.asLong(bx, by, z));
			}
		}
		emit(x0, y0, z, x1 + 1, y1 + 1, z + 1, dir, style, through);
	}

	private static boolean filledYZ(Set<Long> faces, int x, int y0, int y1, int z) {
		for (int y = y0; y <= y1; y++) {
			if (!faces.contains(BlockPos.asLong(x, y, z))) {
				return false;
			}
		}
		return true;
	}

	private static boolean filledXZ(Set<Long> faces, int x0, int x1, int y, int z) {
		for (int x = x0; x <= x1; x++) {
			if (!faces.contains(BlockPos.asLong(x, y, z))) {
				return false;
			}
		}
		return true;
	}

	private static boolean filledXY(Set<Long> faces, int x0, int x1, int y, int z) {
		for (int x = x0; x <= x1; x++) {
			if (!faces.contains(BlockPos.asLong(x, y, z))) {
				return false;
			}
		}
		return true;
	}

	private static void emit(int x0, int y0, int z0, int x1, int y1, int z1, Direction dir, GizmoStyle style, boolean through) {
		GizmoProperties properties = Gizmos.rect(new Vec3(x0, y0, z0), new Vec3(x1, y1, z1), dir, style);
		if (through) {
			properties.setAlwaysOnTop();
		}
	}

	private static void collectEdges(Set<Edge> edges, int x, int y, int z) {
		edges.add(new Edge(x, y, z, Direction.Axis.X));
		edges.add(new Edge(x, y + 1, z, Direction.Axis.X));
		edges.add(new Edge(x, y, z + 1, Direction.Axis.X));
		edges.add(new Edge(x, y + 1, z + 1, Direction.Axis.X));
		edges.add(new Edge(x, y, z, Direction.Axis.Y));
		edges.add(new Edge(x + 1, y, z, Direction.Axis.Y));
		edges.add(new Edge(x, y, z + 1, Direction.Axis.Y));
		edges.add(new Edge(x + 1, y, z + 1, Direction.Axis.Y));
		edges.add(new Edge(x, y, z, Direction.Axis.Z));
		edges.add(new Edge(x + 1, y, z, Direction.Axis.Z));
		edges.add(new Edge(x, y + 1, z, Direction.Axis.Z));
		edges.add(new Edge(x + 1, y + 1, z, Direction.Axis.Z));
	}

	/**
	 * Keep edges on the outer silhouette. Skip edges that sit in the middle of
	 * a flat 2-block face so adjacent ores look like one box.
	 */
	private static boolean outline(Set<Long> occupied, Edge edge) {
		boolean a;
		boolean b;
		boolean c;
		boolean d;
		switch (edge.axis) {
			case X -> {
				a = occupied.contains(BlockPos.asLong(edge.x, edge.y - 1, edge.z - 1));
				b = occupied.contains(BlockPos.asLong(edge.x, edge.y, edge.z - 1));
				c = occupied.contains(BlockPos.asLong(edge.x, edge.y - 1, edge.z));
				d = occupied.contains(BlockPos.asLong(edge.x, edge.y, edge.z));
			}
			case Y -> {
				a = occupied.contains(BlockPos.asLong(edge.x - 1, edge.y, edge.z - 1));
				b = occupied.contains(BlockPos.asLong(edge.x, edge.y, edge.z - 1));
				c = occupied.contains(BlockPos.asLong(edge.x - 1, edge.y, edge.z));
				d = occupied.contains(BlockPos.asLong(edge.x, edge.y, edge.z));
			}
			case Z -> {
				a = occupied.contains(BlockPos.asLong(edge.x - 1, edge.y - 1, edge.z));
				b = occupied.contains(BlockPos.asLong(edge.x, edge.y - 1, edge.z));
				c = occupied.contains(BlockPos.asLong(edge.x - 1, edge.y, edge.z));
				d = occupied.contains(BlockPos.asLong(edge.x, edge.y, edge.z));
			}
			default -> {
				return false;
			}
		}
		int n = (a ? 1 : 0) + (b ? 1 : 0) + (c ? 1 : 0) + (d ? 1 : 0);
		if (n == 1 || n == 3) {
			return true;
		}
		return n == 2 && a == d && b == c && a != b;
	}

	private record Edge(int x, int y, int z, Direction.Axis axis) {
		Vec3 from() {
			return new Vec3(x, y, z);
		}

		Vec3 to() {
			return switch (axis) {
				case X -> new Vec3(x + 1, y, z);
				case Y -> new Vec3(x, y + 1, z);
				case Z -> new Vec3(x, y, z + 1);
			};
		}
	}
}
