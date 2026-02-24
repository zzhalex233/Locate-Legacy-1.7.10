# Locate Command for Minecraft 1.7.10 (Forge)

Based on **GTNH Team – ExampleMod1.7.10**  
https://github.com/GTNewHorizons/ExampleMod1.7.10

A utility mod for **Minecraft 1.7.10** that adds a `/locate` command
similar to newer Minecraft versions, with performance and mod compatibility in mind.

---

## Features

### Locate structures

`/locate structure <namespace:id>`

- Uses **explicit `namespace:id` identifiers** (no legacy short names)
- Currently supported vanilla structures:
  - `minecraft:village`
  - `minecraft:stronghold`
  - `minecraft:mineshaft`
  - `minecraft:desert_pyramid`
  - `minecraft:jungle_pyramid`
  - `minecraft:swamp_hut`
- Each structure type is located **independently**
- Avoids biome-dependent ambiguity (e.g. temples resolving to different structures)

### Locate biomes

`/locate biome <name>`

- Works with **vanilla and modded biomes**
- Also works at **mod's dimensions**
- Compatible with mods such as **Biomes O’ Plenty**

### Safe Teleport

- Clickable coordinates in chat
- Automatically finds a **safe landing position**
- If the target is ocean or liquid, searches for the **nearest solid ground**
- Nether / no-sky dimensions avoid bedrock ceiling issues

### Other

- `/locate cancel` — cancel a running locate task
- Config file:  
  `.minecraft/config/LocateLegacy.cfg`
  - Enable or disable clickable teleport
- Displays the **distance to the target** in chat

### Environment Compatibility

- Tested in **GTNH 2.8.4**
, Village locating and modded biome locating work reliably
- Tested in Biome o' Plenty enviroment
---

## Notes

> **Warning**  
> Due to limitations of Minecraft 1.7.10, structure locating is based on
> **structure spawn rules** rather than guaranteed generation.
>
> This means a located position *may* not always contain a generated structure,
> especially in unexplored terrain.
>
> Later versions (v2.3+) reduce the likelihood of this issue (?).

---

## Localization

- English and Chinese are supported (since v2.3.2)

---

## Download

Download the mod from the **Releases** page.
