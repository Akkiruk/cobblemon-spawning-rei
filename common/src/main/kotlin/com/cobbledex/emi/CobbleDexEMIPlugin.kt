package com.cobbledex.emi

import com.cobbledex.CobbleDexMod
import com.cobbledex.DebugLog
import com.cobbledex.DexCategory
import com.cobbledex.PokemonSpriteService
import com.cobbledex.PokemonSearchTerms
import com.cobbledex.RecipeCatalogCache
import com.cobbledex.RecipeHandle
import com.cobbledex.RecipeViewerReloader
import com.cobbledex.SlotRole
import com.cobbledex.SpawnDataIndex
import com.cobbledex.SpawnDisplayHelper
import com.cobbledex.config.CobbleDexConfig
import dev.emi.emi.api.EmiPlugin
import dev.emi.emi.api.EmiRegistry
import dev.emi.emi.api.stack.Comparison
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
        val hasSync = SpawnDataIndex.loadState == SpawnDataIndex.LoadState.FULLY_LOADED
        val spawnCount = SpawnDataIndex.spawnsBySpecies.size

        DebugLog.info("EMI register() called (loadState=${SpawnDataIndex.loadState.name}, " +
            "dataVersion=${SpawnDataIndex.dataVersion}, spawns=$spawnCount species)")

        // Register Pokémon entries
        var registered = 0
        var formCount = 0
        for (species in SpawnDataIndex.allSpeciesNames) {
            val speciesInfo = SpawnDataIndex.getSpeciesInfo(species)
            if (speciesInfo?.isForm == true && !config.registerFormEntries) continue
            if (!PokemonSpriteService.canRender(species)) continue
            val stack = PokemonEmiStack(species)
            registry.addEmiStack(stack)
            registry.setDefaultComparison(stack, Comparison.DEFAULT_COMPARISON)
            for (term in PokemonSearchTerms.buildTerms(species, includeDisplayName = false)) {
                registry.addAlias(stack, Component.literal(term))
            }
            if (speciesInfo?.isForm == true) formCount++ else registered++
        }

        // Register categories, workstations, and recipes
        val registeredCats = mutableListOf<String>()
        for (def in DexCategory.ALL) {
            if (!def.isEnabled(config)) continue
            val cat = emiCategory(def)
            registry.addCategory(cat)
            registry.addWorkstation(cat, EmiStack.of(def.icon))

            val recipes = RecipeCatalogCache.getAllRecipes(def)
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

        override fun getCategory(): EmiRecipeCategory = emiCategory

        override fun getId(): ResourceLocation =
            ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "emi_${handle.recipeIdPath}")

        override fun getInputs(): List<EmiIngredient> {
            val pokemon = handle.slots.pokemon
                .filter { it.role == SlotRole.INPUT }
                .map { slot -> PokemonEmiStack(slot.species, slot.aspects) }
                .filterNot { it.isEmpty }
            val items = handle.slots.items
                .filter { it.role == SlotRole.INPUT }
                .mapNotNull { slot ->
                    val stack = SpawnDisplayHelper.resolveItemStack(slot.itemId)
                    if (!stack.isEmpty) EmiStack.of(stack) else null
                }
            val catalog = handle.slots.catalogInputIds.mapNotNull { itemId ->
                val stack = SpawnDisplayHelper.resolveItemStack(itemId)
                if (!stack.isEmpty) EmiStack.of(stack) else null
            }
            return pokemon + items + catalog
        }

        override fun getOutputs(): List<EmiStack> {
            val pokemon = handle.slots.pokemon
                .filter { it.role == SlotRole.OUTPUT }
                .map { slot -> PokemonEmiStack(slot.species, slot.aspects) }
                .filterNot { it.isEmpty }
            val items = handle.slots.items
                .filter { it.role == SlotRole.OUTPUT }
                .mapNotNull { slot ->
                    val stack = SpawnDisplayHelper.resolveItemStack(slot.itemId)
                    if (!stack.isEmpty) EmiStack.of(stack) else null
                }
            return pokemon + items
        }

        override fun getDisplayWidth(): Int = handle.width
        override fun getDisplayHeight(): Int = handle.height

        override fun supportsRecipeTree(): Boolean = def.supportsRecipeTree

        override fun addWidgets(widgets: dev.emi.emi.api.widget.WidgetHolder) {
            val slots = handle.slots

            for (slot in slots.pokemon) {
                val stack = PokemonEmiStack(slot.species, slot.aspects)
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
            widgets.addDrawable(0, 0, w, h) { gfx, mouseX, mouseY, _ ->
                handle.layout.render(gfx)
                val tooltip = handle.layout.getTooltipAt(mouseX, mouseY)
                if (tooltip != null) {
                    gfx.renderComponentTooltip(net.minecraft.client.Minecraft.getInstance().font, tooltip, mouseX, mouseY)
                }
            }
        }
    }
}
