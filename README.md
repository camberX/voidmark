# Voidmark

Fabric 26.1.2 QoL for Hypixel Skyblock. Marks **Ender Nodes** on the End Island, tints the world and skybox, and stretches aspect ratio. The config menu is a compact NEVERLOSE-style click GUI (`/voidmark`): a dark frosted-blue sidebar over the blurred world, a near-black content pane with animated starfield particles, rounded chrome, Nunito Sans, and icon-font glyphs. It floats in the center of the screen instead of filling it. The Minecraft title screen is replaced with a matching Voidmark menu: a full-screen starfield, taller Singleplayer / Multiplayer / Realms buttons, and the same pane chrome.

Ender Nodes look like purple stained clay (magenta terracotta on modern versions) and spit portal-colored dust. Voidmark scans loaded chunks, listens for those particles, then draws through-wall boxes, an outline, and a tracer to the nearest node.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for **Minecraft 26.1.2**.
2. Drop [Fabric API](https://modrinth.com/mod/fabric-api/versions?g=26.1.2) into `mods`.
		3. Build this project (`./gradlew build`) and put `build/libs/voidmark-1.1.78.jar` in `mods`.
4. Launch the Fabric 26.1.2 profile.

Java **25** is required.

## Use

- The Minecraft **title screen** is replaced with a Voidmark menu: starry sky background, VOIDMARK title, and taller Singleplayer / Multiplayer / Realms / Options / Quit buttons in the same pane style as the click GUI. Language, Accessibility, and credits stay as text links at the bottom. That same chrome continues onto the **world list**, **server list**, **options**, pause menu, Realms, language, accessibility, and the rest of the out-of-world menus: starfield instead of dirt, pane buttons, restyled lists, sliders, and text fields. Inventory and chat stay vanilla.
- `/vm edit` or `/voidmark edit` opens the item id window. It shows the item you are holding, with its `minecraft:` id or Skyblock `sb:` id in a text box and a large preview above it. Typing another id reskins that item on your client: hand, hotbar, and inventory all show the new look. The server still has the real item. Vanilla ids look like `minecraft:diamond_sword`; Skyblock ids look like `sb:HYPERION`. Tab or click a suggestion to fill it. Type the original id again to clear the reskin.
- `/vm rawmats sb:HYPERION` (or `/voidmark rawmats`, or `/vm rawmats` while holding the item) expands that Skyblock craft and shows a HUD with item icons, have/need counts, and a live progress bar per ingredient. **Materials** (Display tab, or click Raw/Enchanted on the HUD with chat open) picks **Raw** (Iron Ingot) or **Enchanted** (Enchanted Iron). Inventory and armor are counted live. Ender Chest and backpacks come from your Skyblock profile (`hypixel.odtheking.com`) so island swaps and profile refreshes do not wipe them. `/vm rawmats refresh` pulls storage again; `/vm rawmats raw` / `/vm rawmats enchanted` switch the mode; `/vm rawmats clear` hides the tracker.
- The **Music HUD** (Display tab, or the bell) shows the song that is playing in Spotify or YouTube Music: cover art, title, artist, source, a progress bar, and elapsed/duration on the right of the bar (`1:23/3:45`). Pause freezes the bar; scrubbing updates it when the player reports a real timestamp. YouTube Music in a browser often keeps SMTC position at `0` — use YouTube Music Desktop (port 9863) or th-ch YouTube Music (26558) for a live clock. When the track changes, chat shows a styled `VOIDMARK | NOW PLAYING` line with the title and artist. Open Minecraft chat (`T`) to click **previous / play-pause / next** on the HUD. You can also type `.np` `.play` `.pause` `.skip` `.prev` in chat (those stay client-side) or `/vm music`. On Windows it reads the system now-playing session (including SMTC album art), Spotify / YouTube Music window titles, and local companion APIs (YouTube Music Desktop on 9863, th-ch YouTube Music on 26558). Linux uses `playerctl` metadata and `mpris:artUrl`.
- **Mobs** (Mobs tab) highlights every loaded entity of the types you pick, including other players. Scroll the full vanilla list (or type in the list search) and click rows to select them; click a selected row again to drop it. Matching entities get a **silhouette outline** with a shader gradient that fades outward from the model — not boxes, and not Minecraft’s sobel glow. You are never outlined. **Block outline** (on by default) puts that same glow on every edge of the block you are looking at, including the inner edges, around the vanilla selection wire. Opacity, color, and through-walls are on the same page. Reset restores only this tab.
- **Mining** (Mining tab) shows a HUD with current commission progress from the scoreboard and your pickaxe ability cooldown. Use a pickaxe ability and the HUD counts down (120s for Mining Speed Boost, 60s for Pickobulus) until chat says it is ready. **Ability alert** (on by default) flashes a centered **ABILITY READY** banner when chat contains `Pickobulus is now available!` or `Mining Speed Boost is now available!`. Drag the panel in the HUD editor.

- Right Shift is the default keybind (Controls → Voidmark). Press it again to close (the menu eases out).
- `/voidmark toggle` flips node markers without opening the menu.
- Toolbar **HUD** opens the HUD editor: drag any overlay (inventory, watermark, nodes, music, raw mats, mining) and every custom vanilla HUD piece (hotbar, bars, scoreboard, boss, effects, held item). They snap to screen axes and to each other; hold **Shift** to move freely. Click a panel, then drag the **Scale** bar or scroll the mouse wheel (50%–200%). **Reset** on the HUD tab restores default positions.
- **Reset** restores only the page you are looking at.
- The gear opens **Theme**: **Accent**, **Pane** color, and **Opacity** (the menu pane plus every HUD that uses that pane). Animation toggle is there too.
- The bell toggles overlay HUD pieces: inventory, watermark (FPS, ping, clock, name), and each custom vanilla HUD replacement.
- Search (`Ctrl+F` or the magnifier) jumps to a setting.
- **HUD** (HUD tab, or the bell) restyles vanilla HUD layers in the same pane/accent look as the click GUI. Each piece has its own switch. Turning one on hides that vanilla layer so they do not stack: hotbar, health, hunger, armor, air, experience, mount health, scoreboard, boss bar, status effects, and the held-item name. Turn a switch off to get the original Minecraft HUD back. Drag and scale each piece in the HUD editor. The inventory HUD on the Inventory tab is a separate overlay, not the hotbar replacement.
- **Inventory HUD** (Inventory tab, or the bell) draws your armor, storage, and hotbar on-screen. Hotbar, armor/offhand, and the `n/41` count can each be toggled there. Move and scale it in the HUD editor. It reads the live inventory every frame. Hide it with F1; it also hides while a chest or the vanilla inventory is open.
- The **Cape** tab previews a custom cape. Paste a PNG URL or click **Local file...** for a native file picker. It is client-side on you only. Vanilla **64×32** (and 128×64, 256×128, …) cape templates are used as-is. Any other PNG, including photos like 352×272, is fitted into the cape’s front and back faces so it does not stretch into bands.
- **Nick** replaces your username in chat, tab, the scoreboard, and nametags. `&6` `&l` `&r` (and the rest of the legacy codes) work in the input; the preview under it is what other HUD text will look like. The same tab has **Nametags**: Voidmark-styled name plates that keep drawing past vanilla’s 64-block cutoff (range 64–256m). They scale with distance and a Size slider (50–200%). Optional distance text (not on your own tag) and through-walls. UUID v2 entities (Hypixel NPCs) are skipped; only UUID v4 players get a plate. The **VOIDMARK Dev** header is attached above the name pill for one account and is not shown on anyone else. Vanilla tags are hidden while this is on so they do not stack.

### Visuals → World

Recolors terrain toward a color you pick. Strength goes from a light wash to a full client-style paint. **Mode** picks how: **Shader** (default) paints in Sodium's chunk shader, so fullbright cannot cancel it; **Lightmap** is the older lighting wash. Lightmap mode shows a reminder that **fullbright must be off** or the wash will not appear. Skybox can match the world color or use its own. This does not change fog.

### Visuals → Fog

Separate custom fog: color, start, end (as a percent of view distance), and density. Leaves vanilla water, lava, and powdered-snow fog alone. Can match the world tint color.

### Visuals → View

Aspect ratio stretches the world horizontally the same way 4:3 on a 16:9 panel does. Native is 100%. Use the **Native / 16:10 / 4:3 / 5:4** chips on the View page. **4:3** is the usual stretched look.

### Visuals → Mobs

Click one or more types in the scrollable list of every living vanilla type plus players. Click a selected row again to deselect it. Matching entities are drawn into the outline buffer as a silhouette, then a custom post shader blurs that mask and keeps only the outside so you get a clean rim plus an outward gradient. **Block outline** applies the same glow to every edge of the block under your crosshair. Through-walls (mobs only), opacity, and color are on that page.

### Nodes

Markers only run in Skyblock by default. Enable **Force enable** to test in singleplayer with magenta terracotta.

### Mining

Commission lines come from the Skyblock sidebar in the Dwarven Mines, Crystal Hollows, Glacite, and the other mining islands. The HUD lists each job with a bar and the pickaxe ability timer. Ability ready uses the Hypixel chat lines `Pickobulus is now available!` and `Mining Speed Boost is now available!`.

## Settings

| Tab | What it does |
| --- | --- |
| World | Block tint, shader/lightmap mode, skybox tint, colors, strength |
| Fog | Custom fog color, start, end, density |
| View | Aspect ratio slider and Native / 16:10 / 4:3 / 5:4 chips |
| Mobs | Multi-select mob list (including players), hovered-block glow, shader silhouette outline |
| Markers | Scan, End-only filter, particles |
| Mining | Commission HUD, pickaxe cooldown, ability-ready alert |
| Display | Node HUD, watermark, music HUD, mining HUD, boxes, tracers, marker color |
| HUD | Custom vanilla HUD: hotbar, bars, scoreboard, boss, effects, held item |
| Inventory | Inventory HUD: enable, hotbar, armor, item count |
| Status | Hypixel / Skyblock / The End, live FPS and ping |
| Nick | Hide/replace your username with `&` color codes; long-range nametags |
| Cape | Custom cape from a PNG URL or a local file |

Config is saved to `.minecraft/config/voidmark.json`, including click-GUI position and the last tab you had open.

Developer builds stamp a small **DEV** tag on the watermark next to VOIDMARK.

## Develop

```bash
# Java 25
./gradlew build
./gradlew runClient
```
