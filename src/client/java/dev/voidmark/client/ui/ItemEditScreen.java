package dev.voidmark.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmark.client.item.ItemIds;
import dev.voidmark.client.render.GuiDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ItemEditScreen extends Screen {
	private static final float MENU_W = 268;
	private static final float MENU_H = 196;
	private static final float SLOT = 52;
	private static final float FIELD_H = 18;
	private static final int SUGGESTIONS = 6;

	private final ItemStack original;
	private final String originalId;
	private String query;
	private int cursor;
	private boolean focused = true;
	private boolean placed;
	private boolean dragging;
	private double dragOffX;
	private double dragOffY;
	private float windowX;
	private float windowY;
	private float windowW = MENU_W;
	private float windowH = MENU_H;
	private float fieldX;
	private float fieldY;
	private float fieldW;
	private final List<Hit> hits = new ArrayList<>();
	private ItemIds.Preview preview = ItemIds.Preview.empty();
	private List<String> suggestions = List.of();
	private long blinkAt = System.currentTimeMillis();

	public ItemEditScreen() {
		super(Component.literal("Item"));
		Minecraft client = Minecraft.getInstance();
		ItemStack held = client.player == null ? ItemStack.EMPTY : ItemIds.held(client.player);
		this.original = held.isEmpty() ? ItemStack.EMPTY : held.copy();
		this.originalId = ItemIds.idOf(this.original);
		this.query = originalId;
		this.cursor = query.length();
	}

	@Override
	protected void init() {
		super.init();
		if (minecraft != null && minecraft.player != null) {
			ItemIds.rememberInventory(minecraft.player);
		}
		refresh();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (minecraft.level != null) {
			extractBlurredBackground(graphics);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		hits.clear();
		Font font = minecraft.font;
		layout();
		refresh();

		GuiDraw.fill(graphics, 0, 0, width, height, 0x14000000);
		GuiDraw.panel(graphics, windowX, windowY, windowW, windowH, Theme.WINDOW_RADIUS, Theme.WINDOW, Theme.LINE);
		GuiDraw.rounded(graphics, windowX + 1, windowY + 1, 3, windowH - 2, 1.5f, Theme.ACCENT);
		GuiDraw.title(graphics, font, "ITEM", windowX + 12, windowY + 8, Theme.TEXT);
		GuiDraw.small(graphics, font, "ID", windowX + 12 + GuiDraw.titleWidth(font, "ITEM") + 4, windowY + 10, Theme.ACCENT);
		hits.add(new Hit(windowX, windowY, windowW, 22, mx -> startDrag()));

		float slotX = windowX + (windowW - SLOT) * 0.5f;
		float slotY = windowY + 28;
		GuiDraw.panel(graphics, slotX, slotY, SLOT, SLOT, 8, Theme.TRACK, Theme.LINE);
		ItemStack stack = previewStack();
		if (!stack.isEmpty()) {
			float scale = 3f;
			float size = 16f * scale;
			float ix = slotX + (SLOT - size) * 0.5f;
			float iy = slotY + (SLOT - size) * 0.5f;
			graphics.pose().pushMatrix();
			graphics.pose().translate(ix, iy);
			graphics.pose().scale(scale, scale);
			graphics.item(stack, 0, 0);
			graphics.pose().popMatrix();
		} else {
			String mark = preview.kind() == ItemIds.Kind.UNKNOWN ? "?" : "—";
			GuiDraw.menu(
				graphics,
				font,
				mark,
				slotX + (SLOT - GuiDraw.menuWidth(font, mark)) * 0.5f,
				GuiDraw.middle(slotY, SLOT),
				Theme.OFF
			);
		}

		String title = preview.title();
		if (title.length() > 28) {
			title = title.substring(0, 27) + "…";
		}
		GuiDraw.menu(
			graphics,
			font,
			title,
			windowX + (windowW - GuiDraw.menuWidth(font, title)) * 0.5f,
			slotY + SLOT + 6,
			preview.kind() == ItemIds.Kind.UNKNOWN ? Theme.WARN : Theme.TEXT
		);
		String kind = switch (preview.kind()) {
			case VANILLA -> "Vanilla";
			case SKYBLOCK -> "Skyblock";
			case UNKNOWN -> "Unknown";
			case EMPTY -> "Empty";
		};
		GuiDraw.small(
			graphics,
			font,
			kind,
			windowX + (windowW - GuiDraw.smallWidth(font, kind)) * 0.5f,
			slotY + SLOT + 18,
			preview.kind() == ItemIds.Kind.SKYBLOCK ? Theme.ACCENT : Theme.MUTED
		);

		fieldX = windowX + 12;
		fieldY = slotY + SLOT + 32;
		fieldW = windowW - 24;
		boolean hoverField = GuiDraw.hovered(mouseX, mouseY, fieldX, fieldY, fieldW, FIELD_H);
		GuiDraw.panel(
			graphics,
			fieldX,
			fieldY,
			fieldW,
			FIELD_H,
			5,
			focused || hoverField ? Theme.CARD_HOVER : Theme.CARD,
			focused ? Theme.ACCENT : Theme.LINE
		);
		String shown = query.isEmpty() && !focused ? "minecraft: or sb:" : query;
		String clipped = clipField(font, shown, cursor, (int) fieldW - 14);
		int color = query.isEmpty() && !focused ? Theme.MUTED : Theme.TEXT;
		float textX = fieldX + 7;
		float textY = GuiDraw.middle(fieldY, FIELD_H);
		GuiDraw.menu(graphics, font, clipped, textX, textY, color);
		if (focused && ((System.currentTimeMillis() - blinkAt) / 530) % 2 == 0) {
			int caret = Math.min(cursor, clipped.length());
			if (!query.isEmpty() && !shown.equals(query)) {
				caret = clipped.length();
			}
			float cx = textX + GuiDraw.menuWidth(font, clipped.substring(0, Math.min(caret, clipped.length())));
			GuiDraw.fill(graphics, cx, fieldY + 4, 1, FIELD_H - 8, Theme.ACCENT);
		}
		hits.add(new Hit(fieldX, fieldY, fieldW, FIELD_H, mx -> {
			focused = true;
			cursor = cursorAt(font, query, mx, fieldX + 7);
			blinkAt = System.currentTimeMillis();
		}));

		float listY = fieldY + FIELD_H + 6;
		for (int i = 0; i < suggestions.size(); i++) {
			String id = suggestions.get(i);
			boolean hover = GuiDraw.hovered(mouseX, mouseY, fieldX, listY, fieldW, 14);
			if (hover) {
				GuiDraw.rounded(graphics, fieldX, listY, fieldW, 14, 4, 0x14FFFFFF);
			}
			GuiDraw.small(graphics, font, clip(font, id, (int) fieldW - 10), fieldX + 5, listY + 2, hover ? Theme.TEXT : Theme.MUTED);
			final String pick = id;
			hits.add(new Hit(fieldX, listY, fieldW, 14, mx -> applySuggestion(pick)));
			listY += 15;
		}

		if (suggestions.isEmpty()) {
			GuiDraw.small(graphics, font, "Type minecraft:stone or sb:HYPERION", fieldX, listY, Theme.OFF);
		}
	}

	private ItemStack previewStack() {
		if (!original.isEmpty() && query.trim().equalsIgnoreCase(originalId)) {
			return original;
		}
		return preview.stack();
	}

	private void layout() {
		float extra = suggestions.isEmpty() ? 0 : suggestions.size() * 15f - 8;
		windowW = Math.min(MENU_W, Math.max(1, width - 16));
		windowH = Math.min(MENU_H + extra, Math.max(1, height - 16));
		if (!placed) {
			windowX = (width - windowW) / 2f;
			windowY = (height - windowH) / 2f;
			placed = true;
		}
		windowX = Mth.clamp(windowX, 4, Math.max(4, width - windowW - 4));
		windowY = Mth.clamp(windowY, 4, Math.max(4, height - windowH - 4));
	}

	private void refresh() {
		preview = ItemIds.resolve(query);
		String trimmed = query.trim();
		boolean exact = preview.kind() != ItemIds.Kind.UNKNOWN
			&& !trimmed.isEmpty()
			&& (trimmed.equalsIgnoreCase(preview.canonical()) || trimmed.equalsIgnoreCase(originalId));
		suggestions = exact ? List.of() : ItemIds.suggest(query, SUGGESTIONS);
	}

	private void applySuggestion(String id) {
		query = id;
		cursor = query.length();
		focused = true;
		blinkAt = System.currentTimeMillis();
		refresh();
	}

	private void startDrag() {
		dragging = true;
		focused = false;
	}

	private static int cursorAt(Font font, String text, double mouseX, float startX) {
		float x = (float) mouseX - startX;
		int best = 0;
		float bestDist = Float.MAX_VALUE;
		for (int i = 0; i <= text.length(); i++) {
			float w = GuiDraw.menuWidth(font, text.substring(0, i));
			float dist = Math.abs(w - x);
			if (dist < bestDist) {
				bestDist = dist;
				best = i;
			}
		}
		return best;
	}

	private static String clipField(Font font, String value, int cursor, int maxWidth) {
		if (GuiDraw.menuWidth(font, value) <= maxWidth) {
			return value;
		}
		int from = 0;
		while (from < cursor && GuiDraw.menuWidth(font, value.substring(from)) > maxWidth) {
			from++;
		}
		String slice = value.substring(from);
		while (slice.length() > 1 && GuiDraw.menuWidth(font, slice) > maxWidth) {
			slice = slice.substring(0, slice.length() - 1);
		}
		return slice;
	}

	private static String clip(Font font, String value, int maxWidth) {
		if (GuiDraw.smallWidth(font, value) <= maxWidth) {
			return value;
		}
		String trimmed = value;
		while (trimmed.length() > 1 && GuiDraw.smallWidth(font, trimmed + "..") > maxWidth) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + "..";
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (event.button() != 0) {
			return super.mouseClicked(event, doubled);
		}
		dragging = false;
		focused = false;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(event.x(), event.y())) {
				hit.click(event.x());
				dragOffX = event.x() - windowX;
				dragOffY = event.y() - windowY;
				return true;
			}
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (event.button() == 0 && dragging) {
			windowX = (float) (event.x() - dragOffX);
			windowY = (float) (event.y() - dragOffY);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			onClose();
			return true;
		}
		if (!focused) {
			if (event.key() == InputConstants.KEY_TAB && !suggestions.isEmpty()) {
				applySuggestion(suggestions.get(0));
				return true;
			}
			return super.keyPressed(event);
		}
		if (event.key() == InputConstants.KEY_TAB && !suggestions.isEmpty()) {
				applySuggestion(suggestions.get(0));
			return true;
		}
		if (event.key() == InputConstants.KEY_LEFT) {
			cursor = Math.max(0, cursor - 1);
			blinkAt = System.currentTimeMillis();
			return true;
		}
		if (event.key() == InputConstants.KEY_RIGHT) {
			cursor = Math.min(query.length(), cursor + 1);
			blinkAt = System.currentTimeMillis();
			return true;
		}
		if (event.key() == InputConstants.KEY_HOME) {
			cursor = 0;
			blinkAt = System.currentTimeMillis();
			return true;
		}
		if (event.key() == InputConstants.KEY_END) {
			cursor = query.length();
			blinkAt = System.currentTimeMillis();
			return true;
		}
		if (event.key() == InputConstants.KEY_BACKSPACE) {
			if (cursor > 0) {
				query = query.substring(0, cursor - 1) + query.substring(cursor);
				cursor--;
				blinkAt = System.currentTimeMillis();
			}
			return true;
		}
		if (event.key() == InputConstants.KEY_DELETE) {
			if (cursor < query.length()) {
				query = query.substring(0, cursor) + query.substring(cursor + 1);
				blinkAt = System.currentTimeMillis();
			}
			return true;
		}
		if (event.key() == InputConstants.KEY_V && event.hasControlDown()) {
			String clip = minecraft.keyboardHandler.getClipboard();
			if (clip != null && !clip.isBlank()) {
				String insert = clip.replace("\n", "").replace("\r", "").trim();
				query = query.substring(0, cursor) + insert + query.substring(cursor);
				cursor += insert.length();
				blinkAt = System.currentTimeMillis();
			}
			return true;
		}
		if (event.key() == InputConstants.KEY_A && event.hasControlDown()) {
			cursor = query.length();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!focused || !event.isAllowedChatCharacter()) {
			return super.charTyped(event);
		}
		String insert = event.codepointAsString();
		if (insert.isEmpty() || insert.charAt(0) < 32) {
			return true;
		}
		query = query.substring(0, cursor) + insert + query.substring(cursor);
		cursor += insert.length();
		blinkAt = System.currentTimeMillis();
		return true;
	}

	private static final class Hit {
		final float x, y, w, h;
		final Click click;

		Hit(float x, float y, float w, float h, Click click) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.click = click;
		}

		boolean contains(double mx, double my) {
			return mx >= x && mx <= x + w && my >= y && my <= y + h;
		}

		void click(double mx) {
			click.run(mx);
		}
	}

	@FunctionalInterface
	private interface Click {
		void run(double mx);
	}
}
