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

    private fun member(
        species: String,
        role: HerdRole = HerdRole.FOLLOWER,
        isAlpha: Boolean = false,
        weight: Float = 1f,
        maxCount: Int = 4,
    ) = HerdMemberRef(species, "", role, isAlpha, null, "10-20", maxCount, weight)

    private fun herd(vararg members: HerdMemberRef, size: Int = 6) = HerdContext(members.toList(), size)

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
    fun allHerdMembershipsCollapseToOneSpawnEntry() {
        val ctx = herd(member("girafarig"), member("farigiraf", HerdRole.LEADER, isAlpha = true))
        val entries = SpawnDisplayHelper.buildSortedSpawns(
            listOf(
                spawn(),                                                    // a normal spawn
                spawn(ctx).copy(biomes = listOf("#cobblemon:is_savanna")),   // herd, one biome
                spawn(ctx).copy(biomes = listOf("#cobblemon:is_plains")),    // same herd, other biome
                spawn(ctx).copy(bucket = "boss"),                            // alpha herd variant
            )
        )
        assertEquals(2, entries.size) // one plain + one collapsed herd row
        assertEquals(1, entries.count { it.spawn.herd != null })
    }

    @Test
    fun hasAlphaReflectsLeaderFlag() {
        assertTrue(herd(member("girafarig"), member("farigiraf", HerdRole.LEADER, isAlpha = true)).hasAlpha)
        assertFalse(herd(member("bulbasaur"), member("ivysaur")).hasAlpha)
    }

    @Test
    fun rosterKeyIsOrderIndependent() {
        val a = herd(member("bulbasaur"), member("venusaur"), size = 4)
        val b = herd(member("venusaur"), member("bulbasaur"), size = 4)
        assertEquals(a.rosterKey(), b.rosterKey())
    }

    @Test
    fun herdInfoOrdersLeaderFirstThenFollowersByWeight() {
        val ctx = herd(
            member("magikarp", weight = 10f),
            member("gyarados", HerdRole.LEADER, isAlpha = true, weight = 30f),
            member("gyarados", weight = 20f),
        )
        val info = HerdInfo.from(listOf(spawn(ctx))) ?: error("herd not derived")
        assertEquals("gyarados", info.leader.species)
        assertTrue(info.hasDesignatedLeader)
        assertEquals(listOf("gyarados", "magikarp"), info.followers.map { it.species })
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
            listOf(spawn(), spawn(herd(member("bulbasaur", HerdRole.LEADER))))
        )
        assertEquals(2, merged.size)
    }
}
