package dev.voidmark.client.combat;

import dev.voidmark.client.location.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skyblock Bonus Attack Speed from the tab Stats widget.
 * Melee wait is {@code round(10 / (1 + AS/100))} ticks. Shortbows use ceil.
 * Off Skyblock the wait stays 1 tick.
 */
public final class AttackSpeed {
	private static final Pattern LINE = Pattern.compile(
		"(?i)(?:bonus\\s+)?(?:attack\\s+speed|atk\\s*spd)\\s*[:\\s]+[^\\d+\\-]{0,8}([+\\-]?\\d+(?:\\.\\d+)?)"
	);
	private static final Comparator<PlayerInfo> TAB_ORDER = Comparator
		.comparingInt((PlayerInfo info) -> -info.getTabListOrder())
		.thenComparingInt(info -> info.getGameMode() == GameType.SPECTATOR ? 1 : 0)
		.thenComparing(info -> {
			PlayerTeam team = info.getTeam();
			return team == null ? "" : team.getName();
		})
		.thenComparing(info -> info.getProfile().name(), String.CASE_INSENSITIVE_ORDER);
	private static final int REFRESH_TICKS = 10;
	private static final float DEFAULT_AS = 0f;

	private static float bonus;
	private static boolean known;
	private static int lastTick = Integer.MIN_VALUE;

	private AttackSpeed() {
	}

	public static void tick(Minecraft client) {
		if (client.player == null || client.level == null || !SkyblockLocation.inSkyblock) {
			return;
		}
		int t = client.player.tickCount;
		if (lastTick != Integer.MIN_VALUE && t - lastTick < REFRESH_TICKS && t >= lastTick) {
			return;
		}
		lastTick = t;
		Float parsed = readTab(client);
		if (parsed != null) {
			bonus = clamp(parsed);
			known = true;
		}
	}

	public static void reset() {
		bonus = DEFAULT_AS;
		known = false;
		lastTick = Integer.MIN_VALUE;
	}

	public static int meleeDelay() {
		if (!SkyblockLocation.inSkyblock) {
			return 1;
		}
		return meleeTicks(known ? bonus : DEFAULT_AS);
	}

	public static int arrowDelay() {
		if (!SkyblockLocation.inSkyblock) {
			return 1;
		}
		return shortbowTicks(known ? bonus : DEFAULT_AS);
	}

	static int meleeTicks(float attackSpeed) {
		return Math.max(1, Math.round(10f / (1f + clamp(attackSpeed) / 100f)));
	}

	static int shortbowTicks(float attackSpeed) {
		return Math.max(1, (int) Math.ceil(10.0 / (1.0 + clamp(attackSpeed) / 100.0)));
	}

	private static float clamp(float attackSpeed) {
		return Math.max(-99f, Math.min(150f, attackSpeed));
	}

	private static Float readTab(Minecraft client) {
		if (client.player == null) {
			return null;
		}
		ClientPacketListener connection = client.player.connection;
		if (connection == null) {
			return null;
		}
		List<PlayerInfo> infos = new ArrayList<>(connection.getListedOnlinePlayers());
		infos.sort(TAB_ORDER);
		int limit = Math.min(80, infos.size());
		for (int i = 0; i < limit; i++) {
			Float value = parse(plain(tabName(infos.get(i))));
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	private static Float parse(String line) {
		if (line.isEmpty() || !line.toLowerCase(Locale.ROOT).contains("attack")
			&& !line.toLowerCase(Locale.ROOT).contains("atk")) {
			return null;
		}
		Matcher matcher = LINE.matcher(line);
		if (!matcher.find()) {
			return null;
		}
		try {
			return Float.parseFloat(matcher.group(1));
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static Component tabName(PlayerInfo info) {
		Component display = info.getTabListDisplayName();
		if (display != null) {
			return display;
		}
		return PlayerTeam.formatNameForTeam(info.getTeam(), Component.literal(info.getProfile().name()));
	}

	private static String plain(Component component) {
		return component == null ? "" : component.getString().replaceAll("§.", "").trim();
	}
}
