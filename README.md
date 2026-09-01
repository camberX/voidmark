# Voidmark

Fabric 26.1.2 QoL for Hypixel Skyblock. Marks **Ender Nodes** on the End Island, tints the world and skybox, and stretches aspect ratio. The config menu is a compact NEVERLOSE-style click GUI (`/voidmark`): a dark frosted-blue sidebar over the blurred world, a solid near-black content pane, rounded chrome, Nunito Sans, and icon-font glyphs. It floats in the center of the screen instead of filling it.

Ender Nodes look like purple stained clay (magenta terracotta on modern versions) and spit portal-colored dust. Voidmark scans loaded chunks, listens for those particles, then draws through-wall boxes, an outline, and a tracer to the nearest node.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for **Minecraft 26.1.2**.
2. Drop [Fabric API](https://modrinth.com/mod/fabric-api/versions?g=26.1.2) into `mods`.
3. Build this project (`./gradlew build`) and put `build/libs/voidmark-1.1.44.jar` in `mods`.
4. Launch the Fabric 26.1.2 profile.

Java **25** is required.

## Use

- `/voidmark` or `/vm` opens the config screen. Drag the title bar or top chrome to move it; the position and last tab are saved to config.
- `/vm edit` or `/voidmark edit` opens the item id window. It shows the item you are holding, with its `minecraft:` id or Skyblock `sb:` id in a text box and a large preview above it. Typing another id reskins that item on your client: hand, hotbar, and inventory all show the new look. The server still has the real item. Vanilla ids look like `minecraft:diamond_sword`; Skyblock ids look like `sb:HYPERION`. Tab or click a suggestion to fill it. Type the original id again to clear the reskin.
- The **Music HUD** (Display tab, or the bell) shows the song that is playing in Spotify or YouTube Music: cover art, title, artist, source, and a progress bar. Open Minecraft chat (`T`) to click **previous / play-pause / next** on the HUD. You can also type `.np` `.play` `.pause` `.skip` `.prev` in chat (those stay client-side) or `/vm music`. On Windows it reads the system now-playing session (including SMTC album art), Spotify / YouTube Music window titles, and local companion APIs (YouTube Music Desktop on 9863, th-ch YouTube Music on 26558). Linux uses `playerctl` metadata and `mpris:artUrl`.
- Right Shift is the default keybind (Controls → Voidmark). Press it again to close (the menu eases out).
- `/voidmark toggle` flips node markers without opening the menu.
- Toolbar **HUD** opens the HUD editor: drag any overlay (inventory, watermark, nodes) and every custom vanilla HUD piece (hotbar, bars, scoreboard, boss, effects, held item). They snap to screen axes and to each other; hold **Shift** to move freely. Click a panel, then drag the **Scale** bar or scroll the mouse wheel (50%–200%). **Reset** on the HUD tab restores default positions.
- **Reset** restores only the page you are looking at.
- The gear opens **Theme**: **Accent**, **Pane** color, and **Opacity** (the menu pane plus every HUD that uses that pane). Animation toggle is there too.
- The bell toggles overlay HUD pieces: inventory, watermark (FPS, ping, clock, name), and each custom vanilla HUD replacement.
- Search (`Ctrl+F` or the magnifier) jumps to a setting.
- **HUD** (HUD tab, or the bell) restyles vanilla HUD layers in the same pane/accent look as the click GUI. Each piece has its own switch. Turning one on hides that vanilla layer so they do not stack: hotbar, health, hunger, armor, air, experience, mount health, scoreboard, boss bar, status effects, and the held-item name. Turn a switch off to get the original Minecraft HUD back. Drag and scale each piece in the HUD editor. The inventory HUD on the Inventory tab is a separate overlay, not the hotbar replacement.
- **Inventory HUD** (Inventory tab, or the bell) draws your armor, storage, and hotbar on-screen. Hotbar, armor/offhand, and the `n/41` count can each be toggled there. Move and scale it in the HUD editor. It reads the live inventory every frame. Hide it with F1; it also hides while a chest or the vanilla inventory is open.
- The **Cape** tab previews a custom cape. Paste a PNG URL or click **Local file...** for a native file picker. It is client-side on you only. Vanilla **64×32** (and 128×64, 256×128, …) cape templates are used as-is. Any other PNG, including photos like 352×272, is fitted into the cape’s front and back faces so it does not stretch into bands.
- **Nick** replaces your username in chat, tab, the scoreboard, and nametags. `&6` `&l` `&r` (and the rest of the legacy codes) work in the input; the preview under it is what other HUD text will look like.

### Visuals → World

Recolors terrain toward a color you pick. Strength goes from a light wash to a full client-style paint. **Mode** picks how: **Shader** (default) paints in Sodium's chunk shader, so fullbright cannot cancel it; **Lightmap** is the older lighting wash. Lightmap mode shows a reminder that **fullbright must be off** or the wash will not appear. Skybox can match the world color or use its own. This does not change fog.

### Visuals → Fog

Separate custom fog: color, start, end (as a percent of view distance), and density. Leaves vanilla water, lava, and powdered-snow fog alone. Can match the world tint color.

### Visuals → View

Aspect ratio stretches the world horizontally the same way 4:3 on a 16:9 panel does. Native is 100%. Use the **Native / 16:10 / 4:3 / 5:4** chips on the View page. **4:3** is the usual stretched look.

### Nodes

Markers only run in Skyblock by default. Enable **Force enable** to test in singleplayer with magenta terracotta.

## Settings

| Tab | What it does |
| --- | --- |
| World | Block tint, shader/lightmap mode, skybox tint, colors, strength |
| Fog | Custom fog color, start, end, density |
| View | Aspect ratio slider and Native / 16:10 / 4:3 / 5:4 chips |
| Markers | Scan, End-only filter, particles |
| Display | Node HUD, watermark, music HUD, boxes, tracers, marker color |
| HUD | Custom vanilla HUD: hotbar, bars, scoreboard, boss, effects, held item |
| Inventory | Inventory HUD: enable, hotbar, armor, item count |
| Status | Hypixel / Skyblock / The End, live FPS and ping |
| Nick | Hide/replace your username with `&` color codes |
| Cape | Custom cape from a PNG URL or a local file |

Config is saved to `.minecraft/config/voidmark.json`, including click-GUI position and the last tab you had open.

Developer builds stamp a small **DEV** tag on the watermark next to VOIDMARK.

## Develop

```bash
# Java 25
./gradlew build
./gradlew runClient
```
