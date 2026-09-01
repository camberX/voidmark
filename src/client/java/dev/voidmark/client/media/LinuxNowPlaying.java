package dev.voidmark.client.media;

final class LinuxNowPlaying {
	NowPlaying snapshot() {
		if (MediaKeys.windows()) {
			return NowPlaying.none();
		}
		String listed = MediaProcesses.run(new String[]{"playerctl", "-l"}, 250);
		if (listed == null || listed.isBlank()) {
			return NowPlaying.none();
		}
		String player = pick(listed);
		String[] cmd = player == null
			? new String[]{"playerctl", "metadata", "--format", "{{status}}|{{playerName}}|{{xesam:title}}|{{xesam:artist}}|{{mpris:length}}|{{position}}|{{mpris:artUrl}}"}
			: new String[]{"playerctl", "-p", player, "metadata", "--format", "{{status}}|{{playerName}}|{{xesam:title}}|{{xesam:artist}}|{{mpris:length}}|{{position}}|{{mpris:artUrl}}"};
		String meta = MediaProcesses.run(cmd, 280);
		if (meta == null || meta.isBlank()) {
			return NowPlaying.none();
		}
		String[] parts = meta.strip().split("\\|", -1);
		if (parts.length < 5) {
			return NowPlaying.none();
		}
		String title = parts[2].trim();
		if (title.isBlank() || "(null)".equals(title)) {
			return NowPlaying.none();
		}
		String artist = parts[3].trim();
		if ("(null)".equals(artist)) {
			artist = "";
		}
		long durationMs = microsToMs(parts[4]);
		long positionMs = parts.length > 5 ? microsToMs(parts[5]) : 0L;
		if (positionMs == 0L) {
			double seconds = parseDouble(MediaProcesses.run(
				player == null ? new String[]{"playerctl", "position"} : new String[]{"playerctl", "-p", player, "position"},
				200
			));
			positionMs = Math.round(seconds * 1000.0);
		}
		boolean playing = parts[0].trim().equalsIgnoreCase("Playing");
		String cover = parts.length > 6 ? parts[6].trim() : "";
		if ("(null)".equals(cover)) {
			cover = "";
		}
		return new NowPlaying(
			title,
			artist,
			"",
			parts[1].trim(),
			"playerctl",
			cover,
			playing,
			positionMs,
			durationMs,
			System.nanoTime()
		);
	}

	static boolean control(String verb) {
		if (MediaKeys.windows()) {
			return false;
		}
		return MediaProcesses.run(new String[]{"playerctl", verb}, 400) != null;
	}

	private static String pick(String listed) {
		String best = null;
		int bestScore = -1;
		for (String raw : listed.split("\\R")) {
			String name = raw.trim();
			if (name.isEmpty()) {
				continue;
			}
			int score = score(name);
			if (score > bestScore) {
				bestScore = score;
				best = name;
			}
		}
		return best;
	}

	private static int score(String name) {
		String lower = name.toLowerCase();
		if (lower.contains("spotify")) {
			return 100;
		}
		if (lower.contains("youtube") || lower.contains("cider") || lower.contains("ytmd")) {
			return 90;
		}
		if (lower.contains("chromium") || lower.contains("chrome") || lower.contains("firefox") || lower.contains("msedge")) {
			return 40;
		}
		return 1;
	}

	private static long microsToMs(String raw) {
		long value = parseLong(raw);
		if (value >= 10_000_000L) {
			return value / 1000L;
		}
		return value;
	}

	private static long parseLong(String raw) {
		if (raw == null) {
			return 0L;
		}
		String digits = raw.trim();
		int cut = 0;
		while (cut < digits.length() && Character.isDigit(digits.charAt(cut))) {
			cut++;
		}
		if (cut == 0) {
			return 0L;
		}
		try {
			return Long.parseLong(digits.substring(0, cut));
		} catch (Exception exception) {
			return 0L;
		}
	}

	private static double parseDouble(String raw) {
		if (raw == null) {
			return 0d;
		}
		try {
			return Double.parseDouble(raw.trim().split("\\s+")[0]);
		} catch (Exception exception) {
			return 0d;
		}
	}
}
