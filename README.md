# Voidmark

Fabric 26.1.2 QoL for Hypixel Skyblock. Marks **Ender Nodes** on the End Island, tints the world and skybox, and stretches aspect ratio. The config menu is a compact NEVERLOSE-style click GUI (`/voidmark`): a dark frosted-blue sidebar over the blurred world, a solid near-black content pane, rounded chrome, Nunito Sans, and icon-font glyphs. It floats in the center of the screen instead of filling it.

Ender Nodes look like purple stained clay (magenta terracotta on modern versions) and spit portal-colored dust. Voidmark scans loaded chunks, listens for those particles, then draws through-wall boxes, an outline, and a tracer to the nearest node.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for **Minecraft 26.1.2**.
2. Drop [Fabric API](https://modrinth.com/mod/fabric-api/versions?g=26.1.2) into `mods`.
3. Build this project (`./gradlew build`) and put `build/libs/voidmark-1.1.28.jar` in `mods`.
4. Launch the Fabric 26.1.2 profile.

Java **25** is required.

## Use

- `/voidmark` or `/vm` opens the config screen.
- Right Shift is the default keybind (Controls → Voidmark). Press it again to close (the menu eases out).
- `/voidmark toggle` flips node markers without opening the menu.
- Toolbar **Unload** disables world tint, sky, fog, aspect, and markers in one click (**Load** restores them).
- **Reset** restores only the page you are looking at.
- The gear opens **Theme**: **Accent** (highlights, toggles, icons) and **Pane** (main window background) are separate colors. Animation toggle is there too.
- The bell toggles the **watermark** overlay (FPS, ping, clock, name).
- Search (`Ctrl+F` or the magnifier) jumps to a setting.
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
| Display | Node HUD, watermark, boxes, tracers, marker color |
| Status | Hypixel / Skyblock / The End, live FPS and ping |
| Nick | Hide/replace your username with `&` color codes |
| Cape | Custom cape from a PNG URL or a local file |

Config is saved to `.minecraft/config/voidmark.json`.

## Develop

```bash
# Java 25
./gradlew build
./gradlew runClient
```
