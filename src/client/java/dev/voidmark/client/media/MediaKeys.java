package dev.voidmark.client.media;

import com.sun.jna.Native;
import com.sun.jna.win32.StdCallLibrary;
import dev.voidmark.Voidmark;

public final class MediaKeys {
	private static final int KEYEVENTF_KEYUP = 0x0002;
	private static final byte VK_NEXT = (byte) 0xB0;
	private static final byte VK_PREV = (byte) 0xB1;
	private static final byte VK_PLAY = (byte) 0xB3;
	private static User32Keys keys;

	private MediaKeys() {
	}

	public static boolean windows() {
		String os = System.getProperty("os.name", "");
		return os.toLowerCase().contains("win");
	}

	public static boolean playPause() {
		return tap(VK_PLAY) || runPlayerctl("play-pause");
	}

	public static boolean next() {
		return tap(VK_NEXT) || runPlayerctl("next");
	}

	public static boolean previous() {
		return tap(VK_PREV) || runPlayerctl("previous");
	}

	private static boolean tap(byte vk) {
		if (!windows()) {
			return false;
		}
		User32Keys api = api();
		if (api == null) {
			return false;
		}
		try {
			api.keybd_event(vk, (byte) 0, 0, 0);
			api.keybd_event(vk, (byte) 0, KEYEVENTF_KEYUP, 0);
			return true;
		} catch (Throwable throwable) {
			Voidmark.LOGGER.debug("Media key tap failed", throwable);
			return false;
		}
	}

	private static User32Keys api() {
		if (keys != null) {
			return keys;
		}
		if (!windows()) {
			return null;
		}
		try {
			keys = Native.load("user32", User32Keys.class);
			return keys;
		} catch (Throwable throwable) {
			Voidmark.LOGGER.debug("Could not load user32 for media keys", throwable);
			return null;
		}
	}

	private static boolean runPlayerctl(String verb) {
		if (windows()) {
			return false;
		}
		return MediaProcesses.run(new String[]{"playerctl", verb}, 400) != null;
	}

	public interface User32Keys extends StdCallLibrary {
		void keybd_event(byte bVk, byte bScan, int dwFlags, int dwExtraInfo);
	}
}
