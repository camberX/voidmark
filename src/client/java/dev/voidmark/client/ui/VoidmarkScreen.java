package dev.voidmark.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmark.client.config.UnloadState;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.location.SkyblockLocation;
import dev.voidmark.client.node.EnderNodeTracker;
import dev.voidmark.client.render.GuiDraw;
import dev.voidmark.client.render.HudStats;
import dev.voidmark.client.render.MobCatalog;
import dev.voidmark.client.render.MobGlowRenderer;
import dev.voidmark.client.render.Starfield;
import dev.voidmark.client.visual.CustomCape;
import dev.voidmark.client.visual.NickHider;
import dev.voidmark.client.visual.WorldTint;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

public class VoidmarkScreen extends Screen {
	private static final float MENU_W = 400;
	private static final float MENU_H = 294;
	private static final float SIDEBAR_W = 88;
	private static final float TOOLBAR_H = 22;
	private static final float ROW = 16;
	private static final float COL_GAP = 10;
	private static final float PAD = 8;
	private static final float CARD_PAD = 8;
	private static final float CARD_HEAD = 20;
	private static final float ACTION_W = 54;
	private static final float RESET_W = 44;
	private static final float ICON_SLOT = 14;
	private static final float PICKER_W = 132;
	private static final float PICKER_H = 122;
	private static final float PANEL_W = 168;
	private static final float SETTINGS_H = 176;

	private enum Group {
		VISUALS("VISUALS"),
		NODES("NODES"),
		MISC("MISCELLANEOUS");

		final String label;

		Group(String label) {
			this.label = label;
		}
	}

	private enum Tab {
		WORLD("World", Group.VISUALS),
		VIEW("View", Group.VISUALS),
		FOG("Fog", Group.VISUALS),
		MOBS("Mobs", Group.VISUALS),
		MARKERS("Markers", Group.NODES),
		DISPLAY("Display", Group.NODES),
		HUD("HUD", Group.NODES),
		STATUS("Status", Group.MISC),
		INVENTORY("Inventory", Group.MISC),
		NICK("Nick", Group.MISC),
		CAPE("Cape", Group.MISC);

		final String label;
		final Group group;

		Tab(String label, Group group) {
			this.label = label;
			this.group = group;
		}
	}

	private enum PickerTarget {
		WORLD, SKY, FOG, NODE, THEME, PANE, MOB
	}

	private record SearchEntry(String label, Tab tab, String hint) {
	}

	private static final SearchEntry[] SEARCH = {
		new SearchEntry("World tint", Tab.WORLD, "Blocks"),
		new SearchEntry("Lightmap", Tab.WORLD, "Mode"),
		new SearchEntry("Shader", Tab.WORLD, "Mode"),
		new SearchEntry("Skybox tint", Tab.WORLD, "Skybox"),
		new SearchEntry("Aspect ratio", Tab.VIEW, "Camera"),
		new SearchEntry("Custom fog", Tab.FOG, "Fog"),
		new SearchEntry("Mob glow", Tab.MOBS, "ESP"),
		new SearchEntry("Block outline", Tab.MOBS, "ESP"),
		new SearchEntry("Mobs", Tab.MOBS, "ESP"),
		new SearchEntry("Markers", Tab.MARKERS, "Nodes"),
		new SearchEntry("Filled box", Tab.DISPLAY, "ESP"),
		new SearchEntry("Watermark", Tab.DISPLAY, "HUD"),
		new SearchEntry("Node HUD", Tab.DISPLAY, "HUD"),
		new SearchEntry("Music HUD", Tab.DISPLAY, "HUD"),
		new SearchEntry("Raw mats", Tab.DISPLAY, "HUD"),
		new SearchEntry("Enchanted materials", Tab.DISPLAY, "HUD"),
		new SearchEntry("Spotify", Tab.DISPLAY, "Music"),
		new SearchEntry("YouTube Music", Tab.DISPLAY, "Music"),
		new SearchEntry("Hotbar", Tab.HUD, "Vanilla HUD"),
		new SearchEntry("Health", Tab.HUD, "Vanilla HUD"),
		new SearchEntry("Hunger", Tab.HUD, "Vanilla HUD"),
		new SearchEntry("Armor bar", Tab.HUD, "Vanilla HUD"),
		new SearchEntry("Air", Tab.HUD, "Vanilla HUD"),
		new SearchEntry("Experience", Tab.HUD, "Vanilla HUD"),
		new SearchEntry("Scoreboard", Tab.HUD, "Vanilla HUD"),
		new SearchEntry("Boss bar", Tab.HUD, "Vanilla HUD"),
		new SearchEntry("Effects", Tab.HUD, "Vanilla HUD"),
		new SearchEntry("Held item", Tab.HUD, "Vanilla HUD"),
		new SearchEntry("Mount health", Tab.HUD, "Vanilla HUD"),
		new SearchEntry("Inventory HUD", Tab.INVENTORY, "Inventory"),
		new SearchEntry("Hotbar", Tab.INVENTORY, "Inventory"),
		new SearchEntry("Armor", Tab.INVENTORY, "Inventory"),
		new SearchEntry("Item count", Tab.INVENTORY, "Inventory"),
		new SearchEntry("Pane opacity", Tab.DISPLAY, "Theme"),
		new SearchEntry("FPS", Tab.STATUS, "Stats"),
		new SearchEntry("Ping", Tab.STATUS, "Stats"),
		new SearchEntry("Hypixel", Tab.STATUS, "Server"),
		new SearchEntry("Nick hider", Tab.NICK, "Name"),
		new SearchEntry("Nametags", Tab.NICK, "Players"),
		new SearchEntry("Nametag size", Tab.NICK, "Players"),
		new SearchEntry("Cape", Tab.CAPE, "Texture")
	};

	private final List<Hit> hits = new ArrayList<>();
	private final Map<String, Float> anims = new HashMap<>();
	private Tab tab = Tab.WORLD;
	private PickerTarget pickerTarget;
	private float pickerHue = 200f;
	private float pickerSat = 0.82f;
	private float pickerVal = 1f;
	private float pickerX;
	private float pickerY;
	private double lastClickY;
	private boolean settingsOpen;
	private boolean bellOpen;
	private boolean searchOpen;
	private boolean capeFocused;
	private boolean nickFocused;
	private String searchQuery = "";
	private String capeUrlDraft = "";
	private boolean dragging;
	private boolean moved;
	private double dragOffX;
	private double dragOffY;
	private boolean placed;
	private long lastNs = System.nanoTime();
	private float dt = 0.016f;
	private float appear;
	private boolean closing;
	private boolean finishedClose;
	private float navY = -1f;
	private float settingsT;
	private float bellT;
	private float searchT;
	private float pickerT;
	private float settingsX;
	private float settingsY;
	private float bellX;
	private float bellY;
	private float searchFieldX;
	private float searchFieldW;
	private float capeFieldX;
	private float capeFieldY;
	private float capeFieldW;
	private float nickFieldX;
	private float nickFieldY;
	private float nickFieldW;
	private boolean mobSearchFocused;
	private String mobQuery = "";
	private float mobScroll;
	private float mobListX;
	private float mobListY;
	private float mobListW;
	private float mobListH;
	private float mobFieldX;
	private float mobFieldY;
	private float mobFieldW;
	private boolean ensureMobVisible;

	private float windowX;
	private float windowY;
	private float windowW = MENU_W;
	private float windowH = MENU_H;

	public VoidmarkScreen() {
		super(Component.literal("Voidmark"));
		tab = parseTab(VoidmarkConfig.get().menuTab);
		String url = VoidmarkConfig.get().capeUrl;
		if (url != null && !url.isBlank()) {
			capeUrlDraft = url;
		} else {
			String path = VoidmarkConfig.get().capePath;
			capeUrlDraft = path == null ? "" : path;
		}
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
		tickAnim();
		hits.clear();
		if (closing && appear <= 0.02f) {
			finishClose();
			return;
		}
		Font font = minecraft.font;
		layout();

		int dim = Anim.fade(0x14000000, appear);
		GuiDraw.fill(graphics, 0, 0, width, height, dim);

		float scale = 0.92f + 0.08f * appear;
		float lift = (1f - appear) * 12f;
		float cx = windowX + windowW * 0.5f;
		float cy = windowY + windowH * 0.5f;
		graphics.pose().pushMatrix();
		graphics.pose().translate(cx, cy + lift);
		graphics.pose().scale(scale, scale);
		graphics.pose().translate(-cx, -cy);

		boolean chromeClip = GuiDraw.scissor(graphics, windowX, windowY, windowW, windowH);
		GuiDraw.rounded(graphics, windowX, windowY, windowW, windowH, Theme.WINDOW_RADIUS, Theme.WINDOW);
		GuiDraw.roundLeft(graphics, windowX, windowY, SIDEBAR_W, windowH, Theme.WINDOW_RADIUS, Theme.SIDEBAR);
		Starfield.draw(graphics, windowX + SIDEBAR_W, windowY, windowW - SIDEBAR_W, windowH, Theme.WINDOW_RADIUS, appear);
		GuiDraw.fill(graphics, windowX + SIDEBAR_W, windowY, 1, windowH, Theme.withAlpha(Theme.ACCENT, 90));
		if (chromeClip) {
			GuiDraw.disableScissor(graphics);
		}

		drawSidebar(graphics, font, mouseX, mouseY);
		drawToolbar(graphics, font, mouseX, mouseY);
		drawColumns(graphics, font, mouseX, mouseY);
		if (searchT > 0.02f && !searchQuery.isBlank()) {
			drawSearchResults(graphics, font, mouseX, mouseY);
		}
		if (settingsT > 0.02f) {
			drawSettings(graphics, font, mouseX, mouseY);
		}
		if (bellT > 0.02f) {
			drawBell(graphics, font, mouseX, mouseY);
		}
		if (pickerT > 0.02f && pickerTarget != null) {
			drawPicker(graphics, font);
		}
		graphics.pose().popMatrix();
		if (closing || appear < 0.88f) {
			hits.clear();
		}
	}

