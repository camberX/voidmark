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
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Starts when the crosshair is near a lock box, then looks at each remaining
 * mark until Experience Gained.
 */
public final class ChestAimer {
	private static final float TOLERANCE = 0.15f;
	private static final int PASS = 5;
	private static final double SAME = 0.10;
	private static final double AIM_NEAR = 0.40;

	private static boolean running;
	private static boolean listening;
	private static ChestEsp.Mark chest;
	private static ChestEsp.Mark locked;
	private static final List<Vec3> done = new ArrayList<>();
	private static final List<ChestEsp.Mark> doneMarks = new ArrayList<>();
	private static boolean waiting;
	private static boolean turning;
	private static boolean armed;
	private static volatile boolean ding;
	private static long quietUntil;
	private static long dingAt;
	private static long arrivedAt;
	private static Vec3 lastLook;
	private static long turnStart;
	private static long turnMs;
	private static SmoothRotate.Rotation from = new SmoothRotate.Rotation(0f, 0f);
	private static SmoothRotate.Rotation to = new SmoothRotate.Rotation(0f, 0f);
	private static int tries;

	private ChestAimer() {
	}

	public static void init() {
		listen(Minecraft.getInstance());
	}

	public static boolean running() {
		return running;
	}

	public static void onPacket(Packet<?> packet) {
		if (!running) {
			return;
		}
		if (packet instanceof ClientboundSoundPacket sound) {
			if (isDing(sound.getSound())) {
				hear();
			}
			return;
		}
		if (packet instanceof ClientboundSoundEntityPacket sound && isDing(sound.getSound())) {
			hear();
		}
	}

	public static void stop() {
		stop(Minecraft.getInstance(), null);
	}

	public static void tick(Minecraft client) {
		if (client.player == null || !ChestEsp.active()) {
			if (running) {
				stop(client, null);
			}
			return;
		}
		listen(client);
		LocalPlayer player = client.player;
		if (!running) {
			ChestEsp.Mark aim = boxUnderCrosshair(player);
			if (aim == null) {
				return;
			}
			chest = ChestEsp.get().nearestChest(aim.box());
			if (chest == null) {
				return;
			}
			running = true;
			waiting = false;
			turning = false;
			armed = false;
			ding = false;
			locked = null;
			lastLook = null;
			done.clear();
			doneMarks.clear();
			quietUntil = 0L;
			dingAt = 0L;
			arrivedAt = 0L;
			tries = 0;
			beginTurn(player, aim);
			return;
		}
		if (!bindChest(player)) {
			stop(client, null);
			return;
		}
		if (turning) {
			if (advance(player)) {
				arrive();
			} else {
				return;
			}
		}
		if (ding && armed) {
			finishLock();
			return;
		}
		if (waiting) {
			if (locked != null && ChestEsp.get().stillHas(locked) && locked.consumeMove()) {
				Vec3 moved = locked.box();
				if (lastLook == null || moved.closerThan(lastLook, 0.25)) {
					beginTurn(player, locked);
					return;
				}
			}
			boolean gone = locked == null || !ChestEsp.get().stillHas(locked);
			boolean late = arrivedAt > 0L && System.currentTimeMillis() - arrivedAt > 1_100L;
			if (gone || late) {
				ChestEsp.Mark fresh = pickNewest();
				if (fresh != null && fresh != locked) {
					if (locked != null) {
						remember(locked);
					}
					waiting = false;
					armed = false;
					beginTurn(player, fresh);
				}
			}
			return;
		}
		if (System.currentTimeMillis() < quietUntil) {
			return;
		}
		ChestEsp.Mark next = pickNewest();
		if (next == null) {
			next = pickClosest(player);
		}
		if (next != null) {
			beginTurn(player, next);
		}
	}

	private static void listen(Minecraft client) {
		if (listening || client == null || client.getSoundManager() == null) {
			return;
		}
		client.getSoundManager().addListener((instance, events, range) -> {
			Identifier id = instance == null ? null : instance.getIdentifier();
			if (id != null && isDingPath(id.getPath())) {
				hear();
			}
		});
		listening = true;
	}

	private static boolean bindChest(LocalPlayer player) {
		if (chest != null && ChestEsp.get().chestAt(chest.pos) != null) {
			return true;
		}
		chest = ChestEsp.get().nearestChest(player.position());
		return chest != null;
	}

