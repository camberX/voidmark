package dev.voidmark.client.ui;

import dev.voidmark.client.config.VoidmarkConfig;
import net.fabricmc.loader.api.FabricLoader;

public final class ReleaseNotes {
	public record Entry(String version, String[] lines) {
	}

	public static final Entry[] ENTRIES = {
		new Entry("1.1.89", new String[]{
			"Efficient Miner uses Hypixel's spread math in a 3×3×3, not a vein fill."
		}),
		new Entry("1.1.88", new String[]{
			"Titanium ESP while a Titanium commission is active.",
			"Efficient Miner overlay on the extra blocks Mining Spread will break."
		}),
		new Entry("1.1.87", new String[]{
			"3D preview head stays locked to the body.",
			"Nametag pills fit the text. Compact Dev badge.",
			"Nametag opacity plus a fade when you enter range.",
			"Nick applies to your own F5 tag.",
			"Menu scale 100/90/75/50%. Optional HUD stars."
		}),
		new Entry("1.1.86", new String[]{
			"Bell opens versioned release notes.",
			"Settings panes stay more opaque.",
			"Category divider uses the accent.",
			"Skull/name opens a 3D skin preview.",
			"Nametags on ESP. Node HUD on Nodes."
		}),
		new Entry("1.1.85", new String[]{
			"Settings sheets use the pane fill instead of an accent wash.",
			"Block outline glow has its own color and opacity.",
			"Mining HUD sits on the Mining tab only."
		}),
		new Entry("1.1.84", new String[]{
			"Theme and subsetting popovers use a hairline outline."
		}),
		new Entry("1.1.83", new String[]{
			"Pickaxe ready alert uses the pane fill and accent text."
		}),
		new Entry("1.1.82", new String[]{
			"Click GUI recategorized. Feature cogs open subsettings.",
			"Mining HUD is compact with commissions and ability cooldown."
		}),
		new Entry("1.1.80", new String[]{
			"Backpack and Ender Chest counts fetch on join and /vm rawmats.",
			"Commissions read from the tab list."
		}),
		new Entry("1.1.76", new String[]{
			"Block outline glow is a filled-face silhouette, not a wire cage."
		})
	};

	private ReleaseNotes() {
	}

	public static String currentVersion() {
		return FabricLoader.getInstance()
			.getModContainer("voidmark")
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse(ENTRIES[0].version);
	}

	public static boolean unread() {
		String seen = VoidmarkConfig.get().changelogSeen;
		if (seen == null || seen.isBlank()) {
			return true;
		}
		return !seen.equals(currentVersion());
	}

	public static void markSeen() {
		VoidmarkConfig config = VoidmarkConfig.get();
		config.changelogSeen = currentVersion();
		config.save();
	}

	public static float contentHeight(float row) {
		float h = 20f;
		for (Entry entry : ENTRIES) {
			h += 14f + entry.lines.length * row;
			h += 6f;
		}
		return h + 8f;
	}
}
