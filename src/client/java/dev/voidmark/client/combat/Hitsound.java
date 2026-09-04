package dev.voidmark.client.combat;

import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Plays a hitsound as soon as the client sees a melee swing or an arrow
 * overlap a mob, instead of waiting for the server to confirm damage.
 * Hypixel often reports mob health as 0, so we never gate on {@code isAlive()}.
 */
public final class Hitsound {
	private static final SoundEvent SOUND = SoundEvent.createVariableRangeEvent(Voidmark.id("hit"));
	private static final double ARROW_MARGIN = 0.6;
	private static final int ARROW_DEBOUNCE = 6;
	private static final int VANILLA_PING_TICKS = 20;
	private static final Long2IntOpenHashMap HITS = new Long2IntOpenHashMap();
	private static final Int2IntOpenHashMap TARGETS = new Int2IntOpenHashMap();
	private static int gameTick;
	private static int lastVanillaSuppressTick = Integer.MIN_VALUE;
	private static int lastMeleeTick = Integer.MIN_VALUE;

	private Hitsound() {
	}

	public static void reset() {
		HITS.clear();
		TARGETS.clear();
		gameTick = 0;
		lastVanillaSuppressTick = Integer.MIN_VALUE;
		lastMeleeTick = Integer.MIN_VALUE;
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
		AABB around = player.getBoundingBox().inflate(48.0);
		for (Entity entity : player.level().getEntities(player, around, Hitsound::isArrow)) {
			predictArrow(player, (AbstractArrow) entity);
		}
	}

