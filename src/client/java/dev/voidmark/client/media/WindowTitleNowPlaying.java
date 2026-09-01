package dev.voidmark.client.media;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;

import java.util.Locale;

final class WindowTitleNowPlaying {
	private static final int PROCESS_QUERY_LIMITED_INFORMATION = 0x1000;
	private static final String[] IDLE = {
		"spotify", "spotify premium", "spotify free", "spotify.exe",
		"youtube music", "youtube music desktop app", "youtube",
		"microsoft text input application", "windows input experience"
	};

	NowPlaying snapshot() {
		if (!MediaKeys.windows()) {
			return NowPlaying.none();
		}
		try {
			Holder best = new Holder();
			WinUser.WNDENUMPROC callback = (hwnd, data) -> {
				consider(hwnd, best);
				return true;
			};
			User32.INSTANCE.EnumWindows(callback, Pointer.NULL);
			if (best.track == null) {
				return NowPlaying.none();
			}
			return best.track;
		} catch (Throwable ignored) {
			return NowPlaying.none();
		}
	}

	private static void consider(HWND hwnd, Holder best) {
		if (hwnd == null || !User32.INSTANCE.IsWindowVisible(hwnd)) {
			return;
		}
		int length = User32.INSTANCE.GetWindowTextLength(hwnd);
		if (length < 3) {
			return;
		}
		char[] buf = new char[length + 2];
		int written = User32.INSTANCE.GetWindowText(hwnd, buf, buf.length);
		if (written <= 0) {
			return;
		}
		String title = new String(buf, 0, written).trim();
		if (title.isBlank() || idle(title)) {
			return;
		}
		IntByReference pid = new IntByReference();
		User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid);
		String exe = processPath(pid.getValue());
		int score = score(exe, title);
		if (score <= 0 || score <= best.score) {
			return;
		}
		Parsed parsed = parse(title);
		if (parsed.title.isBlank()) {
			return;
		}
		String app = exe.isBlank() ? title : exe;
		best.score = score;
		best.track = new NowPlaying(
			parsed.title,
			NowPlaying.placeholder(parsed.artist) ? "" : parsed.artist,
			"",
			app,
			"window",
			"",
			true,
			0L,
			0L,
			System.nanoTime()
		);
	}

	private static String processPath(int pid) {
		if (pid <= 0) {
			return "";
		}
		WinNT.HANDLE handle = Kernel32.INSTANCE.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, pid);
		if (handle == null) {
			return "";
		}
		try {
			char[] name = new char[1024];
			IntByReference size = new IntByReference(name.length);
			if (!Kernel32.INSTANCE.QueryFullProcessImageName(handle, 0, name, size)) {
				return "";
			}
			return new String(name, 0, Math.max(0, size.getValue())).trim();
		} catch (Throwable ignored) {
			return "";
		} finally {
			Kernel32.INSTANCE.CloseHandle(handle);
		}
	}

	private static int score(String exe, String title) {
		String path = exe.toLowerCase(Locale.ROOT);
		String text = title.toLowerCase(Locale.ROOT);
		int score = 0;
		if (path.contains("spotify")) {
			score += 120;
		}
		if (path.contains("ytm") || path.contains("youtube-music") || path.contains("youtubemusic") || path.contains("youtube music")) {
			score += 110;
		}
		if (path.contains("cider")) {
			score += 100;
		}
		if (text.contains("youtube music")) {
			score += 80;
		}
		if (text.contains("spotify")) {
			score += 70;
		}
		if (path.contains("electron") && (text.contains("music") || text.contains("youtube") || text.contains(" • ") || text.contains(" - "))) {
			score += 60;
		}
		if (score == 0 && (text.contains(" - ") || text.contains(" • ")) && path.contains("music")) {
			score += 40;
		}
		return score;
	}

	private static boolean idle(String title) {
		String lower = title.toLowerCase(Locale.ROOT);
		for (String value : IDLE) {
			if (lower.equals(value)) {
				return true;
			}
		}
		return lower.startsWith("minecraft") || lower.contains("voidmark");
	}

	private static Parsed parse(String title) {
		String lower = title.toLowerCase(Locale.ROOT);
		boolean youtube = lower.contains("youtube");
		String trimmed = title
			.replaceAll("(?i)\\s+[|\\-]\\s+YouTube Music.*$", "")
			.replaceAll("(?i)\\s+[|\\-]\\s+Spotify.*$", "")
			.trim();
		String[] seps = youtube
			? new String[]{" • ", " · ", " – ", " — ", " | "}
			: new String[]{" • ", " · ", " – ", " — ", " - "};
		for (String sep : seps) {
			int at = trimmed.indexOf(sep);
			if (at > 0 && at + sep.length() < trimmed.length()) {
				String left = trimmed.substring(0, at).trim();
				String right = trimmed.substring(at + sep.length()).trim();
				if (!NowPlaying.placeholder(right) && !left.isBlank()) {
					return new Parsed(left, right);
				}
			}
		}
		return new Parsed(trimmed, "");
	}

	private static final class Holder {
		int score = -1;
		NowPlaying track;
	}

	private record Parsed(String title, String artist) {
	}
}