	private void tickAnim() {
		long now = System.nanoTime();
		dt = Math.min(0.05f, (now - lastNs) / 1_000_000_000f);
		lastNs = now;
		appear = Anim.exp(appear, closing ? 0f : 1f, closing ? 16f : 13f, dt);
		settingsT = Anim.exp(settingsT, settingsOpen ? 1f : 0f, 18f, dt);
		bellT = Anim.exp(bellT, bellOpen ? 1f : 0f, 18f, dt);
		searchT = Anim.exp(searchT, searchOpen ? 1f : 0f, 18f, dt);
		pickerT = Anim.exp(pickerT, pickerTarget != null ? 1f : 0f, 18f, dt);
	}

	private float anim(String key, float target) {
		float current = anims.getOrDefault(key, target);
		float next = Anim.exp(current, target, 16f, dt);
		anims.put(key, next);
		return next;
	}

	private void layout() {
		windowW = Math.min(MENU_W, Math.max(1, width - 16));
		windowH = Math.min(MENU_H, Math.max(1, height - 16));
		if (!placed) {
			VoidmarkConfig config = VoidmarkConfig.get();
			if (config.menuPlaced) {
				windowX = config.menuX;
				windowY = config.menuY;
			} else {
				windowX = (width - windowW) / 2f;
				windowY = (height - windowH) / 2f;
			}
			placed = true;
		}
		windowX = Mth.clamp(windowX, 4, Math.max(4, width - windowW - 4));
		windowY = Mth.clamp(windowY, 4, Math.max(4, height - windowH - 4));
	}

	private float contentX() {
		return windowX + SIDEBAR_W + PAD;
	}

	private float contentW() {
		return windowW - SIDEBAR_W - PAD * 2;
	}

	private float colW() {
		return (contentW() - COL_GAP) / 2f;
	}

	private void drawSidebar(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		GuiDraw.title(graphics, font, "VOIDMARK", windowX + 10, windowY + 8, Theme.TEXT);
		GuiDraw.small(graphics, font, "v" + modVersion(), windowX + 10 + GuiDraw.titleWidth(font, "VOIDMARK") + 3, windowY + 10, Theme.ACCENT);
		GuiDraw.rounded(graphics, windowX + 10, windowY + 20, 16, 2, 1, Theme.ACCENT);
		hits.add(new Hit(windowX, windowY, SIDEBAR_W, 26, mx -> startDrag(mx, lastClickY), true));

		float y = windowY + 30;
		Group last = null;
		float activeY = y;
		for (Tab value : Tab.values()) {
			if (value.group != last) {
				y += 5;
				GuiDraw.small(graphics, font, value.group.label, windowX + 10, y, Theme.HEADER);
				y += 10;
				last = value.group;
			}
			if (tab == value) {
				activeY = y;
			}
			boolean hovered = GuiDraw.hovered(mouseX, mouseY, windowX + 6, y, SIDEBAR_W - 12, 16);
			float hover = anim("navh-" + value.name(), hovered && tab != value ? 1f : 0f);
			if (hover > 0.02f) {
				GuiDraw.rounded(graphics, windowX + 6, y, SIDEBAR_W - 12, 16, 8, Anim.fade(0x18FFFFFF, hover));
			}
			float labelY = GuiDraw.middle(y, 16);
			GuiDraw.icon(graphics, font, tabGlyph(value), windowX + 11, labelY, Theme.ACCENT);
			GuiDraw.menu(graphics, font, value.label, windowX + 24, labelY, Theme.MUTED);
			hits.add(new Hit(windowX + 6, y, SIDEBAR_W - 12, 16, () -> selectTab(value)));
			y += 17;
		}
		if (navY < 0f) {
			navY = activeY;
		}
		navY = Anim.exp(navY, activeY, 18f, dt);
		GuiDraw.rounded(graphics, windowX + 6, navY, SIDEBAR_W - 12, 16, 8, Theme.NAV_PILL);
		// redraw the active tab label over the sliding pill
		float labelY = GuiDraw.middle(navY, 16);
		GuiDraw.icon(graphics, font, tabGlyph(tab), windowX + 11, labelY, Theme.TEXT);
		GuiDraw.menu(graphics, font, tab.label, windowX + 24, labelY, Theme.TEXT);

		float footY = windowY + windowH - 20;
		int face = 14;
		int faceX = Math.round(windowX + 10);
		int faceY = Math.round(footY);
		PlayerSkin skin = playerSkin();
		if (skin != null && skin.body() != null) {
			PlayerFaceExtractor.extractRenderState(graphics, skin, faceX, faceY, face);
		} else {
			GuiDraw.rounded(graphics, faceX, faceY, face, face, 3, Theme.ACCENT);
		}
		float nameX = windowX + 10 + face + 4;
		GuiDraw.menu(graphics, font, fitName(font, playerName(), (int) (SIDEBAR_W - 18 - face)), nameX, GuiDraw.middle(footY, face), Theme.TEXT);
	}

	private void selectTab(Tab value) {
		tab = value;
		pickerTarget = null;
		searchOpen = false;
		capeFocused = tab == Tab.CAPE;
		nickFocused = tab == Tab.NICK;
		mobSearchFocused = false;
		if (value == Tab.MOBS) {
			ensureMobVisible = true;
		}
		commitCapeUrl();
		VoidmarkConfig config = VoidmarkConfig.get();
		config.menuTab = value.name();
		config.save();
	}

	private static Tab parseTab(String name) {
		try {
			return Tab.valueOf(VoidmarkConfig.normalizeMenuTab(name));
		} catch (IllegalArgumentException ignored) {
			return Tab.WORLD;
		}
	}

	private void drawMobsTab(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float left = contentX();
		float top = windowY + TOOLBAR_H + 6;
		float col = colW();
		float right = left + col + COL_GAP;
		float ix = innerX(left);
		float rx = innerX(right);
		float iw = innerW(col);
		VoidmarkConfig config = VoidmarkConfig.get();
		config.normalizeMobGlowIds();

		float y = featureCard(graphics, font, left, top, col, cardHeight(7), "Glow");
		y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Enable", config.mobGlowEnabled, v -> config.mobGlowEnabled = v);
		y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Through walls", config.mobGlowThroughWalls, v -> config.mobGlowThroughWalls = v);
		y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Block outline", config.blockOutlineGlow, v -> config.blockOutlineGlow = v);
		y = slider(graphics, font, ix, y, iw, "Size", String.format(Locale.ROOT, "%.0f", config.mobGlowSize * 100), (config.mobGlowSize - 0.12f) / 1.08f, v -> config.mobGlowSize = VoidmarkConfig.clamp(0.12f + v * 1.08f, 0.12f, 1.20f));
		y = slider(graphics, font, ix, y, iw, "Opacity", Math.round(config.mobGlowOpacity * 100) + "%", (config.mobGlowOpacity - 0.15f) / 0.75f, v -> config.mobGlowOpacity = VoidmarkConfig.clamp(0.15f + v * 0.75f, 0.15f, 0.90f));
		y = colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Color", config.mobGlowRgb, PickerTarget.MOB);
		String selected = clip(font, MobCatalog.displayNames(config.mobGlowIds), (int) iw - 4);
		GuiDraw.menu(graphics, font, selected, ix + 1, GuiDraw.middle(y, ROW), Theme.ACCENT);

		List<MobCatalog.Entry> entries = MobCatalog.filtered(mobQuery);
		float listH = windowY + windowH - PAD - top;
		featureCard(graphics, font, right, top, col, listH, entries.isEmpty() ? "Mobs" : "Mobs  " + entries.size());
		float searchY = top + CARD_HEAD;
		mobFieldX = rx;
		mobFieldY = searchY;
		mobFieldW = iw;
		boolean hoverSearch = GuiDraw.hovered(mouseX, mouseY, mobFieldX, mobFieldY, mobFieldW, 14);
		GuiDraw.panel(graphics, mobFieldX, mobFieldY, mobFieldW, 14, 5, mobSearchFocused || hoverSearch ? Theme.CARD_HOVER : Theme.PANEL, mobSearchFocused ? Theme.ACCENT : Theme.LINE);
		String shown = mobQuery.isEmpty() && !mobSearchFocused ? "Search mobs..." : mobQuery + (mobSearchFocused ? "|" : "");
		GuiDraw.menu(graphics, font, clip(font, shown, (int) mobFieldW - 10), mobFieldX + 5, GuiDraw.middle(mobFieldY, 14), mobQuery.isEmpty() && !mobSearchFocused ? Theme.MUTED : Theme.TEXT);
		hits.add(new Hit(mobFieldX, mobFieldY, mobFieldW, 14, () -> {
			mobSearchFocused = true;
			capeFocused = false;
			nickFocused = false;
			searchOpen = false;
		}));

		mobListX = rx;
		mobListY = searchY + 18;
		mobListW = iw;
		mobListH = Math.max(16, listH - CARD_HEAD - 22);
		float contentH = entries.size() * ROW;
		float maxScroll = Math.max(0f, contentH - mobListH);
		if (ensureMobVisible) {
			for (int i = 0; i < entries.size(); i++) {
				if (config.isMobGlowSelected(entries.get(i).id().toString())) {
					mobScroll = Mth.clamp(i * ROW - mobListH * 0.4f, 0f, maxScroll);
					break;
				}
			}
			ensureMobVisible = false;
		}
		mobScroll = Mth.clamp(mobScroll, 0f, maxScroll);

		boolean clipped = GuiDraw.scissor(graphics, mobListX, mobListY, mobListW, mobListH);
		if (entries.isEmpty()) {
			GuiDraw.menu(graphics, font, "No matching mobs", mobListX + 2, GuiDraw.middle(mobListY, mobListH), Theme.MUTED);
		} else {
			int first = (int) (mobScroll / ROW);
			int last = Math.min(entries.size() - 1, first + (int) (mobListH / ROW) + 1);
			for (int i = first; i <= last; i++) {
				MobCatalog.Entry entry = entries.get(i);
				float iy = mobListY + i * ROW - mobScroll;
				boolean on = config.isMobGlowSelected(entry.id().toString());
				boolean hover = GuiDraw.hovered(mouseX, mouseY, mobListX, iy, mobListW, ROW)
					&& GuiDraw.hovered(mouseX, mouseY, mobListX, mobListY, mobListW, mobListH);
				if (on) {
					GuiDraw.rounded(graphics, mobListX - 2, iy, mobListW + 4, ROW, 5, Theme.withAlpha(Theme.ACCENT, 38));
					GuiDraw.rounded(graphics, mobListX - 2, iy + 3, 2, ROW - 6, 1, Theme.ACCENT);
				} else if (hover) {
					GuiDraw.rounded(graphics, mobListX - 2, iy, mobListW + 4, ROW, 5, 0x10FFFFFF);
				}
				GuiDraw.menu(graphics, font, clip(font, entry.name(), (int) mobListW - 8), mobListX + 6, GuiDraw.middle(iy, ROW), on ? Theme.TEXT : Theme.HEADER);
				float hitY = Math.max(iy, mobListY);
				float hitB = Math.min(iy + ROW, mobListY + mobListH);
				if (hitB - hitY >= 3f) {
					hits.add(new Hit(mobListX, hitY, mobListW, hitB - hitY, () -> {
						config.toggleMobGlow(entry.id().toString());
						UnloadState.markDirty();
					}));
				}
			}
		}
		if (clipped) {
			GuiDraw.disableScissor(graphics);
		}

		if (maxScroll > 1f) {
			float trackX = right + col - 5;
			float trackY = mobListY;
			float trackH = mobListH;
			GuiDraw.rounded(graphics, trackX, trackY, 2.4f, trackH, 1.2f, Theme.TRACK);
			float thumbH = Math.max(14f, trackH * trackH / (trackH + maxScroll));
			float thumbY = trackY + (mobScroll / maxScroll) * (trackH - thumbH);
			GuiDraw.rounded(graphics, trackX - 0.4f, thumbY, 3.2f, thumbH, 1.6f, Theme.ACCENT);
		}

		int nearby = config.mobGlowEnabled ? MobGlowRenderer.nearbyCount() : 0;
		GuiDraw.small(graphics, font, nearby == 0 ? "None nearby" : nearby + " nearby", ix + 1, top + cardHeight(7) + 6, Theme.MUTED);
		GuiDraw.small(graphics, font, "Click a type to add it, again to drop it.", ix + 1, top + cardHeight(7) + 16, Theme.MUTED);
	}

