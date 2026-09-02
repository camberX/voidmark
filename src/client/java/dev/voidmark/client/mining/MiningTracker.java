package dev.voidmark.client.mining;

import dev.voidmark.client.config.VoidmarkConfig;
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

public final class MiningTracker {
	private static final Pattern INLINE = Pattern.compile(
		"^(.+?)(?:\\s*[:\\-–]\\s*|\\s+)(\\d+(?:\\.\\d+)?\\s*%|\\d+\\s*/\\s*\\d+|done|complete|✔)$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern PERCENT = Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*%$");
	private static final Pattern RATIO = Pattern.compile("^(\\d+)\\s*/\\s*(\\d+)$");
	private static final Pattern WIDGET = Pattern.compile(
		"^(players?\\b|info|area\\b|server\\b|gems\\b|profile\\b|sb level|bank\\b|skills\\b|stats\\b|event\\b|pet\\b|powders?\\b|crystals?\\b|pickaxe ability|unclaimed|forges?\\b|timers\\b|party\\b|slayer\\b|active effects|bestiary|essence|collection|fire sales|election|north stars|guests\\b|coop\\b|island\\b|minions?\\b|account info|dungeon stats|player stats|puzzles\\b|opened rooms).*",
		Pattern.CASE_INSENSITIVE
	);
	private static final Comparator<PlayerInfo> TAB_ORDER = Comparator
		.comparingInt((PlayerInfo info) -> -info.getTabListOrder())
		.thenComparingInt(info -> info.getGameMode() == GameType.SPECTATOR ? 1 : 0)
		.thenComparing(info -> {
			PlayerTeam team = info.getTeam();
			return team == null ? "" : team.getName();
		})
		.thenComparing(info -> info.getProfile().name(), String.CASE_INSENSITIVE_ORDER);
	private static final long ALERT_MS = 3500L;
	private static final long BOOST_MS = 120_000L;
	private static final long PICKOBULUS_MS = 60_000L;

	private static final Object LOCK = new Object();
	private static List<Commission> commissions = List.of();
	private static String ability = "Pickaxe";
	private static boolean abilityReady = true;
	private static long cooldownUntil;
	private static long cooldownTotal = BOOST_MS;
	private static String alertName = "";
	private static long alertUntil;

	private MiningTracker() {
	}

	public static void tick(Minecraft client) {
		if (client.player == null || client.level == null) {
			synchronized (LOCK) {
				commissions = List.of();
			}
			return;
		}
		List<String> lines = tabLines(client);
		List<Commission> parsed = parseCommissions(lines);
		synchronized (LOCK) {
			commissions = parsed;
			if (!abilityReady && System.currentTimeMillis() >= cooldownUntil) {
				abilityReady = true;
			}
		}
	}

	public static void onChat(Component message) {
		String text = plain(message);
		if (text.isEmpty()) {
			return;
		}
		String name = abilityIn(text);
		if (name == null) {
			return;
		}
		String key = text.toLowerCase(Locale.ROOT);
		if (key.contains("is now available")) {
			markReady(name);
			return;
		}
		if (key.contains("you used") && key.contains("pickaxe ability")) {
			startCooldown(name);
		}
	}

	public static void reset() {
		synchronized (LOCK) {
			commissions = List.of();
			ability = "Pickaxe";
			abilityReady = true;
			cooldownUntil = 0L;
			alertName = "";
			alertUntil = 0L;
		}
	}

	public static Snapshot snapshot() {
		synchronized (LOCK) {
			long now = System.currentTimeMillis();
			boolean ready = abilityReady || now >= cooldownUntil;
			float remain = 0f;
			float progress = 1f;
			String label = "Ready";
			if (!ready) {
				remain = Math.max(0f, (cooldownUntil - now) / 1000f);
				progress = cooldownTotal <= 0L ? 0f : 1f - Math.min(1f, (cooldownUntil - now) / (float) cooldownTotal);
				label = formatTime(remain);
			}
			boolean alert = VoidmarkConfig.get().miningAbilityAlert && now < alertUntil && !alertName.isEmpty();
			float alertT = alert ? Math.min(1f, (alertUntil - now) / (float) ALERT_MS) : 0f;
			boolean mining = inMiningIsland() || !commissions.isEmpty() || !ready || alert;
			return new Snapshot(
				mining,
				SkyblockLocation.area,
				List.copyOf(commissions),
				ability,
				label,
				ready,
				progress,
				alert ? alertName : "",
				alertT
			);
		}
	}

	public static boolean inMiningIsland() {
		String area = SkyblockLocation.area.toLowerCase(Locale.ROOT);
		if (area.isEmpty()) {
			return false;
		}
		return area.contains("dwarven")
			|| area.contains("crystal hollow")
			|| area.contains("glacite")
			|| area.contains("mines of divan")
			|| area.contains("magma field")
			|| area.contains("precursor")
			|| area.contains("goblin holdout")
			|| area.contains("mithril deposit")
			|| area.contains("khazad")
			|| area.contains("the forge")
			|| area.contains("forge")
			|| area.contains("base camp")
			|| area.contains("mineshaft")
			|| area.contains("quarry")
			|| area.contains("rampart")
			|| area.contains("upper mines")
			|| area.contains("royal mines")
			|| area.contains("cliffside veins")
			|| area.contains("lava springs")
			|| area.contains("divan")
			|| area.contains("deep cavern");
	}

	private static void startCooldown(String name) {
		long duration = durationOf(name);
		synchronized (LOCK) {
			ability = name;
			abilityReady = false;
			cooldownTotal = duration;
			cooldownUntil = System.currentTimeMillis() + duration;
		}
	}

