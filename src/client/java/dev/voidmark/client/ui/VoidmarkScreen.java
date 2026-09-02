package dev.voidmark.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmark.client.config.UnloadState;
import dev.voidmark.client.config.VoidmarkConfig;
import dev.voidmark.client.location.SkyblockLocation;
import dev.voidmark.client.mining.MiningAreas;
import dev.voidmark.client.mining.MiningTracker;
import dev.voidmark.client.mining.TitaniumTracker;
import dev.voidmark.client.render.GuiDraw;
import dev.voidmark.client.render.HudStats;
import dev.voidmark.client.render.MobCatalog;
import dev.voidmark.client.render.MobGlowRenderer;
import dev.voidmark.client.render.NametagRenderer;
import dev.voidmark.client.render.PlayerPreview;
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
	private static final float MENU_H = 248;
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
	private static final float FEATURE_W = 176;
	private static final float SETTINGS_H = 264;
	private static final float COG_W = 14;

	private enum Group {
		VISUALS("VISUALS"),
		HUD("HUD"),
		SKYBLOCK("SKYBLOCK"),
		PLAYER("PLAYER");

		final String label;

		Group(String label) {
			this.label = label;
		}
	}

	private enum Tab {
		WORLD("World", Group.VISUALS),
		ESP("ESP", Group.VISUALS),
		OVERLAY("Overlay", Group.HUD),
		BARS("Bars", Group.HUD),
		NODES("Nodes", Group.SKYBLOCK),
		MINING("Mining", Group.SKYBLOCK),
		PLAYER("Player", Group.PLAYER);

		final String label;
		final Group group;

		Tab(String label, Group group) {
			this.label = label;
			this.group = group;
		}
	}

	private enum Feature {
		WORLD("World tint", 4),
		SKY("Skybox", 3),
		FOG("Fog", 5),
		VIEW("Aspect", 3),
		MOB("Mob glow", 4),
		BLOCK("Block outline", 2),
		NODE_ESP("Node ESP", 5),
		WATERMARK("Watermark", 4),
		MUSIC("Music", 1),
		RAWMATS("Raw mats", 1),
		MINING("Mining HUD", 1),
		TITANIUM("Titanium ESP", 3),
		INVENTORY("Inventory", 3),
		NAMETAGS("Nametags", 5),
		NODES("Nodes", 5);

		final String title;
		final int rows;

		Feature(String title, int rows) {
			this.title = title;
			this.rows = rows;
		}

		float height() {
			return 22 + rows * ROW + 10;
		}
	}

	private enum PickerTarget {
		WORLD, SKY, FOG, NODE, THEME, PANE, MOB, BLOCK, TITANIUM
	}

	private record SearchEntry(String label, Tab tab, String hint) {
	}

	private static final SearchEntry[] SEARCH = {
		new SearchEntry("World tint", Tab.WORLD, "World"),
		new SearchEntry("Lightmap", Tab.WORLD, "World"),
		new SearchEntry("Shader", Tab.WORLD, "World"),
		new SearchEntry("Skybox tint", Tab.WORLD, "World"),
		new SearchEntry("Aspect ratio", Tab.WORLD, "World"),
		new SearchEntry("Custom fog", Tab.WORLD, "World"),
		new SearchEntry("Mob glow", Tab.ESP, "ESP"),
		new SearchEntry("Block outline", Tab.ESP, "ESP"),
		new SearchEntry("Block outline color", Tab.ESP, "ESP"),
		new SearchEntry("Mobs", Tab.ESP, "ESP"),
		new SearchEntry("Node ESP", Tab.ESP, "ESP"),
		new SearchEntry("Nametags", Tab.ESP, "ESP"),
		new SearchEntry("Nametag size", Tab.ESP, "ESP"),
		new SearchEntry("Nametag opacity", Tab.ESP, "ESP"),
		new SearchEntry("Menu scale", Tab.OVERLAY, "Theme"),
		new SearchEntry("HUD opacity", Tab.OVERLAY, "Theme"),
		new SearchEntry("HUD stars", Tab.OVERLAY, "Theme"),
		new SearchEntry("Markers", Tab.NODES, "Nodes"),
		new SearchEntry("Node HUD", Tab.NODES, "Nodes"),
		new SearchEntry("Mining HUD", Tab.MINING, "Mining"),
		new SearchEntry("Titanium ESP", Tab.MINING, "Mining"),
		new SearchEntry("Commissions", Tab.MINING, "Mining"),
		new SearchEntry("Pickaxe ability", Tab.MINING, "Mining"),
		new SearchEntry("Ability alert", Tab.MINING, "Mining"),
		new SearchEntry("Filled box", Tab.ESP, "ESP"),
		new SearchEntry("Watermark", Tab.OVERLAY, "Overlay"),
		new SearchEntry("Music HUD", Tab.OVERLAY, "Overlay"),
		new SearchEntry("Raw mats", Tab.OVERLAY, "Overlay"),
		new SearchEntry("Enchanted materials", Tab.OVERLAY, "Overlay"),
		new SearchEntry("Spotify", Tab.OVERLAY, "Music"),
		new SearchEntry("YouTube Music", Tab.OVERLAY, "Music"),
		new SearchEntry("Hotbar", Tab.BARS, "Bars"),
		new SearchEntry("Health", Tab.BARS, "Bars"),
		new SearchEntry("Hunger", Tab.BARS, "Bars"),
		new SearchEntry("Armor bar", Tab.BARS, "Bars"),
		new SearchEntry("Air", Tab.BARS, "Bars"),
		new SearchEntry("Experience", Tab.BARS, "Bars"),
		new SearchEntry("Scoreboard", Tab.BARS, "Bars"),
		new SearchEntry("Boss bar", Tab.BARS, "Bars"),
		new SearchEntry("Effects", Tab.BARS, "Bars"),
		new SearchEntry("Held item", Tab.BARS, "Bars"),
		new SearchEntry("Mount health", Tab.BARS, "Bars"),
		new SearchEntry("Inventory HUD", Tab.OVERLAY, "Overlay"),
		new SearchEntry("Item count", Tab.OVERLAY, "Overlay"),
		new SearchEntry("Pane opacity", Tab.OVERLAY, "Theme"),
		new SearchEntry("FPS", Tab.NODES, "Status"),
		new SearchEntry("Ping", Tab.NODES, "Status"),
		new SearchEntry("Hypixel", Tab.NODES, "Status"),
		new SearchEntry("Nick hider", Tab.PLAYER, "Player"),
		new SearchEntry("Cape", Tab.PLAYER, "Player")
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
	private boolean notesOpen;
	private boolean searchOpen;
	private boolean featureOpen;
	private Feature featureId;
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
	private float notesT;
	private float searchT;
	private float pickerT;
	private float featureT;
	private float settingsX;
	private float settingsY;
	private float notesX;
	private float notesY;
	private float notesH;
	private float notesScroll;
	private float featureX;
	private float featureY;
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
	private float previewYaw = 18f;
	private float previewPitch = -8f;
	private boolean previewDrag;
	private float previewX;
	private float previewY;
	private float previewW;
	private float previewH;

	private float windowX;
	private float windowY;
	private float windowW = MENU_W;
	private float windowH = MENU_H;
	private float viewScale = 1f;
	private float viewCx;
	private float viewCy;
	private float viewLift;

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

		float scale = (0.92f + 0.08f * appear) * VoidmarkConfig.normalizeMenuScale(VoidmarkConfig.get().menuScale);
		float lift = (1f - appear) * 12f;
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

		boolean chromeClip = GuiDraw.scissor(graphics, windowX, windowY, windowW, windowH);
		GuiDraw.rounded(graphics, windowX, windowY, windowW, windowH, Theme.WINDOW_RADIUS, Theme.WINDOW);
		GuiDraw.roundLeft(graphics, windowX, windowY, SIDEBAR_W, windowH, Theme.WINDOW_RADIUS, Theme.SIDEBAR);
		Starfield.draw(graphics, windowX + SIDEBAR_W, windowY, windowW - SIDEBAR_W, windowH, Theme.WINDOW_RADIUS, appear);
		GuiDraw.fill(graphics, windowX + SIDEBAR_W, windowY, 1, windowH, Theme.withAlpha(Theme.ACCENT, 90));
		if (chromeClip) {
			GuiDraw.disableScissor(graphics);
		}

		drawSidebar(graphics, font, localMx, localMy);
		drawToolbar(graphics, font, localMx, localMy);
		drawColumns(graphics, font, localMx, localMy);
		if (searchT > 0.02f && !searchQuery.isBlank()) {
			drawSearchResults(graphics, font, localMx, localMy);
		}
		if (featureT > 0.02f && featureId != null) {
			drawFeaturePanel(graphics, font, localMx, localMy);
		}
		if (settingsT > 0.02f) {
			drawSettings(graphics, font, localMx, localMy);
		}
		if (notesT > 0.02f) {
			drawNotes(graphics, font, localMx, localMy);
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
		notesT = Anim.exp(notesT, notesOpen ? 1f : 0f, 18f, dt);
		searchT = Anim.exp(searchT, searchOpen ? 1f : 0f, 18f, dt);
		pickerT = Anim.exp(pickerT, pickerTarget != null ? 1f : 0f, 18f, dt);
		featureT = Anim.exp(featureT, featureOpen && featureId != null ? 1f : 0f, 18f, dt);
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

	private float localX(double mx) {
		return (float) ((mx - viewCx) / viewScale + viewCx);
	}

	private float localY(double my) {
		return (float) ((my - viewCy - viewLift) / viewScale + viewCy);
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

		float y = windowY + 28;
		Group last = null;
		float activeY = y;
		float footY = windowY + windowH - 22;
		for (Tab value : Tab.values()) {
			if (value == Tab.PLAYER) {
				continue;
			}
			if (value.group != last) {
				y += 4;
				GuiDraw.small(graphics, font, value.group.label, windowX + 10, y, Theme.HEADER);
				y += 9;
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
			y += 16;
		}
		if (tab != Tab.PLAYER) {
			if (navY < 0f) {
				navY = activeY;
			}
			navY = Anim.exp(navY, activeY, 18f, dt);
			GuiDraw.rounded(graphics, windowX + 6, navY, SIDEBAR_W - 12, 16, 8, Theme.NAV_PILL);
			float labelY = GuiDraw.middle(navY, 16);
			GuiDraw.icon(graphics, font, tabGlyph(tab), windowX + 11, labelY, Theme.TEXT);
			GuiDraw.menu(graphics, font, tab.label, windowX + 24, labelY, Theme.TEXT);
		} else {
			navY = -1f;
		}

		GuiDraw.fill(graphics, windowX + 8, footY - 5, SIDEBAR_W - 16, 1, Theme.ACCENT);
		int face = 14;
		int faceX = Math.round(windowX + 10);
		int faceY = Math.round(footY);
		boolean footHover = GuiDraw.hovered(mouseX, mouseY, windowX + 6, footY - 2, SIDEBAR_W - 12, face + 4);
		boolean footOn = tab == Tab.PLAYER;
		if (footOn || footHover) {
			GuiDraw.rounded(graphics, windowX + 6, footY - 2, SIDEBAR_W - 12, face + 4, 6, footOn ? Theme.NAV_PILL : Anim.fade(0x18FFFFFF, 1f));
		}
		PlayerSkin skin = playerSkin();
		if (skin != null && skin.body() != null) {
			PlayerFaceExtractor.extractRenderState(graphics, skin, faceX, faceY, face);
		} else {
			GuiDraw.rounded(graphics, faceX, faceY, face, face, 3, Theme.ACCENT);
		}
		float nameX = windowX + 10 + face + 4;
		GuiDraw.menu(graphics, font, fitName(font, playerName(), (int) (SIDEBAR_W - 18 - face)), nameX, GuiDraw.middle(footY, face), Theme.TEXT);
		hits.add(new Hit(windowX + 6, footY - 2, SIDEBAR_W - 12, face + 4, () -> selectTab(Tab.PLAYER)));
	}

	private void selectTab(Tab value) {
		tab = value;
		pickerTarget = null;
		searchOpen = false;
		featureOpen = false;
		capeFocused = tab == Tab.PLAYER;
		nickFocused = tab == Tab.PLAYER;
		mobSearchFocused = false;
		if (value == Tab.ESP) {
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

		float y = featureCard(graphics, font, left, top, col, cardHeight(4), "Glow");
		y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Mob glow", config.mobGlowEnabled, v -> config.mobGlowEnabled = v, Feature.MOB);
		y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Block outline", config.blockOutlineGlow, v -> config.blockOutlineGlow = v, Feature.BLOCK);
		y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Nametags", config.nametagsEnabled, v -> config.nametagsEnabled = v, Feature.NAMETAGS);
		y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Node ESP", config.boxFill, v -> config.boxFill = v, Feature.NODE_ESP);

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
		GuiDraw.small(graphics, font, nearby == 0 ? "None nearby" : nearby + " nearby", ix + 1, top + cardHeight(3) + 6, Theme.MUTED);
		GuiDraw.small(graphics, font, "Click a type to add it, again to drop it.", ix + 1, top + cardHeight(3) + 16, Theme.MUTED);
	}

	private void drawPlayerTab(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float left = contentX();
		float top = windowY + TOOLBAR_H + 6;
		float col = colW();
		float right = left + col + COL_GAP;
		float ix = innerX(left);
		float iw = innerW(col);
		VoidmarkConfig config = VoidmarkConfig.get();
		float modelH = windowY + windowH - PAD - top;
		featureCard(graphics, font, left, top, col, modelH, "You");
		previewX = ix;
		previewY = top + CARD_HEAD;
		previewW = iw;
		previewH = Math.max(48, modelH - CARD_HEAD - CARD_PAD);
		if (!previewDrag) {
			previewYaw += dt * 22f;
			if (previewYaw > 360f || previewYaw < -360f) {
				previewYaw %= 360f;
			}
		}
		boolean previewHover = GuiDraw.hovered(mouseX, mouseY, previewX, previewY, previewW, previewH);
		PlayerPreview.Drawn drawn = PlayerPreview.draw(
			graphics,
			previewX,
			previewY,
			previewW,
			previewH,
			previewYaw,
			previewPitch,
			new PlayerPreview.View(viewScale, viewCx, viewCy, viewLift)
		);
		NickHider.suppress();
		Component tag = config.nickEnabled ? NickHider.formattedNick() : Component.literal(playerName());
		NickHider.resume();
		if (drawn == null) {
			PlayerSkin skin = playerSkin();
			if (skin != null && skin.body() != null) {
				int face = 28;
				int fx = Math.round(previewX + (previewW - face) * 0.5f);
				int fy = Math.round(previewY + previewH * 0.55f - face * 0.5f);
				PlayerFaceExtractor.extractRenderState(graphics, skin, fx, fy, face);
				NametagRenderer.drawVanilla(graphics, font, previewX + previewW * 0.5f, fy - 12, tag);
			} else {
				GuiDraw.menu(graphics, font, "Join a world to rotate", previewX + 4, previewY + previewH - 16, Theme.MUTED);
			}
		} else {
			NametagRenderer.drawVanilla(graphics, font, drawn.nameX(), drawn.nameY(), tag);
		}
		if (previewHover) {
			GuiDraw.small(graphics, font, "Drag to rotate", previewX + 4, previewY + previewH - 12, Theme.MUTED);
		}
		hits.add(new Hit(previewX, previewY, previewW, previewH, () -> previewDrag = true));

		float y = featureCard(graphics, font, right, top, col, cardHeight(1) + 56, "Nick");
		float rx = innerX(right);
		y = toggle(graphics, font, rx, y, iw, mouseX, mouseY, "Replace my name", config.nickEnabled, v -> config.nickEnabled = v);
		GuiDraw.small(graphics, font, "Chat, tab, scoreboard. Use &6 &l.", rx, y + 1, Theme.MUTED);
		y += 12;
		nickFieldX = rx;
		nickFieldY = y;
		nickFieldW = iw;
		boolean hoverNick = GuiDraw.hovered(mouseX, mouseY, rx, y, iw, 16);
		GuiDraw.panel(graphics, rx, y, iw, 16, 5, nickFocused || hoverNick ? Theme.CARD_HOVER : Theme.CARD, nickFocused ? Theme.ACCENT : Theme.LINE);
		NickHider.suppress();
		String raw = config.nick == null ? "" : config.nick;
		String shown = raw.isEmpty() && !nickFocused ? "Nick  (&6Name)" : raw + (nickFocused ? "|" : "");
		GuiDraw.menu(graphics, font, clip(font, shown, (int) iw - 12), rx + 5, GuiDraw.middle(y, 16), raw.isEmpty() && !nickFocused ? Theme.MUTED : Theme.TEXT);
		NickHider.resume();
		hits.add(new Hit(rx, y, iw, 16, () -> {
			nickFocused = true;
			capeFocused = false;
			searchOpen = false;
		}));
		y += 22;
		GuiDraw.rounded(graphics, rx, y, iw, 22, 5, Theme.PANEL);
		NickHider.suppress();
		Component preview = config.nickEnabled ? NickHider.formattedNick() : Component.literal(playerName());
		if (preview.getString().isEmpty()) {
			GuiDraw.menu(graphics, font, "Name hidden", rx + 6, GuiDraw.middle(y, 22), Theme.MUTED);
		} else {
			graphics.pose().pushMatrix();
			graphics.pose().translate(rx + 6, GuiDraw.middle(y, 22));
			graphics.text(font, preview, 0, 0, 0xFFFFFFFF, false);
			graphics.pose().popMatrix();
		}
		NickHider.resume();

		drawCapeColumn(graphics, font, mouseX, mouseY, right, top + cardHeight(1) + 64, col);
	}

	private void drawCapeColumn(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float right, float top, float col) {
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

		float y = featureCard(graphics, font, right, top, col, cardHeight(4), "Cape");
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
			case ESP -> MenuFont.MOB;
			case OVERLAY -> MenuFont.MONITOR;
			case BARS -> MenuFont.HUD;
			case NODES -> MenuFont.CUBE;
			case MINING -> MenuFont.CUBE;
			case PLAYER -> MenuFont.PERSON;
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
			notesOpen = false;
			searchOpen = false;
			featureOpen = false;
		});
		drawIconButton(graphics, font, mouseX, mouseY, iconX + ICON_SLOT, y, MenuFont.BELL, notesOpen, () -> {
			notesOpen = !notesOpen;
			settingsOpen = false;
			searchOpen = false;
			featureOpen = false;
			if (notesOpen) {
				ReleaseNotes.markSeen();
			}
		});
		if (ReleaseNotes.unread() && !notesOpen) {
			GuiDraw.circle(graphics, iconX + ICON_SLOT + ICON_SLOT - 2.5f, y + 3.2f, 2.1f, Theme.ACCENT);
		}
		drawIconButton(graphics, font, mouseX, mouseY, iconX + ICON_SLOT * 2, y, MenuFont.SEARCH, searchOpen, () -> {
			searchOpen = !searchOpen;
			settingsOpen = false;
			notesOpen = false;
			featureOpen = false;
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
				config.aspectEnabled = false;
				config.aspectRatio = 1.0f;
				config.fogEnabled = false;
				config.fogRgb = 0x8EC8FF;
				config.fogStart = 0.12f;
				config.fogEnd = 0.72f;
				config.fogDensity = 1.0f;
				config.matchFogToWorld = false;
			}
			case ESP -> {
				config.mobGlowEnabled = false;
				config.mobGlowThroughWalls = true;
				config.blockOutlineGlow = true;
				config.blockOutlineRgb = 0x2FB5FF;
				config.blockOutlineOpacity = 0.58f;
				config.mobGlowId = "";
				config.mobGlowIds = new ArrayList<>();
				config.mobGlowSize = 0.48f;
				config.mobGlowOpacity = 0.58f;
				config.mobGlowRgb = 0x2FB5FF;
				config.boxFill = true;
				config.boxOutline = true;
				config.tracersEnabled = true;
				config.throughWalls = true;
				config.fillOpacity = 0.32f;
				config.colorRgb = 0x2FB5FF;
				config.nametagsEnabled = true;
				config.nametagThroughWalls = false;
				config.nametagDistance = true;
				config.nametagRange = 128;
				config.nametagScale = 1.0f;
				config.nametagOpacity = 1.0f;
				mobQuery = "";
				mobScroll = 0f;
			}
			case OVERLAY -> {
				config.watermarkEnabled = true;
				config.hudWatermarkX = -1f;
				config.hudWatermarkY = -1f;
				config.hudWatermarkScale = 1.0f;
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
				config.inventoryHudEnabled = true;
				config.inventoryHudHotbar = true;
				config.inventoryHudArmor = true;
				config.inventoryHudCount = true;
				config.inventoryHudAnchor = "bottom_right";
				config.inventoryHudScale = 1.0f;
				config.hudInventoryX = -1f;
				config.hudInventoryY = -1f;
			}
			case BARS -> {
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
			case NODES -> {
				config.markersEnabled = true;
				config.onlyInTheEnd = true;
				config.forceEnable = false;
				config.blockScan = true;
				config.particleDetection = true;
				config.scanRadius = 48;
				config.hudEnabled = true;
				config.hudNodesX = -1f;
				config.hudNodesY = -1f;
				config.hudNodesScale = 1.0f;
			}
			case MINING -> {
				config.miningHudEnabled = true;
				config.miningAbilityAlert = true;
				config.titaniumEsp = true;
				config.titaniumEspThroughWalls = true;
				config.titaniumEspRange = 48;
				config.titaniumEspRgb = 0xE8ECF2;
				config.hudMiningX = -1f;
				config.hudMiningY = -1f;
				config.hudMiningScale = 1.0f;
			}
			case PLAYER -> {
				config.nickEnabled = false;
				config.nick = "";
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
		GuiDraw.sheet(graphics, settingsX, settingsY, PANEL_W, SETTINGS_H * Math.max(0.2f, settingsT), 8, Anim.fade(Theme.SHEET, settingsT), Anim.fade(Theme.ACCENT, settingsT));
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
		GuiDraw.small(graphics, font, "Scale", settingsX + 8, y + 1, Theme.MUTED);
		y += 12;
		y = chipRow(graphics, font, settingsX + 8, y, PANEL_W - 16, mouseX, mouseY, new String[]{"100%", "90%", "75%", "50%"}, menuScaleChip(), index -> {
			float[] values = {1.00f, 0.90f, 0.75f, 0.50f};
			VoidmarkConfig.get().menuScale = values[index];
		});
		y = slider(graphics, font, settingsX + 8, y, PANEL_W - 16, "HUD", Math.round(VoidmarkConfig.get().hudOpacity * 100) + "%", (VoidmarkConfig.get().hudOpacity - 0.20f) / 0.80f, v -> {
			VoidmarkConfig.get().hudOpacity = VoidmarkConfig.clamp(0.20f + v * 0.80f, 0.20f, 1f);
			Theme.refresh();
		});
		y = toggle(graphics, font, settingsX + 8, y, PANEL_W - 16, mouseX, mouseY, "HUD stars", VoidmarkConfig.get().hudStarfield, v -> VoidmarkConfig.get().hudStarfield = v);
		toggle(graphics, font, settingsX + 8, y, PANEL_W - 16, mouseX, mouseY, "Animations", VoidmarkConfig.get().uiAnimations, v -> VoidmarkConfig.get().uiAnimations = v);
	}

	private static int menuScaleChip() {
		float scale = VoidmarkConfig.normalizeMenuScale(VoidmarkConfig.get().menuScale);
		if (Math.abs(scale - 1.00f) < 0.01f) {
			return 0;
		}
		if (Math.abs(scale - 0.90f) < 0.01f) {
			return 1;
		}
		if (Math.abs(scale - 0.75f) < 0.01f) {
			return 2;
		}
		return 3;
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

	private void drawNotes(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		notesX = contentX() + contentW() - PANEL_W;
		notesY = windowY + TOOLBAR_H + 2;
		notesH = Math.min(windowH - TOOLBAR_H - 10, 210);
		GuiDraw.sheet(graphics, notesX, notesY, PANEL_W, notesH * Math.max(0.2f, notesT), 8, Anim.fade(Theme.SHEET, notesT), Anim.fade(Theme.ACCENT, notesT));
		if (notesT < 0.85f) {
			return;
		}
		GuiDraw.menu(graphics, font, "What's new", notesX + 8, notesY + 6, Theme.HEADER);
		float listX = notesX + 8;
		float listY = notesY + 20;
		float listW = PANEL_W - 16;
		float listH = notesH - 28;
		float contentH = ReleaseNotes.contentHeight(11);
		float maxScroll = Math.max(0f, contentH - listH);
		notesScroll = Mth.clamp(notesScroll, 0f, maxScroll);
		boolean clipped = GuiDraw.scissor(graphics, listX, listY, listW, listH);
		float y = listY - notesScroll;
		for (ReleaseNotes.Entry entry : ReleaseNotes.ENTRIES) {
			GuiDraw.small(graphics, font, entry.version(), listX, y, Theme.ACCENT);
			y += 12;
			for (String line : entry.lines()) {
				GuiDraw.menu(graphics, font, clip(font, line, (int) listW), listX, y, Theme.TEXT);
				y += 11;
			}
			y += 6;
		}
		if (clipped) {
			GuiDraw.disableScissor(graphics);
		}
		hits.add(new Hit(notesX, notesY, PANEL_W, notesH, () -> {
		}));
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
				float y = featureCard(graphics, font, left, top, col, cardHeight(2), "World");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "World tint", config.worldTintEnabled, v -> config.worldTintEnabled = v, Feature.WORLD);
				toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Skybox", config.skyTintEnabled, v -> config.skyTintEnabled = v, Feature.SKY);

				y = featureCard(graphics, font, right, top, col, cardHeight(2), "Camera");
				y = toggle(graphics, font, rx, y, iw, mouseX, mouseY, "Fog", config.fogEnabled, v -> config.fogEnabled = v, Feature.FOG);
				toggle(graphics, font, rx, y, iw, mouseX, mouseY, "Aspect ratio", config.aspectEnabled, v -> config.aspectEnabled = v, Feature.VIEW);
			}
			case ESP -> drawMobsTab(graphics, font, mouseX, mouseY);
			case OVERLAY -> {
				float y = featureCard(graphics, font, left, top, col, cardHeight(4), "HUD");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Watermark", config.watermarkEnabled, v -> config.watermarkEnabled = v, Feature.WATERMARK);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Music", config.musicHudEnabled, v -> config.musicHudEnabled = v, Feature.MUSIC);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Raw mats", config.rawmatsHudEnabled, v -> config.rawmatsHudEnabled = v, Feature.RAWMATS);
				toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Inventory HUD", config.inventoryHudEnabled, v -> config.inventoryHudEnabled = v, Feature.INVENTORY);
			}
			case BARS -> {
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
			case NODES -> {
				float y = featureCard(graphics, font, left, top, col, cardHeight(2), "Markers");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Enable", config.markersEnabled, v -> config.markersEnabled = v, Feature.NODES);
				toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Node HUD", config.hudEnabled, v -> config.hudEnabled = v);

				y = featureCard(graphics, font, right, top, col, cardHeight(6), "Status");
				y = readout(graphics, font, rx, y, iw, "Hypixel", SkyblockLocation.onHypixel);
				y = readout(graphics, font, rx, y, iw, "Skyblock", SkyblockLocation.inSkyblock);
				y = readout(graphics, font, rx, y, iw, "The End", SkyblockLocation.inTheEnd);
				y = statRow(graphics, font, rx, y, iw, "FPS", HudStats.fps() + "");
				y = statRow(graphics, font, rx, y, iw, "Ping", HudStats.pingLabel());
				String area = SkyblockLocation.area.isEmpty() ? "Unknown" : SkyblockLocation.area;
				GuiDraw.menu(graphics, font, clip(font, area, (int) iw - 4), rx, GuiDraw.middle(y, ROW), Theme.MUTED);
			}
			case MINING -> {
				float y = featureCard(graphics, font, left, top, col, cardHeight(2), "Mining");
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Mining HUD", config.miningHudEnabled, v -> config.miningHudEnabled = v, Feature.MINING);
				toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Titanium ESP", config.titaniumEsp, v -> config.titaniumEsp = v, Feature.TITANIUM);

				y = featureCard(graphics, font, right, top, col, CARD_HEAD + 54 + CARD_PAD, "Live");
				var snap = MiningTracker.snapshot();
				GuiDraw.menu(graphics, font, snap.ability(), rx, y + 2, Theme.TEXT);
				GuiDraw.menu(graphics, font, snap.abilityReady() ? "Ready" : snap.abilityLabel(), rx, y + 14, snap.abilityReady() ? Theme.ACCENT : Theme.MUTED);
				String jobs = snap.commissions().isEmpty() ? "No commissions" : snap.commissions().size() + " commission" + (snap.commissions().size() == 1 ? "" : "s");
				GuiDraw.menu(graphics, font, jobs, rx, y + 26, Theme.MUTED);
				String titanium;
				int titaniumColor = Theme.MUTED;
				if (!MiningTracker.hasTitaniumCommission()) {
					titanium = "No titanium job";
				} else {
					MiningAreas.TitaniumFilter filter = MiningTracker.titaniumFilter();
					int count = TitaniumTracker.get().count();
					titanium = filter.unrestricted()
						? count + " titanium"
						: count + " in " + filter.label();
					titaniumColor = Theme.ACCENT;
				}
				GuiDraw.menu(graphics, font, clip(font, titanium, (int) iw - 4), rx, y + 38, titaniumColor);
			}
			case PLAYER -> drawPlayerTab(graphics, font, mouseX, mouseY);
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
		return toggle(graphics, font, x, y, w, mouseX, mouseY, label, value, setter, null);
	}

	private float toggle(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, int mouseX, int mouseY, String label, boolean value, Consumer<Boolean> setter, Feature feature) {
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
		if (feature != null) {
			float cogX = tx - COG_W - 2;
			boolean cogOn = featureOpen && featureId == feature;
			boolean cogHover = GuiDraw.hovered(mouseX, mouseY, cogX, y, COG_W, ROW);
			GuiDraw.icon(graphics, font, MenuFont.SETTINGS, cogX + 1, labelY, cogOn || cogHover ? Theme.ACCENT : Theme.MUTED);
			hits.add(new Hit(cogX, y, COG_W, ROW, () -> openFeature(feature)));
		}
		int fill = t > 0.5f ? Theme.ACCENT : Theme.TRACK;
		GuiDraw.pill(graphics, tx, ty, trackW, trackH, fill);
		float knob = tx + 6 + t * (trackW - 12);
		GuiDraw.circle(graphics, knob, ty + trackH / 2f, 4.6f, t > 0.5f ? Theme.TEXT : Theme.OFF);
		float hitW = feature == null ? w : w - COG_W - 2;
		float hitX = feature == null ? x : x;
		if (feature != null) {
			hits.add(new Hit(x, y, tx - COG_W - 4 - x, ROW, () -> {
				setter.accept(!value);
				UnloadState.markDirty();
			}));
			hits.add(new Hit(tx, y, trackW, ROW, () -> {
				setter.accept(!value);
				UnloadState.markDirty();
			}));
		} else {
			hits.add(new Hit(hitX, y, hitW, ROW, () -> {
				setter.accept(!value);
				UnloadState.markDirty();
			}));
		}
		return y + ROW;
	}

	private void openFeature(Feature feature) {
		if (featureOpen && featureId == feature) {
			featureOpen = false;
			return;
		}
		featureId = feature;
		featureOpen = true;
		settingsOpen = false;
		notesOpen = false;
		searchOpen = false;
		pickerTarget = null;
	}

	private void drawFeaturePanel(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		Feature feature = featureId;
		if (feature == null) {
			return;
		}
		float h = feature.height();
		featureX = contentX() + contentW() - FEATURE_W;
		featureY = windowY + TOOLBAR_H + 2;
		hits.add(new Hit(featureX, featureY, FEATURE_W, h, () -> {
		}));
		GuiDraw.sheet(graphics, featureX, featureY, FEATURE_W, h * Math.max(0.2f, featureT), 8, Anim.fade(Theme.SHEET, featureT), Anim.fade(Theme.ACCENT, featureT));
		if (featureT < 0.85f) {
			return;
		}
		GuiDraw.menu(graphics, font, feature.title, featureX + 8, featureY + 6, Theme.HEADER);
		float ix = featureX + 8;
		float y = featureY + 20;
		float iw = FEATURE_W - 16;
		VoidmarkConfig config = VoidmarkConfig.get();
		switch (feature) {
			case WORLD -> {
				y = cycle(graphics, font, ix, y, iw, mouseX, mouseY, "Mode", config.worldTintModeLabel(), config::cycleWorldTintMode);
				y = colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Color", config.worldTintRgb, PickerTarget.WORLD);
				y = slider(graphics, font, ix, y, iw, "Strength", String.format(Locale.ROOT, "%.0f", config.worldTintStrength * 100), config.worldTintStrength, v -> config.worldTintStrength = v);
				if (config.worldTintUsesLightmap()) {
					hint(graphics, font, ix, y, iw, "Turn fullbright off");
				}
			}
			case SKY -> {
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Match world", config.matchSkyToWorld, v -> config.matchSkyToWorld = v);
				int skyPreview = config.matchSkyToWorld ? config.worldTintRgb : config.skyTintRgb;
				y = colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Color", skyPreview, PickerTarget.SKY);
				slider(graphics, font, ix, y, iw, "Strength", String.format(Locale.ROOT, "%.0f", config.skyTintStrength * 100), config.skyTintStrength, v -> config.skyTintStrength = v);
			}
			case FOG -> {
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Match world", config.matchFogToWorld, v -> config.matchFogToWorld = v);
				int fogPreview = config.matchFogToWorld ? config.worldTintRgb : config.fogRgb;
				y = colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Color", fogPreview, PickerTarget.FOG);
				y = slider(graphics, font, ix, y, iw, "Start", String.format(Locale.ROOT, "%.0f%%", config.fogStart * 100), config.fogStart / 0.95f, v -> config.fogStart = VoidmarkConfig.clamp(v * 0.95f, 0f, 0.95f));
				y = slider(graphics, font, ix, y, iw, "End", String.format(Locale.ROOT, "%.0f%%", config.fogEnd * 100), (config.fogEnd - 0.05f) / 0.95f, v -> config.fogEnd = VoidmarkConfig.clamp(0.05f + v * 0.95f, 0.05f, 1f));
				slider(graphics, font, ix, y, iw, "Density", String.format(Locale.ROOT, "%.0f", config.fogDensity * 100), config.fogDensity, v -> config.fogDensity = v);
			}
			case VIEW -> {
				y = slider(graphics, font, ix, y, iw, "Aspect", aspectLabel(config.aspectRatio), (config.aspectRatio - 0.50f) / 0.70f, v -> config.aspectRatio = VoidmarkConfig.clamp(0.50f + v * 0.70f, 0.50f, 1.20f));
				chipRow(graphics, font, ix, y, iw, mouseX, mouseY, new String[]{"Native", "16:10", "4:3", "5:4"}, aspectChipIndex(config.aspectRatio), index -> {
					float[] values = {1.00f, 0.90f, 0.75f, 0.70f};
					config.aspectEnabled = true;
					config.aspectRatio = values[index];
				});
			}
			case MOB -> {
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Through walls", config.mobGlowThroughWalls, v -> config.mobGlowThroughWalls = v);
				y = slider(graphics, font, ix, y, iw, "Size", String.format(Locale.ROOT, "%.0f", config.mobGlowSize * 100), (config.mobGlowSize - 0.12f) / 1.08f, v -> config.mobGlowSize = VoidmarkConfig.clamp(0.12f + v * 1.08f, 0.12f, 1.20f));
				y = slider(graphics, font, ix, y, iw, "Opacity", Math.round(config.mobGlowOpacity * 100) + "%", (config.mobGlowOpacity - 0.15f) / 0.75f, v -> config.mobGlowOpacity = VoidmarkConfig.clamp(0.15f + v * 0.75f, 0.15f, 0.90f));
				colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Color", config.mobGlowRgb, PickerTarget.MOB);
			}
			case BLOCK -> {
				y = slider(graphics, font, ix, y, iw, "Opacity", Math.round(config.blockOutlineOpacity * 100) + "%", (config.blockOutlineOpacity - 0.15f) / 0.75f, v -> config.blockOutlineOpacity = VoidmarkConfig.clamp(0.15f + v * 0.75f, 0.15f, 0.90f));
				colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Color", config.blockOutlineRgb, PickerTarget.BLOCK);
			}
			case NODE_ESP -> {
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Outline", config.boxOutline, v -> config.boxOutline = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Tracer", config.tracersEnabled, v -> config.tracersEnabled = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Through walls", config.throughWalls, v -> config.throughWalls = v);
				y = slider(graphics, font, ix, y, iw, "Fill opacity", Math.round(config.fillOpacity * 100) + "%", (config.fillOpacity - 0.08f) / 0.77f, v -> config.fillOpacity = VoidmarkConfig.clamp(0.08f + v * 0.77f, 0.08f, 0.85f));
				colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Color", config.colorRgb, PickerTarget.NODE);
			}
			case WATERMARK -> {
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "FPS", config.watermarkFps, v -> config.watermarkFps = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Ping", config.watermarkPing, v -> config.watermarkPing = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Clock", config.watermarkTime, v -> config.watermarkTime = v);
				toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Name", config.watermarkName, v -> config.watermarkName = v);
			}
			case MUSIC -> toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Hide when idle", config.musicHideIdle, v -> config.musicHideIdle = v);
			case RAWMATS -> cycle(graphics, font, ix, y, iw, mouseX, mouseY, "Materials", config.rawmatsModeLabel(), config::cycleRawmatsMode);
			case MINING -> toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Ability alert", config.miningAbilityAlert, v -> config.miningAbilityAlert = v);
			case TITANIUM -> {
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Through walls", config.titaniumEspThroughWalls, v -> config.titaniumEspThroughWalls = v);
				y = slider(graphics, font, ix, y, iw, "Range", config.titaniumEspRange + "m", (config.titaniumEspRange - 24) / 56f, v -> config.titaniumEspRange = VoidmarkConfig.clamp(24 + Math.round(v * 56f), 24, 80));
				colorRow(graphics, font, ix, y, iw, mouseX, mouseY, "Color", config.titaniumEspRgb, PickerTarget.TITANIUM);
			}
			case INVENTORY -> {
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Hotbar", config.inventoryHudHotbar, v -> config.inventoryHudHotbar = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Armor", config.inventoryHudArmor, v -> config.inventoryHudArmor = v);
				toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Item count", config.inventoryHudCount, v -> config.inventoryHudCount = v);
			}
			case NAMETAGS -> {
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Through walls", config.nametagThroughWalls, v -> config.nametagThroughWalls = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Show distance", config.nametagDistance, v -> config.nametagDistance = v);
				y = slider(graphics, font, ix, y, iw, "Size", Math.round(config.nametagScale * 100) + "%", (config.nametagScale - 0.50f) / 1.50f, v -> config.nametagScale = VoidmarkConfig.clamp(0.50f + v * 1.50f, 0.50f, 2.00f));
				y = slider(graphics, font, ix, y, iw, "Opacity", Math.round(config.nametagOpacity * 100) + "%", (config.nametagOpacity - 0.15f) / 0.85f, v -> config.nametagOpacity = VoidmarkConfig.clamp(0.15f + v * 0.85f, 0.15f, 1f));
				slider(graphics, font, ix, y, iw, "Range", config.nametagRange + "m", (config.nametagRange - 64) / 192f, v -> config.nametagRange = VoidmarkConfig.clamp(64 + Math.round(v * 192f), 64, 256));
			}
			case NODES -> {
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Only in The End", config.onlyInTheEnd, v -> config.onlyInTheEnd = v);
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Force enable", config.forceEnable, v -> config.forceEnable = v);
				y = slider(graphics, font, ix, y, iw, "Scan radius", config.scanRadius + "m", (config.scanRadius - 16) / 64f, v -> config.scanRadius = VoidmarkConfig.clamp(16 + Math.round(v * 64f), 16, 80));
				y = toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Block scan", config.blockScan, v -> config.blockScan = v);
				toggle(graphics, font, ix, y, iw, mouseX, mouseY, "Particle hints", config.particleDetection, v -> config.particleDetection = v);
			}
		}
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
		float rowY = y;
		for (int i = 0; i < labels.length; i++) {
			float cw = GuiDraw.menuWidth(font, labels[i]) + 10;
			if (cx > x && cx + cw > x + w) {
				cx = x;
				rowY += ROW;
			}
			boolean on = i == selected;
			boolean hover = GuiDraw.hovered(mouseX, mouseY, cx, rowY + 1, cw, ROW - 2);
			GuiDraw.panel(graphics, cx, rowY + 1, cw, ROW - 2, 5, on ? Theme.ACCENT : hover ? Theme.CARD_HOVER : Theme.CARD, on ? Theme.ACCENT : Theme.LINE);
			GuiDraw.menu(graphics, font, labels[i], cx + 5, GuiDraw.middle(rowY, ROW), on ? Theme.WINDOW_SOLID : Theme.TEXT);
			int index = i;
			hits.add(new Hit(cx, rowY, cw, ROW, () -> pick.accept(index)));
			cx += cw + 4;
		}
		return rowY + ROW;
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
		GuiDraw.sheet(graphics, x, y, w, h, 8, Anim.fade(Theme.SHEET, pickerT), Anim.fade(Theme.ACCENT, pickerT));
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
			case BLOCK -> config.blockOutlineRgb = packed;
			case TITANIUM -> config.titaniumEspRgb = packed;
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
			.orElse("1.1.95");
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (event.button() != 0) {
			return super.mouseClicked(event, doubled);
		}
		lastClickY = localY(event.y());
		dragging = false;
		double lx = localX(event.x());
		double ly = lastClickY;
		boolean onCape = GuiDraw.hovered(lx, ly, capeFieldX, capeFieldY, capeFieldW, ROW);
		boolean onNick = GuiDraw.hovered(lx, ly, nickFieldX, nickFieldY, nickFieldW, 16);
		if (capeFocused && !onCape) {
			capeFocused = false;
			commitCapeUrl();
		}
		if (nickFocused && !onNick) {
			nickFocused = false;
		}
		boolean onMobSearch = GuiDraw.hovered(lx, ly, mobFieldX, mobFieldY, mobFieldW, 14);
		if (mobSearchFocused && !onMobSearch) {
			mobSearchFocused = false;
		}
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(lx, ly)) {
				hit.click(lx);
				return true;
			}
		}
		if (pickerTarget != null && !GuiDraw.hovered(lx, ly, pickerX, pickerY, PICKER_W, PICKER_H)) {
			pickerTarget = null;
			return true;
		}
		if (settingsOpen && !GuiDraw.hovered(lx, ly, settingsX, settingsY, PANEL_W, SETTINGS_H)) {
			settingsOpen = false;
			return true;
		}
		if (notesOpen && !GuiDraw.hovered(lx, ly, notesX, notesY, PANEL_W, notesH)) {
			notesOpen = false;
			return true;
		}
		if (featureOpen && featureId != null && !GuiDraw.hovered(lx, ly, featureX, featureY, FEATURE_W, featureId.height())) {
			featureOpen = false;
			return true;
		}
		if (searchOpen && !GuiDraw.hovered(lx, ly, searchFieldX, windowY + 5, searchFieldW, 80)) {
			searchOpen = false;
			return true;
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (event.button() == 0 && previewDrag) {
			previewYaw += (float) dx * 0.7f;
			previewPitch = Mth.clamp(previewPitch - (float) dy * 0.45f, -35f, 35f);
			return true;
		}
		if (event.button() == 0 && dragging) {
			windowX = localX(event.x()) - (float) dragOffX;
			windowY = localY(event.y()) - (float) dragOffY;
			moved = true;
			return true;
		}
		if (event.button() == 0) {
			lastClickY = localY(event.y());
			double lx = localX(event.x());
			for (int i = hits.size() - 1; i >= 0; i--) {
				Hit hit = hits.get(i);
				if (hit.drag && hit.contains(lx, lastClickY)) {
					hit.click(lx);
					return true;
				}
			}
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		previewDrag = false;
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
		double lx = localX(mouseX);
		double ly = localY(mouseY);
		if (notesOpen && scrollY != 0 && GuiDraw.hovered(lx, ly, notesX, notesY, PANEL_W, notesH)) {
			float maxScroll = Math.max(0f, ReleaseNotes.contentHeight(11) - (notesH - 28));
			notesScroll = Mth.clamp(notesScroll - (float) scrollY * 18f, 0f, maxScroll);
			return true;
		}
		if (tab == Tab.ESP && scrollY != 0 && GuiDraw.hovered(lx, ly, mobFieldX, mobFieldY, mobListW, mobListY + mobListH - mobFieldY)) {
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
			if (notesOpen) {
				notesOpen = false;
				return true;
			}
			if (pickerTarget != null) {
				pickerTarget = null;
				return true;
			}
			if (featureOpen) {
				featureOpen = false;
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
			notesOpen = false;
			featureOpen = false;
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
