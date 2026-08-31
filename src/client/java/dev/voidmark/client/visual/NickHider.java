package dev.voidmark.client.visual;

import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

public final class NickHider {
	private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
	private static String cachedRaw = "";
	private static Component cachedFormatted = Component.empty();
	private static String cachedPlain = "";

	private NickHider() {
	}

	public static boolean active() {
		if (DEPTH.get() > 0) {
			return false;
		}
		VoidmarkConfig config = VoidmarkConfig.get();
		if (!config.nickEnabled) {
			return false;
		}
		return !realName().isEmpty();
	}

	public static void suppress() {
		DEPTH.set(DEPTH.get() + 1);
	}

	public static void resume() {
		DEPTH.set(Math.max(0, DEPTH.get() - 1));
	}

	public static String realName() {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			String name = client.player.getGameProfile().name();
			return name == null ? "" : name;
		}
		String name = client.getGameProfile().name();
		return name == null ? "" : name;
	}

	public static String plainNick() {
		refreshCache();
		String nick = cachedPlain;
		return nick.isEmpty() ? realName() : nick;
	}

	public static Component formattedNick() {
		refreshCache();
		return cachedFormatted;
	}

	public static String rewrite(String text) {
		if (!active() || text == null || text.isEmpty()) {
			return text;
		}
		String name = realName();
		if (name.isEmpty() || !containsName(text, name)) {
			return text;
		}
		return replaceName(text, name, plainNick());
	}

	public static Component rewrite(Component component) {
		if (!active() || component == null) {
			return component;
		}
		String name = realName();
		if (name.isEmpty() || !containsName(component.getString(), name)) {
			return component;
		}
		DEPTH.set(DEPTH.get() + 1);
		try {
			return rewriteTree(component, name);
		} finally {
			resume();
		}
	}

	public static Component parseLegacy(String raw) {
		if (raw == null || raw.isEmpty()) {
			return Component.empty();
		}
		MutableComponent root = Component.empty();
		Style style = Style.EMPTY;
		StringBuilder buffer = new StringBuilder();
		for (int i = 0; i < raw.length(); i++) {
			char current = raw.charAt(i);
			if (current == '&' && i + 1 < raw.length()) {
				char code = Character.toLowerCase(raw.charAt(i + 1));
				ChatFormatting formatting = ChatFormatting.getByCode(code);
				if (formatting != null) {
					flush(root, buffer, style);
					style = formatting == ChatFormatting.RESET ? Style.EMPTY : style.applyFormat(formatting);
					i++;
					continue;
				}
				if (raw.charAt(i + 1) == '&') {
					buffer.append('&');
					i++;
					continue;
				}
			}
			buffer.append(current);
		}
		flush(root, buffer, style);
		return root;
	}

	private static void refreshCache() {
		String raw = VoidmarkConfig.get().nick;
		if (raw == null) {
			raw = "";
		}
		if (raw.equals(cachedRaw)) {
			return;
		}
		cachedRaw = raw;
		cachedFormatted = parseLegacy(raw);
		cachedPlain = ChatFormatting.stripFormatting(cachedFormatted.getString());
	}

	private static Component rewriteTree(Component component, String name) {
		MutableComponent out;
		var contents = component.getContents();
		if (contents instanceof PlainTextContents plain) {
			out = Component.empty().setStyle(component.getStyle());
			appendReplaced(out, plain.text(), component.getStyle(), name);
		} else if (contents instanceof TranslatableContents trans) {
			Object[] args = trans.getArgs();
			Object[] next = new Object[args.length];
			for (int i = 0; i < args.length; i++) {
				Object arg = args[i];
				if (arg instanceof Component nested) {
					next[i] = rewriteTree(nested, name);
				} else if (arg instanceof String text) {
					next[i] = replaceName(text, name, plainNick());
				} else {
					next[i] = arg;
				}
			}
			String fallback = trans.getFallback();
			if (fallback == null) {
				out = Component.translatable(trans.getKey(), next).setStyle(component.getStyle());
			} else {
				out = Component.translatableWithFallback(trans.getKey(), fallback, next).setStyle(component.getStyle());
			}
		} else {
			out = component.plainCopy().setStyle(component.getStyle());
		}
		for (Component sibling : component.getSiblings()) {
			out.append(rewriteTree(sibling, name));
		}
		return out;
	}

	private static void appendReplaced(MutableComponent out, String text, Style style, String name) {
		int cursor = 0;
		while (cursor <= text.length()) {
			int at = indexOfName(text, name, cursor);
			if (at < 0) {
				if (cursor < text.length()) {
					out.append(Component.literal(text.substring(cursor)).setStyle(style));
				}
				return;
			}
			if (at > cursor) {
				out.append(Component.literal(text.substring(cursor, at)).setStyle(style));
			}
			out.append(formattedNick().copy());
			cursor = at + name.length();
		}
	}

	private static String replaceName(String text, String name, String nick) {
		StringBuilder out = new StringBuilder(text.length());
		int cursor = 0;
		while (true) {
			int at = indexOfName(text, name, cursor);
			if (at < 0) {
				out.append(text, cursor, text.length());
				return out.toString();
			}
			out.append(text, cursor, at);
			out.append(nick);
			cursor = at + name.length();
		}
	}

	private static boolean containsName(String text, String name) {
		return indexOfName(text, name, 0) >= 0;
	}

	private static int indexOfName(String text, String name, int from) {
		int at = from;
		while (true) {
			at = text.indexOf(name, at);
			if (at < 0) {
				return -1;
			}
			boolean left = at == 0 || !nameChar(text.charAt(at - 1));
			boolean right = at + name.length() >= text.length() || !nameChar(text.charAt(at + name.length()));
			if (left && right) {
				return at;
			}
			at += name.length();
		}
	}

	private static boolean nameChar(char value) {
		return Character.isLetterOrDigit(value) || value == '_';
	}

	private static void flush(MutableComponent root, StringBuilder buffer, Style style) {
		if (buffer.isEmpty()) {
			return;
		}
		root.append(Component.literal(buffer.toString()).setStyle(style));
		buffer.setLength(0);
	}
}
