package dev.voidmark.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import dev.voidmark.Voidmark;
import dev.voidmark.client.render.GuiDraw;
import dev.voidmark.client.visual.CapeCrop;
import dev.voidmark.client.visual.CustomCape;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Crop a photo onto the 10×16 cape face. Drag pans the window; scroll zooms.
 */
public class CapeCreatorScreen extends Screen {
	private static final Identifier SOURCE_ID = Voidmark.id("cape_edit");
	private static final float MENU_W = 392;
	private static final float MENU_H = 236;
	private static final float STAGE_W = 248;
	private static final float STAGE_H = 168;
	private static final float PREVIEW_W = 62;
	private static final float PREVIEW_H = 100;
	private static final float BTN_H = 16;

	private final Screen parent;
	private final byte[] imageBytes;
	private final List<Hit> hits = new ArrayList<>();
	private CapeCrop crop;
	private int srcW;
	private int srcH;
	private float windowX;
	private float windowY;
	private float stageX;
	private float stageY;
	private float imgX;
	private float imgY;
	private float imgW;
	private float imgH;
	private boolean placed;
	private boolean draggingWindow;
	private boolean panning;
	private double dragOffX;
	private double dragOffY;
	private boolean ready;

	public CapeCreatorScreen(Screen parent, byte[] imageBytes) {
		super(Component.literal("Cape"));
		this.parent = parent;
		this.imageBytes = imageBytes;
	}

