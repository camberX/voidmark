package dev.voidmark.client.mining;

import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.location.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Treasure chests that spawn next to the player in Dwarven Mines / Crystal
 * Hollows. Fed only from packets so Sodium particle settings do not matter.
 */
public final class ChestEsp {
	private static final ChestEsp INSTANCE = new ChestEsp();
	private static final double RANGE = 5.0;
	private static final double RANGE_SQ = RANGE * RANGE;
	private static final long CRIT_MS = 1_200L;
	private static final double CRIT_CHEST = 0.3;
	private static final int MAX_CHESTS = 64;
	private static final int MAX_CRITS = 32;

	private final Map<Long, Mark> chests = new ConcurrentHashMap<>();
	private final List<Mark> crits = new ArrayList<>();
	private List<Mark> chestView = List.of();
	private List<Mark> critView = List.of();
	private boolean dirty = true;

	private ChestEsp() {
	}

	public static ChestEsp get() {
		return INSTANCE;
	}

	public static boolean active() {
		return VoidmarkConfig.get().chestEspEnabled && inIsland();
	}

	public static boolean inIsland() {
		if (!SkyblockLocation.inSkyblock) {
			return false;
		}
		String area = SkyblockLocation.area.toLowerCase(Locale.ROOT);
		if (area.isEmpty()) {
			return false;
		}
		return dwarven(area) || crystalHollows(area);
	}

	private static boolean dwarven(String area) {
		return area.contains("dwarven")
			|| area.contains("the forge")
			|| area.contains("rampart")
			|| area.contains("upper mines")
			|| area.contains("royal mines")
			|| area.contains("cliffside")
			|| area.contains("lava springs")
			|| area.contains("far reserve")
			|| area.contains("goblin burrow")
			|| area.contains("divan's gateway")
			|| area.contains("divans gateway")
			|| area.contains("great ice wall")
			|| area.contains("barracks of heroes")
			|| area.contains("aristocrat passage")
			|| area.equals("the lift");
	}

	private static boolean crystalHollows(String area) {
		return area.contains("crystal hollow")
			|| area.contains("precursor")
			|| area.contains("goblin holdout")
			|| area.contains("mithril deposit")
			|| area.contains("magma field")
			|| area.contains("mines of divan")
			|| area.contains("crystal nucleus")
			|| area.contains("fairy grotto")
			|| area.contains("khazad")
			|| area.contains("jungle temple")
			|| area.equals("jungle");
	}

	public static void onPacket(Packet<?> packet) {
		if (!VoidmarkConfig.get().chestEspEnabled) {
			return;
		}
		ChestAimer.onPacket(packet);
		if (packet instanceof ClientboundLevelParticlesPacket particles) {
			if (!isCrit(particles.getParticle().getType())) {
				return;
			}
			double x = particles.getX();
			double y = particles.getY();
			double z = particles.getZ();
			Minecraft.getInstance().execute(() -> INSTANCE.onCrit(x, y, z));
			return;
		}
		if (packet instanceof ClientboundBlockUpdatePacket update) {
			BlockPos pos = update.getPos();
			BlockState state = update.getBlockState();
			Minecraft.getInstance().execute(() -> INSTANCE.onBlock(pos, state));
			return;
		}
		if (packet instanceof ClientboundSectionBlocksUpdatePacket section) {
			section.runUpdates((pos, state) -> {
				BlockPos copy = pos.immutable();
				Minecraft.getInstance().execute(() -> INSTANCE.onBlock(copy, state));
			});
			return;
		}
		if (packet instanceof ClientboundBlockEntityDataPacket data) {
			if (data.getType() != BlockEntityType.CHEST && data.getType() != BlockEntityType.TRAPPED_CHEST) {
				return;
			}
			BlockPos pos = data.getPos();
			Minecraft.getInstance().execute(() -> INSTANCE.onChestEntity(pos));
		}
	}

	public void tick(Minecraft client) {
		if (!active() || client.player == null || client.level == null) {
			if (!chests.isEmpty() || !crits.isEmpty()) {
				clear();
			}
			return;
		}
		long now = System.currentTimeMillis();
		chests.entrySet().removeIf(entry -> {
			Mark mark = entry.getValue();
			if (!client.level.hasChunkAt(mark.pos)) {
				return false;
			}
			return !isChest(client.level.getBlockState(mark.pos));
		});
		synchronized (crits) {
			crits.removeIf(mark -> now - mark.born > CRIT_MS);
		}
		dirty = true;
	}

	public void clear() {
		chests.clear();
		synchronized (crits) {
			crits.clear();
		}
		chestView = List.of();
		critView = List.of();
		dirty = false;
	}

	public List<Mark> chests() {
		rebuild();
		return chestView;
	}

	public List<Mark> crits() {
		rebuild();
		return critView;
	}

	public Mark chestAt(BlockPos pos) {
		return pos == null ? null : chests.get(pos.asLong());
	}

	public List<Mark> marksNear(Mark chest) {
		ArrayList<Mark> out = new ArrayList<>();
		synchronized (crits) {
			for (Mark mark : crits) {
				Vec3 at = mark.box();
				if (chest != null && !nearChest(chest, at.x, at.y, at.z)) {
					continue;
				}
				out.add(mark);
			}
		}
		return out;
	}