	private static void markReady(String name) {
		synchronized (LOCK) {
			ability = name;
			abilityReady = true;
			cooldownUntil = 0L;
			if (VoidmarkConfig.get().miningAbilityAlert) {
				alertName = name;
				alertUntil = System.currentTimeMillis() + ALERT_MS;
			}
		}
	}

	private static String abilityIn(String text) {
		String key = text.toLowerCase(Locale.ROOT);
		if (key.contains("pickobulus")) {
			return "Pickobulus";
		}
		if (key.contains("mining speed boost")) {
			return "Mining Speed Boost";
		}
		if (key.contains("maniac miner")) {
			return "Maniac Miner";
		}
		if (key.contains("gemstone infusion")) {
			return "Gemstone Infusion";
		}
		return null;
	}

	private static long durationOf(String name) {
		String key = name.toLowerCase(Locale.ROOT);
		if (key.contains("pickobulus") || key.contains("maniac")) {
			return PICKOBULUS_MS;
		}
		return BOOST_MS;
	}

	public static boolean hasTitaniumCommission() {
		return titaniumFilter().active();
	}

	public static MiningAreas.TitaniumFilter titaniumFilter() {
		List<Commission> copy;
		synchronized (LOCK) {
			copy = commissions;
		}
		return MiningAreas.filter(copy);
	}

	private static List<Commission> parseCommissions(List<String> lines) {
		List<Commission> out = new ArrayList<>();
		boolean section = false;
		String pending = null;
		for (String raw : lines) {
			String line = cleanName(raw);
			if (line.isEmpty()) {
				continue;
			}
			if (isCommissionsHeader(line)) {
				section = true;
				pending = null;
				continue;
			}
			if (!section) {
				continue;
			}
			if (isNewWidget(line)) {
				break;
			}
			Progress only = progressOnly(line);
			if (only != null && pending != null) {
				out.add(new Commission(pending, only.label, only.fraction, only.done));
				pending = null;
				continue;
			}
			Commission inline = inline(line);
			if (inline != null) {
				out.add(inline);
				pending = null;
				continue;
			}
			pending = line;
		}
		return List.copyOf(out);
	}

	private static List<String> tabLines(Minecraft client) {
		if (client.player == null) {
			return List.of();
		}
		ClientPacketListener connection = client.player.connection;
		if (connection == null) {
			return List.of();
		}
		List<PlayerInfo> infos = new ArrayList<>(connection.getListedOnlinePlayers());
		infos.sort(TAB_ORDER);
		int limit = Math.min(80, infos.size());
		List<String> lines = new ArrayList<>(limit);
		for (int i = 0; i < limit; i++) {
			lines.add(plain(tabName(infos.get(i))));
		}
		return lines;
	}

	private static Component tabName(PlayerInfo info) {
		Component display = info.getTabListDisplayName();
		if (display != null) {
			return display;
		}
		return PlayerTeam.formatNameForTeam(info.getTeam(), Component.literal(info.getProfile().name()));
	}

	private static boolean isCommissionsHeader(String line) {
		String key = line.toLowerCase(Locale.ROOT);
		return key.equals("commissions") || key.equals("commission");
	}

	private static boolean isNewWidget(String line) {
		return WIDGET.matcher(line).matches();
	}

	private static Commission inline(String line) {
		Matcher matcher = INLINE.matcher(line);
		if (!matcher.matches()) {
			return null;
		}
		Progress progress = progressOnly(matcher.group(2).trim());
		if (progress == null) {
			return null;
		}
		String name = cleanName(matcher.group(1));
		if (name.isEmpty()) {
			return null;
		}
		return new Commission(name, progress.label, progress.fraction, progress.done);
	}

	private static Progress progressOnly(String line) {
		String text = line.trim();
		if (text.equalsIgnoreCase("done") || text.equalsIgnoreCase("complete") || text.equals("✔")) {
			return new Progress("Done", 1f, true);
		}
		Matcher percent = PERCENT.matcher(text);
		if (percent.matches()) {
			float value = Float.parseFloat(percent.group(1));
			float fraction = Math.max(0f, Math.min(1f, value / 100f));
			return new Progress(Math.round(value) + "%", fraction, fraction >= 0.999f);
		}
		Matcher ratio = RATIO.matcher(text);
		if (ratio.matches()) {
			int have = Integer.parseInt(ratio.group(1));
			int need = Math.max(1, Integer.parseInt(ratio.group(2)));
			float fraction = Math.max(0f, Math.min(1f, have / (float) need));
			return new Progress(have + "/" + need, fraction, have >= need);
		}
		return null;
	}

	private static String cleanName(String value) {
		if (value == null) {
			return "";
		}
		String text = value.replace('\u00A0', ' ').replaceAll("§.", "").trim();
		text = text.replaceAll("^[\\s•·▪▸►\\-–—★☆✔✅⏣]+", "");
		text = text.replaceAll("[:\\-–—]+$", "");
		return text.replaceAll("\\s+", " ").trim();
	}

	private static String plain(Component component) {
		return component == null ? "" : component.getString().replaceAll("§.", "").replace('\u00A0', ' ').trim();
	}

	private static String formatTime(float seconds) {
		int total = Math.max(0, Math.round(seconds));
		int min = total / 60;
		int sec = total % 60;
		if (min > 0) {
			return min + ":" + String.format(Locale.ROOT, "%02d", sec);
		}
		return sec + "s";
	}

	public record Commission(String name, String progress, float fraction, boolean done) {
	}

	public record Snapshot(
		boolean present,
		String area,
		List<Commission> commissions,
		String ability,
		String abilityLabel,
		boolean abilityReady,
		float abilityProgress,
		String alertName,
		float alertT
	) {
	}

	private record Progress(String label, float fraction, boolean done) {
	}
}
