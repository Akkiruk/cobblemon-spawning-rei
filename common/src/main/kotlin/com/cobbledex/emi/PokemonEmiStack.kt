package com.cobbledex.emi

import com.cobbledex.PokemonItemCache
import com.cobbledex.PokemonIconRenderer
import com.cobbledex.SpawnDisplayHelper
import com.cobbledex.SpeciesNameNormalizer
import com.cobbledex.formatSpeciesName
import com.cobbledex.sanitizePath
import dev.emi.emi.api.stack.EmiStack
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

class PokemonEmiStack private constructor(
    private val species: String,
    private val formAspects: Set<String>,
) : EmiStack() {
    private data class Key(val species: String, val aspects: Set<String>)

    private val normalizedSpecies = SpeciesNameNormalizer.normalize(species)
    private val displayName = formatSpeciesName(normalizedSpecies)
    private val key = Key(normalizedSpecies, formAspects)
    private val id = ResourceLocation.fromNamespaceAndPath("cobblemon", sanitizePath(normalizedSpecies))

    override fun copy(): EmiStack = PokemonEmiStack(species, formAspects).also { copy ->
        copy.setAmount(getAmount())
        copy.setChance(getChance())
    }

    override fun isEmpty(): Boolean = species.isBlank() || !PokemonItemCache.canRender(species, formAspects)

    override fun getComponentChanges(): DataComponentPatch = DataComponentPatch.EMPTY

    override fun getKey(): Any = key

    override fun getId(): ResourceLocation = id

    override fun getTooltipText(): List<Component> =
        SpawnDisplayHelper.buildPokemonTooltipLines(normalizedSpecies, displayName)

    override fun getName(): Component = Component.literal(displayName)

    override fun render(graphics: GuiGraphics, x: Int, y: Int, delta: Float, flags: Int) {
        PokemonIconRenderer.render(graphics, species, formAspects, x, y)
    }

    companion object {
        fun of(species: String, formAspects: Set<String> = emptySet()): PokemonEmiStack =
            PokemonEmiStack(species, formAspects)
    }
}