package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DerivedDataBuilderTest {
    @Test
    fun backfillsEvolutionKeysAsFormsAndKeepsDerivedIndexesTogether() {
        val snapshot = CobbleDexDataSnapshot(
            speciesInfo = mapOf("eevee" to speciesInfo("eevee", nationalDexNumber = 133)),
            evolutionsBySpecies = mapOf(
                "eeveefrost" to listOf(
                    EvolutionInfo(
                        id = "test:evolution",
                        fromSpecies = "eevee",
                        fromAspects = setOf("frost"),
                        toSpecies = "glaceon",
                        toAspects = emptySet(),
                        variant = "item_interact",
                        requirements = emptyList(),
                        requiredContext = null,
                        consumeHeldItem = true,
                    )
                )
            ),
        )

        val result = DerivedDataBuilder.rebuild(snapshot) { emptyList() }

        assertEquals(1, result.backfilledSpeciesInfoCount)
        assertEquals("eevee", result.snapshot.speciesInfo["eeveefrost"]?.baseSpeciesName)
        assertTrue(result.snapshot.speciesInfo["eeveefrost"]?.isForm == true)
        assertEquals(listOf("eevee", "eeveefrost", "glaceon"), result.snapshot.allSpeciesNames)
        assertEquals("test:evolution", result.snapshot.evolutionsToSpecies["glaceon"]?.single()?.id)
    }

    @Test
    fun buildsDropAndMoveLearnerIndexesFromTheSameSnapshot() {
        val shadowBall = MoveDetail("Shadow Ball", "ghost", "SPECIAL", 80, 100, 15)
        val lick = MoveDetail("Lick", "ghost", "PHYSICAL", 30, 100, 30)
        val snapshot = CobbleDexDataSnapshot(
            speciesInfo = mapOf(
                "gengar" to speciesInfo(
                    "gengar",
                    nationalDexNumber = 94,
                    drops = listOf(DropEntryInfo("minecraft:phantom_membrane", 25f, 1)),
                    tmMoves = listOf(shadowBall),
                    levelUpMoves = listOf(LevelUpMove(1, listOf(lick))),
                )
            )
        )

        val result = DerivedDataBuilder.rebuild(snapshot) { emptyList() }

        assertEquals(listOf("gengar"), result.snapshot.dropsByItem["minecraft:phantom_membrane"])
        // The learner index covers every method, not just TM.
        assertEquals(listOf("gengar"), result.snapshot.speciesByMove["shadow ball"])
        assertEquals(listOf("gengar"), result.snapshot.speciesByMove["lick"])
    }

    @Test
    fun queriesEvaluateMaterialFormsAgainstTheirSnapshot() {
        val snapshot = CobbleDexDataSnapshot(
            speciesInfo = mapOf(
                "vivillon" to speciesInfo("vivillon"),
                "vivillonfancy" to speciesInfo(
                    "vivillonfancy",
                    baseSpeciesName = "vivillon",
                    formAspects = setOf("fancy"),
                ),
            )
        )
        val queries = CobbleDexDataQueries(snapshot)

        assertTrue(queries.isForm("vivillonfancy"))
        assertEquals("vivillon", queries.getBaseOf("vivillonfancy"))
        assertFalse(queries.shouldSurfaceSpecies("vivillonfancy"))
    }

    private fun speciesInfo(
        name: String,
        nationalDexNumber: Int = 1,
        primaryType: String = "normal",
        secondaryType: String? = null,
        drops: List<DropEntryInfo>? = null,
        tmMoves: List<MoveDetail>? = null,
        levelUpMoves: List<LevelUpMove>? = null,
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
        abilities = listOf("Run Away"),
        drops = drops,
        tmMoves = tmMoves,
        levelUpMoves = levelUpMoves,
        baseSpeciesName = baseSpeciesName,
        formAspects = formAspects,
    )
}