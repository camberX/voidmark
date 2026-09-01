package dev.voidmark.client.media;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public final class MediaSession {
	private static final WindowsNowPlaying WINDOWS = new WindowsNowPlaying();
	private static final LinuxNowPlaying LINUX = new LinuxNowPlaying();
	private static final YtmdNowPlaying YTMD = new YtmdNowPlaying();

	private static volatile NowPlaying current = NowPlaying.none();
	private static volatile String route = "";
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
		return value == null ? NowPlaying.none() : value;
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
		if ("ytmd".equals(active)) {
			String command = switch (action) {
				case "next" -> "track-next";
				case "prev" -> "track-previous";
				default -> current().playing() ? "track-pause" : "track-play";
			};
			if (YtmdNowPlaying.command(command) || ("toggle".equals(action) && YtmdNowPlaying.command("track-playPause"))) {
				return true;
			}
		}
		if ("playerctl".equals(active)) {
			String verb = switch (action) {
				case "next" -> "next";
				case "prev" -> "previous";
				default -> "play-pause";
			};
			if (LinuxNowPlaying.control(verb)) {
				return true;
			}
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
				if (MediaKeys.windows() && !WINDOWS.alive() && System.nanoTime() - lastRestartNs > 4_000_000_000L) {
					lastRestartNs = System.nanoTime();
					WINDOWS.start();
				}
				current = pick();
				Thread.sleep(current.present() ? 420 : 900);
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
		if (windows.present() && preferred(windows)) {
			route = "windows";
			return windows;
		}
		NowPlaying ytmd = YTMD.snapshot();
		if (ytmd.present()) {
			route = "ytmd";
			return ytmd;
		}
		if (windows.present()) {
			route = "windows";
			return windows;
		}
		NowPlaying linux = LINUX.snapshot();
		if (linux.present()) {
			route = "playerctl";
			return linux;
		}
		route = "";
		return NowPlaying.none();
	}

	private static boolean preferred(NowPlaying track) {
		String label = track.sourceLabel();
		return label.contains("SPOTIFY") || label.contains("YOUTUBE");
	}
}
