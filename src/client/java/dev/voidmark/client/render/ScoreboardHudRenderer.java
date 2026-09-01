package dev.voidmark.client.render;

import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.ui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ScoreboardHudRenderer {
	private static final int MAX_ROWS = 15;
	private static final float PAD = 7;
	private static final float HEAD = 22;
	private static final float ROW = 10;

	private ScoreboardHudRenderer() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		Objective objective = sidebar(client.level.getScoreboard(), client.player);
		if (objective == null) {
			return;
		}
		Font font = client.font;
		Scoreboard scoreboard = objective.getScoreboard();
		List<Line> lines = lines(scoreboard, objective, font);
		if (lines.isEmpty()) {
			return;
		}

		Component title = objective.getDisplayName();
		float titleW = font.width(title);
		float maxLine = titleW;
		for (Line line : lines) {
			maxLine = Math.max(maxLine, line.width);
		}
		float panelW = Math.min(180, Math.max(88, maxLine + PAD * 2 + 10));
		float panelH = PAD + HEAD + lines.size() * ROW + PAD - 2;
		float x = graphics.guiWidth() - panelW - HudLayout.MARGIN;
		float y = HudLayout.MARGIN;
		if (VoidmarkConfig.get().hudEffects) {
			y += EffectsHudRenderer.stackHeight() + 6;
		}

		GuiDraw.panel(graphics, x, y, panelW, panelH, 6, Theme.WINDOW, Theme.LINE, Theme.ACCENT);
		GuiDraw.small(graphics, font, "SCOREBOARD", x + PAD + 4, y + PAD - 1, Theme.ACCENT);
		GuiDraw.text(graphics, font, title, x + PAD + 4, y + PAD + 8, 0xFFFFFFFF, false);

		float ly = y + PAD + HEAD + 2;
		for (Line line : lines) {
			GuiDraw.text(graphics, font, line.name, x + PAD + 4, ly, 0xFFFFFFFF, false);
			if (line.score != null && font.width(line.score) > 0 && !line.score.getString().isBlank()) {
				float sx = x + panelW - PAD - font.width(line.score);
				GuiDraw.text(graphics, font, line.score, sx, ly, 0xFFFFFFFF, false);
			}
			ly += ROW;
		}
	}

	private static Objective sidebar(Scoreboard scoreboard, LocalPlayer player) {
		Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());
		if (team != null && team.getColor().isColor()) {
			DisplaySlot colored = DisplaySlot.teamColorToSlot(team.getColor());
			if (colored != null) {
				Objective teamObjective = scoreboard.getDisplayObjective(colored);
				if (teamObjective != null) {
					return teamObjective;
				}
			}
		}
		return objective;
	}

	private static List<Line> lines(Scoreboard scoreboard, Objective objective, Font font) {
		List<PlayerScoreEntry> entries = new ArrayList<>();
		for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
			if (!entry.isHidden()) {
				entries.add(entry);
			}
		}
		entries.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed());
		if (entries.size() > MAX_ROWS) {
			entries = entries.subList(0, MAX_ROWS);
		}
		List<Line> out = new ArrayList<>(entries.size());
		for (PlayerScoreEntry entry : entries) {
			Component raw = entry.display() != null ? entry.display() : Component.literal(entry.owner());
			Component name = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), raw);
			Component score = entry.formatValue(objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT));
			float width = font.width(name) + 8 + font.width(score);
			out.add(new Line(name, score, width));
		}
		return out;
	}

	private record Line(Component name, Component score, float width) {
	}
}
