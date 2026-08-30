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
    /** Slot box size in px. REI honours this; JEI/EMI use their fixed slot size. */
    val size: Int = 18,
)

/**
 * A clickable text region bound to a move — the viewer plugin turns it into a click target that
 * opens the "every Pokémon that can learn this move" grid. Requires no external mod.
 */
data class MoveLinkDef(
    val moveName: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

class RecipeHandle(
    val recipeIdPath: String,
    val inputSpecies: List<String>,
    val outputSpecies: List<String>,
    layoutFactory: () -> PanelLayout,
    private val _width: (() -> Int)? = null,
    private val _height: (() -> Int)? = null,
    private val _slots: () -> Slots = { Slots() },
) {
    data class Slots(
        val pokemon: List<PokemonSlotDef> = emptyList(),
        val items: List<ItemSlotDef> = emptyList(),
        val arrowX: Int = 0,
        val arrowY: Int = 0,
        val hasArrow: Boolean = false,
        val catalogInputIds: List<String> = emptyList(),
        val moveLinks: List<MoveLinkDef> = emptyList(),
        /** Set on a move-learner grid: the move it lists learners for, so viewers can key nav on it. */
        val moveKey: String? = null,
    )

    val layout: PanelLayout by lazy(LazyThreadSafetyMode.NONE) {
        try {
            layoutFactory()
        } catch (e: Exception) {
            DebugLog.once("layout-$recipeIdPath") { "Layout failed: ${e.message}" }
            PanelLayout.error("Data unavailable")
        }
    }
    val width: Int by lazy(LazyThreadSafetyMode.NONE) {
        try { _width?.invoke() ?: layout.width } catch (_: Exception) { 200 }
    }
    val height: Int by lazy(LazyThreadSafetyMode.NONE) {
        try { _height?.invoke() ?: layout.height } catch (_: Exception) { 100 }
    }
    val hasExplicitSize: Boolean get() = _width != null && _height != null
    val slots: Slots by lazy(LazyThreadSafetyMode.NONE) {
        try {
            _slots()
        } catch (e: Exception) {
            DebugLog.once("slots-$recipeIdPath") { "Slots failed: ${e.message}" }
            Slots()
        }
    }

    fun lookupInputSpecies(): List<String> = lookupSpecies(inputSpecies, outputSpecies)

    fun lookupOutputSpecies(): List<String> = lookupSpecies(outputSpecies, inputSpecies)

    fun lookupInputItemIds(): List<String> {
        val currentSlots = slots
        return (currentSlots.catalogInputIds + currentSlots.items.filter { it.role == SlotRole.INPUT }.map { it.itemId })
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun lookupOutputItemIds(): List<String> = slots.items
        .filter { it.role == SlotRole.OUTPUT }
        .map { it.itemId }
        .filter { it.isNotBlank() }
        .distinct()

    fun explicitWidthOrNull(): Int? = if (_width != null) width else null

    fun explicitHeightOrNull(): Int? = if (_height != null) height else null

    private fun lookupSpecies(primary: List<String>, fallback: List<String>): List<String> =
        (if (primary.isNotEmpty()) primary else fallback)
            .filter { it.isNotBlank() }
            .distinct()
}

interface DexCategory {
    val id: String
    val titleKey: String
    val icon: Item
    val maxSize: () -> CategorySizer.PanelSize
    val supportsRecipeTree: Boolean get() = false

    fun isEnabled(config: CobbleDexConfig): Boolean
    fun buildAllRecipes(): List<RecipeHandle>
    fun buildRecipesFor(species: String): List<RecipeHandle>
    fun buildUsagesFor(species: String): List<RecipeHandle> = emptyList()
    fun buildRecipesForItem(itemId: String): List<RecipeHandle> = emptyList()

    /** Displays to show when a [MoveLinkDef] for [moveName] is clicked (viewer-agnostic). */
    fun buildRecipesForMove(moveName: String): List<RecipeHandle> = emptyList()

    companion object {
        val ALL: List<DexCategory> = listOf(
            PokemonOverviewDex, SpawnDex, EvolutionDex, ObtainmentDex, DropDex,
            StatsDex, MovesDex, PokedexInfoDex, PokemonDescriptionDex, FossilDex,
            TypeChartDex, NatureDex, JobsDex, FormsDex, RidingDex
        )
    }
}

private fun pokemonInput(species: String, x: Int = 8, y: Int = 3) =
    PokemonSlotDef(species, emptySet(), x, y, SlotRole.INPUT)

private fun pokemonOutput(species: String, x: Int = 8, y: Int = 3) =
    PokemonSlotDef(species, emptySet(), x, y, SlotRole.OUTPUT)

// ----- Pokemon Overview -----

object PokemonOverviewDex : DexCategory {
    override val id = "overview"
    override val titleKey = "category.cobbledex-rei-emi-jei.overview"
    override val icon: Item = Items.COMPASS
    override val maxSize: () -> CategorySizer.PanelSize = { CategorySizer.getBounds(this) }
    override val supportsRecipeTree = true
    override fun isEnabled(config: CobbleDexConfig) = true

    override fun buildAllRecipes(): List<RecipeHandle> =
        RecipeBuilder.buildAllOverviewRecipes().map(::toHandle)

    override fun buildRecipesFor(species: String): List<RecipeHandle> {
        val recipe = RecipeBuilder.buildOverviewFor(species) ?: return emptyList()
        return listOf(toHandle(recipe))
    }

    private fun toHandle(d: PokemonOverviewRecipeData) = RecipeHandle(
        recipeIdPath = "overview/${sanitizePath(d.speciesName)}",
        inputSpecies = listOf(d.speciesName),
        outputSpecies = emptyList(),
        layoutFactory = { PokemonInfoPageBuilder.buildOverview(d) },
        _slots = { RecipeHandle.Slots(pokemon = listOf(pokemonInput(d.speciesName))) },
    )
}

// ----- Spawn -----

object SpawnDex : DexCategory {
    override val id = "spawns"
    override val titleKey = "category.cobbledex-rei-emi-jei.spawn"
    override val icon: Item = Items.GRASS_BLOCK
    override val maxSize: () -> CategorySizer.PanelSize = { CategorySizer.getBounds(this) }
    override fun isEnabled(config: CobbleDexConfig) = true

    override fun buildAllRecipes(): List<RecipeHandle> {
        val all = mutableListOf<RecipeHandle>()
        for ((species, spawns) in SpawnDataIndex.spawnsBySpecies) {
            if (!SpawnDataIndex.shouldSurfaceSpecies(species)) continue
            if (spawns.isEmpty()) continue
            all.addAll(RecipeBuilder.buildSpawnRecipes(species, spawns).map(::toHandle))
        }
        return all
    }

    override fun buildRecipesFor(species: String): List<RecipeHandle> {
        if (!SpawnDataIndex.shouldSurfaceSpecies(species)) return emptyList()
        val spawns = SpawnDataIndex.getSpawnsFor(species)
        if (spawns.isEmpty()) return emptyList()
        return RecipeBuilder.buildSpawnRecipes(species, spawns).map(::toHandle)
    }

    private fun toHandle(d: SpawnRecipeData) = RecipeHandle(
        recipeIdPath = "spawn/${sanitizePath(d.speciesName)}/${sanitizePath(d.spawn.bucket)}/${d.bucketIndex}",
        inputSpecies = emptyList(),
        outputSpecies = listOf(d.speciesName),
        layoutFactory = { SpawnPageBuilder.build(d) },
        _slots = { RecipeHandle.Slots(pokemon = listOf(pokemonInput(d.speciesName))) },
    )
}

// ----- Evolution -----

object EvolutionDex : DexCategory {
    override val id = "evolution"
    override val titleKey = "category.cobbledex-rei-emi-jei.evolution"
    override val icon: Item = Items.EXPERIENCE_BOTTLE
    override val maxSize: () -> CategorySizer.PanelSize = { CategorySizer.getBounds(this) }
    override fun isEnabled(config: CobbleDexConfig) = config.showEvolutions

    override fun buildAllRecipes(): List<RecipeHandle> =
        RecipeBuilder.buildAllEvolutionRecipes().map(::toHandle)

    // The Pokemon Overview navigator only ever calls buildRecipesFor per
    // category (never buildUsagesFor) - so a species with no INCOMING
    // evolution (e.g. a base form like Eevee) showed no Evolution tab at all,
    // even though it has plenty of OUTGOING branches. Combine both directions
    // here so every member of a family (base or evolved) shows its full
    // evolution info - what it comes from AND what it can become.
    override fun buildRecipesFor(species: String): List<RecipeHandle> {
        val incoming = RecipeBuilder.buildEvolutionRecipesInto(species).filter { it.targetSpeciesName != null }
        val outgoing = RecipeBuilder.buildEvolutionPagesFor(species).filter { it.targetSpeciesName != null }
        val combined = incoming + outgoing
        if (combined.isEmpty()) {
            // Neither direction has real data - fall back to the plain
            // "no evolutions" placeholder instead of showing nothing.
            return RecipeBuilder.buildEvolutionPagesFor(species).map(::toHandle)
        }
        val total = combined.size
        return combined
            .mapIndexed { index, data -> data.copy(pageIndex = index + 1, pageTotal = total, totalOutcomes = total) }
            .map(::toHandle)
    }

    override fun buildUsagesFor(species: String): List<RecipeHandle> =
        RecipeBuilder.buildEvolutionPagesFor(species).map(::toHandle)

    override fun buildRecipesForItem(itemId: String): List<RecipeHandle> =
        RecipeBuilder.buildEvolutionRecipesForItem(itemId).map(::toHandle)

    private fun toHandle(d: EvolutionRecipeData): RecipeHandle {
        var evolutionResult: SpawnDisplayHelper.EvolutionLayoutResult? = null
        return RecipeHandle(
            recipeIdPath = "evolution/${sanitizePath(d.sourceSpeciesName)}_${d.pageIndex}",
            inputSpecies = listOf(d.sourceSpeciesName),
            outputSpecies = listOfNotNull(d.targetSpeciesName),
            layoutFactory = {
                val r = evolutionResult ?: EvolutionPageBuilder.build(d)
                evolutionResult = r
                r.layout
            },
            _slots = {
                val r = evolutionResult ?: EvolutionPageBuilder.build(d).also { evolutionResult = it }
                RecipeHandle.Slots(
                    pokemon = r.pokemonSlots,
                    items = r.itemSlots,
                    catalogInputIds = d.methods.flatMap { method -> method.itemRequirements.map(EvolutionItemInfo::itemId) }.distinct(),
                )
            },
        )
    }
}

// ----- Obtainment -----

object ObtainmentDex : DexCategory {
    override val id = "obtainment"
    override val titleKey = "category.cobbledex-rei-emi-jei.obtainment"
    override val icon: Item = Items.NETHER_STAR
    override val maxSize: () -> CategorySizer.PanelSize = { CategorySizer.getBounds(this) }
    override val supportsRecipeTree = true
    override fun isEnabled(config: CobbleDexConfig) = config.showObtainment

    override fun buildAllRecipes() =
        RecipeBuilder.buildAllUnifiedObtainmentRecipes().map(::toUnifiedHandle)

    override fun buildRecipesFor(species: String): List<RecipeHandle> =
        RecipeBuilder.buildUnifiedObtainmentFor(species).map(::toUnifiedHandle)

    override fun buildRecipesForItem(itemId: String): List<RecipeHandle> =
        RecipeBuilder.buildUnifiedObtainmentForItem(itemId).map(::toUnifiedHandle)

    private fun toUnifiedHandle(d: UnifiedObtainmentRecipeData) = RecipeHandle(
        recipeIdPath = "obtainment/${sanitizePath(d.speciesName)}_${d.pageIndex}",
        inputSpecies = emptyList(),
        outputSpecies = listOf(d.speciesName),
        layoutFactory = { ObtainmentPageBuilder.buildUnified(d) },
        _width = { ObtainmentPageBuilder.measureUnifiedWidth(d.speciesName) },
        _height = { ObtainmentPageBuilder.measureUnifiedHeight(d) },
        _slots = {
            RecipeHandle.Slots(
                pokemon = listOf(pokemonOutput(d.speciesName, 8, 3)),
                catalogInputIds = d.routes.flatMap { it.itemIds }.distinct(),
            )
        },
    )
}

// ----- Drops -----

object DropDex : DexCategory {
    override val id = "drops"
    override val titleKey = "category.cobbledex-rei-emi-jei.drops"
    override val icon: Item = Items.DIAMOND
    override val maxSize: () -> CategorySizer.PanelSize = { CategorySizer.getBounds(this) }
    override val supportsRecipeTree = true
    override fun isEnabled(config: CobbleDexConfig) = config.showDrops

    override fun buildAllRecipes() =
        RecipeBuilder.buildAllDropRecipes().map(::toHandle) +
            RecipeBuilder.buildAllItemDropperRecipes().map(::toItemDroppersHandle)

    override fun buildRecipesFor(species: String) =
        RecipeBuilder.buildDropsFor(species).map(::toHandle)

    override fun buildRecipesForItem(itemId: String) =
        RecipeBuilder.buildItemDroppersForItem(itemId).map(::toItemDroppersHandle)

    private fun toItemDroppersHandle(d: ItemDroppersRecipeData): RecipeHandle {
        var result: SpawnDisplayHelper.ItemDroppersLayoutResult? = null
        fun res() = result ?: DropPageBuilder.buildItemDroppers(d).also { result = it }
        return RecipeHandle(
            recipeIdPath = "drops/droppers_${sanitizePath(d.itemId.replace(':', '_').replace('/', '_'))}_${d.pageIndex}",
            inputSpecies = emptyList(),
            outputSpecies = emptyList(),
            layoutFactory = { res().layout },
            _width = { SpawnDisplayHelper.itemDroppersPanelSize(d).width },
            _height = { SpawnDisplayHelper.itemDroppersPanelSize(d).height },
            _slots = {
                RecipeHandle.Slots(
                    pokemon = res().pokemonSlots,
                    items = listOf(ItemSlotDef(d.itemId, PanelLayout.PADDING, 4, SlotRole.INPUT)),
                    catalogInputIds = listOf(d.itemId),
                )
            },
        )
    }

    private fun toHandle(d: DropRecipeData) = RecipeHandle(
        recipeIdPath = "drops/${sanitizePath(d.speciesName)}_${d.pageIndex}",
        inputSpecies = listOf(d.speciesName),
        outputSpecies = emptyList(),
        layoutFactory = { DropPageBuilder.build(d) },
        _width = { DropPageBuilder.measureWidth(d) },
        _height = { DropPageBuilder.measureHeight(d) },
        _slots = {
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
    override val maxSize: () -> CategorySizer.PanelSize = { CategorySizer.getBounds(this) }
    override val supportsRecipeTree = true
    override fun isEnabled(config: CobbleDexConfig) = config.showStats

    override fun buildAllRecipes() =
        RecipeBuilder.buildAllStatsRecipes().map(::toHandle)

    override fun buildRecipesFor(species: String): List<RecipeHandle> {
        val r = RecipeBuilder.buildStatsFor(species) ?: return emptyList()
        return listOf(toHandle(r))
    }

    private fun toHandle(d: StatsRecipeData) = RecipeHandle(
        recipeIdPath = "stats/${sanitizePath(d.speciesName)}",
        inputSpecies = listOf(d.speciesName),
        outputSpecies = emptyList(),
        layoutFactory = { PokemonInfoPageBuilder.buildStats(d) },
        _slots = { RecipeHandle.Slots(pokemon = listOf(pokemonInput(d.speciesName))) },
    )
}

// ----- Moves -----

object MovesDex : DexCategory {
    override val id = "moves"
    override val titleKey = "category.cobbledex-rei-emi-jei.moves"
    override val icon: Item = Items.PAPER
    override val maxSize: () -> CategorySizer.PanelSize = { CategorySizer.getBounds(this) }
    override val supportsRecipeTree = true
    override fun isEnabled(config: CobbleDexConfig) = config.showMoves

    override fun buildAllRecipes() =
        RecipeBuilder.buildAllMovesRecipes().map(::toHandle) +
            RecipeBuilder.buildAllMoveLearnerRecipes().map(::toMoveLearnersHandle)

    override fun buildRecipesFor(species: String) =
        RecipeBuilder.buildMovesFor(species).map(::toHandle)

    override fun buildRecipesForItem(itemId: String): List<RecipeHandle> {
        if (!TmItemUtils.isTmItem(itemId)) return emptyList()
        return RecipeBuilder.buildMoveLearnersForItem(itemId).map(::toMoveLearnersHandle)
    }

    override fun buildRecipesForMove(moveName: String): List<RecipeHandle> =
        RecipeBuilder.buildMoveLearnersForMove(moveName).map(::toMoveLearnersHandle)

    private fun toHandle(d: MovesRecipeData): RecipeHandle {
        var result: SpawnDisplayHelper.MovesLayoutResult? = null
        fun res() = result ?: MovePageBuilder.buildMoves(d).also { result = it }
        return RecipeHandle(
            recipeIdPath = "moves/${sanitizePath(d.speciesName)}_${d.pageIndex}",
            inputSpecies = listOf(d.speciesName),
            outputSpecies = emptyList(),
            layoutFactory = { res().layout },
            _slots = {
                RecipeHandle.Slots(
                    pokemon = listOf(pokemonInput(d.speciesName)),
                    moveLinks = res().moveLinks,
                )
            },
        )
    }

    private fun toMoveLearnersHandle(d: MoveLearnersRecipeData): RecipeHandle {
        var result: SpawnDisplayHelper.MoveLearnersLayoutResult? = null
        fun res() = result ?: MovePageBuilder.buildMoveLearners(d).also { result = it }
        return RecipeHandle(
            recipeIdPath = "moves/move_learners_${sanitizePath(d.moveName)}_${d.pageIndex}",
            inputSpecies = emptyList(),
            outputSpecies = emptyList(),
            layoutFactory = { res().layout },
            _width = { SpawnDisplayHelper.moveLearnersPanelSize(d).width },
            _height = { SpawnDisplayHelper.moveLearnersPanelSize(d).height },
            _slots = {
                RecipeHandle.Slots(
                    pokemon = res().pokemonSlots,
                    catalogInputIds = TmItemUtils.tmItemIds(d.moveName),
                    moveKey = d.moveName,
                )
            },
        )
    }
}

// ----- Pokédex Info -----

object PokedexInfoDex : DexCategory {
    override val id = "pokedex_info"
    override val titleKey = "category.cobbledex-rei-emi-jei.pokedex_info"
    override val icon: Item = Items.WRITABLE_BOOK
    override val maxSize: () -> CategorySizer.PanelSize = { CategorySizer.getBounds(this) }
    override val supportsRecipeTree = true
    override fun isEnabled(config: CobbleDexConfig) = config.showPokedexInfo

    override fun buildAllRecipes() =
        RecipeBuilder.buildAllPokedexInfoRecipes().map(::toHandle)

    override fun buildRecipesFor(species: String): List<RecipeHandle> {
        val r = RecipeBuilder.buildPokedexInfoFor(species) ?: return emptyList()
        return listOf(toHandle(r))
    }

    private fun toHandle(d: PokedexInfoRecipeData) = RecipeHandle(
        recipeIdPath = "pokedex_info/${sanitizePath(d.speciesName)}",
        inputSpecies = listOf(d.speciesName),
        outputSpecies = emptyList(),
        layoutFactory = { PokemonInfoPageBuilder.buildPokedex(d) },
        _slots = { RecipeHandle.Slots(pokemon = listOf(pokemonInput(d.speciesName))) },
    )
}

// ----- Pokemon Description -----

object PokemonDescriptionDex : DexCategory {
    override val id = "pokemon_description"
    override val titleKey = "category.cobbledex-rei-emi-jei.pokemon_description"
    override val icon: Item = Items.BOOK
    override val maxSize: () -> CategorySizer.PanelSize = { CategorySizer.getBounds(this) }
    override val supportsRecipeTree = true
    override fun isEnabled(config: CobbleDexConfig) = config.showPokemonDescription

    override fun buildAllRecipes() =
        RecipeBuilder.buildAllDescriptionRecipes().map(::toHandle)

    override fun buildRecipesFor(species: String): List<RecipeHandle> {
        val r = RecipeBuilder.buildDescriptionFor(species) ?: return emptyList()
        return listOf(toHandle(r))
    }

    private fun toHandle(d: PokemonDescriptionRecipeData) = RecipeHandle(
        recipeIdPath = "pokemon_description/${sanitizePath(d.speciesName)}",
        inputSpecies = listOf(d.speciesName),
        outputSpecies = emptyList(),
        layoutFactory = { PokemonInfoPageBuilder.buildDescription(d) },
        _slots = { RecipeHandle.Slots(pokemon = listOf(pokemonInput(d.speciesName))) },
    )
}

// ----- Fossils -----

object FossilDex : DexCategory {
    override val id = "fossils"
    override val titleKey = "category.cobbledex-rei-emi-jei.fossils"
    override val icon: Item = Items.BONE
    override val maxSize: () -> CategorySizer.PanelSize = { CategorySizer.getBounds(this) }
    override val supportsRecipeTree = true
    override fun isEnabled(config: CobbleDexConfig) = config.showFossils

    override fun buildAllRecipes() =
        RecipeBuilder.buildAllFossilRecipes().map(::toHandle)

    override fun buildRecipesFor(species: String) =
        RecipeBuilder.buildFossilsFor(species).map(::toHandle)

    override fun buildRecipesForItem(itemId: String) =
        RecipeBuilder.buildFossilRecipesForItem(itemId).map(::toHandle)

    private fun toHandle(d: FossilRecipeData) = RecipeHandle(
        recipeIdPath = "fossils/${sanitizePath(d.speciesName)}",
        inputSpecies = emptyList(),
        outputSpecies = listOf(d.speciesName),
        layoutFactory = { MechanicPageBuilder.buildFossil(d) },
        _slots = {
            RecipeHandle.Slots(
                pokemon = listOf(pokemonOutput(d.speciesName)),
                items = d.fossilItems.mapIndexed { i, itemId ->
                    ItemSlotDef(itemId, 8, 38 + i * 20, SlotRole.INPUT)
                },
            )
        },
    )
}

// ----- Type Chart -----

object TypeChartDex : DexCategory {
    override val id = "type_chart"
    override val titleKey = "category.cobbledex-rei-emi-jei.type_chart"
    override val icon: Item = Items.SHIELD
    override val maxSize: () -> CategorySizer.PanelSize = { CategorySizer.getBounds(this) }
    override val supportsRecipeTree = true
    override fun isEnabled(config: CobbleDexConfig) = config.showTypeChart

    override fun buildAllRecipes() =
        RecipeBuilder.buildAllTypeChartRecipes().map(::toHandle)

    override fun buildRecipesFor(species: String): List<RecipeHandle> {
        val r = RecipeBuilder.buildTypeChartFor(species) ?: return emptyList()
        return listOf(toHandle(r))
    }

    private fun toHandle(d: TypeChartRecipeData) = RecipeHandle(
        recipeIdPath = "type_chart/${sanitizePath(d.speciesName)}",
        inputSpecies = listOf(d.speciesName),
        outputSpecies = emptyList(),
        layoutFactory = { PokemonInfoPageBuilder.buildTypeChart(d) },
        _slots = { RecipeHandle.Slots(pokemon = listOf(pokemonInput(d.speciesName))) },
    )
}

// ----- Natures -----

object NatureDex : DexCategory {
    override val id = "natures"
    override val titleKey = "category.cobbledex-rei-emi-jei.natures"
    override val icon: Item = Items.WRITABLE_BOOK
    override val maxSize: () -> CategorySizer.PanelSize = { CategorySizer.getBounds(this) }
    override fun isEnabled(config: CobbleDexConfig) = config.showNatures

    override fun buildAllRecipes() =
        RecipeBuilder.buildNatureRecipes().map(::toHandle)

    override fun buildRecipesFor(species: String) = emptyList<RecipeHandle>()

    private fun toHandle(d: NatureRecipeData) = RecipeHandle(
        recipeIdPath = "natures/table_${d.pageIndex}",
        inputSpecies = emptyList(),
        outputSpecies = emptyList(),
        layoutFactory = { MechanicPageBuilder.buildNature(d) },
    )
}

// ----- Cobbleworkers Jobs -----

object JobsDex : DexCategory {
    override val id = "jobs"
    override val titleKey = "category.cobbledex-rei-emi-jei.jobs"
    override val icon: Item = Items.IRON_PICKAXE
    override val maxSize: () -> CategorySizer.PanelSize = { CategorySizer.getBounds(this) }
    override val supportsRecipeTree = true
    override fun isEnabled(config: CobbleDexConfig) = config.showJobs

    override fun buildAllRecipes(): List<RecipeHandle> {
        if (!SpawnDataIndex.hasJobRules()) return emptyList()
        return RecipeBuilder.buildAllJobsRecipes().map(::toHandle)
    }

    override fun buildRecipesFor(species: String): List<RecipeHandle> {
        if (!SpawnDataIndex.hasJobRules()) return emptyList()
        return RecipeBuilder.buildJobsFor(species).map(::toHandle)
    }

    private fun toHandle(d: JobRecipeData) = RecipeHandle(
        recipeIdPath = "jobs/${sanitizePath(d.speciesName)}/${sanitizePath(d.match.rule.id)}",
        inputSpecies = listOf(d.speciesName),
        outputSpecies = emptyList(),
        layoutFactory = { MechanicPageBuilder.buildJob(d.speciesName, d.match) },
        _slots = { RecipeHandle.Slots(pokemon = listOf(pokemonInput(d.speciesName))) },
    )
}

// ----- Alternate Forms -----

object FormsDex : DexCategory {
    override val id = "forms"
    override val titleKey = "category.cobbledex-rei-emi-jei.forms"
    override val icon: Item = Items.AMETHYST_SHARD
    override val maxSize: () -> CategorySizer.PanelSize = { CategorySizer.getBounds(this) }
    override val supportsRecipeTree = true
    override fun isEnabled(config: CobbleDexConfig) = config.showAlternateForms

    override fun buildAllRecipes(): List<RecipeHandle> {
        return RecipeBuilder.buildAllFormRecipes().map(::toHandle)
    }

    override fun buildRecipesFor(species: String): List<RecipeHandle> {
        // If species is itself a form, show its base species' form list for sibling navigation
        val info = SpawnDataIndex.getSpeciesInfo(species)
        val lookupKey = info?.baseSpeciesName ?: species
        return RecipeBuilder.buildFormsFor(lookupKey).map(::toHandle)
    }

    private fun toHandle(d: FormRecipeData): RecipeHandle {
        var formResult: SpawnDisplayHelper.FormLayoutResult? = null
        return RecipeHandle(
            recipeIdPath = "forms/${sanitizePath(d.baseSpeciesName)}_${d.pageIndex}",
            inputSpecies = listOf(d.baseSpeciesName, d.form.formKey),
            outputSpecies = emptyList(),
            layoutFactory = {
                val r = formResult ?: PokemonInfoPageBuilder.buildForms(d)
                formResult = r
                r.layout
            },
            _width = { PokemonInfoPageBuilder.measureFormsWidth(d) },
            _height = { PokemonInfoPageBuilder.measureFormsHeight(d) },
            _slots = {
                val r = formResult ?: PokemonInfoPageBuilder.buildForms(d).also { formResult = it }
                RecipeHandle.Slots(pokemon = r.pokemonSlots)
            },
        )
    }
}

// ----- Riding Data -----

object RidingDex : DexCategory {
    override val id = "riding"
    override val titleKey = "category.cobbledex-rei-emi-jei.riding"
    override val icon: Item = Items.SADDLE
    override val maxSize: () -> CategorySizer.PanelSize = { CategorySizer.getBounds(this) }
    override val supportsRecipeTree = true
    override fun isEnabled(config: CobbleDexConfig) = config.showRiding

    override fun buildAllRecipes() =
        RecipeBuilder.buildAllRidingRecipes().map(::toHandle)

    override fun buildRecipesFor(species: String): List<RecipeHandle> {
        return RecipeBuilder.buildRidingFor(species).map(::toHandle)
    }

    private fun toHandle(d: RidingRecipeData) = RecipeHandle(
        recipeIdPath = "riding/${sanitizePath(d.speciesName)}/${d.mount.mountType.lowercase()}",
        inputSpecies = listOf(d.speciesName),
        outputSpecies = emptyList(),
        layoutFactory = { PokemonInfoPageBuilder.buildRiding(d) },
        _slots = { RecipeHandle.Slots(pokemon = listOf(pokemonInput(d.speciesName))) },
    )
}