	private void drawCapeTab(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float left = contentX();
		float top = windowY + TOOLBAR_H + 6;
		float col = colW();
		float right = left + col + COL_GAP;
		float ix = innerX(left);
		float rx = innerX(right);
		float iw = innerW(col);

		if (!capeFocused) {
			VoidmarkConfig config = VoidmarkConfig.get();
			if (config.capeUrl != null && !config.capeUrl.isBlank()) {
				capeUrlDraft = config.capeUrl;
			} else if (config.capePath != null && !config.capePath.isBlank()) {
				capeUrlDraft = config.capePath;
			} else if (CustomCape.status() == CustomCape.Status.EMPTY) {
				capeUrlDraft = "";
			}
		}

		featureCard(graphics, font, left, top, col, CARD_HEAD + 128 + CARD_PAD, "Preview");
		drawCapePreview(graphics, ix + 8, top + CARD_HEAD + 4, iw - 16, 102);
		String hint = CustomCape.layoutHint();
		if (!hint.isBlank()) {
			GuiDraw.small(graphics, font, clip(font, hint, (int) iw - 4), ix + 2, top + CARD_HEAD + 108, Theme.MUTED);
		}

		float y = featureCard(graphics, font, right, top, col, cardHeight(5), "Source");
		GuiDraw.small(graphics, font, CustomCape.statusLabel(), rx, y + 2, CustomCape.status() == CustomCape.Status.ERROR ? Theme.WARN : Theme.MUTED);
		y += ROW;

		capeFieldX = rx;
		capeFieldY = y;
		capeFieldW = iw;
		boolean hoverField = GuiDraw.hovered(mouseX, mouseY, rx, y, iw, ROW);
		GuiDraw.panel(graphics, rx, y, iw, ROW, 4, capeFocused || hoverField ? Theme.CARD_HOVER : Theme.CARD, capeFocused ? Theme.ACCENT : Theme.LINE);
		NickHider.suppress();
		String shown = capeUrlDraft.isEmpty() && !capeFocused ? "https://...png" : capeUrlDraft + (capeFocused ? "|" : "");
		GuiDraw.menu(graphics, font, clip(font, shown, (int) iw - 10), rx + 5, GuiDraw.middle(y, ROW), capeUrlDraft.isEmpty() && !capeFocused ? Theme.MUTED : Theme.TEXT);
		NickHider.resume();
		hits.add(new Hit(rx, y, iw, ROW, () -> {
			capeFocused = true;
			nickFocused = false;
			searchOpen = false;
		}));
		y += ROW;

		boolean hoverFile = GuiDraw.hovered(mouseX, mouseY, rx, y, iw, ROW);
		GuiDraw.panel(graphics, rx, y + 1, iw, ROW - 2, 5, hoverFile ? Theme.CARD_HOVER : Theme.CARD, Theme.LINE);
		GuiDraw.menu(graphics, font, "Local file...", rx + 5, GuiDraw.middle(y, ROW), Theme.TEXT);
		hits.add(new Hit(rx, y, iw, ROW, CustomCape::pickLocal));
		y += ROW;

		boolean hoverClear = GuiDraw.hovered(mouseX, mouseY, rx, y, iw, ROW);
		GuiDraw.panel(graphics, rx, y + 1, iw, ROW - 2, 5, hoverClear ? Theme.CARD_HOVER : Theme.CARD, Theme.LINE);
		GuiDraw.menu(graphics, font, "Remove cape", rx + 5, GuiDraw.middle(y, ROW), Theme.TEXT);
		hits.add(new Hit(rx, y, iw, ROW, () -> {
			capeUrlDraft = "";
			CustomCape.clear();
		}));
	}

	private void drawCapePreview(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		if (!CustomCape.ready()) {
			float cx = x + w * 0.5f;
			GuiDraw.rounded(graphics, cx - 10, y + 4, 20, h - 8, 3, Theme.TRACK);
			return;
		}
		int tw = CustomCape.width();
		int th = CustomCape.height();
		float capeW = Math.min(w - 8, 48);
		float capeH = Math.min(h - 8, capeW * 1.7f);
		float px = x + (w - capeW) * 0.5f;
		float py = y + (h - capeH) * 0.5f;
		GuiDraw.blit(
			graphics,
			CustomCape.textureId(),
			px,
			py,
			capeW,
			capeH,
			CustomCape.faceU(),
			CustomCape.faceV(),
			CustomCape.faceW(),
			CustomCape.faceH(),
			tw,
			th
		);
	}

	private void commitCapeUrl() {
		String draft = capeUrlDraft.trim();
		VoidmarkConfig config = VoidmarkConfig.get();
		if (draft.isEmpty()) {
			if ((config.capeUrl == null || config.capeUrl.isBlank()) && (config.capePath == null || config.capePath.isBlank())) {
				return;
			}
			CustomCape.clear();
			return;
		}
		if (draft.equals(config.capeUrl) || draft.equals(config.capePath)) {
			return;
		}
		if (draft.startsWith("http://") || draft.startsWith("https://")) {
			CustomCape.applyUrl(draft);
		}
	}

	private String playerName() {
		if (VoidmarkConfig.get().nickEnabled) {
			String nick = NickHider.plainNick();
			if (!nick.isBlank()) {
				return nick;
			}
		}
		String name = minecraft.getGameProfile().name();
		if (name == null || name.isBlank()) {
			return "Player";
		}
		return name;
	}

	private PlayerSkin playerSkin() {
		if (minecraft.player != null) {
			return minecraft.player.getSkin();
		}
		return minecraft.getSkinManager().createLookup(minecraft.getGameProfile(), true).get();
	}

	private static String fitName(Font font, String name, int maxWidth) {
		if (GuiDraw.menuWidth(font, name) <= maxWidth) {
			return name;
		}
		String trimmed = name;
		while (trimmed.length() > 1 && GuiDraw.menuWidth(font, trimmed + "..") > maxWidth) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + "..";
	}

	private static String tabGlyph(Tab value) {
		return switch (value) {
			case WORLD -> MenuFont.GLOBE;
			case VIEW -> MenuFont.EYE;
			case FOG -> MenuFont.CLOUD;
			case MOBS -> MenuFont.MOB;
			case MARKERS -> MenuFont.CUBE;
			case DISPLAY -> MenuFont.MONITOR;
			case HUD -> MenuFont.HUD;
			case STATUS -> MenuFont.SIGNAL;
			case INVENTORY -> MenuFont.BAG;
			case NICK -> MenuFont.PERSON;
			case CAPE -> MenuFont.FLAG;
		};
	}

