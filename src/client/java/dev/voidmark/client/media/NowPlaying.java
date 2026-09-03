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
	long sampledAtNanos,
	long sourcePositionMs
) {
	public static NowPlaying none() {
		return new NowPlaying("", "", "", "", "", "", false, 0L, 0L, 0L, -1L);
	}

	public boolean present() {
		return title != null && !title.isBlank();
	}

	public boolean hasCover() {
		return cover != null && !cover.isBlank();
	}

	public NowPlaying cleaned() {
		String cleanedAlbum = placeholder(album) ? "" : nullToEmpty(album);
		if (companionSource(source) && !placeholder(title)) {
			String cleanedArtist = placeholder(artist) ? Split.of(title).artist : artist.trim();
			String cleanedTitle = placeholder(artist) ? Split.of(title).title : title.trim();
			return new NowPlaying(
				cleanedTitle,
				cleanedArtist,
				cleanedAlbum,
				nullToEmpty(app),
				nullToEmpty(source),
				nullToEmpty(cover),
				playing,
				positionMs,
				durationMs,
				sampledAtNanos,
				sourcePositionMs
			);
		}
		Split split = Split.of(title);
		String cleanedTitle = split.title;
		String cleanedArtist = preferArtist(cleanedAlbum, artist, split.artist);
		if (sameName(cleanedArtist, cleanedAlbum)) {
			cleanedArtist = "";
		}
		return new NowPlaying(
			cleanedTitle,
			cleanedArtist,
			cleanedAlbum,
			nullToEmpty(app),
			nullToEmpty(source),
			nullToEmpty(cover),
			playing,
			positionMs,
			durationMs,
			sampledAtNanos,
			sourcePositionMs
		);
	}

	public NowPlaying overlay(NowPlaying extra) {
		return overlay(extra, null);
	}

	public NowPlaying overlay(NowPlaying extra, NowPlaying previous) {
		NowPlaying base = cleaned();
		if (extra == null || !extra.present()) {
			return base;
		}
		NowPlaying other = extra.cleaned();
		String mergedAlbum = firstNonBlank(base.album, other.album);
		boolean companion = companionSource(other.source);
		boolean playing = companion ? other.playing : base.playing;
		long sourcePos = pickSourcePosition(other.sourcePositionMs, base.sourcePositionMs, companion);
		long pos = sourcePos >= 0L ? sourcePos : Math.max(0L, base.positionMs);
		long dur = other.durationMs > 0L ? other.durationMs : base.durationMs;
		long sampled = other.sampledAtNanos > 0L ? other.sampledAtNanos : base.sampledAtNanos;
		return new NowPlaying(
			base.title,
			preferArtist(mergedAlbum, base.artist, other.artist),
			mergedAlbum,
			firstNonBlank(other.app, base.app),
			companion ? other.source : firstNonBlank(base.source, other.source),
			firstNonBlank(base.cover, other.cover),
			playing,
			pos,
			dur,
			sampled > 0L ? sampled : System.nanoTime(),
			sourcePos
		);
	}

	public NowPlaying withCatalog(String catalogTitle, String catalogArtist, String catalogAlbum, String catalogCover, long catalogDurationMs) {
		NowPlaying base = cleaned();
		boolean missingArtist = placeholder(base.artist) || (!placeholder(base.album) && sameName(base.artist, base.album));
		String newArtist = missingArtist
			? firstPerson(catalogArtist, base.artist)
			: base.artist;
		return new NowPlaying(
			base.title,
			newArtist,
			firstNonBlank(base.album, catalogAlbum),
			base.app,
			base.source,
			firstNonBlank(base.cover, catalogCover),
			base.playing,
			base.positionMs,
			base.durationMs > 0L ? base.durationMs : Math.max(0L, catalogDurationMs),
			base.sampledAtNanos,
			base.sourcePositionMs
		);
	}

	private static final long SEEK_MS = 800L;

	static boolean companionSource(String source) {
		if (source == null || source.isBlank()) {
			return false;
		}
		String lower = source.toLowerCase(Locale.ROOT);
		return lower.equals("ytm")
			|| lower.equals("ytmd")
			|| lower.equals("cider")
			|| lower.equals("companion")
			|| lower.equals("playerctl");
	}

	private static long pickSourcePosition(long other, long base, boolean companion) {
		if (companion && other >= 0L) {
			return other;
		}
		if (other > 0L) {
			return other;
		}
		if (base > 0L) {
			return base;
		}
		if (other >= 0L) {
			return other;
		}
		return base;
	}

	/**
	 * True only when the player itself moved the clock: a real timestamp that
	 * jumped away from interpolation, including scrubbing backward. A stuck 0
	 * from YouTube Music SMTC is not a seek.
	 */
	private static boolean isRealSeek(long source, long previousSource, long expected, String kind, String previousKind) {
		if (source < 0L) {
			return false;
		}
		if (Math.abs(source - expected) <= SEEK_MS) {
			return false;
		}
		if (source == 0L && previousSource <= 0L) {
			return false;
		}
		if (source == 0L && previousSource > SEEK_MS) {
			if (companionSource(previousKind) && !companionSource(kind)) {
				return false;
			}
			return true;
		}
		return Math.abs(source - previousSource) > SEEK_MS;
	}

	public NowPlaying carryTime(NowPlaying previous) {
		if (companionSource(source) && (previous == null || !companionSource(previous.source))) {
			long start = sourcePositionMs >= 0L ? sourcePositionMs : Math.max(0L, positionMs);
			return withTimeline(start, durationMs, System.nanoTime(), sourcePositionMs);
		}
		if (sourcePositionMs < 0L && !companionSource(source)) {
			return withTimeline(0L, 0L, System.nanoTime(), -1L);
		}
		if (previous == null || !previous.present() || !titlesClose(title, previous.title)) {
			long start = sourcePositionMs > 0L ? sourcePositionMs : Math.max(0L, positionMs);
			return withTimeline(start, durationMs, System.nanoTime(), sourcePositionMs);
		}
		long dur = durationMs > 0L ? durationMs : previous.durationMs;
		long expected = previous.displayPositionMs();
		long source = sourcePositionMs;
		if (isRealSeek(source, previous.sourcePositionMs, expected, this.source, previous.source)) {
			return withTimeline(source, dur, System.nanoTime(), source);
		}
		if (!playing) {
			return withTimeline(expected, dur, System.nanoTime(), source);
		}
		long origin = previous.sampledAtNanos > 0L ? previous.positionMs : expected;
		long sampled = previous.sampledAtNanos > 0L ? previous.sampledAtNanos : System.nanoTime();
		if (!previous.playing) {
			origin = expected;
			sampled = System.nanoTime();
		}
		return withTimeline(origin, dur, sampled, source);
	}

	public NowPlaying withTimeline(long positionMs, long durationMs, long sampledAtNanos) {
		return withTimeline(positionMs, durationMs, sampledAtNanos, sourcePositionMs);
	}

	public NowPlaying withTimeline(long positionMs, long durationMs, long sampledAtNanos, long sourcePositionMs) {
		return new NowPlaying(
			title,
			artist,
			album,
			app,
			source,
			cover,
			playing,
			Math.max(0L, positionMs),
			Math.max(0L, durationMs),
			sampledAtNanos > 0L ? sampledAtNanos : System.nanoTime(),
			sourcePositionMs
		);
	}

	static NowPlaying sampled(
		String title,
		String artist,
		String album,
		String app,
		String source,
		String cover,
		boolean playing,
		long positionMs,
		long durationMs
	) {
		long raw = positionMs;
		long pos = positionMs < 0L ? 0L : positionMs;
		return new NowPlaying(
			title,
			artist,
			album,
			app,
			source,
			cover,
			playing,
			pos,
			Math.max(0L, durationMs),
			System.nanoTime(),
			raw
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
		if (placeholder(artist)) {
			return "";
		}
		return artist.trim();
	}

	public String sourceLabel() {
		String raw = ((app == null ? "" : app) + " " + (source == null ? "" : source)).toLowerCase(Locale.ROOT);
		if (raw.contains("spotify")) {
			return "SPOTIFY";
		}
		if (raw.contains("cider")
			|| raw.contains("youtubemusic")
			|| raw.contains("youtube music")
			|| raw.contains("youtube.music")
			|| raw.contains("ytm")
			|| raw.contains("ytmd")
			|| "browser".equals(source)
			|| "ytm".equals(source)
			|| raw.contains("electron")) {
			return "YOUTUBE MUSIC";
		}
		if (raw.contains("youtube")) {
			return "YOUTUBE";
		}
		if (source != null && !source.isBlank() && !"windows".equals(source)) {
			return source.toUpperCase(Locale.ROOT);
		}
		return "MEDIA";
	}

	static NowPlaying fromSmtc(
		String title,
		String artist,
		String album,
		String albumArtist,
		String subtitle,
		String app,
		String kind,
		String cover,
		boolean playing,
		long positionMs,
		long durationMs
	) {
		title = nullToEmpty(title).trim();
		artist = blankIfPlaceholder(artist);
		album = blankIfPlaceholder(album);
		albumArtist = blankIfPlaceholder(albumArtist);
		subtitle = blankIfPlaceholder(subtitle);
		String subArtist = "";
		String subAlbum = "";
		String[] seps = {" • ", " · ", " – ", " — ", " | "};
		for (String sep : seps) {
			int at = subtitle.indexOf(sep);
			if (at > 0 && at + sep.length() < subtitle.length()) {
				subArtist = subtitle.substring(0, at).trim();
				subAlbum = subtitle.substring(at + sep.length()).trim();
				break;
			}
		}
		if (subArtist.isEmpty() && !subtitle.isEmpty() && !sameName(subtitle, title) && !sameName(subtitle, album)) {
			subArtist = subtitle;
		}
		app = nullToEmpty(app);
		if (album.isEmpty()) {
			album = blankIfPlaceholder(subAlbum);
		}
		artist = preferArtist(album, artist, albumArtist, subArtist);
		if (sameName(artist, album)) {
			artist = preferArtist(album, albumArtist, subArtist);
		}
		String source = kind == null || kind.isBlank() ? "windows" : kind;
		return sampled(
			title,
			artist,
			album,
			nullToEmpty(app),
			source,
			nullToEmpty(cover),
			playing,
			positionMs,
			durationMs
		).cleaned();
	}

	private static boolean browserApp(String app) {
		String raw = app == null ? "" : app.toLowerCase(Locale.ROOT);
		return raw.contains("chrome")
			|| raw.contains("edge")
			|| raw.contains("brave")
			|| raw.contains("firefox")
			|| raw.contains("opera")
			|| raw.contains("vivaldi")
			|| raw.contains("youtube")
			|| raw.contains("ytm")
			|| raw.contains("cider")
			|| raw.contains("electron");
	}

	private static String blankIfPlaceholder(String value) {
		return placeholder(value) ? "" : value.trim();
	}

	public boolean youtubeMusic() {
		return "YOUTUBE MUSIC".equals(sourceLabel()) || browserApp(app);
	}

	public long displayPositionMs() {
		if (!present()) {
			return 0L;
		}
		long extra = 0L;
		if (playing && sampledAtNanos > 0L) {
			extra = Math.max(0L, (System.nanoTime() - sampledAtNanos) / 1_000_000L);
		}
		long pos = (positionMs < 0L ? 0L : positionMs) + extra;
		if (durationMs > 0L) {
			pos = Math.min(pos, durationMs);
		}
		return Math.max(0L, pos);
	}

	public String clockLine() {
		String elapsed = clock(displayPositionMs());
		if (durationMs > 0L) {
			return elapsed + "/" + clock(durationMs);
		}
		return elapsed + "/--:--";
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

	static boolean sameName(String left, String right) {
		String a = normalizeTitle(left);
		String b = normalizeTitle(right);
		return !a.isEmpty() && a.equals(b);
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

	static String preferArtist(String album, String... people) {
		for (String value : people) {
			if (placeholder(value)) {
				continue;
			}
			if (!placeholder(album) && sameName(value, album)) {
				continue;
			}
			return value.trim();
		}
		return "";
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
