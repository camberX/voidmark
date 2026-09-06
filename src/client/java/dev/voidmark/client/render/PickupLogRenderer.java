package dev.voidmark.client.render;

import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.item.ItemIds;
import dev.voidmark.client.ui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Recent items collected by the local player. */
public final class PickupLogRenderer {
	private static final long LIFE_NS = 5_000_000_000L;
	private static final long FADE_NS = 1_000_000_000L;
	private static final int MAX_ENTRIES = 6;
	private static final float WIDTH = 150f;
	private static final float PAD = 5f;
	private static final float HEAD = 13f;
	private static final float ROW = 18f;
	private static final List<Entry> ENTRIES = new ArrayList<>();
	private static Map<String, InventoryTotal> inventory = Map.of();
	private static int playerId = Integer.MIN_VALUE;
	private static boolean inventoryReady;

	private PickupLogRenderer() {
	}

	public static void init() {
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Voidmark.id("pickup_log"),
			PickupLogRenderer::extract
		);
	}

	public static void add(ItemStack stack, int amount) {
		if (stack == null || stack.isEmpty() || amount <= 0) {
			return;
		}
		long now = System.nanoTime();
		prune(now);
		for (int i = 0; i < ENTRIES.size(); i++) {
			Entry entry = ENTRIES.get(i);
			if (ItemStack.isSameItemSameComponents(entry.stack, stack)) {
				entry.amount += amount;
				entry.time = now;
				ENTRIES.remove(i);
				ENTRIES.addFirst(entry);
				return;
			}
		}
		ENTRIES.addFirst(new Entry(stack.copyWithCount(1), stack.getStyledHoverName(), amount, now));
		while (ENTRIES.size() > MAX_ENTRIES) {
			ENTRIES.removeLast();
		}
	}

	public static void tick(Minecraft client) {
		if (client == null || client.player == null) {
			resetInventory();
			return;
		}
		Map<String, InventoryTotal> current = inventory(client.player.getInventory());
		if (inventoryReady && playerId == client.player.getId()) {
			for (Map.Entry<String, InventoryTotal> row : current.entrySet()) {
				InventoryTotal before = inventory.get(row.getKey());
				int gained = row.getValue().amount - (before == null ? 0 : before.amount);
				if (gained > 0 && VoidmarkConfig.get().pickupLogEnabled) {
					add(row.getValue().stack, gained);
				}
			}
		}
		inventory = current;
		playerId = client.player.getId();
		inventoryReady = true;
	}

	public static void clear() {
		ENTRIES.clear();
		resetInventory();
	}

	public static float drawWidth() {
		return WIDTH;
	}

	public static float drawHeight() {
		int count = visibleCount();
		if (count == 0 && HudLayout.editorOpen()) {
			count = 1;
		}
		return count == 0 ? 0f : PAD * 2 + HEAD + count * ROW;
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui || !VoidmarkConfig.get().pickupLogEnabled) {
			return;
		}
		if (visibleCount() == 0 && !HudLayout.editorOpen()) {
			return;
		}
		HudLayout.Box box = HudLayout.box(HudLayout.Id.PICKUPS, client.font, graphics.guiWidth(), graphics.guiHeight());
		draw(graphics, client, box.x(), box.y(), HudLayout.scale(HudLayout.Id.PICKUPS));
	}

	public static void draw(GuiGraphicsExtractor graphics, Minecraft client, float x, float y, float scale) {
		long now = System.nanoTime();
		prune(now);
		boolean sample = ENTRIES.isEmpty();
		int count = sample ? 1 : ENTRIES.size();
		float height = PAD * 2 + HEAD + count * ROW;

		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		if (scale != 1f) {
			graphics.pose().scale(scale, scale);
		}
		HudChrome.panel(graphics, 0, 0, WIDTH, height, 5, Theme.HUD_WINDOW, Theme.HUD_LINE);
		GuiDraw.small(graphics, client.font, "PICKUPS", PAD + 1, PAD, Theme.ACCENT);
		if (sample) {
			drawRow(graphics, client, new Entry(new ItemStack(Items.WHEAT), Component.literal("Wheat"), 3, now), 0, 255);
		} else {
			for (int i = 0; i < ENTRIES.size(); i++) {
				Entry entry = ENTRIES.get(i);
				long left = LIFE_NS - (now - entry.time);
				int alpha = left >= FADE_NS ? 255 : Math.max(0, Math.round(255f * left / FADE_NS));
				drawRow(graphics, client, entry, i, alpha);
			}
		}
		graphics.pose().popMatrix();
	}

	private static void drawRow(GuiGraphicsExtractor graphics, Minecraft client, Entry entry, int index, int alpha) {
		float y = PAD + HEAD + index * ROW;
		graphics.pose().pushMatrix();
		graphics.pose().translate(PAD + 1, y + 1);
		graphics.pose().scale(0.875f, 0.875f);
		graphics.item(client.player, entry.stack, 0, 0, 1);
		graphics.pose().popMatrix();

		int nameColor = Theme.withAlpha(Theme.TEXT, alpha);
		int countColor = Theme.withAlpha(Theme.MUTED, alpha);
		Component name = clip(client.font, entry.name, WIDTH - 54);
		GuiDraw.hud(graphics, client.font, name, PAD + 21, y + 4, nameColor);
		String amount = "×" + entry.amount;
		GuiDraw.small(
			graphics,
			client.font,
			amount,
			WIDTH - PAD - GuiDraw.smallWidth(client.font, amount),
			y + 4,
			countColor
		);
	}

	private static Component clip(Font font, Component value, float width) {
		if (GuiDraw.hudWidth(font, value) <= width) {
			return value;
		}
		String text = value.getString();
		while (text.length() > 1 && GuiDraw.hudWidth(font, Component.literal(text + "…")) > width) {
			text = text.substring(0, text.length() - 1);
		}
		return Component.literal(text + "…").withStyle(value.getStyle());
	}

	private static int visibleCount() {
		prune(System.nanoTime());
		return ENTRIES.size();
	}

	private static void prune(long now) {
		ENTRIES.removeIf(entry -> now - entry.time >= LIFE_NS);
	}

	private static Map<String, InventoryTotal> inventory(Inventory source) {
		Map<String, InventoryTotal> totals = new LinkedHashMap<>();
		for (ItemStack stack : source.getNonEquipmentItems()) {
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			String key = ItemIds.idOf(stack) + '\u0000' + stack.getHoverName().getString();
			InventoryTotal total = totals.get(key);
			if (total == null) {
				totals.put(key, new InventoryTotal(stack.copyWithCount(1), stack.getCount()));
			} else {
				total.amount += stack.getCount();
			}
		}
		return totals;
	}

	private static void resetInventory() {
		inventory = Map.of();
		playerId = Integer.MIN_VALUE;
		inventoryReady = false;
	}

	private static final class Entry {
		private final ItemStack stack;
		private final Component name;
		private int amount;
		private long time;

		private Entry(ItemStack stack, Component name, int amount, long time) {
			this.stack = stack;
			this.name = name;
			this.amount = amount;
			this.time = time;
		}
	}

	private static final class InventoryTotal {
		private final ItemStack stack;
		private int amount;

		private InventoryTotal(ItemStack stack, int amount) {
			this.stack = stack;
			this.amount = amount;
		}
	}
}
