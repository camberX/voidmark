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
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Plays a hitsound as soon as this client lands a melee swing or one of its
 * own arrows overlaps a mob. Other players' hits are ignored.
 * Melee and arrows use a 1-tick delay per entity, not a global cooldown or
 * a nearby-mob stamp. The 1.9 weapon charge bar and hurt-time are ignored.
 * Hypixel often reports mob health as 0, so we never gate on {@code isAlive()}.
 */
public final class Hitsound {
	private static final SoundEvent SOUND = SoundEvent.createVariableRangeEvent(Voidmark.id("hit"));
	private static final double ARROW_MARGIN = 0.6;
	private static final int ARROW_PAIR = 2;
	private static final int VANILLA_PING_TICKS = 20;
	private static final int ENTITY_DELAY = 1;
	private static final Long2IntOpenHashMap ARROWS = new Long2IntOpenHashMap();
	private static final Int2IntOpenHashMap LAST = new Int2IntOpenHashMap();
	private static int gameTick;
	private static int lastVanillaSuppressTick = Integer.MIN_VALUE;

	private Hitsound() {
	}

	public static void reset() {
		ARROWS.clear();
		LAST.clear();
		gameTick = 0;
		lastVanillaSuppressTick = Integer.MIN_VALUE;
	}

	public static void tick(Minecraft client) {
		gameTick++;
		if ((gameTick & 31) == 0) {
			prune();
		}
		VoidmarkConfig config = VoidmarkConfig.get();
		// Mixin onHitEntity already covers arrows for hitsound and hitmarker.
		// This pass is only a backup when a hitsound arrow despawns first.
		if (!config.hitsoundEnabled || !config.hitsoundArrows) {
			return;
		}
		LocalPlayer player = client.player;
		if (player == null || client.level == null || client.isPaused()) {
			return;
		}
		AABB around = player.getBoundingBox().inflate(48.0);
		for (AbstractArrow arrow : player.level().getEntities(
			EntityTypeTest.forClass(AbstractArrow.class),
			around,
			candidate -> !candidate.isRemoved() && shotByLocalPlayer(candidate, player)
		)) {
			predictArrow(player, arrow);
		}
	}

	public static void onMelee(Entity attacker, Entity target) {
		VoidmarkConfig config = VoidmarkConfig.get();
		boolean sound = config.hitsoundEnabled && config.hitsoundMelee;
		boolean mark = config.hitmarkerEnabled;
		if (!sound && !mark) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || attacker != player || target == null || player.isSpectator()) {
			return;
		}
		if (!isMeleeTarget(target, player) || !entityReady(target.getId())) {
			return;
		}
		stampEntity(target.getId());
		land(config, sound, mark);
	}

	public static void onArrowHit(AbstractArrow arrow, Entity target) {
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!wantArrow(config)) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !shotByLocalPlayer(arrow, player) || !isArrowTarget(target, player)) {
			return;
		}
		if (markArrow(arrow.getId(), target.getId())) {
			land(config, config.hitsoundEnabled && config.hitsoundArrows, config.hitmarkerEnabled);
		}
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
				if (markArrow(arrow.getId(), other.getId())) {
					VoidmarkConfig config = VoidmarkConfig.get();
					land(config, config.hitsoundEnabled && config.hitsoundArrows, config.hitmarkerEnabled);
				}
			}
		}
	}

	private static boolean wantArrow(VoidmarkConfig config) {
		return config.hitmarkerEnabled || config.hitsoundEnabled && config.hitsoundArrows;
	}

	private static boolean entityReady(int entityId) {
		int last = LAST.get(entityId);
		return last == 0 || gameTick - last >= ENTITY_DELAY;
	}

	private static void land(VoidmarkConfig config, boolean sound, boolean mark) {
		if (sound) {
			play(config);
		}
		if (mark) {
			Hitmarker.flash();
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

	private static boolean shotByLocalPlayer(AbstractArrow arrow, LocalPlayer player) {
		Entity owner = arrow.getOwner();
		if (owner == player) {
			return true;
		}
		if (owner != null) {
			return owner.getUUID().equals(player.getUUID());
		}
		if (arrow.tickCount > 4 || player.distanceToSqr(arrow) > 6.25) {
			return false;
		}
		Vec3 vel = arrow.getDeltaMovement();
		if (vel.lengthSqr() < 0.04) {
			return false;
		}
		return vel.normalize().dot(player.getLookAngle()) > 0.75;
	}

	private static void stampEntity(int entityId) {
		LAST.put(entityId, gameTick);
		lastVanillaSuppressTick = gameTick;
	}

	private static boolean markArrow(int arrowId, int entityId) {
		if (!entityReady(entityId)) {
			return false;
		}
		long key = ((long) arrowId << 32) ^ (entityId & 0xFFFFFFFFL);
		int last = ARROWS.get(key);
		if (last != 0 && gameTick - last < ARROW_PAIR) {
			return false;
		}
		ARROWS.put(key, gameTick);
		stampEntity(entityId);
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
		if (!ARROWS.isEmpty()) {
			ARROWS.long2IntEntrySet().removeIf(entry -> gameTick - entry.getIntValue() > 40);
		}
		if (!LAST.isEmpty()) {
			LAST.int2IntEntrySet().removeIf(entry -> gameTick - entry.getIntValue() > 40);
		}
	}
}
