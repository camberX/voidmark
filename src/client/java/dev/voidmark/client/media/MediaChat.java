package dev.voidmark.client.media;

import net.minecraft.ChatFormatting;
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

	public static int nowPlaying() {
		NowPlaying track = MediaSession.current();
		if (!track.present()) {
			tell(Component.literal("Nothing playing. Start Spotify or YouTube Music, then open chat and click the HUD.")
				.withStyle(ChatFormatting.GRAY));
			return 1;
		}
		MutableComponent line = Component.literal("").withStyle(ChatFormatting.WHITE)
			.append(Component.literal("VOIDMARK  ").withStyle(ChatFormatting.AQUA))
			.append(Component.literal(track.title()).withStyle(ChatFormatting.WHITE));
		if (!track.artistLine().isBlank()) {
			line.append(Component.literal("  ·  " + track.artistLine()).withStyle(ChatFormatting.GRAY));
		}
		line.append(Component.literal("  " + track.sourceLabel()).withStyle(ChatFormatting.DARK_AQUA));
		tell(line);
		tell(controls(track));
		return 1;
	}

	public static int toggle() {
		NowPlaying before = MediaSession.current();
		boolean ok = MediaSession.playPause();
		if (!ok) {
			tell(Component.literal("Could not reach the media player.").withStyle(ChatFormatting.RED));
			return 0;
		}
		boolean playing = before.present() && !before.playing();
		tell(Component.literal(playing ? "Playing" : "Paused")
			.withStyle(playing ? ChatFormatting.GREEN : ChatFormatting.GRAY)
			.append(before.present() ? Component.literal("  " + before.title()).withStyle(ChatFormatting.WHITE) : CommonComponents.EMPTY));
		return 1;
	}

	public static int skip(boolean next) {
		boolean ok = next ? MediaSession.next() : MediaSession.previous();
		if (!ok) {
			tell(Component.literal("Could not skip.").withStyle(ChatFormatting.RED));
			return 0;
		}
		tell(Component.literal(next ? "Next track" : "Previous track").withStyle(ChatFormatting.AQUA));
		return 1;
	}

	public static Component controls(NowPlaying track) {
		boolean playing = track.playing();
		return Component.literal("")
			.append(button("«", "/vm music prev", "Previous"))
			.append(Component.literal("  "))
			.append(button(playing ? "Pause" : "Play", "/vm music play", playing ? "Pause" : "Play"))
			.append(Component.literal("  "))
			.append(button("»", "/vm music next", "Next"))
			.append(Component.literal("   Open chat and click the HUD, or click these.")
				.withStyle(ChatFormatting.DARK_GRAY));
	}

	private static MutableComponent button(String label, String command, String hover) {
		return Component.literal("[" + label + "]").withStyle(
			Style.EMPTY
				.withColor(ChatFormatting.AQUA)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover)))
		);
	}

	private static void tell(Component message) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui != null) {
			client.gui.getChat().addClientSystemMessage(message);
		}
	}
}
