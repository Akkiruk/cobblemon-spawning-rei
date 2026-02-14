  # CobbleDex REI/EMI/JEI

[![GitHub Release](https://img.shields.io/github/v/release/Akkiruk/cobblemon-spawning-rei?logo=github)](https://github.com/Akkiruk/cobblemon-spawning-rei/releases/latest)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/cobbledex-rei-emi-jei?logo=curseforge&color=F16436)](https://www.curseforge.com/minecraft/mc-mods/cobbledex-rei-emi-jei)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/cobbledex-rei-emi-jei?logo=modrinth&color=00AF5C)](https://modrinth.com/mod/cobbledex-rei-emi-jei)

[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.1-green.svg)](https://www.minecraft.net/)
[![Fabric API](https://img.shields.io/badge/Fabric%20API-0.116%2B-orange.svg)](https://fabricmc.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1%2B-red.svg)](https://neoforged.net/)
[![Cobblemon](https://img.shields.io/badge/Cobblemon-1.7.1%2B-blue.svg)](https://cobblemon.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Your complete **Cobblemon Pokédex** inside **REI**, **JEI**, or **EMI**. Browse spawn locations, evolution chains, base stats, item drops, Pokédex info, and special obtainment methods for every Pokémon — all from your recipe viewer.

> **✨ Multi-Viewer Support:** This mod works with [REI (Roughly Enough Items)](https://github.com/shedaniel/RoughlyEnoughItems), [JEI (Just Enough Items)](https://github.com/mezz/JustEnoughItems), AND [EMI (Enough Mod Items)](https://github.com/emilyploszaj/emi)! Install any one of them and the mod will automatically integrate.

## Features

### Spawn Locations
- Browse spawn conditions for every Pokémon in your modpack
- Biomes, time of day, weather, light level, Y-level, structures, and more
- Rarity tiers with color coding (Common / Uncommon / Rare / Ultra Rare)
- Weight multipliers and anti-conditions displayed
- Supports spawns from Cobblemon, datapacks, and other mods

### Evolution Chains
- Full evolution requirements pulled from Cobblemon's runtime API
- Level-up, item use, trade, friendship, time of day, biome, held item, and dozens more
- Form-specific evolutions (regional variants, gender-based, etc.)
- Branching evolutions shown with branch indicators

### Base Stats & Pokédex Info
- Type matchups, abilities, egg groups, gender ratios
- Height, weight, catch rate, EV yields
- Breeding info and training data

### Item Drops & Special Obtainment
- View what items each Pokémon drops
- Legendary/mythical obtainment via altars, shrines, and special methods
- LumyMon summoning altar and resurrection machine support

### Universal Recipe Viewer Integration
- **Works with REI, JEI, or EMI** — no need to choose, install your favorite!
- Pokémon rendered as 3D models using Cobblemon's `PokemonItem`
- Searchable by species name in your recipe viewer's search bar
- Click any Pokémon to view any of its data displays
- Native plugin for each viewer (not using compatibility layers)

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
