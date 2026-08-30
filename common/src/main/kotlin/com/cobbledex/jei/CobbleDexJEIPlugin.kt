package com.cobbledex.jei

import com.cobbledex.CobbleDexMod
import com.cobbledex.DebugLog
import com.cobbledex.DexCategory
import com.cobbledex.PokemonItemCache
import com.cobbledex.RecipeHandle
import com.cobbledex.RecipeViewerReloader
import com.cobbledex.SlotRole
import com.cobbledex.SpawnDataIndex
import com.cobbledex.SpawnDisplayHelper
import com.cobbledex.ViewerParityGuard
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
import mezz.jei.api.runtime.IJeiRuntime
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

@Suppress("DEPRECATION")
open class CobbleDexJEIPlugin : IModPlugin {

    companion object {
        private val recipeTypes = mutableMapOf<String, RecipeType<GenericRecipe>>()
        private val addedRecipes = mutableMapOf<String, List<GenericRecipe>>()
        @Volatile var runtime: IJeiRuntime? = null
            private set

        fun recipeType(def: DexCategory): RecipeType<GenericRecipe> =
            recipeTypes.getOrPut(def.id) {
                RecipeType(
                    ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, def.id),
                    GenericRecipe::class.java
                )
            }

        /** Called by RecipeViewerReloader after server sync to push new recipes into JEI */
        @JvmStatic
        fun reloadRecipes() {
            val rt = runtime ?: return
            val manager = rt.recipeManager
            val config = CobbleDexConfig.get()
            for (def in DexCategory.ALL) {
                if (!def.isEnabled(config)) continue
                val type = recipeType(def)

                // Hide previously added recipes to avoid duplicates
                addedRecipes[def.id]?.let { old ->
                    if (old.isNotEmpty()) manager.hideRecipes(type, old)
                }

                val handles = def.buildAllRecipes()
                ViewerParityGuard.warn(def, handles, "JEI")
                val recipes = handles.map { GenericRecipe(it) }
                if (recipes.isNotEmpty()) {
                    manager.addRecipes(type, recipes)
                }
                addedRecipes[def.id] = recipes
            }

            // Re-register Pokémon ingredients so search index includes job names
            if (SpawnDataIndex.hasJobRules()) {
                try {
                    val config = CobbleDexConfig.get()
                    val queries = SpawnDataIndex.currentQueries()
                    val ingredientManager = rt.ingredientManager
                    val existing = ingredientManager.getAllIngredients(PokemonIngredientType).toList()
                    if (existing.isNotEmpty()) {
                        ingredientManager.removeIngredientsAtRuntime(PokemonIngredientType, existing)
                    }
                    val updated = SpawnDataIndex.allSpeciesNames
                        .filter { name ->
                            val info = queries.getSpeciesInfo(name)
                            if (info == null) false
                            else if (info.isForm) config.registerFormEntries
                            else info.baseSpeciesName == null
                        }
                        .filter { queries.shouldSurfaceSpecies(it) }
                        .filter { PokemonItemCache.canRender(it) }
                        .map { PokemonIngredient(it) }
                    ingredientManager.addIngredientsAtRuntime(PokemonIngredientType, updated)
                    DebugLog.info("JEI: Re-indexed ${updated.size} Pokémon ingredients with job data")
                } catch (e: Exception) {
                    DebugLog.once("jei-ingredient-reload") { "JEI ingredient reload failed: ${e.message}" }
                }
            }

            RecipeViewerReloader.jeiLastRegisteredVersion = SpawnDataIndex.dataVersion
            DebugLog.info("JEI: Reloaded recipes (dataVersion=${SpawnDataIndex.dataVersion})")
        }
    }

    override fun getPluginUid(): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "jei_plugin")

    override fun registerIngredients(registration: IModIngredientRegistration) {
        SpawnDataIndex.ensureLoaded()
        val config = CobbleDexConfig.get()
        val queries = SpawnDataIndex.currentQueries()
        val allPokemon = SpawnDataIndex.allSpeciesNames
            .filter { name ->
                val info = queries.getSpeciesInfo(name)
                if (info == null) false
                else if (info.isForm) config.registerFormEntries
                else true
            }
            .filter { queries.shouldSurfaceSpecies(it) }
            .filter { PokemonItemCache.canRender(it) }
            .map { PokemonIngredient(it) }

        registration.register(
            PokemonIngredientType,
            allPokemon,
            PokemonIngredientHelper(),
            PokemonIngredientRenderer()
        )
        val formCount = allPokemon.count { queries.isForm(it.species) }
        DebugLog.info("JEI: Registered ${allPokemon.size - formCount} Pokémon + $formCount form ingredients")

        // Moves as (never-shown) ingredients so the Moves-page name links can focus-navigate to
        // "who can learn this move". Also makes moves searchable.
        val moves = SpawnDataIndex.speciesByMove.keys.sorted().map { MoveIngredient(it) }
        registration.register(MoveIngredientType, moves, MoveIngredientHelper(), MoveIngredientRenderer())
        DebugLog.info("JEI: Registered ${moves.size} move ingredients")
    }

    override fun registerCategories(registration: IRecipeCategoryRegistration) {
        val helpers = registration.jeiHelpers
        val config = CobbleDexConfig.get()
        val registered = mutableListOf<String>()

        for (def in DexCategory.ALL) {
            if (!def.isEnabled(config)) continue
            registration.addRecipeCategories(GenericCategory(def, helpers))
            registered.add(def.id)
        }
        DebugLog.info("JEI categories registered (${registered.joinToString(" + ")})")
    }

    override fun registerRecipes(registration: IRecipeRegistration) {
        SpawnDataIndex.ensureLoaded()
        val config = CobbleDexConfig.get()

        for (def in DexCategory.ALL) {
            if (!def.isEnabled(config)) continue
            val handles = def.buildAllRecipes()
            ViewerParityGuard.warn(def, handles, "JEI")
            val recipes = handles.map { GenericRecipe(it) }
            if (recipes.isNotEmpty()) {
                registration.addRecipes(recipeType(def), recipes)
            }
            addedRecipes[def.id] = recipes
        }
        RecipeViewerReloader.jeiLastRegisteredVersion = SpawnDataIndex.dataVersion
        DebugLog.info("JEI: All recipes registered (dataVersion=${SpawnDataIndex.dataVersion})")
    }

    override fun onRuntimeAvailable(jeiRuntime: IJeiRuntime) {
        runtime = jeiRuntime
        DebugLog.info("JEI: Runtime captured")
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
        private val helpers: mezz.jei.api.helpers.IJeiHelpers,
    ) : IRecipeCategory<GenericRecipe> {

        private val guiHelper: IGuiHelper = helpers.guiHelper
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

            // Track which Pokémon are already in each role so we can add invisible
            // counterparts — this lets both R (recipe/output) and U (usage/input) find every entry.
            val inputPokemon = mutableListOf<PokemonIngredient>()
            val outputPokemon = mutableListOf<PokemonIngredient>()

            for (slot in slots.pokemon) {
                val ingredient = PokemonIngredient(slot.species, slot.aspects)
                val role = if (slot.role == SlotRole.INPUT) RecipeIngredientRole.INPUT else RecipeIngredientRole.OUTPUT
                builder.addSlot(role, slot.x, slot.y)
                    .setSlotName(slot.species)
                    .addIngredient(PokemonIngredientType, ingredient)
                if (slot.role == SlotRole.INPUT) inputPokemon.add(ingredient)
                else outputPokemon.add(ingredient)
            }

            val visibleInputSpecies = inputPokemon.map { it.species }.toSet()
            val visibleOutputSpecies = outputPokemon.map { it.species }.toSet()
            val hiddenOutputPokemon = handle.lookupOutputSpecies().filterNot { it in visibleOutputSpecies }
            val hiddenInputPokemon = handle.lookupInputSpecies().filterNot { it in visibleInputSpecies }

            if (hiddenOutputPokemon.isNotEmpty()) {
                val inv = builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT)
                for (species in hiddenOutputPokemon) inv.addIngredient(PokemonIngredientType, PokemonIngredient(species))
            }
            if (hiddenInputPokemon.isNotEmpty()) {
                val inv = builder.addInvisibleIngredients(RecipeIngredientRole.INPUT)
                for (species in hiddenInputPokemon) inv.addIngredient(PokemonIngredientType, PokemonIngredient(species))
            }

            for (slot in slots.items) {
                val stack = SpawnDisplayHelper.resolveItemStack(slot.itemId)
                if (!stack.isEmpty) {
                    val role = if (slot.role == SlotRole.INPUT) RecipeIngredientRole.INPUT else RecipeIngredientRole.OUTPUT
                    builder.addSlot(role, slot.x, slot.y)
                        .addItemStack(stack)
                }
            }

            if (slots.catalogInputIds.isNotEmpty()) {
                val invisible = builder.addInvisibleIngredients(RecipeIngredientRole.INPUT)
                for (itemId in slots.catalogInputIds) {
                    val stack = SpawnDisplayHelper.resolveItemStack(itemId)
                    if (!stack.isEmpty) invisible.addItemStack(stack)
                }
            }

            // Learner grid: declare the move as an (invisible) input so a name-link focus finds it.
            slots.moveKey?.let { move ->
                builder.addInvisibleIngredients(RecipeIngredientRole.INPUT)
                    .addIngredient(MoveIngredientType, MoveIngredient(move))
            }
        }

        override fun createRecipeExtras(
            builder: mezz.jei.api.gui.widgets.IRecipeExtrasBuilder,
            recipe: GenericRecipe,
            focuses: IFocusGroup,
        ) {
            for (link in recipe.handle.slots.moveLinks) {
                builder.addInputHandler(MoveLinkInputHandler(link, helpers.focusFactory))
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

        override fun getTooltipStrings(recipe: GenericRecipe, recipeSlotsView: IRecipeSlotsView, mouseX: Double, mouseY: Double): List<Component> {
            return recipe.handle.layout.getTooltipAt(mouseX.toInt(), mouseY.toInt()) ?: emptyList()
        }
    }

    /** Turns a move-name region on the Moves page into a click → "who can learn this move". */
    private class MoveLinkInputHandler(
        link: com.cobbledex.MoveLinkDef,
        private val focusFactory: mezz.jei.api.recipe.IFocusFactory,
    ) : mezz.jei.api.gui.inputs.IJeiInputHandler {

        private val move = link.moveName
        private val area = net.minecraft.client.gui.navigation.ScreenRectangle(link.x, link.y, link.width, link.height)

        override fun getArea(): net.minecraft.client.gui.navigation.ScreenRectangle = area

        override fun handleInput(
            mouseX: Double, mouseY: Double, input: mezz.jei.api.gui.inputs.IJeiUserInput,
        ): Boolean {
            if (input.key.value != 0) return false // left mouse only
            if (!input.isSimulate) {
                runtime?.recipesGui?.show(
                    focusFactory.createFocus(RecipeIngredientRole.INPUT, MoveIngredientType, MoveIngredient(move))
                )
            }
            return true
        }
    }
}
