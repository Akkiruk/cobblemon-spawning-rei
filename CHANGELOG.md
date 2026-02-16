# Changelog

All notable changes to CobbleDex REI/EMI/JEI will be documented in this file.

## [1.28.5] - 2026-02-15

### Fixed
- EMI: Both left-click and right-click on a Pokémon now show all categories (spawns, evolutions, stats, moves, drops, etc.)
- EMI: Left-click no longer limited to only evolution/obtainment/fossil categories
- EMI: Right-click now correctly includes forward evolutions

## [1.28.4] - 2026-02-15

### Fixed
- GitHub Actions build failures due to Kotlin version conflicts
- Downgraded kotlin-for-forge from 5.11.0 to 5.6.0 (5.11.0 was compiled with Kotlin 2.3.0)
- Added dependency constraints to force Kotlin 2.1.0 for all transitive dependencies

## [1.28.3] - 2026-02-15

### Fixed
- Left-click on Pokémon in REI now shows recipes (where they appear/are used)
- Right-click on Pokémon in REI now shows usages (what uses them)
- Item click navigation also works for modded items that reference Pokémon

## [1.28.2] - 2026-02-15

### Fixed
- Reverted v1.28.0 and v1.28.1 evolution chain changes that broke REI completely
- Updated Kotlin to 2.1.0 (2.2.0 caused GitHub Actions build failures)

### Changed
- Restored individual A→B evolution displays (v1.27.2 system)
- Full evolution chains will be reimplemented in a future version with proper lazy loading

## [1.27.2] - 2026-02-15

### Fixed
- Evolution data and species info now sync from server to client (were only available locally before)
- LAN clients see full evolution chains, base stats, types, abilities, and all Pokédex info
- Server sync packet now carries spawns + evolutions + species info in one bundle
- Client no longer tries to load server-only data locally when server sync is available

## [1.27.1] - 2026-02-15

### Fixed
- LAN clients without CobbleDex no longer crash on connect (server now checks if client supports the packet before sending)
- Fixed codec to use explicit StreamCodec.of() with a 1MB read limit

## [1.27.0] - 2026-02-14

### Added
- Server-to-client spawn data networking: spawn locations now visible on LAN clients and dedicated server clients
- Server sends spawn data to connecting players via custom payload packets
- Works with both Fabric and NeoForge loaders

### Changed
- Mod now loads on both client and server sides (previously client-only)
- Fabric API promoted from recommended to required dependency

### Fixed
- Spawn data empty on non-host LAN clients due to Cobblemon's WORLD_SPAWN_POOL being server-side only

## [1.26.9] - 2026-02-14

### Fixed
- Spawn data now loads on LAN clients and dedicated server clients where Cobblemon's runtime spawn pool is unavailable
- Reads spawn data directly from mod files with full preset resolution when the runtime pool is empty

## [1.26.8] - 2026-02-14

### Changed
- Move names now colored by their type (fire moves in red, water in blue, etc.) instead of plain gray with a colored dot
- Power and accuracy displayed with labeled "Pow | Acc" column header and pipe separator for clarity

## [1.26.7] - 2026-02-14

### Fixed
- Type chart now shows ALL weaknesses and resistances instead of only one per multiplier level

## [1.26.6] - 2026-02-14

### Fixed
- Moves page overflow: egg, tutor, and TM moves now paginate properly instead of overflowing past the panel bottom
- Section headers accounted for in page line budget

## [1.26.5] - 2026-02-14

### Fixed
- Move names now display properly spaced ("Disarming Voice" instead of "Disarmingvoice") using Cobblemon's translation system
- Move names in evolution requirements also use translated names

## [1.26.4] - 2026-02-16

### Fixed
- EMI category tab tooltips now display translated names instead of raw keys
- Pokédex description now renders translated text instead of raw `cobblemon.species.<name>.desc` key
- Biome names in spawn conditions now translate properly for all languages

### Added
- 84 biome translation keys across all 28 language files (climate tags, terrain types, ocean temps, Minecraft biomes)

## [1.26.3] - 2026-02-15

### Added
- 95 missing translation keys added to all 27 non-English language files
- Proper translations for de, es, fr, it, pt, ja, ko, zh, ru, pl, nl, sv, tr, uk, th, vi, cs, hu
- Restored 5 accidentally deleted language files (de_de, cs_cz, el_cy, en_gb, en_pt)

## [1.26.2] - 2026-02-14

