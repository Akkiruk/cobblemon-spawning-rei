package com.cobbledex

import com.cobbledex.EvolutionDataLoader.SpeciesBasicInfo
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The merger's contract: fill only what Cobblemon left at its unset default, never overwrite a
 * value Cobblemon actually supplied.
 *
 * These exercise [SpeciesTraitMerger.mergeTraits] directly rather than [SpeciesTraitMerger.fillGaps],
 * which reads the JarDataCache singleton.
 */
class SpeciesTraitMergerTest {

    private fun species(
        catchRate: Int = 45,
        eggGroups: List<String>? = null,
        eggCycles: Int? = 120,
        baseFriendship: Int? = 0,
        baseExperienceYield: Int? = 10,
    ) = SpeciesBasicInfo(
        name = "testmon",
        nationalDexNumber = 1,
        primaryType = "normal",
        secondaryType = null,
        catchRate = catchRate,
        weight = 1f,
        height = 1f,
        eggGroups = eggGroups,
        eggCycles = eggCycles,
        baseFriendship = baseFriendship,
        baseExperienceYield = baseExperienceYield,
    )

    private val localTraits = JarDataCache.JarTraitData(
        catchRate = 190,
        eggGroups = listOf("field"),
        eggCycles = 20,
        baseFriendship = 70,
        baseExperienceYield = 64,
    )

    @Test
    fun fillsFieldsCobblemonLeftAtTheirUnsetDefaults() {
        val (merged, filled) = SpeciesTraitMerger.mergeTraits(species(), localTraits)

        assertEquals(190, merged.catchRate)
        assertEquals(listOf("field"), merged.eggGroups)
        assertEquals(20, merged.eggCycles)
        assertEquals(70, merged.baseFriendship)
        assertEquals(64, merged.baseExperienceYield)
        assertEquals(5, filled)
    }

    @Test
    fun neverOverwritesValuesCobblemonActuallySupplied() {
        val synced = species(
            catchRate = 3,
            eggGroups = listOf("dragon"),
            eggCycles = 40,
            baseFriendship = 35,
            baseExperienceYield = 200,
        )

        val (merged, filled) = SpeciesTraitMerger.mergeTraits(synced, localTraits)

        assertEquals(3, merged.catchRate)
        assertEquals(listOf("dragon"), merged.eggGroups)
        assertEquals(40, merged.eggCycles)
        assertEquals(35, merged.baseFriendship)
        assertEquals(200, merged.baseExperienceYield)
        assertEquals(0, filled)
    }

    @Test
    fun leavesGapsAloneWhenLocalFilesKnowNothing() {
        val (merged, filled) = SpeciesTraitMerger.mergeTraits(species(), JarDataCache.JarTraitData())

        assertEquals(45, merged.catchRate)
        assertEquals(null, merged.eggGroups)
        assertEquals(0, filled)
    }
}
