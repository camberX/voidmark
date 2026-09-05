package dev.voidmark.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.item.LoadoutsMenus;
import dev.voidmark.client.mixin.AbstractContainerScreenInvoker;
import dev.voidmark.client.render.GuiDraw;
import dev.voidmark.client.render.LoadoutPreview;
import dev.voidmark.client.render.NametagRenderer;
import dev.voidmark.client.render.PlayerPreview;
import dev.voidmark.client.render.Starfield;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom Voidmark chrome for Hypixel {@code /loadouts}. The real chest stays
 * open; every click is sent through that screen's own {@code slotClicked}.
 */
public class LoadoutsScreen extends Screen {
	private static final float MENU_W = 440;
	private static final float MENU_H = 248;
	private static final float WELL = 20;
	private static final float SLOTS_W = 128;
	private static final float SLOTS_H = 148;
	private static float savedYaw = 28f;
	private static float savedPitch = 8f;

	private final AbstractContainerScreen<?> vanilla;
	private final AbstractContainerMenu menu;
	private final List<Hit> hits = new ArrayList<>();
	private float windowX;
	private float windowY;
	private float windowW = MENU_W;
	private float windowH = MENU_H;
	private float viewScale = 1f;
	private float viewCx;
	private float viewCy;
	private float viewLift;
	private float previewYaw = savedYaw;
	private float previewPitch = savedPitch;
	private boolean previewDrag;
	private boolean placed;
	private boolean closingMenu;
	private long lastNs = System.nanoTime();
	private float dt = 0.016f;
	private float appear;
	private ItemStack tooltip = ItemStack.EMPTY;
	private LoadoutsMenus.Snapshot snapshot = LoadoutsMenus.Snapshot.empty();

	public LoadoutsScreen(AbstractContainerScreen<?> vanilla) {
		super(vanilla.getTitle());
		this.vanilla = vanilla;
		this.menu = vanilla.getMenu();
	}

	public static Screen wrap(Screen screen) {
		if (screen instanceof LoadoutsScreen) {
			return screen;
		}
		if (screen instanceof AbstractContainerScreen<?> chest
			&& LoadoutsMenus.enabled()
			&& LoadoutsMenus.matches(chest.getTitle())) {
			return new LoadoutsScreen(chest);
		}
		return screen;
	}

	public static boolean open() {
		return Minecraft.getInstance().screen instanceof LoadoutsScreen;
	}

	public static void tickSwap(Minecraft client) {
		if (client == null) {
			return;
		}
		if (client.screen instanceof LoadoutsScreen loadouts) {
			loadouts.followServer();
			return;
		}
		if (!LoadoutsMenus.enabled()) {
			return;
		}
		if (client.screen instanceof AbstractContainerScreen<?> chest && LoadoutsMenus.matches(chest.getTitle())) {
			client.setScreen(new LoadoutsScreen(chest));
		}
	}

	private void followServer() {
		if (minecraft == null || minecraft.player == null) {
			return;
		}
		if (minecraft.player.containerMenu == menu) {
			return;
		}
		closingMenu = true;
		minecraft.setScreen(null);
	}

	@Override
	protected void init() {
		super.init();
		Theme.refresh();
		placed = false;
		appear = VoidmarkConfig.get().uiAnimations ? 0.08f : 1f;
		vanilla.init(width, height);
	}

