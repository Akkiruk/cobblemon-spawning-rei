package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertTrue

class DiscoveryAliasesTest {
    @Test
    fun formAliasesIncludeBaseSpeciesButNotMaterialReason() {
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
        // materialFormReasons are internal decision strings for the on-page form note, not
        // player-facing search terms - they must never end up as a registered alias.
        assertTrue(aliases.none { it.contains("material form", ignoreCase = true) })
    }

    @Test
    fun addAliasDoesNotRegisterACompactDuplicate() {
        val aliases = DiscoveryAliases.pokemonAliases(
            DiscoveryAliases.PokemonContext(
                species = "bulbasaur",
                displayName = "Bulbasaur",
                primaryType = "grass",
                abilities = listOf("overgrow"),
                hiddenAbility = "chlorophyll",
            )
        )

        assertTrue("ability:overgrow" in aliases)
        assertTrue("abilityovergrow" !in aliases)
        assertTrue("hidden ability:chlorophyll" in aliases)
        assertTrue("hiddenabilitychlorophyll" !in aliases)
    }

    @Test
    fun jeiAliasesAreCappedAndFavorVariety() {
        // Mushette Red has enough categories (base, 2 types, 2 abilities, hidden ability, form)
        // to fill 5 slots with one alias per category before doubling up on any one.
        val aliases = DiscoveryAliases.curatedAliases(
            DiscoveryAliases.PokemonContext(
                species = "mushettered",
                displayName = "Mushette Red",
                baseSpeciesName = "mushette",
                primaryType = "dark",
                secondaryType = "fairy",
                abilities = listOf("magicguard", "effectspore"),
                hiddenAbility = "receiver",
                formAspects = setOf("mushroomred"),
            ),
            limit = 5,
        )

        assertTrue(aliases.size <= 5)
        assertTrue("base:mushette" in aliases)
        assertTrue("type:dark" in aliases)
        assertTrue("ability:magicguard" in aliases)
        assertTrue("form:mushroomred" in aliases)
        // Prefers one alias per category over a second type before the form/base categories get a look in.
        assertTrue("type:fairy" !in aliases || aliases.size == 5)
    }

    @Test
    fun jeiAliasesCapAtLimitWhenManyCategoriesPresent() {
        val aliases = DiscoveryAliases.curatedAliases(
            DiscoveryAliases.PokemonContext(
                species = "test",
                displayName = "Test",
                baseSpeciesName = "base",
                primaryType = "water",
                secondaryType = "flying",
                abilities = listOf("one", "two"),
                hiddenAbility = "three",
                formAspects = setOf("alpha", "beta"),
                jobAliases = listOf("job:fishing"),
            ),
            limit = 5,
        )

        assertTrue(aliases.size == 5)
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