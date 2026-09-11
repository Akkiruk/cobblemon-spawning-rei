package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpawnBiomeRankingTest {

    private fun spawn(
        pokemon: String,
        weight: Float,
        biomes: List<String>,
        bucket: String = "common",
        timeRange: String? = null,
    ) = SpawnInfo(
        id = "t", pokemon = pokemon, formAspects = "", bucket = bucket, weight = weight,
        levelRange = "1-5", context = "grounded", biomes = biomes, timeRange = timeRange,
        weather = SpawnWeather(), dimensions = emptyList(), structures = emptyList(),
        canSeeSky = null, minLight = null, maxLight = null, minSkyLight = null, maxSkyLight = null,
        minY = null, maxY = null, neededNearbyBlocks = emptyList(), neededBaseBlocks = emptyList(),
        moonPhase = null, presets = emptyList(), fluid = null, anticondition = null,
        weightMultipliers = emptyList(), minLureLevel = null,
    )

    @Test fun namesTheBiomeWithAClearOddsEdge() {
        val ours = spawn("pidgey", 10f, listOf("minecraft:forest", "minecraft:plains"))
        val crowd = SpawnBiomeRanking.buildCrowd(
            mapOf(
                "pidgey" to listOf(ours),
                "spearow" to listOf(spawn("spearow", 90f, listOf("minecraft:forest"))), // crowds forest
                "starly" to listOf(spawn("starly", 2f, listOf("minecraft:plains"))),
            )
        )
        assertEquals("minecraft:plains", SpawnBiomeRanking.bestBiomeForWay(ours, crowd))
    }

    @Test fun saysNothingWhenTheBiomesAreCloseToEqual() {
        val ours = spawn("eevee", 10f, listOf("minecraft:forest", "minecraft:plains"))
        val crowd = SpawnBiomeRanking.buildCrowd(
            mapOf(
                "eevee" to listOf(ours),
                "a" to listOf(spawn("a", 11f, listOf("minecraft:forest"))),
                "b" to listOf(spawn("b", 10f, listOf("minecraft:plains"))),
            )
        )
        assertNull(SpawnBiomeRanking.bestBiomeForWay(ours, crowd))
    }

    @Test fun oneBiomeNeverGetsACaption() {
        val ours = spawn("zubat", 10f, listOf("minecraft:lush_caves"))
        val crowd = SpawnBiomeRanking.buildCrowd(mapOf("zubat" to listOf(ours)))
        assertNull(SpawnBiomeRanking.bestBiomeForWay(ours, crowd))
    }

    @Test fun duplicateSpawnRowsDoNotInflateOwnCompetition() {
        // Magikarp ships two identical common [river] rows; that must not count as its own rival.
        val dup1 = spawn("magikarp", 50f, listOf("minecraft:river", "minecraft:swamp"))
        val dup2 = spawn("magikarp", 50f, listOf("minecraft:river", "minecraft:swamp"))
        val crowdWithDup = SpawnBiomeRanking.buildCrowd(
            mapOf(
                "magikarp" to listOf(dup1, dup2),
                "slowpoke" to listOf(spawn("slowpoke", 40f, listOf("minecraft:swamp"))), // crowds swamp
            )
        )
        // river is uncontested, swamp shares with slowpoke -> river wins, and the dup didn't halve it.
        assertEquals("minecraft:river", SpawnBiomeRanking.bestBiomeForWay(dup1, crowdWithDup))
    }

    @Test fun dayAndNightSpawnsDoNotCompete() {
        val day = spawn("volbeat", 10f, listOf("minecraft:plains", "minecraft:sunflower_plains"), timeRange = "day")
        val night = spawn("illumise", 100f, listOf("minecraft:plains"), timeRange = "night")
        val crowd = SpawnBiomeRanking.buildCrowd(mapOf("volbeat" to listOf(day), "illumise" to listOf(night)))
        // If night competed, plains would look mobbed; it doesn't, so the two plains-ish biomes tie -> null.
        assertNull(SpawnBiomeRanking.bestBiomeForWay(day, crowd))
    }

    @Test fun headlineWontSendYouToARareSpawnWhenACommonOneIsBetter() {
        // Common way's best biome sits at ~0.5 share; rare way's best is uncontested (1.0).
        // Raw share would pick the rare biome; bucket weighting keeps the common one.
        val commonWay = spawn("gible", 10f, listOf("minecraft:plains", "minecraft:forest"))
        val rareWay = spawn("gible", 10f, listOf("minecraft:badlands", "minecraft:eroded_badlands"), bucket = "rare")
        val crowd = SpawnBiomeRanking.buildCrowd(
            mapOf(
                "gible" to listOf(commonWay, rareWay),
                "forestbuddy" to listOf(spawn("forestbuddy", 10f, listOf("minecraft:forest"))),
                "plainsmob" to listOf(spawn("plainsmob", 990f, listOf("minecraft:plains"))),
                "erodedmob" to listOf(spawn("erodedmob", 40f, listOf("minecraft:eroded_badlands"), bucket = "rare")),
            )
        )
        assertEquals("minecraft:badlands", SpawnBiomeRanking.bestBiomeForWay(rareWay, crowd)) // rare way's own pick
        assertEquals("minecraft:forest", SpawnBiomeRanking.headline(listOf(commonWay, rareWay), crowd))
    }

    @Test fun noDataDegradesQuietly() {
        val s = spawn("x", 10f, listOf("minecraft:forest", "minecraft:plains"))
        assertNull(SpawnBiomeRanking.bestBiomeForWay(s, emptyMap()))
        assertNull(SpawnBiomeRanking.headline(listOf(s), emptyMap()))
        assertEquals(emptyMap(), SpawnBiomeRanking.buildCrowd(emptyMap()))
    }
}
