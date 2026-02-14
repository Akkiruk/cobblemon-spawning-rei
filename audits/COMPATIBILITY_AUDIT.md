# Cobblemon Spawning REI — Full Ecosystem Compatibility Audit

**Date:** 2026-02-13
**Mod Version:** 1.18.3
**Cobblemon Version:** 1.7.1+1.21.1
**Scope:** All potential compatibility risks with every mod in the Cobblemon ecosystem

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Architecture Risk Surface](#2-architecture-risk-surface)
3. [Installed Mod Compatibility Matrix](#3-installed-mod-compatibility-matrix)
4. [Broader Ecosystem Mod Compatibility](#4-broader-ecosystem-mod-compatibility)
5. [Reflection-Based Cobblemon API Access](#5-reflection-based-cobblemon-api-access)
6. [Spawn Data Loading Risks](#6-spawn-data-loading-risks)
7. [Evolution Data Loading Risks](#7-evolution-data-loading-risks)
8. [Recipe Viewer Integration Risks](#8-recipe-viewer-integration-risks)
9. [Rendering & Performance Risks](#9-rendering--performance-risks)
10. [Network Sync Risks](#10-network-sync-risks)
11. [Species Name Resolution Risks](#11-species-name-resolution-risks)
12. [Platform-Specific Risks (Fabric vs NeoForge)](#12-platform-specific-risks-fabric-vs-neoforge)
13. [Datapack Compatibility Risks](#13-datapack-compatibility-risks)
14. [Active Warnings in Current Build](#14-active-warnings-in-current-build)
15. [Risk Priority Matrix](#15-risk-priority-matrix)
16. [Recommendations](#16-recommendations)
17. [Feature Opportunity Audit — Untapped Data](#17-feature-opportunity-audit--untapped-data-in-the-cobblemon-ecosystem)
18. [Reflection Elimination Strategy](#18-reflection-elimination-strategy)

---

## 1. Executive Summary

CobblemonSpawningREI is a **recipe viewer integration mod** that displays Pokémon spawn conditions, evolution chains, and special obtainment methods in REI/JEI/EMI. It loads spawn data by **directly reading JSON files from mod JARs and datapacks** (bypassing Cobblemon's spawn API), and loads evolution data by **reflecting into Cobblemon's internal classes** at runtime.

### Top-Level Findings

| Severity | Count | Description |
|----------|-------|-------------|
| **CRITICAL** | 4 | Reflection on 30+ Cobblemon internal fields; Fakemon mod species bypass; NeoForge `ModFile` API fragility; Spawn JSON format assumptions |
| **HIGH** | 8 | Data load timing races; missing spawn condition fields; mutable cached ItemStacks; addon evolution types silently dropped; datapack world-level scanning gaps |
| **MEDIUM** | 12 | SpeciesNameNormalizer gaps; bundled LumyMon data staleness; REI registration blocking; network fingerprint determinism; anticondition field gaps |
| **LOW** | 9 | Cosmetic fallbacks; niche edge cases; unlikely race conditions |

**Current state:** The mod works correctly with the 30 Cobblemon ecosystem mods and 7 datapack mods installed in COBBLEVERSE. Zero ERROR-level log entries. Three recurring WARN-level messages about requirement extraction failures (time_range, advancement, biome). No installed mod currently adds custom species to `PokemonSpecies.implemented` (runtime = 1025 base species).

**Future risk:** The mod will break silently for any addon that adds custom Pokémon species via `PokemonSpecies.implemented`, uses non-standard spawn JSON formats, or adds new evolution requirement types.

---

## 2. Architecture Risk Surface

### How data flows through the mod

```
Mod JARs (data/*/spawn_pool_world/*.json)
    ↓ (filesystem walk + JSON parse via Gson)
SpawnDataLoader → SpawnDataIndex.spawnsBySpecies
    ↓
REI/JEI/EMI display generators ← SpawnDataIndex queries
    ↑
EvolutionDataLoader (Cobblemon runtime API + reflection) → SpawnDataIndex.evolutionsBySpecies
    ↑
ObtainmentDataLoader (custom data path + hardcoded LumyMon defaults) → SpawnDataIndex.obtainmentBySpecies
    ↑
PokemonItemCache (PokemonSpecies.getByName + PokemonItem.from) → ItemStack for rendering
```

### Critical integration points

| Integration Point | Method | Risk Level |
|---|---|---|
| **Spawn file discovery** | Filesystem walk on mod JAR root paths | HIGH — depends on platform loader reflection |
| **Spawn JSON parsing** | Custom Gson parser, expects `pokemon` as flat string | HIGH — addon mods may use different formats |
| **Evolution extraction** | Reflection on ~30 private Cobblemon fields | CRITICAL — any field rename breaks silently |
| **Species rendering** | `PokemonItem.from(species)` → `ItemStack` | MEDIUM — depends on Cobblemon's item API |
| **REI/JEI/EMI entry registration** | Iterates `SpawnDataIndex.allSpeciesNames` | MEDIUM — timing-dependent on data load |
| **Server-client sync** | Custom packet protocol with GZIP chunks | MEDIUM — no ACK, 30s timeout fallback |

---

## 3. Installed Mod Compatibility Matrix

### Cobblemon Ecosystem Mods (30 installed)

| # | Mod | Version | Interaction Type | Risk | Issues |
|---|---|---|---|---|---|
| 1 | **Cobblemon** | 1.7.1+1.21.1 | Core dependency | CRITICAL | 30+ reflected field names could change in any update |
| 2 | **REI** | 16.0.799 | Core dependency | LOW | Stable API; EntryType/Display/Category APIs well-defined |
| 3 | **LumyMon** | 0.5.5 | Hardcoded obtainment data | MEDIUM | Bundled altar/shrine item IDs could go stale if LumyMon updates |
| 4 | **LumyREI** | 1.1.1 | Parallel REI plugin | MEDIUM | Both mods register REI categories — potential category ID collision if LumyREI adds spawn/evolution displays |
| 5 | **Mega Showdown** | 1.5.1 | Adds mega evolution system | HIGH | Mega evolutions bypass `species.evolutions` — may not appear in evolution displays. Mega forms register as `FormData` with custom aspects, not as separate species |
| 6 | **ZaMega** | 1.4.6 | Additional mega support | HIGH | Same as Mega Showdown — mega form evolutions may be invisible to the evolution loader. ZaMega may add custom `EvolutionRequirement` types unknown to `parseRequirement()` |
| 7 | **Cobblemon Additions** | 4.1.6 | Adds items/features | LOW | No spawn/evolution data changes. Items added may be evolution items not recognized by `extractItemFromRequirement()` |
| 8 | **Cobblemon Spawn Alerts** | 1.11.4 | Reads spawn data | NONE | Read-only; no data modification; no interaction |
| 9 | **CobbleNav** | 2.2.5 | Pokemon navigation/radar | LOW | Reads Cobblemon spawn data via its own method. If CobbleNav caches spawn data differently, users may see inconsistent info vs SpawningREI |
| 10 | **CobbleCuisine** | 2.0.1 | Pokemon food/cooking | NONE | No spawn/evolution interaction |
| 11 | **CobbleDollars** | 2.0.0 | Currency system | NONE | No spawn/evolution interaction |
| 12 | **CobbleFurnies** | 1.0 | Pokemon furniture | NONE | No spawn/evolution interaction |
| 13 | **Cobbleverse Journey Mounts** | 1.7.2 | Pokemon riding | LOW | Adds riding forms — these register as `FormData` but won't affect spawn/evolution displays |
| 14 | **CobbleverseBadges** | 1.3 | Gym badges | NONE | No spawn/evolution interaction |
| 15 | **Cobbreeding** | 2.1.1 | Pokemon breeding | LOW | Breeding produces species already in registry. May add egg group data or breeding-specific requirements not shown in evolution display |
| 16 | **Fight or Flight** | 0.10.5 | Wild aggression | NONE | Modifies battle behavior, not spawn data |
| 17 | **MoreCobblemonTweaks** | 1.2.1 | QoL tweaks | LOW | May modify Cobblemon config values that affect spawn rates; these won't be reflected in REI displays since display shows file-based data, not runtime-modified rates |
| 18 | **Only Bottle Caps** | 1.3.0 | IV mechanics | NONE | Item-only mod, no spawn/evolution interaction |
| 19 | **PlayerXP** | 1.0.4 | Player XP system | NONE | No spawn/evolution interaction |
| 20 | **Capture XP** | 1.3.0 | Capture XP | NONE | No spawn/evolution interaction |
| 21 | **TimCore** | 1.28.1 | Shared library | LOW | If TimCore modifies `PokemonSpecies` behavior at runtime (e.g., loot table hooks), could affect species iteration timing |
| 22 | **SafePastures** | 1.1.1 | Pasture system | NONE | No spawn/evolution interaction |
| 23 | **Radical Cobblemon Trainers API** | 0.14.5-beta | NPC trainer API | NONE | Trainers use existing species; no new species added |
| 24 | **Radical Cobblemon Trainers** | 0.17.4-beta | NPC trainers | NONE | Same as above |
| 25 | **Pokeblocks** | 1.4.0 | Pokemon blocks | NONE | Decorative; no spawn/evolution interaction |
| 26 | **TMCraft** | 1.4.18 | TM/move crafting | LOW | Adds move-related items. `MoveSetRequirement` and `MoveTypeRequirement` in evolutions reference moves that TMCraft may modify — display should still work |
| 27 | **PastureLoot** | 1.0.5 | Pasture loot | NONE | No spawn/evolution interaction |
| 28 | **Lootrmon** | 0.0.0.1 | Lootr+Cobblemon compat | NONE | Loot table integration only |
| 29 | **CatchRate Display** | 2.5.7 | Catch rate HUD | NONE | Separate HUD mod; no REI/spawn interaction |
| 30 | **Cobblemon Spawning REI** | 1.18.3 | (This mod) | — | — |

### Non-Cobblemon Mods with Potential Interaction

| # | Mod | Risk | Concern |
|---|---|---|---|
| 1 | **Global Packs** | MEDIUM | Force-loads datapacks globally. If a global datapack modifies `spawn_pool_world` files, SpawningREI must discover these files. Global Packs places files in a non-standard location that may not be scanned by the `localDatapackScan`. |
| 2 | **Respackopts** | MEDIUM | Configurable resource/datapacks — can conditionally enable/disable spawn data files. If a spawn file is disabled via Respackopts but still present on disk, SpawningREI may still load and display it. |
| 3 | **Biome Replacer** | LOW | Replaces biome IDs — spawns tagged with original biome IDs in REI may not match the actual in-game biome after replacement, causing misleading spawn location info. |
| 4 | **Repurposed Structures** | LOW | Adds modded structures. Spawn conditions referencing these structure tags will display raw tag IDs (e.g., `repurposed_structures:outpost_badlands`) instead of human-readable names. |
| 5 | **Sodium / ImmediatelyFast / Entity Culling** | LOW | Rendering optimizations could theoretically affect `PokemonItem` rendering in REI slots. Unlikely but documented cases of Sodium interfering with custom item rendering. |
| 6 | **ModernFix** | MEDIUM | Aggressively optimizes class loading and resource management. Could affect: (a) mod JAR root path resolution timing, (b) `PokemonSpecies.implemented` availability during early init, (c) GeckoLib model caching used by PokemonItem. |
| 7 | **Jade** | LOW | Tooltip mod — registers item tooltips that could conflict with custom `PokemonEntry` tooltips in REI if both try to apply to the same ItemStack. |
| 8 | **GeckoLib** | LOW | Animation library used by Cobblemon for 3D models. If GeckoLib's rendering pipeline changes, `PokemonItem.from()` rendering could break. SpawningREI doesn't call GeckoLib directly. |

---

## 4. Broader Ecosystem Mod Compatibility

These mods are NOT installed but are popular in the Cobblemon ecosystem and could be added by users. Each one has specific compatibility concerns.

### Fakemon / Custom Species Mods (CRITICAL RISK CATEGORY)

These mods add **new Pokémon species** to `PokemonSpecies.implemented`, which is the single biggest compatibility risk.

| Mod | Downloads | Risk | Issue |
|---|---|---|---|
| **Cobblemon Alatia's Fakemon Pack** | 148K | CRITICAL | Adds custom species via Cobblemon's species registry. These species have their own spawn files in `data/alatia/spawn_pool_world/`. SpawningREI's mod root path discovery will attempt to walk these paths, but **SpeciesNameNormalizer has no mappings for Fakemon names**. If a Fakemon name contains special characters (hyphens, apostrophes, spaces), normalization may prevent spawn data from linking to the species entry. Additionally, Fakemon-specific evolution types or requirements will silently fail in `parseRequirement()`. |
| **Cobblemon Eldoria's Fakemon** | 4.8K | CRITICAL | Same as above — custom species in a different namespace (`eldoria/`). |
| **Cobblemon Fakemon: Lively 'Mons** | 503 | CRITICAL | Same — 66 custom Fakemon with custom spawns and animations. |
| **Cobblemon Customizamons** | 13.7K | HIGH | Adds custom forms to existing species. The evolution loader iterates `species.forms[].evolutions` which would pick up new forms, but the form aspect naming must match SpawningREI's form variant detection. |
| **Cobblemon Alpha Pokemon** | 3.6K | MEDIUM | Adds "alpha" variants as aspects. These appear in spawn files with `aspects: ["alpha"]`. If the spawn JSON includes alpha variants, they'll be parsed as the base species (alpha aspect stripped by species name extraction). Could show confusing spawn data mixing alpha and normal conditions. |

### Legendary Obtainment Mods (HIGH RISK CATEGORY)

These mods add alternative ways to obtain legendary Pokémon, which could conflict with the hardcoded LumyMon obtainment data or add their own spawn files.

| Mod | Downloads | Risk | Issue |
|---|---|---|---|
| **Myths and Legends** | 1.8M | HIGH | Adds legendary encounter structures and spawn conditions. Includes custom `spawn_pool_world` entries that SpawningREI will load — but these spawns use custom structure tags and condition types. If Myths and Legends uses non-standard spawn JSON fields (e.g., custom `context` types beyond grounded/submerged/surface/seafloor/fishing), they'll display with raw titleCase fallback text. The hardcoded LumyMon obtainment data for overlapping legendaries (Articuno, Zapdos, Moltres, etc.) would suppress Myths and Legends' datapack obtainment for any species that already has bundled obtainment. |
| **Cobblemon Legendary Monuments** | 710K | HIGH | Adds monument structures for legendary spawning. Same spawn/obtainment overlay concerns as Myths and Legends. If both LumyMon and Legendary Monuments provide obtainment for the same species, only one source's data will show. |
| **Cobblemon Orb Summons** | 30.8K | MEDIUM | Adds summoning orbs for specific Pokémon. If this mod provides obtainment data via the `special_obtainment` datapack path, it'll be picked up. If not (likely — this is a custom data path invented by SpawningREI), the orb summon method won't appear in REI at all. |
| **CobbledGacha** | 282K | LOW | Gacha machines drop Pokémon. No spawn or evolution interaction, but users might expect gacha obtainment to appear in the mod's displays. |

### Spawn Modification Mods (HIGH RISK CATEGORY)

These mods modify how or where Pokémon spawn, potentially diverging from the file-based data SpawningREI reads.

| Mod | Downloads | Risk | Issue |
|---|---|---|---|
| **Cobblemon: Cobbled Levels** | 47.7K | HIGH | Dynamically scales wild Pokémon levels based on distance, progression, etc. SpawningREI displays level ranges from spawn JSON files, which won't reflect runtime level scaling. Users will see "Level 5-15" in REI but encounter Level 50+ Pokémon in-game. **Fundamentally misleading but not a crash risk.** |
| **CobbleWeather** | 5.2K | MEDIUM | Grants better IVs based on weather. Doesn't modify spawn locations but changes spawn quality. No direct data conflict, but if it adds weather-specific spawn conditions, they may display incorrectly. |
| **MoreCobblemonTweaks** | (installed) | LOW | Adjusts spawn rates/behavior at runtime. File-based spawn weights in REI won't match runtime-adjusted rates. |
| **Fight or Flight** | (installed) | NONE | Aggression mechanics only; doesn't change spawn data. |

### Evolution Modification Mods (HIGH RISK)

| Mod | Downloads | Risk | Issue |
|---|---|---|---|
| **Mega Showdown** | (installed) | HIGH | Mega evolution is a **battle-time transformation**, not a persistent evolution in Cobblemon's `species.evolutions` list. The evolution loader won't find mega evolution data because it's registered in a separate system (likely custom `Evolution` subclasses or a parallel registry). Users searching for "how to mega evolve Charizard" in REI will find nothing. |
| **ZaMega** | (installed) | HIGH | Same problem as Mega Showdown. ZaMega may register evolutions differently — possibly via custom `EvolutionRequirement` subclasses that `parseRequirement()` can't extract data from (falls to generic `else` branch with `extractField(req, "amount")` returning null). |
| **Cobblemon Fableworks** | 21.9K | MEDIUM | Add-on with custom forms and potentially custom evolution conditions. If it uses standard Cobblemon evolution API, data will be extracted. If it uses a custom system, evolutions will be invisible. |
| **TMCraft** | (installed) | LOW | Adds TM items that teach moves. Move-related evolution requirements (`MoveSetRequirement`) reference moves that TMCraft makes accessible. Display is unaffected. |

### Data Display / Wiki Mods (MEDIUM RISK — Information Overlap)

| Mod | Downloads | Risk | Issue |
|---|---|---|---|
| **Cobblepedia** | 5M | MEDIUM | In-game Pokédex wiki. Shows spawn and evolution info from its own data source. If Cobblepedia and SpawningREI show different spawn locations for the same Pokémon (because they load data from different sources or different timing), users get confused. Not a crash risk. |
| **CobbleStats** | 157K | LOW | Showdown-like stat display. No data overlap with spawn/evolution displays. |
| **Cobblemon MonTracker** | 19.8K | NONE | Notification system only. |
| **Cobblemon Cobble It** | 179K | NONE | Type display on hover. No data conflict. |

### Battle/Combat Mods (LOW RISK)

| Mod | Downloads | Risk | Issue |
|---|---|---|---|
| **Cobblemon Battle Extras** | (installed) | NONE | Battle mechanics only |
| **CobblePass** | 18.5K | NONE | Battle pass system — no spawn interaction |
| **CobGyms** | 617K | NONE | Instanced gym battles — no spawn interaction |

### Item/Economy Mods (LOW RISK)

| Mod | Downloads | Risk | Issue |
|---|---|---|---|
| **Cobblemon Unimplemented Items** | 437K | LOW | Adds items Cobblemon hasn't implemented yet. If these items are evolution items (held items for trade evolution, etc.), `extractItemFromRequirement()` may fail to resolve them from Cobblemon's registry since they're added by a third party. The requirement would show the raw item ID instead of a proper name. |
| **CobbleDollars** | (installed) | NONE | Currency only |
| **CobbleCapsule** | 23.5K | NONE | Capsule loot system — no spawn interaction |
| **Cobblemon Drop Loot Tables** | 533K | NONE | Loot table customization — no spawn interaction |
| **Way Too Many Ingredients** | 103K | NONE | Food/sandwich mod — no spawn interaction |

### Breeding Mods (LOW RISK)

| Mod | Downloads | Risk | Issue |
|---|---|---|---|
| **Cobbreeding** | (installed) | LOW | Users may want to see breeding info in REI alongside spawn/evolution data. Cobbreeding data is not shown — potential feature gap, but not a compatibility issue. |

### Rider/Mount Mods

| Mod | Downloads | Risk | Issue |
|---|---|---|---|
| **Cobbleverse Journey Mounts** | (installed) | NONE | Riding system only |
| **PokeBike** | 11.3K | NONE | Bike items, no Pokémon data modification |

---

## 5. Reflection-Based Cobblemon API Access

This is the **highest-risk area** of the entire codebase. The evolution data loader uses Java reflection to access ~30 private fields in Cobblemon's internal classes.

### All Reflected Field Names

| Field | Class | Purpose | Break Risk |
|---|---|---|---|
| `requiredContext` | `ContextEvolution` subclasses | Evolution trigger item/block | CRITICAL — private field |
| `minLevel` | `LevelRequirement` | Level threshold | HIGH |
| `amount` | `FriendshipRequirement` + others | Numeric threshold | HIGH |
| `range` | `TimeRangeRequirement` | Time of day range | HIGH — **currently broken** (see warnings) |
| `type` | `MoveTypeRequirement` | Required move type | HIGH |
| `move` | `MoveSetRequirement` | Required known move | HIGH |
| `biomeCondition` | Biome requirements | Biome whitelist | HIGH — **currently broken** |
| `biomeAnticondition` | Biome requirements | Biome blacklist | HIGH |
| `structureCondition` | Structure requirements | Structure whitelist | HIGH |
| `structureAnticondition` | Structure requirements | Structure blacklist | HIGH |
| `highStat` / `lowStat` | `StatCompareRequirement` | Stat comparison | HIGH |
| `statOne` / `statTwo` | `StatEqualRequirement` | Stat equality check | HIGH |
| `target` | `PokemonPropertiesRequirement` | Target properties | HIGH |
| `feature` | `PropertyRangeRequirement` | Feature range | HIGH |
| `moonPhase` | `MoonPhaseRequirement` | Moon phase | HIGH |
| `isRaining` | `WeatherRequirement` | Weather condition | HIGH |
| `requiredAdvancement` | `PlayerHasAdvancementRequirement` | Advancement gate | HIGH — **currently broken** |
| `identifier` | `WorldRequirement` | Dimension/world | HIGH |
| `ratio` | `AttackDefenceRatioRequirement` | Stat ratio | HIGH |
| `gender` | `GenderRequirement` | Gender requirement | HIGH |
| `nature` | `NatureRequirement` | Nature requirement | HIGH |
| `maxLevel` | `MaxPokemonLevelRequirement` | Max level cap | HIGH |
| `item` / `items` / `itemCondition` | Various | Held/interaction items | HIGH |
| `contains` | `PartyMemberRequirement` | Party check target | HIGH |

### Why this is critical

1. **No compile-time safety** — field names are strings. If Cobblemon renames `requiredContext` to `context` in a refactor, the reflection silently returns `null` and evolution data is lost.
2. **`field.isAccessible = true`** bypasses Java access controls — future Java module enforcement (`--illegal-access=deny`, Java 17+) could block all reflection access entirely.
3. **Three fields are already broken** in the current Cobblemon 1.7.1 build: `range` (TimeRangeRequirement), advancement, and biome condition fields log warnings on every load.
4. **Addon mods** (Mega Showdown, ZaMega, Fableworks) may add custom `EvolutionRequirement` subclasses with fields not in the `parseRequirement()` switch statement — these fall to the generic `else` branch which only tries `extractField(req, "amount")`, losing all semantic data.

---

## 6. Spawn Data Loading Risks

### 6.1 Mod Root Path Discovery

**How it works:** Uses reflection to call platform-specific APIs:
- **Fabric:** `FabricLoader.getInstance().getAllMods()` → `mod.getRootPaths()`
- **NeoForge:** `ModList.get().getModFiles()` → `modFile.findResource("data")`

**Risk: HIGH (NeoForge)**
- NeoForge's `ModFile` API has changed signatures between versions. The `findResource(Array<String>)` call assumes a specific method signature that may not exist in future NeoForge builds.
- If reflection fails on NeoForge, **zero spawn data is loaded from any mod JAR** — the entire spawn display is empty.

**Risk: MEDIUM (Fabric)**
- `FabricLoader` API is stable-ish but `getRootPaths()` is internal. If Fabric Loader changes to a new path resolution API, discovery silently fails.

### 6.2 Spawn JSON Format Assumptions

**Risk: HIGH for addon mods**

The parser assumes the `pokemon` field in spawn JSON is a **flat string** (e.g., `"pokemon": "pikachu"`). Some addon mods use an object format:

```json
// Standard (works):
"pokemon": "pikachu"

// Alternative format (BREAKS — entire file skipped):
"pokemon": { "species": "pikachu", "aspects": ["shiny"] }
```

When `spawn.get("pokemon")?.asString` encounters a `JsonObject`, Gson throws `IllegalStateException`. The outer `try/catch` catches this and silently skips the entire spawn file. Users see no spawn data for that species with no indication of why.

### 6.3 Species Name Extraction

**Risk: MEDIUM**

`pokemonField.split(" ").first().lowercase()` extracts the species name from the pokemon field. This handles Cobblemon's format (`"pikachu"` or `"pikachu shiny"`) but breaks for:

- Namespaced species: `"mymod:fakemon"` → key becomes `"mymod:fakemon"` which won't match normalized species names (normalizer strips `:`)
- Multi-word species: `"great tusk"` in spawn files → `"great"` only, never matches `"greattusk"`

### 6.4 Spawn Condition Fields Not Parsed

These Cobblemon spawn condition fields exist but are **not read** by SpawnDataLoader:

| Missing Field | Impact |
|---|---|
| `requiredLabel` | Spawn label requirements invisible to users |
| `nearbyPlayerLimit` | Player proximity limits not shown |
| `minWidth` / `minHeight` | Physical space requirements not shown |
| `preventSleepEntities` | Sleep entity limits not shown |
| `isSubmergedInWater` (conditions) | Submerged-in-water condition not parsed from conditions |
| Custom fields from addon mods | Silently ignored (harmless but data lost) |

### 6.5 Anticondition Gaps

`SpawnAntiCondition` only captures: `biomes`, `structures`, `neededBaseBlocks`, `neededNearbyBlocks`, `minY`, `maxY`

**Missing anticondition fields:**
- `timeRange` — time-based exclusions not shown
- `dimensions` — dimension exclusions not shown
- `weather` — weather-based exclusions not shown
- `light` — light level exclusions not shown
- `moonPhase` — moon phase exclusions not shown

This means a spawn might show "Spawns in Plains" without showing "except at night during rain."

---

## 7. Evolution Data Loading Risks

### 7.1 Unknown Evolution Types (Addon Mods)

The evolution loader recognizes these types explicitly:
- `TradeEvolution` → extracts `requiredContext`
- `ItemInteractionEvolution` → extracts `requiredContext`
- `BlockClickEvolution` → extracts `requiredContext`
- `ContextEvolution<*, *>` → generic fallback via reflection

**What breaks:** Any evolution type added by addon mods (Mega Showdown, ZaMega, Fableworks) that is NOT a subclass of `ContextEvolution` will get `null` for `requiredContext`. The evolution will appear in the display but with no useful information about how to trigger it.

### 7.2 Unknown Requirement Types

`parseRequirement()` uses `className.contains()` matching to dispatch to field extractors. Recognized requirement types:

`Level`, `Friendship`, `TimeRange`, `HeldItem`, `MoveType`, `MoveSet`, `Biome`, `Structure`, `StatCompare`, `StatEqual`, `PokemonProperties`, `PropertyRange`, `BlocksTraveled`, `UseMove`, `Defeat`, `Recoil`, `DamageTaken`, `BattleCriticalHits`, `PartyMember`, `MoonPhase`, `Weather`, `Advancement`, `World`, `AttackDefenceRatio`, `Gender`, `Nature`, `MaxPokemonLevel`, `WalkingSteps`, `DamageDealt`

**Any requirement type not in this list** (from addon mods or future Cobblemon versions) falls to: `extractField(req, "amount")`. Most custom requirements will return `null` for `amount`, resulting in an empty requirement line in the display.

### 7.3 Mega Evolution Gap

Neither `Species.evolutions` nor `Species.forms[].evolutions` include mega evolution data, because:
- Mega evolution in Cobblemon is a **battle transformation**, not a persistent evolution
- The mega evolution system (Mega Showdown, ZaMega) likely registers transformations in a parallel system
- **Result:** Users cannot find mega evolution info in REI. This is a significant feature gap for modpacks using mega evolution mods.

---

## 8. Recipe Viewer Integration Risks

### 8.1 REI

| Risk | Detail |
|---|---|
| **DataVersion cache invalidation** | Display generators cache generated displays, keyed by `SpawnDataIndex.dataVersion`. If the data version doesn't increment when it should (e.g., datapack hot-reload without triggering a full re-index), stale displays persist until REI is reloaded. |
| **`ensureLoaded()` blocks registration thread** | If spawn data loading is slow (400+ datapack files, slow disk), REI's `registerEntries` callback is blocked. No timeout or async fallback — could cause perceived freeze during REI initialization on HDDs. |
| **Category display size** | Large spawn datasets (e.g., 50+ spawn conditions for a single Pokémon) create very tall display panels. REI may clip or require excessive scrolling. No pagination within a single display. |

### 8.2 JEI

| Risk | Detail |
|---|---|
| **ClassNotFoundException guard** | JEI plugin classes import `mezz.jei.api.*` directly. These are only loaded when JEI is present via `@JeiPlugin` discovery. Safe — but if someone force-loads the class without JEI, immediate crash. |
| **Custom ingredient type** | `PokemonIngredient` + `PokemonIngredientType` register a custom JEI ingredient type. If JEI changes its ingredient API (major version bump), this breaks. JEI 19.x API is stable. |
| **No JEI installed in COBBLEVERSE** | Currently untested in this modpack. If a user switches from REI to JEI, the JEI integration should work but hasn't been validated against the COBBLEVERSE data volume (4817 spawn entries). |

### 8.3 EMI

| Risk | Detail |
|---|---|
| **REI self-disables when EMI detected** | `Class.forName("dev.emi.emi.api.EmiPlugin")` check causes REI plugin to skip all registration when EMI is present. This is correct behavior, but if the EMI plugin has a bug or missing feature, users have no REI fallback. |
| **EMI recipe display format differs** | EMI uses recipe-based rendering vs REI's display-based rendering. Layout differences may cause visual inconsistencies between REI and EMI views of the same data. |
| **No EMI installed in COBBLEVERSE** | EMI integration is untested in this modpack. |

### 8.4 LumyREI Interaction

LumyREI (v1.1.1) is a separate REI plugin for LumyMon items. Both mod register categories in the same REI instance.

| Risk | Detail |
|---|---|
| **Category ID collision** | SpawningREI uses `cobblemon-spawning-rei:spawns`, `:evolution`, `:obtainment`. If LumyREI uses similar category IDs or registers its own spawn/evolution categories, REI may show duplicate or conflicting entries. Unlikely based on current LumyREI scope (item recipes only). |
| **Entry type conflict** | If LumyREI registers its own custom Pokémon entry type, REI could have two competing entry types for Pokémon. This would cause entry registration failures or display routing errors. |

---

## 9. Rendering & Performance Risks

### 9.1 PokemonItemCache Mutable ItemStack

**Risk: MEDIUM**

`PokemonItemCache` caches `ItemStack` instances returned by `PokemonItem.from()`. `ItemStack` is **mutable** — if any consumer (REI display renderer, tooltip handler, Jade, etc.) mutates the cached stack (changing count, adding NBT), the corrupted stack is returned to all future consumers.

**Fix needed:** Return `itemStack.copy()` instead of the cached reference.

### 9.2 Species Resolution Performance

`resolveSpecies()` tries up to 5 name formats per cache miss:
1. Exact name
2. CamelCase
3. Lowercase
4. Stripped special chars
5. Various combinations

For 1025 species × 5 attempts = 5125 `PokemonSpecies.getByName()` calls on first load. If `getByName()` is O(n) (full registry scan), this is O(n²) total — potentially slow for large registries.

### 9.3 Rendering Volume

- **4817 spawn entries** across 1017 species
- **521 species** with evolution data
- **24 species** with obtainment data

For REI's entry list: ~1025 PokemonEntry entries + 3 categories with dynamic display generators. Performance is acceptable but could degrade with Fakemon mods adding hundreds more species.

### 9.4 Sodium / ImmediatelyFast Compatibility

The mod renders `PokemonItem` ItemStacks via `graphics.renderItem()`. These are standard Minecraft item rendering calls. Sodium doesn't modify item rendering. ImmediatelyFast batches draw calls but doesn't skip them. **Low risk.**

---

## 10. Network Sync Risks

### 10.1 Packet Protocol

| Parameter | Value |
|---|---|
| Chunk size | 32KB |
| Max packet | 64KB cap on read |
| Decompression limit | 50MB |
| Timeout | 30 seconds |
| Retry count | 3 with 1s backoff |
| Ordering | Sequential, 1 chunk/tick |

### 10.2 No ACK Protocol

The server sends chunks blindly without client acknowledgment. If a chunk silently drops:
- Client waits for missing chunk index
- 30-second timeout fires
- Falls back to local data

In practice, Minecraft runs over TCP so packet loss is retried at the transport layer. But: proxy servers (Velocity, BungeeCord), packet throttling mods, or anti-cheat plugins could discard custom packets. The 30s fallback handles this gracefully.

### 10.3 Fingerprint Determinism

The fingerprint is an MD5 hash of serialized JSON. If GSON serializes fields in a different order between server and client (different Java versions, different GSON builds), the fingerprint will always differ, forcing a full resync on every join. Wasteful but not harmful.

### 10.4 Large Modpack Payloads

For COBBLEVERSE with 4817 spawn entries + 521 evolutions + 24 obtainments:
- Serialized JSON: ~2-3MB
- GZIP compressed: ~200-400KB
- Chunks: 6-12 × 32KB packets
- Transfer time: 6-12 ticks (0.3-0.6 seconds) + 5s initial delay = ~6 seconds

For a Fakemon-heavy modpack with 10,000+ spawns, this could grow to 30+ chunks and 30+ seconds of transfer — approaching the timeout limit. The 30s timeout should be configurable for large modpacks.

---

## 11. Species Name Resolution Risks

### 11.1 SpeciesNameNormalizer Coverage

The normalizer has **41 special name mappings** covering Gen 1-9 edge cases (see Section 5). Missing coverage:

| Gap | Impact |
|---|---|
| **Gen 10+ Pokémon** (future) | New Pokémon with special characters won't have mappings. Fallback stripping (`[^a-z0-9_]`) should handle most cases but could create false collisions. |
| **Fakemon names** (addon mods) | No mappings exist. If a Fakemon name contains hyphens, spaces, or apostrophes (e.g., `"rock-drake"`, `"mist'ral"`), the normalized form may not match what `PokemonSpecies.getByName()` expects. |
| **Namespaced species** | Spawn files might reference species as `"mymod:species"`. The normalizer strips `:` → `"mymodspecies"`. `PokemonSpecies.getByName("mymodspecies")` won't find it. **All Fakemon from namespace-aware addon mods will show no spawn data.** |

### 11.2 Display Name Accuracy

`SpeciesNameNormalizer.toDisplayName()` converts normalized keys back to display names. For unmapped names, it just calls `titleCase()`. The result for multi-word species: `"greattusk"` → `"Greattusk"` (wrong — should be "Great Tusk"). This is cosmetic only.

---

## 12. Platform-Specific Risks (Fabric vs NeoForge)

### 12.1 Parity Status

| Feature | Fabric | NeoForge | Parity |
|---|---|---|---|
| Spawn data loading | Via FabricLoader API | Via ModList reflection | **Different code paths** |
| Network payload registration | PayloadTypeRegistry | RegisterPayloadHandlersEvent | OK |
| Client tick | ClientTickEvents | ClientTickEvent.Post | OK |
| Client commands | ClientCommandRegistrationCallback | RegisterClientCommandsEvent | OK |
| Disconnect cleanup | ClientPlayConnectionEvents | ClientPlayerNetworkEvent | OK |
| Server lifecycle | ServerLifecycleEvents | ServerStartedEvent | OK |
| REI plugin discovery | fabric.mod.json entrypoint | @REIPluginClient annotation | OK |
| JEI plugin discovery | fabric.mod.json entrypoint | @JeiPlugin annotation | OK |
| EMI plugin discovery | fabric.mod.json entrypoint | @EmiEntrypoint annotation | OK |

### 12.2 NeoForge Payload Versioning

The NeoForge payload handler uses version string `"1"` with `.optional()`. If the payload format changes in a future mod version, old clients can't decode new server payloads and vice versa. No version negotiation mechanism exists.

### 12.3 NeoForge ModFile API Fragility

The reflection-based `ModList.get().getModFiles()` path through NeoForge's API is the **most fragile integration point**. NeoForge has historically changed `ModFile` and related APIs between major versions. A NeoForge update that restructures `IModFileInfo` or `ModFile` would cause a `NoSuchMethodException` in the reflection call, resulting in zero spawn data loaded on NeoForge. Fabric's equivalent (`FabricLoader.getAllMods()`) is more stable.

---

## 13. Datapack Compatibility Risks

### 13.1 Scanning Scope

| Source | Scanned? | Notes |
|---|---|---|
| Mod JARs (`data/*/spawn_pool_world/`) | YES | Via mod root path discovery |
| Client datapacks (`<gameDir>/datapacks/`) | YES | If `localDatapackScan: true` (currently enabled) |
| ZIP datapacks in `datapacks/` | YES | Opens ZIPs and walks entries |
| World-level datapacks (`saves/<world>/datapacks/`) | PARTIAL | Only on dedicated server via `ServerDataManager` |
| Nested datapacks (`datapacks/extra/`) | UNKNOWN | The scanner resolves `datapacks/` but may not recurse into subdirectories for ZIPs. **The COBBLEVERSE Johto/Hoenn/Sinnoh datapacks in `datapacks/extra/` may not be scanned.** |
| Global Packs (via GlobalPacks mod) | UNKNOWN | Global Packs places datapacks in a non-standard global location. SpawningREI may not scan this path. |
| Respackopts-disabled datapacks | POSSIBLY | Files still exist on disk — may be loaded even when conditionally disabled by Respackopts |

### 13.2 COBBLEVERSE Datapack Analysis

| Datapack | Files | Entries | Scanned? |
|---|---|---|---|
| COBBLEVERSE-DP-v17-CF.zip | 403 | 913 | YES (confirmed in diagnostic — contributes 403 files, 913 entries) |
| COBBLEVERSE-Hoenn-DP.zip (extra/) | Unknown | Unknown | UNVERIFIED — may not be found if scanner doesn't check `extra/` subdirectory |
| COBBLEVERSE-Johto-DP.zip (extra/) | Unknown | Unknown | UNVERIFIED |
| COBBLEVERSE-Sinnoh-DP.zip (extra/) | Unknown | Unknown | UNVERIFIED |

### 13.3 Datapack Spawn Override Semantics

When multiple datapacks provide spawn data for the same species:
- SpawningREI **merges all entries** — the display shows ALL spawn conditions from ALL sources
- If a datapack disables a spawn (`"enabled": false`), SpawningREI correctly skips it
- If a datapack adds a species with different spawn weights, the display shows both the base and override weights — potentially confusing users who expect overrides to replace base data

### 13.4 Hot Reload

There is no filesystem watcher. If a datapack is added or modified while the game is running:
- Client: `/spawningrei reload` command triggers a full re-index
- Server: Data is reloaded on server restart or via the reload command
- **Gap:** No automatic detection of datapack changes

---

## 14. Active Warnings in Current Build

These warnings appear on every data load cycle (captured from `spawningrei-debug/`):

| Warning | Source | Impact | Root Cause |
|---|---|---|---|
| `No data extracted for time_range requirement (TimeRangeRequirement)` | EvolutionDataLoader reflection | Evolution display missing time-of-day requirements | Cobblemon 1.7.1 likely changed the `range` field name or type in `TimeRangeRequirement` |
| `No data extracted for advancement requirement (AdvancementRequirement)` | EvolutionDataLoader reflection | Evolution display missing advancement gate info | `requiredAdvancement` field not found — likely renamed or restructured |
| `No data extracted for biome requirement (BiomeRequirement)` | EvolutionDataLoader reflection | Evolution display missing biome requirements | `biomeCondition` field not found — likely renamed |

**Impact:** 3 categories of evolution requirements are **silently producing empty data**. Users see "Biome: ???" or empty requirement lines for evolutions gated by time, advancement, or biome.

---

## 15. Risk Priority Matrix

### CRITICAL (Requires code changes to prevent breakage)

| ID | Risk | Impact | Affected Mods |
|---|---|---|---|
| C1 | 30+ reflected field names on Cobblemon internals, 3 already broken | Evolution data silently incomplete/wrong | All — any Cobblemon update |
| C2 | Fakemon mods with namespaced species (`mymod:species`) → normalizer strips `:` → spawn data never links | Zero spawn data for all Fakemon species | Alatia's Fakemon, Eldoria's Fakemon, Lively 'Mons, any Fakemon mod |
| C3 | Spawn JSON `pokemon` field assumed to be string, not object | Entire spawn files silently skipped if format differs | Any addon mod using object-format pokemon field |
| C4 | NeoForge `ModFile.findResource()` reflection could break on NeoForge updates | Zero spawn data loaded on NeoForge | All NeoForge users |

### HIGH (Significant data loss or misleading info)

| ID | Risk | Impact | Affected Mods |
|---|---|---|---|
| H1 | Mega evolution data not in `species.evolutions` | Mega evolutions invisible in REI | Mega Showdown, ZaMega |
| H2 | Custom `EvolutionRequirement` subclasses fall to generic handler | Requirement info lost for custom evolution types | Mega Showdown, ZaMega, Fableworks, any evolution addon |
| H3 | `cobbled_levels` runtime level scaling not reflected in REI display | Level ranges shown in REI don't match in-game reality | Cobbled Levels |
| H4 | Data load timing — `PokemonSpecies.implemented` may be empty during early init | Partial load state persists if retry never fires | ModernFix (delays class loading), large modpacks |
| H5 | Anticondition fields incomplete (missing timeRange, dimensions, weather, light, moonPhase) | Users see incomplete spawn restriction info | All |
| H6 | Mutable `ItemStack` cached in `PokemonItemCache` | Cache corruption if any consumer mutates the stack | REI, Jade, any tooltip mod |
| H7 | Regional datapacks in `datapacks/extra/` may not be scanned | Missing spawn data from region-specific datapacks | COBBLEVERSE Johto/Hoenn/Sinnoh datapacks |
| H8 | Myths & Legends / Legendary Monuments custom legendary spawns may conflict with bundled LumyMon obtainment | Incomplete or conflicting legendary obtainment data | Myths & Legends, Legendary Monuments |

### MEDIUM (Edge cases, cosmetic issues, or performance concerns)

| ID | Risk | Impact | Affected Mods |
|---|---|---|---|
| M1 | SpeciesNameNormalizer missing Gen 10+ and Fakemon mappings | Wrong display names for unmapped Pokémon | Future Cobblemon updates, Fakemon mods |
| M2 | Bundled LumyMon obtainment data hardcoded — goes stale on LumyMon updates | Wrong altar/shrine item names displayed | LumyMon |
| M3 | `ensureLoaded()` blocks REI registration synchronously | Slow startup on HDDs with large modpacks | Large modpacks |
| M4 | Network fingerprint depends on GSON serialization order | Unnecessary full resyncs on every server join | Cross-version server/client pairs |
| M5 | No ACK protocol for server→client sync | 30s stall before fallback on silent packet loss | Proxy servers (Velocity, BungeeCord) |
| M6 | 10-second lock timeout on `loadAll()` | Data load silently dropped on slow systems | HDD users, very large modpacks |
| M7 | Global Packs mod datapack location may not be scanned | Missing spawn data from globally-loaded datapacks | Global Packs |
| M8 | Biome Replacer changes biome IDs; REI shows original biome names | Misleading spawn location info | Biome Replacer |
| M9 | Datapack spawn weight merging shows base + override weights | Confusing duplicate spawn entries | Any datapack that overrides base spawns |
| M10 | `displayContext` only handles 5 context types | New spawn context types show raw titleCase | Future Cobblemon updates, addon mods |
| M11 | 30s network transfer timeout too short for very large modpacks | Falls back to potentially stale local data | Fakemon-heavy modpacks (10K+ spawns) |
| M12 | `cleanValue()` rejects strings containing `@` | Could reject valid advancement IDs | Advancement-gated evolutions |

### LOW (Cosmetic, unlikely, or minimal impact)

| ID | Risk | Impact | Affected Mods |
|---|---|---|---|
| L1 | Race condition on `cachedModRoots` first-read-then-write | Redundant mod path discovery (benign) | None |
| L2 | Non-atomic check-then-put on PokemonItemCache ConcurrentHashMap | Duplicate species resolution (benign) | None |
| L3 | `titleCase()` fallback for unknown requirement variant names | Ugly but readable display text | Future Cobblemon |
| L4 | EMI+REI co-install: REI plugin silently disables | User gets EMI-only behavior (intended) | EMI + REI setups |
| L5 | Hardcoded NeoForge payload version `"1"` | No migration path for format changes | Future mod versions |
| L6 | `Files.walk(spawnDir, 10)` depth limit | Spawns in deeply nested directories missed | Unlikely in practice |
| L7 | Cobbreeding data not shown in REI | Feature gap, not a bug | Cobbreeding |
| L8 | Gacha/Summon obtainment not in REI | Feature gap for non-standard obtainment | CobbledGacha, Orb Summons |
| L9 | File-based spawn weights don't reflect runtime modifications | Display shows base weights, not runtime-adjusted | MoreCobblemonTweaks |

---

## 16. Recommendations

### Priority 1 — Fix Broken Reflection (C1)

The three currently-broken reflected fields (`range`, `requiredAdvancement`, `biomeCondition`) need to be investigated against Cobblemon 1.7.1's actual class structure. The field names may have been renamed. Consider:
- Adding a reflection field name mapping layer that can be updated without code changes
- Falling back to toString() on the requirement object when all field extraction fails
- Opening a Cobblemon API request for a public requirements-to-display-string API

### Priority 2 — Support Namespaced Species (C2)

For Fakemon mod compatibility, the species name normalizer and spawn data linker need to handle `namespace:species` format. The normalizer should NOT strip `:` — instead, it should split on `:` and use only the species part for matching, while preserving the namespace for rendering.

### Priority 3 — Handle Object-Format Pokemon Field (C3)

The spawn JSON parser should handle both string and object formats for the `pokemon` field:
```kotlin
val pokemonField = when {
    spawn.get("pokemon")?.isJsonPrimitive == true -> spawn.get("pokemon").asString
    spawn.get("pokemon")?.isJsonObject == true -> spawn.getAsJsonObject("pokemon").get("species")?.asString
    else -> null
}
```

### Priority 4 — Defensive ItemStack Caching (H6)

Return `itemStack.copy()` from `PokemonItemCache.getItemStack()` to prevent cache corruption from external mutation.

### Priority 5 — Mega Evolution Data Source (H1)

Investigate how Mega Showdown and ZaMega register mega evolution data. If they use Cobblemon's form system, the evolution loader already iterates `species.forms[].evolutions`. If they use a custom registry, a new data loader would be needed.

### Priority 6 — Complete Anticondition Parsing (H5)

Add parsing for `timeRange`, `dimensions`, `weather`, `light`, and `moonPhase` fields in `SpawnAntiCondition` to show complete spawn restriction info.

### Priority 7 — Audit `datapacks/extra/` Scanning (H7)

Verify whether the local datapack scanner recursively walks subdirectories within `datapacks/`. If not, the COBBLEVERSE regional datapacks (Johto, Hoenn, Sinnoh) are invisible to the mod.

---

## 17. Feature Opportunity Audit — Untapped Data in the Cobblemon Ecosystem

The mod currently surfaces 4 data categories: spawns, evolutions, obtainment, and basic species info. Cobblemon's data files contain **vastly more** player-relevant information that no recipe viewer mod currently exposes. This section catalogs every data system that could become a new REI/JEI/EMI category or enrich existing displays.

### Opportunity Tier 1 — High Player Demand, Data Already Accessible

These are the things players ask about constantly that have clean, structured data sources ready to be consumed.

#### O1. Breeding / Egg Groups
**Player question:** "What can breed with my Eevee?"

**Data source:** `species.eggGroups` (string array) — every species JSON has this field. Available at runtime via `PokemonSpecies.implemented`.

| Field | Location | Example |
|---|---|---|
| `eggGroups` | Species JSON | `["field"]`, `["water1","dragon"]`, `["undiscovered"]` |
| `maleRatio` | Species JSON | `0.875` (87.5% male), `-1` (genderless) |
| `eggCycles` | Species JSON | `35` (hatch time) |

**What it would show:** REI category listing all Pokémon in the same egg group, grouped by egg group name. Clicking Eevee → shows all "Field" egg group Pokémon. Breeding pair compatibility at a glance.

**Why it matters:** Cobbreeding mod is installed. Players currently have to look up egg groups on external wikis. This is one of the most common questions in any Pokémon community.

**Complexity:** LOW — data is already in `PokemonSpecies.implemented`, just needs a new category and display.

---

#### O2. Moves / Learnsets
**Player question:** "What moves can Charizard learn? At what level?"

**Data source:** `species.moves` (string array) in species JSON. Format: `"source:movename"` where source is one of:

| Source Prefix | Meaning | Example |
|---|---|---|
| `1:` / `5:` / `36:` | Level-up move (number = learn level) | `"1:tackle"`, `"36:flamethrower"` |
| `tm:` | TM-learned | `"tm:earthquake"` |
| `egg:` | Egg move (from breeding) | `"egg:wish"` |
| `tutor:` | Move tutor | `"tutor:firepunch"` |
| `legacy:` | Legacy move (removed in current gen) | `"legacy:toxic"` |
| `special:` | Special event move | `"special:celebrate"` |

**What it would show:** Full learnset table sorted by level, with TM/egg/tutor moves in separate sections. Reverse lookup: "Which Pokémon can learn Earthquake?" — critical for TMCraft users who want to know if crafting a TM is worthwhile.

**Why it matters:** TMCraft (installed, 400+ TM recipes) adds craftable TMs. Players need to know which Pokémon can use a TM before crafting it. Currently requires external wiki.

**Complexity:** MEDIUM — move data is in species JSON as strings, but move details (power, accuracy, type, PP) are in Showdown's compiled `moves.js` which needs JS parsing or a parallel data source.

---

#### O3. Abilities
**Player question:** "What is Eevee's hidden ability? Which Pokémon have Intimidate?"

**Data source:** `species.abilities` (string array). Format: plain name, with `h:` prefix for hidden ability.

| Example | Meaning |
|---|---|
| `["runaway","adaptability","h:anticipation"]` | Slot 1: Run Away, Slot 2: Adaptability, Hidden: Anticipation |
| `["blaze","h:solar_power"]` | Slot 1: Blaze, Hidden: Solar Power |

**What it would show:** Per-species ability listing with hidden ability flagged. Reverse lookup: "All Pokémon with Intimidate" — useful for competitive team building.

**Why it matters:** Ability is one of the first things competitive players check. Currently only visible via external resources.

**Complexity:** LOW — data is in `PokemonSpecies.implemented`. Ability descriptions are in Showdown's `abilities.js` (harder to extract but not required for basic listing).

---

#### O4. Drops / Loot
**Player question:** "What items does Pikachu drop when defeated?"

**Data source:** `species.drops` in species JSON:
```json
{
  "amount": 1,
  "entries": [
    {"item": "cobblemon:silk_scarf", "percentage": 5.0},
    {"item": "cobblemon:oran_berry", "quantityRange": "1-2"}
  ]
}
```

**What it would show:** Drop table per species — which items, drop chances, quantity ranges. Reverse lookup: "Which Pokémon drop Silk Scarf?" — critical for item farming.

**Why it matters:** PastureLoot mod (installed) and Cobblemon Drop Loot Tables mod (installed) both modify drops. Players grinding for specific items need this info. Currently invisible outside of trial-and-error.

**Complexity:** LOW — `drops` field is in species JSON and available at runtime. Could also scan datapack loot table overrides.

---

#### O5. Fossil Resurrection
**Player question:** "Which fossils do I combine to get Dracovish?"

**Data source:** `data/cobblemon/fossils/*.json` — 15+ files:
```json
{"result": "dracovish", "fossils": ["cobblemon:fossilized_fish", "cobblemon:fossilized_drake"]}
```

**What it would show:** Fossil combination → result Pokémon display. Special cases: multi-fossil combos (Gen 8 chimeras), modded fossils (LumyMon's Type:Null from fossilized_helmet, Genesect from dome_fossil+dubious_disc+nether_star).

**Why it matters:** Fossil resurrection is confusing — especially Gen 8's mix-and-match system and LumyMon's custom fossil recipes. No in-game info exists.

**Complexity:** LOW — simple JSON format, small number of files.

---

#### O6. Base Stats / EV Yields
**Player question:** "What are Garchomp's base stats? What EVs does it give?"

**Data source:** `species.baseStats` and `species.evYield` in species JSON:
```json
"baseStats": {"hp": 108, "attack": 130, "defence": 95, "special_attack": 80, "special_defence": 85, "speed": 102},
"evYield": {"attack": 3}
```

**What it would show:** Stat bars/numbers per species. EV training guide: "Which Pokémon give Attack EVs?" sorted by yield amount. BST (base stat total) for quick power comparison.

**Why it matters:** Core competitive info. EV training requires knowing which wild Pokémon to fight. The Cobbled Levels mod (broader ecosystem) makes this even more relevant.

**Complexity:** LOW — already accessible via `PokemonSpecies.implemented` at runtime.

---

### Opportunity Tier 2 — Strong Player Interest, Data Requires Some Work

#### O7. Berry Guide (Growth, Mutations, Bait Effects)
**Player question:** "What berries can I plant together to get a Lum Berry? What does Cheri Berry attract?"

**Data sources (3 separate systems):**

| System | Path | Key Data |
|---|---|---|
| Berry growth | `data/cobblemon/berries/*.json` | Growth time, yield, preferred biomes, mulch preferences |
| Berry mutations | Same file, `mutations` field | `{"cobblemon:cheri_berry": "cobblemon:lum_berry"}` = Oran + Cheri → Lum |
| Spawn bait effects | `data/cobblemon/spawn_bait_effects/berries/*.json` | Which egg groups each berry attracts when thrown |

**What it would show:** Berry encyclopedia — growth conditions, mutation combinations (which two berries planted adjacently produce what), and fishing/bait effects. This is a completely unique system to Cobblemon with zero in-game documentation.

**Why it matters:** Berry farming is a major gameplay loop. Mutations are completely hidden mechanics with no discoverability. Bait effects for fishing are unknown to most players.

**Complexity:** MEDIUM — three separate data directories to parse, but all clean JSON.

---

#### O8. Pokémon Interactions (Form Changes, Item Interactions)
**Player question:** "How do I change Rotom's form? How do I milk Miltank?"

**Data source:** `data/cobblemon/pokemon_interactions/*.json` — 130+ files defining item-based interactions:
- Rotom + Bucket → different forms based on current form
- Miltank + Bucket → Milk
- Furfrou + Shears → trim style
- Alcremie + Brush → drops Sugar
- Regional form changes triggered by items and biome

**What it would show:** "Right-click Rotom with a Bucket while it's in Frost Form → gives Powder Snow Bucket." These interactions are completely invisible in-game — players discover them by accident or from wikis.

**Why it matters:** 130+ unique interactions, almost none documented in-game. Form changes via interaction (different from evolution) are especially confusing.

**Complexity:** MEDIUM — JSON format is consistent but interactions have complex conditional logic (requirements with nested `owner_held_item`, `properties` checks, and scripted effects).

---

#### O9. Mega Evolution Data
**Player question:** "How do I mega evolve Charizard? What's Mega Charizard X's type?"

**Current state:** Mega evolution data exists in species `forms` arrays:
```json
{
  "name": "Mega-X",
  "primaryType": "fire",
  "secondaryType": "dragon",
  "aspects": ["mega_x"],
  "abilities": ["toughclaws"],
  "baseStats": { ... },  // different from base
  "battleOnly": true
}
```

But the **battle trigger mechanism** (Mega Showdown and ZaMega mods) is NOT in species JSON. Those mods register the mega stone → form change binding in their own code.

**What it would show:** Mega form stat comparison (base vs mega), type changes, ability changes. The trigger info ("Hold Charizardite X → Mega Evolve in battle") would need to reference Mega Showdown's item registry.

**Why it matters:** Mega Showdown and ZaMega are both installed. This is an active gameplay mechanic with zero in-game documentation of stat changes, type changes, or which mega stone goes with which Pokémon.

**Complexity:** HIGH — form data is accessible, but linking mega stones to forms requires reading Mega Showdown's and ZaMega's recipe/item registries. The `battleOnly: true` flag identifies mega forms but doesn't specify the trigger item.

---

#### O10. Form Change Conditions (Non-Evolution)
**Player question:** "How does Castform change form? How do I get Shaymin Sky Form?"

**Data source:** `data/cobblemon/species_features/*.json` — 100+ feature definitions:

| Feature | Pokémon | Trigger |
|---|---|---|
| `forecast_form` | Castform | Weather (normal/sunny/rainy/snowy) |
| `gracidea_forme` | Shaymin | Gracidea flower item |
| `hunger_mode` | Morpeko | Hunger mechanic |
| `blazing_mode` | Darmanitan | Battle HP threshold |
| `schooling_form` | Wishiwashi | Level + HP threshold |
| `power_construct` | Zygarde | Battle mechanic |
| `disguise_form` | Mimikyu | First hit in battle |
| `ice_face_form` | Eiscue | Snow/damage mechanic |
| `appliance` | Rotom | Item interaction (see O8) |
| `pumpkin_size` | Pumpkaboo/Gourgeist | Random on spawn |
| `vivillon_wings` | Vivillon | Biome-based |
| `cream` + `decoration` | Alcremie | Spin direction + held item |
| Regional flags | Many species | `alolan`, `galarian`, `hisuian`, `paldean` |

**What it would show:** Form change guide — conditions, triggers, stat differences between forms. Currently completely invisible in-game.

**Why it matters:** Form changes are some of the most confusing mechanics. Players don't know Castform changes in weather, or that Alcremie's form depends on which direction you spin and what sweet you hold.

**Complexity:** MEDIUM — species_features define the feature *existence*, but the trigger logic is often in Cobblemon's code (weather detection, battle mechanics). Some are simple flags, others are deeply behavioral.

---

#### O11. Type Effectiveness Chart
**Player question:** "Is Fire super effective against Steel?"

**Data source:** Showdown's `typechart.js` — compiled JS, but the type chart is also a well-known static dataset that could be hardcoded.

**What it would show:** Interactive type chart in REI. Search "Fire" → see all matchups. Search "Garchomp" → see its defensive/offensive type matchups based on Dragon/Ground.

**Why it matters:** The #1 most-searched Pokémon info by new players. Cobblemon: Cobble It mod (available in ecosystem) does this as a hover tooltip, but a full chart in REI would be more comprehensive.

**Complexity:** LOW (if hardcoded) — the type chart hasn't changed since Gen 6. Could be a static 18×18 table.

---

#### O12. Fishing / PokéRod Loot
**Player question:** "What can I catch with a Great Ball Rod? What fish Pokémon spawn?"

**Data sources:**

| Source | Path | Content |
|---|---|---|
| PokéRod definitions | `data/cobblemon/pokerods/*.json` | 46 rod types, each tied to a ball type |
| Fishing loot tables | `data/cobblemon/loot_table/fishing/*.json` | Junk/treasure loot pools |
| Fishing spawns | `spawn_pool_world` files with `context: "fishing"` | Which Pokémon are fishable |

**What it would show:** Per-rod breakdown — which rod catches what, loot table items, and which Pokémon can be fished. Currently spawning REI shows fishing spawns but doesn't link them to specific rods.

**Why it matters:** PokéRods are a major crafting investment (46 types!). Players need to know which rod to use. Berry bait effects (see O7) also interact with fishing.

**Complexity:** MEDIUM — three data sources to combine. Rod → ball type → catch rates is algorithmic.

---

#### O13. Riding / Mount Stats
**Player question:** "How fast is Charizard to ride? Can Lapras surf?"

**Data source:** Species JSON `riding` field (present on rideable Pokémon):
```json
{
  "behaviours": {
    "AIR": {"key": "cobblemon:air/bird", "stats": {"SPEED": "30-65", "STAMINA": "30-65", ...}},
    "LAND": {"key": "cobblemon:land/horse", "stats": {"SPEED": "35-70", ...}}
  }
}
```

Also: `data/cobblemon/ride_settings/*.json` — 13 ride archetypes with base speed/acceleration/jump formulas.

**What it would show:** Mount comparison table — speed, stamina, jump, terrain capability (land/water/air). Players currently have no way to compare mounts except by trying them.

**Why it matters:** Journey Mounts mod is installed. Riding is a core travel mechanic. No in-game comparison of mount stats exists.

**Complexity:** MEDIUM — riding data is per-species in the species JSON, but stat ranges are expressed as strings ("30-65") and archetype formulas are in separate ride_settings files.

---

### Opportunity Tier 3 — Niche but Unique (No Other Mod Covers These)

#### O14. Pokémon Marks & Ribbons
**Player question:** "What marks exist? How rare is the Fishing Mark?"

**Data source:** `data/cobblemon/marks/*.json` — 100+ marks with chance rates, conditions, textures.

**What it would show:** Mark encyclopedia — name, rarity, conditions. Collectors want to know which marks are possible to obtain and their odds.

**Complexity:** LOW — simple JSON files.

---

#### O15. Cosmetic Items
**Player question:** "What can my Squirtle wear?"

**Data source:** `data/cobblemon/cosmetic_items/*.json` — 28+ items mapping items to compatible species.

**What it would show:** Per-Pokémon list of equippable cosmetics. Reverse: per-item list of compatible Pokémon.

**Complexity:** LOW.

---

#### O16. Herd Behavior & AI
**Player question:** "Do Pikachu travel in groups? Will Charizard defend me?"

**Data source:** Species JSON `behaviour` field:
```json
{
  "herd": {"maxSize": "5", "toleratedLeaders": [{"pokemon": "raichu", "tier": 2}]},
  "combat": {"willDefendOwner": true, "willDefendSelf": true},
  "resting": {"canSleep": true, "willSleepOnBed": true}
}
```

**What it would show:** Fun flavor info — group size, leadership hierarchies, sleep habits, combat willingness. Niche but adds personality to the Pokédex.

**Complexity:** LOW.

---

#### O17. Cooking / Seasoning Recipes
**Player question:** "How do I cook Curry? What does this seasoning do?"

**Data source:** `data/cobblemon/seasonings/*.json` (72 items) + CobbleCuisine recipes.

**What it would show:** Cooking guide — ingredient list, effects, color values. CobbleCuisine is installed, so this is relevant.

**Complexity:** LOW-MEDIUM.

---

#### O18. Shoulder-Mountable Pokémon
**Player question:** "Which Pokémon can sit on my shoulder?"

**Data source:** `shoulderMountable: true` in species JSON.

**What it would show:** Simple filterable list. Small feature, but players ask this constantly.

**Complexity:** TRIVIAL — one boolean field.

---

#### O19. Nature Effects
**Player question:** "What does Adamant nature do to my stats?"

**Data source:** Not in JSON — hardcoded 25-nature table. But universally known:
| Nature | +10% | -10% |
|---|---|---|
| Adamant | Attack | Sp. Atk |
| Jolly | Speed | Sp. Atk |
| etc. | | |

**What it would show:** Nature → stat modifier lookup. Could be a static display or tied to mint items (installed: Cobblemon has mintable natures).

**Complexity:** TRIVIAL — static data, can be hardcoded.

---

#### O20. TM Recipe Cross-Reference
**Player question:** "How do I craft TM Earthquake? Which Pokémon can learn it?"

**Data source:** TMCraft recipes (`data/tmcraft/recipe/*.json`, 400+ files) + species learnsets (moves with `tm:` prefix).

**What it would show:** TM crafting recipe + full list of compatible Pokémon. This bridges TMCraft's recipes with Cobblemon's learnset data — a cross-mod UX that neither mod provides alone.

**Why it matters:** TMCraft is installed with 400+ recipes. Without knowing which Pokémon can use a TM, crafting is guesswork.

**Complexity:** HIGH — requires cross-referencing TMCraft recipes (datapack JSON) with species `moves` arrays (runtime API). Also requires knowing what `tm:earthquake` means in terms of move stats.

---

### Opportunity Impact Matrix

| ID | Feature | Player Demand | Data Availability | Complexity | Unique Value |
|---|---|---|---|---|---|
| **O1** | Breeding / Egg Groups | VERY HIGH | Runtime API | LOW | No other mod shows this in REI |
| **O2** | Moves / Learnsets | VERY HIGH | Species JSON + Showdown | MEDIUM | Critical for TMCraft users |
| **O3** | Abilities | HIGH | Runtime API | LOW | Quick win |
| **O4** | Drops / Loot | HIGH | Species JSON | LOW | Item farming guide |
| **O5** | Fossil Resurrection | HIGH | Simple JSON | LOW | Trivial to add, big UX win |
| **O6** | Base Stats / EV Yields | HIGH | Runtime API | LOW | EV training essential |
| **O7** | Berry Guide | MEDIUM-HIGH | 3 data directories | MEDIUM | Zero in-game berry docs |
| **O8** | Pokémon Interactions | MEDIUM-HIGH | 130+ JSON files | MEDIUM | Completely hidden mechanic |
| **O9** | Mega Evolution | HIGH | Forms + addon items | HIGH | Mega Showdown/ZaMega integration |
| **O10** | Form Changes | MEDIUM | species_features JSON | MEDIUM | 100+ undocumented forms |
| **O11** | Type Effectiveness | VERY HIGH | Static/hardcoded | LOW | Universal player need |
| **O12** | Fishing / PokéRod | MEDIUM | 3 data sources | MEDIUM | 46 rods, zero docs |
| **O13** | Riding / Mount Stats | MEDIUM | Species JSON + ride_settings | MEDIUM | Journey Mounts relevance |
| **O14** | Marks & Ribbons | LOW-MEDIUM | Simple JSON | LOW | Collector niche |
| **O15** | Cosmetic Items | LOW | Simple JSON | LOW | Quick add |
| **O16** | Herd Behavior | LOW | Species JSON | LOW | Flavor content |
| **O17** | Cooking Recipes | LOW-MEDIUM | Seasoning JSON | LOW-MEDIUM | CobbleCuisine relevance |
| **O18** | Shoulder Mount List | LOW | 1 boolean field | TRIVIAL | Quick add |
| **O19** | Nature Effects | MEDIUM | Hardcoded table | TRIVIAL | Universal need |
| **O20** | TM Recipe Cross-Ref | MEDIUM-HIGH | Cross-mod data | HIGH | Unique cross-mod bridge |

### Opportunity Categories Summary

**Could become new REI categories:**
- Breeding (O1) — "Who breeds with who?"
- Pokédex Stats (O3, O6) — "Stats, abilities, EV yield"
- Drops (O4) — "What does this Pokémon drop?"
- Fossils (O5) — "Fossil → Pokémon"
- Berry Guide (O7) — "Growth, mutations, bait"
- Interactions (O8) — "Item → Pokémon interactions"
- Type Chart (O11) — "Type effectiveness"

**Could enrich existing displays:**
- Learnsets (O2) — add moves tab to species display
- Mega/Forms (O9, O10) — add to evolution display
- Fishing (O12) — add rod info to spawn display
- Riding (O13) — add mount stats to species display
- Marks (O14) — add to species display
- Nature (O19) — add as reference panel
- TM recipes (O20) — add to moves/learnset display

**Quick wins (trivial to implement):**
- O18: Shoulder-mountable list
- O19: Nature effects table
- O15: Cosmetic items list

---

*This audit was generated from analysis of source code, runtime diagnostic data, installed mod JARs, datapack contents, log files, and the broader Cobblemon mod ecosystem as of 2026-02-13. Opportunity section added 2026-02-13.*
