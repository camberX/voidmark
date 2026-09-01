package dev.voidmark.client.media;

import dev.voidmark.client.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Locale;

public final class MediaChat {
	private MediaChat() {
	}

	public static boolean handleTyped(String raw) {
		String message = raw == null ? "" : raw.trim();
		if (message.isEmpty()) {
			return false;
		}
		String lower = message.toLowerCase(Locale.ROOT);
		return switch (lower) {
			case ".np", ".nowplaying", ".song", ".vm np" -> {
				nowPlaying();
				yield true;
			}
			case ".play", ".pause", ".pp", ".vm play", ".vm pause" -> {
				toggle();
				yield true;
			}
			case ".skip", ".next", ".vm skip", ".vm next" -> {
				skip(true);
				yield true;
			}
			case ".prev", ".previous", ".back", ".vm prev" -> {
				skip(false);
				yield true;
			}
			default -> false;
		};
	}

	public static boolean songChanged(NowPlaying track) {
		if (track == null || !track.present()) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gui == null) {
			return false;
		}
		tell(nowPlayingLine(track));
		return true;
	}

	public static int nowPlaying() {
		NowPlaying track = MediaSession.current();
		if (!track.present()) {
			tell(muted("Nothing playing. Start Spotify or YouTube Music, then open chat and click the HUD."));
			return 1;
		}
		tell(nowPlayingLine(track));
		tell(controls(track));
		return 1;
	}

	public static int toggle() {
		NowPlaying before = MediaSession.current();
		boolean ok = MediaSession.playPause();
		if (!ok) {
			tell(Component.literal("Could not reach the media player.").withStyle(style(Theme.DANGER)));
			return 0;
		}
		boolean playing = before.present() && !before.playing();
		MutableComponent line = brand()
			.append(sep())
			.append(Component.literal(playing ? "PLAYING" : "PAUSED").withStyle(style(playing ? Theme.ACCENT : Theme.MUTED).withBold(true)));
		if (before.present()) {
			line.append(Component.literal("  " + before.title()).withStyle(style(Theme.TEXT)));
		}
		tell(line);
		return 1;
	}

	public static int skip(boolean next) {
		boolean ok = next ? MediaSession.next() : MediaSession.previous();
		if (!ok) {
			tell(Component.literal("Could not skip.").withStyle(style(Theme.DANGER)));
			return 0;
		}
		tell(brand()
			.append(sep())
			.append(Component.literal(next ? "NEXT TRACK" : "PREVIOUS TRACK").withStyle(style(Theme.ACCENT).withBold(true))));
		return 1;
	}

	public static Component controls(NowPlaying track) {
		boolean playing = track.playing();
		return Component.empty()
			.append(button("  «  ", "/vm music prev", "Previous"))
			.append(Component.literal(" "))
			.append(button(playing ? "  Pause  " : "  Play  ", "/vm music play", playing ? "Pause" : "Play"))
			.append(Component.literal(" "))
			.append(button("  »  ", "/vm music next", "Next"))
			.append(Component.literal("   HUD clicks work in chat").withStyle(style(Theme.MUTED)));
	}

	private static MutableComponent nowPlayingLine(NowPlaying track) {
		MutableComponent line = brand()
			.append(sep())
			.append(Component.literal("NOW PLAYING").withStyle(style(Theme.ACCENT).withBold(true)))
			.append(Component.literal("  " + track.title()).withStyle(style(Theme.TEXT)));
		if (!track.artistLine().isBlank()) {
			line.append(Component.literal("  ·  " + track.artistLine()).withStyle(style(Theme.MUTED)));
		}
		line.append(Component.literal("  " + track.clockLine()).withStyle(style(Theme.HEADER)));
		line.append(Component.literal("  " + track.sourceLabel()).withStyle(style(Theme.ACCENT)));
		String hover = track.title();
		if (!track.artistLine().isBlank()) {
			hover += "\n" + track.artistLine();
		}
		if (!track.album().isBlank()) {
			hover += "\n" + track.album();
		}
		hover += "\n" + track.clockLine() + "  " + track.sourceLabel();
		return line.withStyle(Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
	}

	private static MutableComponent brand() {
		return Component.literal("VOIDMARK").withStyle(style(Theme.ACCENT).withBold(true));
	}

	private static MutableComponent sep() {
		return Component.literal("  │  ").withStyle(style(Theme.LINE));
	}

	private static MutableComponent muted(String value) {
		return Component.literal(value).withStyle(style(Theme.MUTED));
	}

	private static MutableComponent button(String label, String command, String hover) {
		return Component.literal(label).withStyle(
			style(Theme.ACCENT)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover)))
		);
	}

	private static Style style(int color) {
		return Style.EMPTY.withColor(color & 0xFFFFFF);
	}

	private static void tell(Component message) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui != null) {
			client.gui.getChat().addClientSystemMessage(message);
		}
	}
}
