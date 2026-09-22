package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiscoveryAliasesTest {
    @Test
    fun formAliasesIncludeBaseSpecies() {
        val aliases = DiscoveryAliases.pokemonAliases(
            DiscoveryAliases.PokemonContext(
                species = "wooperpaldean",
                displayName = "Paldean Wooper",
                baseSpeciesName = "wooper",
                primaryType = "poison",
                secondaryType = "ground",
                formAspects = setOf("paldean"),
            )
        )

        assertTrue("Wooper" in aliases)
        assertTrue("base:wooper" in aliases)
        assertTrue("type:poison" in aliases)
        assertTrue("form:paldean" in aliases)
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
    fun jeiAliasesTakeOnePerCategoryBeforeDoublingUp() {
        // Mushette Red - the species from the bug report - has six categories competing for five
        // slots, so every slot should go to a different one and the second typing/ability must
        // lose to a category that has had no slot yet.
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
            )
        )

        assertEquals(
            listOf("base:mushette", "type:dark", "ability:magicguard", "ability:receiver", "form:mushroomred"),
            aliases,
        )
    }

    @Test
    fun jeiAliasesSpendLeftoverSlotsOnCategoriesThatHaveMore() {
        // Only two categories, so once each has had its first slot the remaining ones go back to
        // the categories with more to give rather than being left unused.
        val aliases = DiscoveryAliases.curatedAliases(
            DiscoveryAliases.PokemonContext(
                species = "test",
                displayName = "Test",
                primaryType = "water",
                secondaryType = "flying",
                abilities = listOf("one", "two", "three"),
            )
        )

        assertEquals(
            listOf("type:water", "ability:one", "type:flying", "ability:two", "ability:three"),
            aliases,
        )
    }

    @Test
    fun jeiAliasesNeverExceedTheLimit() {
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
            )
        )

        assertEquals(DiscoveryAliases.JEI_ALIAS_LIMIT, aliases.size)
        assertEquals(aliases.distinct(), aliases)
    }

    @Test
    fun jeiAliasesDropADuplicateRatherThanSpendASlotOnIt() {
        // A datapack listing the same ability as both regular and hidden must not burn two of the
        // five slots printing "ability:overgrow" twice.
        val aliases = DiscoveryAliases.curatedAliases(
            DiscoveryAliases.PokemonContext(
                species = "test",
                displayName = "Test",
                primaryType = "grass",
                abilities = listOf("overgrow"),
                hiddenAbility = "overgrow",
            )
        )

        assertEquals(listOf("type:grass", "ability:overgrow"), aliases)
    }

    @Test
    fun curatedAliasesHandlesASpeciesWithNothingToOffer() {
        val aliases = DiscoveryAliases.curatedAliases(
            DiscoveryAliases.PokemonContext(species = "test", displayName = "Test")
        )

        assertTrue(aliases.isEmpty())
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