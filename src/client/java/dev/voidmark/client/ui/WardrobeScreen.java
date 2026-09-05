package dev.voidmark.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.item.WardrobeMenus;
import dev.voidmark.client.mixin.AbstractContainerScreenInvoker;
import dev.voidmark.client.render.GuiDraw;
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
 * Custom Voidmark chrome for Hypixel wardrobe ({@code (1/3) Armor Sets}).
 * One 3D armor model per slot — clicks go through the real chest's {@code slotClicked}.
 */
public class WardrobeScreen extends Screen {
	private static final float MENU_W = 412;
	private static final float MENU_H = 292;
	private static float savedYaw = 28f;
	private static float savedPitch = 8f;
	private static WardrobeMenus.Snapshot cache = WardrobeMenus.Snapshot.empty();
	private static final List<QueuedClick> QUEUE = new ArrayList<>();
	private static final long SUPPRESS_NS = 3_000_000_000L;
	private static boolean silentFlush;
	private static boolean cancelIncoming;
	private static long suppressUntil;

	private AbstractContainerScreen<?> vanilla;
	private AbstractContainerMenu menu;
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
	private int pendingSlot = -1;
	private int pendingButton;
	private float dragDist;
	private boolean placed;
	private boolean closingMenu;
	private boolean attaching;
	private long lastNs = System.nanoTime();
	private float dt = 0.016f;
	private float appear;
	private ItemStack tooltip = ItemStack.EMPTY;
	private WardrobeMenus.Snapshot snapshot = WardrobeMenus.Snapshot.empty();

	public WardrobeScreen(AbstractContainerScreen<?> vanilla) {
		super(vanilla.getTitle());
		this.vanilla = vanilla;
		this.menu = vanilla.getMenu();
	}

	private WardrobeScreen(WardrobeMenus.Snapshot cached) {
		super(Component.literal(cached.title().isBlank() ? "Armor Sets" : cached.title()));
		this.vanilla = null;
		this.menu = null;
		this.snapshot = cached;
	}

	public static Screen wrap(Screen screen) {
		if (screen instanceof WardrobeScreen || screen instanceof LoadoutsScreen) {
			return screen;
		}
		if (!(screen instanceof AbstractContainerScreen<?> chest)
			|| !WardrobeMenus.enabled()
			|| !WardrobeMenus.matches(chest.getTitle())) {
			return screen;
		}
		if (shouldDiscardIncoming()) {
			return discardIncoming(chest);
		}
		Minecraft client = Minecraft.getInstance();
		if (client.screen instanceof WardrobeScreen existing) {
			existing.attach(chest);
			return existing;
		}
		return new WardrobeScreen(chest);
	}

	public static boolean hasCache() {
		return cache.hasSets();
	}

	public static WardrobeScreen fromCache() {
		allowReopen();
		return new WardrobeScreen(cache.copy());
	}

	public static void allowReopen() {
		suppressUntil = 0L;
		cancelIncoming = false;
	}

	public static void resetPending() {
		QUEUE.clear();
		silentFlush = false;
		cancelIncoming = false;
		suppressUntil = 0L;
	}

	public static void tickSwap(Minecraft client) {
		if (client == null) {
			return;
		}
		if (shouldDiscardIncoming()
			&& client.screen instanceof AbstractContainerScreen<?> chest
			&& WardrobeMenus.matches(chest.getTitle())) {
			discardIncoming(chest);
			client.setScreen(null);
			return;
		}
		if (client.screen instanceof WardrobeScreen wardrobe) {
			wardrobe.followServer();
			return;
		}
		if (client.screen instanceof LoadoutsScreen || !WardrobeMenus.enabled() || shouldDiscardIncoming()) {
			return;
		}
		if (client.screen instanceof AbstractContainerScreen<?> chest && WardrobeMenus.matches(chest.getTitle())) {
			client.setScreen(new WardrobeScreen(chest));
		}
	}

	private void attach(AbstractContainerScreen<?> chest) {
		attaching = true;
		AbstractContainerScreen<?> prior = this.vanilla;
		this.vanilla = chest;
		this.menu = chest.getMenu();
		if (prior != null && prior != chest) {
			prior.removed();
		}
	}

	private void followServer() {
		if (minecraft == null || minecraft.player == null || vanilla == null || menu == null) {
			return;
		}
		if (minecraft.player.containerMenu == menu) {
			return;
		}
		if (minecraft.player.containerMenu == minecraft.player.inventoryMenu) {
			closingMenu = true;
			rememberCache();
			suppressReopen();
			minecraft.setScreen(null);
		}
	}

	@Override
	protected void init() {
		super.init();
		Theme.refresh();
		if (vanilla != null) {
			vanilla.init(width, height);
		}
		if (menu != null) {
			snapshot = WardrobeMenus.read(menu, vanilla != null ? vanilla.getTitle() : getTitle());
			rememberCache();
		}
		if (attaching) {
			attaching = false;
			flushQueue();
			return;
		}
		placed = false;
		appear = VoidmarkConfig.get().loadoutsOpenAnim ? 0.08f : 1f;
	}

