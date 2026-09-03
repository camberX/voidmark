package dev.voidmark.client.render;

import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.mixin.ClientLevelAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Marks matching mobs for the entity-outline buffer. A custom post shader
 * turns that silhouette into a clean outward gradient, not vanilla sobel.
 * <p>
 * A mob glows when its type is selected in the ESP list, or when
 * {@code /vm esp <text>} is set and its nametag (or a hologram nametag
 * riding / floating above it) contains that text.
 * <p>
 * Nametag ESP reads names from every loaded entity, not only ones Minecraft
 * is currently drawing. Hypixel holograms are often invisible armor stands or
 * text displays whose custom name is already on the client before the plate
 * is shown. If Hypixel has not sent the name yet, it cannot be guessed.
 */
public final class MobGlowRenderer {
	private static final double MAX_RANGE = 96.0;
	private static final double MAX_RANGE_SQ = MAX_RANGE * MAX_RANGE;
	private static final double HOLOGRAM_RANGE = 4.5;
	private static final Pattern STRIP = Pattern.compile("§.");
	private static List<String> cachedIds;
	private static Set<EntityType<?>> cachedTypes = Set.of();
	private static int nameTick = Integer.MIN_VALUE;
	private static String nameNeedle = "";
	private static Set<Integer> nameIds = Set.of();
	private static final Map<UUID, String> remembered = new HashMap<>();

	private MobGlowRenderer() {
	}

	public static void init() {
	}

	public static void reset() {
		nameTick = Integer.MIN_VALUE;
		nameNeedle = "";
		nameIds = Set.of();
		remembered.clear();
	}

