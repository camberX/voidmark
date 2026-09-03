package dev.voidmark.client.ui;

import dev.voidmark.Voidmark;
import dev.voidmark.client.config.VoidmarkConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Folder resource pack that feeds the selected system TTF to Minecraft's font loader.
 */
public final class UiFontPack {
	public static final String FOLDER = "voidmark-ui";
	public static final String PACK_ID = "file/voidmark-ui";

	private static volatile boolean busy;
	private static volatile boolean reloading;
	private static volatile boolean bootstrapped;
	private static volatile String skipped;

	private UiFontPack() {
	}

	public static boolean loaded() {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return false;
		}
		return client.getResourceManager().getResource(Voidmark.id("font/ui.json")).isPresent();
	}

	public static void tick(Minecraft client) {
		if (client == null || busy || reloading) {
			return;
		}
		String family = VoidmarkConfig.get().uiFont;
		boolean want = systemFont(family);
		if (want && !loaded() && !family.equals(skipped)) {
			apply(family);
			return;
		}
		if (!want && !bootstrapped) {
			bootstrapped = true;
			disable(client);
		}
	}

	public static void apply(String family) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || busy || reloading) {
			return;
		}
		busy = true;
		try {
			if (!systemFont(family)) {
				VoidmarkConfig.get().uiFont = MenuFont.minecraftFamily(family) ? MenuFont.MINECRAFT_FAMILY : "";
				VoidmarkConfig.get().save();
				skipped = null;
				disable(client);
				return;
			}
			Path file = SystemFonts.file(family);
			if (file == null || !Files.isRegularFile(file)) {
				Voidmark.LOGGER.warn("No installed font file for {}", family);
				skipped = family;
				return;
			}
			write(client, file);
			VoidmarkConfig.get().uiFont = family;
			VoidmarkConfig.get().save();
			skipped = null;
			enable(client);
		} catch (Exception exception) {
			Voidmark.LOGGER.warn("Could not apply UI font {}", family, exception);
			skipped = family;
		} finally {
			busy = false;
			bootstrapped = true;
		}
	}

	private static boolean systemFont(String family) {
		return family != null && !family.isBlank() && !MenuFont.minecraftFamily(family);
	}

	private static void write(Minecraft client, Path ttf) throws IOException {
		Path root = client.getResourcePackDirectory().resolve(FOLDER);
		Path fontDir = root.resolve("assets/voidmark/font");
		Files.createDirectories(fontDir);
		Files.writeString(root.resolve("pack.mcmeta"), packMcmeta(), StandardCharsets.UTF_8);
		Files.copy(ttf, fontDir.resolve("ui.ttf"), StandardCopyOption.REPLACE_EXISTING);
		Files.writeString(fontDir.resolve("ui.json"), fontJson(6.5, 0.0), StandardCharsets.UTF_8);
		Files.writeString(fontDir.resolve("ui_small.json"), fontJson(5.25, 0.0), StandardCharsets.UTF_8);
		Files.writeString(fontDir.resolve("ui_title.json"), fontJson(8.0, 0.0), StandardCharsets.UTF_8);
	}

	private static void enable(Minecraft client) {
		PackRepository repo = client.getResourcePackRepository();
		repo.reload();
		if (!repo.getSelectedIds().contains(PACK_ID)) {
			if (!repo.addPack(PACK_ID)) {
				for (String id : repo.getAvailableIds()) {
					if (id.toLowerCase(Locale.ROOT).contains("voidmark-ui") && repo.addPack(id)) {
						break;
					}
				}
			}
		}
		client.options.updateResourcePacks(repo);
		client.options.save();
		reload(client);
	}

	private static void disable(Minecraft client) {
		PackRepository repo = client.getResourcePackRepository();
		if (!repo.getSelectedIds().contains(PACK_ID)) {
			return;
		}
		repo.removePack(PACK_ID);
		client.options.updateResourcePacks(repo);
		client.options.save();
		reload(client);
	}

	private static void reload(Minecraft client) {
		reloading = true;
		client.reloadResourcePacks().whenComplete((unused, error) -> reloading = false);
	}

	private static String packMcmeta() {
		return """
			{
				"pack": {
					"pack_format": 84,
					"description": "Voidmark UI font"
				}
			}
			""";
	}

	private static String fontJson(double size, double shiftY) {
		return """
			{
				"providers": [
					{
						"type": "ttf",
						"file": "voidmark:ui.ttf",
						"shift": [0, %s],
						"size": %s,
						"oversample": 12.0
					},
					{
						"type": "reference",
						"id": "minecraft:include/space"
					},
					{
						"type": "reference",
						"id": "minecraft:include/default",
						"filter": {
							"uniform": false
						}
					},
					{
						"type": "reference",
						"id": "minecraft:include/unifont"
					}
				]
			}
			""".formatted(shiftY, size);
	}
}