	private static void finishLock() {
		ding = false;
		armed = false;
		waiting = false;
		if (locked != null) {
			remember(locked);
			lastLook = locked.box();
		}
		ChestEsp.get().dropMarks(chest);
		locked = null;
		dingAt = System.currentTimeMillis();
		quietUntil = dingAt + 150L;
		tries++;
		if (tries >= PASS && chest != null && ChestEsp.get().chestAt(chest.pos) != null) {
			tries = 0;
		}
	}

	private static List<ChestEsp.Mark> marks() {
		return ChestEsp.get().marksNear(chest);
	}

	private static ChestEsp.Mark boxUnderCrosshair(LocalPlayer player) {
		ChestEsp.Mark best = null;
		double bestMiss = AIM_NEAR;
		for (ChestEsp.Mark found : ChestEsp.get().chests()) {
			for (ChestEsp.Mark mark : ChestEsp.get().marksNear(found)) {
				double miss = aimMiss(player, mark.box());
				if (miss < bestMiss) {
					bestMiss = miss;
					best = mark;
				}
			}
		}
		return best;
	}

	private static double aimMiss(LocalPlayer player, Vec3 target) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 to = target.subtract(eye);
		double along = to.dot(look);
		if (along <= 0.15) {
			return Double.MAX_VALUE;
		}
		return look.scale(along).add(eye).distanceTo(target);
	}

	private static ChestEsp.Mark pickClosest(LocalPlayer player) {
		ChestEsp.Mark best = null;
		float bestAng = Float.MAX_VALUE;
		for (ChestEsp.Mark mark : marks()) {
			if (seen(mark) || stale(mark)) {
				continue;
			}
			float ang = angleTo(player, mark.box());
			if (ang < bestAng) {
				bestAng = ang;
				best = mark;
			}
		}
		return best;
	}

	private static ChestEsp.Mark pickNewest() {
		ChestEsp.Mark best = null;
		for (ChestEsp.Mark mark : marks()) {
			if (seen(mark) || stale(mark)) {
				continue;
			}
			if (best == null || mark.boxAt > best.boxAt) {
				best = mark;
			}
		}
		return best;
	}

	private static boolean seen(ChestEsp.Mark mark) {
		if (mark == null) {
			return false;
		}
		if (doneMarks.contains(mark)) {
			return true;
		}
		Vec3 at = mark.box();
		for (Vec3 old : done) {
			if (at.closerThan(old, SAME)) {
				return true;
			}
		}
		return false;
	}

	private static boolean stale(ChestEsp.Mark mark) {
		return dingAt > 0L && mark.boxAt <= dingAt;
	}

	private static void remember(ChestEsp.Mark mark) {
		if (mark == null || seen(mark)) {
			return;
		}
		doneMarks.add(mark);
		done.add(mark.box());
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
		boolean follow = target == locked;
		locked = target;
		if (!follow) {
			remember(target);
		}
		armed = true;
		lastLook = target.box();
		Vec3 at = lastLook;
		from = new SmoothRotate.Rotation(
			SmoothRotate.normalizeYaw(player.getYRot()),
			SmoothRotate.normalizePitch(player.getXRot())
		);
		to = SmoothRotate.to(player.getEyePosition(), at);
		if (follow && SmoothRotate.close(from, to, TOLERANCE)) {
			arrive();
			return;
		}
		float span = Math.max(
			Math.abs(SmoothRotate.normalizeYaw(to.yaw() - from.yaw())),
			Math.abs(to.pitch() - from.pitch())
		);
		float speed = VoidmarkConfig.clamp(VoidmarkConfig.get().chestAimSpeed, 0.25f, 2.00f);
		long base = 160L + Math.round(span * 2.4f);
		turnMs = Math.max(120L, Math.min(800L, Math.round(base / speed)));
		turnStart = System.currentTimeMillis();
		turning = true;
		waiting = false;
	}

	private static void arrive() {
		turning = false;
		waiting = true;
		armed = true;
		arrivedAt = System.currentTimeMillis();
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
		if (event == SoundEvents.EXPERIENCE_ORB_PICKUP) {
			return true;
		}
		return event != null && isDingPath(event.location().getPath());
	}

	private static boolean isDingPath(String path) {
		return path != null && (path.contains("experience_orb") || path.equals("random.orb"));
	}

	private static void stop(Minecraft client, String reason) {
		running = false;
		chest = null;
		locked = null;
		lastLook = null;
		done.clear();
		doneMarks.clear();
		quietUntil = 0L;
		dingAt = 0L;
		arrivedAt = 0L;
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
