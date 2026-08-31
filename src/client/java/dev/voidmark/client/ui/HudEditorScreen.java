package dev.voidmark.client.ui;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.render.GuiDraw;
import dev.voidmark.client.render.HudLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

public class HudEditorScreen extends Screen {
	private HudLayout.Id dragging;
	private float grabX;
	private float grabY;
	private HudLayout.Snap snap;
	private boolean hovered;

	public HudEditorScreen() {
		super(Component.literal("HUD Editor"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		Font font = minecraft.font;
		int guiW = graphics.guiWidth();
		int guiH = graphics.guiHeight();
		List<HudLayout.Box> boxes = HudLayout.boxes(font, guiW, guiH);

		GuiDraw.fill(graphics, 0, 0, guiW, guiH, 0x22000000);
		drawBar(graphics, font, mouseX, mouseY, guiW);

		if (snap != null) {
			if (snap.vLine() != null) {
				GuiDraw.fill(graphics, snap.vLine(), 0, 1, guiH, Theme.withAlpha(Theme.ACCENT, 160));
			}
			if (snap.hLine() != null) {
				GuiDraw.fill(graphics, 0, snap.hLine(), guiW, 1, Theme.withAlpha(Theme.ACCENT, 160));
			}
		}

		hovered = false;
		for (HudLayout.Box box : boxes) {
			boolean on = dragging == box.id() || box.contains(mouseX, mouseY);
			if (on && dragging == null) {
				hovered = true;
			}
			if (!HudLayout.enabled(box.id())) {
				GuiDraw.panel(graphics, box.x(), box.y(), box.w(), box.h(), 5, Theme.withAlpha(Theme.WINDOW, 80), Theme.LINE);
				GuiDraw.small(graphics, font, box.id().label, box.x() + 6, box.y() + 4, Theme.MUTED);
			}
			int outline = dragging == box.id() ? Theme.ACCENT : on ? Theme.withAlpha(Theme.ACCENT, 200) : Theme.withAlpha(Theme.TEXT, 50);
			GuiDraw.border(graphics, box.x() - 1, box.y() - 1, box.w() + 2, box.h() + 2, outline, dragging == box.id() ? 1.5f : 1f);
			if (on) {
				String tag = box.id().label + (HudLayout.enabled(box.id()) ? "" : "  off");
				float tagW = GuiDraw.smallWidth(font, tag) + 10;
				float tagX = box.x();
				float tagY = box.y() - 14;
				if (tagY < 22) {
					tagY = box.bottom() + 3;
				}
				GuiDraw.panel(graphics, tagX, tagY, tagW, 12, 3, Theme.WINDOW, Theme.ACCENT);
				GuiDraw.small(graphics, font, tag, tagX + 5, tagY + 1, Theme.TEXT);
			}
		}

		if (hovered || dragging != null) {
			graphics.requestCursor(CursorTypes.RESIZE_ALL);
		}
	}

	private void drawBar(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, int guiW) {
		float h = 20;
		float w = 268;
		float x = (guiW - w) * 0.5f;
		float y = 6;
		GuiDraw.panel(graphics, x, y, w, h, 6, Theme.WINDOW, Theme.LINE);
		GuiDraw.rounded(graphics, x + 1, y + 1, 3, h - 2, 1.5f, Theme.ACCENT);
		GuiDraw.small(graphics, font, "HUD EDITOR", x + 8, y + 5, Theme.ACCENT);
		boolean free = minecraft.hasShiftDown();
		GuiDraw.small(graphics, font, free ? "Free move" : "Snap to axes", x + 78, y + 5, Theme.MUTED);

		float dw = 40;
		float dx = x + w - dw - 6;
		boolean hover = GuiDraw.hovered(mouseX, mouseY, dx, y + 3, dw, 14);
		GuiDraw.panel(graphics, dx, y + 3, dw, 14, 5, hover ? Theme.CARD_HOVER : Theme.CARD, hover ? Theme.ACCENT : Theme.LINE);
		GuiDraw.menu(graphics, font, "Done", dx + (dw - GuiDraw.menuWidth(font, "Done")) * 0.5f, GuiDraw.middle(y + 3, 14), Theme.TEXT);
	}

	private boolean onDone(double mouseX, double mouseY, int guiW) {
		float w = 268;
		float x = (guiW - w) * 0.5f;
		float dx = x + w - 40 - 6;
		return GuiDraw.hovered(mouseX, mouseY, dx, 9, 40, 14);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (event.button() != 0) {
			return super.mouseClicked(event, doubled);
		}
		int guiW = minecraft.getWindow().getGuiScaledWidth();
		int guiH = minecraft.getWindow().getGuiScaledHeight();
		if (onDone(event.x(), event.y(), guiW)) {
			done();
			return true;
		}
		List<HudLayout.Box> boxes = HudLayout.boxes(minecraft.font, guiW, guiH);
		HudLayout.Box hit = pick(boxes, event.x(), event.y());
		if (hit != null) {
			dragging = hit.id();
			grabX = (float) (event.x() - hit.x());
			grabY = (float) (event.y() - hit.y());
			snap = null;
			return true;
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (event.button() != 0 || dragging == null) {
			return super.mouseDragged(event, dx, dy);
		}
		int guiW = minecraft.getWindow().getGuiScaledWidth();
		int guiH = minecraft.getWindow().getGuiScaledHeight();
		List<HudLayout.Box> boxes = HudLayout.boxes(minecraft.font, guiW, guiH);
		HudLayout.Box current = HudLayout.box(dragging, minecraft.font, guiW, guiH);
		float nx = (float) (event.x() - grabX);
		float ny = (float) (event.y() - grabY);
		snap = HudLayout.snap(nx, ny, current.w(), current.h(), dragging, boxes, guiW, guiH, minecraft.hasShiftDown());
		HudLayout.set(dragging, snap.x(), snap.y());
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			snap = null;
			VoidmarkConfig.get().save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			done();
			return true;
		}
		return super.keyPressed(event);
	}

	private void done() {
		VoidmarkConfig.get().save();
		minecraft.setScreen(new VoidmarkScreen());
	}

	private static HudLayout.Box pick(List<HudLayout.Box> boxes, double mx, double my) {
		HudLayout.Box best = null;
		float bestArea = Float.MAX_VALUE;
		for (HudLayout.Box box : boxes) {
			if (!box.contains(mx, my)) {
				continue;
			}
			float area = box.w() * box.h();
			if (area <= bestArea) {
				bestArea = area;
				best = box;
			}
		}
		return best;
	}
}
