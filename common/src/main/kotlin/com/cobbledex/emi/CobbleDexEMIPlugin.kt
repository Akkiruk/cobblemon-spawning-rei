package com.cobbledex.emi

import com.cobbledex.CobbleDexMod
import com.cobbledex.DebugLog
import com.cobbledex.DexCategory
import com.cobbledex.DiscoveryAliases
import com.cobbledex.PokemonItemCache
import com.cobbledex.RecipeHandle
import com.cobbledex.RecipeViewerReloader
import com.cobbledex.SlotRole
import com.cobbledex.SpawnDataIndex
import com.cobbledex.SpawnDisplayHelper
import com.cobbledex.ViewerParityGuard
import com.cobbledex.config.CobbleDexConfig
import dev.emi.emi.api.EmiPlugin
import dev.emi.emi.api.EmiRegistry
import dev.emi.emi.api.recipe.EmiRecipe
import dev.emi.emi.api.recipe.EmiRecipeCategory
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Items

open class CobbleDexEMIPlugin : EmiPlugin {

    companion object {
        private val categories = mutableMapOf<String, EmiRecipeCategory>()

        private fun emiCategory(def: DexCategory): EmiRecipeCategory =
            categories.getOrPut(def.id) {
                object : EmiRecipeCategory(
                    ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, def.id),
                    EmiStack.of(def.icon)
                ) {
                    override fun getName(): Component = Component.translatable(def.titleKey)
                }
            }

