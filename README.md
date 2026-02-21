# Locate Command for Minecraft 1.7.10 (Forge)

Based on **GTNH Team – ExampleMod1.7.10**  
https://github.com/GTNewHorizons/ExampleMod1.7.10

A simple utility mod for **Minecraft 1.7.10** that adds a `/locate` command similar to newer versions.

---

## Features

### Locate structures

`/locate structure <name>`

- Supports vanilla structures  
  (`village`, `stronghold`, `mineshaft`, `temple`)
- Searches the nearest valid structure location

### Locate biomes

`/locate biome <name>`

- Works with **vanilla and modded biomes**
- Compatible with mods such as **Biomes O’ Plenty**

### Safe Teleport
- Clickable coordinates in chat
- Automatically finds a **safe landing position**
- If target is ocean/liquid, will search for **nearest land**
- Nether / no-sky dimensions avoid bedrock ceiling

### Other
- `/locate cancel` — cancel a running locate task
- Tab-completion support
- Config `.minecraft/config/LocateLegacy.cfg` for whether could click to teleport and more...
- Display the distance to the target in the chat box

### Environment Compatible
- tested in GTNH2.8.4: locating village and mod's biome has perfectly performed

---

## Notes

> **Warning**  
> Due to limitations of Minecraft 1.7.10, structure locating is based on structure spawn logic rather than guaranteed generation.  
> In some cases, the located coordinates may not contain a generated structure. ps:v2.3 later, this situation is less possible

---

## Download

Download the mod from the **Releases** page.

## Localization

Already support English and Chinese in v2.3.2
