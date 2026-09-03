package dev.voidmark.client.location;

import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Hypixel / Skyblock / island flags. Tab and scoreboard are walked at most a
 * few times a second, and each walk stops as soon as it has what it needs.
 */
public final class SkyblockLocation {
	private static final String AREA_PREFIX = "Area: ";
	private static final String DUNGEON_PREFIX = "Dungeon: ";
	private static final int REFRESH_TICKS = 5;

	public static boolean onHypixel;
	public static boolean inSkyblock;
	public static boolean inTheEnd;
	public static String area = "";
	public static String serverBrand = "";

	private static int lastTick = Integer.MIN_VALUE;

	private SkyblockLocation() {
	}

	public static void tick(Minecraft client) {
		if (client.player == null || client.level == null) {
			reset();
			return;
		}

		int t = client.player.tickCount;
		if (lastTick != Integer.MIN_VALUE && t - lastTick < REFRESH_TICKS && t >= lastTick) {
			return;
		}
		lastTick = t;

		ClientPacketListener connection = client.player.connection;
		serverBrand = brand(connection);
		onHypixel = serverBrand.toLowerCase().contains("hypixel");

		Sidebar sidebar = readSidebar(client);
		area = readArea(connection, sidebar);
		inSkyblock = onHypixel && (sidebar.skyblock || !area.isEmpty());
		inTheEnd = inSkyblock && isTheEnd(area, sidebar);
	}

	public static boolean shouldMarkNodes() {
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.markersEnabled) {
			return false;
		}
		if (config.forceEnable) {
			return true;
		}
		if (!inSkyblock) {
			return false;
		}
		return !config.onlyInTheEnd || inTheEnd;
	}

	public static void reset() {
		onHypixel = false;
		inSkyblock = false;
		inTheEnd = false;
		area = "";
		serverBrand = "";
		lastTick = Integer.MIN_VALUE;
	}

	private static String brand(ClientPacketListener connection) {
		if (connection == null) {
			return "";
		}
		String brand = connection.serverBrand();
		return brand == null ? "" : brand;
	}

	private static String readArea(ClientPacketListener connection, Sidebar sidebar) {
		if (connection != null) {
			for (PlayerInfo info : connection.getListedOnlinePlayers()) {
				Component display = info.getTabListDisplayName();
				if (display == null) {
					continue;
				}
				String text = plain(display);
				if (text.startsWith(AREA_PREFIX)) {
					return text.substring(AREA_PREFIX.length()).trim();
				}
				if (text.startsWith(DUNGEON_PREFIX)) {
					return text.substring(DUNGEON_PREFIX.length()).trim();
				}
			}
		}
		if (!sidebar.endArea.isEmpty()) {
			return sidebar.endArea;
		}
		return area;
	}

	private static boolean isTheEnd(String currentArea, Sidebar sidebar) {
		if (currentArea.equalsIgnoreCase("The End") || currentArea.toLowerCase().contains("end island")) {
			return true;
		}
		return sidebar.end;
	}

	private static Sidebar readSidebar(Minecraft client) {
		if (client.level == null) {
			return Sidebar.EMPTY;
		}
		Scoreboard scoreboard = client.level.getScoreboard();
		Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (sidebar == null) {
			return Sidebar.EMPTY;
		}
		boolean skyblock = "SBScoreboard".equals(sidebar.getName());
		boolean end = false;
		String endArea = "";
		String title = plain(sidebar.getDisplayName());
		String titleKey = title.toUpperCase();
		if (titleKey.contains("SKYBLOCK")) {
			skyblock = true;
		}
		if (title.contains("The End")) {
			end = true;
			endArea = "The End";
		}
		for (var score : scoreboard.listPlayerScores(sidebar)) {
			Component line = PlayerTeam.formatNameForTeam(
				scoreboard.getPlayersTeam(score.owner()),
				Component.literal(score.owner())
			);
			String text = plain(line);
			if (!skyblock && text.toUpperCase().contains("SKYBLOCK")) {
				skyblock = true;
			}
			if (text.contains("The End") || text.contains("End Island")) {
				end = true;
				if (endArea.isEmpty()) {
					endArea = "The End";
				}
			}
		}
		return new Sidebar(skyblock, end, endArea);
	}

	private static String plain(Component component) {
		return component == null ? "" : component.getString().replaceAll("§.", "");
	}

	private record Sidebar(boolean skyblock, boolean end, String endArea) {
		private static final Sidebar EMPTY = new Sidebar(false, false, "");
	}
}
