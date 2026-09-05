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
 * Plays a hitsound as soon as this client lands a charged melee swing or one
 * of its own arrows overlaps a mob. Other players' hits are ignored.
 * Melee waits for vanilla charge ({@code >= 0.9}), the item's attack delay,
 * and the target's hurt-time so spam-clicks during hit delay stay quiet.
 * Hypixel often reports mob health as 0, so we never gate on {@code isAlive()}.
 */
public final class Hitsound {
	private static final SoundEvent SOUND = SoundEvent.createVariableRangeEvent(Voidmark.id("hit"));
	private static final double ARROW_MARGIN = 0.6;
	private static final int ARROW_DEBOUNCE = 6;
	private static final int VANILLA_PING_TICKS = 20;
	private static final float CHARGED_HIT = 0.9f;
	private static final int MIN_MELEE_DELAY = 10;
	private static final Long2IntOpenHashMap HITS = new Long2IntOpenHashMap();
	private static final Int2IntOpenHashMap TARGETS = new Int2IntOpenHashMap();
	private static final Int2IntOpenHashMap MELEE = new Int2IntOpenHashMap();
	private static int gameTick;
	private static int lastVanillaSuppressTick = Integer.MIN_VALUE;

	private Hitsound() {
	}

	public static void reset() {
		HITS.clear();
		TARGETS.clear();
		MELEE.clear();
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
		if (!isMeleeTarget(target, player) || !meleeReady(player, target)) {
			return;
		}
		MELEE.put(target.getId(), gameTick);
		land(config, sound, mark);
		stampNearby(player, target);
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
		if (markHit(arrow.getId(), target.getId())) {
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
				if (markHit(arrow.getId(), other.getId())) {
					VoidmarkConfig config = VoidmarkConfig.get();
					land(config, config.hitsoundEnabled && config.hitsoundArrows, config.hitmarkerEnabled);
				}
			}
		}
	}

	private static boolean wantArrow(VoidmarkConfig config) {
		return config.hitmarkerEnabled || config.hitsoundEnabled && config.hitsoundArrows;
	}

	private static boolean meleeReady(LocalPlayer player, Entity target) {
		if (player.cannotAttackWithItem(player.getWeaponItem(), 0)) {
			return false;
		}
		if (player.getAttackStrengthScale(0.0f) < CHARGED_HIT) {
			return false;
		}
		if (target instanceof LivingEntity living && living.hurtTime > 0) {
			return false;
		}
		int last = MELEE.get(target.getId());
		int wait = Math.max(MIN_MELEE_DELAY, Math.round(player.getCurrentItemAttackStrengthDelay()));
		return last == 0 || gameTick - last >= wait;
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
		if (!MELEE.isEmpty()) {
			MELEE.int2IntEntrySet().removeIf(entry -> gameTick - entry.getIntValue() > 40);
		}
	}
}
