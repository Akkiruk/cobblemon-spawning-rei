# Cobblemon 1.8.0 Integration Plan

Status: **design approved, implementation in progress**. Target: one minor series (2.15.x) that
players on 1.8.0 get automatically, with every change degrading cleanly on 1.7.x (reflection-guarded,
no hard 1.8.0 symbol references on load paths).

Ground rule from the charter: **new page only when the knowledge cannot live on an existing page and
is genuinely worth a click.** Everything else is added context on the page a player is already on.
No "seen it in Cobblemon already" duplication — verified below per item.

Legend: 🟢 context on existing page · 🔵 new page (justified) · ⚪ deliberately skipped (noise)

### Progress

| # | Item | State |
|---|------|-------|
| 2 | Alpha context | ✅ 2.15.0 |
| 3 | Herd spawns | ✅ 2.15.0 |
| 4 | Habitat spawn pools | ✅ 2.15.0 |
| 1 | Native TMs (+ 3rd-party kept) | ✅ 2.16.0 — new TM Recipes page |
| 7 | Conditional riding seats | ✅ 2.17.0 |
| 6 | Evolution drop labels | ✅ 2.18.0 |
| 5 | Marks page | ✅ 2.19.0 — new page |
| 8 | Size variation | ⚪ skipped (runtime-computed, no per-species datum) |
| 9 | NPC party datapacks | ⚪ skipped (outside the viewer-reference mission) |
| 10 | New species/forms/evos | ⏳ verification only — during your 1.8.0 test pass |

**All planned code is implemented.** Everything is on the test profile (2.19.0, Cobblemon 1.8.0).
Remaining work is your in-game test pass across items 1–7 + the item-10 checks (Arbok patterns stay
collapsed, new evo items resolve, Yamask/Avalugg chains link).

Native TM slice: `NativeTmDataLoader` (reflection over `TechnicalMachines.tmMap`, no 1.8.0 symbols on
the load path), `JarDataCache` fallback from `data/*/tms/`, `TmItemUtils` native branch reading the
`tm_move` component off the stack, new `TmRecipeDex` category + `buildTmRecipeLayout`, exporter sheet,
`NativeTmTest`. 1.7.x: registry absent → page empty, third-party TMs untouched, same jar runs on both.
The Moves-page TM column already worked on 1.8.0 (`Learnset.tmMoves` is still synced) so no change there.

Slices 2–4 shipped together as the "spawn-data pass": `HerdSpawnReader` (reflection, no 1.8.0
symbols on the load path), herd handling in `SpawnDataLoader`, habitat-pool parsing in `JarDataCache`
via a new `forEachDataFile` helper, `HerdContext` / `HabitatContext` on `SpawnInfo`, rendering through
`buildSpecials`, merge-key + exporter + tests. Both loaders build; `HerdSpawnContextTest` green.
Note: when world spawns come from the runtime but habitat rows come from local files, the spawn
category's single source tier still reads COBBLEMON — same limitation the world spawn pool already
has; acceptable for v1.

---

## 1. 🔵 Native TM system — TM recipe / learn lookup

**What 1.8.0 does.** TMs are now first-class Cobblemon: one item `cobblemon:technical_machine`
carrying a `cobblemon:tm_move` data component (`{move: "<name>"}`). `TechnicalMachines.tmMap`
(`ResourceLocation → TechnicalMachine`) and `TechnicalMachines.moveToTM` are synced to the client via
`TechnicalMachineRegistrySyncPacket`. Each `TechnicalMachine` carries: `moveName: MoveTemplate`,
`type: String` (elemental type), `recipe: List<TechnicalMachineRecipe>?` (each = `Ingredient` + `count`),
and `obtainMethods` (`DefaultObtainMethod` = passively obtained; otherwise unlockable). A form's
TM-learnable moves are `form.moves.tmLearnableMoves()`.

**Does Cobblemon already show this?** No. `CobblemonJeiPlugin` registers only Berry / Campfire /
Brewing categories — there is **no** native REI/JEI/EMI surface for TMs, and the in-game Move Dex is a
Pokédex screen, not a recipe-viewer surface. Gap confirmed.

**Solution.**

- **`TmItemUtils` gains a native branch.** Today it only knows `tmcraft:` / `simpletms:` string
  prefixes. Add: recognise `cobblemon:technical_machine`, and resolve its move by reading the
  `tm_move` component off the looked-up `ItemStack` (reflection-guarded; the component class only
  exists on 1.8.0). Keep the id-prefix path for the addon mods. Signature changes from
  `extractMove(itemId)` to also accept an optional `ItemStack` so the component can be read; callers
  in `DexCategory` / `RecipeBuilder` / `TmTooltipHandler` already have the stack.
