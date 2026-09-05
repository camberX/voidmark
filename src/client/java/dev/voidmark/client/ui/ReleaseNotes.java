package dev.voidmark.client.ui;

import dev.voidmark.client.config.VoidmarkConfig;
import net.fabricmc.loader.api.FabricLoader;

public final class ReleaseNotes {
	public record Entry(String version, String[] lines) {
	}

	public static final Entry[] ENTRIES = {
		new Entry("1.2.1", new String[]{
			"Chest Aim tracks each distinct crit box and only turns after that box jumps or vanishes, instead of locking onto the chest block."
		}),
		new Entry("1.2.0", new String[]{
			"Voidmark 1.2.0 release."
		}),
		new Entry("1.1.223", new String[]{
			"Chest ESP has a Speed slider for how fast Chest Aim turns."
		}),
		new Entry("1.1.222", new String[]{
			"Chest Aim uses a timed ease-in-out look: shortest-path yaw, lerp pitch, then a mouse-step snap."
		}),
		new Entry("1.1.221", new String[]{
			"Chest Aim (Controls) smoothly looks at each crit box on a nearby chest, waits for it to move, then goes to the next. Five locks is one pass; if the chest is still there it keeps going."
		}),
		new Entry("1.1.220", new String[]{
			"Chest ESP marks newly spawned chests within 5 blocks in Dwarven Mines and Crystal Hollows from packets, with tracers and crit-particle boxes even if Sodium hides particles."
		}),
		new Entry("1.1.219", new String[]{
			"Block outline glow is back. Looking at a block uses a small blur instead of the full ESP kernel, so world FPS stays up."
		}),
		new Entry("1.1.218", new String[]{
			"Farm keys uses VOIDMARK chat prefixes, sets sensitivity to minimum, and restores your exact sensitivity when disabled."
		}),
		new Entry("1.1.217", new String[]{
			"/vm farmkeys or /vm fk swaps Attack/Destroy with Jump and makes Attack/Destroy toggle. Run it again to restore your original bindings."
		}),
		new Entry("1.1.216", new String[]{
			"Wardrobe empty slots hide the armor chips, only real locked slots say Locked, and only the equipped set is outlined."
		}),
		new Entry("1.1.215", new String[]{
			"The Music HUD reads Spotify progress from the live SMTC track every frame, the same way ForageKit does. YouTube Music is unchanged."
		}),
		new Entry("1.1.214", new String[]{
			"Spotify now-playing uses the same SMTC helper and wall-clock bar as ForageKit. YouTube Music is unchanged."
		}),
		new Entry("1.1.213", new String[]{
			"Spotify progress uses the first SMTC timestamp as an anchor and adds wall-clock time, so the bar moves every frame."
		}),
		new Entry("1.1.212", new String[]{
			"Spotify on the Music HUD reads the Windows media session. No Spotify login. YouTube Music is unchanged."
		}),
		new Entry("1.1.211", new String[]{
			"If Spotify login works but the bar does not move, the developer app is still in development mode. Add that account under User Management."
		}),
		new Entry("1.1.210", new String[]{
			"Spotify Connected still shows the HUD from the desktop window when the API has no active player, instead of hiding as idle."
		}),
		new Entry("1.1.209", new String[]{
			"Spotify progress keeps moving from the API clock. A brief empty poll no longer swaps in the window title, which has no time."
		}),
		new Entry("1.1.208", new String[]{
			"Spotify Connect ships the shared app ID in the jar, so Connect opens the browser login instead of Need Client ID."
		}),
		new Entry("1.1.207", new String[]{
			"Spotify Connect loads the shared client ID from GitHub instead of the shop worker, so friends are not stuck on Need Client ID when /api/spotify is missing."
		}),
		new Entry("1.1.206", new String[]{
			"Music HUD can read Spotify through the official API. Overlay → Music → Spotify API connects once, then title, artist, album art, and skip controls come from Spotify."
		}),
		new Entry("1.1.205", new String[]{
			"Loadouts and wardrobe verify the menu title before saving a cache."
		}),
		new Entry("1.1.204", new String[]{
			"Picking a local cape no longer freezes Minecraft while the folder dialog is open."
		}),
		new Entry("1.1.203", new String[]{
			"Auto-update removes the old jar from mods. If Windows has it locked, it is moved out of the way and deleted on the next launch."
		}),
		new Entry("1.1.202", new String[]{
			"Only the equipped loadout and wardrobe set are outlined. Wardrobe shows helmet, chest, legs, and boots under each model. New installs start with every optional feature off."
		}),
		new Entry("1.1.201", new String[]{
			"Closing loadouts with the hotkey keeps the last snapshot, so the next open still comes from cache instead of a blank wait."
		}),
		new Entry("1.1.200", new String[]{
			"Switching click-GUI tabs only slides the sidebar pill. Icons and labels stay on their own rows."
		}),
		new Entry("1.1.199", new String[]{
			"Farming tab: Yaw / Pitch sits next to the crosshair while you hold an item whose lore includes FARMING TOOL."
		}),
		new Entry("1.1.198", new String[]{
			"World FPS is back: looking at a block no longer runs the glow shader, and combat no longer scans every nearby entity each tick for the hitmarker."
		}),
		new Entry("1.1.197", new String[]{
			"Shop bump so auto-update can pull the latest build."
		}),
		new Entry("1.1.196", new String[]{
			"The hitmarker is white with a black outline again, like CoD. It still fades out without shifting color."
		}),
		new Entry("1.1.195", new String[]{
			"Hitmarker size is a slider on Combat → Mix. The X stays white and only fades out."
		}),
		new Entry("1.1.194", new String[]{
			"Wardrobe and loadouts no longer flash a lime green plate behind 3D models. Equipped slots use the accent rim instead."
		}),
		new Entry("1.1.193", new String[]{
			"Melee hitsounds wait for the weapon hit delay, not every click on a mob. A CoD-style hitmarker flashes on the crosshair when a hit lands."
		}),
		new Entry("1.1.192", new String[]{
			"The wardrobe menu is a grid of 3D armor models, one per slot, instead of a copy of the loadouts layout."
		}),
		new Entry("1.1.191", new String[]{
			"Auto update replaces the jar and stops this launch so the next start loads the new code."
		}),
		new Entry("1.1.190", new String[]{
			"Shop bump so auto-update can pull the swap-and-continue updater."
		}),
		new Entry("1.1.189", new String[]{
			"Auto update writes the new jar and keeps this launch going. Fabric already loaded the current jar, so the new code is on the next start."
		}),
		new Entry("1.1.188", new String[]{
			"Shop bump so auto-update can pull the relaunch fix."
		}),
		new Entry("1.1.187", new String[]{
			"Auto update actually relaunches Minecraft after it swaps the jar. The last build exited cleanly and never started the new process."
		}),
		new Entry("1.1.186", new String[]{
			"Theme has a Menu stars toggle. Turn it off to hide the drifting stars in the click GUI, loadouts, and wardrobe."
		}),
		new Entry("1.1.185", new String[]{
			"Auto update replaces the jar and relaunches Minecraft on its own, so you do not have to open the launcher again."
		}),
		new Entry("1.1.184", new String[]{
			"The vanilla black block outline stays off while Voidmark's glow outline is on, so you only see the custom rim."
		}),
		new Entry("1.1.183", new String[]{
			"The click GUI has more sidebar tabs: Combat, Menus, and Status sit next to World, ESP, Overlay, Bars, Nodes, and Mining so the list fills the pane."
		}),
		new Entry("1.1.182", new String[]{
			"Auto update is off by default. Turn it on in the theme settings and the next launch waits for voidmark.cloud: if a newer jar is there it replaces the one in mods and Minecraft closes so you can relaunch."
		}),
		new Entry("1.1.181", new String[]{
			"Hypixel wardrobe ((1/3) Armor Sets) opens a Voidmark menu with a 3D armor preview and the set slots on the right. Pets and extra gear are left out. Clicks go through the real chest."
		}),
		new Entry("1.1.180", new String[]{
			"The loadouts gear row only shows armor and equipment that is actually there. Empty placeholder slots to the right are gone."
		}),
		new Entry("1.1.179", new String[]{
			"1-9 no longer flashes the loadouts menu after it closes. Late Hypixel chest packets are dropped until you open it again."
		}),
		new Entry("1.1.178", new String[]{
			"The loadouts menu opens instantly from the last snapshot, so Hypixel lag no longer blanks the UI. Clicks you make while it is loading are sent when the real chest arrives. Open animation has its own toggle under Nodes → Menus."
		}),
		new Entry("1.1.177", new String[]{
			"Loadout slots follow Hypixel's 3-wide grid, so slot 1 and 4 match the vanilla chest. The pet is a floating skull with the item's real name colors."
		}),
		new Entry("1.1.176", new String[]{
			"Loadout slots stay inside the slots pane. Press 1-9 to equip that slot and close. Bind Open Loadouts in Controls to open the menu without typing /loadouts."
		}),
		new Entry("1.1.175", new String[]{
			"The custom loadouts menu actually opens. It looks for Hypixel's (1/3) Loadouts title (any page numbers), which the last build missed because it looked for lowercase loadout."
		}),
		new Entry("1.1.174", new String[]{
			"Hypixel /loadouts opens a Voidmark menu: 3D armor and pet for the selected loadout, and the eight slots on the right. Clicks go through the real chest so the server sees a normal GUI click."
		}),
		new Entry("1.1.173", new String[]{
			"Melee hitsounds wait for Minecraft's attack cooldown. Spam-clicks before the weapon is charged no longer ding."
		}),
		new Entry("1.1.172", new String[]{
			"Shop download version is 1.1.172 so voidmark.cloud can show the new jar name after a git push."
		}),
		new Entry("1.1.171", new String[]{
			"Shop download version is 1.1.171 so voidmark.cloud can show the new jar name after a git push."
		}),
		new Entry("1.1.170", new String[]{
			"Hitsound uses the agpa2 clip, and only plays when you land the hit. Nearby players punching the same mob no longer trigger it."
		}),
		new Entry("1.1.169", new String[]{
			"Ships a verified hitsound jar. 1.1.168 was a truncated zip for some downloads (zip END header not found); delete that file from mods or Fabric will not launch."
		}),
		new Entry("1.1.168", new String[]{
			"Hitsounds actually play on Hypixel. The first version skipped every mob whose client health was 0 (most Skyblock mobs), used the Players slider, and had no backup when an arrow or hologram click did not collide locally."
		}),
		new Entry("1.1.167", new String[]{
			"Theme Font Minecraft is smaller and sits on the same baseline as Nunito. The last pass still left it high and a bit large in the click GUI rows."
		}),
		new Entry("1.1.166", new String[]{
			"Theme Font Minecraft is drawn at the same body, small, and title sizes as Nunito. It no longer fills the click GUI and HUD like vanilla 8px chat text."
		}),
		new Entry("1.1.165", new String[]{
			"Hitsounds play the instant a melee swing or your arrow overlaps a mob on the client, instead of waiting for Hypixel to confirm the hit. Melee and arrows are separate toggles on the World tab; volume and pitch sit on the cog."
		}),
		new Entry("1.1.164", new String[]{
			"HUD panes no longer show seam lines or darker bands at the rounded corners. Item wells and stars stay on the cheap fill path."
		}),
		new Entry("1.1.163", new String[]{
			"The click GUI, title screen, and other menus use the old smooth fills again. HUD panes keep the cheaper integer path while you are in a world."
		}),
		new Entry("1.1.162", new String[]{
			"HUD panes no longer re-read the tab list, scoreboard, and inventory every frame to decide what to show. Mining commissions update a few times a second, and a pane that is off does not keep scanning."
		}),
		new Entry("1.1.161", new String[]{
			"Click GUI cards no longer show faint vertical seams at the rounded corners. HUD fills stay on the cheap integer path."
		}),
		new Entry("1.1.160", new String[]{
			"HUD chrome is cheaper with every pane on. Item wells are flat instead of rounded, fills no longer break the GUI batch, and raw mats / scoreboard stop rebuilding several times a frame. HUD stars skip the tiny bars."
		}),
		new Entry("1.1.159", new String[]{
			"Only the watermark keeps the accent rail. The other HUD panes are outline and fill."
		}),
		new Entry("1.1.158", new String[]{
			"Theme Font includes Minecraft next to Nunito and your installed fonts. The dropdown rows are spaced like the rest of the menu. Nametag ESP lives only on the ESP tab. Raw mats notes say Used in instead of Uses."
		}),
		new Entry("1.1.157", new String[]{
			"Hypixel nametags keep their colors. Ironman plates no longer show leftover 8 / b / 7 from §8 §b §7."
		}),
		new Entry("1.1.156", new String[]{
			"Raw mats still counts Refined Mithril (and other compact forms) toward Enchanted Mithril, and each row shows Uses plus the ingredient the recipe actually wants."
		}),
		new Entry("1.1.155", new String[]{
			"Glow ESP has a Radius slider for how far the halo reaches. The blur no longer fades to a black vignette at the edge."
		}),
		new Entry("1.1.154", new String[]{
			"ESP lists every /vm esp nametag filter with an x to remove it. You can glow more than one word at once. Player nametags stay on Minecraft's font; Unicode that the Theme font is missing falls back to vanilla so it still draws."
		}),
		new Entry("1.1.153", new String[]{
			"/vm esp seer no longer glows Obsidian Defenders. Nametags bind to the mob under them, not every nearby mob, and a known different plate is never treated as a copy."
		}),
		new Entry("1.1.152", new String[]{
			"/vm esp forgets learned mob looks when you change worlds, so you have to see one named copy again. Samples stay in memory for that world only."
		}),
		new Entry("1.1.151", new String[]{
			"/vm esp remembers the type and armor of the first named mob you see, then glows other copies at render distance before their nametag appears."
		}),
		new Entry("1.1.150", new String[]{
			"/vm esp <text> glows the living mob as soon as Hypixel sends the nametag in entity metadata, even if the hologram plate has not rendered yet."
		}),
		new Entry("1.1.149", new String[]{
			"/vm esp <text> reads Hypixel nametags as soon as they exist on the client, including hidden hologram stands and text displays, instead of waiting until the plate is drawn."
		}),
		new Entry("1.1.148", new String[]{
			"Glow ESP no longer covers slayers and other mobs that already have vanilla glow — they keep Minecraft's outline."
		}),
		new Entry("1.1.147", new String[]{
			"/vm esp <text> glows every mob whose nametag contains that word, including holograms above the real mob. Raw mats reads Ender Chest and backpacks from the profile API again — older inventory layouts, nested backpacks, and 1.21 item components included."
		}),
		new Entry("1.1.146", new String[]{
			"Theme has a Font picker for every menu and HUD label. It lists the TrueType fonts installed on your PC. The watermark VOIDMARK logo and the VOIDMARK Dev nametag stay on Nunito."
		}),
		new Entry("1.1.145", new String[]{
			"/vm edit is a visual reskin again — no lore, no Maxed, no worn armor models. Raw mats only counts the materials you actually have, not cobble locked inside minions and other crafts."
		}),
		new Entry("1.1.144", new String[]{
			"Raw mats no longer double-counts items you pull out of a backpack or Ender Chest after the API snapshot. Storage moves while the bag is open are tracked; armor is counted once."
		}),
		new Entry("1.1.143", new String[]{
			"Chest GUIs like Heart of the Mountain no longer hitch. Item reskins never copy NBT or walk lore on menu stacks — only the items in your own inventory."
		}),
		new Entry("1.1.142", new String[]{
			"Glow ESP renders through walls at full range — entities behind geometry are no longer culled. Music HUD is more compact; hover controls (prev/play/next) are visible again."
		}),
		new Entry("1.1.141", new String[]{
			"Chams and the ESP 3D preview are gone. Vanilla nametag style still uses Voidmark range, size, opacity, through-walls, distance text, and distance scaling — only the chrome is Minecraft's."
		}),
		new Entry("1.1.140", new String[]{
			"Fresh jar. Replace a truncated 1.1.139 download (zip END header not found) with this build."
		}),
		new Entry("1.1.139", new String[]{
			"ESP has a 3D player preview that live-applies glow and chams. Chams has Fill (solid unlit), Default (solid with lighting), and Tint (color wash over the skin), with through-walls, opacity, and its own color."
		}),
		new Entry("1.1.138", new String[]{
			"The Cape card stays inside the Player tab instead of hanging off the bottom of the menu."
		}),
		new Entry("1.1.137", new String[]{
			"Titanium ESP uses SkyHanni's Dwarven Mines area nodes, so named jobs like Rampart's Quarry Titanium only mark ore in that zone. Neighbouring rooms (Forge, Village, Far Reserve, corridors) no longer steal those veins."
		}),
		new Entry("1.1.136", new String[]{
			"/vm edit has a Maxed toggle: copied lore gets recomb, dungeon stars, master stars, gem slots, hot-potato stats, and max enchants — only the pieces that item already has. Copied armor also uses its worn 3D model, including dragon helmets."
		}),
		new Entry("1.1.135", new String[]{
			"Copied Skyblock lore is no longer forced italic. Hypixel tooltip lines stay upright."
		}),
		new Entry("1.1.134", new String[]{
			"/vm edit: type a Skyblock item name (Hyperion) to reskin and copy that item's Hypixel name and lore, color codes included."
		}),
		new Entry("1.1.133", new String[]{
			"Music controls stay hidden until you hover the HUD with chat open, then slide down. /vm edit can copy a Hypixel item's name and lore, color codes included, and replace the one you are holding."
		}),
		new Entry("1.1.132", new String[]{
			"Cape creator crops photos onto the 10×16 cape face in Voidmark and on the cape desk. Drag to pan, scroll to zoom, then apply. Vanilla 64×32 templates still skip the cropper."
		}),
		new Entry("1.1.131", new String[]{
			"Nametags cog picks Voidmark plates or vanilla tags. Menu buttons, fields, list rows, and compact HUD pieces no longer draw a left accent rail. Remaining HUD rails flip to the side closer to the screen edge."
		}),
		new Entry("1.1.130", new String[]{
			"Node ESP sits on the Nodes tab without the extra caption text. Server list and world list no longer have dark bands over the header and footer."
		}),
		new Entry("1.1.129", new String[]{
			"YouTube Music HUD no longer guesses a different song from the window title. Artist, cover, and the progress bar come from the API Server on 26538."
		}),
		new Entry("1.1.128", new String[]{
			"Server list and world list keep a single Voidmark separator. The extra vanilla bar is gone."
		}),
		new Entry("1.1.127", new String[]{
			"YouTube Music now reads the API Server on 26538, asks for access when the plugin requires it, and uses the album cover and artist the API actually returns."
		}),
		new Entry("1.1.126", new String[]{
			"Fake boosting bans last 360 days instead of 180."
		}),
		new Entry("1.1.125", new String[]{
			"Music HUD no longer launches an external Windows shell. Now-playing on Windows uses the player window and local companion apps."
		}),
		new Entry("1.1.124", new String[]{
			"Fake ban Retry also fakes Encrypting before Joining world."
		}),
		new Entry("1.1.123", new String[]{
			"Fake ban Retry never opens a real connection. Vanilla Connecting then Joining world are faked, then the kick."
		}),
		new Entry("1.1.122", new String[]{
			"Fake ban reconnects reach Joining world, sit there for half a second, then kick. Retry actually connects again."
		}),
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
