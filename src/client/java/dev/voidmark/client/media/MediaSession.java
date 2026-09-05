package dev.voidmark.client.media;

import net.minecraft.client.Minecraft;

public final class MediaSession {
	private static final WindowTitleNowPlaying TITLES = new WindowTitleNowPlaying();
	private static final CompanionNowPlaying COMPANION = new CompanionNowPlaying();
	private static final LinuxNowPlaying LINUX = new LinuxNowPlaying();

	private static volatile NowPlaying current = NowPlaying.none();
	private static volatile String route = "";
	private static volatile String hint = "Play a track in Spotify or YouTube Music";
	private static volatile String lastAnnounced = "";
	private static volatile String pendingAnnounce = "";

	private MediaSession() {
	}

	public static void init() {
		Thread thread = new Thread(MediaSession::loop, "voidmark-media");
		thread.setDaemon(true);
		thread.start();
	}

	public static NowPlaying current() {
		NowPlaying value = current;
		return value == null || !value.present() ? NowPlaying.none() : value;
	}

	public static String hint() {
		return hint;
	}

	public static boolean playPause() {
		return control("toggle");
	}

	public static boolean next() {
		return control("next");
	}

	public static boolean previous() {
		return control("prev");
	}

	private static boolean control(String action) {
		String active = route;
		if ("spotify".equals(active)) {
			if (SpotifyNowPlaying.control(action)) {
				return true;
			}
		}
		if ("ytm".equals(active) || "ytmd".equals(active) || "cider".equals(active)) {
			if (COMPANION.control(action)) {
				return true;
			}
		}
		if ("playerctl".equals(active) && LinuxNowPlaying.control(switch (action) {
			case "next" -> "next";
			case "prev" -> "previous";
			default -> "play-pause";
		})) {
			return true;
		}
		return switch (action) {
			case "next" -> MediaKeys.next();
			case "prev" -> MediaKeys.previous();
			default -> MediaKeys.playPause();
		};
	}

	private static void loop() {
		while (true) {
			try {
				NowPlaying next = pick();
				announceIfChanged(next);
				current = next;
				Thread.sleep(current.present() ? 400 : 700);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				return;
			} catch (Exception exception) {
				current = NowPlaying.none();
				lastAnnounced = "";
			}
		}
	}

	private static NowPlaying pick() {
		NowPlaying spotify = SpotifyNowPlaying.connected() ? SpotifyNowPlaying.snapshot() : NowPlaying.none();
		if (spotify.present() && spotify.playing()) {
			NowPlaying out = spotify.cleaned().carryTime(current);
			route = "spotify";
			hint = "SPOTIFY";
			return out;
		}
		NowPlaying companion = COMPANION.snapshot();
		if (companion.present()) {
			NowPlaying out = companion.cleaned().carryTime(current);
			route = companion.source();
			hint = out.sourceLabel();
			return out;
		}
		if (spotify.present()) {
			NowPlaying out = spotify.cleaned().carryTime(current);
			route = "spotify";
			hint = "SPOTIFY";
			return out;
		}
		if (COMPANION.reachable() || COMPANION.awaitingAuth()) {
			route = "ytm";
			hint = COMPANION.statusHint();
			return NowPlaying.none();
		}
		NowPlaying titled = TITLES.snapshot();
		if (titled.present()) {
			NowPlaying out = titled.cleaned();
			if (!out.youtubeMusic()) {
				out = TrackLookup.enrich(out);
			}
			out = out.carryTime(current);
			route = "window";
			hint = out.youtubeMusic() ? "YouTube Music API not connected" : out.sourceLabel();
			return out;
		}
		NowPlaying linux = LINUX.snapshot();
		if (linux.present()) {
			NowPlaying out = TrackLookup.enrich(linux.cleaned()).carryTime(current);
			route = "playerctl";
			hint = out.sourceLabel();
			return out;
		}
		route = "";
		hint = "Play a track in Spotify or YouTube Music";
		return NowPlaying.none();
	}

	private static void announceIfChanged(NowPlaying next) {
		if (next == null || !next.present()) {
			lastAnnounced = "";
			pendingAnnounce = "";
			return;
		}
		if (NowPlaying.titlesClose(lastAnnounced, next.title()) || NowPlaying.titlesClose(pendingAnnounce, next.title())) {
			return;
		}
		pendingAnnounce = next.title();
		NowPlaying snap = next;
		Minecraft.getInstance().execute(() -> {
			if (MediaChat.songChanged(snap)) {
				lastAnnounced = snap.title();
			}
			pendingAnnounce = "";
		});
	}
}
