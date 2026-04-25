package com.cobbledex.rei

import com.cobbledex.CobbleDexMod
import com.cobbledex.DebugLog
import com.cobbledex.DexCategory
import com.cobbledex.PokemonSpriteService
import com.cobbledex.RecipeHandle
import com.cobbledex.RecipeViewerReloader
import com.cobbledex.SlotRole
import com.cobbledex.SpawnDataIndex
import com.cobbledex.SpawnDisplayHelper
import com.cobbledex.config.CobbleDexConfig
import com.cobbledex.rei.entry.PokemonEntry
import com.cobbledex.rei.entry.PokemonEntryDefinition
import com.cobbledex.rei.entry.PokemonEntryType
import me.shedaniel.math.Point
import me.shedaniel.math.Rectangle
import me.shedaniel.rei.api.client.plugins.REIClientPlugin
import me.shedaniel.rei.api.client.gui.Renderer
import me.shedaniel.rei.api.client.gui.widgets.Widget
import me.shedaniel.rei.api.client.gui.widgets.Widgets
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry
import me.shedaniel.rei.api.client.registry.display.DynamicDisplayGenerator
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry
import me.shedaniel.rei.api.client.view.ViewSearchBuilder
import me.shedaniel.rei.api.common.category.CategoryIdentifier
import me.shedaniel.rei.api.common.display.Display
import me.shedaniel.rei.api.common.entry.EntryIngredient
import me.shedaniel.rei.api.common.entry.EntryStack
import me.shedaniel.rei.api.common.entry.type.EntryTypeRegistry
import me.shedaniel.rei.api.common.util.EntryStacks
import me.shedaniel.rei.api.client.registry.display.DisplayCategory
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import java.util.Optional

open class CobbleDexREIPlugin : REIClientPlugin {

    companion object {
        private val categoryIds = mutableMapOf<String, CategoryIdentifier<GenericDisplay>>()

        private fun buildIndexedPokemonEntries(config: CobbleDexConfig = CobbleDexConfig.get()): List<EntryStack<PokemonEntry>> {
            return SpawnDataIndex.allSpeciesNames.mapNotNull { species ->
                val info = SpawnDataIndex.getSpeciesInfo(species) ?: return@mapNotNull null
                if (info.isForm && !config.registerFormEntries) return@mapNotNull null
                if (!PokemonSpriteService.canRender(species)) return@mapNotNull null
                EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(species))
            }
        }

        @JvmStatic
        fun reloadEntries() {
            val registry = EntryRegistry.getInstance()
            try {
                registry.removeEntryIf { entry -> entry.value is PokemonEntry }
                val updated = buildIndexedPokemonEntries()
                if (updated.isNotEmpty()) {
                    registry.addEntries(updated)
                }
                registry.refilter()
                RecipeViewerReloader.reiEntriesLastRegisteredVersion = SpawnDataIndex.dataVersion
                val formCount = updated.count {
                    val value = it.value as? PokemonEntry ?: return@count false
                    SpawnDataIndex.isForm(value.species)
                }
                DebugLog.info("REI: Re-indexed ${updated.size - formCount} Pokémon + $formCount forms (dataVersion=${SpawnDataIndex.dataVersion})")
            } catch (e: Exception) {
                DebugLog.warn("REI entry reload failed: ${e.message}")
            }
        }

