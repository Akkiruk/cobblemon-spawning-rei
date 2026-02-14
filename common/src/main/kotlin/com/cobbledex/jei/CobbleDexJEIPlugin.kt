package com.cobbledex.jei

import com.cobbledex.CobbleDexMod
import com.cobbledex.DebugLog
import com.cobbledex.DexCategory
import com.cobbledex.PokemonItemCache
import com.cobbledex.RecipeHandle
import com.cobbledex.SlotRole
import com.cobbledex.SpawnDataIndex
import com.cobbledex.SpawnDisplayHelper
import com.cobbledex.config.CobbleDexConfig
import mezz.jei.api.IModPlugin
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.drawable.IDrawable
import mezz.jei.api.gui.ingredient.IRecipeSlotsView
import mezz.jei.api.helpers.IGuiHelper
import mezz.jei.api.recipe.IFocusGroup
import mezz.jei.api.recipe.RecipeIngredientRole
import mezz.jei.api.recipe.RecipeType
import mezz.jei.api.recipe.category.IRecipeCategory
import mezz.jei.api.registration.IModIngredientRegistration
import mezz.jei.api.registration.IRecipeCatalystRegistration
import mezz.jei.api.registration.IRecipeCategoryRegistration
import mezz.jei.api.registration.IRecipeRegistration
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

@Suppress("DEPRECATION")
open class CobbleDexJEIPlugin : IModPlugin {

    companion object {
        private val recipeTypes = mutableMapOf<String, RecipeType<GenericRecipe>>()

        fun recipeType(def: DexCategory): RecipeType<GenericRecipe> =
            recipeTypes.getOrPut(def.id) {
                RecipeType(
                    ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, def.id),
                    GenericRecipe::class.java
                )
            }
    }

    override fun getPluginUid(): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "jei_plugin")

    override fun registerIngredients(registration: IModIngredientRegistration) {
        SpawnDataIndex.ensureLoaded()
        val allPokemon = SpawnDataIndex.allSpeciesNames
            .filter { PokemonItemCache.canRender(it) }
            .map { PokemonIngredient(it) }

        registration.register(
            PokemonIngredientType,
            allPokemon,
            PokemonIngredientHelper(),
            PokemonIngredientRenderer()
        )
        DebugLog.info("JEI: Registered ${allPokemon.size} Pokémon ingredients")
    }

    override fun registerCategories(registration: IRecipeCategoryRegistration) {
        val guiHelper = registration.jeiHelpers.guiHelper
        val config = CobbleDexConfig.get()
        val registered = mutableListOf<String>()

        for (def in DexCategory.ALL) {
            if (!def.isEnabled(config)) continue
            registration.addRecipeCategories(GenericCategory(def, guiHelper))
            registered.add(def.id)
        }
        DebugLog.info("JEI categories registered (${registered.joinToString(" + ")})")
    }

    override fun registerRecipes(registration: IRecipeRegistration) {
        SpawnDataIndex.ensureLoaded()
        val config = CobbleDexConfig.get()

        for (def in DexCategory.ALL) {
            if (!def.isEnabled(config)) continue
            val recipes = def.buildAllRecipes().map { GenericRecipe(it) }
            if (recipes.isNotEmpty()) {
                registration.addRecipes(recipeType(def), recipes)
            }
        }
        DebugLog.info("JEI: All recipes registered")
    }

    override fun registerRecipeCatalysts(registration: IRecipeCatalystRegistration) {
        val config = CobbleDexConfig.get()
        for (def in DexCategory.ALL) {
            if (!def.isEnabled(config)) continue
            registration.addRecipeCatalyst(ItemStack(def.icon), recipeType(def))
        }
    }

    // ----- Generic Recipe wrapping RecipeHandle -----

    data class GenericRecipe(val handle: RecipeHandle)

    // ----- Generic Category wrapping DexCategory -----

    @Suppress("DEPRECATION")
    class GenericCategory(
        private val def: DexCategory,
        guiHelper: IGuiHelper,
    ) : IRecipeCategory<GenericRecipe> {

        private val recipeArrow: IDrawable = guiHelper.getRecipeArrow()
        private val background: IDrawable = guiHelper.createBlankDrawable(
            def.maxSize().width,
            def.maxSize().height
        )
        private val iconDrawable: IDrawable = guiHelper.createDrawableItemStack(ItemStack(def.icon))

        override fun getRecipeType(): RecipeType<GenericRecipe> = recipeType(def)
        override fun getTitle(): Component = Component.translatable(def.titleKey)
        override fun getBackground(): IDrawable = background
        override fun getIcon(): IDrawable = iconDrawable

        override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: GenericRecipe, focuses: IFocusGroup) {
            val handle = recipe.handle
            val slots = handle.slots

            for (slot in slots.pokemon) {
                val role = if (slot.role == SlotRole.INPUT) RecipeIngredientRole.INPUT else RecipeIngredientRole.OUTPUT
                builder.addSlot(role, slot.x, slot.y)
                    .setSlotName(slot.species)
                    .addIngredient(PokemonIngredientType, PokemonIngredient(slot.species, slot.aspects))
            }

            for (slot in slots.items) {
                val stack = SpawnDisplayHelper.resolveItemStack(slot.itemId)
                if (!stack.isEmpty) {
                    val role = if (slot.role == SlotRole.INPUT) RecipeIngredientRole.INPUT else RecipeIngredientRole.OUTPUT
                    builder.addSlot(role, slot.x, slot.y)
                        .addItemStack(stack)
                }
            }
        }

        override fun draw(recipe: GenericRecipe, recipeSlotsView: IRecipeSlotsView, guiGraphics: GuiGraphics, mouseX: Double, mouseY: Double) {
            val handle = recipe.handle
            val slots = handle.slots

            if (slots.hasArrow) {
                recipeArrow.draw(guiGraphics, slots.arrowX, slots.arrowY)
            }

            handle.layout.render(guiGraphics)
        }
    }
}