	@Override
	protected void init() {
		super.init();
		if (!placed) {
			windowX = (width - MENU_W) * 0.5f;
			windowY = (height - MENU_H) * 0.5f;
			placed = true;
		}
		if (ready) {
			return;
		}
		try {
			NativeImage source = NativeImage.read(imageBytes);
			srcW = source.getWidth();
			srcH = source.getHeight();
			NativeImage copy = new NativeImage(srcW, srcH, false);
			copy.copyFrom(source);
			source.close();
			minecraft.getTextureManager().register(SOURCE_ID, new DynamicTexture(() -> "voidmark-cape-edit", copy));
			crop = CapeCrop.cover(srcW, srcH);
			ready = true;
		} catch (Exception exception) {
			onClose();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		ready = false;
		minecraft.setScreen(parent);
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
		if (!ready || crop == null) {
			return;
		}
		Font font = minecraft.font;
		Theme.refresh();
		GuiDraw.fill(graphics, 0, 0, width, height, 0x14000000);
		GuiDraw.panel(graphics, windowX, windowY, MENU_W, MENU_H, Theme.WINDOW_RADIUS, Theme.WINDOW, Theme.LINE);
		GuiDraw.title(graphics, font, "CAPE", windowX + 12, windowY + 8, Theme.TEXT);
		GuiDraw.small(graphics, font, "CREATOR", windowX + 12 + GuiDraw.titleWidth(font, "CAPE") + 4, windowY + 10, Theme.ACCENT);
		hits.add(new Hit(windowX, windowY, MENU_W, 22, this::startWindowDrag, true));

		stageX = windowX + 12;
		stageY = windowY + 28;
		GuiDraw.panel(graphics, stageX, stageY, STAGE_W, STAGE_H, 6, Theme.PANEL, Theme.LINE);
		layoutImage();
		GuiDraw.blit(graphics, SOURCE_ID, imgX, imgY, imgW, imgH, 0f, 0f, srcW, srcH, srcW, srcH);
		float cx = imgX + crop.x * imgW;
		float cy = imgY + crop.y * imgH;
		float cw = crop.w * imgW;
		float ch = crop.h * imgH;
		GuiDraw.fill(graphics, imgX, imgY, imgW, Math.max(0f, cy - imgY), 0x99000000);
		GuiDraw.fill(graphics, imgX, cy + ch, imgW, Math.max(0f, imgY + imgH - cy - ch), 0x99000000);
		GuiDraw.fill(graphics, imgX, cy, Math.max(0f, cx - imgX), ch, 0x99000000);
		GuiDraw.fill(graphics, cx + cw, cy, Math.max(0f, imgX + imgW - cx - cw), ch, 0x99000000);
		GuiDraw.border(graphics, cx, cy, cw, ch, Theme.ACCENT, 1.2f);

		float px = windowX + MENU_W - 12 - PREVIEW_W;
		float py = stageY;
		GuiDraw.small(graphics, font, "FACE", px, py - 1, Theme.MUTED);
		GuiDraw.panel(graphics, px, py + 10, PREVIEW_W, PREVIEW_H, 5, Theme.PANEL, Theme.LINE);
		int ru = Math.max(0, Math.round(crop.x * srcW));
		int rv = Math.max(0, Math.round(crop.y * srcH));
		int rw = Math.max(1, Math.round(crop.w * srcW));
		int rh = Math.max(1, Math.round(crop.h * srcH));
		ru = Math.min(ru, srcW - 1);
		rv = Math.min(rv, srcH - 1);
		rw = Math.min(rw, srcW - ru);
		rh = Math.min(rh, srcH - rv);
		GuiDraw.blit(graphics, SOURCE_ID, px + 3, py + 13, PREVIEW_W - 6, PREVIEW_H - 6, ru, rv, rw, rh, srcW, srcH);

		GuiDraw.small(graphics, font, "Drag to pan · Scroll to zoom", stageX, stageY + STAGE_H + 6, Theme.MUTED);

		float by = windowY + MENU_H - 12 - BTN_H;
		button(graphics, font, mouseX, mouseY, stageX, by, 70, "Reset", () -> crop = CapeCrop.cover(srcW, srcH));
		button(graphics, font, mouseX, mouseY, stageX + 78, by, 70, "Cancel", this::onClose);
		button(graphics, font, mouseX, mouseY, windowX + MENU_W - 12 - 86, by, 86, "Apply cape", this::apply);
	}

	private void layoutImage() {
		float fit = Math.min((STAGE_W - 8) / srcW, (STAGE_H - 8) / srcH);
		imgW = srcW * fit;
		imgH = srcH * fit;
		imgX = stageX + (STAGE_W - imgW) * 0.5f;
		imgY = stageY + (STAGE_H - imgH) * 0.5f;
	}

	private void button(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		float x,
		float y,
		float w,
		String label,
		Runnable click
	) {
		boolean hover = GuiDraw.hovered(mouseX, mouseY, x, y, w, BTN_H);
		GuiDraw.panel(graphics, x, y, w, BTN_H, 5, hover ? Theme.CARD_HOVER : Theme.CARD, hover ? Theme.ACCENT : Theme.LINE);
		GuiDraw.menu(graphics, font, label, x + (w - GuiDraw.menuWidth(font, label)) * 0.5f, GuiDraw.middle(y, BTN_H), Theme.TEXT);
		hits.add(new Hit(x, y, w, BTN_H, click, false));
	}

	private void apply() {
		if (crop == null) {
			return;
		}
		CapeCrop copy = new CapeCrop(crop.x, crop.y, crop.w, crop.h);
		CustomCape.applyCrop(imageBytes, copy);
		onClose();
	}

	private void startWindowDrag() {
		draggingWindow = true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (event.button() != 0 || !ready) {
			return super.mouseClicked(event, doubled);
		}
		double mx = event.x();
		double my = event.y();
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(mx, my)) {
				if (hit.windowDrag) {
					dragOffX = mx - windowX;
					dragOffY = my - windowY;
				}
				hit.click.run();
				return true;
			}
		}
		layoutImage();
		if (GuiDraw.hovered(mx, my, imgX, imgY, imgW, imgH)) {
			panning = true;
			return true;
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (event.button() == 0 && draggingWindow) {
			windowX = (float) (event.x() - dragOffX);
			windowY = (float) (event.y() - dragOffY);
			return true;
		}
		if (event.button() == 0 && panning && crop != null && imgW > 1f && imgH > 1f) {
			crop.pan((float) (dx / imgW), (float) (dy / imgH), srcW, srcH);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		draggingWindow = false;
		panning = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!ready || crop == null || scrollY == 0) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}
		layoutImage();
		if (!GuiDraw.hovered(mouseX, mouseY, imgX, imgY, imgW, imgH)) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}
		float px = Mth.clamp((float) ((mouseX - imgX) / imgW), 0f, 1f);
		float py = Mth.clamp((float) ((mouseY - imgY) / imgH), 0f, 1f);
		float factor = scrollY > 0 ? 0.88f : 1.14f;
		crop.zoom(factor, px, py, srcW, srcH);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape() || event.key() == InputConstants.KEY_RETURN) {
			if (event.key() == InputConstants.KEY_RETURN) {
				apply();
			} else {
				onClose();
			}
			return true;
		}
		return super.keyPressed(event);
	}

	private record Hit(float x, float y, float w, float h, Runnable click, boolean windowDrag) {
		boolean contains(double mx, double my) {
			return GuiDraw.hovered(mx, my, x, y, w, h);
		}
	}
}
