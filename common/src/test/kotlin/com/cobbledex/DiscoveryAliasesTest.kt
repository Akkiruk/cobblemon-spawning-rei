package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertTrue

class DiscoveryAliasesTest {
    @Test
    fun formAliasesIncludeBaseSpeciesAndMaterialReason() {
        val aliases = DiscoveryAliases.pokemonAliases(
            DiscoveryAliases.PokemonContext(
                species = "wooperpaldean",
                displayName = "Paldean Wooper",
                baseSpeciesName = "wooper",
                primaryType = "poison",
                secondaryType = "ground",
                formAspects = setOf("paldean"),
                materialFormReasons = listOf("typing"),
            )
        )

        assertTrue("Wooper" in aliases)
        assertTrue("base:wooper" in aliases)
        assertTrue("type:poison" in aliases)
        assertTrue("form:paldean" in aliases)
        assertTrue("material form:typing" in aliases)
    }

    @Test
    fun jobAndAbilityAliasesAreSearchable() {
        val aliases = DiscoveryAliases.pokemonAliases(
            DiscoveryAliases.PokemonContext(
                species = "bulbasaur",
                displayName = "Bulbasaur",
                primaryType = "grass",
                abilities = listOf("overgrow"),
                hiddenAbility = "chlorophyll",
                jobAliases = listOf("job:berry_harvest", "Berry Harvester"),
            )
        )

        assertTrue("ability:overgrow" in aliases)
        assertTrue("hidden ability:chlorophyll" in aliases)
        assertTrue("job:berry_harvest" in aliases)
        assertTrue("Berry Harvester" in aliases)
    }

    @Test
    fun moveAliasesCoverTmSearchTerms() {
        val aliases = DiscoveryAliases.moveAliases("thunderbolt")

        assertTrue("move:thunderbolt" in aliases)
        assertTrue("tm:thunderbolt" in aliases)
        assertTrue("technical machine Thunderbolt" in aliases)
    }
}