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
import net.minecraft.util.Mth;

import java.util.List;

public class HudEditorScreen extends Screen {
	private static final float BAR_W = 268;
	private static final float INSPECT_W = 248;
	private static final float INSPECT_H = 22;
	private static final float SLIDER_W = 110;

	private HudLayout.Id selected;
	private HudLayout.Id dragging;
	private boolean scaling;
	private float grabX;
	private float grabY;
	private HudLayout.Snap snap;
	private boolean hovered;
	private float inspectX;
	private float inspectY;
	private float sliderX;
	private float sliderY;

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
			boolean on = selected == box.id() || dragging == box.id() || box.contains(mouseX, mouseY);
			if (on && dragging == null && !scaling) {
				hovered = true;
			}
			if (!HudLayout.enabled(box.id())) {
				GuiDraw.panel(graphics, box.x(), box.y(), box.w(), box.h(), 5, Theme.withAlpha(Theme.WINDOW, 80), Theme.LINE);
				GuiDraw.small(graphics, font, box.id().label, box.x() + 6, box.y() + 4, Theme.MUTED);
			}
			int outline = dragging == box.id() || selected == box.id()
				? Theme.ACCENT
				: on ? Theme.withAlpha(Theme.ACCENT, 200) : Theme.withAlpha(Theme.TEXT, 50);
			GuiDraw.border(graphics, box.x() - 1, box.y() - 1, box.w() + 2, box.h() + 2, outline, selected == box.id() ? 1.5f : 1f);
			if (on) {
				String tag = box.id().label
					+ "  "
					+ Math.round(HudLayout.scale(box.id()) * 100)
					+ "%"
					+ (HudLayout.enabled(box.id()) ? "" : "  off");
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

		if (selected != null) {
			drawInspector(graphics, font, mouseX, mouseY, guiW, guiH);
		}

		if (hovered || dragging != null) {
			graphics.requestCursor(CursorTypes.RESIZE_ALL);
		}
	}

	private void drawBar(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, int guiW) {
		float h = 20;
		float x = (guiW - BAR_W) * 0.5f;
		float y = 6;
		GuiDraw.panel(graphics, x, y, BAR_W, h, 6, Theme.WINDOW, Theme.LINE, Theme.ACCENT);
		GuiDraw.small(graphics, font, "HUD EDITOR", x + 8, y + 5, Theme.ACCENT);
		boolean free = minecraft.hasShiftDown();
		GuiDraw.small(graphics, font, free ? "Free move" : "Snap · scroll to scale", x + 78, y + 5, Theme.MUTED);

		float dw = 40;
		float dx = x + BAR_W - dw - 6;
		boolean hover = GuiDraw.hovered(mouseX, mouseY, dx, y + 3, dw, 14);
		GuiDraw.panel(graphics, dx, y + 3, dw, 14, 5, hover ? Theme.CARD_HOVER : Theme.CARD, hover ? Theme.ACCENT : Theme.LINE);
		GuiDraw.menu(graphics, font, "Done", dx + (dw - GuiDraw.menuWidth(font, "Done")) * 0.5f, GuiDraw.middle(y + 3, 14), Theme.TEXT);
	}

	private void drawInspector(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, int guiW, int guiH) {
		inspectX = (guiW - INSPECT_W) * 0.5f;
		inspectY = guiH - INSPECT_H - 8;
		GuiDraw.panel(graphics, inspectX, inspectY, INSPECT_W, INSPECT_H, 6, Theme.WINDOW, Theme.ACCENT);
		GuiDraw.small(graphics, font, selected.label, inspectX + 8, inspectY + 6, Theme.ACCENT);
		GuiDraw.small(graphics, font, "Scale", inspectX + 64, inspectY + 6, Theme.MUTED);

		sliderX = inspectX + 92;
		sliderY = inspectY + 9;
		float scale = HudLayout.scale(selected);
		float t = (scale - HudLayout.SCALE_MIN) / (HudLayout.SCALE_MAX - HudLayout.SCALE_MIN);
		boolean hover = GuiDraw.hovered(mouseX, mouseY, sliderX - 2, inspectY, SLIDER_W + 4, INSPECT_H);
		GuiDraw.pill(graphics, sliderX, sliderY, SLIDER_W, 3, Theme.TRACK);
		GuiDraw.pill(graphics, sliderX, sliderY, Math.max(3, SLIDER_W * t), 3, Theme.ACCENT);
		GuiDraw.circle(graphics, sliderX + SLIDER_W * t, sliderY + 1.5f, hover || scaling ? 4.2f : 3.6f, Theme.ACCENT);

		String pct = Math.round(scale * 100) + "%";
		GuiDraw.small(graphics, font, pct, inspectX + INSPECT_W - GuiDraw.smallWidth(font, pct) - 8, inspectY + 6, Theme.TEXT);
	}

	private boolean onDone(double mouseX, double mouseY, int guiW) {
		float x = (guiW - BAR_W) * 0.5f;
		float dx = x + BAR_W - 40 - 6;
		return GuiDraw.hovered(mouseX, mouseY, dx, 9, 40, 14);
	}

	private boolean onInspector(double mouseX, double mouseY) {
		return selected != null && GuiDraw.hovered(mouseX, mouseY, inspectX, inspectY, INSPECT_W, INSPECT_H);
	}

	private boolean onSlider(double mouseX, double mouseY) {
		return selected != null && GuiDraw.hovered(mouseX, mouseY, sliderX - 4, inspectY, SLIDER_W + 8, INSPECT_H);
	}

	private void applySlider(double mouseX) {
		if (selected == null) {
			return;
		}
		float t = Mth.clamp((float) ((mouseX - sliderX) / SLIDER_W), 0f, 1f);
		HudLayout.setScale(selected, HudLayout.SCALE_MIN + t * (HudLayout.SCALE_MAX - HudLayout.SCALE_MIN));
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
		if (onSlider(event.x(), event.y())) {
			scaling = true;
			applySlider(event.x());
			return true;
		}
		if (onInspector(event.x(), event.y())) {
			return true;
		}
		List<HudLayout.Box> boxes = HudLayout.boxes(minecraft.font, guiW, guiH);
		HudLayout.Box hit = pick(boxes, event.x(), event.y());
		if (hit != null) {
			selected = hit.id();
			dragging = hit.id();
			grabX = (float) (event.x() - hit.x());
			grabY = (float) (event.y() - hit.y());
			snap = null;
			return true;
		}
		selected = null;
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (event.button() != 0) {
			return super.mouseDragged(event, dx, dy);
		}
		if (scaling && selected != null) {
			applySlider(event.x());
			return true;
		}
		if (dragging == null) {
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
		if (dragging != null || scaling) {
			dragging = null;
			scaling = false;
			snap = null;
			VoidmarkConfig.get().save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int guiW = minecraft.getWindow().getGuiScaledWidth();
		int guiH = minecraft.getWindow().getGuiScaledHeight();
		HudLayout.Id id = selected;
		if (id == null) {
			HudLayout.Box hit = pick(HudLayout.boxes(minecraft.font, guiW, guiH), mouseX, mouseY);
			id = hit == null ? null : hit.id();
			if (id != null) {
				selected = id;
			}
		}
		if (id == null || scrollY == 0) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}
		HudLayout.setScale(id, HudLayout.scale(id) + (float) scrollY * 0.08f);
		VoidmarkConfig.get().save();
		return true;
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
