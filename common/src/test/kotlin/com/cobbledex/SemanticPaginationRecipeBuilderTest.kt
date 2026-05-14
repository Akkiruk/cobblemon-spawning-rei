package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SemanticPaginationRecipeBuilderTest {
    @Test
    fun baseEvolutionPagesOnlyShowImmediateBaseBranchesAndGroupSameTargetMethods() {
        val snapshot = CobbleDexDataSnapshot(
            speciesInfo = mapOf(
                "eevee" to speciesInfo("eevee", nationalDexNumber = 133),
                "vaporeon" to speciesInfo("vaporeon", nationalDexNumber = 134),
                "jolteon" to speciesInfo("jolteon", nationalDexNumber = 135),
                "glaceon" to speciesInfo("glaceon", nationalDexNumber = 471),
                "eeveefrost" to speciesInfo(
                    "eeveefrost",
                    nationalDexNumber = 133,
                    primaryType = "ice",
                    abilities = listOf("Snow Cloak"),
                    baseSpeciesName = "eevee",
                    formAspects = setOf("frost"),
                ),
            ),
            evolutionsBySpecies = mapOf(
                "eevee" to listOf(
                    evolution(fromSpecies = "eevee", toSpecies = "vaporeon", variant = "item_interact", requiredContext = "cobblemon:water_stone"),
                    evolution(fromSpecies = "eevee", toSpecies = "vaporeon", variant = "trade"),
                    evolution(fromSpecies = "eevee", toSpecies = "jolteon", variant = "item_interact", requiredContext = "cobblemon:thunder_stone"),
                    evolution(fromSpecies = "eevee", fromAspects = setOf("frost"), toSpecies = "glaceon", variant = "item_interact", requiredContext = "cobblemon:ice_stone"),
                ),
                "eeveefrost" to listOf(
                    evolution(fromSpecies = "eevee", fromAspects = setOf("frost"), toSpecies = "glaceon", variant = "item_interact", requiredContext = "cobblemon:ice_stone"),
                ),
            ),
            allSpeciesNames = listOf("eevee", "vaporeon", "jolteon", "glaceon", "eeveefrost"),
        )

        val pages = RecipeBuilder.buildEvolutionPagesFor("eevee", snapshot)

        assertEquals(2, pages.size)
        assertFalse(pages.any { it.targetSpeciesName == "glaceon" })
        assertEquals(setOf("vaporeon", "jolteon"), pages.mapNotNull { it.targetSpeciesName }.toSet())
        assertEquals(2, pages.first { it.targetSpeciesName == "vaporeon" }.methods.size)
    }

    @Test
    fun formEvolutionPagesUseOnlyTheCurrentFormsBranches() {
        val snapshot = CobbleDexDataSnapshot(
            speciesInfo = mapOf(
                "eevee" to speciesInfo("eevee", nationalDexNumber = 133),
                "glaceon" to speciesInfo("glaceon", nationalDexNumber = 471),
                "eeveefrost" to speciesInfo(
                    "eeveefrost",
                    nationalDexNumber = 133,
                    primaryType = "ice",
                    abilities = listOf("Snow Cloak"),
                    baseSpeciesName = "eevee",
                    formAspects = setOf("frost"),
                ),
            ),
            evolutionsBySpecies = mapOf(
                "eevee" to listOf(
                    evolution(fromSpecies = "eevee", fromAspects = setOf("frost"), toSpecies = "glaceon", variant = "item_interact", requiredContext = "cobblemon:ice_stone"),
                ),
                "eeveefrost" to listOf(
                    evolution(fromSpecies = "eevee", fromAspects = setOf("frost"), toSpecies = "glaceon", variant = "item_interact", requiredContext = "cobblemon:ice_stone"),
                ),
            ),
            allSpeciesNames = listOf("eevee", "eeveefrost", "glaceon"),
        )

        val pages = RecipeBuilder.buildEvolutionPagesFor("eeveefrost", snapshot)

        assertEquals(1, pages.size)
        assertEquals("glaceon", pages.single().targetSpeciesName)
        assertEquals(setOf("frost"), pages.single().sourceAspects)
    }

    @Test
    fun recipeEvolutionLookupShowsIncomingPreviousStageOnly() {
        val snapshot = CobbleDexDataSnapshot(
            speciesInfo = mapOf(
                "eevee" to speciesInfo("eevee", nationalDexNumber = 133),
                "vaporeon" to speciesInfo("vaporeon", nationalDexNumber = 134),
                "jolteon" to speciesInfo("jolteon", nationalDexNumber = 135),
            ),
            evolutionsBySpecies = mapOf(
                "eevee" to listOf(
                    evolution(fromSpecies = "eevee", toSpecies = "vaporeon", requiredContext = "cobblemon:water_stone"),
                    evolution(fromSpecies = "eevee", toSpecies = "jolteon", requiredContext = "cobblemon:thunder_stone"),
                ),
            ),
            evolutionsToSpecies = mapOf(
                "vaporeon" to listOf(evolution(fromSpecies = "eevee", toSpecies = "vaporeon", requiredContext = "cobblemon:water_stone")),
                "jolteon" to listOf(evolution(fromSpecies = "eevee", toSpecies = "jolteon", requiredContext = "cobblemon:thunder_stone")),
            ),
            allSpeciesNames = listOf("eevee", "vaporeon", "jolteon"),
        )

        val incoming = RecipeBuilder.buildEvolutionRecipesInto("vaporeon", snapshot)
        val basicStageIncoming = RecipeBuilder.buildEvolutionRecipesInto("eevee", snapshot)
        val outgoing = RecipeBuilder.buildEvolutionPagesFor("eevee", snapshot)

        assertEquals(1, incoming.size)
        assertEquals("eevee", incoming.single().sourceSpeciesName)
        assertEquals("vaporeon", incoming.single().targetSpeciesName)
        assertEquals(1, incoming.single().pageIndex)
        assertEquals(emptyList(), basicStageIncoming)
        assertEquals(setOf("vaporeon", "jolteon"), outgoing.mapNotNull { it.targetSpeciesName }.toSet())
    }

    @Test
    fun recipeEvolutionLookupPreservesForwardPageIndexForBranchTargets() {
        val snapshot = CobbleDexDataSnapshot(
            speciesInfo = mapOf(
                "eevee" to speciesInfo("eevee", nationalDexNumber = 133),
                "vaporeon" to speciesInfo("vaporeon", nationalDexNumber = 134),
                "jolteon" to speciesInfo("jolteon", nationalDexNumber = 135),
            ),
            evolutionsBySpecies = mapOf(
                "eevee" to listOf(
                    evolution(fromSpecies = "eevee", toSpecies = "vaporeon", requiredContext = "cobblemon:water_stone"),
                    evolution(fromSpecies = "eevee", toSpecies = "jolteon", requiredContext = "cobblemon:thunder_stone"),
                ),
            ),
            evolutionsToSpecies = mapOf(
                "jolteon" to listOf(evolution(fromSpecies = "eevee", toSpecies = "jolteon", requiredContext = "cobblemon:thunder_stone")),
            ),
            allSpeciesNames = listOf("eevee", "vaporeon", "jolteon"),
        )

        val incoming = RecipeBuilder.buildEvolutionRecipesInto("jolteon", snapshot)

        assertEquals(1, incoming.size)
        assertEquals("jolteon", incoming.single().targetSpeciesName)
        assertEquals(2, incoming.single().pageIndex)
    }

    @Test
    fun transformationFormsRemainImmediateEvolutionPagesForTheirBaseSpecies() {
        val snapshot = CobbleDexDataSnapshot(
            speciesInfo = mapOf(
                "charizard" to speciesInfo("charizard", nationalDexNumber = 6, primaryType = "fire", secondaryType = "flying"),
                "charizardmegax" to speciesInfo(
                    "charizardmegax",
                    nationalDexNumber = 6,
                    primaryType = "fire",
                    secondaryType = "dragon",
                    abilities = listOf("Tough Claws"),
                    labels = setOf("mega"),
                    baseSpeciesName = "charizard",
                    formAspects = setOf("mega", "x"),
                ),
                "charizardmegay" to speciesInfo(
                    "charizardmegay",
                    nationalDexNumber = 6,
                    primaryType = "fire",
                    secondaryType = "flying",
                    abilities = listOf("Drought"),
                    labels = setOf("mega"),
                    baseSpeciesName = "charizard",
                    formAspects = setOf("mega", "y"),
                ),
            ),
            allSpeciesNames = listOf("charizard", "charizardmegax", "charizardmegay"),
        )

        val pages = RecipeBuilder.buildEvolutionPagesFor("charizard", snapshot)

        assertEquals(setOf("charizardmegax", "charizardmegay"), pages.mapNotNull { it.targetSpeciesName }.toSet())
        assertTrue(pages.all { page -> page.methods.single().requirementText.isNotBlank() })
    }

    @Test
    fun recipeEvolutionLookupIncludesTransformationFormPreviousStage() {
        val snapshot = CobbleDexDataSnapshot(
            speciesInfo = mapOf(
                "charizard" to speciesInfo("charizard", nationalDexNumber = 6, primaryType = "fire", secondaryType = "flying"),
                "charizardmegax" to speciesInfo(
                    "charizardmegax",
                    nationalDexNumber = 6,
                    primaryType = "fire",
                    secondaryType = "dragon",
                    abilities = listOf("Tough Claws"),
                    labels = setOf("mega"),
                    baseSpeciesName = "charizard",
                    formAspects = setOf("mega", "x"),
                ),
            ),
            allSpeciesNames = listOf("charizard", "charizardmegax"),
        )

        val incoming = RecipeBuilder.buildEvolutionRecipesInto("charizardmegax", snapshot)

        assertEquals(1, incoming.size)
        assertEquals("charizard", incoming.single().sourceSpeciesName)
        assertEquals("charizardmegax", incoming.single().targetSpeciesName)
    }

    @Test
    fun itemEvolutionLookupKeepsFormSpecificSourcesBounded() {
        val snapshot = CobbleDexDataSnapshot(
            speciesInfo = mapOf(
                "eevee" to speciesInfo("eevee", nationalDexNumber = 133),
                "vaporeon" to speciesInfo("vaporeon", nationalDexNumber = 134),
                "glaceon" to speciesInfo("glaceon", nationalDexNumber = 471),
                "eeveefrost" to speciesInfo(
                    "eeveefrost",
                    nationalDexNumber = 133,
                    primaryType = "ice",
                    abilities = listOf("Snow Cloak"),
                    baseSpeciesName = "eevee",
                    formAspects = setOf("frost"),
                ),
                "pikachu" to speciesInfo("pikachu", nationalDexNumber = 25, primaryType = "electric"),
                "raichu" to speciesInfo("raichu", nationalDexNumber = 26, primaryType = "electric"),
            ),
            evolutionsBySpecies = mapOf(
                "eevee" to listOf(
                    evolution(fromSpecies = "eevee", toSpecies = "vaporeon", requiredContext = "cobblemon:water_stone"),
                    evolution(fromSpecies = "eevee", fromAspects = setOf("frost"), toSpecies = "glaceon", requiredContext = "cobblemon:ice_stone"),
                ),
                "eeveefrost" to listOf(
                    evolution(fromSpecies = "eevee", fromAspects = setOf("frost"), toSpecies = "glaceon", requiredContext = "cobblemon:ice_stone"),
                ),
                "pikachu" to listOf(
                    evolution(fromSpecies = "pikachu", toSpecies = "raichu", requiredContext = "cobblemon:thunder_stone"),
                ),
            ),
            allSpeciesNames = listOf("eevee", "vaporeon", "glaceon", "eeveefrost", "pikachu", "raichu"),
        )

        val pages = RecipeBuilder.buildEvolutionRecipesForItem("cobblemon:ice_stone", snapshot)

        assertEquals(1, pages.size)
        assertEquals("eeveefrost", pages.single().sourceSpeciesName)
        assertEquals("glaceon", pages.single().targetSpeciesName)
    }

    @Test
    fun formPagesAreOneFormPerPageAndRetainSiblingNavigationKeys() {
        val snapshot = CobbleDexDataSnapshot(
            speciesInfo = mapOf(
                "eevee" to speciesInfo("eevee", nationalDexNumber = 133),
                "eeveeflame" to speciesInfo(
                    "eeveeflame",
                    nationalDexNumber = 133,
                    primaryType = "fire",
                    abilities = listOf("Flash Fire"),
                    baseSpeciesName = "eevee",
                    formAspects = setOf("flame"),
                ),
                "eeveefrost" to speciesInfo(
                    "eeveefrost",
                    nationalDexNumber = 133,
                    primaryType = "ice",
                    abilities = listOf("Snow Cloak"),
                    baseSpeciesName = "eevee",
                    formAspects = setOf("frost"),
                ),
            ),
            allSpeciesNames = listOf("eevee", "eeveeflame", "eeveefrost"),
        )

        val pages = RecipeBuilder.buildFormsFor("eeveefrost", snapshot)

        assertEquals(2, pages.size)
        assertEquals(setOf("eeveeflame", "eeveefrost"), pages.flatMap { it.siblingFormKeys }.toSet())
        assertEquals(setOf("eeveeflame", "eeveefrost"), pages.map { it.form.formKey }.toSet())
        assertTrue(pages.all { it.totalForms == 2 })
    }

    private fun evolution(
        fromSpecies: String,
        toSpecies: String,
        fromAspects: Set<String> = emptySet(),
        toAspects: Set<String> = emptySet(),
        variant: String = "item_interact",
        requiredContext: String? = null,
    ) = EvolutionInfo(
        id = "$fromSpecies->$toSpecies:$variant:${requiredContext.orEmpty()}",
        fromSpecies = fromSpecies,
        fromAspects = fromAspects,
        toSpecies = toSpecies,
        toAspects = toAspects,
        variant = variant,
        requirements = emptyList(),
        requiredContext = requiredContext,
        consumeHeldItem = false,
    )

    private fun speciesInfo(
        name: String,
        nationalDexNumber: Int = 1,
        primaryType: String = "normal",
        secondaryType: String? = null,
        abilities: List<String> = listOf("Run Away"),
        labels: Set<String>? = null,
        baseSpeciesName: String? = null,
        formAspects: Set<String> = emptySet(),
    ) = EvolutionDataLoader.SpeciesBasicInfo(
        name = name,
        nationalDexNumber = nationalDexNumber,
        primaryType = primaryType,
        secondaryType = secondaryType,
        catchRate = 45,
        weight = 10f,
        height = 1f,
        baseStats = mapOf("hp" to 45, "attack" to 49, "defence" to 49),
        baseStatTotal = 143,
        abilities = abilities,
        labels = labels,
        baseSpeciesName = baseSpeciesName,
        formAspects = formAspects,
    )
}