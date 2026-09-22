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
    fun jeiAliasesCoverAllSixCategoriesInPriorityOrder() {
        // A species hitting every category at its real maximum: 2 types, 3 riding styles, a base
        // species, a rarity tag, 3 abilities (2 regular + hidden), 1 regional variant - 11 total,
        // exactly JEI_ALIAS_LIMIT, so nothing gets dropped even though every category is maxed out.
        val aliases = DiscoveryAliases.curatedAliases(
            DiscoveryAliases.PokemonContext(
                species = "test",
                displayName = "Test",
                baseSpeciesName = "base",
                primaryType = "dark",
                secondaryType = "fairy",
                abilities = listOf("magicguard", "effectspore"),
                hiddenAbility = "receiver",
                ridingStyles = listOf("LAND", "AIR", "LIQUID"),
                rarityLabels = listOf("legendary"),
                regionalVariant = "galarian",
            )
        )

        assertEquals(
            listOf(
                "type:dark", "type:fairy",
                "riding:land", "riding:air", "riding:liquid",
                "base:base",
                "rarity:legendary",
                "ability:magicguard", "ability:effectspore", "ability:receiver",
                "regional:galarian",
            ),
            aliases,
        )
        assertEquals(DiscoveryAliases.JEI_ALIAS_LIMIT, aliases.size)
    }

    @Test
    fun jeiAliasesTruncateLowestPriorityCategoriesFirstWhenCapped() {
        // A cap tighter than the real total drops from the end of the priority order (abilities'
        // 2nd/3rd values, then regional variant) rather than spreading the cut evenly - a species'
        // second ability outranks its regional-variant tag, so it must survive a tighter cap that
        // the regional tag doesn't.
        val aliases = DiscoveryAliases.curatedAliases(
            DiscoveryAliases.PokemonContext(
                species = "test",
                displayName = "Test",
                primaryType = "water",
                secondaryType = "flying",
                abilities = listOf("one", "two", "three"),
                regionalVariant = "alolan",
            ),
            limit = 4,
        )

        assertEquals(listOf("type:water", "type:flying", "ability:one", "ability:two"), aliases)
    }

    @Test
    fun jeiAliasesDropADuplicateRatherThanSpendASlotOnIt() {
        // A datapack listing the same ability as both regular and hidden must not burn two slots
        // printing "ability:overgrow" twice.
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
    fun curatedAliasesIgnoresJobAndGenericFormAspectsEntirely() {
        // Job tags and non-regional form aspects (e.g. a custom "mushroomred" costume form) are
        // real, useful search terms for REI/EMI's uncapped list, but they aren't one of the six
        // categories JEI's eleven slots are reserved for.
        val aliases = DiscoveryAliases.curatedAliases(
            DiscoveryAliases.PokemonContext(
                species = "test",
                displayName = "Test",
                primaryType = "dark",
                formAspects = setOf("mushroomred"),
                jobAliases = listOf("job:fishing", "Fisher"),
            )
        )

        assertEquals(listOf("type:dark"), aliases)
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