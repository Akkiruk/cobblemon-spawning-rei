package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `tr()` resolves to the raw key on the test classpath (no Minecraft client), so these assertions
 * match against translation keys rather than English text.
 */
class HerdSpawnContextTest {

    private fun spawn(herd: HerdContext? = null, habitat: HabitatContext? = null) = SpawnInfo(
        id = "test", pokemon = "spinda", formAspects = "", bucket = "common", weight = 1f,
        levelRange = "5-10", context = "grounded", biomes = emptyList(), timeRange = null,
        weather = SpawnWeather(), dimensions = emptyList(), structures = emptyList(),
        canSeeSky = null, minLight = null, maxLight = null, minSkyLight = null, maxSkyLight = null,
        minY = null, maxY = null, neededNearbyBlocks = emptyList(), neededBaseBlocks = emptyList(),
        moonPhase = null, presets = emptyList(), fluid = null, anticondition = null,
        weightMultipliers = emptyList(), minLureLevel = null, herd = herd, habitat = habitat,
    )

    @Test
    fun leaderSpecialsCarryAlphaLine() {
        val specials = SpawnDisplayHelper.buildSpecials(
            spawn(HerdContext(HerdRole.LEADER, maxHerdSize = 6))
        )
        assertTrue(specials.any { it == "cobbledex-rei-emi-jei.spawn.herd.leader" })
        assertTrue(specials.any { it == "cobbledex-rei-emi-jei.spawn.herd.alpha" })
    }

    @Test
    fun followerSpecialsHaveNoAlphaLine() {
        val specials = SpawnDisplayHelper.buildSpecials(
            spawn(HerdContext(HerdRole.FOLLOWER, maxHerdSize = 6))
        )
        assertTrue(specials.any { it == "cobbledex-rei-emi-jei.spawn.herd.follower" })
        assertFalse(specials.any { it == "cobbledex-rei-emi-jei.spawn.herd.alpha" })
    }

    @Test
    fun habitatPhaseLineOnlyWhenPartial() {
        val partial = HabitatContext("cobblemon.habitat.zen_garden.name", "3", phaseCount = 1, totalPhases = 5)
        assertTrue(partial.displayLines().any { it == "cobbledex-rei-emi-jei.spawn.habitat.phases" })

        val allDay = HabitatContext("cobblemon.habitat.zen_garden.name", "1-5", phaseCount = 5, totalPhases = 5)
        assertFalse(allDay.displayLines().any { it == "cobbledex-rei-emi-jei.spawn.habitat.phases" })
    }

    @Test
    fun phaseSpecsExpandToDistinctPhaseNumbers() {
        assertEquals(setOf(1, 2, 3, 4, 5), JarDataCache.parsePhaseSet("1-5"))
        assertEquals(setOf(1, 3, 4, 5), JarDataCache.parsePhaseSet("1, 3-5"))
        assertEquals(setOf(3), JarDataCache.parsePhaseSet("3"))
        assertEquals(emptySet(), JarDataCache.parsePhaseSet(null))
        assertEquals(emptySet(), JarDataCache.parsePhaseSet(" "))
    }

    @Test
    fun herdAndNonHerdEntriesDoNotMerge() {
        val merged = SpawnDisplayHelper.mergeVariantSpawns(
            listOf(spawn(), spawn(HerdContext(HerdRole.LEADER, maxHerdSize = 6)))
        )
        assertEquals(2, merged.size)
    }
}
