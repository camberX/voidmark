package dev.voidmark.client.mining;

import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.item.ItemIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Mining Spread / Efficient Miner extras, from Hypixel's 0.20.6 rules.
 *
 * Count (official):
 * <pre>
 *   guaranteed = floor(S / 100)
 *   chance%    = S mod 100
 *   extras     = guaranteed, plus one more with chance% probability
 * </pre>
 *
 * Pool: every cell of the 3×3×3 around the mined block — faces, edge
 * diagonals, and corners (Chebyshev radius 1). Same type. No gemstone glass.
 * Blocks below your feet are skipped unless you look down.
 *
 * Those 26 neighbors are equally adjacent. Bright boxes are the predicted
 * extras (look-aligned). Dimmer boxes are the rest of the cube that spread
 * can still roll, including diagonals.
 */
public final class EfficientMiner {
	private static final int[][] OFFSETS = bakeOffsets();

	private EfficientMiner() {
	}

	public static List<Target> extras(Minecraft client) {
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.efficientMinerEsp || client.player == null || client.level == null) {
			return List.of();
		}
		if (!MiningTracker.inMiningIsland()) {
			return List.of();
		}
		if (!holdingMiner(client.player)) {
			return List.of();
		}
		if (!(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return List.of();
		}
		BlockPos origin = hit.getBlockPos();
		BlockState state = client.level.getBlockState(origin);
		if (!mineable(state)) {
			return List.of();
		}
		Breakdown math = breakdown(MiningTracker.effectiveSpread());
		if (math.maxExtras() <= 0) {
			return List.of();
		}
		boolean lookingDown = client.player.getXRot() > 0.5f;
		int feetY = client.player.blockPosition().getY();
		return collect(client.level, origin, state.getBlock(), math, feetY, lookingDown, client.player.getViewVector(1f));
	}

	public static Breakdown breakdown(int spread) {
		spread = VoidmarkConfig.clamp(spread, 0, 10_000);
		return new Breakdown(spread, spread / 100, spread % 100);
	}

	public static int extraCount(int spread) {
		return breakdown(spread).maxExtras();
	}

	private static List<Target> collect(
		Level level,
		BlockPos origin,
		Block type,
		Breakdown math,
		int feetY,
		boolean lookingDown,
		Vec3 look
	) {
		List<BlockPos> pool = new ArrayList<>(26);
		for (int[] off : OFFSETS) {
			BlockPos next = origin.offset(off[0], off[1], off[2]);
			if (!lookingDown && next.getY() < feetY) {
				continue;
			}
			if (!level.hasChunkAt(next)) {
				continue;
			}
			BlockState state = level.getBlockState(next);
			if (!sameVein(type, state.getBlock()) || !mineable(state)) {
				continue;
			}
			pool.add(next);
		}
		if (pool.isEmpty()) {
			return List.of();
		}
		double ox = origin.getX() + 0.5;
		double oy = origin.getY() + 0.5;
		double oz = origin.getZ() + 0.5;
		pool.sort(Comparator.comparingDouble((BlockPos pos) -> {
			double dx = pos.getX() + 0.5 - ox;
			double dy = pos.getY() + 0.5 - oy;
			double dz = pos.getZ() + 0.5 - oz;
			return -(dx * look.x + dy * look.y + dz * look.z);
		}).thenComparingLong(BlockPos::asLong));
		List<Target> out = new ArrayList<>(pool.size());
		for (int i = 0; i < pool.size(); i++) {
			Kind kind;
			if (i < math.guaranteed()) {
				kind = Kind.SURE;
			} else if (i == math.guaranteed() && math.chancePercent() > 0) {
				kind = Kind.CHANCE;
			} else {
				kind = Kind.POOL;
			}
			out.add(new Target(pool.get(i), kind));
		}
		return List.copyOf(out);
	}

	/** All 26 neighbors in the 3×3×3, including edge and corner diagonals. */
	private static int[][] bakeOffsets() {
		int[][] raw = new int[26][3];
		int i = 0;
		for (int x = -1; x <= 1; x++) {
			for (int y = -1; y <= 1; y++) {
				for (int z = -1; z <= 1; z++) {
					if (x == 0 && y == 0 && z == 0) {
						continue;
					}
					raw[i][0] = x;
					raw[i][1] = y;
					raw[i][2] = z;
					i++;
				}
			}
		}
		return raw;
	}

	static boolean sameVein(Block origin, Block other) {
		if (origin == other) {
			return true;
		}
		return prismarine(origin) && prismarine(other);
	}

	private static boolean prismarine(Block block) {
		return block == Blocks.PRISMARINE
			|| block == Blocks.PRISMARINE_BRICKS
			|| block == Blocks.DARK_PRISMARINE;
	}

	static boolean mineable(BlockState state) {
		if (state == null || state.isAir()) {
			return false;
		}
		if (!state.getFluidState().isEmpty() && state.getFluidState().isSource()) {
			return false;
		}
		Block block = state.getBlock();
		if (block == Blocks.BEDROCK || block == Blocks.BARRIER || block == Blocks.VOID_AIR) {
			return false;
		}
		String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
		if (path.contains("glass") || path.contains("chest") || path.contains("spawner")) {
			return false;
		}
		return state.getBlock().defaultDestroyTime() >= 0f;
	}

	static boolean holdingMiner(LocalPlayer player) {
		ItemStack stack = player.getMainHandItem();
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS)) {
			return true;
		}
		String id = ItemIds.skyblockId(stack);
		if (id == null || id.isBlank()) {
			return false;
		}
		String key = id.toUpperCase(Locale.ROOT);
		return key.contains("DRILL")
			|| key.contains("PICKAXE")
			|| key.contains("GAUNTLET")
			|| key.contains("CHISEL");
	}

	public record Breakdown(int spread, int guaranteed, int chancePercent) {
		public int maxExtras() {
			return guaranteed + (chancePercent > 0 ? 1 : 0);
		}

		public String label() {
			if (spread <= 0) {
				return "0 extra";
			}
			if (guaranteed == 0) {
				return chancePercent + "% for 1";
			}
			if (chancePercent == 0) {
				return guaranteed + " extra";
			}
			return guaranteed + " extra + " + chancePercent + "%";
		}
	}

	public record Target(BlockPos pos, Kind kind) {
		public boolean guaranteed() {
			return kind == Kind.SURE;
		}
	}

	public enum Kind {
		SURE,
		CHANCE,
		POOL
	}
}
