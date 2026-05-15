package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertEquals

class PokemonSpriteAtlasTest {
    @Test
    fun spriteKeysNormalizeSpeciesAndSortedAspects() {
        val key = PokemonSpriteAtlas.SpriteKey("Cobblemon:Mr. Mime", setOf("region_bias=Galar", "Shiny!!"))

        assertEquals("mrmime__region_bias=galar_shiny", key.id)
    }

    @Test
    fun regionalFormNamesResolveToBaseSpeciesAndAspectKey() {
        val resolved = PokemonSpriteAtlas.resolve("vulpix-alolan")

        assertEquals("vulpix", resolved.renderSpecies)
        assertEquals(setOf("alolan"), resolved.renderAspects)
        assertEquals("vulpix__alolan", resolved.key.id)
    }

    @Test
    fun explicitAspectsWinForBaseSpeciesSlots() {
        val resolved = PokemonSpriteAtlas.resolve("vulpix", setOf("alolan"))

        assertEquals("vulpix", resolved.renderSpecies)
        assertEquals(setOf("alolan"), resolved.renderAspects)
        assertEquals("vulpix__alolan", resolved.key.id)
    }
}