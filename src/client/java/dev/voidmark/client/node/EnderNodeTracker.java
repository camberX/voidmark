package dev.voidmark.client.node;

import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.location.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EnderNodeTracker {
	private static final EnderNodeTracker INSTANCE = new EnderNodeTracker();
	private static final long STALE_AFTER_MS = 8_000L;

	private final Map<Long, TrackedNode> nodes = new ConcurrentHashMap<>();
	private List<TrackedNode> view = List.of();
	private boolean viewDirty = true;
	private int tick;

	private EnderNodeTracker() {
	}

	public static EnderNodeTracker get() {
		return INSTANCE;
	}

	public void tick(Minecraft client) {
		if (!SkyblockLocation.shouldMarkNodes() || client.player == null || client.level == null) {
			if (!nodes.isEmpty() && !VoidmarkConfig.get().forceEnable) {
				nodes.clear();
				view = List.of();
				viewDirty = false;
			}
			return;
		}

		tick++;
		long now = System.currentTimeMillis();
		ClientLevel level = client.level;
		BlockPos origin = client.player.blockPosition();
		VoidmarkConfig config = VoidmarkConfig.get();

		if (config.blockScan && tick % 15 == 0) {
			scan(level, origin, config.scanRadius, now);
		}

		nodes.entrySet().removeIf(entry -> {
			TrackedNode node = entry.getValue();
			BlockPos pos = node.pos();
			if (origin.distSqr(pos) > (long) config.scanRadius * config.scanRadius * 4L) {
				return true;
			}
			if (!level.hasChunkAt(pos)) {
				return now - node.lastSeenMs() > STALE_AFTER_MS;
			}
			if (!isNodeBlock(level.getBlockState(pos))) {
				return true;
			}
			return false;
		});
		viewDirty = true;
	}

	public void onParticle(double x, double y, double z, ParticleType<?> type) {
		if (!VoidmarkConfig.get().particleDetection || !SkyblockLocation.shouldMarkNodes()) {
			return;
		}
		if (!isNodeParticle(type)) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (level == null || client.player == null) {
			return;
		}

		BlockPos hint = BlockPos.containing(x, y, z);
		int radius = 1;
		for (BlockPos pos : BlockPos.betweenClosed(hint.offset(-radius, -radius, -radius), hint.offset(radius, radius, radius))) {
			if (isNodeBlock(level.getBlockState(pos))) {
				remember(pos.immutable(), System.currentTimeMillis());
				return;
			}
		}

		// Hypixel sometimes emits the particle slightly inside the clay.
		if (isNodeBlock(level.getBlockState(hint))) {
			remember(hint.immutable(), System.currentTimeMillis());
		}
	}

	public void clear() {
		nodes.clear();
		view = List.of();
		viewDirty = false;
	}

	public List<TrackedNode> snapshot() {
		if (!viewDirty) {
			return view;
		}
		ArrayList<TrackedNode> copy = new ArrayList<>(nodes.values());
		copy.sort(Comparator.comparingLong(TrackedNode::packed));
		view = List.copyOf(copy);
		viewDirty = false;
		return view;
	}

	public int count() {
		return nodes.size();
	}

	public TrackedNode nearest(Vec3 from) {
		TrackedNode best = null;
		double bestDist = Double.MAX_VALUE;
		for (TrackedNode node : nodes.values()) {
			double dist = from.distanceToSqr(node.x() + 0.5, node.y() + 0.5, node.z() + 0.5);
			if (dist < bestDist) {
				bestDist = dist;
				best = node;
			}
		}
		return best;
	}

	private void scan(ClientLevel level, BlockPos origin, int radius, long now) {
		int minX = origin.getX() - radius;
		int maxX = origin.getX() + radius;
		int minY = Math.max(level.getMinY(), origin.getY() - radius);
		int maxY = Math.min(level.getMaxY(), origin.getY() + radius);
		int minZ = origin.getZ() - radius;
		int maxZ = origin.getZ() + radius;
		int radiusSq = radius * radius;

		int minChunkX = minX >> 4;
		int maxChunkX = maxX >> 4;
		int minChunkZ = minZ >> 4;
		int maxChunkZ = maxZ >> 4;

		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				if (!level.hasChunk(chunkX, chunkZ)) {
					continue;
				}
				LevelChunk chunk = level.getChunk(chunkX, chunkZ);
				scanChunk(chunk, origin, radiusSq, minX, maxX, minY, maxY, minZ, maxZ, now);
			}
		}
	}

	private void scanChunk(LevelChunk chunk, BlockPos origin, int radiusSq, int minX, int maxX, int minY, int maxY, int minZ, int maxZ, long now) {
		LevelChunkSection[] sections = chunk.getSections();
		int minSectionY = chunk.getMinSectionY();

		for (int index = 0; index < sections.length; index++) {
			LevelChunkSection section = sections[index];
			if (section == null || section.hasOnlyAir()) {
				continue;
			}
			if (!section.maybeHas(EnderNodeTracker::isNodeBlock)) {
				continue;
			}

			int sectionY = minSectionY + index;
			int baseY = sectionY << 4;
			if (baseY + 15 < minY || baseY > maxY) {
				continue;
			}

			int baseX = chunk.getPos().getMinBlockX();
			int baseZ = chunk.getPos().getMinBlockZ();

			for (int ly = 0; ly < 16; ly++) {
				int y = baseY + ly;
				if (y < minY || y > maxY) {
					continue;
				}
				for (int lx = 0; lx < 16; lx++) {
					int x = baseX + lx;
					if (x < minX || x > maxX) {
						continue;
					}
					for (int lz = 0; lz < 16; lz++) {
						int z = baseZ + lz;
						if (z < minZ || z > maxZ) {
							continue;
						}
						long dx = x - origin.getX();
						long dy = y - origin.getY();
						long dz = z - origin.getZ();
						if (dx * dx + dy * dy + dz * dz > radiusSq) {
							continue;
						}
						BlockState state = section.getBlockState(lx, ly, lz);
						if (isNodeBlock(state)) {
							remember(new BlockPos(x, y, z), now);
						}
					}
				}
			}
		}
	}

	private void remember(BlockPos pos, long now) {
		long packed = pos.asLong();
		nodes.put(packed, new TrackedNode(pos, packed, now));
		viewDirty = true;
	}

	public static boolean isNodeBlock(BlockState state) {
		Block block = state.getBlock();
		return block == Blocks.MAGENTA_TERRACOTTA || block == Blocks.PURPLE_TERRACOTTA;
	}

	private static boolean isNodeParticle(ParticleType<?> type) {
		return type == ParticleTypes.PORTAL
			|| type == ParticleTypes.REVERSE_PORTAL
			|| type == ParticleTypes.WITCH
			|| type == ParticleTypes.DRAGON_BREATH;
	}

	public record TrackedNode(BlockPos pos, long packed, long lastSeenMs) {
		public int x() {
			return pos.getX();
		}

		public int y() {
			return pos.getY();
		}

		public int z() {
			return pos.getZ();
		}

		public double distanceTo(Vec3 from) {
			return from.distanceTo(new Vec3(x() + 0.5, y() + 0.5, z() + 0.5));
		}

		public float yawTo(Vec3 from) {
			double dx = (x() + 0.5) - from.x;
			double dz = (z() + 0.5) - from.z;
			return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
		}
	}
}
