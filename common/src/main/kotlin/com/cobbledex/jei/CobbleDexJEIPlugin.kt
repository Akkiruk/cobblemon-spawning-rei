package com.cobbledex.jei

import com.cobbledex.CategorySizer
import com.cobbledex.CobbleDexMod
import com.cobbledex.DebugLog
import com.cobbledex.DexCategory
import com.cobbledex.DiscoveryAliases
import com.cobbledex.PanelLayout
import com.cobbledex.PanelPage
import com.cobbledex.Paged
import com.cobbledex.PokemonItemCache
import com.cobbledex.RecipeBuildCache
import com.cobbledex.RecipeHandle
import com.cobbledex.RecipeViewerReloader
import com.cobbledex.SlotRole
import com.cobbledex.SpawnDataIndex
import com.cobbledex.SpawnDisplayHelper
import com.cobbledex.TmDiscStacks
import com.cobbledex.TmItemUtils
import com.cobbledex.ViewerParityGuard
import com.cobbledex.config.CobbleDexConfig
import com.cobbledex.contentFor
import com.cobbledex.paged
import mezz.jei.api.IModPlugin
import mezz.jei.api.constants.VanillaTypes
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.drawable.IDrawable
import mezz.jei.api.gui.ingredient.IRecipeSlotsView
import mezz.jei.api.helpers.IGuiHelper
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter
import mezz.jei.api.ingredients.subtypes.UidContext
import mezz.jei.api.recipe.IFocusGroup
import mezz.jei.api.recipe.RecipeIngredientRole
import mezz.jei.api.recipe.RecipeType
import mezz.jei.api.recipe.category.IRecipeCategory
import mezz.jei.api.registration.IExtraIngredientRegistration
import mezz.jei.api.registration.IModIngredientRegistration
import mezz.jei.api.registration.IRecipeCatalystRegistration
import mezz.jei.api.registration.IRecipeCategoryRegistration
import mezz.jei.api.registration.IRecipeRegistration
import mezz.jei.api.registration.ISubtypeRegistration
import mezz.jei.api.runtime.IJeiRuntime
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

