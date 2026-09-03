# Voidmark

Fabric 26.1.2 QoL for Hypixel Skyblock. Marks **Ender Nodes** on the End Island, tints the world and skybox, and stretches aspect ratio. The config menu is a compact NEVERLOSE-style click GUI (`/voidmark`): a dark frosted-blue sidebar over the blurred world, a near-black content pane with animated starfield particles, rounded chrome, Nunito Sans, and icon-font glyphs. It floats in the center of the screen instead of filling it. The Minecraft title screen is replaced with a matching Voidmark menu: a full-screen starfield, taller Singleplayer / Multiplayer / Realms buttons, and the same pane chrome.

Ender Nodes look like purple stained clay (magenta terracotta on modern versions) and spit portal-colored dust. Voidmark scans loaded chunks, listens for those particles, then draws through-wall boxes, an outline, and a tracer to the nearest node.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for **Minecraft 26.1.2**.
2. Drop [Fabric API](https://modrinth.com/mod/fabric-api/versions?g=26.1.2) into `mods`.
3. Build this project (`./gradlew build`) and put `build/libs/voidmark-1.1.151.jar` in `mods`.
4. Launch the Fabric 26.1.2 profile.

Java **25** is required.

## Use

- The Minecraft **title screen** is replaced with a Voidmark menu: starry sky background, VOIDMARK title, and taller Singleplayer / Multiplayer / Realms / Options / Quit buttons in the same pane style as the click GUI. Language, Accessibility, and credits stay as text links at the bottom. That same chrome continues onto the **world list**, **server list**, **options**, pause menu, Realms, language, accessibility, and the rest of the out-of-world menus: starfield instead of dirt, pane buttons, restyled lists, sliders, and text fields. Inventory and chat stay vanilla.
- `/vm edit` or `/voidmark edit` opens the item id window. It shows the item you are holding, with its `minecraft:` id or Skyblock `sb:` id in a text box and a large preview above it. Typing another id reskins that item on your client: hand, hotbar, and inventory show the new look. Worn armor is unchanged. The server still has the real item. Vanilla ids look like `minecraft:diamond_sword`. Skyblock ids look like `sb:HYPERION`, or just the name (`Hyperion`). Tab or click a suggestion to fill it. Type the original id again to clear the reskin.
- `/vm rawmats sb:HYPERION` (or `/voidmark rawmats`, or `/vm rawmats` while holding the item) expands that Skyblock craft and shows a HUD with item icons, have/need counts, and a live progress bar per ingredient. **Materials** (Overlay tab cog, or click Raw/Enchanted on the HUD with chat open) picks **Raw** (Iron Ingot) or **Enchanted** (Enchanted Iron). Inventory, armor, and the held cursor stack are counted live. Ender Chest and backpacks start from your Skyblock profile (`hypixel.odtheking.com`) when you join a server and whenever you run `/vm rawmats` — including items sitting inside backpacks stored in the Ender Chest, and both the current Hypixel `inventory.*` payload and older member-level NBT. Moving items in or out of an open bag or Ender Chest updates that snapshot so the same stack is not counted twice. `/vm rawmats refresh` pulls storage again; `/vm rawmats raw` / `/vm rawmats enchanted` switch the mode; `/vm rawmats clear` hides the tracker.
- The **Music HUD** (Overlay tab) shows the song that is playing in Spotify or YouTube Music: cover art, title, artist, source, a progress bar, and elapsed/duration on the right of the bar (`1:23/3:45`). Pause freezes the bar; scrubbing updates it when the player reports a real timestamp. YouTube Music in a browser often has no live clock — use [th-ch YouTube Music](https://github.com/th-ch/youtube-music) with **Plugins → API Server** on port **26538** (the plugin default) for position, artist, and album art. If the plugin’s Authorization strategy is “Authorize at first request”, click **Allow** when YouTube Music asks for Voidmark; or set Authorization to **None**. A custom port goes in `voidmark.json` as `musicApiPort`. YouTube Music Desktop (port 9863, 1.x `/query`) still works. When the track changes, chat shows a styled `VOIDMARK | NOW PLAYING` line with the title and artist. Open Minecraft chat (`T`) and hover the music HUD to slide out **previous / play-pause / next**. You can also type `.np` `.play` `.pause` `.skip` `.prev` in chat (those stay client-side) or `/vm music`. On Windows it reads Spotify / YouTube Music window titles and local companion APIs. Media keys go through the Windows key API, not a shell. Linux uses `playerctl` metadata and `mpris:artUrl`.
- **ESP** (ESP tab) highlights every loaded entity of the types you pick, including other players. Scroll the full vanilla list (or type in the list search) and click rows to select them; click a selected row again to drop it. Matching entities get a **silhouette outline** with a shader gradient that fades outward from the model — not boxes, and not Minecraft’s sobel glow. You are never outlined. `/vm esp Frozen Blaze` (or any other nametag substring) glows every mob whose nametag contains that text, including Hypixel hologram armor stands, text displays, and the living mob under them. Walk up to one named copy once; Voidmark remembers that mob's type and armor and glows the rest at render distance. `/vm esp clear` turns the nametag filter off. Mobs that already have vanilla GLOWING (slayers, the glowing effect) keep Minecraft’s outline instead of ESP. **Block outline** (on by default) puts that same glow on the block you are looking at, around the vanilla selection wire. Color and opacity sit on the cog next to Block outline. **Nametags** sit on the same Glow card.
- **Mining** (Mining tab) shows a compact HUD with commission progress from the tab list and your pickaxe ability cooldown. Each job has a bar and a percent colored from red (just started) through gold to accent (done). Use a pickaxe ability and the HUD counts down (120s for Mining Speed Boost, 60s for Pickobulus) until chat says it is ready. **Ability alert** (cog next to Mining HUD, on by default) flashes a centered **READY** banner when chat contains `Pickobulus is now available!` or `Mining Speed Boost is now available!`. **Titanium ESP** turns on only while an unfinished tab commission contains `Titanium`: it marks polished diorite (Hypixel’s Titanium ore) through walls, merging neighboring ores into one box. A job like `Rampart's Quarry Titanium` only highlights veins in that named region; `Titanium Miner` highlights every vein in range. Drag the panel in the HUD editor.

- Right Shift is the default keybind (Controls → Voidmark). Press it again to close (the menu eases out).
- `/voidmark toggle` flips node markers without opening the menu.
- Toolbar **HUD** opens the HUD editor: drag any overlay (inventory, watermark, nodes, music, raw mats, mining) and every custom vanilla HUD piece (hotbar, bars, scoreboard, boss, effects, held item). They snap to screen axes and to each other; hold **Shift** to move freely. Click a panel, then drag the **Scale** bar or scroll the mouse wheel (50%–200%). **Reset** on the Bars tab restores default positions.
- A cog next to a feature toggle opens that feature’s subsettings in a side window.
- The toolbar gear opens **Theme**: **Accent**, **Pane** color, pane **Opacity**, **Font** (installed TrueType families on your PC, applied to every menu and HUD label except the watermark VOIDMARK logo and the VOIDMARK Dev nametag), **Scale** (100% / 90% / 75% / 50%), **HUD** opacity for overlay panes, and **HUD stars**. Theme and feature panes stay more opaque than the main window so the text stays readable. Animation toggle is there too.
- The bell opens **What’s new**: a versioned changelog. An accent dot on the bell means there are notes you have not opened yet.
- Search (`Ctrl+F` or the magnifier) jumps to a setting.
- **Bars** (Bars tab) restyles vanilla HUD layers in the same pane look as the click GUI. Compact pieces (hotbar, health, hunger, armor, air, experience, mount health, boss bar, status effects, held item) have no accent rail. Overlay panes and the scoreboard keep a rail on the side closer to the screen edge. Each piece has its own switch. Turning one on hides that vanilla layer so they do not stack. Turn a switch off to get the original Minecraft HUD back. Drag and scale each piece in the HUD editor. The inventory HUD on Overlay is a separate overlay, not the hotbar replacement.
- **Inventory HUD** (Overlay tab cog) draws your armor, storage, and hotbar on-screen. Hotbar, armor/offhand, and the `n/41` count can each be toggled there. Move and scale it in the HUD editor. It reads the live inventory every frame. Hide it with F1; it also hides while a chest or the vanilla inventory is open.
- Click your **skull or name** in the sidebar for a 3D rotatable preview of your skin with nick and custom cape. The model fills the You card, follows the menu scale, and is drawn without armor or held items. A vanilla Minecraft nametag sits above the head. The Cape card stays locked until your UUID is on the shop list. Then paste a PNG URL, click **Local file...**, or **Create cape...** to crop any photo (PNG or JPEG) onto the 10×16 cape face: drag to pan, scroll to zoom, then Apply. The crop is baked into a vanilla cape atlas and published so everyone sees the same cut. **Refresh capes** (everyone can use it, even if the card is locked) pulls the latest shop capes and head tags for nearby players, at most once every 5 minutes. Other Voidmark users also pick them up when they join a world. Vanilla **64×32** (and 128×64, 256×128, …) cape templates skip the cropper and are used as-is. **Nick** replaces your username in chat, tab, the scoreboard, and nametags. `&6` `&l` `&r` (and the rest of the legacy codes) work in the input; the preview under it is what other HUD text will look like.
- **Nametags** (ESP tab cog) default to Voidmark-styled name plates in the Minecraft font that keep drawing past vanilla’s 64-block cutoff (range 64–256m). **Style** on that cog switches to **Vanilla** chrome (Minecraft’s background box) while keeping the same range, Size slider (50–200%), Opacity, optional distance text, through-walls, and distance scaling. **Own nametag** is its own switch on ESP (off in first person either way) and shows your plate in F5; **VOIDMARK Dev** only draws when that is on. UUID v2 entities (Hypixel NPCs) are skipped; only UUID v4 players get a plate. The Dev line uses the menu font and sits in the same plate as the name. Vanilla Minecraft tags are hidden while Voidmark nametags are on so they do not stack. Your nick replaces your own name in F5.

### Visuals → World

Recolors terrain toward a color you pick. Strength goes from a light wash to a full client-style paint. **Mode** (World tint cog) picks how: **Shader** (default) paints in Sodium's chunk shader, so fullbright cannot cancel it; **Lightmap** is the older lighting wash. Lightmap mode shows a reminder that **fullbright must be off** or the wash will not appear. Skybox and fog cogs can match the world color or use their own. Aspect chips are on the Aspect cog.

### Visuals → ESP

Click one or more types in the scrollable list of every living vanilla type plus players. Click a selected row again to deselect it. Matching entities are drawn into the outline buffer as a silhouette, then a custom post shader blurs that mask and keeps only the outside so you get a clean rim plus an outward gradient. `/vm esp <text>` does the same for nametags that contain that word: it reads the name from entity metadata packets and glows the living mob in render distance, even before the hologram plate draws. Entities that already glow in vanilla are left on Minecraft’s sobel outline. **Block outline** applies the same glow to the block under your crosshair; its color and opacity are on the Block outline cog. Through-walls, size, opacity, and color for mobs are on the Mob glow cog.

### Skyblock → Nodes

Markers only run in Skyblock by default. Enable **Force enable** on the Nodes cog to test in singleplayer with magenta terracotta. **Node HUD** and **Node ESP** (boxes, tracers, fill, color) are on this page. Status (Hypixel / Skyblock / The End, FPS, ping) is on the same page.

### Skyblock → Mining

Commission lines come from the Skyblock tab list (`Commissions:` widget) in the Dwarven Mines, Crystal Hollows, Glacite, and the other mining islands. Turn on Player List Info in SkyBlock Menu → Settings → Personal → User Interface if the widget is missing. The compact HUD lists each job with a progress bar and percent, plus the pickaxe ability timer. Ability ready uses the Hypixel chat lines `Pickobulus is now available!` and `Mining Speed Boost is now available!`. Titanium ESP uses that same widget: if a job name contains Titanium and is not Done, polished diorite in range is outlined, and neighboring ores merge into one box. Location jobs (`Lava Springs Titanium`, `Cliffside Veins Titanium`, `Rampart's Quarry Titanium`, `Upper Mines Titanium`, `Royal Mines Titanium`) only outline veins in that SkyHanni-mapped region. Neighbouring zones (Far Reserve, Goblin Burrows, The Mist, the Village, the Forge) are excluded so they cannot steal an adjacent job. `Titanium Miner` outlines every vein in range.

## Settings

| Tab | What it does |
| --- | --- |
| World | World tint, skybox, fog, aspect — extra options on each cog |
| ESP | Mob list, glow, block outline, nametags |
| Overlay | Watermark / music / raw mats / inventory HUDs |
| Bars | Custom vanilla HUD: hotbar, bars, scoreboard, boss, effects, held item |
| Nodes | Ender node markers, node HUD, and live Hypixel/Skyblock status |
| Mining | Compact commission HUD, pickaxe cooldown, Titanium ESP |

Config is saved to `.minecraft/config/voidmark.json`, including click-GUI position and the last tab you had open.

## Cape shop

Paid custom capes ($1 via PayPal Friends and Family). You whitelist their Minecraft username or UUID on the cape site. They crop a photo with **Create cape** in Voidmark (or you do it on the cape desk). Other Voidmark clients fetch `/capes/{uuid}.png`. Changing the cape in the menu overwrites that file; others pick it up the next time they join a world, or when they click **Refresh capes** (once every 5 minutes). Players can change their own cape once per 24 hours unless **Upload bypass** is checked on the admin list. Admin cape uploads skip that limit. If a UUID is not on the list, the in-game Cape card stays locked. The shop still answers `uuid not whitelisted` if someone bypasses the lock. **Head tag** on the admin list is custom text above their nametag for Voidmark users (`&6` `&l` and the rest of the nick color codes). It refreshes on the same join or refresh.

The mod always talks to `https://voidmark.cloud`. That host is not a config option. Hosting notes are in **[web/CLOUDFLARE.md](web/CLOUDFLARE.md)**.

Friends and Family has no PayPal purchase protection. Capes only show for Voidmark users.

Local testing only:

```bash
node web/server.mjs
```

- Site: `http://127.0.0.1:43150` (override with `VOIDMARK_CAPE_PORT`). Public shop is `/`. Admin is `/admin.html`.
- After a payment, open `/admin.html`, enter `VOIDMARK_CAPE_ADMIN` (default `change-me`), and add their username or UUID.
- Keep `web/data/whitelist.json` off git.

Developer builds stamp a small **DEV** tag on the watermark next to VOIDMARK.

## Develop

```bash
# Java 25
./gradlew build
./gradlew runClient
```
