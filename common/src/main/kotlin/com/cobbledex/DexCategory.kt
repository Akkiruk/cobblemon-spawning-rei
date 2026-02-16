package com.cobbledex

import com.cobbledex.config.CobbleDexConfig
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

enum class SlotRole { INPUT, OUTPUT }

data class PokemonSlotDef(
    val species: String,
    val aspects: Set<String> = emptySet(),
    val x: Int,
    val y: Int,
    val role: SlotRole,
    val disableBackground: Boolean = true,
    val disableHighlight: Boolean = true,
)

data class ItemSlotDef(
    val itemId: String,
    val x: Int,
    val y: Int,
    val role: SlotRole,
)

class RecipeHandle(
    val recipeIdPath: String,
    val inputSpecies: List<String>,
    val outputSpecies: List<String>,
    layoutFactory: () -> PanelLayout,
    private val _width: (() -> Int)? = null,
    private val _height: (() -> Int)? = null,
    private val _slots: (PanelLayout) -> Slots = { Slots() },
) {
    data class Slots(
        val pokemon: List<PokemonSlotDef> = emptyList(),
        val items: List<ItemSlotDef> = emptyList(),
        val arrowX: Int = 0,
        val arrowY: Int = 0,
        val hasArrow: Boolean = false,
    )

    val layout: PanelLayout by lazy(LazyThreadSafetyMode.NONE, layoutFactory)
    val width: Int get() = _width?.invoke() ?: layout.width
    val height: Int get() = _height?.invoke() ?: layout.height
    val slots: Slots by lazy(LazyThreadSafetyMode.NONE) { _slots(layout) }
}

interface DexCategory {
    val id: String
    val titleKey: String
    val icon: Item
    val maxSize: () -> DisplayLayout.PanelSize
    val supportsRecipeTree: Boolean get() = false

    fun isEnabled(config: CobbleDexConfig): Boolean
    fun buildAllRecipes(): List<RecipeHandle>
    fun buildRecipesFor(species: String): List<RecipeHandle>
    fun buildUsagesFor(species: String): List<RecipeHandle> = emptyList()
    fun buildRecipesForItem(itemId: String): List<RecipeHandle> = emptyList()

    companion object {
        val ALL: List<DexCategory> = listOf(
            SpawnDex, EvolutionDex, ObtainmentDex, DropDex,
            StatsDex, MovesDex, PokedexInfoDex, PokemonDescriptionDex, FossilDex,
            TypeChartDex, NatureDex
        )
    }
}

private fun pokemonInput(species: String, x: Int = 8, y: Int = 3) =
    PokemonSlotDef(species, emptySet(), x, y, SlotRole.INPUT)

private fun pokemonOutput(species: String, x: Int = 8, y: Int = 3) =
    PokemonSlotDef(species, emptySet(), x, y, SlotRole.OUTPUT)

// ----- Spawn -----

object SpawnDex : DexCategory {
    override val id = "spawns"
    override val titleKey = "category.cobbledex-rei-emi-jei.spawn"
    override val icon: Item = Items.GRASS_BLOCK
    override val maxSize = DisplayLayout::getMaxSpawnSize
    override fun isEnabled(config: CobbleDexConfig) = true

    override fun buildAllRecipes(): List<RecipeHandle> {
        val all = mutableListOf<RecipeHandle>()
        for ((species, spawns) in SpawnDataIndex.spawnsBySpecies) {
            if (spawns.isEmpty()) continue
            all.addAll(RecipeBuilder.buildSpawnRecipes(species, spawns).map(::toHandle))
        }
        return all
    }

    override fun buildRecipesFor(species: String): List<RecipeHandle> {
        val spawns = SpawnDataIndex.getSpawnsFor(species)
        if (spawns.isEmpty()) return emptyList()
        return RecipeBuilder.buildSpawnRecipes(species, spawns).map(::toHandle)
    }

    private fun toHandle(d: SpawnRecipeData) = RecipeHandle(
        recipeIdPath = "spawn/${sanitizePath(d.speciesName)}/${sanitizePath(d.spawn.bucket)}/${d.bucketIndex}",
        inputSpecies = emptyList(),
        outputSpecies = listOf(d.speciesName),
        layoutFactory = { SpawnDisplayHelper.buildSpawnLayout(d.speciesName, d.spawn, d.mergedFormVariants, d.bucketIndex, d.bucketTotal) },
        _slots = { RecipeHandle.Slots(pokemon = listOf(pokemonInput(d.speciesName))) },
    )
}

