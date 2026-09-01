package dev.voidmark.client.media;

import net.minecraft.util.Mth;

public record NowPlaying(
	String title,
	String artist,
	String album,
	String app,
	String source,
	String cover,
	boolean playing,
	long positionMs,
	long durationMs,
	long sampledAtNanos
) {
	public static NowPlaying none() {
		return new NowPlaying("", "", "", "", "", "", false, 0L, 0L, 0L);
	}

	public boolean present() {
		return title != null && !title.isBlank();
	}

	public boolean hasCover() {
		return cover != null && !cover.isBlank();
	}

	public String artistLine() {
		if (artist != null && !artist.isBlank()) {
			return artist;
		}
		if (album != null && !album.isBlank()) {
			return album;
		}
		return sourceLabel();
	}

	public String sourceLabel() {
		String raw = ((app == null ? "" : app) + " " + (source == null ? "" : source)).toLowerCase();
		if (raw.contains("spotify")) {
			return "SPOTIFY";
		}
		if (raw.contains("cider") || raw.contains("youtubemusic") || raw.contains("youtube music") || raw.contains("youtube.music")) {
			return "YOUTUBE MUSIC";
		}
		if (raw.contains("youtube")) {
			return "YOUTUBE";
		}
		if (raw.contains("window") || raw.contains("ytm") || raw.contains("electron")) {
			if (raw.contains("spotify")) {
				return "SPOTIFY";
			}
			return "YOUTUBE MUSIC";
		}
		if (source != null && !source.isBlank()) {
			return source.toUpperCase();
		}
		return "MEDIA";
	}

	public long displayPositionMs() {
		if (!present()) {
			return 0L;
		}
		long extra = 0L;
		if (playing && sampledAtNanos > 0L) {
			extra = Math.max(0L, (System.nanoTime() - sampledAtNanos) / 1_000_000L);
		}
		long pos = positionMs + extra;
		if (durationMs > 0L) {
			pos = Math.min(pos, durationMs);
		}
		return Math.max(0L, pos);
	}

	public float progress() {
		if (durationMs <= 0L) {
			return playing ? 0.08f : 0f;
		}
		return Mth.clamp(displayPositionMs() / (float) durationMs, 0f, 1f);
	}

	public static String clock(long millis) {
		long total = Math.max(0L, millis) / 1000L;
		long m = total / 60L;
		long s = total % 60L;
		return m + ":" + (s < 10 ? "0" : "") + s;
	}
}
