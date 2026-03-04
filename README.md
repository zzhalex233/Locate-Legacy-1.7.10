# Locate Command for Minecraft 1.7.10 (Forge)

Based on **GTNH Team - ExampleMod1.7.10**  
https://github.com/GTNewHorizons/ExampleMod1.7.10

A utility mod for **Minecraft 1.7.10** that adds a `/locate` command
similar to newer Minecraft versions.

Starting from **v3.0**, LocateLegacy adopts a **.json design**
for supporting modded structures and biomes without hard-coded integration.

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
- Handles shared MapGen cases by combining mapGen + biome/filter rules (for example ,temples resolving to different structures according to biomes)

#### .JSON additional structure support (v3.0+)

- Structure locating can be extended via: `config/LocateLegacyStructures.json`
- Additional structure definitions can be provided by **biomepack resourcepacks**
- Supports:
  - Dimensions
  - MapGen structures
  - Biome name filtering (whitelist / blacklist / all)
  - Height range filtering
  - Occupied chunk diameter filtering
  - To allow all biomes, use `biomeName: "all"`

---

### Locate biomes

`/locate biome <name>`

- Works with **vanilla and modded biomes**
- <small>You need to walk around a bit to observe nearby biome names in non-overworld dimensions at first. soon the data will be stored in cache</small>
- Works in **modded dimensions**

#### Biome cache (v3.0+)

- Biomes are collected from:
  - Player exploration (automatically recorded)
  - External biome lists provided by biomepack resource packs
- Biome data is stored in: `.minecraft/config/LocateLegacyBiomeList.json`
- With cache ,you do not need to travel around for observing biomes for locate everytime

---

### Safe Teleport

- Clickable coordinates in chat
- Uses a safe-teleport algorithm for:
  - Structure locate
  - Biome locate
- Automatically finds a **safe landing position**
- Handles edge cases:
  - Liquids and oceans
  - Solid block suffocation
  - Nether / no-sky dimensions (bedrock ceiling avoidance)

---

### Resourcepack Extension (v3.0+)

LocateLegacy supports a custom resource pack format:

**BiomePack**
- File name pattern: `*-biomepack.zip`
- Location: `.minecraft/resourcepacks/`
- Supported files inside biomepacks:
  - `config/LocateLegacyStructures.json`
  - `config/LocateLegacyBiomeList.json`

- Only **currently selected** resource packs are used
- If no biomepack is selected in-game, no biomepack override is applied
- Switching resource packs updates:
  - Available structures
  - Available biomes

---

### Other Commands

- `/locate cancel`  
  Cancel a running locate task
- `/locate debug biome`  
  Show current biome name
- `/locate debug structures`  
  Show detected MapGen info
- `/locate debug learn <namespace:id>`  
  Learn and merge structure profile samples information into temp file everytime using the command . Every last use will output average sample data (such as structure's height)
- You need to add a new structure `<namespace:id>` in `LocateLegacyStructrue.json`  in order to learn. The structure-related information can be empty or random, only <namespace:id> and dimID are necessary.
- Stand on the target strucutre ,then learn
- `/locate debug clearlearn`  
  Clear learned temp data (if you stand on a structure and learned the other)

Debug usability:
- `[Copy JSON]` button copies generated JSON directly to clipboard
---

## Configuration

Config file: `.minecraft/config/LocateLegacy.cfg`

- Enable or disable clickable teleport

Data files:
- `.minecraft/config/LocateLegacyStructures.json`
- `.minecraft/config/LocateLegacyBiomeList.json`
- `.minecraft/config/LocateLegacyLearnTemp.json` (temporary learn profile data)

These can be overridden by selected biomepack resource packs.

---

## Environment Compatibility

- Tested in **GTNH 2.8.4**
- Tested with **Biomes O' Plenty**
- Tested with **Twilight Forest**

---

## Notes

> **Warning**  
> Due to Minecraft 1.7.10 limitations, structure locating is based on
> **structure spawn rules**, not guaranteed final generation.
>
> A located position may not always contain an already generated structure,
> especially in unexplored or heavily modified worlds.
> 
> However, manually setting filter conditions in the .json, such as "occupied chunk diameter" and "structure height", can greatly increase the probability of locating the actual structure.

---

## Localization

- English
- Chinese  

---

## Download

Download the mod from the **Releases** page.  
Download the official biomepack from the **Releases** page.
