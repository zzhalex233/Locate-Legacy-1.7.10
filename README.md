# Locate Command for Minecraft 1.7.10 (Forge)

Based on **GTNH Team – ExampleMod1.7.10**  
https://github.com/GTNewHorizons/ExampleMod1.7.10

A utility mod for **Minecraft 1.7.10** that adds a `/locate` command
similar to newer Minecraft versions.

Starting from **v3.0**, LocateLegacy adopts a **.json design**
for trying to support modded structures and biomes without hard-coded integration.

---

## Features

### Locate structures

`/locate structure <namespace:id>`

- Uses **`namespace:id` identifiers only**
- Vanilla structures supported out of the box:
  - `minecraft:village`
  - `minecraft:stronghold`
  - `minecraft:mineshaft`
  - `minecraft:desert_pyramid`
  - `minecraft:jungle_pyramid`
  - `minecraft:swamp_hut`
  - `minecraft:fortress`
- Each structure type is located independently
- Avoids biome-dependent for temple in MapGen
  (temples resolving to different structures according to biomes)

#### .JSON addition structure support (v3.0 +)


- Structure locating can extended via:`config/LocateLegacyStructures.json`
- Additional structure definitions can be provided by
**"biomepack" resourcepacks**
- Supports:
- Modded dimensions
- Modded MapGen structures
- Biome ID filtering (whitelist / blacklist / all)

---

### Locate biomes

`/locate biome <name>`

- Works with **vanilla and modded biomes**
  <small>You need to walk around a few steps to explore the nearby biome</small>
- Works in **modded dimensions**
- Compatible with mods such as **Biomes O’ Plenty**

#### Biome cache (v3.0 +)

- Biomes are collected from:
- Player exploration (automatically recorded)
- External biome lists provided by biomepack resource packs
- Biome data is stored in:`.minecraft/config/LocateLegacyBiomeList.json`
- Allows biome locating in dimensions before the player has explored them (with biomepacks)

---

### Safe Teleport

- Clickable coordinates in chat
- Uses a safe-teleport algorithm for: 
  Structure locate & Biome locate
- Automatically finds a **safe landing position**
- Handles edge cases:
  Liquids and oceans
  Solid block suffocation
  Nether / no-sky dimensions : Bedrock ceiling avoidance

---

### Resourcepack Extension (v3.0 +)

LocateLegacy 3.0 supports a custom resource pack format:

**BiomePack**
- File name pattern:`*-biomepack.zip`
- Location:`.minecraft/resourcepacks/`
- Supported files inside biomepacks:
`config/LocateLegacyStructures.json`
`config/LocateLegacyBiomeList.json`

- Only **currently selected** resource packs are used
- Switching resource packs will updates:
  - Available structures
  - Available biomes
- Enables mod compatibility 

---

### Other Commands

- `/locate cancel`  
  Cancel a running locate task
- `/locate debug biome`  
  Show current biome name and biome ID
- `/locate debug structures`  
  List detected structure generators in the current dimension (to find MapGen)

---

## Configuration

config file:`.minecraft/config/LocateLegacy.cfg`

- Enable or disable clickable teleport

Data files:
`.minecraft/config/LocateLegacyStructures.json`
`.minecraft/config/LocateLegacyBiomeList.json`

These can be **overridden by biomepack resource packs**.

---

## Environment Compatibility

- Tested in **GTNH 2.8.4**
- Tested with **Biomes O’ Plenty**

---

## Notes

> **Warning**  
> Due to limitations of Minecraft 1.7.10, structure locating is based on
> **structure spawn rules**, not guaranteed generation.
>
> A located position may not always contain a generated structure,
> especially in unexplored or heavily modified worlds.
>
> This is a vanilla limitation and cannot be fully avoided.

---

## Localization

- English
- Chinese  
(Supported since v2.3.2)

---

## Download

Download the mod from the **Releases** page.
Download the official biomepack from the **Releases** page.