- **Native TM registry loader** (`NativeTmDataLoader`, mirrors `RidingDataLoader` /
  `FossilDataLoader`): reflectively read `TechnicalMachines.tmMap` after Cobblemon's data signal
  fires, producing `TmInfo(move, elementalType, ingredients: List<TmIngredient>, passivelyObtained)`.
  `TmIngredient` = resolved item-id list (tag expanded to members for icon cycling) + count.
  Bundled-jar fallback reads `data/cobblemon/tms/*.json` from the Cobblemon jar via `JarDataCache`.
- **New page: "TM Recipes"** (`DexCategory.TM_RECIPE`). One recipe per TM: move disc/name + type
  badge on the left, the up-to-3 ingredient stacks + counts on the right, a line for
  "Unlocked by owning a Pokémon that knows this move" vs "Always available", and a clickable grid of
  every species that learns it (reuse the existing move-learner grid builder). This *has* to be its
  own page: it is item-recipe-shaped (ingredients → result), it is looked up from the TM item and
  from Type Gems, and folding it into the Moves page would bury it under level-up data.
- **Moves page + move-learner grid**: the existing `tm` boolean already drives a column; wire the
  native `tmLearnableMoves()` set into `extractMoves` so the column is correct on 1.8.0, and make the
  TM glyph clickable through to the new TM Recipes page.
- **Type Gem items** (`cobblemon:*_gem`, `*_gem_cluster`, `*_gem_block`, `deepslate_crystal_core`):
  looking one up lists every TM recipe of that type. Cheap — it's a filter on the TM index.
- **Cheat mode**: clicking a TM disc in REI/JEI cheat mode gives the real `technical_machine` stack
  with the component set (parallels the existing `/pokegive` behaviour). `/givetm` is Cobblemon's.
- ⚪ **Advancement triggers** (`has_learn_specific_tm`, …) — not a reference-viewer question, skip.

**Files:** `TmItemUtils`, new `NativeTmDataLoader` + `TmInfo`, `SpawnDataIndex` (hold the index),
`CobbleDexDataQueries`, `DexCategory`, `RecipeBuilder`, `DexCategoryRegistration` per viewer,
`JarDataCache`, `en_us.json`, `SpreadsheetExporter` (new "TMs" sheet), tests for the loader + parity.

---

## 2. 🟢 Alpha Pokémon

**What 1.8.0 does.** Alphas are a runtime spawn outcome (herd leaders / `is_alpha` Molang), not a
species flag. They are bigger, level-scaled stat boost, always know 2 TM moves, lead herds, spawn in
groups, aggressive. `Marks` includes `cobblemon:alpha`. Hopo Berry (`cobblemon:hopo_berry`) attracts
them.

**Does Cobblemon already show this?** The herd/alpha data is only in spawn JSON and code; nothing
surfaces "this species can be an alpha" to the player.

**Solution — context only, no page.**

- **Spawn page**: when a species appears in any `pokemon-herd` spawn as a member with `isLeader: true`,
  add an "Alpha: can appear as a herd leader" line to that spawn entry, plus a one-time tooltip
  explaining what an alpha is (larger, stat boost scaling with level, knows 2 random TM moves, leads a
  herd). Herd data is parsed anyway for item #3.
- **Hopo Berry** lookup / obtainment: short note "Attracts Alpha Pokémon" on the berry.
- ⚪ No "Alphas" list page — it would just be "every herd-leader species", already reachable by
  filtering spawns. No alpha-capture advancement surface.

