package com.cobbledex

import com.cobbledex.SpawnPageModel.BiomeClass
import com.cobbledex.SpawnPageModel.TimeLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/** `tr()` returns the raw key on the test classpath, so assertions match keys. */
class SpawnPageModelTest {

    // ---- classifyBiome / splitBiomes

    @Test fun dimensionTagsClassifyAsDimension() {
        assertEquals(BiomeClass.DIMENSION, SpawnPageModel.classifyBiome("#cobblemon:is_overworld"))
        assertEquals(BiomeClass.DIMENSION, SpawnPageModel.classifyBiome("#minecraft:is_nether"))
        assertEquals(BiomeClass.DIMENSION, SpawnPageModel.classifyBiome("#aether:is_aether"))
        assertEquals(BiomeClass.DIMENSION, SpawnPageModel.classifyBiome("the_bumblezone:howling_constructs"))
    }

    @Test fun climateAndConcreteClassify() {
        assertEquals(BiomeClass.CLIMATE, SpawnPageModel.classifyBiome("#cobblemon:is_temperate"))
        assertEquals(BiomeClass.CONCRETE, SpawnPageModel.classifyBiome("minecraft:jungle"))
        assertEquals(BiomeClass.CONCRETE, SpawnPageModel.classifyBiome("aether:skyroot_forest"))
    }

    @Test fun splitBiomesDropsDimensionTagsToDimensionLabels() {
        val s = SpawnPageModel.splitBiomes(listOf("#cobblemon:is_overworld", "#cobblemon:is_spooky"))
        assertEquals(listOf("#cobblemon:is_spooky"), s.climateOrConcrete)
        assertTrue(s.dimensions.isEmpty()) // overworld -> no label

        val nether = SpawnPageModel.splitBiomes(listOf("#minecraft:is_nether"))
        assertTrue(nether.climateOrConcrete.isEmpty())
        assertEquals(listOf("cobbledex-rei-emi-jei.spawn.dim.nether"), nether.dimensions)
    }

    // ---- lightPhrase

    @Test fun lightPhraseCombinations() {
        assertEquals("cobbledex-rei-emi-jei.spawn.light.total_dark",
            SpawnPageModel.lightPhrase(0, 0, null, 0, null))
        assertEquals("cobbledex-rei-emi-jei.spawn.light.open_unlit",
            SpawnPageModel.lightPhrase(8, 15, null, 0, null))
        assertEquals("cobbledex-rei-emi-jei.spawn.light.open_sky",
            SpawnPageModel.lightPhrase(8, 15, null, null, null))
        assertEquals("cobbledex-rei-emi-jei.spawn.light.under_cover",
            SpawnPageModel.lightPhrase(null, null, null, null, false))
        assertNull(SpawnPageModel.lightPhrase(null, null, null, null, null))
    }

    // ---- normalizeTime

    @Test fun normalizeTimeNamedAndTicks() {
        assertEquals(TimeLabel.DAY, SpawnPageModel.normalizeTime("day"))
        assertEquals(TimeLabel.NIGHT, SpawnPageModel.normalizeTime("night"))
        assertEquals(TimeLabel.DUSK, SpawnPageModel.normalizeTime("twilight"))
        assertEquals(TimeLabel.ANY, SpawnPageModel.normalizeTime(null))
        assertEquals(TimeLabel.NIGHT, SpawnPageModel.normalizeTime("13000-23000"))
        assertEquals(TimeLabel.ODD, SpawnPageModel.normalizeTime("2000-3000"))
    }

    // ---- phraseMultiplier

    private fun wm(mult: Float, part: WeightConditionPart) =
        WeightMultiplier(multiplier = mult, conditionParts = listOf(part))

    @Test fun multiplierPhrasing() {
        val night = SpawnPageModel.phraseMultiplier(wm(0.25f, WeightConditionPart(type = "time_range", text = "13000-23000")))
        assertEquals("cobbledex-rei-emi-jei.spawn.mult.much cobbledex-rei-emi-jei.spawn.mult.less cobbledex-rei-emi-jei.spawn.mult.at_night", night?.text)
        assertFalse(night!!.spatial)

        val rain = SpawnPageModel.phraseMultiplier(wm(2f, WeightConditionPart(type = "rain")))
        assertEquals("cobbledex-rei-emi-jei.spawn.mult.more cobbledex-rei-emi-jei.spawn.mult.in_rain", rain?.text)

        assertNull(SpawnPageModel.phraseMultiplier(wm(1f, WeightConditionPart(type = "rain"))))
        assertNull(SpawnPageModel.phraseMultiplier(wm(0.5f, WeightConditionPart(type = "always"))))
    }

