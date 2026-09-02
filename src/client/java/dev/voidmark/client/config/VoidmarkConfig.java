package dev.voidmark.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.voidmark.Voidmark;
import dev.voidmark.client.render.MobCatalog;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VoidmarkConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("voidmark.json");
	private static VoidmarkConfig instance = new VoidmarkConfig();

	public boolean markersEnabled = true;
	public boolean hudEnabled = true;
	public boolean tracersEnabled = true;
	public boolean boxFill = true;
	public boolean boxOutline = true;
	public boolean throughWalls = true;
	public boolean onlyInTheEnd = true;
	public boolean forceEnable = false;
	public boolean particleDetection = true;
	public boolean blockScan = true;
	public int scanRadius = 48;
	public float fillOpacity = 0.32f;
	public int colorRgb = 0x2FB5FF;
	public boolean worldTintEnabled = false;
	public int worldTintRgb = 0x2FB5FF;
	public float worldTintStrength = 0.70f;
	public String worldTintMode = "shader";
	public boolean skyTintEnabled = false;
	public int skyTintRgb = 0x1B4F8A;
	public float skyTintStrength = 0.70f;
	public boolean matchSkyToWorld = true;
	public boolean fogEnabled = false;
	public int fogRgb = 0x8EC8FF;
	public float fogStart = 0.12f;
	public float fogEnd = 0.72f;
	public float fogDensity = 1.0f;
	public boolean matchFogToWorld = false;
	public boolean aspectEnabled = false;
	public float aspectRatio = 1.0f;
	public int themeAccentRgb = 0x2FB5FF;
	public int themePaneRgb = 0x0B0E14;
	public String themePreset = "cyan";
	public boolean uiAnimations = true;
	public boolean watermarkEnabled = true;
	public boolean watermarkFps = true;
	public boolean watermarkPing = true;
	public boolean watermarkTime = true;
	public boolean watermarkName = false;
	public boolean musicHudEnabled = true;
	public boolean musicHideIdle = false;
	public boolean rawmatsHudEnabled = true;
	public boolean miningHudEnabled = true;
	public boolean miningAbilityAlert = true;
	public boolean rawmatsEnchanted = false;
	public String rawmatsItemId = "";
	public boolean inventoryHudEnabled = true;
	public boolean inventoryHudHotbar = true;
	public boolean inventoryHudArmor = true;
	public boolean inventoryHudCount = true;
	public boolean hudHotbar = true;
	public boolean hudHealth = true;
	public boolean hudHunger = true;
	public boolean hudArmor = true;
	public boolean hudAir = true;
	public boolean hudExperience = true;
	public boolean hudScoreboard = true;
	public boolean hudBossBar = true;
	public boolean hudEffects = true;
	public boolean hudHeldItem = true;
	public boolean hudMountHealth = true;
	public HudSlot slotHotbar = new HudSlot();
	public HudSlot slotHealth = new HudSlot();
	public HudSlot slotHunger = new HudSlot();
	public HudSlot slotArmor = new HudSlot();
	public HudSlot slotAir = new HudSlot();
	public HudSlot slotExperience = new HudSlot();
	public HudSlot slotMount = new HudSlot();
	public HudSlot slotScoreboard = new HudSlot();
	public HudSlot slotBoss = new HudSlot();
	public HudSlot slotEffects = new HudSlot();
	public HudSlot slotHeldItem = new HudSlot();
	public String inventoryHudAnchor = "bottom_right";
	public float inventoryHudScale = 1.0f;
	public float hudWatermarkScale = 1.0f;
	public float hudNodesScale = 1.0f;
	public float hudMusicScale = 1.0f;
	public float hudRawmatsScale = 1.0f;
	public float hudMiningScale = 1.0f;
	public float hudInventoryX = -1f;
	public float hudInventoryY = -1f;
	public float hudWatermarkX = -1f;
	public float hudWatermarkY = -1f;
	public float hudNodesX = -1f;
	public float hudNodesY = -1f;
	public float hudMusicX = -1f;
	public float hudMusicY = -1f;
	public float hudRawmatsX = -1f;
	public float hudRawmatsY = -1f;
	public float hudMiningX = -1f;
	public float hudMiningY = -1f;
	public float menuX = -1f;
	public float menuY = -1f;
	public boolean menuPlaced = false;
	public String menuTab = "WORLD";
	public float themePaneOpacity = 0.90f;
	public String capeUrl = "";
	public String capePath = "";
	public boolean nickEnabled = false;
	public String nick = "";
	public boolean nametagsEnabled = true;
	public boolean nametagThroughWalls = false;
	public boolean nametagDistance = true;
	public int nametagRange = 128;
	public float nametagScale = 1.0f;
	public boolean mobGlowEnabled = false;
	public boolean mobGlowThroughWalls = true;
	public boolean blockOutlineGlow = true;
	public float blockOutlineOpacity = 0.58f;
	public int blockOutlineRgb = 0x2FB5FF;
	public String mobGlowId = "";
	public java.util.List<String> mobGlowIds = new java.util.ArrayList<>();
	public float mobGlowSize = 0.48f;
	public float mobGlowOpacity = 0.58f;
	public int mobGlowRgb = 0x2FB5FF;
	public java.util.List<ItemSkin> itemSkins = new java.util.ArrayList<>();

	private VoidmarkConfig() {
	}

	public void normalizeMobGlowIds() {
		mobGlowIds = new java.util.ArrayList<>(MobCatalog.normalizeIds(mobGlowIds));
		mobGlowId = mobGlowIds.isEmpty() ? "" : mobGlowIds.get(0);
	}

	public boolean isMobGlowSelected(String id) {
		String key = MobCatalog.canonical(id);
		if (key == null || key.isEmpty()) {
			return false;
		}
		for (String selected : mobGlowIds) {
			if (key.equals(MobCatalog.canonical(selected))) {
				return true;
			}
		}
		return false;
	}

	/** Toggle a catalog id. Selecting a type while glow is off also turns glow on. */
	public void toggleMobGlow(String id) {
		String key = MobCatalog.canonical(id);
		if (key == null || key.isEmpty()) {
			return;
		}
		java.util.List<String> next = new java.util.ArrayList<>(MobCatalog.normalizeIds(mobGlowIds));
		if (next.contains(key)) {
			next.remove(key);
		} else {
			next.add(key);
			if (!mobGlowEnabled) {
				mobGlowEnabled = true;
			}
		}
		mobGlowIds = next;
		mobGlowId = next.isEmpty() ? "" : next.get(0);
	}

	public static VoidmarkConfig get() {
		return instance;
	}

	public static void load() {
		if (!Files.isRegularFile(PATH)) {
			instance.save();
			return;
		}

		try (Reader reader = Files.newBufferedReader(PATH)) {
			VoidmarkConfig loaded = GSON.fromJson(reader, VoidmarkConfig.class);
			if (loaded != null) {
				loaded.scanRadius = clamp(loaded.scanRadius, 16, 80);
				loaded.fillOpacity = clamp(loaded.fillOpacity, 0.08f, 0.85f);
				loaded.worldTintStrength = clamp(loaded.worldTintStrength, 0f, 1f);
				loaded.worldTintMode = normalizeWorldTintMode(loaded.worldTintMode);
				loaded.skyTintStrength = clamp(loaded.skyTintStrength, 0f, 1f);
				loaded.fogStart = clamp(loaded.fogStart, 0f, 0.95f);
				loaded.fogEnd = clamp(loaded.fogEnd, 0.05f, 1f);
				loaded.fogDensity = clamp(loaded.fogDensity, 0f, 1f);
				loaded.aspectRatio = clamp(loaded.aspectRatio, 0.50f, 1.20f);
				boolean legacyTheme = loaded.themePreset == null || loaded.themePreset.isBlank();
				if (legacyTheme) {
					loaded.themeAccentRgb = 0x2FB5FF;
					loaded.themePaneRgb = 0x0B0E14;
					loaded.themePreset = "cyan";
					loaded.uiAnimations = true;
					loaded.watermarkEnabled = true;
					loaded.watermarkFps = true;
					loaded.watermarkPing = true;
					loaded.watermarkTime = true;
				} else {
					loaded.themeAccentRgb = loaded.themeAccentRgb & 0xFFFFFF;
					if (loaded.themeAccentRgb == 0) {
						loaded.themeAccentRgb = 0x2FB5FF;
					}
					loaded.themePaneRgb = loaded.themePaneRgb & 0xFFFFFF;
					if (loaded.themePaneRgb == 0) {
						loaded.themePaneRgb = 0x0B0E14;
					}
				}
				if (loaded.capeUrl == null) {
					loaded.capeUrl = "";
				}
				if (loaded.capePath == null) {
					loaded.capePath = "";
				}
				if (loaded.nick == null) {
					loaded.nick = "";
				}
				loaded.nametagRange = clamp(loaded.nametagRange <= 0 ? 128 : loaded.nametagRange, 64, 256);
				loaded.nametagScale = clampHudScale(loaded.nametagScale);
				if (loaded.mobGlowIds == null) {
					loaded.mobGlowIds = new java.util.ArrayList<>();
				}
				if (loaded.mobGlowIds.isEmpty() && loaded.mobGlowId != null && !loaded.mobGlowId.isBlank()) {
					loaded.mobGlowIds.add(loaded.mobGlowId);
				}
				loaded.normalizeMobGlowIds();
				loaded.mobGlowSize = clamp(loaded.mobGlowSize <= 0f ? 0.48f : loaded.mobGlowSize, 0.12f, 1.20f);
				loaded.mobGlowOpacity = clamp(loaded.mobGlowOpacity <= 0f ? 0.58f : loaded.mobGlowOpacity, 0.15f, 0.90f);
				loaded.mobGlowRgb = loaded.mobGlowRgb & 0xFFFFFF;
				if (loaded.mobGlowRgb == 0) {
					loaded.mobGlowRgb = 0x2FB5FF;
				}
				loaded.blockOutlineOpacity = clamp(loaded.blockOutlineOpacity <= 0f ? 0.58f : loaded.blockOutlineOpacity, 0.15f, 0.90f);
				loaded.blockOutlineRgb = loaded.blockOutlineRgb & 0xFFFFFF;
				if (loaded.blockOutlineRgb == 0) {
					loaded.blockOutlineRgb = 0x2FB5FF;
				}
				if (loaded.itemSkins == null) {
					loaded.itemSkins = new java.util.ArrayList<>();
				}
				if (loaded.rawmatsItemId == null) {
					loaded.rawmatsItemId = "";
				}
				loaded.menuTab = normalizeMenuTab(loaded.menuTab);
				loaded.inventoryHudAnchor = normalizeInventoryHudAnchor(loaded.inventoryHudAnchor);
				loaded.inventoryHudScale = clampHudScale(loaded.inventoryHudScale);
				loaded.hudWatermarkScale = clampHudScale(loaded.hudWatermarkScale);
				loaded.hudNodesScale = clampHudScale(loaded.hudNodesScale);
				loaded.hudMusicScale = clampHudScale(loaded.hudMusicScale);
				loaded.hudRawmatsScale = clampHudScale(loaded.hudRawmatsScale);
				loaded.hudMiningScale = clampHudScale(loaded.hudMiningScale);
				loaded.slotHotbar = hudSlot(loaded.slotHotbar);
				loaded.slotHealth = hudSlot(loaded.slotHealth);
				loaded.slotHunger = hudSlot(loaded.slotHunger);
				loaded.slotArmor = hudSlot(loaded.slotArmor);
				loaded.slotAir = hudSlot(loaded.slotAir);
				loaded.slotExperience = hudSlot(loaded.slotExperience);
				loaded.slotMount = hudSlot(loaded.slotMount);
				loaded.slotScoreboard = hudSlot(loaded.slotScoreboard);
				loaded.slotBoss = hudSlot(loaded.slotBoss);
				loaded.slotEffects = hudSlot(loaded.slotEffects);
				loaded.slotHeldItem = hudSlot(loaded.slotHeldItem);
				loaded.themePaneOpacity = loaded.themePaneOpacity <= 0f
					? 0.90f
					: clamp(loaded.themePaneOpacity, 0.20f, 1f);
				instance = loaded;
			}
		} catch (Exception exception) {
			Voidmark.LOGGER.warn("Could not read voidmark.json, using defaults", exception);
			instance = new VoidmarkConfig();
		}
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException exception) {
			Voidmark.LOGGER.warn("Could not write voidmark.json", exception);
		}
	}

	public int fillColor() {
		int alpha = Math.round(fillOpacity * 255.0f);
		return (alpha << 24) | (colorRgb & 0xFFFFFF);
	}

	public int lineColor() {
		return 0xFF000000 | (colorRgb & 0xFFFFFF);
	}

	public boolean worldTintUsesLightmap() {
		return "lightmap".equalsIgnoreCase(worldTintMode);
	}

	public void cycleWorldTintMode() {
		worldTintMode = worldTintUsesLightmap() ? "shader" : "lightmap";
	}

	public String worldTintModeLabel() {
		return worldTintUsesLightmap() ? "Lightmap" : "Shader";
	}

	public void cycleRawmatsMode() {
		rawmatsEnchanted = !rawmatsEnchanted;
	}

	public String rawmatsModeLabel() {
		return rawmatsEnchanted ? "Enchanted" : "Raw";
	}

	public void cycleInventoryHudAnchor() {
		inventoryHudAnchor = switch (inventoryHudAnchor) {
			case "top_left" -> "top_right";
			case "top_right" -> "bottom_right";
			case "bottom_right" -> "bottom_left";
			default -> "top_left";
		};
	}

	public String inventoryHudAnchorLabel() {
		return switch (inventoryHudAnchor) {
			case "top_left" -> "Top left";
			case "top_right" -> "Top right";
			case "bottom_left" -> "Bottom left";
			default -> "Bottom right";
		};
	}

	public static String normalizeInventoryHudAnchor(String anchor) {
		if (anchor == null) {
			return "bottom_right";
		}
		return switch (anchor) {
			case "top_left", "top_right", "bottom_left", "bottom_right" -> anchor;
			default -> "bottom_right";
		};
	}

	public static String normalizeWorldTintMode(String mode) {
		return "lightmap".equalsIgnoreCase(mode) ? "lightmap" : "shader";
	}

	public static String normalizeMenuTab(String tab) {
		if (tab == null || tab.isBlank()) {
			return "WORLD";
		}
		String name = tab.trim().toUpperCase(java.util.Locale.ROOT);
		return switch (name) {
			case "WORLD", "VIEW", "FOG" -> "WORLD";
			case "ESP", "MOBS" -> "ESP";
			case "OVERLAY", "DISPLAY", "INVENTORY" -> "OVERLAY";
			case "BARS", "HUD" -> "BARS";
			case "NODES", "MARKERS", "STATUS" -> "NODES";
			case "MINING" -> "MINING";
			case "PLAYER", "NICK", "CAPE" -> "PLAYER";
			default -> "WORLD";
		};
	}

	public static float clampHudScale(float value) {
		if (value <= 0f) {
			return 1.0f;
		}
		return clamp(value, 0.50f, 2.00f);
	}

	public static HudSlot hudSlot(HudSlot slot) {
		if (slot == null) {
			return new HudSlot();
		}
		slot.scale = clampHudScale(slot.scale);
		return slot;
	}

	public void resetHudSlots() {
		slotHotbar = new HudSlot();
		slotHealth = new HudSlot();
		slotHunger = new HudSlot();
		slotArmor = new HudSlot();
		slotAir = new HudSlot();
		slotExperience = new HudSlot();
		slotMount = new HudSlot();
		slotScoreboard = new HudSlot();
		slotBoss = new HudSlot();
		slotEffects = new HudSlot();
		slotHeldItem = new HudSlot();
	}

	public static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	public static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	public static final class HudSlot {
		public float x = -1f;
		public float y = -1f;
		public float scale = 1f;
	}

	public static final class ItemSkin {
		public String key = "";
		public String displayId = "";
		public String originalId = "";
		public int slot = 0;
		public boolean offhand = false;
	}
}
