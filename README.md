# Voidmark

Fabric 26.1.2 QoL for Hypixel Skyblock. Marks **Ender Nodes** on the End Island, tints the world and skybox, and stretches aspect ratio. The config menu is a compact NEVERLOSE-style click GUI (`/voidmark`): a dark frosted-blue sidebar over the blurred world, a solid near-black content pane, rounded chrome, Nunito Sans, and icon-font glyphs. It floats in the center of the screen instead of filling it.

Ender Nodes look like purple stained clay (magenta terracotta on modern versions) and spit portal-colored dust. Voidmark scans loaded chunks, listens for those particles, then draws through-wall boxes, an outline, and a tracer to the nearest node.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for **Minecraft 26.1.2**.
2. Drop [Fabric API](https://modrinth.com/mod/fabric-api/versions?g=26.1.2) into `mods`.
3. Build this project (`./gradlew build`) and put `build/libs/voidmark-1.1.11.jar` in `mods`.
4. Launch the Fabric 26.1.2 profile.

Java **25** is required.

## Use

- `/voidmark` or `/vm` opens the config screen.
- Right Shift is the default keybind (Controls → Voidmark).
- `/voidmark toggle` flips node markers without opening the menu.

### Visuals → World

Tints fog, lighting, and the sky disc toward a color you pick. Strength sliders go from a light wash to a full client-style recolor. Skybox can match the world color or use its own.

### Visuals → View

Aspect ratio stretches the world horizontally the same way 4:3 on a 16:9 panel does. Native is 100%. **4:3** is the usual stretched look.

### Nodes

Markers only run in Skyblock by default. Enable **Force enable** to test in singleplayer with magenta terracotta.

## Settings

| Tab | What it does |
| --- | --- |
| World | World tint, skybox tint, colors, strength |
| View | Aspect ratio slider and 4:3 / 16:10 / 5:4 presets |
| Markers | Scan, End-only filter, particles |
| Display | HUD, boxes, tracers, marker color |
| Status | Hypixel / Skyblock / The End readout |

Config is saved to `.minecraft/config/voidmark.json`.

## Develop

```bash
# Java 25
./gradlew build
./gradlew runClient
```