### Fixed
- All remaining hardcoded English strings now use translation keys (stats, natures, tooltips, label badges, moves prefix, hidden ability tag)
- Nature table columns now auto-size to fit translated text instead of using fixed pixel positions
- Stat bar labels dynamically measure translated text width so bars don't overlap labels in verbose languages
- Tooltip summary counts ("spawns", "evos", "drops", "obtainment") now translatable
- BST/EV yield text now translatable
- Moves page indicator uses existing translation key instead of hardcoded format
- Label badges (Legendary, Mythical, Ultra Beast, Paradox) now translatable

### Added
- 63 new translation keys: stat abbreviations, stat full names, 25 nature names, nature table headers, tooltip counts, BST/EV, hidden ability tag, moves level prefix, label badges

## [1.26.1] - 2026-02-14

### Added
- TM moves now shown in the Moves section with full type/category/power/accuracy details

## [1.26.0] - 2026-02-15

### Changed
- Moves section reworked: each move now shows type-colored indicator, damage category icon (⚔/◆/✦), power, and accuracy
- Egg and tutor moves display as individual rows with full metadata instead of comma-separated text
- Moves panel height increased from 220px to 240px to accommodate richer layout

## [1.25.1] - 2026-02-14

### Added
- Item → Pokémon reverse lookup for drops: clicking an item in REI now shows which Pokémon drop it

## [1.25.0] - 2026-02-14

### Changed
- Unified recipe viewer integration: all 10 categories now share a single DexCategory abstraction
- REI plugin reduced from 21 files to 1 generic adapter
- JEI plugin reduced from 21 files to 1 generic adapter
- EMI plugin reduced from 11 files to 1 generic adapter
- Total viewer code reduced from 61 files (~2,700 lines) to 3 files (~450 lines)

### Removed
- 50+ per-category duplicate files across REI/JEI/EMI (replaced by DexCategory.kt)

## [1.24.9] - 2026-02-14

### Fixed
- Mod now gracefully does nothing if accidentally placed on a dedicated server instead of crashing
- Translation system falls back to raw keys if client I18n is unavailable
- NeoForge init wrapped in safety catch for unexpected server-side class loading

## [1.24.8] - 2026-02-14

### Changed
- Mod is now explicitly client-only in both Fabric and NeoForge metadata
- Fabric environment set to "client" (was "*"), preventing server-side loading
- NeoForge dependencies set to side="CLIENT" (was "BOTH")
- NeoForge init() now only runs on client dist
- Translations use I18n directly instead of server-safe reflection wrapper

### Removed
- Fabric server entrypoint (CobbleDexFabric.kt) — init moved to client entrypoint
- ServerSafeI18n translation wrapper with server fallback (dead code for client-only mod)
- DataSource enum (vestigial from removed server sync, only had NONE/LOCAL)

## [1.24.7] - 2026-02-14

### Removed
- Dead `getClientDatapacksDir()` function (never called after runtime API switch)
- Dead `localDatapackScan` config field (always true, conditional never triggered)
- Dead `CONFIG_VERSION` constant (defined but never checked)
- Dead `SpeciesNameNormalizer.matches()` function (zero call sites)
- Dead `extraDatapacksDir` parameter from ObtainmentDataLoader (always null)
- Old config filename migration code (all users migrated long ago)
- Stale `bin/` directories containing old server sync compiled artifacts

### Fixed
- Misleading "server sync" text in retry log message

## [1.24.6] - 2026-02-14

### Changed
- Spawn data now reads directly from Cobblemon's runtime spawn pool instead of scanning mod JAR filesystems
- Fossil data now reads from Cobblemon's runtime Fossils registry instead of parsing JSON files
- Both loaders automatically include all datapacks and server modifications without manual scanning

### Removed
- JSON file scanning for spawn data (preset loading, ZIP datapack scanning, filesystem traversal)
- JSON file scanning for fossil data
- Leftover server/network source files from v1.24.5

## [1.24.5] - 2026-02-14

### Removed
- Server-to-client data sync system (all spawn, evolution, and species data now loads locally)
- Server-side networking (ServerDataManager, payload classes, packet handlers)
- DataSource.SERVER state and fingerprint comparison logic

### Changed
- Mod is now fully client-side — no server component runs at all
- Evolution and spawn data sourced from Cobblemon's client-synced registry and local mod JARs/datapacks

## [1.24.4] - 2026-02-14

### Fixed
- Evolutions and species data now properly sync to clients in multiplayer
- Evolution loader no longer fails entirely when a single species access throws
- Client stays receptive to server data when local evolution data is empty
- LAN players now receive server data sync (previously only dedicated servers synced)
- Packet drops are now logged for easier debugging

## [1.24.3] - 2026-02-14

### Fixed
- Type chart panel now dynamically sizes to fit all weaknesses, resistances, and immunities (dual-type Pokémon with many matchups were getting clipped)

## [1.24.2] - 2026-02-14