	@Override
	public void resize(int width, int height) {
		super.resize(width, height);
		if (vanilla != null) {
			vanilla.resize(width, height);
		}
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
		if (menu != null) {
			snapshot = WardrobeMenus.read(menu, vanilla != null ? vanilla.getTitle() : getTitle());
		}
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
		drawSlots(graphics, font, localMx, localMy);

		graphics.pose().popMatrix();

		if (!tooltip.isEmpty()) {
			graphics.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
		}
	}

	private void drawHeader(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		GuiDraw.title(graphics, font, "WARDROBE", windowX + 12, windowY + 8, Theme.TEXT);
		String page = snapshot.page().isBlank() ? snapshot.title() : snapshot.page();
		if (!page.isBlank()) {
			GuiDraw.small(
				graphics,
				font,
				page,
				windowX + 12 + GuiDraw.titleWidth(font, "WARDROBE") + 6,
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
		WardrobeMenus.Piece piece
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

	private void drawSlots(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float x = windowX + 10;
		float y = windowY + 28;
		float w = windowW - 20;
		float h = windowH - 48;
		if (!previewDrag) {
			previewYaw += dt * 18f;
			if (previewYaw > 360f || previewYaw < -360f) {
				previewYaw %= 360f;
			}
		}
		hits.add(new Hit(x, y, w, h, -1, true, false));
		List<WardrobeMenus.ArmorSet> sets = snapshot.sets();
		int shown = Math.min(9, Math.max(sets.size(), 1));
		int cols = shown <= 4 ? Math.max(1, shown) : 3;
		int rows = Math.max(1, (shown + cols - 1) / cols);
		float gap = 6f;
		float cellW = (w - gap * (cols - 1)) / cols;
		float cellH = (h - 14f - gap * (rows - 1)) / rows;
		PlayerPreview.View view = new PlayerPreview.View(viewScale, viewCx, viewCy, viewLift);
		for (int i = 0; i < shown; i++) {
			int col = i % cols;
			int row = i / cols;
			float sx = x + col * (cellW + gap);
			float sy = y + row * (cellH + gap);
			WardrobeMenus.ArmorSet set = i < sets.size() ? sets.get(i) : null;
			boolean selected = set != null && set.selected();
			boolean locked = set != null && set.locked();
			boolean hover = GuiDraw.hovered(mouseX, mouseY, sx, sy, cellW, cellH);
			int fill = hover || selected ? Theme.CARD_HOVER : Theme.CARD;
			int line = selected ? Theme.ACCENT : hover ? Theme.withAlpha(Theme.ACCENT, 160) : Theme.LINE;
			GuiDraw.panel(graphics, sx, sy, cellW, cellH, 8, fill, line);
			String mark = String.valueOf(i + 1);
			GuiDraw.small(graphics, font, mark, sx + 5, sy + 4, selected ? Theme.ACCENT : Theme.MUTED);
			if (set != null && set.hasArmor() && !locked) {
				PlayerPreview.drawMini(
					graphics,
					sx + 2,
					sy + 10,
					cellW - 4,
					cellH - 14,
					previewYaw,
					previewPitch,
					view,
					new PlayerPreview.Gear(set.helmet(), set.chest(), set.legs(), set.boots())
				);
			} else {
				String label = locked ? "Locked" : "Empty";
				GuiDraw.small(
					graphics,
					font,
					label,
					sx + (cellW - GuiDraw.smallWidth(font, label)) * 0.5f,
					sy + cellH * 0.5f - 4,
					Theme.OFF
				);
			}
			hits.add(new Hit(sx, sy, cellW, cellH, set == null ? -1 : set.slot(), false, false));
			if (hover && set != null) {
				tooltip = hoverStack(set);
			}
		}
		GuiDraw.small(graphics, font, "1-9 equip and close · Drag a model to rotate", x, y + h - 10, Theme.MUTED);
	}

	private static ItemStack hoverStack(WardrobeMenus.ArmorSet set) {
		if (set == null) {
			return ItemStack.EMPTY;
		}
		if (!set.icon().isEmpty()) {
			return set.icon();
		}
		if (!set.helmet().isEmpty()) {
			return set.helmet();
		}
		if (!set.chest().isEmpty()) {
			return set.chest();
		}
		if (!set.legs().isEmpty()) {
			return set.legs();
		}
		return set.boots();
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
		windowW = Math.min(MENU_W, Math.max(300, width - 16));
		windowH = Math.min(MENU_H, Math.max(220, height - 16));
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
		if (VoidmarkConfig.get().loadoutsOpenAnim) {
			if (Math.abs(1f - appear) < 0.003f) {
				appear = 1f;
			} else {
				appear += (1f - appear) * (1f - (float) Math.exp(-14f * dt));
			}
		} else {
			appear = 1f;
		}
	}

	private float localX(double mx) {
		return (float) ((mx - viewCx) / viewScale + viewCx);
	}

	private float localY(double my) {
		return (float) ((my - viewCy - viewLift) / viewScale + viewCy);
	}

	private void clickSlot(int slot, int button) {
		if (slot == -2) {
			onClose();
			return;
		}
		if (slot < 0) {
			return;
		}
		if (!attached()) {
			QUEUE.add(new QueuedClick(slot, button));
			return;
		}
		sendClick(vanilla, menu, slot, button);
	}

	private boolean attached() {
		return vanilla != null
			&& menu != null
			&& minecraft != null
			&& minecraft.player != null
			&& minecraft.player.containerMenu == menu;
	}

	private void flushQueue() {
		if (!attached() || QUEUE.isEmpty()) {
			return;
		}
		List<QueuedClick> pending = new ArrayList<>(QUEUE);
		QUEUE.clear();
		for (QueuedClick click : pending) {
			sendClick(vanilla, menu, click.slot, click.button);
		}
	}

	private static void flushAgainst(AbstractContainerScreen<?> chest) {
		if (chest == null || QUEUE.isEmpty()) {
			QUEUE.clear();
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.getWindow() != null) {
			chest.init(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
		}
		AbstractContainerMenu target = chest.getMenu();
		List<QueuedClick> pending = new ArrayList<>(QUEUE);
		QUEUE.clear();
		for (QueuedClick click : pending) {
			sendClick(chest, target, click.slot, click.button);
		}
	}

	private static void sendClick(AbstractContainerScreen<?> screen, AbstractContainerMenu target, int slot, int button) {
		if (screen == null || target == null || !target.isValidSlotIndex(slot)) {
			return;
		}
		Slot clicked = target.getSlot(slot);
		((AbstractContainerScreenInvoker) screen).voidmark$slotClicked(clicked, slot, button, ContainerInput.PICKUP);
	}

	private static void closeIncoming(AbstractContainerScreen<?> chest) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && chest != null && client.player.containerMenu == chest.getMenu()) {
			client.player.closeContainer();
		}
	}

	private static boolean shouldDiscardIncoming() {
		return silentFlush || cancelIncoming || System.nanoTime() < suppressUntil;
	}

	private static void suppressReopen() {
		suppressUntil = System.nanoTime() + SUPPRESS_NS;
	}

	private static Screen discardIncoming(AbstractContainerScreen<?> chest) {
		if (silentFlush) {
			silentFlush = false;
			flushAgainst(chest);
		}
		cancelIncoming = false;
		closeIncoming(chest);
		Screen current = Minecraft.getInstance().screen;
		if (current instanceof WardrobeScreen
			|| current instanceof AbstractContainerScreen<?> open && WardrobeMenus.matches(open.getTitle())) {
			return null;
		}
		return current;
	}

	private void rememberCache() {
		if (snapshot != null && snapshot.hasSets()) {
			cache = snapshot.copy();
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		double lx = localX(event.x());
		double ly = localY(event.y());
		previewDrag = false;
		pendingSlot = -1;
		dragDist = 0f;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (!hit.contains(lx, ly)) {
				continue;
			}
			if (hit.close) {
				if (event.button() == 0) {
					onClose();
				}
				return true;
			}
			if (hit.slot >= 0 && (event.button() == 0 || event.button() == 1)) {
				pendingSlot = hit.slot;
				pendingButton = event.button();
				if (event.button() == 0) {
					previewDrag = true;
				}
				return true;
			}
			if (hit.rotate && event.button() == 0) {
				previewDrag = true;
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
			dragDist += (float) (Math.abs(dx) + Math.abs(dy));
			if (dragDist > 4f) {
				pendingSlot = -1;
			}
			return true;
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (pendingSlot >= 0 && event.button() == pendingButton) {
			clickSlot(pendingSlot, pendingButton);
		}
		pendingSlot = -1;
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
		if (menu != null) {
			snapshot = WardrobeMenus.read(menu, vanilla != null ? vanilla.getTitle() : getTitle());
		}
		List<WardrobeMenus.ArmorSet> sets = snapshot.sets();
		if (index < 0 || index >= sets.size()) {
			return;
		}
		WardrobeMenus.ArmorSet set = sets.get(index);
		if (set == null || set.slot() < 0) {
			return;
		}
		clickSlot(set.slot(), 0);
		onClose();
	}

	@Override
	public void onClose() {
		if (closingMenu) {
			super.onClose();
			return;
		}
		rememberCache();
		suppressReopen();
		if (vanilla == null || menu == null) {
			if (!QUEUE.isEmpty()) {
				silentFlush = true;
			} else {
				cancelIncoming = true;
			}
			super.onClose();
			return;
		}
		if (minecraft != null && minecraft.player != null && minecraft.player.containerMenu == menu) {
			closingMenu = true;
			minecraft.player.closeContainer();
			super.onClose();
			return;
		}
		super.onClose();
	}

	@Override
	public void removed() {
		savedYaw = previewYaw;
		savedPitch = previewPitch;
		if (attaching) {
			return;
		}
		rememberCache();
		if (vanilla != null && !closingMenu) {
			vanilla.removed();
		}
	}

	private record QueuedClick(int slot, int button) {
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