	public static void onMelee(Entity attacker, Entity target) {
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.hitsoundEnabled || !config.hitsoundMelee) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || attacker != player || target == null || player.isSpectator()) {
			return;
		}
		if (!isMeleeTarget(target, player)) {
			return;
		}
		lastMeleeTick = gameTick;
		play(config);
		stampNearby(player, target);
	}

	public static void onArrowHit(AbstractArrow arrow, Entity target) {
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.hitsoundEnabled || !config.hitsoundArrows) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !shotByLocalPlayer(arrow, player) || !isArrowTarget(target, player)) {
			return;
		}
		if (markHit(arrow.getId(), target.getId())) {
			play(config);
		}
	}

	/** Backup when client collision missed but the server still hurt a nearby mob. */
	public static void onHurt(int entityId) {
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.hitsoundEnabled) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			return;
		}
		Entity entity = client.level.getEntity(entityId);
		if (entity == null || entity == player || (!isArrowTarget(entity, player) && !isMeleeTarget(entity, player))) {
			return;
		}
		if (recentTarget(entityId)) {
			return;
		}
		if (config.hitsoundMelee && gameTick - lastMeleeTick <= 8) {
			if (markHit(-1, entityId)) {
				play(config);
			}
			return;
		}
		if (!config.hitsoundArrows) {
			return;
		}
		AABB around = entity.getBoundingBox().inflate(8.0);
		boolean ours = false;
		for (Entity other : player.level().getEntities(entity, around, Hitsound::isArrow)) {
			if (shotByLocalPlayer((AbstractArrow) other, player)) {
				ours = true;
				break;
			}
		}
		if (ours && markHit(-2, entityId)) {
			play(config);
		}
	}

	/** Confirmed vanilla damage with the local player as the cause. */
	public static void onDamage(int entityId, int causeId) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		if (causeId == player.getId()) {
			VoidmarkConfig config = VoidmarkConfig.get();
			if (!config.hitsoundEnabled) {
				return;
			}
			if (client.level == null) {
				return;
			}
			Entity entity = client.level.getEntity(entityId);
			if (entity == null || entity == player) {
				return;
			}
			if (!isMeleeTarget(entity, player) && !isArrowTarget(entity, player)) {
				return;
			}
			if (markHit(-3, entityId)) {
				play(config);
			}
			return;
		}
		onHurt(entityId);
	}

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
		AABB search = arrow.getBoundingBox().expandTowards(to.subtract(from)).inflate(ARROW_MARGIN + 0.5);
		for (Entity other : player.level().getEntities(arrow, search, entity -> isArrowTarget(entity, player))) {
			AABB hitbox = other.getBoundingBox().inflate(ARROW_MARGIN);
			if (hitbox.contains(from) || hitbox.contains(to) || hitbox.clip(from, to).isPresent()) {
				if (markHit(arrow.getId(), other.getId())) {
					play(VoidmarkConfig.get());
				}
			}
		}
	}

	private static boolean isMeleeTarget(Entity entity, LocalPlayer player) {
		if (entity == null || entity == player || entity.isRemoved()) {
			return false;
		}
		if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
			return false;
		}
		if (entity instanceof Player other && other.getUUID().version() == 4) {
			return false;
		}
		return entity instanceof LivingEntity || entity instanceof Interaction || entity instanceof Display;
	}

	private static boolean isArrowTarget(Entity entity, LocalPlayer player) {
		if (!(entity instanceof LivingEntity living) || living == player || living.isRemoved()) {
			return false;
		}
		if (living instanceof ArmorStand) {
			return false;
		}
		return !(living instanceof Player other) || other.getUUID().version() != 4;
	}

	private static boolean isArrow(Entity entity) {
		return entity instanceof AbstractArrow && !entity.isRemoved();
	}

	private static boolean shotByLocalPlayer(AbstractArrow arrow, LocalPlayer player) {
		Entity owner = arrow.getOwner();
		if (owner == player) {
			return true;
		}
		if (owner != null) {
			return owner.getUUID().equals(player.getUUID());
		}
		if (arrow.tickCount > 50) {
			return false;
		}
		double dx = arrow.getX() - player.getX();
		double dz = arrow.getZ() - player.getZ();
		if (dx * dx + dz * dz > 36.0) {
			return false;
		}
		Vec3 vel = arrow.getDeltaMovement();
		if (vel.lengthSqr() < 0.01) {
			return player.distanceToSqr(arrow) < 9.0 && arrow.tickCount < 8;
		}
		return vel.normalize().dot(player.getLookAngle()) > 0.2;
	}

	private static boolean recentTarget(int entityId) {
		int last = TARGETS.get(entityId);
		return last != 0 && gameTick - last < ARROW_DEBOUNCE;
	}

	private static void stampNearby(LocalPlayer player, Entity target) {
		stampTarget(target.getId());
		if (player.level() == null) {
			return;
		}
		AABB box = target.getBoundingBox().inflate(2.5, 3.5, 2.5);
		for (Entity other : player.level().getEntities(target, box, entity -> entity instanceof LivingEntity)) {
			stampTarget(other.getId());
		}
	}

	private static void stampTarget(int entityId) {
		TARGETS.put(entityId, gameTick);
		lastVanillaSuppressTick = gameTick;
	}

	private static boolean markHit(int a, int b) {
		if (recentTarget(b)) {
			return false;
		}
		long key = ((long) a << 32) ^ (b & 0xFFFFFFFFL);
		int last = HITS.get(key);
		if (last != 0 && gameTick - last < ARROW_DEBOUNCE) {
			return false;
		}
		HITS.put(key, gameTick);
		stampTarget(b);
		return true;
	}

	private static void play(VoidmarkConfig config) {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return;
		}
		float volume = config.hitsoundVolume <= 0.01f ? 0.80f : config.hitsoundVolume;
		float pitch = config.hitsoundPitch <= 0.05f ? 1.00f : config.hitsoundPitch;
		if (!client.isSameThread()) {
			float v = volume;
			float p = pitch;
			client.execute(() -> playNow(client, v, p));
			return;
		}
		playNow(client, volume, pitch);
	}

	private static void playNow(Minecraft client, float volume, float pitch) {
		if (client.getSoundManager() == null) {
			return;
		}
		SoundEngine.PlayResult result = client.getSoundManager().play(new SimpleSoundInstance(
			SOUND.location(),
			SoundSource.MASTER,
			volume,
			pitch,
			SoundInstance.createUnseededRandom(),
			false,
			0,
			SoundInstance.Attenuation.NONE,
			0.0,
			0.0,
			0.0,
			true
		));
		if (result == SoundEngine.PlayResult.NOT_STARTED || result == SoundEngine.PlayResult.STARTED_SILENTLY) {
			client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARROW_HIT_PLAYER, pitch, volume));
		}
	}

	private static void prune() {
		if (!HITS.isEmpty()) {
			HITS.long2IntEntrySet().removeIf(entry -> gameTick - entry.getIntValue() > 40);
		}
		if (!TARGETS.isEmpty()) {
			TARGETS.int2IntEntrySet().removeIf(entry -> gameTick - entry.getIntValue() > 40);
		}
	}
}
