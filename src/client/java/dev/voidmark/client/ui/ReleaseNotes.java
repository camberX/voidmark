package dev.voidmark.client.ui;

import dev.voidmark.client.config.VoidmarkConfig;
import net.fabricmc.loader.api.FabricLoader;

public final class ReleaseNotes {
	public record Entry(String version, String[] lines) {
	}

	public static final Entry[] ENTRIES = {
		new Entry("1.1.121", new String[]{
			"Fake ban actually sends /limbo again. Reconnects never load the world — Retry just refreshes the kick screen."
		}),
		new Entry("1.1.120", new String[]{
			"Fake ban has no confirm popup. After 5 seconds you get a red limbo exception line, then 3 seconds in Limbo before the kick."
		}),
		new Entry("1.1.119", new String[]{
			"Fake ban duration is white. Remaining time stays frozen on the kick screen and only updates when you reconnect."
		}),
		new Entry("1.1.118", new String[]{
			"Fake boosting bans match Hypixel: 180d countdown, Boosting detected on one or multiple SkyBlock profiles, and reconnects skip Limbo."
		}),
		new Entry("1.1.117", new String[]{
			"Admin Fake ban sends you to Limbo for 2 seconds, then a 180-day Hypixel Boosting kick with a random Ban ID."
		}),
		new Entry("1.1.116", new String[]{
			"capeServerUrl is gone from voidmark.json. Capes still use https://voidmark.cloud."
		}),
		new Entry("1.1.115", new String[]{
			"Cape shop is always https://voidmark.cloud. Old workers.dev and localhost URLs are ignored."
		}),
		new Entry("1.1.114", new String[]{
			"Refresh capes can only run once every 5 minutes so the shop cannot be rate-limited from the button."
		}),
		new Entry("1.1.113", new String[]{
			"Refresh capes on the Cape card pulls the latest shop capes and head tags."
		}),
		new Entry("1.1.112", new String[]{
			"Cape changes are once per 24 hours unless the admin checks Upload bypass.",
			"A cape set in the admin list overrides your local cape."
		}),
		new Entry("1.1.111", new String[]{
			"A cape set in the admin list overrides your local cape.",
			"Shop capes and head tags still refresh when you join a world."
		}),
		new Entry("1.1.110", new String[]{
			"Shop capes and head tags refresh when you join a world, not every couple of seconds."
		}),
		new Entry("1.1.109", new String[]{
			"Admin can set a head tag on the cape list. Color codes work, and it shows above their nametag."
		}),
		new Entry("1.1.108", new String[]{
			"Cape shop defaults to the live Cloudflare host. Localhost configs migrate on launch."
		}),
		new Entry("1.1.107", new String[]{
			"Cape settings stay locked until your UUID is on the shop list.",
			"The shop rejects everyone else with uuid not whitelisted."
		}),
		new Entry("1.1.106", new String[]{
			"Shop capes show for every Voidmark user, not just you.",
			"Changing the cape in the menu updates it for everyone within a couple of seconds."
		}),
		new Entry("1.1.105", new String[]{
			"Own nametag is its own switch on ESP, not inside the Nametags cog."
		}),
		new Entry("1.1.104", new String[]{
			"Own nametag toggle. VOIDMARK Dev only draws with it, and no longer overlaps the name."
		}),
		new Entry("1.1.103", new String[]{
			"VOIDMARK Dev uses vanilla nametag chrome when custom nametags are off."
		}),
		new Entry("1.1.102", new String[]{
			"VOIDMARK Dev and the name share one plate."
		}),
		new Entry("1.1.101", new String[]{
			"VOIDMARK Dev still shows when custom nametags are off."
		}),
		new Entry("1.1.100", new String[]{
			"VOIDMARK Dev uses the menu font and sits on the nametag.",
			"Nametags no longer fade in or out."
		}),
		new Entry("1.1.99", new String[]{
			"Removed the toolbar Reset button.",
			"Adjacent Titanium ESP ores merge into one outline."
		}),
		new Entry("1.1.98", new String[]{
			"Player preview is larger, still inside the You card."
		}),
		new Entry("1.1.97", new String[]{
			"Player preview uses inventory scale so it no longer fills the You card."
		}),
		new Entry("1.1.96", new String[]{
			"Player preview fits inside the You card instead of clipping the head."
		}),
		new Entry("1.1.95", new String[]{
			"Titanium ESP stays inside the named commission area instead of a huge radius around the emissary."
		}),
		new Entry("1.1.94", new String[]{
			"Titanium ESP follows the commission area; Titanium Miner still marks every vein.",
			"Commission HUD bars are back, colored with the percent.",
			"Player preview fills the card and hides armor."
		}),
		new Entry("1.1.93", new String[]{
			"Rounded corners no longer show black gaps from the fill fast-path."
		}),
		new Entry("1.1.92", new String[]{
			"Nametags use the default Minecraft font.",
			"HUD opacity is separate from the click-GUI pane.",
			"Player preview is smaller, follows menu scale, and uses a vanilla nametag above the model."
		}),
		new Entry("1.1.91", new String[]{
			"Removed Efficient Miner prediction.",
			"HUD stars and GUI fills cost less while looking the same."
		}),
		new Entry("1.1.90", new String[]{
			"Mining Spread extras include face, edge, and corner diagonals."
		}),
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
