package dev.voidmark.client.ui;

import dev.voidmark.Voidmark;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

public final class MenuFont {
	public static final Identifier BODY_ID = Voidmark.id("nl");
	public static final Identifier TITLE_ID = Voidmark.id("nl_title");
	public static final Style BODY = Style.EMPTY.withFont(new FontDescription.Resource(BODY_ID));
	public static final Style TITLE = Style.EMPTY.withFont(new FontDescription.Resource(TITLE_ID));

	private MenuFont() {
	}

	public static Component body(String value) {
		return Component.literal(value).withStyle(BODY);
	}

	public static Component title(String value) {
		return Component.literal(value).withStyle(TITLE);
	}
}