### Fixed
- REI, JEI, and EMI are no longer listed as dependencies on CurseForge/Modrinth (prevents launchers from force-installing them)

## [1.24.1] - 2026-02-14

### Fixed
- Text now renders with drop shadows for readability on light/vanilla UI backgrounds
- Bundled LumyMon altar/summoning data no longer appears when LumyMon is not installed

## [1.24.0] - 2026-02-16

### Added
- **Fossil Resurrection category**: Shows fossil item combos needed to resurrect each Pokémon
- **Type Effectiveness Chart category**: Per-species defensive matchups with weaknesses, resistances, and immunities
- **Natures reference table**: Standalone category listing all 25 natures with stat modifiers
- Shoulder-mountable indicator on Pokédex Info panel
- `showFossils`, `showTypeChart`, `showNatures` config toggles

## [1.23.0] - 2026-02-15

### Added
- **Moves category**: New REI/JEI/EMI tab showing level-up, egg, and tutor moves with pagination
- **EV Yields**: Stats panel now shows EV yield line (e.g., "EV: 1 Atk, 2 SpA")
- **Mega form entries**: Mega evolutions appear as separate entries with their own stats, types, and abilities
- Anti-condition rendering: time, dimension, weather, light level, and moon phase exclusions now displayed
- `showMoves` config toggle

### Fixed
- ItemStack cache mutation: cached stacks now return defensive copies
- Spawn JSON: `pokemon` field now handles both string and JSON object format (aspects support)
- Fakemon namespace: species with custom namespaces (e.g., `alatia:rockdrake`) now resolve correctly
- Anti-condition parser: 7 missing fields now extracted and merged (timeRange, dimensions, isRaining, isThundering, minLight, maxLight, moonPhase)

## [1.22.1] - 2026-02-14

### Fixed
- Base Stats: moved numeric stat values to the right side of the bar to prevent overlap with stat name labels

## [1.22.0] - 2026-02-13

### Changed
- **Rebranded from "Cobblemon Spawning REI" to "CobbleDex REI/EMI/JEI"** — reflects expanded scope as a full Pokédex companion
- Mod ID changed to `cobbledex-rei-emi-jei` (Fabric) / `cobbledex_rei_emi_jei` (NeoForge)
- Package renamed from `com.cobblemonrei` to `com.cobbledex`
- Config file automatically migrates from `cobblemon-spawning-rei.json` to `cobbledex-rei-emi-jei.json`
- Debug output folder renamed to `cobbledex-debug/`
- All translation keys updated to `cobbledex-rei-emi-jei.*` prefix

## [1.21.0] - 2026-02-13

### Added
- Base Stats category — view HP, Attack, Defense, Sp.Atk, Sp.Def, Speed as colored stat bars with BST (REI/JEI/EMI)
- Pokédex Info category — abilities (with HA marked), egg groups, gender ratio, breeding, training details, Pokédex description (REI/JEI/EMI)
- `showStats` and `showPokedexInfo` config options to toggle the new categories

### Changed
- Trimmed tooltip: removed BST, abilities, egg groups (now in dedicated categories). Tooltip shows type, label badges, and compact category counts on one line

## [1.20.0] - 2026-02-14

### Changed
- Replaced all reflection-based evolution requirement parsing with typed API access
- Evolution data now uses Cobblemon's public getters instead of private field access
- Fixes 3 previously broken requirement types: TimeRange, Biome, Advancement

### Removed
- All `java.lang.reflect` usage from evolution data loading
- `extractField()`, `findField()`, `extractReadableValue()`, `extractItemFromRequirement()` reflection helpers

## [1.19.0] - 2026-02-14

### Added
- Item Drops category — view what items each Pokémon drops when defeated (REI/JEI/EMI)
- Enriched tooltips: BST, abilities (with hidden ability marked), egg groups, evolution count, drop count
- Label badges in tooltips for Legendary, Mythical, Ultra Beast, and Paradox Pokémon
- `showDrops` config option to toggle the Item Drops category
- Species data now includes base stats, abilities, egg groups, labels, drops, and more

## [1.18.3] - 2026-02-13

### Fixed
- Dedicated server crash: `NoClassDefFoundError: I18n` — translation helper now falls back to bundled en_us.json on servers where client classes aren't available
- Server-side spawn data loading and sync now works correctly for multiplayer

## [1.18.2] - 2026-02-12

### Added
- 27 new language files — mod now supports 28 languages total
- Languages: English (US/UK/Pirate), German, Spanish (Spain/Mexico), French (France/Canada), Italian, Portuguese (Brazil/Portugal), Dutch, Polish, Czech, Hungarian, Swedish, Turkish, Greek, Russian, Ukrainian, Japanese, Korean, Chinese (Simplified/Traditional/Hong Kong), Thai, Vietnamese, Esperanto

