package com.cobbledex.emi

import com.cobbledex.CobbleDexMod
import com.cobbledex.DebugLog
import com.cobbledex.DexCategory
import com.cobbledex.PokemonItemCache
import com.cobbledex.RecipeHandle
import com.cobbledex.SlotRole
import com.cobbledex.SpawnDataIndex
import com.cobbledex.SpawnDisplayHelper
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

        // Register Pokémon entries
        var registered = 0
        for (species in SpawnDataIndex.allSpeciesNames) {
            val item = PokemonItemCache.getItem(species) ?: continue
            if (item.isEmpty) continue
            val stack = EmiStack.of(item)
            registry.addEmiStack(stack)
            registry.setDefaultComparison(stack) { _ ->
                dev.emi.emi.api.stack.Comparison.compareComponents()
            }
            registered++
        }

        // Register categories, workstations, and recipes
        val registeredCats = mutableListOf<String>()
        for (def in DexCategory.ALL) {
            if (!def.isEnabled(config)) continue
            val cat = emiCategory(def)
            registry.addCategory(cat)
            registry.addWorkstation(cat, EmiStack.of(def.icon))

            val recipes = def.buildAllRecipes()
            for (handle in recipes) {
                registry.addRecipe(GenericEmiRecipe(handle, cat, def))
            }
            registeredCats.add(def.id)
        }

        DebugLog.info("EMI: Registered $registered Pokémon, categories: ${registeredCats.joinToString(" + ")}")
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

        override fun getInputs(): List<EmiIngredient> = try {
            val pokemon = handle.slots.pokemon
                .mapNotNull { slot ->
                    val item = PokemonItemCache.getItem(slot.species)
                    if (item != null && !item.isEmpty) EmiStack.of(item) else null
                }
            val items = handle.slots.items
                .filter { it.role == SlotRole.INPUT }
                .mapNotNull { slot ->
                    val stack = SpawnDisplayHelper.resolveItemStack(slot.itemId)
                    if (!stack.isEmpty) EmiStack.of(stack) else null
                }
            pokemon + items
        } catch (_: Exception) { emptyList() }

        override fun getOutputs(): List<EmiStack> = try {
            val pokemon = handle.slots.pokemon
                .mapNotNull { slot ->
                    val item = PokemonItemCache.getItem(slot.species)
                    if (item != null && !item.isEmpty) EmiStack.of(item) else null
                }
            val items = handle.slots.items
                .filter { it.role == SlotRole.OUTPUT }
                .mapNotNull { slot ->
                    val stack = SpawnDisplayHelper.resolveItemStack(slot.itemId)
                    if (!stack.isEmpty) EmiStack.of(stack) else null
                }
            pokemon + items
        } catch (_: Exception) { emptyList() }

        override fun getDisplayWidth(): Int = try { handle.width } catch (_: Exception) { 200 }
        override fun getDisplayHeight(): Int = try { handle.height } catch (_: Exception) { 200 }

        override fun supportsRecipeTree(): Boolean = def.supportsRecipeTree

        override fun addWidgets(widgets: dev.emi.emi.api.widget.WidgetHolder) {
            try {
                val slots = handle.slots

                for (slot in slots.pokemon) {
                    val item = PokemonItemCache.getItem(slot.species)
                    if (item != null && !item.isEmpty) {
                        widgets.addSlot(EmiStack.of(item), slot.x, slot.y).recipeContext(this)
                    }
                }

                for (slot in slots.items) {
                    val stack = SpawnDisplayHelper.resolveItemStack(slot.itemId)
                    if (!stack.isEmpty) {
                        widgets.addSlot(EmiStack.of(stack), slot.x, slot.y).recipeContext(this)
                    }
                }
            } catch (e: Exception) {
                DebugLog.once("emi-slots-${handle.recipeIdPath}") { "Slot setup failed: ${e.message}" }
            }

            val w = try { handle.width } catch (_: Exception) { 200 }
            val h = try { handle.height } catch (_: Exception) { 200 }
            widgets.addDrawable(0, 0, w, h) { gfx, _, _, _ ->
                try {
                    handle.layout.render(gfx)
                } catch (_: Exception) {}
            }
        }
    }
}
