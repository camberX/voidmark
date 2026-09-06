# Voidmark

A client-side Fabric mod for Minecraft 26.1.2 focused on Hypixel SkyBlock quality-of-life features and visual customization.

Voidmark includes Ender Node highlighting, mining and farming tools, configurable HUD elements, custom menus, entity ESP, capes, media controls, and a compact in-game settings interface.

## Requirements

- Minecraft 26.1.2
- Java 25
- [Fabric Loader](https://fabricmc.net/use/installer/)
- [Fabric API](https://modrinth.com/mod/fabric-api/versions?g=26.1.2)

## Installation

1. Install Fabric Loader for Minecraft 26.1.2.
2. Add Fabric API to your `mods` folder.
3. Download Voidmark from [voidmark.cloud](https://voidmark.cloud) and place the JAR in the same folder.
4. Remove older `voidmark-*.jar` files, then launch the Fabric profile.

You can also build the mod yourself and use the JAR generated in `build/libs`.

## Features

### SkyBlock

- Ender Node boxes, outlines, tracers, particles, and a nearby-node HUD
- Custom Loadouts and Wardrobe menus with 3D equipment previews
- Mining commission progress and pickaxe ability cooldowns
- Titanium ESP that follows active commission locations
- Raw material tracking for SkyBlock recipes and storage
- Farming yaw and pitch display while holding a Farming Tool
- Farm Keys mode for swapping controls, toggling attack, and lowering sensitivity
- Chest ESP with configurable range, color, and aim assistance

### Visuals and ESP

- World, skybox, and fog tinting
- Custom aspect ratios
- Entity and nametag-based ESP
- Configurable block outlines and player nametags
- Hitsounds and hitmarkers for melee and ranged attacks
- Client-side item appearance overrides
- Custom capes and nicknames

### HUD and interface

- Movable and scalable HUD editor
- Watermark, inventory, music, mining, node, raw-material, and pickup overlays
- Restyled hotbar, health, armor, hunger, experience, scoreboard, and other vanilla HUD elements
- Pickup log for inventory gains, including direct rewards
- Custom title screen and consistent menu styling
- Configurable colors, fonts, opacity, scale, and animations

## Controls and commands

The default menu key is **Right Shift**. Keybinds can be changed under **Controls → Voidmark**.

| Command | Description |
| --- | --- |
| `/voidmark` or `/vm` | Open the Voidmark menu |
| `/voidmark toggle` | Toggle Ender Node markers |
| `/vm edit` | Open the held-item appearance editor |
| `/vm loadouts` | Open the custom Loadouts menu |
| `/vm wardrobe` | Open the custom Wardrobe menu |
| `/vm rawmats [item]` | Track raw materials for a SkyBlock item |
| `/vm rawmats refresh` | Refresh profile storage data |
| `/vm rawmats clear` | Hide the material tracker |
| `/vm esp <name>` | Add a nametag ESP filter |
| `/vm esp clear [name]` | Remove nametag ESP filters |
| `/vm farmkeys` or `/vm fk` | Toggle farming controls |
| `/vm music` | Show music integration status |

Music controls are also available through `.np`, `.play`, `.pause`, `.skip`, and `.prev`. These messages are handled locally and are not sent to the server.

## Configuration

Open the menu to configure individual features and their settings. The toolbar HUD button opens the layout editor, where overlays can be dragged and scaled.

Settings are saved in:

```text
.minecraft/config/voidmark.json
```

## Building from source

Clone the repository and run:

```bash
./gradlew build
```

Java 25 is required. The built JAR is written to `build/libs`, and the website release files are synchronized under `web/public/mod`.

To launch a development client:

```bash
./gradlew runClient
```

## Website and cape service

The `web` directory contains the Voidmark download site and cape service. Local development can be started with:

```bash
node web/server.mjs
```

The local site runs at `http://127.0.0.1:43150` by default. See [web/CLOUDFLARE.md](web/CLOUDFLARE.md) for deployment instructions.

## License

Voidmark is released under the [CC0 1.0 Universal](LICENSE) public-domain dedication.
