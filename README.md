# Voidmark

Fabric 26.1.2 QoL for Hypixel Skyblock. Marks **Ender Nodes** on the End Island and opens a dark, compact config UI.

Ender Nodes look like purple stained clay (magenta terracotta on modern versions) and spit portal-colored dust. Voidmark scans loaded chunks, listens for those particles, then draws through-wall boxes, an outline, and a tracer to the nearest node.

The UI and world-render approach follow the same 26.1.2 patterns used by [NoammAddons](https://github.com/Noamm9/NoammAddons): a custom click GUI, Skyblock location from scoreboard / tab list, and Fabric `LevelRenderEvents` pipelines that ignore depth.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for **Minecraft 26.1.2**.
2. Drop [Fabric API](https://modrinth.com/mod/fabric-api/versions?g=26.1.2) into `mods`.
3. Build this project (`./gradlew build`) and put `build/libs/voidmark-1.0.0.jar` in `mods`.
4. Launch the Fabric 26.1.2 profile.

Java **25** is required.

## Use

- `/voidmark` or `/vm` opens the config screen.
- Right Shift is the default keybind (Controls → Voidmark).
- `/voidmark toggle` flips node markers without opening the menu.

On the End Island the HUD in the top-left lists how many nodes are in range and which way the closest one is. Markers only run in Skyblock by default. Enable **Force enable** in the Nodes tab to test in singleplayer: place magenta or purple terracotta and they light up the same way.

## Settings

| Tab | What it does |
| --- | --- |
| Nodes | Master switch, End-only gate, block scan, particle hints, scan radius |
| Display | HUD, filled box, outline, tracer, through-walls, opacity, color |
| Status | Live Hypixel / Skyblock / The End readout and tracked count |

Config is saved to `.minecraft/config/voidmark.json`.

## Develop

```bash
# Java 25
./gradlew build
./gradlew runClient
```

`runClient` needs a desktop session. This repo is a client-only Fabric mod, not a web app.