        // Pre-initialize all known categories
        val SPAWN_CATEGORY get() = emiCategory(com.cobbledex.SpawnDex)
        val EVOLUTION_CATEGORY get() = emiCategory(com.cobbledex.EvolutionDex)
        val OBTAINMENT_CATEGORY get() = emiCategory(com.cobbledex.ObtainmentDex)
        val DROP_CATEGORY get() = emiCategory(com.cobbledex.DropDex)
        val STATS_CATEGORY get() = emiCategory(com.cobbledex.StatsDex)
        val MOVES_CATEGORY get() = emiCategory(com.cobbledex.MovesDex)
        val POKEDEX_INFO_CATEGORY get() = emiCategory(com.cobbledex.PokedexInfoDex)
        val FOSSIL_CATEGORY get() = emiCategory(com.cobbledex.FossilDex)
        val TYPE_CHART_CATEGORY get() = emiCategory(com.cobbledex.TypeChartDex)
        val NATURE_CATEGORY get() = emiCategory(com.cobbledex.NatureDex)
    }

    override fun register(registry: EmiRegistry) {
        SpawnDataIndex.ensureLoaded()
        val config = CobbleDexConfig.get()
        val queries = SpawnDataIndex.currentQueries()
        val hasSync = SpawnDataIndex.loadState == SpawnDataIndex.LoadState.FULLY_LOADED
        val spawnCount = SpawnDataIndex.spawnsBySpecies.size

        DebugLog.info("EMI register() called (loadState=${SpawnDataIndex.loadState.name}, " +
            "dataVersion=${SpawnDataIndex.dataVersion}, spawns=$spawnCount species)")

        // Register Pokémon entries
        var registered = 0
        var formCount = 0
        for (species in SpawnDataIndex.allSpeciesNames) {
            val speciesInfo = queries.getSpeciesInfo(species)
            if (speciesInfo == null) continue
            if (speciesInfo.isForm && !config.registerFormEntries) continue
            if (!queries.shouldSurfaceSpecies(species)) continue
            if (!PokemonItemCache.canRender(species)) continue
            val searchText = DiscoveryAliases.pokemonSearchText(species)
            val stack = PokemonEmiStack.of(species)
            registry.addEmiStack(stack)
            if (searchText.isNotBlank()) {
                registry.addAlias(stack, Component.literal(searchText))
            }
            if (speciesInfo.isForm) formCount++ else registered++
        }

        // Register categories, workstations, and recipes
        val registeredCats = mutableListOf<String>()
        for (def in DexCategory.ALL) {
            if (!def.isEnabled(config)) continue
            val cat = emiCategory(def)
            registry.addCategory(cat)
            // No workstation: the category icons are arbitrary markers, not real stations — matches
            // REI/JEI. The category is still in EMI's category list.

            val recipes = def.buildAllRecipes()
            ViewerParityGuard.warn(def, recipes, "EMI")
            for (handle in recipes) {
                registry.addRecipe(GenericEmiRecipe(handle, cat, def))
            }
            registeredCats.add("${def.id}(${recipes.size})")
        }

        RecipeViewerReloader.emiLastRegisteredVersion = SpawnDataIndex.dataVersion
        DebugLog.info("EMI: Registered $registered Pokémon + $formCount forms, categories: ${registeredCats.joinToString(" + ")} (dataVersion=${SpawnDataIndex.dataVersion})")
    }

    // ----- Generic EMI Recipe wrapping RecipeHandle -----

    class GenericEmiRecipe(
        private val handle: RecipeHandle,
        private val emiCategory: EmiRecipeCategory,
        private val def: DexCategory,
    ) : EmiRecipe {

        private val cachedInputs: List<EmiIngredient> by lazy(LazyThreadSafetyMode.NONE) {
            val pokemon = handle.lookupInputSpecies()
                .mapNotNull { species ->
                    if (PokemonItemCache.canRender(species)) PokemonEmiStack.of(species) else null
                }
            val items = handle.lookupInputItemIds().mapNotNull { itemId ->
                val stack = SpawnDisplayHelper.resolveItemStack(itemId)
                if (!stack.isEmpty) EmiStack.of(stack) else null
            }
            val move = handle.slots.moveKey?.let { listOf(MoveEmiStack.of(it)) } ?: emptyList()
            pokemon + items + move
        }

        private val cachedOutputs: List<EmiStack> by lazy(LazyThreadSafetyMode.NONE) {
            val pokemon = handle.lookupOutputSpecies()
                .mapNotNull { species ->
                    if (PokemonItemCache.canRender(species)) PokemonEmiStack.of(species) else null
                }
            val items = handle.lookupOutputItemIds().mapNotNull { itemId ->
                val stack = SpawnDisplayHelper.resolveItemStack(itemId)
                if (!stack.isEmpty) EmiStack.of(stack) else null
            }
            pokemon + items
        }

        override fun getCategory(): EmiRecipeCategory = emiCategory

        override fun getId(): ResourceLocation =
            ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "emi_${handle.recipeIdPath}")

        override fun getInputs(): List<EmiIngredient> = cachedInputs

        override fun getOutputs(): List<EmiStack> = cachedOutputs

        override fun getDisplayWidth(): Int = handle.width
        override fun getDisplayHeight(): Int = handle.height

        override fun supportsRecipeTree(): Boolean = def.supportsRecipeTree

        override fun addWidgets(widgets: dev.emi.emi.api.widget.WidgetHolder) {
            val slots = handle.slots

            for (slot in slots.pokemon) {
                val stack = PokemonEmiStack.of(slot.species, slot.aspects)
                if (!stack.isEmpty) {
                    widgets.addSlot(stack, slot.x, slot.y).recipeContext(this)
                }
            }

            for (slot in slots.items) {
                val stack = SpawnDisplayHelper.resolveItemStack(slot.itemId)
                if (!stack.isEmpty) {
                    widgets.addSlot(EmiStack.of(stack), slot.x, slot.y).recipeContext(this)
                }
            }

            val w = handle.width
            val h = handle.height
            widgets.addDrawable(0, 0, w, h) { gfx, _, _, _ ->
                handle.layout.render(gfx)
            }

            // Move-name links: an invisible clickable over each name → that move's learner grid.
            for (link in slots.moveLinks) {
                widgets.add(
                    MoveLinkEmiWidget(
                        dev.emi.emi.api.widget.Bounds(link.x, link.y, link.width, link.height),
                        link.moveName,
                    )
                )
            }

            for (zone in handle.layout.tooltipZones) {
                if (zone.lines.isNotEmpty()) {
                    widgets.addTooltipText(zone.lines, zone.x, zone.y, zone.width, zone.height)
                }
            }
        }
    }

    /** Invisible click target laid over a move name; faint highlight on hover. */
    private class MoveLinkEmiWidget(
        private val bounds: dev.emi.emi.api.widget.Bounds,
        private val move: String,
    ) : dev.emi.emi.api.widget.Widget() {

        override fun getBounds(): dev.emi.emi.api.widget.Bounds = bounds

        override fun render(graphics: net.minecraft.client.gui.GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
            if (bounds.contains(mouseX, mouseY)) {
                graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), 0x30FFFFFF)
            }
        }

        override fun mouseClicked(mouseX: Int, mouseY: Int, button: Int): Boolean {
            if (button == 0 && bounds.contains(mouseX, mouseY)) {
                // The move is an input on the learner grid, so "uses" is the view that resolves it.
                dev.emi.emi.api.EmiApi.displayUses(MoveEmiStack.of(move))
                return true
            }
            return false
        }
    }
}