// ----- Evolution -----

object EvolutionDex : DexCategory {
    override val id = "evolution"
    override val titleKey = "category.cobbledex-rei-emi-jei.evolution"
    override val icon: Item = Items.EXPERIENCE_BOTTLE
    override val maxSize = DisplayLayout::getMaxEvolutionSize
    override fun isEnabled(config: CobbleDexConfig) = config.showEvolutions

    override fun buildAllRecipes(): List<RecipeHandle> {
        val chains = EvolutionChainBuilder.getAllChains()
        return chains.values.map(::toChainHandle)
    }

    override fun buildRecipesFor(species: String): List<RecipeHandle> {
        val chain = EvolutionChainBuilder.getChainFor(species) ?: return emptyList()
        return listOf(toChainHandle(chain))
    }

    override fun buildUsagesFor(species: String): List<RecipeHandle> {
        val chain = EvolutionChainBuilder.getChainFor(species) ?: return emptyList()
        return listOf(toChainHandle(chain))
    }

    private fun toChainHandle(chain: EvolutionChainBuilder.ChainNode): RecipeHandle {
        val allSpecies = EvolutionChainBuilder.collectAllSpecies(chain).toList()
        return RecipeHandle(
            recipeIdPath = "evolution/chain_${sanitizePath(chain.species)}",
            inputSpecies = allSpecies,
            outputSpecies = allSpecies,
            layoutFactory = { SpawnDisplayHelper.buildEvolutionChainLayout(chain) },
            _slots = { RecipeHandle.Slots(pokemon = listOf(pokemonInput(chain.species))) },
        )
    }
}

// ----- Obtainment -----

object ObtainmentDex : DexCategory {
    override val id = "obtainment"
    override val titleKey = "category.cobbledex-rei-emi-jei.obtainment"
    override val icon: Item = Items.NETHER_STAR
    override val maxSize = DisplayLayout::getMaxObtainmentSize
    override fun isEnabled(config: CobbleDexConfig) = config.showObtainment

    override fun buildAllRecipes() =
        RecipeBuilder.buildAllObtainmentRecipes().map(::toHandle)

    override fun buildRecipesFor(species: String): List<RecipeHandle> {
        val obtainments = SpawnDataIndex.getObtainmentFor(species)
        if (obtainments.isEmpty()) return emptyList()
        return RecipeBuilder.buildObtainmentsFor(species, obtainments).map(::toHandle)
    }

    private fun toHandle(d: ObtainmentRecipeData) = RecipeHandle(
        recipeIdPath = "obtainment/${sanitizePath(d.speciesName)}/${sanitizePath(d.obtainment.method)}/${d.entryIndex}",
        inputSpecies = emptyList(),
        outputSpecies = listOf(d.speciesName),
        layoutFactory = { SpawnDisplayHelper.buildObtainmentLayout(d.speciesName, d.obtainment, d.entryIndex, d.entryTotal) },
        _slots = { RecipeHandle.Slots(pokemon = listOf(pokemonOutput(d.speciesName, 8, 3))) },
    )
}

// ----- Drops -----

object DropDex : DexCategory {
    override val id = "drops"
    override val titleKey = "category.cobbledex-rei-emi-jei.drops"
    override val icon: Item = Items.DIAMOND
    override val maxSize = DisplayLayout::getMaxDropSize
    override val supportsRecipeTree = true
    override fun isEnabled(config: CobbleDexConfig) = config.showDrops

    override fun buildAllRecipes() =
        RecipeBuilder.buildAllDropRecipes().map(::toHandle)

    override fun buildRecipesFor(species: String) =
        RecipeBuilder.buildDropsFor(species).map(::toHandle)

    override fun buildRecipesForItem(itemId: String) =
        RecipeBuilder.buildDropRecipesForItem(itemId).map(::toHandle)

    private fun toHandle(d: DropRecipeData) = RecipeHandle(
        recipeIdPath = "drops/${sanitizePath(d.speciesName)}",
        inputSpecies = listOf(d.speciesName),
        outputSpecies = emptyList(),
        layoutFactory = { SpawnDisplayHelper.buildDropLayout(d.speciesName, d.drops) },
        _slots = { _ ->
            RecipeHandle.Slots(
                pokemon = listOf(pokemonInput(d.speciesName)),
                items = d.drops.mapIndexed { i, drop ->
                    ItemSlotDef(drop.itemId, 8, 34 + i * 20, SlotRole.OUTPUT)
                },
            )
        },
    )
}

// ----- Stats -----