	public boolean stillHas(Mark mark) {
		if (mark == null) {
			return false;
		}
		synchronized (crits) {
			return crits.contains(mark);
		}
	}

	public Mark nearestChest(Vec3 from) {
		Mark best = null;
		double bestDist = Double.MAX_VALUE;
		for (Mark mark : chests.values()) {
			double dist = from.distanceToSqr(mark.x, mark.y, mark.z);
			if (dist < bestDist) {
				bestDist = dist;
				best = mark;
			}
		}
		return best;
	}

	private void onCrit(double x, double y, double z) {
		if (!active()) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.distanceToSqr(x, y, z) > RANGE_SQ) {
			return;
		}
		if (!nearAnyChest(x, y, z)) {
			return;
		}
		long now = System.currentTimeMillis();
		synchronized (crits) {
			for (Mark existing : crits) {
				if (existing.nearCurrent(x, y, z, 0.18)) {
					existing.placeBox(x, y, z, now);
					dirty = true;
					return;
				}
			}
			while (crits.size() >= MAX_CRITS) {
				crits.removeFirst();
			}
			Mark mark = new Mark(BlockPos.containing(x, y, z), x, y, z, now);
			mark.placeBox(x, y, z, now);
			crits.add(mark);
		}
		dirty = true;
	}

	private void onBlock(BlockPos pos, BlockState state) {
		if (!active()) {
			return;
		}
		if (isChest(state)) {
			rememberChest(pos);
			return;
		}
		if (chests.remove(pos.asLong()) != null) {
			dirty = true;
		}
	}

	private void onChestEntity(BlockPos pos) {
		if (!active()) {
			return;
		}
		rememberChest(pos);
	}

	private void rememberChest(BlockPos pos) {
		if (chests.containsKey(pos.asLong())) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		Vec3 center = Vec3.atCenterOf(pos);
		if (player.distanceToSqr(center) > RANGE_SQ) {
			return;
		}
		if (chests.size() >= MAX_CHESTS) {
			Mark farthest = null;
			double farthestDist = -1;
			for (Mark mark : chests.values()) {
				double dist = player.distanceToSqr(mark.x, mark.y, mark.z);
				if (dist > farthestDist) {
					farthestDist = dist;
					farthest = mark;
				}
			}
			if (farthest != null) {
				chests.remove(farthest.pos.asLong());
			}
		}
		chests.put(pos.asLong(), new Mark(pos.immutable(), center.x, center.y, center.z, System.currentTimeMillis()));
		dirty = true;
	}

	private void rebuild() {
		if (!dirty) {
			return;
		}
		ArrayList<Mark> nextChests = new ArrayList<>(chests.values());
		nextChests.sort(Comparator.comparingLong(mark -> mark.pos.asLong()));
		chestView = List.copyOf(nextChests);
		synchronized (crits) {
			critView = List.copyOf(crits);
		}
		dirty = false;
	}

	private boolean nearAnyChest(double x, double y, double z) {
		for (Mark chest : chests.values()) {
			if (nearChest(chest, x, y, z)) {
				return true;
			}
		}
		return false;
	}

	private static boolean nearChest(Mark chest, double x, double y, double z) {
		return chest != null && new AABB(chest.pos).inflate(CRIT_CHEST).contains(x, y, z);
	}

	private static boolean isChest(BlockState state) {
		return state != null && (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST));
	}

	private static boolean isCrit(ParticleType<?> type) {
		return type == ParticleTypes.CRIT || type == ParticleTypes.ENCHANTED_HIT;
	}

	public static final class Mark {
		public final BlockPos pos;
		public final double x;
		public final double y;
		public final double z;
		public long born;
		public double boxX;
		public double boxY;
		public double boxZ;
		public boolean hasBox;
		public boolean moved;
		public long boxAt;

		private Mark(BlockPos pos, double x, double y, double z, long born) {
			this.pos = pos;
			this.x = x;
			this.y = y;
			this.z = z;
			this.born = born;
		}

		public Vec3 box() {
			return hasBox ? new Vec3(boxX, boxY, boxZ) : new Vec3(x, y, z);
		}

		private void placeBox(double nx, double ny, double nz, long now) {
			if (hasBox && !nearBox(nx, ny, nz, 0.15)) {
				moved = true;
			}
			boxX = nx;
			boxY = ny;
			boxZ = nz;
			hasBox = true;
			boxAt = now;
			born = now;
		}

		public boolean consumeMove() {
			if (!moved) {
				return false;
			}
			moved = false;
			return true;
		}

		private boolean nearCurrent(double ox, double oy, double oz, double radius) {
			return hasBox ? nearBox(ox, oy, oz, radius) : near(ox, oy, oz, radius);
		}

		private boolean near(double ox, double oy, double oz, double radius) {
			return distanceSq(ox, oy, oz) <= radius * radius;
		}

		private boolean nearBox(double ox, double oy, double oz, double radius) {
			double dx = boxX - ox;
			double dy = boxY - oy;
			double dz = boxZ - oz;
			return dx * dx + dy * dy + dz * dz <= radius * radius;
		}

		private double distanceSq(double ox, double oy, double oz) {
			double dx = x - ox;
			double dy = y - oy;
			double dz = z - oz;
			return dx * dx + dy * dy + dz * dz;
		}
	}
}