## [1.18.1] - 2026-02-12

### Fixed
- Form aspects now display cleanly — "Alolan" instead of "Region Bias=alola", "Female" instead of "gender=female"
- Obtainment items and blocks now show their actual in-game names from the registry instead of ID-derived text
- Biome tag qualifiers no longer leak through (e.g., "Dry" instead of "Dry/overworld")
- Path separators in structure/block IDs cleaned up for display
- Pokémon type names in tooltips now use Cobblemon's own translated names

### Removed
- Dead `BUCKET_LABELS` and `PRESET_LABELS` maps superseded by localized functions
- Unused `formatItemId` method in ObtainmentInfo

## [1.18.0] - 2026-02-12

### Added
- Complete localization system with ~190 translation keys
- All user-facing text now goes through Minecraft's language system
- Resource pack authors can override any string via lang files
- Shared measurement/rendering text helpers to maintain panel sizing integrity

### Changed
- Category titles, spawn conditions, evolution requirements, obtainment methods, and diagnostic commands all use translatable strings
- Bucket labels, preset labels, and dimension names pulled from lang file
- Weather condition checks use data properties instead of comparing translated strings

## [1.17.1] - 2026-02-11

### Fixed
- Tooltip spawn count now accurately reflects the number of spawn displays you'll actually see when clicking (was sometimes showing higher due to using pre-deduplication count)

## [1.17.0] - 2026-02-11

### Fixed
- **ZIP datapack scanning now actually works** - Fixed config migration to auto-enable `localDatapackScan` for users with old config files
- Diagnostic runtime check now properly compares species names using normalization ("great tusk" vs "greattusk" no longer shows as mismatch)

### Added  
- Extensive debug logging for datapack scanning - logs each ZIP/directory scanned and spawn entries found
- Config auto-migration: old configs with `localDatapackScan: false` are now automatically updated

## [1.16.1] - 2026-02-11

### Changed
- Diagnostic dump files now output to `spawningrei-debug/` folder instead of cluttering the game directory root

## [1.16.0] - 2026-02-11

### Fixed
- **ZIP datapack scanning**: Datapacks stored as ZIP files (like COBBLEVERSE-DP-v17-CF.zip) are now correctly scanned for spawn data
- Previously only extracted/directory datapacks were read, causing Manaphy, Paradox Pokémon, and many legendaries to show as "missing spawn data"
- Local datapack scanning now enabled by default (was previously disabled)

### Changed
- Refactored parseSpawnFile to separate JSON parsing logic into reusable parseSpawnJson function
- More robust "enabled" field parsing (handles boolean strings)

## [1.15.0] - 2026-02-11

