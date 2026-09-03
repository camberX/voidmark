package dev.voidmark.client.ui;

import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public final class MenuFont {
	public static final Identifier BODY_ID = Voidmark.id("nl");
	public static final Identifier SMALL_ID = Voidmark.id("nl_small");
	public static final Identifier TITLE_ID = Voidmark.id("nl_title");
	public static final Identifier ICON_ID = Voidmark.id("nl_icon");
	public static final Identifier UI_ID = Voidmark.id("ui");
	public static final Identifier UI_SMALL_ID = Voidmark.id("ui_small");
	public static final Identifier UI_TITLE_ID = Voidmark.id("ui_title");

	public static final Style BODY = Style.EMPTY.withFont(new FontDescription.Resource(BODY_ID));
	public static final Style SMALL = Style.EMPTY.withFont(new FontDescription.Resource(SMALL_ID));
	public static final Style TITLE = Style.EMPTY.withFont(new FontDescription.Resource(TITLE_ID));
	public static final Style ICON = Style.EMPTY.withFont(new FontDescription.Resource(ICON_ID));
	public static final Style VANILLA = Style.EMPTY.withFont(FontDescription.DEFAULT);
	public static final String MINECRAFT_FAMILY = "Minecraft";
	private static final Style UI_BODY = Style.EMPTY.withFont(new FontDescription.Resource(UI_ID));
	private static final Style UI_SMALL = Style.EMPTY.withFont(new FontDescription.Resource(UI_SMALL_ID));
	private static final Style UI_TITLE = Style.EMPTY.withFont(new FontDescription.Resource(UI_TITLE_ID));

	public static final String GLOBE = "\uE80B";
	public static final String EYE = "\uE8F4";
	public static final String CUBE = "\uE1BD";
	public static final String MONITOR = "\uE30C";
	public static final String SIGNAL = "\uE01D";
	public static final String CLOUD = "\uE2BD";
	public static final String SAVE = "\uE161";
	public static final String SETTINGS = "\uE8B8";
	public static final String BELL = "\uE7F4";
	public static final String SEARCH = "\uE8B6";
	public static final String CHEVRON = "\uE5CF";
	public static final String PERSON = "\uE7FD";
	public static final String FLAG = "\uE227";
	public static final String BAG = "\uE8CC";
	public static final String HUD = "\uE871";
	public static final String MOB = "\uE91D";

	private MenuFont() {
	}

	public static boolean minecraftFamily(String family) {
		return family != null && family.equalsIgnoreCase(MINECRAFT_FAMILY);
	}

	public static boolean minecraft() {
		return minecraftFamily(VoidmarkConfig.get().uiFont);
	}

	public static boolean custom() {
		String family = VoidmarkConfig.get().uiFont;
		return family != null && !family.isBlank() && !minecraftFamily(family) && UiFontPack.loaded();
	}

	public static Style bodyStyle() {
		if (minecraft()) {
			return VANILLA;
		}
		return custom() ? UI_BODY : BODY;
	}

	public static Style smallStyle() {
		if (minecraft()) {
			return VANILLA;
		}
		return custom() ? UI_SMALL : SMALL;
	}

	public static Style titleStyle() {
		if (minecraft()) {
			return VANILLA;
		}
		return custom() ? UI_TITLE : TITLE;
	}

	public static Component body(String value) {
		return withFallback(value == null ? "" : value, bodyStyle());
	}

	public static Component small(String value) {
		return withFallback(value == null ? "" : value, smallStyle());
	}

	public static Component title(String value) {
		return withFallback(value == null ? "" : value, titleStyle());
	}

	public static Component brand(String value) {
		return Component.literal(value).withStyle(BODY);
	}

	public static Component brandSmall(String value) {
		return Component.literal(value).withStyle(SMALL);
	}

	public static Component vanilla(String value) {
		return withFallback(value == null ? "" : value, VANILLA);
	}

	public static Component vanilla(Component value) {
		return withFallback(value, VANILLA);
	}

	public static Component applyBody(Component value) {
		return withFallback(value, bodyStyle());
	}

	public static Component icon(String glyph) {
		return Component.literal(glyph).withStyle(ICON);
	}

	private static Component withFallback(String value, Style custom) {
		return applyLegacy(value, custom);
	}

	private static Component withFallback(Component value, Style custom) {
		Component text = value == null ? Component.empty() : value;
		MutableComponent out = Component.empty();
		text.visit((style, string) -> {
			if (string != null && !string.isEmpty()) {
				Style run = style.withFont(custom.getFont());
				out.append(applyLegacy(string, run));
			}
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	/**
	 * Hypixel nametags are often one literal with {@code §8[level] §bname §7♲}.
	 * Component drawing does not parse those codes, and {@link #splitUnicode}
	 * used to peel {@code §} (U+00A7) onto its own run so {@code 8}, {@code b},
	 * and {@code 7} drew as letters.
	 */
	private static Component applyLegacy(String value, Style base) {
		if (value == null || value.isEmpty()) {
			return Component.empty().withStyle(base);
		}
		if (value.indexOf('§') < 0) {
			return splitUnicode(value, base);
		}
		MutableComponent root = Component.empty();
		Style style = base;
		StringBuilder buf = new StringBuilder();
		int i = 0;
		while (i < value.length()) {
			char c = value.charAt(i);
			if (c == '§' && i + 1 < value.length()) {
				char next = value.charAt(i + 1);
				if (next == '§') {
					buf.append('§');
					i += 2;
					continue;
				}
				ChatFormatting fmt = ChatFormatting.getByCode(Character.toLowerCase(next));
				if (fmt != null) {
					flushLegacy(root, buf, style);
					style = fmt == ChatFormatting.RESET
						? Style.EMPTY.withFont(base.getFont())
						: style.applyFormat(fmt);
					i += 2;
					continue;
				}
			}
			buf.append(c);
			i++;
		}
		flushLegacy(root, buf, style);
		return root;
	}

	private static void flushLegacy(MutableComponent root, StringBuilder buf, Style style) {
		if (buf.isEmpty()) {
			return;
		}
		root.append(splitUnicode(buf.toString(), style));
		buf.setLength(0);
	}

	private static Component splitUnicode(String value, Style custom) {
		if (value == null || value.isEmpty()) {
			return Component.empty().withStyle(custom);
		}
		Style fallback = custom.withFont(FontDescription.DEFAULT);
		MutableComponent out = null;
		int i = 0;
		while (i < value.length()) {
			boolean unicode = unicodeGlyph(value.codePointAt(i));
			int start = i;
			i += Character.charCount(value.codePointAt(start));
			while (i < value.length() && unicodeGlyph(value.codePointAt(i)) == unicode) {
				i += Character.charCount(value.codePointAt(i));
			}
			MutableComponent piece = Component.literal(value.substring(start, i)).withStyle(unicode ? fallback : custom);
			out = out == null ? piece : out.append(piece);
		}
		return out == null ? Component.empty().withStyle(custom) : out;
	}

	/** Section sign is a format prefix, not a glyph that needs the vanilla fallback font. */
	private static boolean unicodeGlyph(int cp) {
		return cp > 0x7F && cp != 0xA7;
	}
}
