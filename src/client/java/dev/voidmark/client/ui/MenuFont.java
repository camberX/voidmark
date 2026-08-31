package dev.voidmark.client.ui;

import dev.voidmark.Voidmark;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

public final class MenuFont {
	public static final Identifier BODY_ID = Voidmark.id("nl");
	public static final Identifier SMALL_ID = Voidmark.id("nl_small");
	public static final Identifier TITLE_ID = Voidmark.id("nl_title");
	public static final Identifier ICON_ID = Voidmark.id("nl_icon");

	public static final Style BODY = Style.EMPTY.withFont(new FontDescription.Resource(BODY_ID));
	public static final Style SMALL = Style.EMPTY.withFont(new FontDescription.Resource(SMALL_ID));
	public static final Style TITLE = Style.EMPTY.withFont(new FontDescription.Resource(TITLE_ID));
	public static final Style ICON = Style.EMPTY.withFont(new FontDescription.Resource(ICON_ID));

	public static final String GLOBE = "\uE80B";
	public static final String EYE = "\uE8F4";
	public static final String CUBE = "\uE1BD";
	public static final String MONITOR = "\uE30C";
	public static final String SIGNAL = "\uE01D";
	public static final String SAVE = "\uE161";
	public static final String SETTINGS = "\uE8B8";
	public static final String BELL = "\uE7F4";
	public static final String SEARCH = "\uE8B6";
	public static final String PALETTE = "\uE40A";
	public static final String CHEVRON = "\uE5CF";

	private MenuFont() {
	}

	public static Component body(String value) {
		return Component.literal(value).withStyle(BODY);
	}

	public static Component small(String value) {
		return Component.literal(value).withStyle(SMALL);
	}

	public static Component title(String value) {
		return Component.literal(value).withStyle(TITLE);
	}

	public static Component icon(String glyph) {
		return Component.literal(glyph).withStyle(ICON);
	}
}