### Fixed
- **Major fix**: Pokémon with special characters in names (Mr. Mime, Farfetch'd, Nidoran-F, Porygon-Z, Ho-Oh, Type: Null, etc.) now correctly show spawn data
- Species name normalization now handles the mismatch between Cobblemon display names and spawn file names
- Covers all special cases: apostrophes, periods, hyphens, colons, spaces in names
- Also affects Gen 9 Paradox Pokémon with spaces (Great Tusk, Iron Treads, etc.) and Tapu guardians

### Added
- Diagnostic commands for troubleshooting data coverage:
  - `/spawningrei stats` - quick summary in chat
  - `/spawningrei missing` - lists Pokémon missing spawn/obtainment data
  - `/spawningrei dump` - writes full diagnostic report to file
  - `/spawningrei reload` - forces data reload

## [1.14.0] - 2026-02-11

### Changed
- All panels (spawn, evolution, obtainment) now dynamically resize to fit their content — no more fixed 180×200 boxes
- Width expands to accommodate long species names, biome lists, evolution requirements, and obtainment descriptions
- Height grows to show every line of text — biomes, conditions, specials, exclusions, and weight modifiers are never cut off
- Removed all hardcoded dimension constants from REI, JEI, and EMI category/recipe files
- New centralized DisplayLayout engine measures every recipe and computes exact panel dimensions
- EMI gets true per-recipe sizing (each recipe has its own measured width/height)
- REI and JEI use max-measured dimensions across all recipes so every display fits perfectly
- Eliminated all text clipping — clipToWidth removed from all rendering paths, replaced with full word-wrapping
- Removed biome line cap (was limited to 3 lines) — all biomes now display in full

## [1.13.0] - 2026-02-11

### Fixed
- All text rendering now uses pixel-width measurement instead of character-count estimates, eliminating clipped/truncated names, rarity labels, biome lists, and requirement text across spawn, evolution, and obtainment pages
- Duplicate evolution entries no longer appear on the same page (form changes like Thundurus no longer double-counted)
- Ghost "Evolution 1/…" overlay text no longer bleeds through adjacent cards (scissor clipping added to evolution display)
- "Built-in" source label no longer clipped at bottom of obtainment cards (footer margin uses actual font height)
- Species name and method/bucket labels no longer overlap when text is long
- Weight modifier condition text no longer truncated at arbitrary character limits

### Changed
- Evolution cards now show a "→" direction indicator between from/to names for clearer reading
- Evolution branch counter ("Evo 1/3") moved from content area to bottom-right corner, preventing visual interference
- Obtainment counter hidden when there's only 1 entry (no more showing "1/1")
- Obtainment method name color toned down from gold to warm tan to avoid looking like a clickable link
- Location labels improved: "On block:" → "Spawns on:", "Near block:" → "Near:", "Near structure:" → "Structure:", "Block:" → "Use:" in obtainment
- Spawn weight label shortened from "Weight:" to "Wt:" to give more room to context parts on the same line
- Context parts and weight now properly share their line with pixel-aware width allocation
- Biome lists, conditions, specials, exclusions, and notes all clip cleanly with ellipsis at actual pixel boundaries

## [1.12.3] - 2026-02-11

### Fixed
- Evolution items now appear correctly — switched from reflection to typed access for vanilla ItemPredicate, fixing Loom obfuscation remapping mismatch at runtime

## [1.12.2] - 2026-02-11

### Fixed
- Evolution items now correctly extracted from Cobblemon's vanilla ItemPredicate API
- HeldItem and OwnerHoldsItem requirements scan all field types for item data
- Generic ContextEvolution fallback handles ItemPredicate in addition to RegistryLikeCondition

## [1.12.1] - 2026-02-11

### Fixed
- NeoForge crash on startup caused by static/instance event subscriber mismatch with Kotlin For Forge 5.11.0

## [1.12.0] - 2026-02-11

### Added
- Evolution items (Fire Stone, Metal Coat, etc.) now appear as actual clickable item slots in REI, JEI, and EMI
- Clicking an evolution item opens its crafting recipe or usage info
- Item names displayed using Minecraft's localized names

### Changed
- Evolution display height increased for better readability
- Polished evolution layout with separator line, better spacing, and branch indicator
- Species names positioned closer to their Pokemon icons
- Non-item requirements (level, friendship, time, etc.) render below item slots

## [1.11.1] - 2026-02-11

### Fixed
- EMI plugin now registers all species (including evolution/obtainment-only Pokémon) instead of only those with spawn data
- Form-specific evolutions (e.g. Alolan forms) now appear under the base species in recipe viewers
- NeoForge packet sending checks connection state before dispatching
- Data loading waits up to 10s for lock instead of silently skipping when another load is in progress
- PokemonItemCache now clears on data reload and disconnect to prevent stale entries
- SpawnDataLoader mod root cache invalidated on reload
- Config only written to disk when file is missing, not on every startup
- intersectLists uses HashSet for O(n) instead of O(n×m) filtering

## [1.11.0] - 2026-02-12

### Changed
- Unified recipe building logic into shared RecipeBuilder — REI, JEI, and EMI all use identical spawn/evolution/obtainment construction
- REI categories now use shared draw helpers instead of duplicated widget code (~400 LOC removed)
- All recipe data classes (SpawnDisplay, JeiSpawnRecipe, EmiSpawnRecipe, etc.) wrap shared RecipeData types
- PokemonRef.displayName now delegates to titleCase() for consistent formatting
- README and docs updated to treat REI, JEI, and EMI as equally supported

### Fixed
- Evolution requirement type extraction now uses reflection-based className matching instead of direct type imports, fixing compilation against Cobblemon 1.7.x

### Removed
- Dead SpawnInfo.bucketColor property (SpawnDisplayHelper.bucketColor() is the single source)
- Dead SpawnInfo.formattedBiomes and SpawnAntiCondition.formattedBiomes properties
- Duplicated private recipe builder methods from all three plugin files

## [1.10.6] - 2026-02-11

### Fixed
- Held item names not displaying in evolution conditions (e.g. "Hold item" instead of "Hold Sachet")
- Field reflection now walks full class hierarchy to reach inherited fields like RegistryLikeIdentifierCondition.identifier

## [1.10.5] - 2026-02-11

### Fixed
- Evolution conditions involving items/trade partners showing raw Java object references instead of proper names (e.g. `PokemonProperties@342f4abf`)
- Held item requirements (like Sachet for Spritzee) now correctly display the item name
- Trade evolutions with specific partner requirements now show "Trade with [species]" instead of garbage text
- Deep extraction of item identifiers from NbtItemPredicate, RegistryLikeCondition, and PokemonProperties objects

## [1.10.4] - 2026-02-11

### Fixed
- EMI crash via JEMI compat layer on NeoForge caused by unsanitized species names in PokemonRef.identifier (Farfetch'd)
- Affects both JEI ingredient lookups and REI entry identifiers
## [1.10.3] - 2026-02-11

### Fixed
- EMI crash on load caused by Unicode characters in species names (e.g. Farfetch’d) producing invalid ResourceLocation paths
- Applied path sanitization to all recipe/display IDs across REI, EMI, and obtainment displays

## [1.10.2] - 2026-02-11

### Fixed
- EMI critical exception on load caused by EMI's REI compatibility layer loading the custom PokemonEntryType; REI plugin now detects EMI and defers to the native EMI plugin

## [1.10.1] - 2026-02-12

### Fixed
- NeoForge crash on load due to @JvmStatic on event handler methods

## [1.10.0] - 2026-02-12

### Added
- Special Obtainment category for REI, JEI, and EMI showing how to obtain legendary/mythical Pokémon via altars, shrines, resurrection machines, and transformations
- Bundled data for 25 LumyMon legendary obtainment methods (bird trio, tower duo, regi trio, eon duo, weather trio, mythicals, Calyrex line, Mewtwo, Type: Null, Shadow Lugia)
- Generic `data/<namespace>/special_obtainment/*.json` format for datapacks to add custom obtainment entries
- `showObtainment` config option (default: true) to toggle the obtainment category
- `cobbleverse:custom_spawn` fake biome now displays as "Altar/Special Only" instead of raw ID
- Pokémon tooltips now show special obtainment count

## [1.9.0] - 2026-02-11

### Fixed
- CurseForge and Modrinth now receive correct environment metadata (client required, server optional)
- Publish workflow sets Modrinth project-level client_side/server_side on every release
- NeoForge JAR metadata now includes logoFile field

## [1.8.7] - 2026-02-11

### Changed
- Data is now kept as a warm cache on disconnect instead of being cleared, so REI/JEI/EMI entries are available instantly on reconnect
- First data load attempt triggers immediately on the first client tick instead of waiting 5 seconds
- Dynamic display generators serve cached/partial data while fresh data loads in the background

## [1.8.6] - 2026-02-10

### Fixed
- Clicking a specific Pokémon in REI no longer shows all Pokémon paginated; only the selected species' spawn/evolution info is displayed

## [1.8.5] - 2026-02-10

### Fixed
- Fingerprint now hashes full serialized content instead of just key counts, preventing stale data when server datapacks modify spawn conditions without adding/removing species
- Data writes (local load, server apply, disconnect clear) are now synchronized via ReentrantLock, eliminating race conditions between async local load and server data arrival
- Async data loading uses a single-thread ExecutorService with cancellation support instead of spawning unbounded raw threads
- Server chunk delivery retries failed chunks up to 3 times (1-second delay between retries) instead of silently abandoning the entire sync
- Client-side chunk receiver now times out after 30 seconds of stalled transfer and falls back to local data

## [1.8.4] - 2026-02-10

### Changed
- Extracted shared PokemonRef interface for PokemonEntry (REI) and PokemonIngredient (JEI)
- Consolidated 3x duplicate spawn merge/sort/bucket logic into SpawnDisplayHelper.buildSortedSpawns()
- Consolidated 2x duplicate spawn rendering into SpawnDisplayHelper.drawSpawnDetails()
- Consolidated 2x duplicate evolution text rendering into SpawnDisplayHelper.drawEvolutionText()
- Consolidated 2x duplicate tooltip logic into SpawnDisplayHelper.buildPokemonTooltipLines()
- Extracted context line assembly into SpawnDisplayHelper.buildContextParts()
- Removed passthrough bucket delegation functions from SpawnCategory, JeiSpawnCategory, EmiSpawnRecipe

## [1.8.3] - 2025-07-23

### Fixed
- Data reload (PARTIAL → FULLY_LOADED) now runs on a background thread instead of freezing the game
- Shared PokemonItemCache eliminates 3x duplicate PokemonItem.from() calls across REI/JEI/EMI
- EMI spawn and evolution recipes use lazy stack resolution instead of eager constructor init
- Registration no longer creates disposable renderer instances whose caches are immediately discarded
- Mod root path discovery (reflection-based) is cached instead of re-running on every data reload

## [1.8.2] - 2025-07-23

### Fixed
- Silent exception swallowing in 12+ catch blocks across SpawnDataLoader, EvolutionDataLoader, REI, and EMI
- Platform discovery (Fabric/NeoForge) now catches ClassNotFoundException specifically and logs unexpected failures
- Reflection-based evolution field extraction distinguishes expected NoSuchFieldException from real errors
- Species loading failures in EvolutionDataLoader now warn instead of silently returning empty
- EMI stack creation failures now log once per species for diagnosability
- REI entry type registration failure now logs instead of silently swallowing

## [1.8.1] - 2025-07-23

### Fixed
- Species permanently hidden when PokemonItem resolution fails during early loading (null-cache bug)
- Silent exception swallowing in spawn data indexing and NeoForge payload dispatch
- Reflection-based datapack directory lookup replaced with stable PlatformHelper call
- SpawnSyncPayload codec read limit now tied to DataSerializer chunk size constant

### Changed
- Consolidated ~600 lines of duplicated display helpers across REI/JEI/EMI into shared SpawnDisplayHelper
- Removed dead code: unused loader methods, debug helpers, and cache invalidation

### Removed
- Dead `loadFromCobblemonJar`, `findCobblemonDataPath`, `findCobblemonRootPath` methods
- Dead `getMissingModelCount`, `hasMissingModel`, `invalidateCaches` methods
- Triplicated MergedSpawn/mergeVariantSpawns/spawnMergeKey across all three plugins
- Triplicated buildConditions/buildSpecials/buildExclusionLines/formatWeight/clip/wrapText across spawn categories
- Triplicated clip/wrapReqText across evolution categories

## [1.8.0] - 2025-07-22

### Added
- Full EMI integration — spawn locations and evolution categories with PokemonItem stacks
- EMI sidebar entries for all Pokémon species with component-based comparison
- EMI recipe tree linking for spawn and evolution lookups
- NeoForge EMI discovery via @EmiEntrypoint annotation

## [1.7.1] - 2025-07-22

### Fixed
- JEI evolution arrow using removed gui_vanilla.png texture (now uses proper API arrow)
- Duplicate JEI plugin registration on NeoForge
- Missing recipe catalysts — spawn/evolution categories now browsable from JEI
- Dead imports and deprecated API usage in JEI ingredient helper

## [1.7.0] - 2025-07-22

### Added
- Full JEI integration — spawn locations and evolution categories with Pokemon ingredients
- JEI/EMI/REI multiloader support: single JAR works with any recipe viewer (or none)
- JEI ingredient type for Pokémon with sprite rendering and tooltips

### Changed
- REI, JEI, and EMI are now optional dependencies — mod loads without any recipe viewer
- NeoForge recipe viewer plugins isolated to prevent crashes when a viewer isn't installed

## [1.6.0] - 2026-02-10

### Changed
- Redesigned server sync: local-first architecture — clients load data immediately, never blocked waiting for server
- Reduced packet chunk size from 900KB to 32KB to prevent connection drops
- Server now sends a tiny fingerprint packet first; full sync only if data differs
- Data chunks are sent 1-per-tick with a 5-second delay instead of all at once
- Removed `awaitingServerData` blocking — clients always have data in REI

### Fixed
- "Connection Lost" crash caused by oversized custom_payload packets on dedicated servers
- Clients seeing empty REI entries when server sync failed silently

## [1.5.1] - 2026-02-09

### Fixed
- Fabric/NeoForge PlatformHelperImpl package paths for Architectury @ExpectPlatform resolution

## [1.5.0] - 2026-02-09

### Added
- Server-to-client spawn data synchronization via compressed chunked packets
- DynamicDisplayGenerator for both spawn and evolution categories
- Config system (`cobblemon-spawning-rei.json`) with debugMode, showSpawnWeights, showEvolutions, localDatapackScan
- "Server" indicator label on displays when viewing server-synced data
- Disconnect cleanup: data clears on server disconnect for next session
- PlatformHelper abstraction for cross-platform config/networking
- REI bookmark/favorites support for Pokémon entries (entry serializer)
- Pokémon type names included in REI search text
- Enhanced tooltips showing type, catch rate, and spawn count
- Missing species logged to `config/cobblemon-spawning-rei-missing-models.txt`
- REI declared as required dependency in NeoForge metadata
- Network decompression size limit (50MB) for safety
- Evolution requirement parsing warns at load time when data extraction fails

### Changed
- Mod environment changed from client-only to both sides to support server-side data loading
- Config options now functional (showSpawnWeights, showEvolutions, localDatapackScan, debugMode)
- Display generation cached by data version for better performance
- Spawn file walking limited to depth 10
- Client datapacks path lookup cached across reloads

### Removed
- Unused PokemonSpriteManager (dead code from pre-1.1.0 renderer)
- Empty CobblemonREICommonPlugin stub
- Unused SubscribeEvent import in NeoForge entrypoint

---

## [1.4.8] - 2026-02-10

### Fixed
- TOCTOU race in SpawnDataIndex.loadAll() — replaced volatile boolean with AtomicBoolean.compareAndSet
- Sprite cache poisoning: cache key now preserves separators (lowercase only) so findSprite can try different styles
- Composite condition biome merge uses intersection (AND semantics) instead of union
- DebugLog.reset() now called at reload start so stale once-keys don't suppress new warnings
- tickReloadCheck throttled to every 100 ticks instead of every tick

### Removed
- Fabricated SpeciesBasicInfo for unknown species — callers already handle null

### Added
- EvolutionDataLoader logs when reflection extracts no data for a requirement type

---

## [Unreleased]

### Added
- Automated CurseForge and Modrinth publishing via GitHub Actions

---

## [1.4.7] - 2026-02-09

### Fixed
- Resource leak: spawn/preset JSON file handles now properly closed after parsing
- Race condition: concurrent data loading guarded to prevent duplicate loads
- Thread safety: all shared caches use concurrent collections (DebugLog, SpriteManager, EntryRenderer)
- Silent failures: preset scanning errors now logged instead of swallowed
- Duplicate mod root paths deduplicated to eliminate redundant I/O
- moonPhase field handles both string and integer JSON values
- Cobblemon dependency version aligned to >=1.7.1 in both Fabric and NeoForge metadata
- Removed dead imports and unused fields in PokemonSpriteManager

## [1.4.6] - 2026-02-09

### Fixed
- Pokemon species with underscores now display properly (Mr Mime, Tapu Koko, etc.)
- All multi-word names use full title case ("Cherry Grove" not "Cherry grove")
- Biome and structure names from any mod namespace now display correctly
- Evolution stat comparisons, moon phases, dimensions, and advancement names fully capitalized
- Defeat/party member targets no longer show raw underscored IDs
- Spawn preset labels, form aspect names, and time range fallbacks normalized

## [1.4.5] - 2026-02-09

### Changed
- Level range text color increased to bright cyan for improved visibility

## [1.4.4] - 2026-02-09

### Changed
- Level range text now uses cyan color for better visibility

## [1.4.3] - 2026-02-09

### Changed
- Pokemon icon and name now have dedicated row in spawn location display
- Level and rarity moved to separate row below for better readability

## [1.4.2] - 2026-02-09

### Changed
- Set up automated publishing workflow for CurseForge and Modrinth releases

## [1.2.4] - 2026-02-08

### Fixed
- Pokemon rendering now matches Cobblemon PC slot exactly (proportional scaling)
- Model anchor at slot top instead of center — no more cut-off bottoms
- Per-species profileScale/profileTranslation handles all sizes automatically

## [1.2.3] - 2026-02-08

### Fixed
- Species list now built from all sources (spawns, evolutions, runtime API)
- No longer relies solely on runtime API for species discovery
- Pokemon entries now appear even if Cobblemon species aren't loaded yet

## [1.2.2] - 2026-02-08

### Fixed
- Pokemon rendering now uses Cobblemon's actual slot rendering approach
- Proper scale values (2.5x prescale, 4.5f profile scale) matching Cobblemon's UI
- Pokemon models properly sized and centered in REI slots

## [1.1.2] - 2026-02-08

### Fixed
- Changed deprecated "rei" entrypoint to "rei_common" to suppress deprecation warning

## [1.1.1] - 2026-02-08

### Fixed
- Pokémon entries not appearing in REI search due to entry type registration lifecycle issue
- Entry type now registered via proper registerEntryTypes() callback
- Added fallback text rendering when 3D model not yet loaded
- Entry type re-registered on each REI reload phase to prevent stale references

## [1.1.0] - 2025-06-10

### Added
- Custom REI EntryType for Pokémon with 3D model rendering via Cobblemon's drawProfilePokemon
- Pokémon entries in REI sidebar (searchable by species name)
- Tooltips on Pokémon entries showing name and dex number
- REI slot widgets in spawn/evolution categories for interactive Pokémon icons

### Changed
- Spawn displays now use PokemonEntry instead of placeholder grass blocks
- Evolution displays now use PokemonEntry for from/to species (R-click/U-click works)
- Categories use REI slot widgets for Pokémon icons instead of manual sprite blitting

### Removed
- PokemonSpriteManager dependency from category renderers

## [1.0.0] - 2026-02-08

### Added
- REI integration for Cobblemon spawn data (biomes, rarity, time, weather, level range)
- REI integration for Cobblemon evolution chains (R-click: evolves into, U-click: evolves from)
- Client-side data loading from Cobblemon JAR (no server needed)
- Spawn info category with color-coded rarity, biome lists, conditions
- Evolution category showing requirements (level, item, trade, friendship, time, etc.)
- Fabric + NeoForge support via Architectury
