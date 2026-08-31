# Voidmark

Fabric 26.1.2 QoL for Hypixel Skyblock. Marks **Ender Nodes** on the End Island, tints the world and skybox, and stretches aspect ratio. The config menu is a compact NEVERLOSE-style click GUI (`/voidmark`): a dark frosted-blue sidebar over the blurred world, a solid near-black content pane, rounded chrome, Nunito Sans, and icon-font glyphs. It floats in the center of the screen instead of filling it.

Ender Nodes look like purple stained clay (magenta terracotta on modern versions) and spit portal-colored dust. Voidmark scans loaded chunks, listens for those particles, then draws through-wall boxes, an outline, and a tracer to the nearest node.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for **Minecraft 26.1.2**.
2. Drop [Fabric API](https://modrinth.com/mod/fabric-api/versions?g=26.1.2) into `mods`.
3. Build this project (`./gradlew build`) and put `build/libs/voidmark-1.1.20.jar` in `mods`.
4. Launch the Fabric 26.1.2 profile.

Java **25** is required.

## Use

- `/voidmark` or `/vm` opens the config screen.
- Right Shift is the default keybind (Controls → Voidmark).
- `/voidmark toggle` flips node markers without opening the menu.
- Toolbar **Unload** disables world tint, sky, fog, aspect, and markers in one click (**Load** restores them).
- The gear opens **Theme** (accent swatches, custom color, animation toggle).
- The bell toggles the **watermark** overlay (FPS, ping, clock, name).
- Search (`Ctrl+F` or the magnifier) jumps to a setting.
- The toolbar dropdown applies **Skyblock / Visuals / All / None** presets (aspect ratios on the View tab).

### Visuals → World

Recolors terrain toward a color you pick. Strength goes from a light wash to a full client-style paint. **Mode** picks how: **Shader** (default) paints in Sodium's chunk shader, so fullbright cannot cancel it; **Lightmap** is the older lighting wash. Lightmap mode shows a reminder that **fullbright must be off** or the wash will not appear. Skybox can match the world color or use its own. This does not change fog.

### Visuals → Fog

Separate custom fog: color, start, end (as a percent of view distance), and density. Leaves vanilla water, lava, and powdered-snow fog alone. Can match the world tint color.

### Visuals → View

Aspect ratio stretches the world horizontally the same way 4:3 on a 16:9 panel does. Native is 100%. **4:3** is the usual stretched look.

### Nodes

Markers only run in Skyblock by default. Enable **Force enable** to test in singleplayer with magenta terracotta.

## Settings

| Tab | What it does |
| --- | --- |
| World | Block tint, shader/lightmap mode, skybox tint, colors, strength |
| Fog | Custom fog color, start, end, density |
| View | Aspect ratio slider and 4:3 / 16:10 / 5:4 presets |
| Markers | Scan, End-only filter, particles |
| Display | Node HUD, watermark, boxes, tracers, marker color |
| Status | Hypixel / Skyblock / The End, live FPS and ping |

Config is saved to `.minecraft/config/voidmark.json`.

## Develop

```bash
# Java 25
./gradlew build
./gradlew runClient
```
