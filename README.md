  # CobbleDex REI/EMI/JEI

[![GitHub Release](https://img.shields.io/github/v/release/Akkiruk/cobblemon-spawning-rei?logo=github)](https://github.com/Akkiruk/cobblemon-spawning-rei/releases/latest)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/cobbledex-rei-emi-jei?logo=curseforge&color=F16436)](https://www.curseforge.com/minecraft/mc-mods/cobbledex-rei-emi-jei)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/cobbledex-rei-emi-jei?logo=modrinth&color=00AF5C)](https://modrinth.com/mod/cobbledex-rei-emi-jei)

[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.1-green.svg)](https://www.minecraft.net/)
[![Fabric API](https://img.shields.io/badge/Fabric%20API-0.116%2B-orange.svg)](https://fabricmc.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1%2B-red.svg)](https://neoforged.net/)
[![Cobblemon](https://img.shields.io/badge/Cobblemon-1.7.1%2B-blue.svg)](https://cobblemon.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Your viewer-native **Cobblemon in-game reference guide** inside **REI**, **JEI**, or **EMI**. CobbleDex turns your recipe viewer into a searchable knowledge layer for spawns, evolutions, drops, moves, forms, fossils, riding data, and pack-specific Cobblemon mechanics without becoming a general QoL or HUD mod.

> **Multi-Viewer Support:** CobbleDex has native integrations for [REI (Roughly Enough Items)](https://github.com/shedaniel/RoughlyEnoughItems), [JEI (Just Enough Items)](https://github.com/mezz/JustEnoughItems), and [EMI (Enough Mod Items)](https://github.com/emilyploszaj/emi). Install any one of them and CobbleDex automatically hooks into that viewer.

## Product Rules

- **Viewer-native reference first:** features belong in CobbleDex when they improve Cobblemon data collection, interpretation, search, or display through REI, JEI, and EMI.
- **Parity by default:** REI, JEI, and EMI should expose the same CobbleDex knowledge unless a viewer API makes exact parity impossible.
- **Player-truth data:** live server-synced or runtime data beats jar/datapack fallback, and bundled defaults are only a last resort.
- **Material forms only:** alternate forms are first-class only when they differ in player-relevant data such as spawns, stats, moves, drops, evolution, obtainment, riding, typing, or abilities. Texture-only variants should stay collapsed.
- **Export stays secondary:** `/cobbledex export` is a hidden diagnostic/planning tool, not the primary product surface.

## Features

### One mod, many Pokédex tabs
CobbleDex adds dedicated viewer pages for:

- **Spawn Data** - where a Pokemon can appear, including biome tags, resolved biome names, time, weather, light, height, structures, nearby blocks, bucket, context, rarity, and weight multipliers
- **Evolution Chains** - full family trees with branching lines, item icons, form changes, and detailed requirements
- **Special Obtainment** - custom per-Pokemon obtainment notes for altar summons, shrine methods, special encounters, legendary acquisition methods, and mod-gated methods such as LumyMon when that mod is present
- **Item Drops** - what each Pokemon drops, with drop chance and quantity ranges
- **Stats** - base stats, total stats, typing, and EV yields
- **Moves** - level-up moves, egg moves, tutor moves, TM compatibility, and TM reverse lookups
- **Pokedex Info** - abilities, hidden abilities, egg groups, gender ratio, catch rate, height, weight, and related species data
- **Pokedex Descriptions** - flavor text entries directly in the viewer
- **Fossils** - which fossil items create each Pokemon, plus reverse lookup from the fossil item itself
- **Type Chart** - offensive and defensive matchup data for each species
- **Natures** - full nature table with stat modifiers
- **CobbleCrew Jobs** - which jobs a Pokemon qualifies for when CobbleCrew is installed
- **Forms** - alternate forms, regional variants, Mega forms, Gigantamax, Primal, Ultra Burst, and modded form families when those forms have material player-facing differences
- **Riding Data** - mount type, riding style, seats, and riding stat ranges for rideable Pokemon

### Spawn data that is actually useful in play
- See the information players actually care about while hunting: biome, dimension, time window, weather, sky access, moon phase, light level, Y range, structures, and block conditions
- View anti-conditions and exclusions, not just positive requirements
- Biome tags resolve into real biome names, with hover details for raw IDs when you need datapack-level precision
- Weight multipliers and their triggers are shown so rare boosted spawns are understandable instead of hidden in JSON
- Includes data from Cobblemon itself, addon mods, server data, and datapacks, including ZIP datapacks

### Evolution pages built around full family trees
- Full chain view instead of isolated A to B fragments
- Branching families are easier to read, with inline requirements and item icons
- Regional forms and major form-change lines are included in the chain where relevant
- Alternate-form species pages can have their own stats, moves, type chart, evolution data, and other form-specific info

### Better search and navigation
- Search by species name directly from your recipe viewer sidebar
- Form entries can be searched by both their own name and their base species name
- Click Pokemon, forms, fossils, drop items, and TM-related entries to jump through related pages
- JEI, REI, and EMI all expose the same core information instead of one viewer being a second-class port

### Multiplayer-friendly client-side design
- Pure client-side mod: no server install required
- Works in singleplayer, LAN, and dedicated servers
- Uses synced data and local fallbacks so spawn, evolution, fossil, and related data still appear even when the server does not have CobbleDex installed
- Viewer reload handling keeps JEI and EMI up to date after server sync instead of leaving categories empty

### Hidden diagnostic export
- Includes `/cobbledex export`, which generates a multi-sheet `.xlsx` workbook in `cobbledex-export/`
- Export includes species overview, spawn data, evolutions, drops, movesets, level-up moves, special obtainment, fossils, abilities, type chart, and riding data
- Useful for debugging, pack documentation, balancing, guide writing, and theorycrafting outside the game, but the in-viewer pages remain the primary CobbleDex experience

### Extra quality-of-life details
- Pokemon are rendered as actual Cobblemon model items instead of plain text placeholders
- REI and JEI cheat mode can give a real Pokemon through `/pokegive` instead of just a cosmetic item stack
- Categories can be toggled in config if you only want part of the data set

## Compatibility

| Component | Supported Versions |
|-----------|-------------------|
| **Minecraft** | 1.21.1 |
| **Cobblemon** | 1.7.1+ |
| **Fabric** | |
| Fabric Loader | 0.15.0+ |
| Fabric API | 0.116.7+ |
| Fabric Language Kotlin | 1.13.4+ |
| **NeoForge** | |
| NeoForge | 21.1.77+ |
| Kotlin for Forge | 5.11.0+ |
| **Recipe Viewers** (choose one or more) | |
| REI (Roughly Enough Items) | 16.0.799+ |
| JEI (Just Enough Items) | 19.27.0.340+ |
| EMI (Enough Mod Items) | 1.1.12+ |

## Installation

### Requirements

**Fabric:** Minecraft 1.21.1, Fabric Loader 0.15.0+, Fabric API 0.116.7+, Fabric Language Kotlin 1.13.4+, Cobblemon 1.7.1+, and **ONE of:** REI, JEI, or EMI

**NeoForge:** Minecraft 1.21.1, NeoForge 21.1.77+, Kotlin for Forge 5.11.0+, Cobblemon 1.7.1+, and **ONE of:** REI, JEI, or EMI

### Client Installation

1. Download the appropriate version from [Modrinth](https://modrinth.com/mod/cobbledex-rei-emi-jei) or [CurseForge](https://www.curseforge.com/minecraft/mc-mods/cobbledex-rei-emi-jei)
   - `cobbledex-rei-emi-jei-fabric-X.Y.Z.jar` for Fabric
   - `cobbledex-rei-emi-jei-neoforge-X.Y.Z.jar` for NeoForge
2. Place in your `mods/` folder
3. Launch Minecraft

No server installation needed — this mod is pure client-side.

## Building from Source

```bash
git clone https://github.com/Akkiruk/cobblemon-spawning-rei.git
cd cobblemon-spawning-rei

# Build both loaders
./gradlew clean :fabric:build :neoforge:build
```

Output jars:
- `fabric/build/libs/cobbledex-rei-emi-jei-fabric-X.Y.Z.jar`
- `neoforge/build/libs/cobbledex-rei-emi-jei-neoforge-X.Y.Z.jar`

## License

MIT License — see [LICENSE](LICENSE) for details.

## Links

- [Modrinth](https://modrinth.com/mod/cobbledex-rei-emi-jei)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/cobbledex-rei-emi-jei)
- [GitHub Issues](https://github.com/Akkiruk/cobblemon-spawning-rei/issues)

## Acknowledgments

- [Cobblemon Team](https://cobblemon.com/) for the Pokémon mod
- [shedaniel](https://github.com/shedaniel) for REI (Roughly Enough Items)
- [mezz](https://github.com/mezz) for JEI (Just Enough Items)
- [emilyploszaj](https://github.com/emilyploszaj) for EMI (Enough Mod Items)
- [Architectury](https://github.com/architectury/architectury-api) for multiloader support
