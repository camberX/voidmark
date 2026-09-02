package dev.voidmark.client.mining;

import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.item.ItemIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;

/**
 * Predicts extra blocks Mining Spread / Efficient Miner will break.
 *
 * Hypixel rules we can observe:
 * <ul>
 *   <li>Only the same block type, except prismarine / bricks / dark prismarine stay one family.</li>
 *   <li>Walks face-adjacent neighbors (BFS), closest first.</li>
 *   <li>Does not break blocks below your feet unless you are looking down (pitch &gt; 0).</li>
 *   <li>Does not apply to gemstone glass after the spread split.</li>
 *   <li>Spread 145 → 1 guaranteed extra + 45% chance of a second. We highlight the
 *       guaranteed extras, then the next candidate at lower opacity.</li>
 * </ul>
 */
public final class EfficientMiner {
	private static final Direction[] WALK = {
		Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN
	};
	private static final int VISIT_CAP = 160;

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
		int spread = MiningTracker.effectiveSpread();
		int extra = extraCount(spread);
		if (extra <= 0) {
			return List.of();
		}
		int guaranteed = spread / 100;
		boolean lookingDown = client.player.getXRot() > 0.5f;
		int feetY = client.player.blockPosition().getY();
		return collect(client.level, origin, state.getBlock(), extra, guaranteed, feetY, lookingDown);
	}

	public static int extraCount(int spread) {
		spread = VoidmarkConfig.clamp(spread, 0, 10_000);
		int guaranteed = spread / 100;
		int chance = spread % 100;
		return guaranteed + (chance > 0 ? 1 : 0);
	}

	private static List<Target> collect(
		Level level,
		BlockPos origin,
		Block type,
		int extra,
		int guaranteed,
		int feetY,
		boolean lookingDown
	) {
		List<Target> out = new ArrayList<>(extra);
		Set<Long> seen = new HashSet<>();
		Queue<BlockPos> queue = new ArrayDeque<>();
		seen.add(origin.asLong());
		queue.add(origin);
		int visits = 0;
		while (!queue.isEmpty() && out.size() < extra && visits < VISIT_CAP) {
			BlockPos current = queue.poll();
			visits++;
			for (Direction dir : WALK) {
				BlockPos next = current.relative(dir);
				if (!seen.add(next.asLong())) {
					continue;
				}
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
				boolean sure = out.size() < guaranteed;
				out.add(new Target(next, sure));
				queue.add(next);
				if (out.size() >= extra) {
					break;
				}
			}
		}
		return List.copyOf(out);
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

	public record Target(BlockPos pos, boolean guaranteed) {
	}
}