    // ---- interpretAnti

    @Test fun naturalBlocksAntiBecomesPositiveWhere() {
        val split = SpawnPageModel.interpretAnti(SpawnAntiCondition(neededBaseBlocks = listOf("#cobblemon:natural")))
        assertEquals(listOf("cobbledex-rei-emi-jei.spawn.on_built"), split.positiveWhere)
        assertTrue(split.remaining.neededBaseBlocks.isEmpty())
    }

    @Test fun otherAntiStaysExclusion() {
        val split = SpawnPageModel.interpretAnti(SpawnAntiCondition(biomes = listOf("#cobblemon:is_ocean")))
        assertTrue(split.positiveWhere.isEmpty())
        assertEquals(listOf("#cobblemon:is_ocean"), split.remaining.biomes)
    }

    // ---- blockChips

    @Test fun blockChipsSuppressStructureMaterialsAndTranslateTrees() {
        val underStructure = SpawnPageModel.blockChips(
            baseBlocks = listOf("#minecraft:trail_ruins_replaceable"),
            nearbyBlocks = emptyList(),
            hasStructure = true,
        )
        assertTrue(underStructure.isEmpty())

        val trees = SpawnPageModel.blockChips(listOf("#cobblemon:trees"), emptyList(), hasStructure = false)
        assertEquals(1, trees.size)
        assertEquals("cobbledex-rei-emi-jei.spawn.needs.trees", trees.first().label)

        val water = SpawnPageModel.blockChips(emptyList(), listOf("minecraft:water"), hasStructure = false)
        assertEquals("minecraft:water_bucket", water.first().itemId)
    }

    // ---- locatorFor

    private fun spawnWith(biomes: List<String> = emptyList(), structures: List<String> = emptyList()) = SpawnInfo(
        id = "t", pokemon = "x", formAspects = "", bucket = "common", weight = 1f, levelRange = "1-5",
        context = "grounded", biomes = biomes, timeRange = null, weather = SpawnWeather(),
        dimensions = emptyList(), structures = structures, canSeeSky = null, minLight = null, maxLight = null,
        minSkyLight = null, maxSkyLight = null, minY = null, maxY = null, neededNearbyBlocks = emptyList(),
        neededBaseBlocks = emptyList(), moonPhase = null, presets = emptyList(), fluid = null,
        anticondition = null, weightMultipliers = emptyList(), minLureLevel = null,
    )

    @Test fun displayGroupKeyMergesBiomeNoiseButSplitsOnRealDifference() {
        val freshRiver = spawnWith(biomes = listOf("#cobblemon:is_freshwater", "minecraft:river"))
        val freshSwamp = spawnWith(biomes = listOf("#cobblemon:is_freshwater", "minecraft:swamp"))
        assertEquals(SpawnPageModel.displayGroupKey(freshRiver), SpawnPageModel.displayGroupKey(freshSwamp))

        val jungleDay = spawnWith(biomes = listOf("#cobblemon:is_jungle")).copy(timeRange = "day")
        val jungleNight = spawnWith(biomes = listOf("#cobblemon:is_jungle")).copy(timeRange = "night")
        assertTrue(SpawnPageModel.displayGroupKey(jungleDay) != SpawnPageModel.displayGroupKey(jungleNight))
    }

    @Test fun lightBucketCoarseCategories() {
        assertEquals("dark", SpawnPageModel.lightBucket(null, null, null, 0, null))
        assertEquals("covered", SpawnPageModel.lightBucket(null, null, null, null, false))
        assertEquals("open", SpawnPageModel.lightBucket(8, 15, null, null, null))
        assertEquals("any", SpawnPageModel.lightBucket(null, null, null, null, null))
    }

    @Test fun locatorPrefersStructureThenConcreteThenClimateThenDimension() {
        assertEquals("Village", SpawnPageModel.locatorFor(spawnWith(structures = listOf("#minecraft:village"), biomes = listOf("#cobblemon:is_overworld"))))
        assertTrue(SpawnPageModel.locatorFor(spawnWith(biomes = listOf("#cobblemon:is_temperate"))).isNotBlank())
        assertEquals("cobbledex-rei-emi-jei.spawn.dim.nether", SpawnPageModel.locatorFor(spawnWith(biomes = listOf("#minecraft:is_nether"))))
        assertEquals("cobbledex-rei-emi-jei.spawn.anywhere", SpawnPageModel.locatorFor(spawnWith()))
    }
}
