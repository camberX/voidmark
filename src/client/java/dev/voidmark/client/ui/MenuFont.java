package dev.voidmark.client.ui;

import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
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

	public static boolean custom() {
		String family = VoidmarkConfig.get().uiFont;
		return family != null && !family.isBlank() && UiFontPack.loaded();
	}

	public static Style bodyStyle() {
		return custom() ? UI_BODY : BODY;
	}

	public static Style smallStyle() {
		return custom() ? UI_SMALL : SMALL;
	}

	public static Style titleStyle() {
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
		return Component.literal(value == null ? "" : value).withStyle(VANILLA);
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
		return splitUnicode(value, custom);
	}

	private static Component withFallback(Component value, Style custom) {
		Component text = value == null ? Component.empty() : value;
		MutableComponent out = Component.empty();
		text.visit((style, string) -> {
			if (string != null && !string.isEmpty()) {
				Style run = style.withFont(custom.getFont());
				out.append(splitUnicode(string, run));
			}
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	private static Component splitUnicode(String value, Style custom) {
		if (value == null || value.isEmpty()) {
			return Component.empty().withStyle(custom);
		}
		Style fallback = custom.withFont(FontDescription.DEFAULT);
		MutableComponent out = null;
		int i = 0;
		while (i < value.length()) {
			boolean unicode = value.codePointAt(i) > 0x7F;
			int start = i;
			i += Character.charCount(value.codePointAt(start));
			while (i < value.length() && (value.codePointAt(i) > 0x7F) == unicode) {
				i += Character.charCount(value.codePointAt(i));
			}
			MutableComponent piece = Component.literal(value.substring(start, i)).withStyle(unicode ? fallback : custom);
			out = out == null ? piece : out.append(piece);
		}
		return out == null ? Component.empty().withStyle(custom) : out;
	}
}
