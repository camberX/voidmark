package dev.voidmark.client.combat;

import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Plays a hitsound as soon as the client sees a melee swing or an arrow
 * overlap a mob, instead of waiting for the server to confirm damage.
 */
public final class Hitsound {
	private static final SoundEvent SOUND = SoundEvent.createVariableRangeEvent(Voidmark.id("hit"));
	private static final double ARROW_MARGIN = 0.45;
	private static final int ARROW_DEBOUNCE = 8;
	private static final int VANILLA_PING_TICKS = 15;
	private static final Long2IntOpenHashMap ARROW_HITS = new Long2IntOpenHashMap();
	private static int gameTick;
	private static int lastVanillaSuppressTick = Integer.MIN_VALUE;

	private Hitsound() {
	}

	public static void reset() {
		ARROW_HITS.clear();
		gameTick = 0;
		lastVanillaSuppressTick = Integer.MIN_VALUE;
	}

	public static void tick(Minecraft client) {
		gameTick++;
		if ((gameTick & 31) == 0) {
			prune();
		}
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.hitsoundEnabled || !config.hitsoundArrows) {
			return;
		}
		LocalPlayer player = client.player;
		if (player == null || client.level == null || client.isPaused()) {
			return;
		}
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof AbstractArrow arrow) {
				predictArrow(player, arrow);
			}
		}
	}

	public static void onMelee(Entity attacker, Entity target) {
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.hitsoundEnabled || !config.hitsoundMelee) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || attacker != player || target == null) {
			return;
		}
		if (player.isSpectator()) {
			return;
		}
		Entity mob = resolveMeleeTarget(player, target);
		if (mob == null) {
			return;
		}
		play(config);
	}

	public static void onArrowHit(AbstractArrow arrow, Entity target) {
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.hitsoundEnabled || !config.hitsoundArrows) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !shotByLocalPlayer(arrow, player) || !isCombatMob(target, player)) {
			return;
		}
		if (markArrowHit(arrow, target)) {
			play(config);
		}
	}

	/** Drop the delayed vanilla arrow-ding when we already predicted the hit. */
	public static boolean suppressVanillaArrowPing() {
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.hitsoundEnabled || !config.hitsoundArrows) {
			return false;
		}
		return gameTick - lastVanillaSuppressTick <= VANILLA_PING_TICKS;
	}

	public static void playPreview() {
		play(VoidmarkConfig.get());
	}

	private static void predictArrow(LocalPlayer player, AbstractArrow arrow) {
		if (arrow.isRemoved() || arrow.tickCount < 1) {
			return;
		}
		if (!shotByLocalPlayer(arrow, player)) {
			return;
		}
		Vec3 from = arrow.oldPosition();
		Vec3 to = arrow.position();
		if (from.distanceToSqr(to) < 1.0E-6) {
			to = from.add(arrow.getDeltaMovement());
		}
		if (from.distanceToSqr(to) < 1.0E-4 && arrow.getDeltaMovement().lengthSqr() < 0.002) {
			return;
		}
		AABB search = arrow.getBoundingBox().expandTowards(to.subtract(from)).inflate(ARROW_MARGIN + 0.35);
		for (Entity other : player.level().getEntities(arrow, search, entity -> isCombatMob(entity, player))) {
			AABB hitbox = other.getBoundingBox().inflate(ARROW_MARGIN);
			if (hitbox.contains(from) || hitbox.contains(to) || hitbox.clip(from, to).isPresent()) {
				if (markArrowHit(arrow, other)) {
					play(VoidmarkConfig.get());
				}
			}
		}
	}

	private static Entity resolveMeleeTarget(LocalPlayer player, Entity target) {
		if (isCombatMob(target, player)) {
			return target;
		}
		if (target instanceof Avatar) {
			return null;
		}
		if (!(target instanceof ArmorStand) && !(target instanceof Display) && !(target instanceof Interaction)) {
			return null;
		}
		AABB box = target.getBoundingBox().inflate(1.6, 3.2, 1.6).move(0.0, -1.8, 0.0);
		Entity best = null;
		double bestDist = Double.MAX_VALUE;
		for (Entity other : player.level().getEntities(target, box, entity -> isCombatMob(entity, player))) {
			double dist = other.distanceToSqr(target);
			if (dist < bestDist) {
				bestDist = dist;
				best = other;
			}
		}
		return best;
	}

	private static boolean isCombatMob(Entity entity, LocalPlayer player) {
		if (!(entity instanceof LivingEntity living) || living == player) {
			return false;
		}
		if (living instanceof Avatar || living instanceof ArmorStand) {
			return false;
		}
		return living.isAlive() && !living.isRemoved();
	}

	private static boolean shotByLocalPlayer(AbstractArrow arrow, LocalPlayer player) {
		Entity owner = arrow.getOwner();
		if (owner == player) {
			return true;
		}
		if (owner != null) {
			return false;
		}
		if (arrow.tickCount > 40) {
			return false;
		}
		double dx = arrow.getX() - player.getX();
		double dz = arrow.getZ() - player.getZ();
		if (dx * dx + dz * dz > 16.0) {
			return false;
		}
		Vec3 look = player.getLookAngle();
		Vec3 vel = arrow.getDeltaMovement();
		if (vel.lengthSqr() < 0.04) {
			return false;
		}
		return vel.normalize().dot(look) > 0.35;
	}

	private static boolean markArrowHit(AbstractArrow arrow, Entity target) {
		long key = ((long) arrow.getId() << 32) ^ (target.getId() & 0xFFFFFFFFL);
		int last = ARROW_HITS.get(key);
		if (last != 0 && gameTick - last < ARROW_DEBOUNCE) {
			return false;
		}
		ARROW_HITS.put(key, gameTick);
		lastVanillaSuppressTick = gameTick;
		return true;
	}

	private static void play(VoidmarkConfig config) {
		float volume = config.hitsoundVolume;
		if (volume <= 0.01f) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.getSoundManager() == null) {
			return;
		}
		client.getSoundManager().play(new SimpleSoundInstance(
			SOUND.location(),
			SoundSource.PLAYERS,
			volume,
			config.hitsoundPitch,
			SoundInstance.createUnseededRandom(),
			false,
			0,
			SoundInstance.Attenuation.NONE,
			0.0,
			0.0,
			0.0,
			true
		));
	}

	private static void prune() {
		if (ARROW_HITS.isEmpty()) {
			return;
		}
		ARROW_HITS.long2IntEntrySet().removeIf(entry -> gameTick - entry.getIntValue() > 40);
	}
}
