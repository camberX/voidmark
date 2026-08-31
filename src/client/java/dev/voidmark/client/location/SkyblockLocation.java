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

public final class SkyblockLocation {
	private static final String AREA_PREFIX = "Area: ";
	private static final String DUNGEON_PREFIX = "Dungeon: ";

	public static boolean onHypixel;
	public static boolean inSkyblock;
	public static boolean inTheEnd;
	public static String area = "";
	public static String serverBrand = "";

	private SkyblockLocation() {
	}

	public static void tick(Minecraft client) {
		if (client.player == null || client.level == null) {
			reset();
			return;
		}

		ClientPacketListener connection = client.player.connection;
		serverBrand = brand(connection);
		onHypixel = serverBrand.toLowerCase().contains("hypixel");
		inSkyblock = onHypixel && looksLikeSkyblock(client, connection);
		area = readArea(connection, client);
		inTheEnd = inSkyblock && isTheEnd(area, client);
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
	}

	private static String brand(ClientPacketListener connection) {
		if (connection == null) {
			return "";
		}
		String brand = connection.serverBrand();
		return brand == null ? "" : brand;
	}

	private static boolean looksLikeSkyblock(Minecraft client, ClientPacketListener connection) {
		Scoreboard scoreboard = client.level.getScoreboard();
		Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (sidebar != null) {
			if ("SBScoreboard".equals(sidebar.getName())) {
				return true;
			}
			String title = plain(sidebar.getDisplayName()).toUpperCase();
			if (title.contains("SKYBLOCK")) {
				return true;
			}
			for (var score : scoreboard.listPlayerScores(sidebar)) {
				Component line = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(score.owner()), Component.literal(score.owner()));
				if (plain(line).toUpperCase().contains("SKYBLOCK")) {
					return true;
				}
			}
		}
		return !readArea(connection, client).isEmpty();
	}

	private static String readArea(ClientPacketListener connection, Minecraft client) {
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

		if (client.level != null) {
			Scoreboard scoreboard = client.level.getScoreboard();
			Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
			if (sidebar != null) {
				for (var score : scoreboard.listPlayerScores(sidebar)) {
					Component line = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(score.owner()), Component.literal(score.owner()));
					String text = plain(line);
					if (text.contains("The End") || text.contains("End Island")) {
						return "The End";
					}
				}
			}
		}

		return area;
	}

	private static boolean isTheEnd(String currentArea, Minecraft client) {
		if (currentArea.equalsIgnoreCase("The End") || currentArea.toLowerCase().contains("end island")) {
			return true;
		}
		if (client.level == null) {
			return false;
		}
		Scoreboard scoreboard = client.level.getScoreboard();
		Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (sidebar == null) {
			return false;
		}
		String title = plain(sidebar.getDisplayName());
		if (title.contains("The End")) {
			return true;
		}
		for (var score : scoreboard.listPlayerScores(sidebar)) {
			Component line = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(score.owner()), Component.literal(score.owner()));
			if (plain(line).contains("The End")) {
				return true;
			}
		}
		return false;
	}

	private static String plain(Component component) {
		return component == null ? "" : component.getString().replaceAll("§.", "");
	}
}