**Files:** `SpawnDataLoader` (herd parse — shared with #3), `SpawnInfo`, `SpawnDisplayHelper`,
`en_us.json`.

---

## 3. 🟢 Herd spawns + day-of-week variation

**What 1.8.0 does.** `PokemonHerdSpawnDetail` (`type: "pokemon-herd"`): `herdablePokemon:
List<Herdable>` each with `pokemon` (properties), `isLeader`, `isFollower`, `weight`, `maxTimes`,
`herdLevelRange`, `levelRangeOffset`, `heldItem`; plus detail-level `levelRange`, `maxHerdSize`,
`minDistanceBetweenSpawns`. Habitat "different Pokémon on different days" is expressed with
Molang conditions on the day counter (spawn `condition` expression), not a dedicated field.

**Does Cobblemon already show this?** No — herd spawns are completely absent from CobbleDex today
(`SpawnDataLoader` only handles `PokemonSpawnDetail`), so herd-only species show *no spawn data at
all*. That is the single biggest correctness gap.

**Solution — context on the Spawn page.**

- `SpawnDataLoader` learns the `pokemon-herd` shape. Emit one `SpawnInfo` per herdable member (so the
  member species' page shows it), tagged with: herd role (leader / follower), herd size
  (`maxHerdSize`), herd level range, held item if any. Reuse all existing condition parsing (biome,
  time, structure, weather, anti-conditions) from the shared condition code — herd details carry the
  same `conditions` list.
- New `SpawnInfo` fields: `herd: HerdContext?` (`role`, `maxSize`, `levelRange`, `heldItem`).
  Rendered as one extra line block on the spawn entry; absent → nothing shown.
- **Day/parity conditions**: surface any spawn `condition`/`anticondition` Molang expression that
  references the world day (`world.day`, `is_raining` already covered, etc.) as a human line
  ("Only on certain in-game days") rather than dropping it. Low confidence on exact expression names
  until tested in-game — flagged for your test pass.

**Files:** `SpawnDataLoader`, `SpawnInfo`, `SpawnDisplayHelper`, `SpawnDataIndex` (species-name
extraction from herd members), tests.

---

## 4. 🟢 Habitat spawn pools  *(scope grew after inspecting the jar — was "structure names")*

**What 1.8.0 actually does.** Habitats are a **separate spawn system**, not structure conditions:
`data/cobblemon/habitat_pools/*.json` (54 files). Each file = one habitat (`name` is a lang key like
`cobblemon.habitat.abandoned_fortress.name`, `type: "cobblemon:natural"`) holding a `spawns` list of
`{species, bucket, spawnablePositionType, weight, levelRange, phases}`. **`phases`** (`"1"`, `"3-5"`,
`"1-3, 5"`) is the day-cycle rotation — a habitat runs a 5-phase cycle and each phase is roughly an
in-game day, so a species with `"phases": "3"` only appears on 1 day in 5. This *is* the "different
Pokémon on different days" feature, and it is 100% absent from CobbleDex today — a large chunk of
1.8.0's obtainable species have **no spawn data at all** in the current mod.

The ruins/coves are structures with loot (TMs, item #1); habitat *structures* also exist but the
spawn knowledge lives in the pools above.

**Does Cobblemon already show this?** Only in the in-game Habitat editor UI (an authoring tool), not
as a lookup. No recipe-viewer surface.

**Solution — context on the existing Spawn page (no new page).**

- New `HabitatSpawnLoader` (reflection where a synced registry exists, else `JarDataCache` reads
  `habitat_pools/*.json`). Confirm in your test pass whether 1.8.0 syncs habitat pools to the client;
  if not, jar fallback is the only path and the "local data" warning applies.
- Emit one entry per `(species, habitat, phase-set)` into the spawn index, rendered on the species
  Spawn page under a "Habitats" group: habitat name (resolved via the `cobblemon.habitat.*.name`
  key Cobblemon already ships), bucket/rarity, level range, and a phase line — "Appears on 2 of the
  habitat's 5 days" with the raw phase spec on hover. No structure-tag translation work needed after
  all; Cobblemon's lang covers the names.
- `SpawnInfo` gains `habitat: HabitatContext?` (`habitatNameKey`, `phases: String`, `phaseCount`,
  `totalPhases`). Absent → nothing rendered, so 1.7.x is untouched.
- Reverse "what spawns in <habitat>" is naturally answered by filtering, but a habitat has no item to
  look up — deferred unless the Habitat Block turns out to be a placeable item worth a lookup page.

**Files:** new `HabitatSpawnLoader` + `HabitatInfo`, `SpawnDataIndex`, `CobbleDexDataQueries`,
`SpawnInfo`, `SpawnDisplayHelper`, `JarDataCache`, `SpreadsheetExporter`, tests.

---

## 5. 🔵 Marks

**What 1.8.0 does.** `Marks` JSON registry (`data/cobblemon/marks/*.json`), synced via
`MarkRegistrySyncPacket`. Each `Mark`: `name`, `description`, `title`/`titleColour`, `texture`,
`chance`, `group`, `aspects`, `sortOrder`. Marks are earned on capture/encounter under conditions
(weather, time, location, being an alpha, …) and give a title.

**Does Cobblemon already show this?** The Pokédex has no marks screen; marks show only on an
individual caught Pokémon's summary. There is no catalogue of "what marks exist and how do I get
one".

**Solution — new page, `DexCategory.MARKS`, styled like the Natures / Type Chart reference pages.**
One scrolling reference: mark icon + name + flavour description + rarity (`chance`) + the title it
grants. It is not per-species and not item-shaped, so it can only be a standalone reference page.
Small, bounded, high "stop needing the wiki" value. Reflection-guarded loader
(`MarkDataLoader`) + jar fallback.

The obtaining *conditions* for each mark are not in the registry data (they're in code), so the page
states rarity + effect and links to the mark's description string; it does not invent condition text.

**Files:** new `MarkDataLoader` + `MarkInfo`, `DexCategory`, `RecipeBuilder`, per-viewer
registration, `en_us.json`, `SpreadsheetExporter` (sheet), test.

---

## 6. 🟢 Evolution drops & brushing drops

**What 1.8.0 does.** Two new item sources: items dropped **when a Pokémon evolves**, and items from
**brushing** a Pokémon. Need to confirm from 1.8.0 data whether these live in the existing species
`drops` table with a new condition/type, or a separate structure.

**Does Cobblemon already show this?** No recipe-viewer surface.

**Solution — context on the existing Item Drops page**, *if* they are modelled in the species drop
table (most likely: a `DropEntry` with a method/trigger field). Then `DropEntryInfo` gains an
optional `trigger` ("On evolution" / "When brushed" / default "On defeat"), rendered as a small label
on the drop row, and the reverse "who drops this" grid already picks them up for free. Only if 1.8.0
stores them in a genuinely separate structure would a new page be considered — decide after
inspecting `data/cobblemon/species/**` in the 1.8.0 jar. **No new page unless forced.**

**Files:** `EvolutionDataLoader.extractDrops`, `DropInfo`, `SpawnDisplayHelper`, `JarDataCache`.

---

## 7. 🟢 Conditional riding seats

**What 1.8.0 does.** Seats can be gated by Molang (`is_alpha`, etc.); 9 new rideable species (data-
driven, appear automatically).

**Does Cobblemon already show this?** Riding UI shows seats for the mount you're on; not the
conditional structure.

**Solution — context on the Riding page.** `RidingInfo.seats: Int` becomes
`seats: List<SeatInfo>` where `SeatInfo` carries an optional `condition` string. Render "3 seats
(1 requires: Alpha)" when any seat is conditional; plain count otherwise. Reflection-guarded read of
the seat list from `form.riding` / `RidingProperties`.

**Files:** `RidingDataLoader`, `RidingInfo`, `SpawnDisplayHelper` riding section, test.

---

## 8. ⚪ Size / scale variation

**What 1.8.0 does.** Per-instance ±5% intrinsic scale and baby-growth (10% at L1 → 100% at L10) are
**computed at runtime**, not species data. `baseScale` / `hitbox` already existed.

**Decision: skip.** There is no per-species datum to show beyond "all Pokémon vary a little", which
is noise. If anything, a single sentence in the mod's own help/among the Pokédex Info tooltip — not a
data field. Revisit only if 1.8.0 turns out to store a per-species size-variance override.

---

## 9. ⚪ NPC datapack additions

`moveset_builders`, `party_pools`, `party_compositions`, `composed_pool` — trainer-team authoring.
Out of the reference-viewer mission (CobbleDex is not a trainer browser). Skip.

---

## 10. 🟢 New species / forms / evolution items — verification pass

Data-driven, so new families (Applin→Hydrapple/Dipplin, Pawniard→Kingambit, Snom→Frosmoth,
Rockruff, Pancham, Seviper/Zangoose, Alolan Sandshrew/Sandslash, Galarian Yamask, Hisuian Avalugg,
Wurmple line) appear automatically. To verify in your test pass:

- Arbok's 14 pattern variations stay collapsed by `MaterialFormPolicy` (texture-only).
- New evolution items resolve to real icons: Syrupy / Tart / Sweet Apple, Leader's Crest, Metal Alloy.
- New evolution requirement types render (Kingambit's "defeat 3 Bisharp holding Leader's Crest"):
  `EvolutionRequirement` already handles `defeat` with count + `held_item`; confirm the combined
  text reads well.
- Galarian Yamask → Runerigus and Hisuian forms link correctly in `EvolutionChainBuilder`.

No code planned here unless the test pass finds a gap.

---

## Sequencing

1. **#4 habitat spawn pools** + **#3 herd spawns** + **#2 alpha** — one spawn-data pass; this is the
   biggest correctness gap (species with zero spawn data today). Ship behind a build you test.
2. **#1 native TMs** — biggest feature gap; new "TM Recipes" page.
3. **#7 conditional seats**, **#6 drop triggers** — small context adds.
4. **#5 Marks page**.
5. **#10 verification** — after you can run 1.8.0.

Every step: `./gradlew :fabric:build :neoforge:build` + `common:test`, parity guard green, then you
run it against a real 1.8.0 instance before the next step. Each step is its own PR-sized change.
