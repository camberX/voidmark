package dev.voidmark.client.media;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public final class MediaSession {
	private static final WindowsNowPlaying WINDOWS = new WindowsNowPlaying();
	private static final WindowTitleNowPlaying TITLES = new WindowTitleNowPlaying();
	private static final CompanionNowPlaying COMPANION = new CompanionNowPlaying();
	private static final LinuxNowPlaying LINUX = new LinuxNowPlaying();

	private static volatile NowPlaying current = NowPlaying.none();
	private static volatile String route = "";
	private static volatile String hint = "Play a track in Spotify or YouTube Music";
	private static long lastRestartNs;

	private MediaSession() {
	}

	public static void init() {
		WINDOWS.start();
		lastRestartNs = System.nanoTime();
		Thread thread = new Thread(MediaSession::loop, "voidmark-media");
		thread.setDaemon(true);
		thread.start();
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> WINDOWS.stop());
	}

	public static NowPlaying current() {
		NowPlaying value = current;
		if (value == null || !value.present()) {
			return NowPlaying.none();
		}
		return TrackLookup.enrich(value);
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
				if (MediaKeys.windows() && !WINDOWS.alive() && System.nanoTime() - lastRestartNs > 3_000_000_000L) {
					lastRestartNs = System.nanoTime();
					WINDOWS.start();
				}
				current = pick();
				Thread.sleep(current.present() ? 400 : 700);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				return;
			} catch (Exception exception) {
				current = NowPlaying.none();
			}
		}
	}

	private static NowPlaying pick() {
		NowPlaying windows = WINDOWS.snapshot();
		NowPlaying companion = COMPANION.snapshot();
		if (windows.present()) {
			NowPlaying out = windows.cleaned();
			if (companion.present() && NowPlaying.related(out, companion)) {
				out = out.overlay(companion);
			}
			out = TrackLookup.enrich(out);
			route = "windows";
			hint = out.sourceLabel();
			return out;
		}
		if (companion.present()) {
			NowPlaying out = TrackLookup.enrich(companion.cleaned());
			route = companion.source();
			hint = out.sourceLabel();
			return out;
		}
		NowPlaying titled = TITLES.snapshot();
		if (titled.present()) {
			NowPlaying out = TrackLookup.enrich(titled.cleaned());
			route = "window";
			hint = out.sourceLabel();
			return out;
		}
		NowPlaying linux = LINUX.snapshot();
		if (linux.present()) {
			NowPlaying out = TrackLookup.enrich(linux.cleaned());
			route = "playerctl";
			hint = out.sourceLabel();
			return out;
		}
		route = "";
		String err = WINDOWS.error();
		if (err != null && !err.isBlank() && !"no-session".equals(err) && !"empty-title".equals(err)) {
			hint = "No media session";
		} else {
			hint = "Play a track in Spotify or YouTube Music";
		}
		return NowPlaying.none();
	}
}
