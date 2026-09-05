package dev.voidmark.client.mining;

import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.phys.Vec3;

/**
 * Smoothly looks at the moving crit box on a nearby treasure chest. Five
 * locked-on boxes is one pass; if the chest is still there it keeps going.
 */
public final class ChestAimer {
	private static final float TOLERANCE = 1f;
	private static final long MOVE_WAIT_MS = 3_000L;
	private static final int PASS = 5;

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
		if (!retarget(client.player)) {
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
			if (!retarget(player)) {
				stop(client, "Chest aim done");
				return;
			}
		}
		ChestEsp.Mark box = ChestEsp.get().nearestBox(chest);
		if (box == null) {
			return;
		}
		Vec3 next = box.box();
		if (!waiting) {
			if (!turning) {
				beginTurn(player, next);
			}
			if (advance(player)) {
				waiting = true;
				turning = false;
				waitedAt = System.currentTimeMillis();
				box.moved = false;
			}
			return;
		}
		if (box.consumeMove() || moved(look, next) || System.currentTimeMillis() - waitedAt > MOVE_WAIT_MS) {
			look = next;
			waiting = false;
			turning = false;
			tries++;
			if (tries >= PASS && ChestEsp.get().chestAt(chest.pos) != null) {
				tries = 0;
			}
		}
	}

	private static boolean retarget(LocalPlayer player) {
		chest = ChestEsp.get().nearestChest(player.position());
		if (chest == null) {
			return false;
		}
		ChestEsp.Mark box = ChestEsp.get().nearestBox(chest);
		look = box == null ? chest.box() : box.box();
		return true;
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

	private static boolean moved(Vec3 from, Vec3 to) {
		return from.distanceToSqr(to) > 0.16;
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
