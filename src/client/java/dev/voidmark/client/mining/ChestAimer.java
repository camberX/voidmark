package dev.voidmark.client.mining;

import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Smoothly looks at each distinct crit box on a nearby treasure chest, waits
 * until that box jumps or vanishes, then turns to another. Five locks is one
 * pass; if the chest is still there it keeps going.
 */
public final class ChestAimer {
	private static final float TOLERANCE = 1f;
	private static final long MOVE_WAIT_MS = 3_000L;
	private static final int PASS = 5;
	private static final double SAME = 0.45;

	private static boolean running;
	private static ChestEsp.Mark chest;
	private static Vec3 look = Vec3.ZERO;
	private static boolean waiting;
	private static boolean turning;
	private static long waitedAt;
	private static long turnStart;
	private static long turnMs;
	private static SmoothRotate.Rotation from = new SmoothRotate.Rotation(0f, 0f);
	private static SmoothRotate.Rotation to = new SmoothRotate.Rotation(0f, 0f);
	private static int tries;

	private ChestAimer() {
	}

	public static boolean running() {
		return running;
	}

	public static void toggle() {
		Minecraft client = Minecraft.getInstance();
		if (running) {
			stop(client, "Chest aim stopped");
			return;
		}
		if (!ChestEsp.active() || client.player == null) {
			tell(client, "Chest ESP is off, or you are not in Dwarven Mines / Crystal Hollows");
			return;
		}
		if (!bindChest(client.player)) {
			tell(client, "No chest box nearby");
			return;
		}
		running = true;
		waiting = false;
		turning = false;
		tries = 0;
		tell(client, "Chest aim started · press again to stop");
	}

	public static void stop() {
		stop(Minecraft.getInstance(), null);
	}

	public static void tick(Minecraft client) {
		if (!running) {
			return;
		}
		if (client.player == null || client.screen != null || !ChestEsp.active()) {
			stop(client, "Chest aim stopped");
			return;
		}
		LocalPlayer player = client.player;
		if (chest == null || ChestEsp.get().chestAt(chest.pos) == null) {
			tries = 0;
			waiting = false;
			turning = false;
			if (!bindChest(player)) {
				stop(client, "Chest aim done");
				return;
			}
		}
		if (turning) {
			if (advance(player)) {
				waiting = true;
				turning = false;
				waitedAt = System.currentTimeMillis();
				tries++;
				if (tries >= PASS && ChestEsp.get().chestAt(chest.pos) != null) {
					tries = 0;
				}
			}
			return;
		}
		if (waiting) {
			if (lockedStillHere() && !dueForOther()) {
				return;
			}
			Vec3 next = pickNext(player, look);
			if (next == null) {
				return;
			}
			waiting = false;
			beginTurn(player, next);
			return;
		}
		Vec3 first = pickFirst(player);
		if (first != null) {
			beginTurn(player, first);
		}
	}

	private static boolean bindChest(LocalPlayer player) {
		chest = ChestEsp.get().nearestChest(player.position());
		return chest != null;
	}

	private static List<Vec3> boxes() {
		return ChestEsp.get().boxesNear(chest);
	}

	private static Vec3 pickFirst(LocalPlayer player) {
		Vec3 best = null;
		float bestAng = Float.MAX_VALUE;
		for (Vec3 point : boxes()) {
			float ang = angleTo(player, point);
			if (ang < bestAng) {
				bestAng = ang;
				best = point;
			}
		}
		return best;
	}

	private static Vec3 pickNext(LocalPlayer player, Vec3 last) {
		Vec3 best = null;
		float bestAng = -1f;
		for (Vec3 point : boxes()) {
			if (last != null && point.closerThan(last, SAME)) {
				continue;
			}
			float ang = angleTo(player, point);
			if (ang > bestAng) {
				bestAng = ang;
				best = point;
			}
		}
		return best;
	}

	private static boolean lockedStillHere() {
		for (Vec3 point : boxes()) {
			if (point.closerThan(look, 0.40)) {
				return true;
			}
		}
		return false;
	}

	private static boolean dueForOther() {
		if (System.currentTimeMillis() - waitedAt <= MOVE_WAIT_MS) {
			return false;
		}
		for (Vec3 point : boxes()) {
			if (!point.closerThan(look, SAME)) {
				return true;
			}
		}
		return false;
	}

	private static float angleTo(LocalPlayer player, Vec3 target) {
		SmoothRotate.Rotation now = new SmoothRotate.Rotation(
			SmoothRotate.normalizeYaw(player.getYRot()),
			SmoothRotate.normalizePitch(player.getXRot())
		);
		SmoothRotate.Rotation dest = SmoothRotate.to(player.getEyePosition(), target);
		return Math.abs(SmoothRotate.normalizeYaw(dest.yaw() - now.yaw()))
			+ Math.abs(dest.pitch() - now.pitch());
	}

	private static void beginTurn(LocalPlayer player, Vec3 target) {
		look = target;
		from = new SmoothRotate.Rotation(
			SmoothRotate.normalizeYaw(player.getYRot()),
			SmoothRotate.normalizePitch(player.getXRot())
		);
		to = SmoothRotate.to(player.getEyePosition(), target);
		if (SmoothRotate.close(from, to, TOLERANCE)) {
			turning = false;
			waiting = true;
			waitedAt = System.currentTimeMillis();
			tries++;
			if (tries >= PASS && ChestEsp.get().chestAt(chest.pos) != null) {
				tries = 0;
			}
			return;
		}
		float span = Math.max(
			Math.abs(SmoothRotate.normalizeYaw(to.yaw() - from.yaw())),
			Math.abs(to.pitch() - from.pitch())
		);
		float speed = VoidmarkConfig.clamp(VoidmarkConfig.get().chestAimSpeed, 0.25f, 2.00f);
		long base = 160L + Math.round(span * 2.4f);
		turnMs = Math.max(80L, Math.min(800L, Math.round(base / speed)));
		turnStart = System.currentTimeMillis();
		turning = true;
	}

	private static boolean advance(LocalPlayer player) {
		if (!turning) {
			return waiting;
		}
		long elapsed = System.currentTimeMillis() - turnStart;
		double progress = turnMs <= 0L ? 1.0 : Math.min(elapsed / (double) turnMs, 1.0);
		float ease = SmoothRotate.easeInOutCubic(progress);
		float yaw = SmoothRotate.interpolateYaw(from.yaw(), to.yaw(), ease);
		float pitch = SmoothRotate.lerp(from.pitch(), to.pitch(), ease);
		SmoothRotate.apply(player, yaw, pitch);
		return progress >= 1.0;
	}

	private static void stop(Minecraft client, String reason) {
		running = false;
		chest = null;
		waiting = false;
		turning = false;
		tries = 0;
		if (reason != null) {
			tell(client, reason);
		}
	}

	private static void tell(Minecraft client, String text) {
		if (client == null || client.player == null) {
			return;
		}
		client.player.sendSystemMessage(
			Component.literal("VOIDMARK").withStyle(Style.EMPTY.withColor(0x2FB5FF).withBold(true))
				.append(Component.literal(" · ").withStyle(Style.EMPTY.withColor(0x6B7280).withBold(false)))
				.append(Component.literal(text).withStyle(Style.EMPTY.withColor(0xE5E7EB).withBold(false)))
		);
	}
}
