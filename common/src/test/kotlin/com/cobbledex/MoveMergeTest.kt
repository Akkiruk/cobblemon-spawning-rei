package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoveMergeTest {
    private fun move(name: String) = MoveDetail(name, "normal", "PHYSICAL", 40, 100, 15)

    private fun info(
        levelUp: List<LevelUpMove>? = null,
        egg: List<MoveDetail>? = null,
        tutor: List<MoveDetail>? = null,
        tm: List<MoveDetail>? = null,
    ) = EvolutionDataLoader.SpeciesBasicInfo(
        name = "pikachu",
        nationalDexNumber = 25,
        primaryType = "electric",
        secondaryType = null,
        catchRate = 190,
        weight = 6f,
        height = 0.4f,
        levelUpMoves = levelUp,
        eggMoves = egg,
        tutorMoves = tutor,
        tmMoves = tm,
    )

    @Test
    fun collapsesAMoveLearnableSeveralWaysIntoOneEntry() {
        val entries = RecipeBuilder.mergeMoveEntries(
            info(
                levelUp = listOf(LevelUpMove(36, listOf(move("Thunderbolt")))),
                tm = listOf(move("Thunderbolt")),
                tutor = listOf(move("Thunderbolt")),
            )
        )

        assertEquals(1, entries.size)
        val e = entries.single()
        assertEquals(listOf(36), e.levelUpLevels)
        assertTrue(e.tm)
        assertTrue(e.tutor)
        assertTrue(e.isLevelUp)
    }

    @Test
    fun mergesMultipleLevelsForTheSameMoveInOrder() {
        val entries = RecipeBuilder.mergeMoveEntries(
            info(
                levelUp = listOf(
                    LevelUpMove(36, listOf(move("Thunderbolt"))),
                    LevelUpMove(1, listOf(move("Thunderbolt"))),
                ),
            )
        )

        assertEquals(listOf(1, 36), entries.single().levelUpLevels)
    }

    @Test
    fun keepsDistinctMovesAndTheirMethods() {
        val entries = RecipeBuilder.mergeMoveEntries(
            info(
                levelUp = listOf(LevelUpMove(1, listOf(move("Thunder Shock")))),
                egg = listOf(move("Volt Tackle")),
                tm = listOf(move("Dig")),
            )
        ).associateBy { it.move.name }

        assertEquals(setOf("Thunder Shock", "Volt Tackle", "Dig"), entries.keys)
        assertTrue(entries.getValue("Volt Tackle").egg)
        assertTrue(entries.getValue("Volt Tackle").levelUpLevels.isEmpty())
        assertEquals("egg", entries.getValue("Volt Tackle").primaryMethod())
        assertEquals("levelup", entries.getValue("Thunder Shock").primaryMethod())
        assertEquals("tm", entries.getValue("Dig").primaryMethod())
    }
}
