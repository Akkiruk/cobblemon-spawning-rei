# Cobblemon Pokédex API Audit — CobblemonSpawningREI

**Date:** 2026-02-13
**Mod:** CobblemonSpawningREI
**Cobblemon Version:** 1.7.1+1.21.1

---

## Table of Contents

1. [Overview](#1-overview)
2. [Currently Used Data](#2-currently-used-data)
3. [Untapped Data — Free (Already Synced to Client)](#3-untapped-data--free-already-synced-to-client)
4. [Untapped Data — Requires Server Packet](#4-untapped-data--requires-server-packet)
5. [Player-Specific Pokédex Data](#5-player-specific-pokédex-data)
6. [Cobblemon Pokédex Architecture](#6-cobblemon-pokédex-architecture)
7. [Clientside Info Provider Classes](#7-clientside-info-provider-classes)
8. [Cobblemon Pokédex GUI — 10 Tabs Reference](#8-cobblemon-pokédex-gui--10-tabs-reference)
9. [Species & FormData — Full Field Reference](#9-species--formdata--full-field-reference)
10. [Drop System Details](#10-drop-system-details)
11. [Research Tasks System Details](#11-research-tasks-system-details)
12. [Integration Code Patterns](#12-integration-code-patterns)
13. [Opportunity Ranking](#13-opportunity-ranking)
14. [Dex Definition System](#14-dex-definition-system)

---

## 1. Overview

CobblemonSpawningREI is essentially an "REI Pokédex" — it displays spawn conditions, evolution data, and obtainment methods in REI/JEI/EMI. Cobblemon's own Pokédex system exposes a wealth of data beyond what the mod currently uses. This audit catalogs every available API surface and identifies untapped opportunities.

---

## 2. Currently Used Data

The mod currently accesses only **7 fields** from Cobblemon's runtime API:

| Data | Where Used | Access Method |
|------|-----------|---------------|
| `species.name` | Everywhere | Direct field |
| `species.nationalPokedexNumber` | Tooltip + SpeciesBasicInfo | Direct field |
| `species.primaryType` / `secondaryType` | SpeciesBasicInfo | Direct field |
| `species.catchRate` | SpeciesBasicInfo | Direct field |
| `species.weight` / `height` | SpeciesBasicInfo | Direct field |
| `species.evolutions` + `form.evolutions` | EvolutionDataLoader | Reflection-heavy parsing |
| Spawn JSON files | SpawnDataLoader | Own JSON parser from mod JARs |

**Not used:** Drops, base stats, abilities, moves, egg groups, flavor text, labels, pre-evolutions, friendship, egg cycles, gender ratio, Pokédex progress, research tasks, or any clientside info providers.

---

## 3. Untapped Data — Free (Already Synced to Client)

All of the following are available on the client without any server requests. They're synced via `SpeciesRegistrySyncPacket` when the player joins.

### 3.1 Drops (`form.drops`)

| Field | Type | Description |
|-------|------|-------------|
| `species.drops` / `form.drops` | `DropTable` | Item drops when defeated |
| `DropTable.entries` | `List<DropEntry>` | All drop entries |
| `ItemDropEntry.item` | `ResourceLocation` | Item ID (e.g., `minecraft:string`) |
| `ItemDropEntry.percentage` | `Float` | Drop chance (100 = guaranteed) |
| `ItemDropEntry.quantity` | `Int` | Base drop count |
| `ItemDropEntry.quantityRange` | `IntRange?` | Range of drop amounts |

**Impact:** Very high. Connects Pokémon → Items, which is the core value proposition of a recipe viewer. This is exactly what Cobblemon's own Pokédex "Drops" tab displays.

### 3.2 Base Stats (`form.baseStats`)

| Field | Type | Description |
|-------|------|-------------|
| `form.baseStats` | `Map<Stat, Int>` | HP, Atk, Def, SpA, SpD, Spe |

**Impact:** High. Stat bars are a Pokédex staple. Available directly, no computation needed.

### 3.3 Abilities (`form.abilities`)

| Field | Type | Description |
|-------|------|-------------|
| `form.abilities` | `AbilityPool` | Sorted list of common + hidden abilities |
| `ability.template` | `AbilityTemplate` | Has `.displayName` for translation |
| `ability is HiddenAbility` | `Boolean` | Distinguishes hidden vs common |

**Impact:** High. Abilities are essential Pokédex info. Each ability has a translatable display name.

### 3.4 Moves / Learnset (`form.moves`)

| Field | Type | Description |
|-------|------|-------------|
| `form.moves` | `Learnset` | All move sources |
| Level-up moves | | Moves learned by leveling |
| TM moves | | Moves learned via TMs |
| Egg moves | | Moves inherited from breeding |
| Tutor moves | | Moves from move tutors |

**Impact:** Medium-High. Very data-rich but complex to display well in REI. Would need pagination or scrolling.

### 3.5 Egg Groups (`form.eggGroups`)

| Field | Type | Description |
|-------|------|-------------|
| `form.eggGroups` | `Set<EggGroup>` | Breeding compatibility groups |

**Impact:** Medium. Useful for breeding info. Low effort to implement.

### 3.6 Pokédex Description (`species.pokedex`)

| Field | Type | Description |
|-------|------|-------------|
| `species.pokedex` | `List<String>` | Flavor text entries |

**Impact:** Medium. Adds character/lore. Multiple entries can be displayed as paragraphs.

### 3.7 Labels (`species.labels`)

| Field | Type | Description |
|-------|------|-------------|
| `species.labels` | `Set<String>` | Category tags |

Known values: `"legendary"`, `"mythical"`, `"ultra_beast"`, `"paradox"`, and others.

**Impact:** Medium. Enables category badges/icons and REI filtering (e.g., "show all legendaries").

### 3.8 Pre-Evolution (`species.preEvolution`)

| Field | Type | Description |
|-------|------|-------------|
| `species.preEvolution` | `PreEvolution?` | What this species evolves FROM |

**Impact:** Medium. Enables full evolution family trees (both forward and backward chains).

### 3.9 Breeding/Hatching Info

| Field | Type | Description |
|-------|------|-------------|
| `species.eggCycles` | `Int` | Egg hatch cycle count |
| `species.baseFriendship` | `Int` | Initial friendship value |
| `species.maleRatio` | `Float` | Gender ratio (-1=genderless, 0=female-only, 1=male-only) |

**Impact:** Low-Medium. Useful for breeding-focused displays.

### 3.10 Experience Info

| Field | Type | Description |
|-------|------|-------------|
| `species.experienceGroup` | `ExperienceGroup` | Growth rate (slow, medium fast, etc.) |
| `species.baseExperienceYield` | `Int` | XP yield when defeated |

**Impact:** Low. Niche but easy to add.

### 3.11 Clientside Evolution Info (`form.clientsidePokedexEvolutionsInfo`)

| Field | Type | Description |
|-------|------|-------------|
| `form.clientsidePokedexEvolutionsInfo` | `List<ClientsidePokedexEvolutionInfo>?` | Pre-computed evolution display data |
| `.type` | `String` | Evolution type (e.g., `"level_up"`, `"item_interact"`) |
| `.result` | `PokemonProperties` | Resulting species/form/aspects |
| `.requirementsString` | `String` | Human-readable requirements |

**Impact:** High (as a replacement strategy). This pre-computed data from Cobblemon could replace the reflection-heavy `EvolutionDataLoader` for basic display. You'd lose the structured granularity of individual requirement parsing but gain stability and zero reflection. See [COMPATIBILITY_AUDIT.md Section 18](COMPATIBILITY_AUDIT.md) for the full reflection elimination strategy.

### 3.12 Other Misc Fields

| Field | Type | Description |
|-------|------|-------------|
| `species.dynamaxBlocked` | `Boolean` | Can this species Dynamax? |
| `species.implemented` | `Boolean` | Has model/textures |
| `form.aspects` | `List<String>` | Form-defining aspects |
| `form.possibleGenders` | `Set<Gender>` | Possible genders for this form |
| `species.hitbox` | `EntityDimensions` | Entity hitbox size |
| `species.features` | `List<String>` | Species features (cosmetic aspects) |
| `species.baseScale` | `Float` | Model scale multiplier |

---

## 4. Untapped Data — Requires Server Packet

These require a client→server request and an async response. More complex to integrate.

### 4.1 Spawn Conditions (Cobblemon's Processed View)

**Request:** `PokedexRequestSpawnInfoPacket(speciesName).sendToServer()`
**Response:** `ClientsidePokedexSpawnDetailInfo` via `ClientsidePokedexSpawnInfoManager.map[speciesName]`

| Field | Type | Description |
|-------|------|-------------|
| `.name` | `String` | Display name for this spawn entry |
| `.pokemonProperties` | `PokemonProperties` | Pokémon properties for this spawn |
| `.levelRange` | `IntRange?` | Level range |
| `.contextName` | `String` | Spawn context (e.g., "Grassy") |
| `.bucketName` | `String` | Rarity bucket (e.g., "Common") |
| `.conditionsString` | `String` | Human-readable conditions (biome, time, weather, etc.) |

**Note:** This is an alternative to the mod's own JSON parsing approach. The mod's approach parses raw spawn files for more control; this approach gives Cobblemon's server-processed view. One advantage: it accounts for runtime spawn modifications and datapacks.

### 4.2 Research Tasks

**Request:** `RequestResearchTasksInfoPacket(species).sendToServer()`
**Response:** `ResearchTasksInfoPacket` via `ClientsideResearchTasksManager`

| Field | Type | Description |
|-------|------|-------------|
| `.species` | `String` | Species name |
| `.progress` | `Map<String, Int>` | Task ID → progress count |
| `.tasks` | `List<ResearchTaskConfig>` | Task definitions |
| `ResearchTaskConfig.task` | `String` | Task type (see below) |
| `ResearchTaskConfig.target` | `String?` | Optional target (move name, ball type, etc.) |
| `ResearchTaskConfig.amount` | `Int` | Required completions |

**18 Research Task Types:**
`catch`, `evolve`, `defeat`, `fish`, `shear`, `hatch`, `milk`, `revive`, `mega_evolve`, `raid_defeat`, `use_move`, `evolve_into`, `catch_with`, `catch_form`, `catch_status`, `catch_time`, `catch_gender`, `catch_ability`, `catch_aspect`

**Completion Check:** `RequestResearchTasksAllCompletedPacket().sendToServer()` → `ClientsideResearchTasksAllCompletedManager.speciesWithAllTasksCompleted` (Set<String>)

**Completing all research tasks for a species unlocks the golden Pokéball shiny boost.**

**Challenges for REI integration:**
- Per-species on-demand (requires packet round-trip per species viewed)
- Player-specific data (progress varies per player)
- Would need pre-caching strategy or lazy loading

---

## 5. Player-Specific Pokédex Data

Automatically synced to the client via `SetClientPlayerDataPacket`. Available at `CobblemonClient.clientPokedexData`.

### 5.1 Pokédex Data Hierarchy

```
CobblemonClient.clientPokedexData (ClientPokedexManager extends AbstractPokedexManager)
└── speciesRecords: Map<ResourceLocation, SpeciesDexRecord>
    └── SpeciesDexRecord
        ├── aspects: Set<String>         — cosmetic variations seen
        └── formRecords: Map<String, FormDexRecord>
            └── FormDexRecord
                ├── genders: Set<Gender>           — MALE, FEMALE, GENDERLESS
                ├── seenShinyStates: Set<String>   — "shiny", "normal"
                └── knowledge: PokedexEntryProgress — NONE, ENCOUNTERED, CAUGHT
```

### 5.2 Available Methods on `AbstractPokedexManager`

| Method | Returns | Description |
|--------|---------|-------------|
| `getSpeciesRecord(speciesId)` | `SpeciesDexRecord?` | Player's record for a species |
| `getHighestKnowledgeForSpecies(id)` | `PokedexEntryProgress` | NONE/ENCOUNTERED/CAUGHT |
| `getEncounteredForms(entry)` | `List<PokedexForm>` | Forms player has encountered |
| `getCaughtForms(entry)` | `List<PokedexForm>` | Forms player has caught |
| `getAllPokedexForms(entry)` | `List<PokedexForm>` | All forms (excluding G-Max) |
| `getSeenShinyStates(entry, form)` | `Set<String>` | Shiny states seen for form |
| `getSeenGenders(entry, form)` | `Set<Gender>` | Genders seen for form |
| `getSeenAspects(entry)` | `Set<String>` | Cosmetic aspects seen |
| `getDexCalculatedValue(dex, calc)` | `T` | Per-dex cached value |
| `getGlobalCalculatedValue(calc)` | `T` | Global cached value |

### 5.3 Enums

- **`PokedexEntryProgress`:** `NONE`, `ENCOUNTERED`, `CAUGHT`
- **`PokedexLearnedInformation`:** `NONE`, `SPECIES`, `FORM`, `VARIATION`

### 5.4 Value Calculators

| Calculator | Type | Description |
|------------|------|-------------|
| `CaughtCount` | `Int` | # of caught species |
| `SeenCount` | `Int` | # of seen species |
| `CaughtPercent` | `Float` | % caught of all dex entries |
| `SeenPercent` | `Float` | % seen of all dex entries |

### 5.5 Potential Uses in REI

- **Caught badge:** Small Pokéball icon on REI entries for caught species
- **Silhouettes:** Darken/hide sprites for species the player hasn't seen
- **Progress bar:** Show player's dex completion in a summary category
- **Form discovery:** Indicate which forms the player has seen/caught
- **Shiny/gender tracking:** Show which shinies and genders the player has encountered

---

## 6. Cobblemon Pokédex Architecture

### Data Flow

```
Server: PokedexManager (per player UUID, persisted in NBT)
    ↓ SetClientPlayerDataPacket (incremental sync on any change)
Client: CobblemonClient.clientPokedexData (ClientPokedexManager)
```

### Registry Classes

- **`DexEntries`** — Global registry of all `PokedexEntry` (loaded from `data/cobblemon/dex_entries/`)
- **`Dexes`** — Registry of all `PokedexDef` (regional dexes); access via `Dexes.dexEntryMap`

### Entry Structure

```kotlin
PokedexEntry {
    id: ResourceLocation
    speciesId: ResourceLocation
    displayAspects: Set<String>
    conditionAspects: Set<String>
    forms: List<PokedexForm>
    variations: List<PokedexCosmeticVariation>
}

PokedexForm {
    displayForm: String          // form name
    unlockForms: MutableSet<String>  // forms unlocked when seen/caught
}

PokedexDef (abstract)
├── SimplePokedexDef    // single regional dex
└── AggregatePokedexDef // combines multiple (e.g. "national")
```

---

## 7. Clientside Info Provider Classes

Cobblemon has exactly **two** client-side info providers that pre-compute display data:

### 7.1 `ClientsidePokedexEvolutionInfo`

```kotlin
data class ClientsidePokedexEvolutionInfo(
    val type: String,                    // evolution type identifier
    val result: PokemonProperties,       // resulting species/form/aspects
    val requirementsString: String       // human-readable requirements
)
```

- **Access:** `FormData.clientsidePokedexEvolutionsInfo`
- **Synced with:** Species data (no extra packet needed)
- **Used by:** Cobblemon's `EvolutionsScrollingWidget` in the Pokédex GUI

### 7.2 `ClientsidePokedexSpawnDetailInfo`

```kotlin
data class ClientsidePokedexSpawnDetailInfo(
    var name: String,
    var pokemonProperties: PokemonProperties,
    var levelRange: IntRange?,
    var contextName: String,
    var bucketName: String,
    var conditionsString: String
)
```

- **Access:** `ClientsidePokedexSpawnInfoManager.map[speciesName]`
- **Requires:** `PokedexRequestSpawnInfoPacket(speciesName).sendToServer()` (on-demand)
- **Used by:** Cobblemon's `LocationsScrollingWidget` in the Pokédex GUI

---

## 8. Cobblemon Pokédex GUI — 10 Tabs Reference

This is what Cobblemon's own Pokédex UI displays — a useful reference for what data is available and display-worthy.

| Tab # | Tab Name | Widget | Data Source |
|-------|----------|--------|-------------|
| 0 | Description | `DescriptionWidget` | `species.standardForm.pokedex` (flavor text) |
| 1 | Locations | `LocationsScrollingWidget` | `ClientsidePokedexSpawnInfoManager` (server-requested) |
| 2 | Drops | `DropsScrollingWidget` | `form.drops` (DropTable) |
| 3 | Evolutions | `EvolutionsScrollingWidget` | `form.clientsidePokedexEvolutionsInfo` |
| 4 | Stats | `StatsWidget` | `form.baseStats` (HP/Atk/Def/SpA/SpD/Spe) |
| 5 | Abilities | `AbilitiesWidget` | `form.abilities` (common + hidden) |
| 6 | Moves | `MovesWidget` | `form.moves` (Learnset) |
| 7 | Egg Groups | `EggGroupsWidget` | `form.eggGroups` |
| 8 | Size | `SizeWidget` | `form.height`, `form.weight`, `form.baseScale` + 3D model |
| 9 | Research Tasks | `ResearchTasksScrollingWidget` | `ClientsideResearchTasksManager` (server-requested) |

The `PokemonInfoWidget` (always visible alongside tabs) displays: species name, dex number, types, form selector, gender/shiny/cosmetic toggles, caught/golden pokéball icon, 3D rendered model.

---

## 9. Species & FormData — Full Field Reference

### From `Species` (synced to client)

| Field | Type | Notes |
|-------|------|-------|
| `name` | `String` | Species name |
| `nationalPokedexNumber` | `Int` | National dex # |
| `implemented` | `Boolean` | Has model/textures |
| `baseStats` | `Map<Stat, Int>` | HP, Atk, Def, SpA, SpD, Spe |
| `primaryType` / `secondaryType` | `ElementalType` | Types |
| `height` | `Float` | Decimeters |
| `weight` | `Float` | Hectograms |
| `maleRatio` | `Float` | -1=genderless, 0=female only, 1=male only |
| `baseScale` | `Float` | Model scale |
| `catchRate` | `Int` | Base catch rate |
| `experienceGroup` | `ExperienceGroup` | Growth rate |
| `baseExperienceYield` | `Int` | XP yield |
| `baseFriendship` | `Int` | Starting friendship |
| `abilities` | `AbilityPool` | Common + Hidden |
| `moves` | `Learnset` | All move sources |
| `evolutions` | `MutableSet<Evolution>` | Direct evolution objects |
| `preEvolution` | `PreEvolution?` | What this evolves from |
| `forms` | `List<FormData>` | All alternate forms |
| `labels` | `Set<String>` | e.g. "legendary", "mythical" |
| `drops` | `DropTable` | Item drop data |
| `pokedex` | `List<String>` | Flavor text description |
| `eggCycles` | `Int` | Steps to hatch |
| `eggGroups` | `Set<EggGroup>` | Breeding groups |
| `dynamaxBlocked` | `Boolean` | Dynamax eligible |
| `hitbox` | `EntityDimensions` | Entity hitbox |
| `features` | `List<String>` | Species features (aspects) |

### From `FormData` (per-form, inherits from species if not overridden)

All of the above as per-form overrides, plus:
- `clientsidePokedexEvolutionsInfo: MutableList<ClientsidePokedexEvolutionInfo>?`
- `aspects: List<String>` — form-defining aspects
- `possibleGenders: Set<Gender>` — derived from maleRatio

---

## 10. Drop System Details

### Class Hierarchy

```
DropTable
├── entries: List<DropEntry>          — all possible drops
├── amount: IntRange                  — default quantity range
├── getDrops(amount, pokemon): List<DropEntry>
├── drop(entity, world, pos, player)  — performs the actual drop
├── encode(buffer) / decode(buffer)   — network serialization
│
DropEntry (interface)
├── percentage: Float    — drop chance (100 = guaranteed)
├── quantity: Int        — slot cost
├── maxSelectableTimes: Int
├── canDrop(pokemon): Boolean
├── drop(entity, world, pos, player)
│
├── ItemDropEntry        — drops an item
│   ├── item: ResourceLocation
│   ├── quantityRange: IntRange?
│   ├── dropMethod: ItemDropMethod?
│   └── components: DataComponentMap?
│
├── CommandDropEntry     — runs a command
│   └── command: String  — with {{player}}, {{x}} etc. placeholders
│
└── EvolutionItemDropEntry  — item drop gated by evolution requirements
    └── requirements: Set<EvolutionRequirement>
```

### Drop Methods
- `ON_ENTITY` — drops on the dying entity
- `ON_PLAYER` — drops on the player
- `TO_INVENTORY` — directly to inventory (fallback for lava)

### Cobblemon Pokédex Drops Display
Cobblemon's `DropsScrollingWidget` iterates `dropTable.entries`, filters to `ItemDropEntry`, and displays:
- Item icon (scaled)
- Item name
- Quantity (or range: "1–3")
- Percentage (formatted with `DecimalFormat("#.##")`)

---

## 11. Research Tasks System Details

### Architecture

```
Server-side:
  ResearchTasksConfig (config/cobblemon/research_tasks.json)
  ├── tasks: Map<String, List<ResearchTaskConfig>>  — species → task list
  └── goldenPokeballShinyRates: Map<String, Float>   — species → shiny rate boost

  PlayerResearchTasksData (per-player, persisted via CCA)
  ├── progress: Map<String, Map<String, Int>>  — species → taskId → count
  └── speciesWithAllTasksCompleted: Set<String>

Client-side:
  ClientsideResearchTasksManager
  ├── progress: Map<String, Map<String, Int>>
  └── tasks: Map<String, List<ResearchTaskConfig>>

  ClientsideResearchTasksAllCompletedManager
  └── speciesWithAllTasksCompleted: Set<String>
```

### Research Task Types (18 total)

| Type | Target | Display |
|------|--------|---------|
| `catch` | none | "Catch" |
| `evolve` | none | "Evolve" |
| `defeat` | none | "Defeat" |
| `fish` | none | "Fish" |
| `shear` | none | "Shear" |
| `hatch` | none | "Hatch" |
| `milk` | none | "Milk" |
| `revive` | none | "Revive From Fossil" |
| `mega_evolve` | none | "Mega Evolve" |
| `raid_defeat` | none | "Defeat in Raid Battle" |
| `use_move` | move name | "Use Move \<move\>" |
| `evolve_into` | species | "Evolve Into \<species\>" |
| `catch_with` | ball ID | "Catch With \<ball\>" |
| `catch_form` | form name | "Catch Form \<form\>" |
| `catch_status` | status | "Catch With Status: \<status\>" |
| `catch_time` | time period | "Catch At \<time\>" |
| `catch_gender` | gender | "Catch Gender: \<gender\>" |
| `catch_ability` | ability | "Catch With Ability: \<ability\>" |
| `catch_aspect` | aspect | "Catch: \<aspect\>" |

### Reward: Golden Pokéball
Completing all research tasks for a species unlocks the golden Pokéball, which provides a configurable shiny chance boost for that species. Default rates are in `goldenPokeballShinyRates`.

---

## 12. Integration Code Patterns

### Accessing Species Data (completely free, already synced)

```kotlin
val species = PokemonSpecies.getByName("bulbasaur") ?: return
val stats = species.baseStats                    // Map<Stat, Int>
val types = species.types                        // Iterable<ElementalType>
val abilities = species.abilities                // AbilityPool
val eggGroups = species.eggGroups               // Set<EggGroup>
val drops = species.drops                        // DropTable
val description = species.pokedex               // List<String>
val catchRate = species.catchRate               // Int
val labels = species.labels                     // Set<String>
val preEvo = species.preEvolution               // PreEvolution?
val eggCycles = species.eggCycles               // Int
val friendship = species.baseFriendship         // Int
val genderRatio = species.maleRatio             // Float
val xpGroup = species.experienceGroup           // ExperienceGroup
val xpYield = species.baseExperienceYield       // Int
```

### Checking Player's Pokédex Progress

```kotlin
val pokedex = CobblemonClient.clientPokedexData ?: return
val speciesId = species.resourceIdentifier
val knowledge = pokedex.getHighestKnowledgeForSpecies(speciesId)
val isCaught = knowledge == PokedexEntryProgress.CAUGHT
val isSeen = knowledge.ordinal >= PokedexEntryProgress.ENCOUNTERED.ordinal
```

### Using Clientside Evolution Info (no reflection needed)

```kotlin
val form = species.standardForm
val evolutions = form.clientsidePokedexEvolutionsInfo
evolutions?.forEach { evo ->
    val type = evo.type                // e.g., "level_up", "item_interact"
    val result = evo.result            // PokemonProperties (species, form, aspects)
    val requirements = evo.requirementsString  // Human-readable
}
```

### Requesting Spawn Info from Server

```kotlin
// 1. Send request
PokedexRequestSpawnInfoPacket(speciesName).sendToServer()

// 2. Check result later (async — arrives via packet handler)
val spawns = ClientsidePokedexSpawnInfoManager.map[speciesName]
spawns?.forEach { info ->
    info.name            // spawn display name
    info.contextName     // biome context
    info.bucketName      // rarity bucket
    info.levelRange      // level range
    info.conditionsString // human-readable conditions
}
```

### Requesting Research Tasks from Server

```kotlin
// Per-species tasks + current player progress
RequestResearchTasksInfoPacket(speciesName).sendToServer()
// Result arrives at ClientsideResearchTasksManager.progress[species] / .tasks[species]

// Global completion status (which species have all tasks done)
RequestResearchTasksAllCompletedPacket().sendToServer()
// Result arrives at ClientsideResearchTasksAllCompletedManager.speciesWithAllTasksCompleted
```

### Reading Drops

```kotlin
val drops = species.drops  // or form.drops for form-specific
drops.entries.filterIsInstance<ItemDropEntry>().forEach { entry ->
    val itemId = entry.item           // ResourceLocation
    val percent = entry.percentage    // Float
    val qty = entry.quantity          // Int
    val range = entry.quantityRange   // IntRange?
}
```

---

## 13. Opportunity Ranking

Ranked by impact × ease of implementation:

| Rank | Feature | Data Source | Server Needed | Effort | Impact |
|------|---------|-------------|---------------|--------|--------|
| **1** | **Drops display** | `form.drops` | No | Low | **Very High** — connects Pokémon↔Items in recipe viewer |
| **2** | **Base Stats** | `form.baseStats` | No | Low | **High** — Pokédex staple |
| **3** | **Abilities** | `form.abilities` | No | Low | **High** — essential info |
| **4** | **Egg Groups** | `form.eggGroups` | No | Very Low | **Medium** — breeding info |
| **5** | **Caught/Seen badges** | `clientPokedexData` | No | Low | **Medium-High** — progress tracking |
| **6** | **Labels (Legendary/Mythical)** | `species.labels` | No | Very Low | **Medium** — badges/filters |
| **7** | **Pre-Evolution chain** | `species.preEvolution` | No | Low | **Medium** — full family tree |
| **8** | **Description text** | `species.pokedex` | No | Very Low | **Medium** — flavor |
| **9** | **clientsidePokedexEvolutionsInfo** | `form.clientsidePokedexEvolutionsInfo` | No | Medium | **High** — reflection replacement |
| **10** | **Gender ratio / Egg cycles** | `species.maleRatio`, `.eggCycles` | No | Very Low | **Low-Medium** |
| **11** | **Moves / Learnset** | `form.moves` | No | High | **Medium** — complex display |
| **12** | **Regional dex filtering** | `Dexes.dexEntryMap` | No | Medium | **Low-Medium** |
| **13** | **Research Tasks** | `ClientsideResearchTasksManager` | Yes (per-species) | High | **Medium** — unique feature |
| **14** | **Server spawn data** | `ClientsidePokedexSpawnInfoManager` | Yes (per-species) | Medium | **Low** — already have own system |

---

## 14. Dex Definition System

### Data-Driven Dex Definitions

Cobblemon loads regional dexes from `data/cobblemon/dex_entries/pokemon/` organized by region:

```
data/cobblemon/dex_entries/pokemon/
├── kanto/
├── johto/
├── hoenn/
├── sinnoh/
├── unova/
├── kalos/
├── alola/
├── galar/
├── hisui/
└── paldea/
```

### Access at Runtime

```kotlin
val availableRegions = Dexes.dexEntryMap.keys.toList()  // List of ResourceLocations
val nationalDex = Dexes.dexEntryMap[cobblemonResource("national")]
val entries = nationalDex?.getEntries()  // List<PokedexEntry>
```

This could enable REI category filtering by region (e.g., "Show only Kanto Pokémon").