object StatsDex : DexCategory {
    override val id = "stats"
    override val titleKey = "category.cobbledex-rei-emi-jei.stats"
    override val icon: Item = Items.BOOK
    override val maxSize = DisplayLayout::getMaxStatsSize
    override val supportsRecipeTree = true
    override fun isEnabled(config: CobbleDexConfig) = config.showStats

    override fun buildAllRecipes() =
        RecipeBuilder.buildAllStatsRecipes().map(::toHandle)

    override fun buildRecipesFor(species: String): List<RecipeHandle> {
        val r = RecipeBuilder.buildStatsFor(species) ?: return emptyList()
        return listOf(toHandle(r))
    }

    private fun toHandle(d: StatsRecipeData): RecipeHandle {
        val size = maxSize()
        return RecipeHandle(
            recipeIdPath = "stats/${sanitizePath(d.speciesName)}",
            inputSpecies = listOf(d.speciesName),
            outputSpecies = emptyList(),
            layoutFactory = { SpawnDisplayHelper.buildStatsLayout(d.speciesName, d.baseStats, d.baseStatTotal, d.primaryType, d.secondaryType, evYield = d.evYield) },
            _width = { size.width },
            _height = { size.height },
            _slots = { RecipeHandle.Slots(pokemon = listOf(pokemonInput(d.speciesName))) },
        )
    }
}

// ----- Moves -----

object MovesDex : DexCategory {
    override val id = "moves"
    override val titleKey = "category.cobbledex-rei-emi-jei.moves"
    override val icon: Item = Items.PAPER
    override val maxSize = DisplayLayout::getMaxMovesSize
    override val supportsRecipeTree = true
    override fun isEnabled(config: CobbleDexConfig) = config.showMoves

    override fun buildAllRecipes() =
        RecipeBuilder.buildAllMovesRecipes().map(::toHandle)

    override fun buildRecipesFor(species: String) =
        RecipeBuilder.buildMovesFor(species).map(::toHandle)

    private fun toHandle(d: MovesRecipeData): RecipeHandle {
        val size = maxSize()
        return RecipeHandle(
            recipeIdPath = "moves/${sanitizePath(d.speciesName)}_${d.pageIndex}",
            inputSpecies = listOf(d.speciesName),
            outputSpecies = emptyList(),
            layoutFactory = { SpawnDisplayHelper.buildMovesLayout(d) },
            _width = { size.width },
            _height = { size.height },
            _slots = { RecipeHandle.Slots(pokemon = listOf(pokemonInput(d.speciesName))) },
        )
    }
}

// ----- Pokédex Info -----

object PokedexInfoDex : DexCategory {
    override val id = "pokedex_info"
    override val titleKey = "category.cobbledex-rei-emi-jei.pokedex_info"
    override val icon: Item = Items.WRITABLE_BOOK
    override val maxSize = DisplayLayout::getMaxPokedexInfoSize
    override val supportsRecipeTree = true
    override fun isEnabled(config: CobbleDexConfig) = config.showPokedexInfo

    override fun buildAllRecipes() =
        RecipeBuilder.buildAllPokedexInfoRecipes().map(::toHandle)

    override fun buildRecipesFor(species: String): List<RecipeHandle> {
        val r = RecipeBuilder.buildPokedexInfoFor(species) ?: return emptyList()
        return listOf(toHandle(r))
    }

    private fun toHandle(d: PokedexInfoRecipeData): RecipeHandle {
        val size = maxSize()
        return RecipeHandle(
            recipeIdPath = "pokedex_info/${sanitizePath(d.speciesName)}",
            inputSpecies = listOf(d.speciesName),
            outputSpecies = emptyList(),
            layoutFactory = { SpawnDisplayHelper.buildPokedexInfoLayout(d) },
            _width = { size.width },
            _height = { size.height },
            _slots = { RecipeHandle.Slots(pokemon = listOf(pokemonInput(d.speciesName))) },
        )
    }
}

// ----- Pokemon Description -----

object PokemonDescriptionDex : DexCategory {
    override val id = "pokemon_description"
    override val titleKey = "category.cobbledex-rei-emi-jei.pokemon_description"
    override val icon: Item = Items.BOOK
    override val maxSize = DisplayLayout::getMaxPokemonDescriptionSize
    override val supportsRecipeTree = true
    override fun isEnabled(config: CobbleDexConfig) = config.showPokemonDescription

    override fun buildAllRecipes() =
        RecipeBuilder.buildAllDescriptionRecipes().map(::toHandle)

