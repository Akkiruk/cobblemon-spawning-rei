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