@Suppress("DEPRECATION")
/**
 * Wraps [RecipeHandle]/[DexCategory] for JEI. Shared behavior - pagination ([RecipeHandle.paginate],
 * [RecipeHandle.contentFor]), the clickable move/category-jump links, and per-slot ingredient
 * resolution - belongs on [RecipeHandle]/[PanelLayout] and gets reused by [com.cobbledex.rei] and
 * [com.cobbledex.emi] too. If JEI needs something the other two viewers would also need, extend the
 * shared layer rather than adding a JEI-only copy here.
 */
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

        // ----- Incremental reload -----
        //
        // A reload used to hide + rebuild + add every category's recipes in one call - for a large
        // modpack (~1000+ species, ~10000+ recipes) that's several seconds on the render thread,
        // felt as a freeze right after joining a world (issue #43's remaining case: a reload that's
        // genuinely necessary, not the redundant one #43 also fixed). buildAllRecipes() itself is
        // cheap (RecipeHandle's layout/width/height are lazy), so the cost is JEI's own
        // IRecipeManager bookkeeping (search/ingredient re-indexing) - not something we can make
        // faster, only spread out. continueReload() below does a bounded slice of that work per
        // call and reports whether it's finished; RecipeViewerReloader drives it once per tick
        // instead of invoking one atomic method and hoping.

        /** One category's rebuilt recipes, queued for incremental hide+add. */
        private class ReloadUnit(val def: DexCategory, val recipes: List<GenericRecipe>)

        /** `addRecipes` calls of roughly this size each - enough ticks to smooth out even the
         *  Spawns category (by far the largest) without dragging a reload out for many seconds. */
        private const val RECIPES_PER_TICK = 250

        @Volatile private var reloadUnits: List<ReloadUnit>? = null
        @Volatile private var reloadTargetVersion = -1L
        private var unitIndex = 0
        private var hideOffsetInUnit = 0
        private var offsetInUnit = 0
        private var addedThisUnit = mutableListOf<GenericRecipe>()

        /**
         * Advances an in-progress reload for [targetVersion] by one tick's worth of work (starting
         * a new one if none is running, or the target changed). Returns true once fully caught up.
         * Safe to call every tick - a no-op call (nothing pending) is just a version check.
         */
        @JvmStatic
        fun continueReload(targetVersion: Long): Boolean {
            val rt = runtime ?: return true
            if (reloadUnits == null || reloadTargetVersion != targetVersion) {
                val config = CobbleDexConfig.get()
                reloadUnits = DexCategory.ALL.filter { it.isEnabled(config) }.map { def ->
                    val handles = try { def.buildAllRecipes() } catch (e: Exception) {
                        DebugLog.warn("JEI reload: ${def.id} buildAllRecipes failed: ${e.message}")
                        emptyList()
                    }
                    ViewerParityGuard.warn(def, handles, "JEI")
                    ReloadUnit(def, handles.paged().map { GenericRecipe(it) })
                }
                reloadTargetVersion = targetVersion
                unitIndex = 0
                hideOffsetInUnit = 0
                offsetInUnit = 0
                addedThisUnit = mutableListOf()
            }

            val units = reloadUnits ?: return true
            val manager = rt.recipeManager
            // One shared, per-tick ceiling covers both hiding the old recipes and adding the new
            // ones - the biggest category (Spawns) can have thousands of each, so neither side gets
            // to do unbounded work in a single call.
            var budget = RECIPES_PER_TICK

            while (budget > 0 && unitIndex < units.size) {
                val unit = units[unitIndex]
                val type = recipeType(unit.def)
                val old = addedRecipes[unit.def.id] ?: emptyList()

                if (hideOffsetInUnit < old.size) {
                    val takeHide = minOf(budget, old.size - hideOffsetInUnit)
                    manager.hideRecipes(type, old.subList(hideOffsetInUnit, hideOffsetInUnit + takeHide))
                    hideOffsetInUnit += takeHide
                    budget -= takeHide
                    if (hideOffsetInUnit < old.size) break // rest of the old list next tick
                }

                val take = minOf(budget, unit.recipes.size - offsetInUnit)
                if (take > 0) {
                    val slice = unit.recipes.subList(offsetInUnit, offsetInUnit + take)
                    manager.addRecipes(type, slice)
                    addedThisUnit.addAll(slice)
                    offsetInUnit += take
                    budget -= take
                }

                if (offsetInUnit >= unit.recipes.size) {
                    addedRecipes[unit.def.id] = addedThisUnit
                    addedThisUnit = mutableListOf()
                    unitIndex++
                    hideOffsetInUnit = 0
                    offsetInUnit = 0
                } else {
                    break // category has more left than this tick's budget - continue it next tick
                }
            }

            if (unitIndex < units.size) return false // more categories queued for later ticks

            finishReload(rt, targetVersion)
            reloadUnits = null
            return true
        }

        private fun finishReload(rt: IJeiRuntime, targetVersion: Long) {
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

            // Re-register native TM discs so a server with a different TM set (or none at all)
            // doesn't keep whatever was registered from this client's own files at startup -
            // registerExtraIngredients only ever runs once, before any server is connected to.
            try {
                val ingredientManager = rt.ingredientManager
                val existingDiscs = ingredientManager.getAllIngredients(VanillaTypes.ITEM_STACK)
                    .filter { TmDiscStacks.isTmDiscItem(it.item) }
                if (existingDiscs.isNotEmpty()) {
                    ingredientManager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, existingDiscs)
                }
                val updatedDiscs = TmDiscStacks.all().map { it.stack }
                if (updatedDiscs.isNotEmpty()) {
                    ingredientManager.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, updatedDiscs)
                }
                if (existingDiscs.isNotEmpty() || updatedDiscs.isNotEmpty()) {
                    DebugLog.info("JEI: Re-indexed ${updatedDiscs.size} native TM disc ingredients")
                }
            } catch (e: Exception) {
                DebugLog.once("jei-tm-disc-reload") { "JEI TM disc reload failed: ${e.message}" }
            }

            RecipeViewerReloader.jeiLastRegisteredVersion = targetVersion
            DebugLog.info("JEI: Reloaded recipes (dataVersion=$targetVersion)")
        }
    }

    override fun getPluginUid(): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "jei_plugin")

    /**
     * Tells JEI that two `cobblemon:technical_machine` stacks carrying different moves in their
     * `cobblemon:tm_move` data component are different ingredients - without this, JEI's default
     * uniqueId is just the item's registry name (see `StackHelper.getUniqueIdentifierForStack`),
     * so every TM disc would collapse to one indistinguishable entry no matter how many get
     * registered as ingredients below.
     *
     * Runs in JEI's first plugin-loading phase, before ingredients/recipes and before this mod's
     * own data is guaranteed loaded - so this is gated purely on the item existing in the registry
     * (always true by this point; Minecraft's registries are populated at bootstrap, long before
     * JEI plugins load), never on [SpawnDataIndex] or [TmDiscStacks]. On pre-1.8.0 Cobblemon the
     * item may not exist at all, or may exist without the component - either way this just
     * no-ops (empty Optional, or [TmItemUtils.extractMoveFromStack] returning null for every
     * stack, which per [ISubtypeInterpreter]'s own contract means "no subtype data", the same as
     * not registering an interpreter at all).
     */
    override fun registerItemSubtypes(registration: ISubtypeRegistration) {
        try {
            val item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(TmItemUtils.NATIVE_TM_ID))
                .orElse(null) ?: return
            registration.registerSubtypeInterpreter(item, object : ISubtypeInterpreter<ItemStack> {
                override fun getSubtypeData(ingredient: ItemStack, context: UidContext): Any? =
                    TmItemUtils.extractMoveFromStack(ingredient)

                // Only reached for old saved config referencing a string uid from before this
                // interpreter existed, so there's never real legacy data to preserve here - but
                // returning the move name instead of always "" still gives each move a distinct
                // legacy key rather than collapsing them all into one on that one-time migration.
                override fun getLegacyStringSubtypeInfo(ingredient: ItemStack, context: UidContext): String =
                    TmItemUtils.extractMoveFromStack(ingredient) ?: ""
            })
        } catch (_: Throwable) {}
    }

    /**
     * Native TM discs have no creative-tab entry ([TmDiscStacks]'s own doc), so they're invisible
     * to JEI's default item scan - this is the official hook for exactly that case ("extra
     * ingredients... not already in the creative menu"). REI/EMI use their own equivalent
     * per-viewer registration for the same stacks; this is JEI's.
     */
    override fun registerExtraIngredients(registration: IExtraIngredientRegistration) {
        SpawnDataIndex.ensureLoaded()
        val discs = TmDiscStacks.all()
        if (discs.isEmpty()) return
        registration.addExtraItemStacks(discs.map { it.stack })
        DebugLog.info("JEI: Registered ${discs.size} native TM disc ingredients")
    }

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

        // Moves exist only as an invisible ingredient type: the Moves-page name links and
        // "who can learn this move" lookups use MoveIngredientType via addInvisibleIngredients()
        // and createFocus(), neither of which requires the type's ingredient list to be non-empty.
        // Registering the real move list here would put every move (they have no ItemStack, so
        // MoveIngredientRenderer is a no-op) into JEI's visible ingredient panel as blank tiles
        // alongside real items like TM discs and eggs.
        registration.register(MoveIngredientType, emptyList(), MoveIngredientHelper(), MoveIngredientRenderer())
    }

    override fun registerIngredientAliases(registration: mezz.jei.api.registration.IIngredientAliasRegistration) {
        SpawnDataIndex.ensureLoaded()
        val config = CobbleDexConfig.get()
        val queries = SpawnDataIndex.currentQueries()
        var count = 0
        for (species in SpawnDataIndex.allSpeciesNames) {
            val info = queries.getSpeciesInfo(species) ?: continue
            if (info.isForm && !config.registerFormEntries) continue
            if (!queries.shouldSurfaceSpecies(species)) continue
            if (!PokemonItemCache.canRender(species)) continue
            val aliases = DiscoveryAliases.pokemonAliasesForJei(species)
            if (aliases.isNotEmpty()) {
                registration.addAliases(PokemonIngredientType, PokemonIngredient(species), aliases)
                count++
            }
        }
        DebugLog.info("JEI: Registered search aliases for $count Pokémon")

        var tmCount = 0
        for (entry in TmDiscStacks.all()) {
            val aliases = DiscoveryAliases.moveAliasesForJei(entry.moveName)
            if (aliases.isNotEmpty()) {
                registration.addAliases(VanillaTypes.ITEM_STACK, entry.stack, aliases)
                tmCount++
            }
        }
        if (tmCount > 0) DebugLog.info("JEI: Registered search aliases for $tmCount native TM discs")
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
            // Shared with CategorySizer - JEI's own registerCategories() (just before this) already
            // forced this category's recipes to be built once, to measure width/height. Reusing that
            // build here instead of redoing it saves rebuilding the whole category from scratch.
            val handles = RecipeBuildCache.getOrBuild(def)
            ViewerParityGuard.warn(def, handles, "JEI")
            val recipes = handles.paged().map { GenericRecipe(it) }
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

    // No recipe catalysts: the category icons (Diamond, Paper, XP Bottle, …) are arbitrary markers,
    // not real workstations, so registering them just makes looking up a diamond surface "Item
    // Drops". REI omits them too - categories are still reachable from JEI's category list.

    // ----- Generic Recipe wrapping RecipeHandle -----

    data class GenericRecipe(val paged: Paged) {
        val handle: RecipeHandle get() = paged.handle
        val page: PanelPage get() = paged.page
    }

    // ----- Generic Category wrapping DexCategory -----

    @Suppress("DEPRECATION")
    class GenericCategory(
        private val def: DexCategory,
        private val helpers: mezz.jei.api.helpers.IJeiHelpers,
    ) : IRecipeCategory<GenericRecipe> {

        private val guiHelper: IGuiHelper = helpers.guiHelper
        private val iconDrawable: IDrawable = guiHelper.createDrawableItemStack(ItemStack(def.icon))

        // JEI sizes a category to its single largest recipe and keeps that size for every recipe.
        // The blank background is rebuilt whenever CategorySizer's bounds change (they grow after a
        // server sync adds data), so it never goes stale.
        private var bgSize: CategorySizer.PanelSize? = null
        private var bgDrawable: IDrawable = guiHelper.createBlankDrawable(1, 1)

        private fun background(): IDrawable {
            val s = def.maxSize
            if (s != bgSize) {
                bgSize = s
                bgDrawable = guiHelper.createBlankDrawable(s.width, s.height)
            }
            return bgDrawable
        }

        override fun getRecipeType(): RecipeType<GenericRecipe> = recipeType(def)
        override fun getTitle(): Component = Component.translatable(def.titleKey)
        override fun getBackground(): IDrawable = background()
        override fun getIcon(): IDrawable = iconDrawable

        // Small recipes (a 3-drop Pokémon, a short evolution) would otherwise sit lost in that big
        // area. Centre them horizontally, keep them at the top, and frame the content - the
        // per-recipe framing REI (createRecipeBase) and EMI (per-recipe size) already give.
        private fun offsetX(handle: RecipeHandle): Int {
            val w = (background().width - handle.width) / 2
            return w.coerceAtLeast(0)
        }

        private fun offsetY(handle: RecipeHandle): Int = 0

        override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: GenericRecipe, focuses: IFocusGroup) {
            val handle = recipe.handle
            val slots = handle.slots
            val content = handle.contentFor(recipe.page)
            val dx = offsetX(handle)
            val dy = offsetY(handle)

            // Track which Pokémon are already in each role so we can add invisible
            // counterparts - this lets both R (recipe/output) and U (usage/input) find every entry.
            val inputPokemon = mutableListOf<PokemonIngredient>()
            val outputPokemon = mutableListOf<PokemonIngredient>()

            for (slot in content.pokemonSlots) {
                val ingredient = PokemonIngredient(slot.species, slot.aspects)
                val role = if (slot.role == SlotRole.INPUT) RecipeIngredientRole.INPUT else RecipeIngredientRole.OUTPUT
                val slotBuilder = builder.addSlot(role, slot.x + dx, slot.y + dy)
                    .setSlotName(slot.species)
                    .addIngredient(PokemonIngredientType, ingredient)
                // JEI resolves a slot's own ingredient tooltip before ever consulting the category's
                // tooltip zones (see PokemonSlotDef.cellTooltip) - grid pages (move learners, item
                // droppers) need their per-cell info to win over the generic species tooltip when
                // the icon itself is hovered, not just in the gaps between icons.
                val cellTooltip = slot.cellTooltip
                if (cellTooltip != null) {
                    slotBuilder.addRichTooltipCallback { _, tooltip ->
                        tooltip.clear()
                        tooltip.addAll(cellTooltip)
                    }
                }
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

            for (slot in content.itemSlots) {
                val stack = SpawnDisplayHelper.resolveItemStack(slot.itemId)
                if (!stack.isEmpty) {
                    val role = when (slot.role) {
                        SlotRole.INPUT -> RecipeIngredientRole.INPUT
                        SlotRole.OUTPUT -> RecipeIngredientRole.OUTPUT
                        SlotRole.DISPLAY -> RecipeIngredientRole.RENDER_ONLY
                    }
                    builder.addSlot(role, slot.x + dx, slot.y + dy)
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

            // Native TM disc, declared invisibly the same way moveKey/catalogInputIds are above -
            // this is what lets "R"/"U" on the exact disc stack (matched via the subtype
            // interpreter in registerItemSubtypes) find these recipes, rather than adding a new
            // visible icon to this page's own hand-drawn layout. Direction matters: the crafting
            // recipe's disc is its OUTPUT ("R" on the disc shows how to craft it); the
            // move-learners recipe's disc is its INPUT ("U" on the disc shows what it teaches).
            slots.tmDiscMove?.let { move ->
                TmDiscStacks.forMove(move)?.let { stack ->
                    builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT).addItemStack(stack)
                }
            }
            slots.tmDiscMoveInput?.let { move ->
                TmDiscStacks.forMove(move)?.let { stack ->
                    builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addItemStack(stack)
                }
            }
        }

        override fun createRecipeExtras(
            builder: mezz.jei.api.gui.widgets.IRecipeExtrasBuilder,
            recipe: GenericRecipe,
            focuses: IFocusGroup,
        ) {
            val dx = offsetX(recipe.handle)
            val dy = offsetY(recipe.handle)
            val content = recipe.handle.contentFor(recipe.page)
            for (link in content.moveLinks) {
                builder.addInputHandler(LinkInputHandler(link.x + dx, link.y + dy, link.width, link.height) {
                    runtime?.recipesGui?.show(
                        helpers.focusFactory.createFocus(RecipeIngredientRole.INPUT, MoveIngredientType, MoveIngredient(link.moveName))
                    )
                })
            }
            for (link in content.categoryLinks) {
                builder.addInputHandler(LinkInputHandler(link.x + dx, link.y + dy, link.width, link.height) {
                    val gui = runtime?.recipesGui
                    val def = DexCategory.ALL.firstOrNull { it.id == link.categoryId }
                    if (gui != null && def != null) {
                        gui.showTypes(listOf(recipeType(def)))
                    } else {
                        gui?.show(helpers.focusFactory.createFocus(RecipeIngredientRole.INPUT, PokemonIngredientType, PokemonIngredient(link.species)))
                    }
                })
            }
        }

        override fun draw(recipe: GenericRecipe, recipeSlotsView: IRecipeSlotsView, guiGraphics: GuiGraphics, mouseX: Double, mouseY: Double) {
            val handle = recipe.handle
            val page = recipe.page
            val dx = offsetX(handle)
            val dy = offsetY(handle)
            val w = handle.width
            val h = page.height

            // Content-sized surface the mod owns, centred in JEI's category area. Opaque so panel
            // text keeps its contrast whatever theme JEI/the pack uses (issue #42).
            PanelLayout.renderSurface(guiGraphics, dx, dy, w, h)

            guiGraphics.pose().pushPose()
            guiGraphics.pose().translate(dx.toFloat(), dy.toFloat(), 0f)
            handle.layout.renderRange(guiGraphics, page.top, page.bottom, page.shift)
            guiGraphics.pose().popPose()
        }

        override fun getTooltipStrings(recipe: GenericRecipe, recipeSlotsView: IRecipeSlotsView, mouseX: Double, mouseY: Double): List<Component> {
            val dx = offsetX(recipe.handle)
            val dy = offsetY(recipe.handle)
            val zones = recipe.handle.contentFor(recipe.page).tooltipZones
            return recipe.handle.layout.getTooltipAt((mouseX - dx).toInt(), (mouseY - dy).toInt(), zones) ?: emptyList()
        }
    }

    /**
     * A clickable region that runs [onClick] on left-click - shared by the move-name link (opens
     * "who can learn this move") and the category-jump link (opens another category's page), which
     * otherwise differed only in what clicking them does.
     */
    private class LinkInputHandler(
        x: Int, y: Int, width: Int, height: Int,
        private val onClick: () -> Unit,
    ) : mezz.jei.api.gui.inputs.IJeiInputHandler {

        private val area = net.minecraft.client.gui.navigation.ScreenRectangle(x, y, width, height)

        override fun getArea(): net.minecraft.client.gui.navigation.ScreenRectangle = area

        override fun handleInput(
            mouseX: Double, mouseY: Double, input: mezz.jei.api.gui.inputs.IJeiUserInput,
        ): Boolean {
            if (input.key.value != 0) return false // left mouse only
            if (!input.isSimulate) onClick()
            return true
        }
    }
}
