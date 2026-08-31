package dev.voidmark.client.render;

import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.ui.HudEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public final class HudLayout {
	public static final float MARGIN = 8f;
	public static final float SNAP = 6f;

	public enum Id {
		WATERMARK("Watermark"),
		INVENTORY("Inventory"),
		NODES("Nodes");

		public final String label;

		Id(String label) {
			this.label = label;
		}
	}

	public record Box(Id id, float x, float y, float w, float h) {
		public float cx() {
			return x + w * 0.5f;
		}

		public float cy() {
			return y + h * 0.5f;
		}

		public float right() {
			return x + w;
		}

		public float bottom() {
			return y + h;
		}

		public boolean contains(double mx, double my) {
			return mx >= x && mx <= x + w && my >= y && my <= y + h;
		}
	}

	public record Snap(float x, float y, Float vLine, Float hLine) {
	}

	private HudLayout() {
	}

	public static boolean editorOpen() {
		return Minecraft.getInstance().screen instanceof HudEditorScreen;
	}

	public static List<Box> boxes(Font font, int guiW, int guiH) {
		List<Box> out = new ArrayList<>();
		for (Id id : Id.values()) {
			out.add(box(id, font, guiW, guiH));
		}
		return out;
	}

	public static Box box(Id id, Font font, int guiW, int guiH) {
		float w = width(id, font);
		float h = height(id);
		float x;
		float y;
		VoidmarkConfig config = VoidmarkConfig.get();
		switch (id) {
			case WATERMARK -> {
				x = placed(config.hudWatermarkX) ? config.hudWatermarkX : MARGIN;
				y = placed(config.hudWatermarkY) ? config.hudWatermarkY : MARGIN;
			}
			case INVENTORY -> {
				if (placed(config.hudInventoryX) && placed(config.hudInventoryY)) {
					x = config.hudInventoryX;
					y = config.hudInventoryY;
				} else {
					x = InventoryHudRenderer.defaultX(guiW, w, config.inventoryHudAnchor);
					y = InventoryHudRenderer.defaultY(guiH, h, config.inventoryHudAnchor);
				}
			}
			case NODES -> {
				x = placed(config.hudNodesX) ? config.hudNodesX : MARGIN;
				float belowMark = WatermarkRenderer.occupiedHeight();
				y = placed(config.hudNodesY) ? config.hudNodesY : MARGIN + belowMark;
			}
			default -> {
				x = MARGIN;
				y = MARGIN;
			}
		}
		x = Mth.clamp(x, 0f, Math.max(0f, guiW - w));
		y = Mth.clamp(y, 0f, Math.max(0f, guiH - h));
		return new Box(id, x, y, w, h);
	}

	public static void set(Id id, float x, float y) {
		VoidmarkConfig config = VoidmarkConfig.get();
		switch (id) {
			case WATERMARK -> {
				config.hudWatermarkX = x;
				config.hudWatermarkY = y;
			}
			case INVENTORY -> {
				config.hudInventoryX = x;
				config.hudInventoryY = y;
			}
			case NODES -> {
				config.hudNodesX = x;
				config.hudNodesY = y;
			}
		}
	}

	public static void reset(Id id) {
		VoidmarkConfig config = VoidmarkConfig.get();
		switch (id) {
			case WATERMARK -> {
				config.hudWatermarkX = -1f;
				config.hudWatermarkY = -1f;
			}
			case INVENTORY -> {
				config.hudInventoryX = -1f;
				config.hudInventoryY = -1f;
			}
			case NODES -> {
				config.hudNodesX = -1f;
				config.hudNodesY = -1f;
			}
		}
	}

	public static boolean enabled(Id id) {
		VoidmarkConfig config = VoidmarkConfig.get();
		return switch (id) {
			case WATERMARK -> config.watermarkEnabled;
			case INVENTORY -> config.inventoryHudEnabled;
			case NODES -> config.hudEnabled;
		};
	}

	public static Snap snap(float x, float y, float w, float h, Id moving, List<Box> others, int guiW, int guiH, boolean free) {
		x = Mth.clamp(x, 0f, Math.max(0f, guiW - w));
		y = Mth.clamp(y, 0f, Math.max(0f, guiH - h));
		if (free) {
			return new Snap(x, y, null, null);
		}
		List<Guide> vertical = new ArrayList<>();
		List<Guide> horizontal = new ArrayList<>();
		addV(vertical, MARGIN, MARGIN);
		addV(vertical, guiW - w - MARGIN, guiW - MARGIN);
		addV(vertical, (guiW - w) * 0.5f, guiW * 0.5f);
		addH(horizontal, MARGIN, MARGIN);
		addH(horizontal, guiH - h - MARGIN, guiH - MARGIN);
		addH(horizontal, (guiH - h) * 0.5f, guiH * 0.5f);
		for (Box other : others) {
			if (other.id() == moving) {
				continue;
			}
			addV(vertical, other.x(), other.x());
			addV(vertical, other.right() - w, other.right());
			addV(vertical, other.cx() - w * 0.5f, other.cx());
			addV(vertical, other.right(), other.right());
			addV(vertical, other.x() - w, other.x());
			addH(horizontal, other.y(), other.y());
			addH(horizontal, other.bottom() - h, other.bottom());
			addH(horizontal, other.cy() - h * 0.5f, other.cy());
			addH(horizontal, other.bottom(), other.bottom());
			addH(horizontal, other.y() - h, other.y());
		}
		Float vLine = null;
		Float hLine = null;
		Guide bestV = nearest(vertical, x);
		if (bestV != null) {
			x = bestV.value;
			vLine = bestV.line;
		}
		Guide bestH = nearest(horizontal, y);
		if (bestH != null) {
			y = bestH.value;
			hLine = bestH.line;
		}
		x = Mth.clamp(x, 0f, Math.max(0f, guiW - w));
		y = Mth.clamp(y, 0f, Math.max(0f, guiH - h));
		return new Snap(x, y, vLine, hLine);
	}

	private static void addV(List<Guide> list, float value, float line) {
		list.add(new Guide(value, line));
	}

	private static void addH(List<Guide> list, float value, float line) {
		list.add(new Guide(value, line));
	}

	private static Guide nearest(List<Guide> guides, float current) {
		Guide best = null;
		float bestDist = SNAP;
		for (Guide guide : guides) {
			float dist = Math.abs(guide.value - current);
			if (dist <= bestDist) {
				bestDist = dist;
				best = guide;
			}
		}
		return best;
	}

	private static float width(Id id, Font font) {
		return switch (id) {
			case WATERMARK -> WatermarkRenderer.width(font);
			case INVENTORY -> InventoryHudRenderer.drawWidth();
			case NODES -> NodeHudRenderer.drawWidth();
		};
	}

	private static float height(Id id) {
		return switch (id) {
			case WATERMARK -> WatermarkRenderer.HEIGHT;
			case INVENTORY -> InventoryHudRenderer.drawHeight();
			case NODES -> NodeHudRenderer.drawHeight();
		};
	}

	private static boolean placed(float value) {
		return value >= 0f;
	}

	private record Guide(float value, float line) {
	}
}
