package dev.voidmark.client.mining;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Smoothly looks at the moving crit box on a nearby treasure chest. Five
 * locked-on boxes is one pass; if the chest is still there it keeps going.
 */
public final class ChestAimer {
	private static final float ARRIVE = 0.85f;
	private static final float MAX_STEP = 16f;
	private static final long MOVE_WAIT_MS = 3_000L;
	private static final int PASS = 5;

	private static boolean running;
	private static ChestEsp.Mark chest;
	private static Vec3 look = Vec3.ZERO;
	private static boolean waiting;
	private static long waitedAt;
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
			look = next;
			if (rotate(player, look)) {
				waiting = true;
				waitedAt = System.currentTimeMillis();
				box.moved = false;
			}
			return;
		}
		if (box.consumeMove() || moved(look, next) || System.currentTimeMillis() - waitedAt > MOVE_WAIT_MS) {
			look = next;
			waiting = false;
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

	private static boolean rotate(LocalPlayer player, Vec3 target) {
		float[] want = angles(player.getEyePosition(), target);
		float yaw = step(player.getYRot(), want[0]);
		float pitch = step(player.getXRot(), want[1]);
		player.setYRot(yaw);
		player.setYHeadRot(yaw);
		player.setXRot(pitch);
		return Math.abs(wrap(want[0] - yaw)) <= ARRIVE && Math.abs(want[1] - pitch) <= ARRIVE;
	}

	private static float step(float current, float target) {
		float delta = wrap(target - current);
		float t = 0.28f;
		float move = Mth.clamp(delta * t, -MAX_STEP, MAX_STEP);
		if (Math.abs(delta) <= ARRIVE) {
			return current + delta;
		}
		return current + move;
	}

	private static float[] angles(Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dy = to.y - from.y;
		double dz = to.z - from.z;
		double horiz = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
		float pitch = (float) (-(Mth.atan2(dy, horiz) * Mth.RAD_TO_DEG));
		return new float[]{yaw, Mth.clamp(pitch, -90f, 90f)};
	}

	private static float wrap(float degrees) {
		return Mth.wrapDegrees(degrees);
	}

	private static boolean moved(Vec3 from, Vec3 to) {
		return from.distanceToSqr(to) > 0.16;
	}

	private static void stop(Minecraft client, String reason) {
		running = false;
		chest = null;
		waiting = false;
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
