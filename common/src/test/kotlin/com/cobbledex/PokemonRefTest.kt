package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PokemonRefTest {

    private fun formInfo(
        name: String,
        baseSpeciesName: String,
        formAspects: Set<String>
    ) = EvolutionDataLoader.SpeciesBasicInfo(
        name = name,
        nationalDexNumber = 26,
        primaryType = "electric",
        secondaryType = null,
        catchRate = 75,
        weight = 21.0f,
        height = 0.8f,
        baseSpeciesName = baseSpeciesName,
        formAspects = formAspects,
    )

    @Test
    fun pokemonIdentityKeyNormalizesSpeciesAndAspectOrder() {
        val first = pokemonIdentityKey("Cobblemon:Raichu", setOf("Alolan", "shiny"))
        val second = pokemonIdentityKey("raichu", setOf("shiny", "alolan"))

        assertEquals("raichu|alolan,shiny", first)
        assertEquals(first, second)
    }

    @Test
    fun parseAspectStringDropsNoiseAndSorts() {
        val aspects = parseAspectString("  Shiny   alolan   shiny   ")

        assertEquals(setOf("alolan", "shiny"), aspects)
        assertTrue(aspects.first() == "alolan")
    }

    @Test
    fun iconResolverUsesIndexedFormBaseSpeciesAndAspects() {
        val resolved = PokemonIconResolver.resolve(
            species = "raichualolan",
            explicitAspects = emptySet(),
            info = formInfo("raichualolan", "raichu", setOf("alolan"))
        )

        assertEquals("raichualolan", resolved.requestedSpecies)
        assertEquals(setOf("alolan"), resolved.requestedAspects)
        assertEquals("raichu", resolved.captureSpecies)
        assertEquals(setOf("alolan"), resolved.captureAspects)
        assertEquals("pokemon:raichu|alolan", resolved.cacheKey)
    }

    @Test
    fun iconResolverMergesExplicitAndImplicitRegionalAspects() {
        val resolved = PokemonIconResolver.resolve("RaichuAlolan", setOf("Shiny"))

        assertEquals("raichu", resolved.captureSpecies)
        assertEquals(setOf("alolan", "shiny"), resolved.captureAspects)
        assertEquals("pokemon:raichu|alolan,shiny", resolved.cacheKey)
    }
}