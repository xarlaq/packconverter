# Pack Converter

### Old resource packs on the newest Minecraft.

Use resource packs made for **any version from 1.7 to 26.2** on Minecraft 26.3, without manually updating them.
Turn an old pack on like any other, and **pack loads become almost instant**: it’s converted once, then cached for every load after.
Switching packs is smooth too: no loading screen and no long freeze, even for a pack you've never used.
Your original packs stay untouched, and packs made for 26.3 are never converted.

**Fabric • Minecraft 26.3 • Client-side**

---

## Features

| Feature | What it does |
| --- | --- |
| **Automatic conversion** | Old packs work as they are: textures, models, GUI, items and mobs are renamed and rearranged for 26.3. |
| **Convert and save** | Saves a converted copy as a normal 26.3 pack that works without the mod, one pack or a whole folder. |
| **Pack previews** | Hover a pack to preview its HUD, items, blocks and armor on a 3D player before turning it on. |
| **Pack folders** | Sort packs into folders in the Resource Packs screen; search looks through all of them. |
| **Favourites** | Star a pack to keep it at the top of the list, wherever its folder is. |
| **Random pack** | A die button next to Done swaps in a random pack, from all packs or only favourites. Right-click it to bind a key and roll packs while playing. |
| **Netherite from diamond** | Packs from before netherite get it, recoloured from their own diamond items, in any colour. |
| **Fast pack switching** | No loading screen and no long freeze: new packs go in over a few frames, and packs never used before are converted in the background first. |

<details>
<summary>What gets converted</summary>

- Every file renamed since 1.7 (the 1.13 Flattening, 1.14 dyes, 1.21.2 armor, 26.1 entity folders...) and every reference to it
- GUI sheets split into 1.20.2+ sprites: hotbar, hearts, buttons, containers
- 1.9 inventory layouts (survival and creative) with an off-hand slot in the pack's own style
- 1.8 held-item positions, chests, beds, signs, and mobs whose models changed
- Particles, paintings, effect icons, compass and clock, enchantment glint, menu background
- Custom model data, custom bows, `.lang` files and OptiFine (`mcpatcher/`) folders

Nothing is added from elsewhere: what an old pack doesn't have stays vanilla.

</details>

---

## Settings

In **Mod Menu**, or **Settings** in Pack Converter (its button is on the title screen, the pause menu and the Resource Packs screen).

| Option | Default | What it does |
| --- | --- | --- |
| **Convert old packs** | on | Converts old packs when you turn them on. |
| **Pack folders** | on | Folders in the Resource Packs screen. |
| **Pack previews** | on | The preview when hovering a pack. |
| **Previews show** | All | Every material in turn (wood to netherite), or just one. |
| **Make netherite** | on | Netherite from diamond, in the colour of the swatch next to it. |
| **Fast pack switching** | on | Keeps the game on screen while packs load, with no long freeze when they go in. |
| **Hide loading bar** | on | No progress bar while packs switch (RRLS's too). |
| **Favourite packs** | on | The star on each pack. |
| **Random pack** | All packs | The die button and its key: from all packs, only favourites, or off. |
| **Cache limit** | 1 GB | Space converted packs may use; the oldest go first. |
| **Export folder** | `resourcepacks/converted` | Where "Convert and save" puts converted packs. |

---

## Installation

1. Install Fabric Loader
2. Install Fabric API
3. Place Pack Converter into your `mods` folder
4. Launch Minecraft

Mod Menu is optional. Works on Lunar Client, and alongside Sodium, RRLS and Async Pack Scan.

---

## Building

```
./gradlew build
```

On Windows:

```
gradlew.bat build
```

The release jar is `build/libs/packconverter-<version>+26.3.jar` — ignore the `-sources` one.

---

## License

Copyright (C) 2026 xarlaq. Pack Converter is licensed under **GPL-3.0-only**.

Parts are adapted from other projects, and their notices ship inside the jar:

| Project | Licence |
| --- | --- |
| [Mojang's slicer](https://github.com/Mojang/slicer) (GUI sprite migrations) | MIT |
| [ResourcePackConverter](https://github.com/agentdid127/ResourcePackConverter) by agentdid127 (layouts, 1.13 names) | MIT |
