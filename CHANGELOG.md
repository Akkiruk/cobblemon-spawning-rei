# Changelog

All notable changes to Cobblemon Spawning REI will be documented in this file.
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