	public static int nearbyCount() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return 0;
		}
		int count = 0;
		for (Entity entity : allEntities(client)) {
			if (isEspTarget(entity, client.player)) {
				count++;
			}
		}
		return count;
	}

	/**
	 * True when this entity should enter Minecraft's outline pass for Voidmark ESP.
	 * Vanilla GLOWING (slayers, spectral arrows, the glowing effect) is left alone.
	 */
	public static boolean shouldForceGlow(Entity entity) {
		return outlineColor(entity) != 0;
	}

	public static boolean hasVanillaGlow(Entity entity) {
		return entity != null && entity.isCurrentlyGlowing();
	}

	public static int outlineColor(Entity entity) {
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.mobGlowEnabled || entity == null) {
			return 0;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || entity == client.player) {
			return 0;
		}
		if (hasVanillaGlow(entity) || !isEspTarget(entity, client.player)) {
			return 0;
		}
		Vec3 camera = client.gameRenderer.getMainCamera().position();
		if (entity.distanceToSqr(camera) > MAX_RANGE_SQ) {
			return 0;
		}
		if (!config.mobGlowThroughWalls && occluded(client, camera, entity.getEyePosition())) {
			return 0;
		}
		return packColor(config);
	}

	public static int packColor(VoidmarkConfig config) {
		float opacity = VoidmarkConfig.clamp(config.mobGlowOpacity, 0.15f, 0.90f);
		int alpha = Math.round(opacity * 255f);
		return (alpha << 24) | (config.mobGlowRgb & 0xFFFFFF);
	}

	public static boolean listed(EntityType<?> type) {
		return type != null && selectedTypes().contains(type);
	}

	public static boolean listed(Entity entity) {
		Minecraft client = Minecraft.getInstance();
		return client.player != null && isEspTarget(entity, client.player);
	}

	private static boolean isEspTarget(Entity entity, Entity player) {
		if (entity == null || entity == player || entity.isRemoved() || hasVanillaGlow(entity)) {
			return false;
		}
		if (entity instanceof LivingEntity living && !living.isAlive()) {
			return false;
		}
		if (listed(entity.getType())) {
			return true;
		}
		return nametagHit(entity);
	}

	private static Set<EntityType<?>> selectedTypes() {
		List<String> ids = VoidmarkConfig.get().mobGlowIds;
		if (ids == cachedIds) {
			return cachedTypes;
		}
		cachedIds = ids;
		Set<EntityType<?>> types = new HashSet<>();
		if (ids != null) {
			for (String id : ids) {
				EntityType<?> type = MobCatalog.type(id);
				if (type != null) {
					types.add(type);
				}
			}
		}
		cachedTypes = types;
		return types;
	}

	private static boolean nametagHit(Entity entity) {
		VoidmarkConfig config = VoidmarkConfig.get();
		String needle = config.mobGlowName == null ? "" : config.mobGlowName.trim();
		if (needle.isEmpty()) {
			return false;
		}
		refreshNameIds(needle);
		return nameIds.contains(entity.getId());
	}

	private static void refreshNameIds(String needle) {
		Minecraft client = Minecraft.getInstance();
		int tick = client.player == null ? 0 : client.player.tickCount;
		if (tick == nameTick && needle.equals(nameNeedle)) {
			return;
		}
		nameTick = tick;
		nameNeedle = needle;
		if (client.level == null || client.player == null) {
			nameIds = Set.of();
			return;
		}
		String n = needle.toLowerCase(Locale.ROOT);
		Set<Integer> ids = new HashSet<>();
		Set<UUID> seen = new HashSet<>();
		for (Entity entity : allEntities(client)) {
			if (entity == null || entity == client.player) {
				continue;
			}
			seen.add(entity.getUUID());
			String live = labelOf(entity);
			if (!live.isEmpty()) {
				remembered.put(entity.getUUID(), live);
			}
			if (!nameMatches(entity, n)) {
				continue;
			}
			if (!hasVanillaGlow(entity)) {
				ids.add(entity.getId());
			}
			linkHologram(client, entity, live, ids);
		}
		remembered.keySet().removeIf(uuid -> !seen.contains(uuid));
		nameIds = ids;
	}

	private static boolean nameMatches(Entity entity, String needle) {
		if (labelContains(entity, needle)) {
			return true;
		}
		for (Entity passenger : entity.getPassengers()) {
			if (labelContains(passenger, needle)) {
				return true;
			}
		}
		Entity vehicle = entity.getVehicle();
		if (vehicle != null && labelContains(vehicle, needle)) {
			return true;
		}
		String cached = remembered.get(entity.getUUID());
		return cached != null && cached.contains(needle);
	}

	private static boolean labelContains(Entity entity, String needle) {
		String live = labelOf(entity);
		return !live.isEmpty() && live.contains(needle);
	}

	/**
	 * Custom name, scoreboard prefix, and text-display lines — including names
	 * Hypixel has already sent but has not made visible yet.
	 */
	private static String labelOf(Entity entity) {
		if (entity == null) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		append(out, entity.getCustomName());
		if (entity.hasCustomName()) {
			append(out, entity.getDisplayName());
		}
		PlayerTeam team = entity.getTeam();
		if (team != null) {
			append(out, team.getPlayerPrefix());
			append(out, team.getPlayerSuffix());
		}
		if (entity instanceof Display.TextDisplay display) {
			Display.TextDisplay.TextRenderState state = display.textRenderState();
			if (state != null) {
				append(out, state.text());
			}
		}
		return out.toString();
	}

	private static void append(StringBuilder out, Component text) {
		if (text == null) {
			return;
		}
		String raw = STRIP.matcher(text.getString()).replaceAll("").toLowerCase(Locale.ROOT).trim();
		if (raw.isEmpty()) {
			return;
		}
		if (!out.isEmpty()) {
			out.append(' ');
		}
		out.append(raw);
	}

	/**
	 * Hypixel often puts the real name on an armor-stand or text-display hologram
	 * above the mob, not on the mob itself. Glow the living entity under it too,
	 * and remember that name on the mob so ESP still hits after the plate hides.
	 */
	private static void linkHologram(Minecraft client, Entity hologram, String live, Set<Integer> ids) {
		if (!isHologram(hologram)) {
			return;
		}
		String label = live == null || live.isEmpty() ? labelOf(hologram) : live;
		AABB box = hologram.getBoundingBox().inflate(HOLOGRAM_RANGE);
		for (Entity other : client.level.getEntities(hologram, box)) {
			if (other instanceof LivingEntity living
				&& living.isAlive()
				&& other != client.player
				&& !isHologram(other)
				&& !hasVanillaGlow(other)) {
				ids.add(other.getId());
				if (!label.isEmpty()) {
					remembered.put(other.getUUID(), label);
				}
			}
		}
	}

	private static boolean isHologram(Entity entity) {
		return entity.getType() == EntityType.ARMOR_STAND
			|| entity.getType() == EntityType.TEXT_DISPLAY
			|| entity instanceof Display.TextDisplay;
	}

	private static Iterable<Entity> allEntities(Minecraft client) {
		if (client.level instanceof ClientLevelAccessor access) {
			return access.voidmark$entities().getAll();
		}
		return client.level.entitiesForRendering();
	}

	private static boolean occluded(Minecraft client, Vec3 from, Vec3 to) {
		HitResult hit = client.level.clip(new ClipContext(
			from,
			to,
			ClipContext.Block.VISUAL,
			ClipContext.Fluid.NONE,
			client.player
		));
		if (hit.getType() == HitResult.Type.MISS) {
			return false;
		}
		return hit.getLocation().distanceToSqr(from) + 0.36 < to.distanceToSqr(from);
	}
}