	@Override
	public void resize(int width, int height) {
		super.resize(width, height);
		vanilla.resize(width, height);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (minecraft.level != null) {
			extractBlurredBackground(graphics);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		tickAnim();
		hits.clear();
		tooltip = ItemStack.EMPTY;
		snapshot = LoadoutsMenus.read(menu, getTitle());
		Font font = minecraft.font;
		layout();

		int dim = Anim.fade(0x28000000, appear);
		GuiDraw.fill(graphics, 0, 0, width, height, dim);

		float scale = (0.94f + 0.06f * appear) * VoidmarkConfig.normalizeMenuScale(VoidmarkConfig.get().menuScale);
		float lift = (1f - appear) * 10f;
		float cx = windowX + windowW * 0.5f;
		float cy = windowY + windowH * 0.5f;
		viewScale = Math.max(0.35f, scale);
		viewCx = cx;
		viewCy = cy;
		viewLift = lift;
		int localMx = Math.round(localX(mouseX));
		int localMy = Math.round(localY(mouseY));

		graphics.pose().pushMatrix();
		graphics.pose().translate(cx, cy + lift);
		graphics.pose().scale(scale, scale);
		graphics.pose().translate(-cx, -cy);

		boolean clip = GuiDraw.scissor(graphics, windowX, windowY, windowW, windowH);
		GuiDraw.rounded(graphics, windowX, windowY, windowW, windowH, Theme.WINDOW_RADIUS, Theme.WINDOW);
		Starfield.draw(graphics, windowX, windowY, windowW, windowH, Theme.WINDOW_RADIUS, appear);
		if (clip) {
			GuiDraw.disableScissor(graphics);
		}

		drawHeader(graphics, font, localMx, localMy);
		drawPreview(graphics, font, localMx, localMy);
		drawLoadouts(graphics, font, localMx, localMy);
		drawContents(graphics, font, localMx, localMy);

		graphics.pose().popMatrix();

		if (!tooltip.isEmpty()) {
			graphics.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
		}
	}

	private void drawHeader(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		GuiDraw.title(graphics, font, "LOADOUTS", windowX + 12, windowY + 8, Theme.TEXT);
		String page = snapshot.page().isBlank() ? snapshot.title() : snapshot.page();
		if (!page.isBlank()) {
			GuiDraw.small(
				graphics,
				font,
				page,
				windowX + 12 + GuiDraw.titleWidth(font, "LOADOUTS") + 6,
				windowY + 10,
				Theme.ACCENT
			);
		}
		hits.add(new Hit(windowX, windowY, windowW - 72, 22, -1, true, false));

		float btnY = windowY + 7;
		if (snapshot.prev() != null) {
			chip(graphics, font, windowX + windowW - 70, btnY, 20, mouseX, mouseY, "‹", snapshot.prev());
		}
		if (snapshot.next() != null) {
			chip(graphics, font, windowX + windowW - 48, btnY, 20, mouseX, mouseY, "›", snapshot.next());
		}
		chip(graphics, font, windowX + windowW - 26, btnY, 16, mouseX, mouseY, "×", snapshot.close());
	}

	private void chip(
		GuiGraphicsExtractor graphics,
		Font font,
		float x,
		float y,
		float w,
		int mouseX,
		int mouseY,
		String label,
		LoadoutsMenus.Piece piece
	) {
		boolean hover = GuiDraw.hovered(mouseX, mouseY, x, y, w, 16);
		GuiDraw.panel(graphics, x, y, w, 16, 5, hover ? Theme.CARD_HOVER : Theme.CARD, hover ? Theme.ACCENT : Theme.LINE);
		GuiDraw.menu(
			graphics,
			font,
			label,
			x + (w - GuiDraw.menuWidth(font, label)) * 0.5f,
			GuiDraw.middle(y, 16),
			Theme.TEXT
		);
		int slot = piece == null ? -2 : piece.slot();
		hits.add(new Hit(x, y, w, 16, slot, false, piece == null));
		if (hover && piece != null && !piece.stack().isEmpty()) {
			tooltip = piece.stack();
		}
	}

	private void drawPreview(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float left = windowX + 10;
		float top = windowY + 28;
		float stageW = windowX + windowW - SLOTS_W - 20 - left;
		float stageH = 148;
		GuiDraw.panel(graphics, left, top, stageW, stageH, 8, Theme.PANEL, Theme.LINE);
		if (!previewDrag) {
			previewYaw += dt * 18f;
			if (previewYaw > 360f || previewYaw < -360f) {
				previewYaw %= 360f;
			}
		}
		PlayerPreview.View view = new PlayerPreview.View(viewScale, viewCx, viewCy, viewLift);
		boolean hasPet = !snapshot.pet().isEmpty();
		float playerW = hasPet ? stageW * 0.58f : stageW;
		PlayerPreview.Drawn player = LoadoutPreview.player(
			graphics,
			left + 4,
			top + 2,
			playerW - 8,
			stageH - 18,
			previewYaw,
			previewPitch,
			view,
			snapshot.helmet(),
			snapshot.chest(),
			snapshot.legs(),
			snapshot.boots()
		);
		if (player != null) {
			NametagRenderer.drawVanilla(graphics, font, player.nameX(), player.nameY(), Component.literal(playerName()));
		}
		if (hasPet) {
			drawFloatingPet(graphics, font, left + playerW, top, stageW - playerW, stageH);
		} else {
			GuiDraw.small(graphics, font, "No pet in this loadout", left + 10, top + stageH - 16, Theme.OFF);
		}
		if (GuiDraw.hovered(mouseX, mouseY, left, top, stageW, stageH)) {
			GuiDraw.small(graphics, font, "Drag to rotate", left + 8, top + 6, Theme.MUTED);
		}
		hits.add(new Hit(left, top, stageW, stageH, -1, true, false));
	}

	private void drawFloatingPet(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, float h) {
		ItemStack pet = snapshot.pet();
		if (pet == null || pet.isEmpty()) {
			return;
		}
		float size = Math.min(52f, Math.min(w - 12f, h - 36f));
		float bob = (float) Math.sin(System.currentTimeMillis() / 420.0) * 3f;
		float ix = x + (w - size) * 0.5f;
		float iy = y + (h - size) * 0.48f + bob;
		drawItem(graphics, pet, ix, iy, size / 16f);
		Component name = snapshot.petName();
		if (name != null && !name.getString().isBlank()) {
			NametagRenderer.drawVanilla(graphics, font, x + w * 0.5f, iy - 14, name);
		}
	}

	private void drawLoadouts(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float x = windowX + windowW - SLOTS_W - 10;
		float y = windowY + 28;
		GuiDraw.panel(graphics, x, y, SLOTS_W, SLOTS_H, 8, Theme.CARD, Theme.LINE);
		GuiDraw.small(graphics, font, "SLOTS", x + 8, y + 6, Theme.ACCENT);
		boolean clip = GuiDraw.scissor(graphics, x + 1, y + 1, SLOTS_W - 2, SLOTS_H - 2);
		List<LoadoutsMenus.Piece> loadouts = snapshot.loadouts();
		int cols = 3;
		int rows = 4;
		float pad = 8f;
		float head = 18f;
		float gap = 3f;
		float innerW = SLOTS_W - pad * 2f;
		float innerH = SLOTS_H - head - pad;
		float cell = Math.min((innerW - gap * (cols - 1)) / cols, (innerH - gap * (rows - 1)) / rows);
		cell = Math.max(16f, Math.min(24f, cell));
		float gridW = cols * cell + (cols - 1) * gap;
		float gridH = rows * cell + (rows - 1) * gap;
		float gridX = x + pad + Math.max(0f, (innerW - gridW) * 0.5f);
		float gridY = y + head + Math.max(0f, (innerH - gridH) * 0.5f);
		int shown = Math.min(cols * rows, Math.max(loadouts.size(), 1));
		for (int i = 0; i < shown; i++) {
			int col = i % cols;
			int row = i / cols;
			float sx = gridX + col * (cell + gap);
			float sy = gridY + row * (cell + gap);
			if (sx + cell > x + SLOTS_W - 2f || sy + cell > y + SLOTS_H - 2f) {
				continue;
			}
			LoadoutsMenus.Piece piece = i < loadouts.size() ? loadouts.get(i) : null;
			boolean selected = piece != null && piece.selected();
			boolean hover = GuiDraw.hovered(mouseX, mouseY, sx, sy, cell, cell);
			GuiDraw.well(
				graphics,
				sx,
				sy,
				cell,
				selected ? Theme.withAlpha(0x55FF55, 70) : hover ? Theme.CARD_HOVER : Theme.TRACK,
				selected ? 0xFF55FF55 : hover ? Theme.ACCENT : Theme.LINE
			);
			float icon = Math.min(16f, cell - 8f);
			if (piece != null && !piece.stack().isEmpty()) {
				drawItem(graphics, piece.stack(), sx + (cell - icon) * 0.5f, sy + (cell - icon) * 0.5f, icon / 16f);
			}
			String mark = String.valueOf(i + 1);
			GuiDraw.small(graphics, font, mark, sx + 2, sy + 1, selected ? Theme.TEXT : Theme.MUTED);
			hits.add(new Hit(sx, sy, cell, cell, piece == null ? -1 : piece.slot(), false, false));
			if (hover && piece != null && !piece.stack().isEmpty()) {
				tooltip = piece.stack();
			}
		}
		if (clip) {
			GuiDraw.disableScissor(graphics);
		}
	}

	private void drawContents(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float x = windowX + 10;
		float y = windowY + 182;
		float w = windowW - 20;
		float h = windowH - 190;
		GuiDraw.panel(graphics, x, y, w, h, 8, Theme.CARD, Theme.LINE);
		float cx = x + 8;
		float cy = y + 8;
		for (LoadoutsMenus.Piece piece : labeledContents()) {
			if (cx + WELL + 4 > x + w - 8) {
				break;
			}
			boolean hover = GuiDraw.hovered(mouseX, mouseY, cx, cy, WELL, WELL);
			boolean selected = piece.selected();
			GuiDraw.well(
				graphics,
				cx,
				cy,
				WELL,
				selected ? Theme.withAlpha(0x55FF55, 60) : hover ? Theme.CARD_HOVER : Theme.TRACK,
				selected ? 0xFF55FF55 : hover ? Theme.ACCENT : Theme.LINE
			);
			if (!piece.stack().isEmpty()) {
				drawItem(graphics, piece.stack(), cx + 2, cy + 2, 1f);
			}
			hits.add(new Hit(cx, cy, WELL, WELL, piece.slot(), false, false));
			if (hover && !piece.stack().isEmpty()) {
				tooltip = piece.stack();
			}
			cx += WELL + 4;
		}
		GuiDraw.small(graphics, font, "1-9 equip and close · Right-click to edit", x + 8, y + h - 14, Theme.MUTED);
	}

	private List<LoadoutsMenus.Piece> labeledContents() {
		List<LoadoutsMenus.Piece> out = new ArrayList<>();
		LoadoutsMenus.Kind[] order = {
			LoadoutsMenus.Kind.HELMET,
			LoadoutsMenus.Kind.CHEST,
			LoadoutsMenus.Kind.LEGS,
			LoadoutsMenus.Kind.BOOTS,
			LoadoutsMenus.Kind.NECKLACE,
			LoadoutsMenus.Kind.CLOAK,
			LoadoutsMenus.Kind.BELT,
			LoadoutsMenus.Kind.GLOVES,
			LoadoutsMenus.Kind.PET,
			LoadoutsMenus.Kind.POWER,
			LoadoutsMenus.Kind.TUNING,
			LoadoutsMenus.Kind.HOTM,
			LoadoutsMenus.Kind.HOTF,
			LoadoutsMenus.Kind.OTHER
		};
		for (LoadoutsMenus.Kind kind : order) {
			for (LoadoutsMenus.Piece piece : snapshot.contents()) {
				if (piece.kind() == kind) {
					out.add(piece);
				}
			}
		}
		return out;
	}

	private void drawItem(GuiGraphicsExtractor graphics, ItemStack stack, float x, float y, float scale) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		LocalPlayer player = minecraft.player;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		if (scale != 1f) {
			graphics.pose().scale(scale, scale);
		}
		if (player == null) {
			graphics.item(stack, 0, 0);
		} else {
			graphics.item(player, stack, 0, 0, 1);
		}
		graphics.pose().popMatrix();
	}

