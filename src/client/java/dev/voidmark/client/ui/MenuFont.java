package dev.voidmark.client.ui;

import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

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
		return Component.literal(value).withStyle(bodyStyle());
	}

	public static Component small(String value) {
		return Component.literal(value).withStyle(smallStyle());
	}

	public static Component title(String value) {
		return Component.literal(value).withStyle(titleStyle());
	}

	public static Component brand(String value) {
		return Component.literal(value).withStyle(BODY);
	}

	public static Component brandSmall(String value) {
		return Component.literal(value).withStyle(SMALL);
	}

	public static Component applyBody(Component value) {
		Component text = value == null ? Component.empty() : value;
		return text.copy().withStyle(bodyStyle());
	}

	public static Component icon(String glyph) {
		return Component.literal(glyph).withStyle(ICON);
	}
}
