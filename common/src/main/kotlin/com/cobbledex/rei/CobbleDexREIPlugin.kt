package com.cobbledex.rei

import com.cobbledex.CobbleDexMod
import com.cobbledex.DebugLog
import com.cobbledex.DexCategory
import com.cobbledex.PokemonItemCache
import com.cobbledex.RecipeHandle
import com.cobbledex.SlotRole
import com.cobbledex.SpawnDataIndex
import com.cobbledex.SpawnDisplayHelper
import com.cobbledex.ViewerParityGuard
import com.cobbledex.config.CobbleDexConfig
import com.cobbledex.rei.entry.MoveEntry
import com.cobbledex.rei.entry.MoveEntryDefinition
import com.cobbledex.rei.entry.MoveEntryType
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

        fun categoryId(def: DexCategory): CategoryIdentifier<GenericDisplay> =
            categoryIds.getOrPut(def.id) { CategoryIdentifier.of(CobbleDexMod.MOD_ID, def.id) }
    }

    private val emiActive: Boolean by lazy {
        try {
            Class.forName("dev.emi.emi.api.EmiPlugin")
            DebugLog.info("EMI detected — skipping REI plugin (native EMI plugin handles registration)")
            true
        } catch (_: ClassNotFoundException) { false }
    }

    override fun registerEntryTypes(registry: EntryTypeRegistry) {
        if (emiActive) return
        try {
            registry.register(PokemonEntryType.POKEMON.id, PokemonEntryDefinition())
            registry.register(MoveEntryType.MOVE.id, MoveEntryDefinition())
            DebugLog.info("Pokémon + move entry types registered")
        } catch (e: Exception) {
            DebugLog.warn("registerEntryTypes failed: ${e.message}")
        }
    }

    private fun ensureEntryTypeAvailable() {
        try { PokemonEntryType.POKEMON.definition } catch (_: Exception) {
            try {
                EntryTypeRegistry.getInstance().register(PokemonEntryType.POKEMON.id, PokemonEntryDefinition())
            } catch (e: Exception) {
                DebugLog.warnOnce("rei-entry-type-pokemon") { "Failed to register PokemonEntryType: ${e.message}" }
            }
        }
        try { MoveEntryType.MOVE.definition } catch (_: Exception) {
            try {
                EntryTypeRegistry.getInstance().register(MoveEntryType.MOVE.id, MoveEntryDefinition())
            } catch (e: Exception) {
                DebugLog.warnOnce("rei-entry-type-move") { "Failed to register MoveEntryType: ${e.message}" }
            }
        }
    }

    override fun registerCategories(registry: CategoryRegistry) {
        if (emiActive) return
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
        if (emiActive) return
        ensureEntryTypeAvailable()
        SpawnDataIndex.ensureLoaded()
        val config = CobbleDexConfig.get()

        for (def in DexCategory.ALL) {
            if (!def.isEnabled(config)) continue
            if (def is com.cobbledex.NatureDex) {
                val handles = def.buildAllRecipes()
                ViewerParityGuard.warn(def, handles, "REI")
                handles.map { GenericDisplay(it, def) }.forEach { registry.add(it) }
            } else {
                registry.registerDisplayGenerator(categoryId(def), GenericDisplayGenerator(def))
            }
        }
        DebugLog.info("Registered dynamic display generators")
    }

    override fun registerEntries(registry: EntryRegistry) {
        if (emiActive) return
        ensureEntryTypeAvailable()
        SpawnDataIndex.ensureLoaded()
        val config = CobbleDexConfig.get()
        val queries = SpawnDataIndex.currentQueries()

        var registered = 0
        var formCount = 0
        var hidden = 0
        for (species in SpawnDataIndex.allSpeciesNames) {
            val info = queries.getSpeciesInfo(species)
            if (info == null) continue
            if (info.isForm && !config.registerFormEntries) continue
            if (!queries.shouldSurfaceSpecies(species)) continue
            if (!PokemonItemCache.canRender(species)) {
                DebugLog.trackMissingModel(species)
                hidden++
                continue
            }
            try {
                val stack = EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(species))
                registry.addEntry(stack)
                if (info.isForm) formCount++ else registered++
            } catch (e: Exception) {
                DebugLog.once("entry-fail-$species") { "Entry registration failed for $species: ${e.message}" }
                hidden++
            }
        }
        DebugLog.info("Registered $registered Pokémon entries + $formCount forms ($hidden hidden — no model)")
        DebugLog.printSummary()
    }

    // ----- Generic Display wrapping RecipeHandle -----

    class GenericDisplay(val handle: RecipeHandle, val def: DexCategory) : Display {

        private val cachedInputEntries: List<EntryIngredient> by lazy {
            val pokemon = handle.lookupInputSpecies().map {
                EntryIngredient.of(EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(it)))
            }
            val items = handle.lookupInputItemIds().mapNotNull { itemId ->
                val stack = SpawnDisplayHelper.resolveItemStack(itemId)
                if (!stack.isEmpty) EntryIngredient.of(EntryStacks.of(stack)) else null
            }
            pokemon + items
        }

        private val cachedOutputEntries: List<EntryIngredient> by lazy {
            val pokemonEntries = handle.lookupOutputSpecies().map {
                EntryIngredient.of(EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(it)))
            }
            val itemEntries = handle.lookupOutputItemIds().mapNotNull { itemId ->
                val stack = SpawnDisplayHelper.resolveItemStack(itemId)
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
                // REI's slot-background sprite is 18x18; blitting it into a 20x20 box bleeds the
                // neighbouring texels (an arrow + an adjacent slot edge) and paints those "arrow"
                // artefacts between framed grid cells. Framed cells → 18x18 inside the 20px cell;
                // the borderless single-Pokémon slot on detail pages keeps its larger icon.
                val box = if (slot.disableBackground)
                    Rectangle(px + slot.x, py + slot.y, 20, 20)
                else
                    Rectangle(px + slot.x + 1, py + slot.y + 1, 18, 18)
                val s = Widgets.createSlot(box)
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
                    val s = Widgets.createSlot(Rectangle(px + slot.x, py + slot.y, slot.size, slot.size))
                        .entries(listOf(EntryStacks.of(stack)))
                    when (slot.role) {
                        SlotRole.INPUT -> s.markInput()
                        SlotRole.OUTPUT -> s.markOutput()
                        SlotRole.DISPLAY -> {}
                    }
                    s.disableBackground()
                    widgets.add(s)
                }
            }

            widgets.add(Widgets.createDrawableWidget { gfx, _, _, _ ->
                gfx.pose().pushPose()
                gfx.pose().translate(px.toFloat(), py.toFloat(), 0f)
                handle.layout.render(gfx)
                gfx.pose().popPose()
            })

            // Move-name links: an invisible button over each name that opens the move's learner grid.
            for (link in slots.moveLinks) {
                val move = link.moveName
                val button = MoveLinkButton(px + link.x, py + link.y, link.width, link.height) {
                    ViewSearchBuilder.builder()
                        .addRecipesFor(EntryStack.of(MoveEntryType.MOVE, MoveEntry(move)))
                        .open()
                }
                widgets.add(Widgets.wrapVanillaWidget(button))
            }

            for (zone in handle.layout.tooltipZones) {
                if (zone.lines.isNotEmpty()) {
                    widgets.add(Widgets.createTooltip(
                        Rectangle(px + zone.x, py + zone.y, zone.width, zone.height),
                        zone.lines,
                    ))
                }
            }

            return widgets
        }
    }

    // ----- Generic DynamicDisplayGenerator -----

    private inner class GenericDisplayGenerator(private val def: DexCategory) : DynamicDisplayGenerator<GenericDisplay> {

        @Volatile private var cachedVersion = -1L
        @Volatile private var cachedDisplays: List<GenericDisplay>? = null

        private fun forValue(handles: List<RecipeHandle>): Optional<List<GenericDisplay>> {
            if (handles.isEmpty()) return Optional.empty()
            ViewerParityGuard.warn(def, handles, "REI")
            return Optional.of(handles.map { GenericDisplay(it, def) })
        }

        override fun getRecipeFor(entry: EntryStack<*>): Optional<List<GenericDisplay>> {
            when (val value = entry.value ?: return Optional.empty()) {
                is PokemonEntry -> return forValue(def.buildRecipesFor(value.species))
                is MoveEntry -> return forValue(def.buildRecipesForMove(value.moveName))
                is net.minecraft.world.item.ItemStack -> {
                    val itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(value.item).toString()
                    return forValue(def.buildRecipesForItem(itemId))
                }
            }
            return Optional.empty()
        }

        override fun getUsageFor(entry: EntryStack<*>): Optional<List<GenericDisplay>> {
            when (val value = entry.value ?: return Optional.empty()) {
                is PokemonEntry -> return forValue(def.buildUsagesFor(value.species))
                is MoveEntry -> return forValue(def.buildRecipesForMove(value.moveName))
                is net.minecraft.world.item.ItemStack -> {
                    val itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(value.item).toString()
                    return forValue(def.buildRecipesForItem(itemId))
                }
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

            val handles = def.buildAllRecipes()
            ViewerParityGuard.warn(def, handles, "REI")
            val all = handles.map { GenericDisplay(it, def) }
            cachedDisplays = all
            cachedVersion = version
            return if (all.isEmpty()) Optional.empty() else Optional.of(all)
        }
    }

    /** Invisible click target laid over a move name; faint highlight on hover. */
    private class MoveLinkButton(
        x: Int, y: Int, w: Int, h: Int,
        private val onPress: () -> Unit,
    ) : net.minecraft.client.gui.components.AbstractWidget(x, y, w, h, Component.empty()) {

        override fun renderWidget(
            graphics: net.minecraft.client.gui.GuiGraphics,
            mouseX: Int, mouseY: Int, delta: Float,
        ) {
            if (isHovered) graphics.fill(x, y, x + width, y + height, 0x30FFFFFF)
        }

        override fun onClick(mouseX: Double, mouseY: Double) = onPress()

        override fun updateWidgetNarration(output: net.minecraft.client.gui.narration.NarrationElementOutput) {}
    }
}
