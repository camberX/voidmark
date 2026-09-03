package dev.voidmark.client.render;

import dev.voidmark.client.ui.MenuFont;
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
	private static final float MIN_W = 88;
	private static final float MAX_W = 180;
	private static final float EMPTY_H = PAD + HEAD + ROW + PAD;

	private ScoreboardHudRenderer() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		Font font = client.font;
		Layout layout = layout(font);
		if (layout.lines.isEmpty() && !HudLayout.editorOpen()) {
			return;
		}
		HudLayout.apply(graphics, font, HudLayout.Id.SCOREBOARD, () -> draw(graphics, font, layout, 0, 0));
	}

	public static float drawWidth(Font font) {
		return layout(font).w;
	}

	public static float drawHeight(Font font) {
		return layout(font).h;
	}

	private static void draw(GuiGraphicsExtractor graphics, Font font, Layout layout, float x, float y) {
		HudChrome.panel(graphics, x, y, layout.w, layout.h, 6, Theme.WINDOW, Theme.LINE);
		GuiDraw.small(graphics, font, "SCOREBOARD", x + PAD + 4, y + PAD - 1, Theme.ACCENT);
		GuiDraw.hud(graphics, font, layout.title, x + PAD + 4, y + PAD + 8, 0xFFFFFFFF);
		float ly = y + PAD + HEAD + 2;
		for (Line line : layout.lines) {
			GuiDraw.hud(graphics, font, line.name, x + PAD + 4, ly, 0xFFFFFFFF);
			if (line.score != null && font.width(line.score) > 0 && !line.score.getString().isBlank()) {
				float sx = x + layout.w - PAD - GuiDraw.hudWidth(font, line.score);
				GuiDraw.hud(graphics, font, line.score, sx, ly, 0xFFFFFFFF);
			}
			ly += ROW;
		}
	}

	private static Layout layout(Font font) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return new Layout(Component.literal("Skyblock"), List.of(), MIN_W, EMPTY_H);
		}
		Objective objective = sidebar(client.level.getScoreboard(), client.player);
		if (objective == null) {
			return new Layout(Component.literal("Skyblock"), List.of(), MIN_W, EMPTY_H);
		}
		List<Line> lines = lines(objective.getScoreboard(), objective, font);
		Component title = MenuFont.applyBody(objective.getDisplayName());
		float maxLine = font.width(title);
		for (Line line : lines) {
			maxLine = Math.max(maxLine, line.width);
		}
		int rows = Math.max(1, lines.size());
		float w = Math.min(MAX_W, Math.max(MIN_W, maxLine + PAD * 2 + 10));
		float h = PAD + HEAD + rows * ROW + PAD - 2;
		return new Layout(title, lines, w, h);
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
			Component name = MenuFont.applyBody(PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), raw));
			Component score = MenuFont.applyBody(entry.formatValue(objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT)));
			float width = font.width(name) + 8 + font.width(score);
			out.add(new Line(name, score, width));
		}
		return out;
	}

	private record Line(Component name, Component score, float width) {
	}

	private record Layout(Component title, List<Line> lines, float w, float h) {
	}
}