    override fun buildRecipesFor(species: String): List<RecipeHandle> {
        val r = RecipeBuilder.buildDescriptionFor(species) ?: return emptyList()
        return listOf(toHandle(r))
    }

    private fun toHandle(d: PokemonDescriptionRecipeData): RecipeHandle {
        val size = maxSize()
        return RecipeHandle(
            recipeIdPath = "pokemon_description/${sanitizePath(d.speciesName)}",
            inputSpecies = listOf(d.speciesName),
            outputSpecies = emptyList(),
            layoutFactory = { SpawnDisplayHelper.buildPokemonDescriptionLayout(d) },
            _width = { size.width },
            _height = { size.height },
            _slots = { RecipeHandle.Slots(pokemon = listOf(pokemonInput(d.speciesName))) },
        )
    }
}

// ----- Fossils -----

object FossilDex : DexCategory {
    override val id = "fossils"
    override val titleKey = "category.cobbledex-rei-emi-jei.fossils"
    override val icon: Item = Items.BONE
    override val maxSize = DisplayLayout::getMaxFossilSize
    override val supportsRecipeTree = true
    override fun isEnabled(config: CobbleDexConfig) = config.showFossils

    override fun buildAllRecipes() =
        RecipeBuilder.buildAllFossilRecipes().map(::toHandle)

    override fun buildRecipesFor(species: String) =
        RecipeBuilder.buildFossilsFor(species).map(::toHandle)

    private fun toHandle(d: FossilRecipeData): RecipeHandle {
        val size = maxSize()
        return RecipeHandle(
            recipeIdPath = "fossils/${sanitizePath(d.speciesName)}",
            inputSpecies = emptyList(),
            outputSpecies = listOf(d.speciesName),
            layoutFactory = { SpawnDisplayHelper.buildFossilLayout(d) },
            _width = { size.width },
            _height = { size.height },
            _slots = { _ ->
                RecipeHandle.Slots(
                    pokemon = listOf(pokemonOutput(d.speciesName)),
                    items = d.fossilItems.mapIndexed { i, itemId ->
                        ItemSlotDef(itemId, 8, 38 + i * 20, SlotRole.INPUT)
                    },
                )
            },
        )
    }
}

// ----- Type Chart -----

object TypeChartDex : DexCategory {
    override val id = "type_chart"
    override val titleKey = "category.cobbledex-rei-emi-jei.type_chart"
    override val icon: Item = Items.SHIELD
    override val maxSize = DisplayLayout::getMaxTypeChartSize
    override val supportsRecipeTree = true
    override fun isEnabled(config: CobbleDexConfig) = config.showTypeChart

    override fun buildAllRecipes() =
        RecipeBuilder.buildAllTypeChartRecipes().map(::toHandle)

    override fun buildRecipesFor(species: String): List<RecipeHandle> {
        val r = RecipeBuilder.buildTypeChartFor(species) ?: return emptyList()
        return listOf(toHandle(r))
    }

    private fun toHandle(d: TypeChartRecipeData): RecipeHandle {
        val size = maxSize()
        return RecipeHandle(
            recipeIdPath = "type_chart/${sanitizePath(d.speciesName)}",
            inputSpecies = listOf(d.speciesName),
            outputSpecies = emptyList(),
            layoutFactory = { SpawnDisplayHelper.buildTypeChartLayout(d) },
            _width = { size.width },
            _height = { size.height },
            _slots = { RecipeHandle.Slots(pokemon = listOf(pokemonInput(d.speciesName))) },
        )
    }
}

// ----- Natures -----

object NatureDex : DexCategory {
    override val id = "natures"
    override val titleKey = "category.cobbledex-rei-emi-jei.natures"
    override val icon: Item = Items.WRITABLE_BOOK
    override val maxSize = DisplayLayout::getMaxNatureSize
    override fun isEnabled(config: CobbleDexConfig) = config.showNatures

    override fun buildAllRecipes() =
        RecipeBuilder.buildNatureRecipes().map(::toHandle)

    override fun buildRecipesFor(species: String) = emptyList<RecipeHandle>()

    private fun toHandle(d: NatureRecipeData): RecipeHandle {
        val size = maxSize()
        return RecipeHandle(
            recipeIdPath = "natures/table_${d.pageIndex}",
            inputSpecies = emptyList(),
            outputSpecies = emptyList(),
            layoutFactory = { SpawnDisplayHelper.buildNatureLayout(d) },
            _width = { size.width },
            _height = { size.height },
        )
    }
}