	private void drawToolbar(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float x = contentX();
		float y = windowY + 5;
		float w = contentW();
		GuiDraw.hline(graphics, windowX + SIDEBAR_W + 1, windowY + TOOLBAR_H - 1, windowW - SIDEBAR_W - 1, Theme.LINE);
		hits.add(new Hit(windowX + SIDEBAR_W, windowY, windowW - SIDEBAR_W, TOOLBAR_H, mx -> startDrag(mx, lastClickY), true));

		float labelY = GuiDraw.middle(y, 14);
		boolean hudHover = GuiDraw.hovered(mouseX, mouseY, x, y, ACTION_W, 14);
		GuiDraw.panel(graphics, x, y, ACTION_W, 14, 5, hudHover ? Theme.CARD_HOVER : Theme.CARD, Theme.LINE);
		GuiDraw.menu(graphics, font, "HUD", x + (ACTION_W - GuiDraw.menuWidth(font, "HUD")) / 2f, labelY, Theme.TEXT);
		hits.add(new Hit(x, y, ACTION_W, 14, () -> minecraft.setScreen(new HudEditorScreen())));

		float resetX = x + ACTION_W + 4;
		boolean resetHover = GuiDraw.hovered(mouseX, mouseY, resetX, y, RESET_W, 14);
		GuiDraw.panel(graphics, resetX, y, RESET_W, 14, 5, resetHover ? Theme.CARD_HOVER : Theme.CARD, Theme.LINE);
		GuiDraw.menu(graphics, font, "Reset", resetX + (RESET_W - GuiDraw.menuWidth(font, "Reset")) / 2f, labelY, Theme.TEXT);
		hits.add(new Hit(resetX, y, RESET_W, 14, this::resetCurrentPage));

		float titleX = resetX + RESET_W + 8;
		float searchMax = w - ACTION_W - RESET_W - 8 - ICON_SLOT * 3 - 8;
		searchFieldX = titleX;
		searchFieldW = Mth.lerp(searchT, 72, Math.max(72, searchMax));

		if (searchT > 0.08f) {
			GuiDraw.panel(graphics, searchFieldX, y, searchFieldW, 14, 5, Theme.CARD_HOVER, Theme.ACCENT);
			String shown = searchQuery.isEmpty() ? "Search settings..." : searchQuery + (searchOpen ? "|" : "");
			int color = searchQuery.isEmpty() ? Theme.MUTED : Theme.TEXT;
			GuiDraw.menu(graphics, font, clip(font, shown, (int) searchFieldW - 10), searchFieldX + 6, labelY, color);
			hits.add(new Hit(searchFieldX, y, searchFieldW, 14, () -> searchOpen = true));
		} else {
			GuiDraw.menu(graphics, font, tab.label, titleX, labelY, Theme.HEADER);
		}

		float iconX = x + w - ICON_SLOT * 3;
		drawIconButton(graphics, font, mouseX, mouseY, iconX, y, MenuFont.SETTINGS, settingsOpen, () -> {
			settingsOpen = !settingsOpen;
			bellOpen = false;
			searchOpen = false;
		});
		drawIconButton(graphics, font, mouseX, mouseY, iconX + ICON_SLOT, y, MenuFont.BELL, bellOpen, () -> {
			bellOpen = !bellOpen;
			settingsOpen = false;
			searchOpen = false;
		});
		drawIconButton(graphics, font, mouseX, mouseY, iconX + ICON_SLOT * 2, y, MenuFont.SEARCH, searchOpen, () -> {
			searchOpen = !searchOpen;
			settingsOpen = false;
			bellOpen = false;
			if (!searchOpen) {
				searchQuery = "";
			}
		});
	}

	private void drawIconButton(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, String glyph, boolean active, Runnable click) {
		boolean hover = GuiDraw.hovered(mouseX, mouseY, x, y, ICON_SLOT, 14);
		float t = anim("icon-" + glyph, hover || active ? 1f : 0f);
		if (t > 0.02f) {
			GuiDraw.rounded(graphics, x, y, ICON_SLOT, 14, 4, Anim.fade(Theme.withAlpha(Theme.ACCENT, 40), t));
		}
		GuiDraw.icon(graphics, font, glyph, x + 3, GuiDraw.middle(y, 14), active ? Theme.ACCENT : Theme.MUTED);
		hits.add(new Hit(x, y, ICON_SLOT, 14, click));
	}

	private static String clip(Font font, String value, int maxWidth) {
		if (GuiDraw.menuWidth(font, value) <= maxWidth) {
			return value;
		}
		String trimmed = value;
		while (trimmed.length() > 1 && GuiDraw.menuWidth(font, trimmed + "..") > maxWidth) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + "..";
	}

	private void resetCurrentPage() {
		VoidmarkConfig config = VoidmarkConfig.get();
		UnloadState.markDirty();
		switch (tab) {
			case WORLD -> {
				config.worldTintEnabled = false;
				config.worldTintRgb = 0x2FB5FF;
				config.worldTintStrength = 0.70f;
				config.worldTintMode = "shader";
				config.skyTintEnabled = false;
				config.skyTintRgb = 0x1B4F8A;
				config.skyTintStrength = 0.70f;
				config.matchSkyToWorld = true;
			}
			case VIEW -> {
				config.aspectEnabled = false;
				config.aspectRatio = 1.0f;
			}
			case FOG -> {
				config.fogEnabled = false;
				config.fogRgb = 0x8EC8FF;
				config.fogStart = 0.12f;
				config.fogEnd = 0.72f;
				config.fogDensity = 1.0f;
				config.matchFogToWorld = false;
			}
			case MOBS -> {
				config.mobGlowEnabled = false;
				config.mobGlowThroughWalls = true;
				config.blockOutlineGlow = true;
				config.mobGlowId = "";
				config.mobGlowIds = new ArrayList<>();
				config.mobGlowSize = 0.48f;
				config.mobGlowOpacity = 0.58f;
				config.mobGlowRgb = 0x2FB5FF;
				mobQuery = "";
				mobScroll = 0f;
			}
			case MARKERS -> {
				config.markersEnabled = true;
				config.onlyInTheEnd = true;
				config.forceEnable = false;
				config.blockScan = true;
				config.particleDetection = true;
				config.scanRadius = 48;
			}
			case DISPLAY -> {
				config.boxFill = true;
				config.boxOutline = true;
				config.tracersEnabled = true;
				config.throughWalls = true;
				config.fillOpacity = 0.32f;
				config.hudEnabled = true;
				config.watermarkEnabled = true;
				config.hudWatermarkX = -1f;
				config.hudWatermarkY = -1f;
				config.hudWatermarkScale = 1.0f;
				config.hudNodesX = -1f;
				config.hudNodesY = -1f;
				config.hudNodesScale = 1.0f;
				config.musicHudEnabled = true;
				config.musicHideIdle = false;
				config.hudMusicX = -1f;
				config.hudMusicY = -1f;
				config.hudMusicScale = 1.0f;
				config.rawmatsHudEnabled = true;
				config.rawmatsEnchanted = false;
				config.hudRawmatsX = -1f;
				config.hudRawmatsY = -1f;
				config.hudRawmatsScale = 1.0f;
				config.colorRgb = 0x2FB5FF;
			}
			case HUD -> {
				config.hudHotbar = true;
				config.hudHealth = true;
				config.hudHunger = true;
				config.hudArmor = true;
				config.hudAir = true;
				config.hudExperience = true;
				config.hudScoreboard = true;
				config.hudBossBar = true;
				config.hudEffects = true;
				config.hudHeldItem = true;
				config.hudMountHealth = true;
				config.resetHudSlots();
			}
			case INVENTORY -> {
				config.inventoryHudEnabled = true;
				config.inventoryHudHotbar = true;
				config.inventoryHudArmor = true;
				config.inventoryHudCount = true;
				config.inventoryHudAnchor = "bottom_right";
				config.inventoryHudScale = 1.0f;
				config.hudInventoryX = -1f;
				config.hudInventoryY = -1f;
			}
			case STATUS -> {
			}
			case NICK -> {
				config.nickEnabled = false;
				config.nick = "";
				config.nametagsEnabled = true;
				config.nametagThroughWalls = false;
				config.nametagDistance = true;
				config.nametagRange = 128;
				config.nametagScale = 1.0f;
			}
			case CAPE -> {
				capeUrlDraft = "";
				CustomCape.clear();
			}
		}
		WorldTint.syncChunkMeshes(minecraft);
	}

	private void drawSearchResults(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		List<SearchEntry> matches = matches();
		if (matches.isEmpty()) {
			GuiDraw.panel(graphics, searchFieldX, windowY + 20, searchFieldW, 20, 6, Anim.fade(Theme.PANEL, searchT), Theme.LINE);
			GuiDraw.menu(graphics, font, "No matches", searchFieldX + 8, GuiDraw.middle(windowY + 20, 20), Theme.MUTED);
			return;
		}
		float h = matches.size() * 16 + 6;
		GuiDraw.panel(graphics, searchFieldX, windowY + 20, searchFieldW, h, 6, Anim.fade(Theme.PANEL, searchT), Theme.LINE);
		float iy = windowY + 23;
		for (SearchEntry entry : matches) {
			boolean hover = GuiDraw.hovered(mouseX, mouseY, searchFieldX, iy, searchFieldW, 16);
			if (hover) {
				GuiDraw.rounded(graphics, searchFieldX + 2, iy, searchFieldW - 4, 16, 4, Theme.withAlpha(Theme.ACCENT, 28));
			}
			GuiDraw.menu(graphics, font, entry.label, searchFieldX + 8, GuiDraw.middle(iy, 16), hover ? Theme.ACCENT : Theme.TEXT);
			GuiDraw.small(graphics, font, entry.hint, searchFieldX + searchFieldW - GuiDraw.smallWidth(font, entry.hint) - 8, GuiDraw.middle(iy, 16) + 1, Theme.MUTED);
			hits.add(new Hit(searchFieldX, iy, searchFieldW, 16, () -> {
				selectTab(entry.tab);
				searchQuery = "";
			}));
			iy += 16;
		}
	}

	private List<SearchEntry> matches() {
		String q = searchQuery.trim().toLowerCase(Locale.ROOT);
		List<SearchEntry> out = new ArrayList<>();
		if (q.isEmpty()) {
			return out;
		}
		for (SearchEntry entry : SEARCH) {
			if (entry.label.toLowerCase(Locale.ROOT).contains(q) || entry.hint.toLowerCase(Locale.ROOT).contains(q) || entry.tab.label.toLowerCase(Locale.ROOT).contains(q)) {
				out.add(entry);
			}
		}
		return out;
	}

	private void drawSettings(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		settingsX = contentX() + contentW() - PANEL_W;
		settingsY = windowY + TOOLBAR_H + 2;
		GuiDraw.panel(graphics, settingsX, settingsY, PANEL_W, SETTINGS_H * Math.max(0.2f, settingsT), 8, Anim.fade(Theme.PANEL, settingsT), Theme.ACCENT);
		if (settingsT < 0.85f) {
			return;
		}
		GuiDraw.menu(graphics, font, "Theme", settingsX + 8, settingsY + 6, Theme.HEADER);
		GuiDraw.small(graphics, font, "Accent", settingsX + 8, settingsY + 20, Theme.MUTED);
		float y = swatchRow(graphics, mouseX, mouseY, settingsX + 10, settingsY + 32, Theme.PRESETS, true);
		y = colorRow(graphics, font, settingsX + 8, y, PANEL_W - 16, mouseX, mouseY, "Custom", VoidmarkConfig.get().themeAccentRgb, PickerTarget.THEME);
		GuiDraw.small(graphics, font, "Pane", settingsX + 8, y + 1, Theme.MUTED);
		y = swatchRow(graphics, mouseX, mouseY, settingsX + 10, y + 12, Theme.PANE_PRESETS, false);
		y = colorRow(graphics, font, settingsX + 8, y, PANEL_W - 16, mouseX, mouseY, "Custom", VoidmarkConfig.get().themePaneRgb, PickerTarget.PANE);
		y = slider(graphics, font, settingsX + 8, y, PANEL_W - 16, "Opacity", Math.round(VoidmarkConfig.get().themePaneOpacity * 100) + "%", (VoidmarkConfig.get().themePaneOpacity - 0.20f) / 0.80f, v -> {
			VoidmarkConfig.get().themePaneOpacity = VoidmarkConfig.clamp(0.20f + v * 0.80f, 0.20f, 1f);
			Theme.refresh();
		});
		toggle(graphics, font, settingsX + 8, y, PANEL_W - 16, mouseX, mouseY, "Animations", VoidmarkConfig.get().uiAnimations, v -> VoidmarkConfig.get().uiAnimations = v);
	}

