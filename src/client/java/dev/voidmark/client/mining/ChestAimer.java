package dev.voidmark.client.mining;

import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Smoothly looks at a crit mark on a nearby treasure chest and holds until
 * Hypixel's lock-pick ding plays, then turns to the next mark.
 */
public final class ChestAimer {
	private static final float TOLERANCE = 1f;
	private static final int PASS = 5;
	private static final double SAME = 0.18;

	private static boolean running;
	private static ChestEsp.Mark chest;
	private static ChestEsp.Mark locked;
	private static Vec3 lastAt;
	private static boolean waiting;
	private static boolean turning;
	private static boolean armed;
	private static boolean ding;
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

	public static void onPacket(Packet<?> packet) {
		if (!running) {
			return;
		}
		if (packet instanceof ClientboundSoundPacket sound) {
			if (!isDing(sound.getSound())) {
				return;
			}
			Minecraft.getInstance().execute(ChestAimer::hear);
			return;
		}
		if (packet instanceof ClientboundSoundEntityPacket sound) {
			if (!isDing(sound.getSound())) {
				return;
			}
			Minecraft.getInstance().execute(ChestAimer::hear);
		}
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
		armed = false;
		ding = false;
		locked = null;
		lastAt = null;
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
			armed = false;
			ding = false;
			locked = null;
			lastAt = null;
			if (!bindChest(player)) {
				stop(client, "Chest aim done");
				return;
			}
		}
		if (turning) {
			if (advance(player)) {
				arrive();
			}
			return;
		}
		if (waiting) {
			if (!ding) {
				return;
			}
			ding = false;
			armed = false;
			waiting = false;
			lastAt = locked == null ? lastAt : locked.box();
			tries++;
			if (tries >= PASS && chest != null && ChestEsp.get().chestAt(chest.pos) != null) {
				tries = 0;
			}
		}
		ChestEsp.Mark next = pickNext(player, locked, lastAt);
		if (next != null) {
			beginTurn(player, next);
		}
	}

	private static boolean bindChest(LocalPlayer player) {
		chest = ChestEsp.get().nearestChest(player.position());
		return chest != null;
	}

	private static List<ChestEsp.Mark> marks() {
		return ChestEsp.get().marksNear(chest);
	}

	private static ChestEsp.Mark pickNext(LocalPlayer player, ChestEsp.Mark last, Vec3 avoid) {
		ChestEsp.Mark best = null;
		float bestAng = last == null && avoid == null ? Float.MAX_VALUE : -1f;
		boolean first = last == null && avoid == null;
		for (ChestEsp.Mark mark : marks()) {
			if (same(last, avoid, mark)) {
				continue;
			}
			float ang = angleTo(player, mark.box());
			if (first ? ang < bestAng : ang > bestAng) {
				bestAng = ang;
				best = mark;
			}
		}
		return best;
	}

	private static boolean same(ChestEsp.Mark last, Vec3 lastAt, ChestEsp.Mark mark) {
		if (last != null && mark == last) {
			return true;
		}
		return lastAt != null && mark.box().closerThan(lastAt, SAME);
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

	private static void beginTurn(LocalPlayer player, ChestEsp.Mark target) {
		locked = target;
		lastAt = target.box();
		armed = true;
		ding = false;
		Vec3 at = target.box();
		from = new SmoothRotate.Rotation(
			SmoothRotate.normalizeYaw(player.getYRot()),
			SmoothRotate.normalizePitch(player.getXRot())
		);
		to = SmoothRotate.to(player.getEyePosition(), at);
		if (SmoothRotate.close(from, to, TOLERANCE)) {
			arrive();
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
		waiting = false;
	}

	private static void arrive() {
		turning = false;
		waiting = true;
		armed = true;
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

	private static void hear() {
		if (running && armed) {
			ding = true;
		}
	}

	private static boolean isDing(Holder<SoundEvent> holder) {
		if (holder == null) {
			return false;
		}
		SoundEvent event = holder.value();
		if (event == null) {
			return false;
		}
		if (event == SoundEvents.EXPERIENCE_ORB_PICKUP) {
			return true;
		}
		String path = event.location().getPath();
		return path.contains("experience_orb") || path.equals("random.orb");
	}

	private static void stop(Minecraft client, String reason) {
		running = false;
		chest = null;
		locked = null;
		lastAt = null;
		waiting = false;
		turning = false;
		armed = false;
		ding = false;
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
