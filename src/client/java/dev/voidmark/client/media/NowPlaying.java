package dev.voidmark.client.media;

import net.minecraft.util.Mth;

import java.util.Locale;

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

	public NowPlaying cleaned() {
		Split split = Split.of(title);
		String cleanedTitle = split.title;
		String cleanedArtist = firstPerson(artist, split.artist);
		return new NowPlaying(
			cleanedTitle,
			cleanedArtist,
			placeholder(album) ? "" : nullToEmpty(album),
			nullToEmpty(app),
			nullToEmpty(source),
			nullToEmpty(cover),
			playing,
			positionMs,
			durationMs,
			sampledAtNanos
		);
	}

	public NowPlaying overlay(NowPlaying extra) {
		NowPlaying base = cleaned();
		if (extra == null || !extra.present()) {
			return base;
		}
		NowPlaying other = extra.cleaned();
		return new NowPlaying(
			base.title,
			firstPerson(base.artist, other.artist),
			firstNonBlank(base.album, other.album),
			base.app,
			base.source,
			firstNonBlank(other.cover, base.cover),
			base.playing,
			base.positionMs > 0L ? base.positionMs : other.positionMs,
			base.durationMs > 0L ? base.durationMs : other.durationMs,
			base.sampledAtNanos
		);
	}

	public NowPlaying withCatalog(String catalogTitle, String catalogArtist, String catalogAlbum, String catalogCover) {
		NowPlaying base = cleaned();
		if (placeholder(catalogTitle) || placeholder(catalogArtist)) {
			return base;
		}
		if (!placeholder(base.artist) && !titlesClose(base.artist, catalogArtist) && !titlesClose(base.title, catalogArtist)) {
			return base;
		}
		String newTitle = base.title;
		String newArtist = firstPerson(base.artist, catalogArtist);
		boolean swapped = titlesClose(base.title, catalogArtist);
		boolean missing = placeholder(base.artist);
		if ((swapped || missing) && !placeholder(catalogTitle)) {
			newTitle = catalogTitle;
			newArtist = catalogArtist;
		}
		return new NowPlaying(
			newTitle,
			newArtist,
			firstNonBlank(base.album, catalogAlbum),
			base.app,
			base.source,
			firstNonBlank(catalogCover, base.cover),
			base.playing,
			base.positionMs,
			base.durationMs,
			base.sampledAtNanos
		);
	}

	static boolean related(NowPlaying left, NowPlaying right) {
		if (left == null || right == null || !left.present() || !right.present()) {
			return false;
		}
		return titlesClose(left.title(), right.title())
			|| titlesClose(left.title(), right.artist())
			|| titlesClose(left.artist(), right.title())
			|| (!placeholder(left.artist()) && titlesClose(left.artist(), right.artist()));
	}

	public String artistLine() {
		if (!placeholder(artist) && artist != null && !artist.isBlank()) {
			return artist;
		}
		if (!placeholder(album) && album != null && !album.isBlank()) {
			return album;
		}
		return "";
	}

	public String sourceLabel() {
		String raw = ((app == null ? "" : app) + " " + (source == null ? "" : source)).toLowerCase(Locale.ROOT);
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
			return "YOUTUBE MUSIC";
		}
		if (source != null && !source.isBlank()) {
			return source.toUpperCase(Locale.ROOT);
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

	static boolean titlesClose(String left, String right) {
		String a = normalizeTitle(left);
		String b = normalizeTitle(right);
		if (a.isEmpty() || b.isEmpty()) {
			return false;
		}
		return a.equals(b) || a.contains(b) || b.contains(a);
	}

	static boolean placeholder(String value) {
		if (value == null || value.isBlank()) {
			return true;
		}
		String lower = value.trim().toLowerCase(Locale.ROOT);
		return lower.equals("youtube music")
			|| lower.equals("youtubemusic")
			|| lower.equals("youtube")
			|| lower.equals("spotify")
			|| lower.equals("spotify premium")
			|| lower.equals("spotify free")
			|| lower.equals("chrome")
			|| lower.equals("microsoft edge")
			|| lower.equals("edge")
			|| lower.equals("brave")
			|| lower.equals("media")
			|| lower.equals("unknown")
			|| lower.equals("various artists");
	}

	private static String firstPerson(String... values) {
		for (String value : values) {
			if (!placeholder(value)) {
				return value.trim();
			}
		}
		return "";
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return "";
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private static String normalizeTitle(String value) {
		if (value == null) {
			return "";
		}
		return value.toLowerCase(Locale.ROOT)
			.replaceAll("\\s+[\\-|]\\s+youtube music.*$", "")
			.replaceAll("\\s+[\\-|]\\s+spotify.*$", "")
			.replace(" • ", " ")
			.replaceAll("[^a-z0-9]+", " ")
			.trim();
	}

	private record Split(String title, String artist) {
		private static Split of(String raw) {
			String title = raw == null ? "" : raw.trim();
			if (title.isEmpty()) {
				return new Split("", "");
			}
			String[] seps = {" • ", " · ", " – ", " — ", " | ", " by "};
			for (String sep : seps) {
				int at = title.indexOf(sep);
				if (at > 0 && at + sep.length() < title.length()) {
					String left = title.substring(0, at).trim();
					String right = title.substring(at + sep.length()).trim();
					if (!placeholder(right) && !left.isEmpty()) {
						return new Split(left, right);
					}
				}
			}
			return new Split(title, "");
		}
	}
}