	private float swatchRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float startX, float startY, Theme.Swatch[] swatches, boolean accent) {
		float dx = startX;
		float dy = startY;
		float rowEnd = settingsX + PANEL_W - 20;
		for (int i = 0; i < swatches.length; i++) {
			Theme.Swatch swatch = swatches[i];
			int current = accent ? VoidmarkConfig.get().themeAccentRgb : VoidmarkConfig.get().themePaneRgb;
			boolean active = (current & 0xFFFFFF) == swatch.rgb();
			boolean hover = GuiDraw.hovered(mouseX, mouseY, dx, dy, 14, 14);
			GuiDraw.rounded(graphics, dx - 1, dy - 1, 16, 16, 4, active || hover ? Theme.TEXT : Theme.LINE);
			GuiDraw.rounded(graphics, dx, dy, 14, 14, 3, 0xFF000000 | swatch.rgb());
			hits.add(new Hit(dx, dy, 14, 14, accent ? () -> Theme.applyPreset(swatch) : () -> Theme.applyPanePreset(swatch)));
			dx += 18;
			if (i + 1 < swatches.length && dx > rowEnd) {
				dx = startX;
				dy += 18;
			}
		}
		return dy + 18;
	}

	private void drawBell(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		bellX = contentX() + contentW() - PANEL_W;
		bellY = windowY + TOOLBAR_H + 2;
		float h = 240;
		GuiDraw.panel(graphics, bellX, bellY, PANEL_W, h * Math.max(0.2f, bellT), 8, Anim.fade(Theme.PANEL, bellT), Theme.ACCENT);
		if (bellT < 0.85f) {
			return;
		}
		GuiDraw.menu(graphics, font, "Overlay", bellX + 8, bellY + 6, Theme.HEADER);
		VoidmarkConfig config = VoidmarkConfig.get();
		float y = bellY + 20;
		float iw = PANEL_W - 16;
		float ix = bellX + 8;
		y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Inventory HUD", config.inventoryHudEnabled, v -> config.inventoryHudEnabled = v);
		y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Watermark", config.watermarkEnabled, v -> config.watermarkEnabled = v);
		y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Music", config.musicHudEnabled, v -> config.musicHudEnabled = v);
		y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Raw mats", config.rawmatsHudEnabled, v -> config.rawmatsHudEnabled = v);
		y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "FPS", config.watermarkFps, v -> config.watermarkFps = v);
		y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Ping", config.watermarkPing, v -> config.watermarkPing = v);
		y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Clock", config.watermarkTime, v -> config.watermarkTime = v);
		y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Name", config.watermarkName, v -> config.watermarkName = v);
		GuiDraw.small(graphics, font, "VANILLA HUD", ix, y + 2, Theme.HEADER);
		y += 12;
		float col = (iw - 6) * 0.5f;
		float ly = y;
		float ry = y;
		ly = toggle(graphics, font, ix, ly, col, mouseX, mouseY, "Hotbar", config.hudHotbar, v -> config.hudHotbar = v);
		ry = toggle(graphics, font, ix + col + 6, ry, col, mouseX, mouseY, "Score", config.hudScoreboard, v -> config.hudScoreboard = v);
		ly = toggle(graphics, font, ix, ly, col, mouseX, mouseY, "Health", config.hudHealth, v -> config.hudHealth = v);
		ry = toggle(graphics, font, ix + col + 6, ry, col, mouseX, mouseY, "Hunger", config.hudHunger, v -> config.hudHunger = v);
		ly = toggle(graphics, font, ix, ly, col, mouseX, mouseY, "Armor", config.hudArmor, v -> config.hudArmor = v);
		ry = toggle(graphics, font, ix + col + 6, ry, col, mouseX, mouseY, "Air", config.hudAir, v -> config.hudAir = v);
		ly = toggle(graphics, font, ix, ly, col, mouseX, mouseY, "XP", config.hudExperience, v -> config.hudExperience = v);
		ry = toggle(graphics, font, ix + col + 6, ry, col, mouseX, mouseY, "Boss", config.hudBossBar, v -> config.hudBossBar = v);
		ly = toggle(graphics, font, ix, ly, col, mouseX, mouseY, "Effects", config.hudEffects, v -> config.hudEffects = v);
		ry = toggle(graphics, font, ix + col + 6, ry, col, mouseX, mouseY, "Held", config.hudHeldItem, v -> config.hudHeldItem = v);
		toggle(graphics, font, ix, ly, col, mouseX, mouseY, "Mount", config.hudMountHealth, v -> config.hudMountHealth = v);
	}

	private void drawColumns(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float left = contentX();
		float top = windowY + TOOLBAR_H + 6;
		float col = colW();
		float right = left + col + COL_GAP;
		float ix = innerX(left);
		float rx = innerX(right);
		float iw = innerW(col);
		VoidmarkConfig config = VoidmarkConfig.get();

		switch (tab) {
			case WORLD -> {
				boolean lightmap = config.worldTintUsesLightmap();
				float y = featureCard(graphics, font, left, top, col, cardHeight(lightmap ? 5 : 4), "Blocks");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "World tint", config.worldTintEnabled, v -> config.worldTintEnabled = v);
				y = cycle(graphics, font, ix, y, iw, mouseX, mouseY, "Mode", config.worldTintModeLabel(), config::cycleWorldTintMode);
				y = colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Color", config.worldTintRgb, PickerTarget.WORLD);
				y = slider(graphics, font, ix, y, iw, "Strength", String.format(Locale.ROOT, "%.0f", config.worldTintStrength * 100), config.worldTintStrength, v -> config.worldTintStrength = v);
				if (lightmap) {
					hint(graphics, font, ix, y, iw, "Turn fullbright off");
				}

				y = featureCard(graphics, font, right, top, col, cardHeight(4), "Skybox");
				y = toggle(graphics, font, rx, y, iw, mouseX, mouseY, "Skybox tint", config.skyTintEnabled, v -> config.skyTintEnabled = v);
				y = toggle(graphics, font, rx, y, iw, mouseX, mouseY, "Match world", config.matchSkyToWorld, v -> config.matchSkyToWorld = v);
				int skyPreview = config.matchSkyToWorld ? config.worldTintRgb : config.skyTintRgb;
				y = colorRow(graphics, font, rx, y, iw, mouseX, mouseY, "Color", skyPreview, PickerTarget.SKY);
				slider(graphics, font, rx, y, iw, "Strength", String.format(Locale.ROOT, "%.0f", config.skyTintStrength * 100), config.skyTintStrength, v -> config.skyTintStrength = v);
			}
			case VIEW -> {
				float y = featureCard(graphics, font, left, top, col, cardHeight(3), "Camera");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Aspect ratio", config.aspectEnabled, v -> config.aspectEnabled = v);
				y = slider(graphics, font, ix, y, iw, "Aspect", aspectLabel(config.aspectRatio), (config.aspectRatio - 0.50f) / 0.70f, v -> config.aspectRatio = VoidmarkConfig.clamp(0.50f + v * 0.70f, 0.50f, 1.20f));
				chipRow(graphics, font, ix, y, iw, mouseX, mouseY, new String[]{"Native", "16:10", "4:3", "5:4"}, aspectChipIndex(config.aspectRatio), index -> {
					float[] values = {1.00f, 0.90f, 0.75f, 0.70f};
					config.aspectEnabled = true;
					config.aspectRatio = values[index];
				});

				y = featureCard(graphics, font, right, top, col, CARD_HEAD + 32 + CARD_PAD, "Info");
				GuiDraw.menu(graphics, font, "Below Native stretches", rx, y + 2, Theme.MUTED);
				GuiDraw.menu(graphics, font, "horizontally, like 4:3.", rx, y + 14, Theme.MUTED);
			}
			case FOG -> {
				float y = featureCard(graphics, font, left, top, col, cardHeight(3), "Main");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Custom fog", config.fogEnabled, v -> config.fogEnabled = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Match world", config.matchFogToWorld, v -> config.matchFogToWorld = v);
				int fogPreview = config.matchFogToWorld ? config.worldTintRgb : config.fogRgb;
				colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Color", fogPreview, PickerTarget.FOG);

				y = featureCard(graphics, font, right, top, col, cardHeight(3), "Distance");
				y = slider(graphics, font, rx, y, iw, "Start", String.format(Locale.ROOT, "%.0f%%", config.fogStart * 100), config.fogStart / 0.95f, v -> config.fogStart = VoidmarkConfig.clamp(v * 0.95f, 0f, 0.95f));
				y = slider(graphics, font, rx, y, iw, "End", String.format(Locale.ROOT, "%.0f%%", config.fogEnd * 100), (config.fogEnd - 0.05f) / 0.95f, v -> config.fogEnd = VoidmarkConfig.clamp(0.05f + v * 0.95f, 0.05f, 1f));
				slider(graphics, font, rx, y, iw, "Density", String.format(Locale.ROOT, "%.0f", config.fogDensity * 100), config.fogDensity, v -> config.fogDensity = v);
			}
			case MOBS -> drawMobsTab(graphics, font, mouseX, mouseY);
			case MARKERS -> {
				float y = featureCard(graphics, font, left, top, col, cardHeight(4), "Main");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Enable", config.markersEnabled, v -> config.markersEnabled = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Only in The End", config.onlyInTheEnd, v -> config.onlyInTheEnd = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Force enable", config.forceEnable, v -> config.forceEnable = v);
				slider(graphics, font, ix, y, iw, "Scan radius", config.scanRadius + "m", (config.scanRadius - 16) / 64f, v -> config.scanRadius = VoidmarkConfig.clamp(16 + Math.round(v * 64f), 16, 80));

				y = featureCard(graphics, font, right, top, col, cardHeight(2), "Detection");
				y = toggle(graphics, font, rx, y, iw, mouseX, mouseY, "Block scan", config.blockScan, v -> config.blockScan = v);
				toggle(graphics, font, rx, y, iw, mouseX, mouseY, "Particle hints", config.particleDetection, v -> config.particleDetection = v);
			}
			case DISPLAY -> {
				float y = featureCard(graphics, font, left, top, col, cardHeight(6), "Esp");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Filled box", config.boxFill, v -> config.boxFill = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Outline", config.boxOutline, v -> config.boxOutline = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Tracer", config.tracersEnabled, v -> config.tracersEnabled = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Through walls", config.throughWalls, v -> config.throughWalls = v);
				y = slider(graphics, font, ix, y, iw, "Fill opacity", Math.round(config.fillOpacity * 100) + "%", (config.fillOpacity - 0.08f) / 0.77f, v -> config.fillOpacity = VoidmarkConfig.clamp(0.08f + v * 0.77f, 0.08f, 0.85f));
				colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Marker color", config.colorRgb, PickerTarget.NODE);

				y = featureCard(graphics, font, right, top, col, cardHeight(7), "Overlay");
				y = toggle(graphics, font, rx, y, iw, mouseX, mouseY, "Node HUD", config.hudEnabled, v -> config.hudEnabled = v);
				y = toggle(graphics, font, rx, y, iw, mouseX, mouseY, "Watermark", config.watermarkEnabled, v -> config.watermarkEnabled = v);
				y = toggle(graphics, font, rx, y, iw, mouseX, mouseY, "Music HUD", config.musicHudEnabled, v -> config.musicHudEnabled = v);
				y = toggle(graphics, font, rx, y, iw, mouseX, mouseY, "Raw mats HUD", config.rawmatsHudEnabled, v -> config.rawmatsHudEnabled = v);
				y = cycle(graphics, font, rx, y, iw, mouseX, mouseY, "Materials", config.rawmatsModeLabel(), config::cycleRawmatsMode);
				y = toggle(graphics, font, rx, y, iw, mouseX, mouseY, "Nametags", config.nametagsEnabled, v -> config.nametagsEnabled = v);
				toggle(graphics, font, rx, y, iw, mouseX, mouseY, "Hide when idle", config.musicHideIdle, v -> config.musicHideIdle = v);
			}
			case HUD -> {
				float y = featureCard(graphics, font, left, top, col, cardHeight(7), "Bars");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Hotbar", config.hudHotbar, v -> config.hudHotbar = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Health", config.hudHealth, v -> config.hudHealth = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Hunger", config.hudHunger, v -> config.hudHunger = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Armor", config.hudArmor, v -> config.hudArmor = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Air", config.hudAir, v -> config.hudAir = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Experience", config.hudExperience, v -> config.hudExperience = v);
				toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Mount health", config.hudMountHealth, v -> config.hudMountHealth = v);

				y = featureCard(graphics, font, right, top, col, cardHeight(4) + 28, "Info");
				y = toggle(graphics, font, rx, y, iw, mouseX, mouseY, "Scoreboard", config.hudScoreboard, v -> config.hudScoreboard = v);
				y = toggle(graphics, font, rx, y, iw, mouseX, mouseY, "Boss bar", config.hudBossBar, v -> config.hudBossBar = v);
				y = toggle(graphics, font, rx, y, iw, mouseX, mouseY, "Effects", config.hudEffects, v -> config.hudEffects = v);
				y = toggle(graphics, font, rx, y, iw, mouseX, mouseY, "Held item", config.hudHeldItem, v -> config.hudHeldItem = v);
				GuiDraw.menu(graphics, font, "Move and scale each piece", rx, y + 4, Theme.MUTED);
				GuiDraw.menu(graphics, font, "from the toolbar HUD editor.", rx, y + 16, Theme.MUTED);
			}
			case INVENTORY -> {
				float y = featureCard(graphics, font, left, top, col, cardHeight(4), "Inventory HUD");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Enable", config.inventoryHudEnabled, v -> config.inventoryHudEnabled = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Hotbar", config.inventoryHudHotbar, v -> config.inventoryHudHotbar = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Armor", config.inventoryHudArmor, v -> config.inventoryHudArmor = v);
				toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Item count", config.inventoryHudCount, v -> config.inventoryHudCount = v);

				y = featureCard(graphics, font, right, top, col, CARD_HEAD + 48 + CARD_PAD, "Layout");
				GuiDraw.menu(graphics, font, "Move and scale this HUD", rx, y + 2, Theme.MUTED);
				GuiDraw.menu(graphics, font, "from the toolbar HUD editor.", rx, y + 14, Theme.MUTED);
				GuiDraw.menu(graphics, font, "Scroll or drag the scale bar.", rx, y + 26, Theme.MUTED);
			}
			case STATUS -> {
				float y = featureCard(graphics, font, left, top, col, cardHeight(5), "Server");
				y = readout(graphics, font, ix, y, iw, "Hypixel", SkyblockLocation.onHypixel);
				y = readout(graphics, font, ix, y, iw, "Skyblock", SkyblockLocation.inSkyblock);
				y = readout(graphics, font, ix, y, iw, "The End", SkyblockLocation.inTheEnd);
				y = statRow(graphics, font, ix, y, iw, "FPS", HudStats.fps() + "");
				statRow(graphics, font, ix, y, iw, "Ping", HudStats.pingLabel());

				y = featureCard(graphics, font, right, top, col, CARD_HEAD + 42 + CARD_PAD, "Nodes");
				String area = SkyblockLocation.area.isEmpty() ? "Unknown" : SkyblockLocation.area;
				GuiDraw.menu(graphics, font, area, rx, y + 2, Theme.TEXT);
				GuiDraw.menu(graphics, font, EnderNodeTracker.get().count() + " tracked", rx, y + 14, Theme.MUTED);
				GuiDraw.menu(graphics, font, "Right Shift", rx, y + 28, Theme.HEADER);
			}
			case NICK -> {
				float y = featureCard(graphics, font, left, top, col, cardHeight(5), "Nametags");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Enable", config.nametagsEnabled, v -> config.nametagsEnabled = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Through walls", config.nametagThroughWalls, v -> config.nametagThroughWalls = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Show distance", config.nametagDistance, v -> config.nametagDistance = v);
				y = slider(graphics, font, ix, y, iw, "Size", Math.round(config.nametagScale * 100) + "%", (config.nametagScale - 0.50f) / 1.50f, v -> config.nametagScale = VoidmarkConfig.clamp(0.50f + v * 1.50f, 0.50f, 2.00f));
				slider(graphics, font, ix, y, iw, "Range", config.nametagRange + "m", (config.nametagRange - 64) / 192f, v -> config.nametagRange = VoidmarkConfig.clamp(64 + Math.round(v * 192f), 64, 256));

				y = featureCard(graphics, font, right, top, col, cardHeight(2), "Hider");
				y = toggle(graphics, font, rx, y, iw, mouseX, mouseY, "Replace my name", config.nickEnabled, v -> config.nickEnabled = v);
				GuiDraw.small(graphics, font, "Chat, tab, scoreboard. Use &6 &l.", rx, y + 2, Theme.MUTED);

				float full = contentW();
				float inputY = top + cardHeight(5) + 8;
				nickFieldX = left;
				nickFieldY = inputY;
				nickFieldW = full;
				boolean hoverNick = GuiDraw.hovered(mouseX, mouseY, left, inputY, full, 18);
				GuiDraw.panel(graphics, left, inputY, full, 18, 5, nickFocused || hoverNick ? Theme.CARD_HOVER : Theme.CARD, nickFocused ? Theme.ACCENT : Theme.LINE);
				NickHider.suppress();
				String raw = config.nick == null ? "" : config.nick;
				String shown = raw.isEmpty() && !nickFocused ? "Nick  (&6Name)" : raw + (nickFocused ? "|" : "");
				GuiDraw.menu(graphics, font, clip(font, shown, (int) full - 14), left + 7, GuiDraw.middle(inputY, 18), raw.isEmpty() && !nickFocused ? Theme.MUTED : Theme.TEXT);
				NickHider.resume();
				hits.add(new Hit(left, inputY, full, 18, () -> {
					nickFocused = true;
					capeFocused = false;
					searchOpen = false;
				}));

				float previewY = inputY + 26;
				float previewH = 56;
				GuiDraw.panel(graphics, left, previewY, full, previewH, 8, Theme.CARD, Theme.LINE);
				GuiDraw.small(graphics, font, "PREVIEW", left + CARD_PAD, previewY + 6, Theme.HEADER);
				GuiDraw.hline(graphics, left + CARD_PAD, previewY + 16, full - CARD_PAD * 2, Theme.LINE);
				GuiDraw.rounded(graphics, left + CARD_PAD, previewY + 22, full - CARD_PAD * 2, 26, 5, Theme.PANEL);
				NickHider.suppress();
				Component preview = config.nickEnabled ? NickHider.formattedNick() : Component.literal(playerName());
				if (preview.getString().isEmpty()) {
					GuiDraw.menu(graphics, font, "Your name will be hidden", left + CARD_PAD + 6, GuiDraw.middle(previewY + 22, 26), Theme.MUTED);
				} else {
					graphics.pose().pushMatrix();
					graphics.pose().translate(left + CARD_PAD + 6, GuiDraw.middle(previewY + 22, 26));
					graphics.text(font, preview, 0, 0, 0xFFFFFFFF, false);
					graphics.pose().popMatrix();
				}
				NickHider.resume();
			}
			case CAPE -> drawCapeTab(graphics, font, mouseX, mouseY);
		}
	}

	private static float innerX(float cardX) {
		return cardX + CARD_PAD;
	}

	private static float innerW(float cardW) {
		return cardW - CARD_PAD * 2;
	}

	private static float cardHeight(int rows) {
		return CARD_HEAD + rows * ROW + CARD_PAD;
	}

	private float featureCard(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, float h, String title) {
		GuiDraw.panel(graphics, x, y, w, h, Math.min(14f, h / 2f), Theme.CARD, Theme.LINE);
		GuiDraw.small(graphics, font, title, x + CARD_PAD, y + 5, Theme.HEADER);
		GuiDraw.hline(graphics, x + CARD_PAD, y + 16, w - CARD_PAD * 2, Theme.LINE);
		return y + CARD_HEAD;
	}

	private float toggle(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, int mouseX, int mouseY, String label, boolean value, Consumer<Boolean> setter) {
		boolean hovered = GuiDraw.hovered(mouseX, mouseY, x, y, w, ROW);
		float hover = anim("hov-" + label, hovered ? 1f : 0f);
		if (hover > 0.02f) {
			GuiDraw.rounded(graphics, x - 3, y, w + 6, ROW, 6, Anim.fade(0x08FFFFFF, hover));
		}
		float labelY = GuiDraw.middle(y, ROW);
		GuiDraw.menu(graphics, font, label, x + 1, labelY, Theme.TEXT);

		float t = anim("tog-" + label, value ? 1f : 0f);
		float trackW = 22;
		float trackH = 11;
		float tx = x + w - trackW;
		float ty = y + (ROW - trackH) / 2f;
		int fill = t > 0.5f ? Theme.ACCENT : Theme.TRACK;
		GuiDraw.pill(graphics, tx, ty, trackW, trackH, fill);
		float knob = tx + 6 + t * (trackW - 12);
		GuiDraw.circle(graphics, knob, ty + trackH / 2f, 4.6f, t > 0.5f ? Theme.TEXT : Theme.OFF);
		hits.add(new Hit(x, y, w, ROW, () -> {
			setter.accept(!value);
			UnloadState.markDirty();
		}));
		return y + ROW;
	}

	private float cycle(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, int mouseX, int mouseY, String label, String value, Runnable next) {
		boolean hovered = GuiDraw.hovered(mouseX, mouseY, x, y, w, ROW);
		if (hovered) {
			GuiDraw.rounded(graphics, x - 3, y, w + 6, ROW, 6, 0x08FFFFFF);
		}
		float labelY = GuiDraw.middle(y, ROW);
		GuiDraw.menu(graphics, font, label, x + 1, labelY, Theme.TEXT);
		int valueWidth = GuiDraw.menuWidth(font, value);
		GuiDraw.menu(graphics, font, value, x + w - valueWidth, labelY, Theme.ACCENT);
		hits.add(new Hit(x, y, w, ROW, next));
		return y + ROW;
	}

	private float chipRow(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, int mouseX, int mouseY, String[] labels, int selected, java.util.function.IntConsumer pick) {
		float cx = x;
		for (int i = 0; i < labels.length; i++) {
			float cw = GuiDraw.menuWidth(font, labels[i]) + 10;
			if (cx + cw > x + w) {
				break;
			}
			boolean on = i == selected;
			boolean hover = GuiDraw.hovered(mouseX, mouseY, cx, y + 1, cw, ROW - 2);
			GuiDraw.panel(graphics, cx, y + 1, cw, ROW - 2, 5, on ? Theme.ACCENT : hover ? Theme.CARD_HOVER : Theme.CARD, on ? Theme.ACCENT : Theme.LINE);
			GuiDraw.menu(graphics, font, labels[i], cx + 5, GuiDraw.middle(y, ROW), on ? Theme.WINDOW_SOLID : Theme.TEXT);
			int index = i;
			hits.add(new Hit(cx, y, cw, ROW, () -> pick.accept(index)));
			cx += cw + 4;
		}
		return y + ROW;
	}

	private static int aspectChipIndex(float ratio) {
		if (Math.abs(ratio - 1.00f) < 0.02f) {
			return 0;
		}
		if (Math.abs(ratio - 0.90f) < 0.02f) {
			return 1;
		}
		if (Math.abs(ratio - 0.75f) < 0.02f) {
			return 2;
		}
		if (Math.abs(ratio - 0.70f) < 0.02f) {
			return 3;
		}
		return -1;
	}

	private float slider(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, String label, String valueText, float progress, Consumer<Float> setter) {
		float labelY = GuiDraw.middle(y, ROW);
		GuiDraw.menu(graphics, font, label, x + 1, labelY, Theme.TEXT);
		int valueWidth = GuiDraw.menuWidth(font, valueText);
		GuiDraw.menu(graphics, font, valueText, x + w - valueWidth, labelY, Theme.TEXT);
		float barX = x + GuiDraw.menuWidth(font, label) + 8;
		float barW = Math.max(24, w - GuiDraw.menuWidth(font, label) - valueWidth - 16);
		float barY = y + 7;
		float t = Mth.clamp(progress, 0f, 1f);
		GuiDraw.pill(graphics, barX, barY, barW, 3, Theme.TRACK);
		GuiDraw.pill(graphics, barX, barY, Math.max(3, barW * t), 3, Theme.ACCENT);
		GuiDraw.circle(graphics, barX + barW * t, barY + 1.5f, 3.6f, Theme.ACCENT);
		hits.add(new Hit(barX - 2, y, barW + 4, ROW, mx -> setter.accept(Mth.clamp((float) ((mx - barX) / barW), 0f, 1f)), true));
		return y + ROW;
	}

	private float colorRow(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, int mouseX, int mouseY, String label, int rgb, PickerTarget target) {
		float labelY = GuiDraw.middle(y, ROW);
		GuiDraw.menu(graphics, font, label, x + 1, labelY, Theme.TEXT);
		float pw = 18;
		float ph = 10;
		float px = x + w - pw;
		float py = y + (ROW - ph) / 2f;
		boolean hover = GuiDraw.hovered(mouseX, mouseY, px - 1, y, pw + 2, ROW);
		GuiDraw.rounded(graphics, px - 1, py - 1, pw + 2, ph + 2, 3, hover ? Theme.ACCENT : Theme.LINE);
		GuiDraw.rounded(graphics, px, py, pw, ph, 2, 0xFF000000 | rgb);
		hits.add(new Hit(px - 2, y, pw + 4, ROW, () -> {
			if (target == PickerTarget.SKY) {
				VoidmarkConfig.get().matchSkyToWorld = false;
			}
			if (target == PickerTarget.FOG) {
				VoidmarkConfig.get().matchFogToWorld = false;
			}
			openPicker(target, rgb, px - PICKER_W + pw, y + ROW + 2);
		}));
		return y + ROW;
	}

	private float readout(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, String label, boolean on) {
		float labelY = GuiDraw.middle(y, ROW);
		GuiDraw.menu(graphics, font, label, x + 1, labelY, Theme.TEXT);
		GuiDraw.menu(graphics, font, on ? "ON" : "OFF", x + w - GuiDraw.menuWidth(font, on ? "ON" : "OFF"), labelY, on ? Theme.ACCENT : Theme.MUTED);
		return y + ROW;
	}

	private float statRow(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, String label, String value) {
		float labelY = GuiDraw.middle(y, ROW);
		GuiDraw.menu(graphics, font, label, x + 1, labelY, Theme.TEXT);
		GuiDraw.menu(graphics, font, value, x + w - GuiDraw.menuWidth(font, value), labelY, Theme.ACCENT);
		return y + ROW;
	}

	private float hint(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, String text) {
		GuiDraw.small(graphics, font, text, x + 1, GuiDraw.middle(y, ROW) + 1, Theme.WARN);
		return y + ROW;
	}

	private void drawPicker(GuiGraphicsExtractor graphics, Font font) {
		float x = pickerX;
		float y = pickerY;
		float w = PICKER_W;
		float h = PICKER_H;
		GuiDraw.panel(graphics, x, y, w, h, 8, Anim.fade(Theme.PANEL, pickerT), Theme.ACCENT);
		if (pickerT < 0.7f) {
			return;
		}
		GuiDraw.menu(graphics, font, "Color", x + 6, y + 5, Theme.MUTED);

		int current = WorldTint.hsvToRgb(pickerHue, pickerSat, pickerVal);
		GuiDraw.rounded(graphics, x + w - 22, y + 5, 14, 10, 3, Theme.LINE);
		GuiDraw.rounded(graphics, x + w - 21, y + 6, 12, 8, 2, 0xFF000000 | current);

		float svX = x + 6;
		float svY = y + 20;
		float svW = w - 12;
		float svH = 68;
		GuiDraw.hsvSquare(graphics, svX, svY, svW, svH, pickerHue);
		float cursorX = svX + pickerSat * svW;
		float cursorY = svY + (1f - pickerVal) * svH;
		GuiDraw.circle(graphics, cursorX, cursorY, 3.6f, 0xFF000000);
		GuiDraw.circle(graphics, cursorX, cursorY, 2.6f, 0xFFFFFFFF);
		GuiDraw.circle(graphics, cursorX, cursorY, 1.6f, 0xFF000000 | current);
		hits.add(new Hit(svX, svY, svW, svH, mx -> {
			pickerSat = Mth.clamp((float) ((mx - svX) / svW), 0f, 1f);
			pickerVal = Mth.clamp(1f - (float) ((lastClickY - svY) / svH), 0f, 1f);
			commitPicker();
		}, true));

		float hueY = svY + svH + 4;
		float hueH = 6;
		GuiDraw.hueBar(graphics, svX, hueY, svW, hueH);
		float hueMark = svX + (pickerHue / 360f) * svW;
		GuiDraw.fill(graphics, hueMark - 1.2f, hueY - 1, 2.4f, hueH + 2, 0xFF000000);
		GuiDraw.fill(graphics, hueMark - 0.5f, hueY - 1, 1f, hueH + 2, 0xFFFFFFFF);
		hits.add(new Hit(svX, hueY, svW, hueH, mx -> {
			pickerHue = Mth.clamp((float) ((mx - svX) / svW) * 360f, 0f, 359f);
			commitPicker();
		}, true));

		GuiDraw.menu(graphics, font, hex(current), x + 6, y + h - 12, Theme.MUTED);
	}

	private void openPicker(PickerTarget target, int rgb, float x, float y) {
		pickerTarget = target;
		float[] hsv = WorldTint.rgbToHsv(rgb);
		pickerHue = hsv[0];
		pickerSat = hsv[1];
		pickerVal = hsv[2];
		pickerX = Mth.clamp(x, windowX + SIDEBAR_W + 4, windowX + windowW - PICKER_W - 4);
		pickerY = Mth.clamp(y, windowY + TOOLBAR_H, windowY + windowH - PICKER_H - 4);
	}

	private void commitPicker() {
		applyColor(pickerTarget, WorldTint.hsvToRgb(pickerHue, pickerSat, pickerVal));
	}

	private void applyColor(PickerTarget target, int rgb) {
		VoidmarkConfig config = VoidmarkConfig.get();
		int packed = rgb & 0xFFFFFF;
		switch (target) {
			case WORLD -> config.worldTintRgb = packed;
			case SKY -> config.skyTintRgb = packed;
			case FOG -> config.fogRgb = packed;
			case NODE -> config.colorRgb = packed;
			case MOB -> config.mobGlowRgb = packed;
			case THEME -> Theme.applyCustom(packed);
			case PANE -> Theme.applyPane(packed);
		}
	}

	private void startDrag(double mx, double my) {
		dragging = true;
		dragOffX = mx - windowX;
		dragOffY = my - windowY;
	}

	private static String aspectLabel(float ratio) {
		if (Math.abs(ratio - 1.00f) < 0.02f) {
			return "Native";
		}
		if (Math.abs(ratio - 0.75f) < 0.02f) {
			return "4:3";
		}
		if (Math.abs(ratio - 0.70f) < 0.02f) {
			return "5:4";
		}
		if (Math.abs(ratio - 0.90f) < 0.02f) {
			return "16:10";
		}
		return Math.round(ratio * 100) + "%";
	}

	private static String hex(int rgb) {
		return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
	}

	private static String modVersion() {
		return FabricLoader.getInstance()
			.getModContainer("voidmark")
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("1.1.76");
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (event.button() != 0) {
			return super.mouseClicked(event, doubled);
		}
		lastClickY = event.y();
		dragging = false;
		boolean onCape = GuiDraw.hovered(event.x(), event.y(), capeFieldX, capeFieldY, capeFieldW, ROW);
		boolean onNick = GuiDraw.hovered(event.x(), event.y(), nickFieldX, nickFieldY, nickFieldW, 18);
		if (capeFocused && !onCape) {
			capeFocused = false;
			commitCapeUrl();
		}
		if (nickFocused && !onNick) {
			nickFocused = false;
		}
		boolean onMobSearch = GuiDraw.hovered(event.x(), event.y(), mobFieldX, mobFieldY, mobFieldW, 14);
		if (mobSearchFocused && !onMobSearch) {
			mobSearchFocused = false;
		}
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(event.x(), event.y())) {
				hit.click(event.x());
				return true;
			}
		}
		if (pickerTarget != null && !GuiDraw.hovered(event.x(), event.y(), pickerX, pickerY, PICKER_W, PICKER_H)) {
			pickerTarget = null;
			return true;
		}
		if (settingsOpen && !GuiDraw.hovered(event.x(), event.y(), settingsX, settingsY, PANEL_W, SETTINGS_H)) {
			settingsOpen = false;
			return true;
		}
		if (bellOpen && !GuiDraw.hovered(event.x(), event.y(), bellX, bellY, PANEL_W, 240)) {
			bellOpen = false;
			return true;
		}
		if (searchOpen && !GuiDraw.hovered(event.x(), event.y(), searchFieldX, windowY + 5, searchFieldW, 80)) {
			searchOpen = false;
			return true;
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (event.button() == 0 && dragging) {
			windowX = (float) (event.x() - dragOffX);
			windowY = (float) (event.y() - dragOffY);
			moved = true;
			return true;
		}
		if (event.button() == 0) {
			lastClickY = event.y();
			for (int i = hits.size() - 1; i >= 0; i--) {
				Hit hit = hits.get(i);
				if (hit.drag && hit.contains(event.x(), event.y())) {
					hit.click(event.x());
					return true;
				}
			}
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging && moved) {
			persistMenuPosition();
		}
		dragging = false;
		VoidmarkConfig.get().save();
		WorldTint.syncChunkMeshes(minecraft);
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (tab == Tab.MOBS && scrollY != 0 && GuiDraw.hovered(mouseX, mouseY, mobFieldX, mobFieldY, mobListW, mobListY + mobListH - mobFieldY)) {
			List<MobCatalog.Entry> entries = MobCatalog.filtered(mobQuery);
			float maxScroll = Math.max(0f, entries.size() * ROW - mobListH);
			mobScroll = Mth.clamp(mobScroll - (float) scrollY * ROW * 2.2f, 0f, maxScroll);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private void persistMenuPosition() {
		VoidmarkConfig config = VoidmarkConfig.get();
		config.menuX = windowX;
		config.menuY = windowY;
		config.menuPlaced = true;
		config.menuTab = tab.name();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			if (capeFocused) {
				capeFocused = false;
				commitCapeUrl();
				return true;
			}
			if (nickFocused) {
				nickFocused = false;
				return true;
			}
			if (mobSearchFocused) {
				if (!mobQuery.isEmpty()) {
					mobQuery = "";
					mobScroll = 0f;
				} else {
					mobSearchFocused = false;
				}
				return true;
			}
			if (searchOpen) {
				searchOpen = false;
				searchQuery = "";
				return true;
			}
			if (settingsOpen) {
				settingsOpen = false;
				return true;
			}
			if (bellOpen) {
				bellOpen = false;
				return true;
			}
			if (pickerTarget != null) {
				pickerTarget = null;
				return true;
			}
		}
		if (capeFocused && event.key() == InputConstants.KEY_BACKSPACE) {
			if (!capeUrlDraft.isEmpty()) {
				capeUrlDraft = capeUrlDraft.substring(0, capeUrlDraft.length() - 1);
			}
			return true;
		}
		if (nickFocused && event.key() == InputConstants.KEY_BACKSPACE) {
			String nick = VoidmarkConfig.get().nick;
			if (nick != null && !nick.isEmpty()) {
				VoidmarkConfig.get().nick = nick.substring(0, nick.length() - 1);
			}
			return true;
		}
		if (mobSearchFocused && event.key() == InputConstants.KEY_BACKSPACE) {
			if (!mobQuery.isEmpty()) {
				mobQuery = mobQuery.substring(0, mobQuery.length() - 1);
				mobScroll = 0f;
			}
			return true;
		}
		if (capeFocused && event.key() == InputConstants.KEY_RETURN) {
			capeFocused = false;
			commitCapeUrl();
			return true;
		}
		if (nickFocused && event.key() == InputConstants.KEY_RETURN) {
			nickFocused = false;
			return true;
		}
		if (capeFocused && event.key() == InputConstants.KEY_V && event.hasControlDown()) {
			String clip = minecraft.keyboardHandler.getClipboard();
			if (clip != null && !clip.isBlank()) {
				capeUrlDraft += clip.replace("\n", "").replace("\r", "").trim();
			}
			return true;
		}
		if (nickFocused && event.key() == InputConstants.KEY_V && event.hasControlDown()) {
			String clip = minecraft.keyboardHandler.getClipboard();
			if (clip != null && !clip.isBlank()) {
				VoidmarkConfig config = VoidmarkConfig.get();
				config.nick = (config.nick == null ? "" : config.nick) + clip.replace("\n", "").replace("\r", "");
			}
			return true;
		}
		if (searchOpen && event.key() == InputConstants.KEY_BACKSPACE) {
			if (!searchQuery.isEmpty()) {
				searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
			}
			return true;
		}
		if (event.key() == InputConstants.KEY_F && event.hasControlDown()) {
			searchOpen = true;
			settingsOpen = false;
			bellOpen = false;
			capeFocused = false;
			nickFocused = false;
			mobSearchFocused = false;
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (capeFocused && event.isAllowedChatCharacter()) {
			capeUrlDraft += event.codepointAsString();
			return true;
		}
		if (nickFocused && event.isAllowedChatCharacter()) {
			VoidmarkConfig config = VoidmarkConfig.get();
			if (config.nick == null) {
				config.nick = "";
			}
			if (config.nick.length() < 48) {
				config.nick += event.codepointAsString();
			}
			return true;
		}
		if (mobSearchFocused && event.isAllowedChatCharacter()) {
			if (mobQuery.length() < 32) {
				mobQuery += event.codepointAsString();
				mobScroll = 0f;
			}
			return true;
		}
		if (searchOpen && event.isAllowedChatCharacter()) {
			searchQuery += event.codepointAsString();
			return true;
		}
		return super.charTyped(event);
	}

	public void requestClose() {
		onClose();
	}

	@Override
	public void onClose() {
		if (moved) {
			persistMenuPosition();
		} else {
			VoidmarkConfig.get().menuTab = tab.name();
		}
		VoidmarkConfig.get().save();
		commitCapeUrl();
		if (!closing && VoidmarkConfig.get().uiAnimations && appear > 0.04f) {
			closing = true;
			capeFocused = false;
			nickFocused = false;
			return;
		}
		finishClose();
	}

	private void finishClose() {
		if (finishedClose) {
			return;
		}
		finishedClose = true;
		WorldTint.syncChunkMeshes(minecraft);
		super.onClose();
	}

	private static final class Hit {
		final float x, y, w, h;
		final Runnable click;
		final DoubleConsumer dragClick;
		final boolean drag;

		Hit(float x, float y, float w, float h, Runnable click) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.click = click;
			this.dragClick = null;
			this.drag = false;
		}

		Hit(float x, float y, float w, float h, DoubleConsumer dragClick, boolean drag) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.click = null;
			this.dragClick = dragClick;
			this.drag = drag;
		}

		boolean contains(double mx, double my) {
			return mx >= x && mx <= x + w && my >= y && my <= y + h;
		}

		void click(double mx) {
			if (dragClick != null) {
				dragClick.accept(mx);
			} else if (click != null) {
				click.run();
			}
		}
	}
}
