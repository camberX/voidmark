package dev.voidmark.client.mining;

import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
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

/**
 * Polished diorite is Hypixel's Titanium ore block in the Dwarven Mines.
 * Scans loaded chunks while an unfinished Titanium commission is on the tab list.
 * {@code [Area] Titanium} jobs only keep ores in that named region;
 * {@code Titanium Miner} keeps every vein in range.
 */
public final class TitaniumTracker {
	private static final TitaniumTracker INSTANCE = new TitaniumTracker();
	private final Map<Long, BlockPos> blocks = new ConcurrentHashMap<>();
	private List<BlockPos> view = List.of();
	private MiningAreas.TitaniumFilter filter = MiningAreas.TitaniumFilter.NONE;
	private int tick;

	private TitaniumTracker() {
	}

	public static TitaniumTracker get() {
		return INSTANCE;
	}

	public void tick(Minecraft client) {
		MiningAreas.TitaniumFilter next = MiningTracker.titaniumFilter();
		boolean filterChanged = !next.equals(filter);
		filter = next;
		if (!active() || client.player == null || client.level == null) {
			if (!blocks.isEmpty()) {
				blocks.clear();
				view = List.of();
			}
			return;
		}
		tick++;
		ClientLevel level = client.level;
		BlockPos origin = client.player.blockPosition();
		int radius = VoidmarkConfig.get().titaniumEspRange;
		if (filterChanged || tick % 12 == 0) {
			scan(level, origin, radius);
		}
		int radiusSq = radius * radius;
		blocks.entrySet().removeIf(entry -> {
			BlockPos pos = entry.getValue();
			if (!filter.allows(pos)) {
				return true;
			}
			if (origin.distSqr(pos) > (long) radiusSq * 4L) {
				return true;
			}
			if (!level.hasChunkAt(pos)) {
				return false;
			}
			return !isTitanium(level.getBlockState(pos));
		});
		rebuildView();
	}

	public void clear() {
		blocks.clear();
		view = List.of();
		filter = MiningAreas.TitaniumFilter.NONE;
	}

	public List<BlockPos> snapshot() {
		return view;
	}

	public int count() {
		return blocks.size();
	}

	public static boolean active() {
		return VoidmarkConfig.get().titaniumEsp
			&& MiningTracker.hasTitaniumCommission()
			&& MiningTracker.inMiningIsland();
	}

	private void rebuildView() {
		ArrayList<BlockPos> copy = new ArrayList<>(blocks.values());
		copy.sort(Comparator.comparingLong(BlockPos::asLong));
		view = List.copyOf(copy);
	}

	public static boolean isTitanium(BlockState state) {
		return state != null && state.is(Blocks.POLISHED_DIORITE);
	}

	private void scan(ClientLevel level, BlockPos origin, int radius) {
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
				scanChunk(level.getChunk(chunkX, chunkZ), origin, radiusSq, minX, maxX, minY, maxY, minZ, maxZ);
			}
		}
	}

	private void scanChunk(
		LevelChunk chunk,
		BlockPos origin,
		int radiusSq,
		int minX,
		int maxX,
		int minY,
		int maxY,
		int minZ,
		int maxZ
	) {
		LevelChunkSection[] sections = chunk.getSections();
		int minSectionY = chunk.getMinSectionY();
		for (int index = 0; index < sections.length; index++) {
			LevelChunkSection section = sections[index];
			if (section == null || section.hasOnlyAir() || !section.maybeHas(TitaniumTracker::isTitanium)) {
				continue;
			}
			int baseY = (minSectionY + index) << 4;
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
						if (!filter.allows(x, y, z)) {
							continue;
						}
						if (isTitanium(section.getBlockState(lx, ly, lz))) {
							BlockPos pos = new BlockPos(x, y, z);
							blocks.put(pos.asLong(), pos);
						}
					}
				}
			}
		}
	}

	public BlockPos nearest(Vec3 from) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos pos : blocks.values()) {
			double dist = from.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
			if (dist < bestDist) {
				bestDist = dist;
				best = pos;
			}
		}
		return best;
	}
}
