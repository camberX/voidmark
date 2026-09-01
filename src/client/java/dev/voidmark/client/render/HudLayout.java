package dev.voidmark.client.render;

import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.ui.HudEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public final class HudLayout {
	public static final float MARGIN = 8f;
	public static final float SNAP = 6f;
	public static final float SCALE_MIN = 0.50f;
	public static final float SCALE_MAX = 2.00f;

	public enum Id {
		WATERMARK("Watermark"),
		INVENTORY("Inventory"),
		NODES("Nodes"),
		MUSIC("Music"),
		RAWMATS("Raw mats"),
		MINING("Mining"),
		HOTBAR("Hotbar"),
		HEALTH("Health"),
		HUNGER("Hunger"),
		ARMOR("Armor"),
		AIR("Air"),
		EXPERIENCE("Experience"),
		MOUNT("Mount"),
		SCOREBOARD("Scoreboard"),
		BOSS("Boss bar"),
		EFFECTS("Effects"),
		HELD_ITEM("Held item");

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
		float h = height(id, font);
		float x;
		float y;
		VoidmarkConfig config = VoidmarkConfig.get();
		VoidmarkConfig.HudSlot slot = slot(id);
		if (slot != null && placed(slot.x) && placed(slot.y)) {
			x = slot.x;
			y = slot.y;
		} else {
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
				case MUSIC -> {
					x = placed(config.hudMusicX) ? config.hudMusicX : MARGIN;
					y = placed(config.hudMusicY) ? config.hudMusicY : Math.max(MARGIN, guiH - h - 40);
				}
				case RAWMATS -> {
					x = placed(config.hudRawmatsX) ? config.hudRawmatsX : Math.max(MARGIN, guiW - w - MARGIN);
					y = placed(config.hudRawmatsY) ? config.hudRawmatsY : MARGIN;
				}
				case MINING -> {
					x = placed(config.hudMiningX) ? config.hudMiningX : MARGIN;
					float below = WatermarkRenderer.occupiedHeight();
					if (config.hudEnabled) {
						below += NodeHudRenderer.drawHeight() * scale(Id.NODES) + 4;
					}
					y = placed(config.hudMiningY) ? config.hudMiningY : MARGIN + below;
				}
				default -> {
					x = defaultX(id, font, guiW, w);
					y = defaultY(id, font, guiH, h);
				}
			}
		}
		x = Mth.clamp(x, 0f, Math.max(0f, guiW - w));
		y = Mth.clamp(y, 0f, Math.max(0f, guiH - h));
		return new Box(id, x, y, w, h);
	}

	public static void apply(GuiGraphicsExtractor graphics, Font font, Id id, Runnable draw) {
		Box box = box(id, font, graphics.guiWidth(), graphics.guiHeight());
		float scale = scale(id);
		graphics.pose().pushMatrix();
		graphics.pose().translate(box.x(), box.y());
		if (scale != 1.0f) {
			graphics.pose().scale(scale, scale);
		}
		draw.run();
		graphics.pose().popMatrix();
	}

	public static void set(Id id, float x, float y) {
		VoidmarkConfig config = VoidmarkConfig.get();
		VoidmarkConfig.HudSlot slot = slot(id);
		if (slot != null) {
			slot.x = x;
			slot.y = y;
			return;
		}
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
			case MUSIC -> {
				config.hudMusicX = x;
				config.hudMusicY = y;
			}
			case RAWMATS -> {
				config.hudRawmatsX = x;
				config.hudRawmatsY = y;
			}
			case MINING -> {
				config.hudMiningX = x;
				config.hudMiningY = y;
			}
			default -> {
			}
		}
	}

	public static float scale(Id id) {
		VoidmarkConfig config = VoidmarkConfig.get();
		VoidmarkConfig.HudSlot slot = slot(id);
		if (slot != null) {
			return VoidmarkConfig.clampHudScale(slot.scale);
		}
		return switch (id) {
			case WATERMARK -> VoidmarkConfig.clampHudScale(config.hudWatermarkScale);
			case INVENTORY -> VoidmarkConfig.clampHudScale(config.inventoryHudScale);
			case NODES -> VoidmarkConfig.clampHudScale(config.hudNodesScale);
			case MUSIC -> VoidmarkConfig.clampHudScale(config.hudMusicScale);
			case RAWMATS -> VoidmarkConfig.clampHudScale(config.hudRawmatsScale);
			case MINING -> VoidmarkConfig.clampHudScale(config.hudMiningScale);
			default -> 1.0f;
		};
	}

	public static void setScale(Id id, float scale) {
		float value = VoidmarkConfig.clampHudScale(scale);
		VoidmarkConfig config = VoidmarkConfig.get();
		VoidmarkConfig.HudSlot slot = slot(id);
		if (slot != null) {
			slot.scale = value;
			return;
		}
		switch (id) {
			case WATERMARK -> config.hudWatermarkScale = value;
			case INVENTORY -> config.inventoryHudScale = value;
			case NODES -> config.hudNodesScale = value;
			case MUSIC -> config.hudMusicScale = value;
			case RAWMATS -> config.hudRawmatsScale = value;
			case MINING -> config.hudMiningScale = value;
			default -> {
			}
		}
	}

	public static void reset(Id id) {
		VoidmarkConfig config = VoidmarkConfig.get();
		VoidmarkConfig.HudSlot slot = slot(id);
		if (slot != null) {
			slot.x = -1f;
			slot.y = -1f;
			return;
		}
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
			case MUSIC -> {
				config.hudMusicX = -1f;
				config.hudMusicY = -1f;
			}
			case RAWMATS -> {
				config.hudRawmatsX = -1f;
				config.hudRawmatsY = -1f;
			}
			case MINING -> {
				config.hudMiningX = -1f;
				config.hudMiningY = -1f;
			}
			default -> {
			}
		}
	}

	public static boolean enabled(Id id) {
		VoidmarkConfig config = VoidmarkConfig.get();
		return switch (id) {
			case WATERMARK -> config.watermarkEnabled;
			case INVENTORY -> config.inventoryHudEnabled;
			case NODES -> config.hudEnabled;
			case MUSIC -> config.musicHudEnabled;
			case RAWMATS -> config.rawmatsHudEnabled;
			case MINING -> config.miningHudEnabled;
			case HOTBAR -> config.hudHotbar;
			case HEALTH -> config.hudHealth;
			case HUNGER -> config.hudHunger;
			case ARMOR -> config.hudArmor;
			case AIR -> config.hudAir;
			case EXPERIENCE -> config.hudExperience;
			case MOUNT -> config.hudMountHealth;
			case SCOREBOARD -> config.hudScoreboard;
			case BOSS -> config.hudBossBar;
			case EFFECTS -> config.hudEffects;
			case HELD_ITEM -> config.hudHeldItem;
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

	private static VoidmarkConfig.HudSlot slot(Id id) {
		VoidmarkConfig config = VoidmarkConfig.get();
		return switch (id) {
			case HOTBAR -> config.slotHotbar;
			case HEALTH -> config.slotHealth;
			case HUNGER -> config.slotHunger;
			case ARMOR -> config.slotArmor;
			case AIR -> config.slotAir;
			case EXPERIENCE -> config.slotExperience;
			case MOUNT -> config.slotMount;
			case SCOREBOARD -> config.slotScoreboard;
			case BOSS -> config.slotBoss;
			case EFFECTS -> config.slotEffects;
			case HELD_ITEM -> config.slotHeldItem;
			default -> null;
		};
	}

	private static float defaultX(Id id, Font font, int guiW, float w) {
		return switch (id) {
			case HOTBAR -> (guiW - w) * 0.5f;
			case HEALTH, ARMOR -> guiW * 0.5f - 91;
			case HUNGER, AIR, MOUNT -> guiW * 0.5f + 91 - w;
			case EXPERIENCE, HELD_ITEM -> (guiW - w) * 0.5f;
			case SCOREBOARD, EFFECTS -> guiW - w - MARGIN;
			case BOSS -> (guiW - w) * 0.5f;
			default -> MARGIN;
		};
	}

	private static float defaultY(Id id, Font font, int guiH, float h) {
		float hotbar = guiH - HotbarHudRenderer.HEIGHT - 3;
		float xp = hotbar - 1 - StatusHudRenderer.XP_BOX_H * scale(Id.EXPERIENCE);
		float status = xp - 4 - StatusHudRenderer.BAR_H * scale(Id.HEALTH);
		return switch (id) {
			case HOTBAR -> hotbar;
			case EXPERIENCE -> xp;
			case HEALTH, HUNGER, MOUNT -> status;
			case ARMOR, AIR -> status - 3 - h;
			case HELD_ITEM -> status - 6 - h;
			case SCOREBOARD, EFFECTS -> MARGIN;
			case BOSS -> MARGIN;
			default -> MARGIN;
		};
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
		float scale = scale(id);
		return switch (id) {
			case WATERMARK -> WatermarkRenderer.width(font) * scale;
			case INVENTORY -> InventoryHudRenderer.drawWidth() * scale;
			case NODES -> NodeHudRenderer.drawWidth() * scale;
			case MUSIC -> MusicHudRenderer.drawWidth() * scale;
			case RAWMATS -> RawmatsHudRenderer.drawWidth() * scale;
			case MINING -> MiningHudRenderer.drawWidth() * scale;
			case HOTBAR -> HotbarHudRenderer.drawWidth() * scale;
			case HEALTH, HUNGER, ARMOR, AIR, MOUNT -> StatusHudRenderer.BAR_W * scale;
			case EXPERIENCE -> StatusHudRenderer.xpWidth() * scale;
			case SCOREBOARD -> ScoreboardHudRenderer.drawWidth(font) * scale;
			case BOSS -> BossBarHudRenderer.drawWidth() * scale;
			case EFFECTS -> EffectsHudRenderer.drawWidth(font) * scale;
			case HELD_ITEM -> HeldItemHudRenderer.drawWidth(font) * scale;
		};
	}

	private static float height(Id id, Font font) {
		float scale = scale(id);
		return switch (id) {
			case WATERMARK -> WatermarkRenderer.HEIGHT * scale;
			case INVENTORY -> InventoryHudRenderer.drawHeight() * scale;
			case NODES -> NodeHudRenderer.drawHeight() * scale;
			case MUSIC -> MusicHudRenderer.drawHeight() * scale;
			case RAWMATS -> RawmatsHudRenderer.drawHeight() * scale;
			case MINING -> MiningHudRenderer.drawHeight() * scale;
			case HOTBAR -> HotbarHudRenderer.HEIGHT * scale;
			case HEALTH, HUNGER, ARMOR, AIR, MOUNT -> StatusHudRenderer.BAR_H * scale;
			case EXPERIENCE -> StatusHudRenderer.XP_BOX_H * scale;
			case SCOREBOARD -> ScoreboardHudRenderer.drawHeight(font) * scale;
			case BOSS -> BossBarHudRenderer.drawHeight() * scale;
			case EFFECTS -> EffectsHudRenderer.drawHeight() * scale;
			case HELD_ITEM -> HeldItemHudRenderer.HEIGHT * scale;
		};
	}

	private static boolean placed(float value) {
		return value >= 0f;
	}

	private record Guide(float value, float line) {
	}
}
