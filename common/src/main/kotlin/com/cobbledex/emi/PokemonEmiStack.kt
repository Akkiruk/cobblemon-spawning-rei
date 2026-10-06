package com.cobbledex.emi

import com.cobbledex.PokemonItemCache
import com.cobbledex.PokemonIconRenderer
import com.cobbledex.SpawnDataIndex
import com.cobbledex.SpawnDisplayHelper
import com.cobbledex.SpeciesNameNormalizer
import com.cobbledex.formatSpeciesName
import com.cobbledex.sanitizePath
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dev.emi.emi.api.render.EmiTooltipComponents
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

class PokemonEmiStack private constructor(
    private val species: String,
    private val formAspects: Set<String>,
) : EmiStack() {
    /**
     * Species only, on purpose. Every stack EMI indexes for lookups (recipe inputs/outputs, the
     * browsable list) is created without aspects, while recipe slots carry the form's aspects so
     * the right icon is drawn - keying on both meant clicking an evolved/regional form's icon
     * looked up a key that was never indexed and found no recipes. Aspects only affect rendering.
     */
    private data class Key(val species: String)

    private val normalizedSpecies = SpeciesNameNormalizer.normalize(species)
    private val displayName = formatSpeciesName(normalizedSpecies)
    private val key = Key(normalizedSpecies)

    /**
     * Namespaced by whatever added the species (a mod or datapack, falling back to "cobblemon"), so
     * EMI's mod search and the `@mod` filter find add-on Pokémon under their real source, the way
     * JEI's mod tag already does. Resolved on first use - when EMI asks, species data is loaded.
     */
    private val resourceId: ResourceLocation by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val source = SpawnDataIndex.getSpeciesInfo(normalizedSpecies)?.source.orEmpty()
            .lowercase().replace(NAMESPACE_INVALID, "").ifBlank { "cobblemon" }
        ResourceLocation.fromNamespaceAndPath(source, sanitizePath(normalizedSpecies))
    }

    /** [PokemonItemCache.generation] this stack last confirmed it can render at, or -1. */
    @Volatile private var renderableAtGeneration = -1

    override fun copy(): EmiStack = PokemonEmiStack(species, formAspects).also { copy ->
        copy.setAmount(getAmount())
        copy.setChance(getChance())
    }

    override fun isEmpty(): Boolean {
        if (species.isBlank()) return true
        val generation = PokemonItemCache.generation
        if (renderableAtGeneration == generation) return false
        val renderable = PokemonItemCache.canRender(species, formAspects)
        if (renderable) renderableAtGeneration = generation
        return !renderable
    }

    override fun getComponentChanges(): DataComponentPatch = DataComponentPatch.EMPTY

    override fun getKey(): Any = key

    override fun getId(): ResourceLocation = resourceId

    override fun getTooltipText(): List<Component> =
        SpawnDisplayHelper.buildPokemonTooltipLines(normalizedSpecies, displayName)

    /**
     * EMI draws [getTooltip], never [getTooltipText] (that one only feeds search) - the base class
     * returns nothing but the remainder, so every stack type has to build its own, the way EMI's
     * item and fluid stacks do. Without this a Pokémon had no hover tooltip at all.
     */
    override fun getTooltip(): List<ClientTooltipComponent> {
        if (isEmpty()) return emptyList()
        val list = getTooltipText().mapTo(mutableListOf()) { EmiTooltipComponents.of(it) }
        // The add-on/datapack that added this species, same source JEI shows as its mod tag.
        EmiTooltipComponents.appendModName(list, SpawnDataIndex.getSpeciesInfo(normalizedSpecies)?.source ?: "cobblemon")
        list.addAll(super.getTooltip())
        return list
    }

    override fun getName(): Component = Component.literal(displayName)

    override fun render(graphics: GuiGraphics, x: Int, y: Int, delta: Float, flags: Int) {
        PokemonIconRenderer.render(graphics, species, formAspects, x, y)
    }

    companion object {
        private val NAMESPACE_INVALID = Regex("[^a-z0-9_.-]")

        fun of(species: String, formAspects: Set<String> = emptySet()): PokemonEmiStack =
            PokemonEmiStack(species, formAspects)
    }

    /**
     * Lets EMI save a Pokémon in favorites, bookmarks, lookup history and its hidden list - EMI
     * only persists stacks whose class has a serializer, and without one `canFavorite` rejects the
     * stack outright.
     */
    class Serializer : EmiIngredientSerializer<PokemonEmiStack> {
        override fun getType(): String = TYPE

        override fun serialize(stack: PokemonEmiStack): JsonElement = JsonObject().apply {
            addProperty("type", TYPE)
            addProperty("species", stack.normalizedSpecies)
            if (stack.getAmount() != 1L) addProperty("amount", stack.getAmount())
            if (stack.getChance() != 1f) addProperty("chance", stack.getChance())
        }

        override fun deserialize(element: JsonElement): EmiIngredient {
            val json = element.takeIf { it.isJsonObject }?.asJsonObject ?: return EmiStack.EMPTY
            val species = json.get("species")?.takeIf { it.isJsonPrimitive }?.asString ?: return EmiStack.EMPTY
            val stack = of(species)
            if (stack.isEmpty()) return EmiStack.EMPTY
            json.get("amount")?.takeIf { it.isJsonPrimitive }?.asLong?.let { stack.setAmount(it) }
            json.get("chance")?.takeIf { it.isJsonPrimitive }?.asFloat?.let { stack.setChance(it) }
            return stack
        }

        companion object {
            const val TYPE = "cobbledex_pokemon"
        }
    }
}