	private void layout() {
		windowW = Math.min(MENU_W, Math.max(280, width - 16));
		windowH = Math.min(MENU_H, Math.max(200, height - 16));
		if (!placed) {
			windowX = (width - windowW) * 0.5f;
			windowY = (height - windowH) * 0.5f;
			placed = true;
		}
		windowX = Mth.clamp(windowX, 4, Math.max(4, width - windowW - 4));
		windowY = Mth.clamp(windowY, 4, Math.max(4, height - windowH - 4));
	}

	private void tickAnim() {
		long now = System.nanoTime();
		dt = Mth.clamp((now - lastNs) / 1_000_000_000f, 0.008f, 0.05f);
		lastNs = now;
		appear = Anim.exp(appear, 1f, 14f, dt);
	}

	private float localX(double mx) {
		return (float) ((mx - viewCx) / viewScale + viewCx);
	}

	private float localY(double my) {
		return (float) ((my - viewCy - viewLift) / viewScale + viewCy);
	}

	private String playerName() {
		LocalPlayer player = minecraft.player;
		return player == null ? "You" : player.getGameProfile().name();
	}

	private void clickSlot(int slot, int button) {
		if (slot == -2) {
			onClose();
			return;
		}
		if (slot < 0 || minecraft == null || minecraft.player == null || minecraft.player.containerMenu != menu) {
			return;
		}
		if (!menu.isValidSlotIndex(slot)) {
			return;
		}
		Slot target = menu.getSlot(slot);
		((AbstractContainerScreenInvoker) vanilla).voidmark$slotClicked(target, slot, button, ContainerInput.PICKUP);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		double lx = localX(event.x());
		double ly = localY(event.y());
		previewDrag = false;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (!hit.contains(lx, ly)) {
				continue;
			}
			if (hit.rotate && event.button() == 0) {
				previewDrag = true;
				return true;
			}
			if (hit.close) {
				if (event.button() == 0) {
					onClose();
				}
				return true;
			}
			if (hit.slot >= 0 && (event.button() == 0 || event.button() == 1)) {
				clickSlot(hit.slot, event.button());
				return true;
			}
			return true;
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (previewDrag && event.button() == 0) {
			previewYaw += (float) dx * 0.7f;
			previewPitch = Mth.clamp(previewPitch - (float) dy * 0.45f, -35f, 35f);
			return true;
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == 0) {
			previewDrag = false;
			savedYaw = previewYaw;
			savedPitch = previewPitch;
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (minecraft != null && minecraft.options.keyInventory.matches(event)) {
			onClose();
			return true;
		}
		int index = hotkeyIndex(event.key());
		if (index >= 0) {
			equipAndClose(index);
			return true;
		}
		return super.keyPressed(event);
	}

	private static int hotkeyIndex(int key) {
		if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9) {
			return key - InputConstants.KEY_1;
		}
		if (key >= InputConstants.KEY_NUMPAD1 && key <= InputConstants.KEY_NUMPAD9) {
			return key - InputConstants.KEY_NUMPAD1;
		}
		return -1;
	}