        fun categoryId(def: DexCategory): CategoryIdentifier<GenericDisplay> =
            categoryIds.getOrPut(def.id) { CategoryIdentifier.of(CobbleDexMod.MOD_ID, def.id) }
    }

    override fun registerEntryTypes(registry: EntryTypeRegistry) {
        try {
            registry.register(PokemonEntryType.POKEMON.id, PokemonEntryDefinition())
            DebugLog.info("Pokémon entry type registered")
        } catch (e: Exception) {
            DebugLog.warn("registerEntryTypes failed: ${e.message}")
        }
    }

    private fun ensureEntryTypeAvailable() {
        try {
            PokemonEntryType.POKEMON.definition
        } catch (_: Exception) {
            try {
                EntryTypeRegistry.getInstance().register(PokemonEntryType.POKEMON.id, PokemonEntryDefinition())
            } catch (e: Exception) {
                DebugLog.warnOnce("rei-entry-type") { "Failed to register PokemonEntryType: ${e.message}" }
            }
        }
    }

    override fun registerCategories(registry: CategoryRegistry) {
        ensureEntryTypeAvailable()
        val config = CobbleDexConfig.get()
        val registered = mutableListOf<String>()
        for (def in DexCategory.ALL) {
            if (!def.isEnabled(config)) continue
            registry.add(GenericCategory(def))
            registered.add(def.id)
        }
        DebugLog.info("REI categories registered (${registered.joinToString(" + ")})")
    }

    override fun registerDisplays(registry: DisplayRegistry) {
        ensureEntryTypeAvailable()
        SpawnDataIndex.ensureLoaded()
        val config = CobbleDexConfig.get()

        for (def in DexCategory.ALL) {
            if (!def.isEnabled(config)) continue
            if (def is com.cobbledex.NatureDex) {
                def.buildAllRecipes().map { GenericDisplay(it, def) }.forEach { registry.add(it) }
            } else {
                registry.registerDisplayGenerator(categoryId(def), GenericDisplayGenerator(def))
            }
        }
        DebugLog.info("Registered dynamic display generators")
    }

    override fun registerEntries(registry: EntryRegistry) {
        ensureEntryTypeAvailable()
        SpawnDataIndex.ensureLoaded()
        val config = CobbleDexConfig.get()

        var registered = 0
        var formCount = 0
        var hidden = 0
        for (stack in buildIndexedPokemonEntries(config)) {
            val pokemon = stack.value ?: continue
            val info = SpawnDataIndex.getSpeciesInfo(pokemon.species) ?: continue
            try {
                registry.addEntry(stack)
                if (info.isForm) formCount++ else registered++
            } catch (e: Exception) {
                DebugLog.once("entry-fail-${pokemon.species}") { "Entry registration failed for ${pokemon.species}: ${e.message}" }
                hidden++
            }
        }
        RecipeViewerReloader.reiEntriesLastRegisteredVersion = SpawnDataIndex.dataVersion
        DebugLog.info("Registered $registered Pokémon entries + $formCount forms ($hidden hidden — no model)")
        DebugLog.printSummary()
    }

    // ----- Generic Display wrapping RecipeHandle -----

    class GenericDisplay(val handle: RecipeHandle, val def: DexCategory) : Display {

        private val cachedInputEntries: List<EntryIngredient> by lazy {
            val pokemon = handle.inputSpecies.map {
                EntryIngredient.of(EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(it)))
            }
            val catalog = handle.slots.catalogInputIds.mapNotNull { itemId ->
                val stack = SpawnDisplayHelper.resolveItemStack(itemId)
                if (!stack.isEmpty) EntryIngredient.of(EntryStacks.of(stack)) else null
            }
            val itemInputs = handle.slots.items
                .filter { it.role == SlotRole.INPUT }
                .mapNotNull { slot ->
                    val stack = SpawnDisplayHelper.resolveItemStack(slot.itemId)
                    if (!stack.isEmpty) EntryIngredient.of(EntryStacks.of(stack)) else null
                }
            pokemon + catalog + itemInputs
        }

        private val cachedOutputEntries: List<EntryIngredient> by lazy {
            val pokemonEntries = handle.outputSpecies.map {
                EntryIngredient.of(EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(it)))
            }
            val itemEntries = handle.slots.items
                .filter { it.role == SlotRole.OUTPUT }
                .mapNotNull { slot ->
                    val stack = SpawnDisplayHelper.resolveItemStack(slot.itemId)
                    if (!stack.isEmpty) EntryIngredient.of(EntryStacks.of(stack)) else null
                }
            pokemonEntries + itemEntries
        }

        override fun getInputEntries(): List<EntryIngredient> = cachedInputEntries
        override fun getOutputEntries(): List<EntryIngredient> = cachedOutputEntries
        override fun getCategoryIdentifier(): CategoryIdentifier<*> = categoryId(def)

        override fun getDisplayLocation(): Optional<ResourceLocation> = Optional.of(
            ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, handle.recipeIdPath)
        )
    }

    // ----- Generic Category wrapping DexCategory -----

    class GenericCategory(private val def: DexCategory) : DisplayCategory<GenericDisplay> {

        override fun getCategoryIdentifier(): CategoryIdentifier<out GenericDisplay> = categoryId(def)
        override fun getTitle(): Component = Component.translatable(def.titleKey)
        override fun getIcon(): Renderer = EntryStacks.of(def.icon)
        override fun getDisplayHeight(): Int = def.maxSize().height
        override fun getFixedDisplaysPerPage(): Int = 1
        override fun getDisplayWidth(display: GenericDisplay): Int = display.handle.width

        override fun setupDisplay(display: GenericDisplay, bounds: Rectangle): List<Widget> {
            val widgets = mutableListOf<Widget>()
            val handle = display.handle
            val slots = handle.slots
            val w = handle.width
            val h = handle.height

            val yOff = (bounds.height - h).coerceAtLeast(0) / 2
            val px = bounds.x
            val py = bounds.y + yOff

            widgets.add(Widgets.createRecipeBase(Rectangle(px, py, w, h)))

            for (slot in slots.pokemon) {
                val entry = EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(slot.species, slot.aspects))
                val s = Widgets.createSlot(Rectangle(px + slot.x, py + slot.y, 20, 20))
                    .entries(listOf(entry))
                if (slot.role == SlotRole.INPUT) s.markInput() else s.markOutput()
                if (slot.disableBackground) s.disableBackground()
                if (slot.disableHighlight) s.disableHighlight()
                widgets.add(s)
            }

            if (slots.hasArrow) {
                widgets.add(Widgets.createArrow(Point(px + slots.arrowX, py + slots.arrowY)))
            }

            for (slot in slots.items) {
                val stack = SpawnDisplayHelper.resolveItemStack(slot.itemId)
                if (!stack.isEmpty) {
                    val s = Widgets.createSlot(Rectangle(px + slot.x, py + slot.y, 18, 18))
                        .entries(listOf(EntryStacks.of(stack)))
                    if (slot.role == SlotRole.INPUT) s.markInput() else s.markOutput()
                    s.disableBackground()
                    widgets.add(s)
                }
            }

            widgets.add(Widgets.createDrawableWidget { gfx, mouseX, mouseY, _ ->
                gfx.pose().pushPose()
                gfx.pose().translate(px.toFloat(), py.toFloat(), 0f)
                handle.layout.render(gfx)
                gfx.pose().popPose()
                val tooltip = handle.layout.getTooltipAt(mouseX - px, mouseY - py)
                if (tooltip != null) {
                    gfx.renderComponentTooltip(net.minecraft.client.Minecraft.getInstance().font, tooltip, mouseX, mouseY)
                }
            })

            return widgets
        }
    }

    // ----- Generic DynamicDisplayGenerator -----

    private inner class GenericDisplayGenerator(private val def: DexCategory) : DynamicDisplayGenerator<GenericDisplay> {

        @Volatile private var cachedVersion = -1L
        @Volatile private var cachedDisplays: List<GenericDisplay>? = null

        override fun getRecipeFor(entry: EntryStack<*>): Optional<List<GenericDisplay>> {
            val value = entry.value ?: return Optional.empty()
            if (value is PokemonEntry) {
                val handles = def.buildRecipesFor(value.species)
                if (handles.isEmpty()) return Optional.empty()
                return Optional.of(handles.map { GenericDisplay(it, def) })
            }
            if (value is net.minecraft.world.item.ItemStack) {
                val itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(value.item).toString()
                val handles = def.buildRecipesForItem(itemId)
                if (handles.isEmpty()) return Optional.empty()
                return Optional.of(handles.map { GenericDisplay(it, def) })
            }
            return Optional.empty()
        }

        override fun getUsageFor(entry: EntryStack<*>): Optional<List<GenericDisplay>> {
            val value = entry.value ?: return Optional.empty()
            if (value is PokemonEntry) {
                val handles = def.buildUsagesFor(value.species)
                if (handles.isEmpty()) return Optional.empty()
                return Optional.of(handles.map { GenericDisplay(it, def) })
            }
            if (value is net.minecraft.world.item.ItemStack) {
                val itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(value.item).toString()
                val handles = def.buildUsagesForItem(itemId)
                if (handles.isEmpty()) return Optional.empty()
                return Optional.of(handles.map { GenericDisplay(it, def) })
            }
            return Optional.empty()
        }

        override fun generate(builder: ViewSearchBuilder): Optional<List<GenericDisplay>> {
            // Per-entry lookups are handled by getRecipeFor/getUsageFor.
            // generate() only handles "view all" browsing.
            if (builder.recipesFor.isNotEmpty() || builder.usagesFor.isNotEmpty()) {
                return Optional.empty()
            }

            if (!SpawnDataIndex.hasData()) return Optional.empty()
            val version = SpawnDataIndex.dataVersion
            cachedDisplays?.let { if (cachedVersion == version) return Optional.of(it) }

            val all = def.buildAllRecipes().map { GenericDisplay(it, def) }
            cachedDisplays = all
            cachedVersion = version
            return if (all.isEmpty()) Optional.empty() else Optional.of(all)
        }
    }
}