	private void equipAndClose(int index) {
		snapshot = LoadoutsMenus.read(menu, getTitle());
		List<LoadoutsMenus.Piece> loadouts = snapshot.loadouts();
		if (index < 0 || index >= loadouts.size()) {
			return;
		}
		LoadoutsMenus.Piece piece = loadouts.get(index);
		if (piece == null || piece.slot() < 0) {
			return;
		}
		clickSlot(piece.slot(), 0);
		onClose();
	}

	@Override
	public void onClose() {
		if (closingMenu) {
			super.onClose();
			return;
		}
		if (minecraft != null && minecraft.player != null && minecraft.player.containerMenu == menu) {
			closingMenu = true;
			minecraft.player.closeContainer();
			return;
		}
		super.onClose();
	}

	@Override
	public void removed() {
		savedYaw = previewYaw;
		savedPitch = previewPitch;
		LoadoutPreview.clear();
		if (!closingMenu) {
			vanilla.removed();
		}
	}

	private static final class Hit {
		final float x, y, w, h;
		final int slot;
		final boolean rotate;
		final boolean close;

		Hit(float x, float y, float w, float h, int slot, boolean rotate, boolean close) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.slot = slot;
			this.rotate = rotate;
			this.close = close;
		}

		boolean contains(double mx, double my) {
			return mx >= x && mx <= x + w && my >= y && my <= y + h;
		}
	}
}
