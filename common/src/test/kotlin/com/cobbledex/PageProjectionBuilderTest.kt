package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PageProjectionBuilderTest {
    @Test
    fun obtainmentRoutesAreSpecialOnly() {
        val snapshot = CobbleDexDataSnapshot(
            speciesInfo = mapOf(
                "omastar" to speciesInfo("omastar", nationalDexNumber = 139),
                "omanyte" to speciesInfo("omanyte", nationalDexNumber = 138),
            ),
            // Wild spawns, fossils and evolution-into are deliberately NOT surfaced as obtainment
            // routes any more - they only restated the dedicated Spawn / Fossil / Evolution pages.
            spawnsBySpecies = mapOf("omastar" to listOf(spawn("omastar"))),
            obtainmentBySpecies = mapOf(
                "omastar" to listOf(
                    ObtainmentInfo(
                        pokemon = "omastar",
                        method = "altar",
                        description = "Summon with a rare star.",
                        items = listOf("minecraft:nether_star"),
                    )
                )
            ),
            fossilsBySpecies = mapOf("omastar" to listOf(FossilCombo("omastar", listOf("cobblemon:helix_fossil")))),
            allSpeciesNames = listOf("omanyte", "omastar"),
        )

        val omastar = PageProjectionBuilder.pokemon("omastar", snapshot)?.obtainmentRoutes.orEmpty()
        assertEquals(1, omastar.size)
        assertEquals("altar", omastar.single().obtainment.method)
        assertTrue("minecraft:nether_star" in omastar.single().itemIds)

        // A species with only spawn data and no special route produces no obtainment page.
        assertTrue(PageProjectionBuilder.pokemon("omanyte", snapshot)?.obtainmentRoutes.orEmpty().isEmpty())
    }

    @Test
    fun overviewProjectionsCanEnumeratePartialSnapshots() {
        val snapshot = CobbleDexDataSnapshot(
            speciesInfo = mapOf("lapras" to speciesInfo("lapras", nationalDexNumber = 131)),
            spawnsBySpecies = mapOf("lapras" to listOf(spawn("lapras"))),
        )

        val recipes = PageProjectionBuilder.allPokemon(snapshot).map { PokemonOverviewRecipeData(it) }

        assertEquals(listOf("lapras"), recipes.map { it.speciesName })
        assertTrue(recipes.single().projection.sortedSpawns.isNotEmpty())
    }

    @Test
    fun projectionCacheReusesPokemonPagesForTheSameSnapshot() {
        val snapshot = CobbleDexDataSnapshot(
            speciesInfo = mapOf("lapras" to speciesInfo("lapras", nationalDexNumber = 131)),
            spawnsBySpecies = mapOf("lapras" to listOf(spawn("lapras"))),
            allSpeciesNames = listOf("lapras"),
        )

        val allProjection = PageProjectionBuilder.allPokemon(snapshot).single()
        val directProjection = PageProjectionBuilder.pokemon("lapras", snapshot)

        assertSame(allProjection, directProjection)
    }

    private fun spawn(species: String) = SpawnInfo(
        id = "test:$species",
        pokemon = species,
        formAspects = "",
        bucket = "rare",
        weight = 1f,
        levelRange = "40-50",
        context = "grounded",
        biomes = listOf("minecraft:ocean"),
        timeRange = null,
        weather = SpawnWeather(),
        dimensions = listOf("minecraft:overworld"),
        structures = emptyList(),
        canSeeSky = null,
        minLight = null,
        maxLight = null,
        minSkyLight = null,
        maxSkyLight = null,
        minY = null,
        maxY = null,
        neededNearbyBlocks = emptyList(),
        neededBaseBlocks = emptyList(),
        moonPhase = null,
        presets = emptyList(),
        fluid = null,
        anticondition = null,
        weightMultipliers = emptyList(),
        minLureLevel = null,
    )

    private fun speciesInfo(name: String, nationalDexNumber: Int) = EvolutionDataLoader.SpeciesBasicInfo(
        name = name,
        nationalDexNumber = nationalDexNumber,
        primaryType = "rock",
        secondaryType = "water",
        catchRate = 45,
        weight = 35f,
        height = 1f,
        baseStats = mapOf("hp" to 70, "attack" to 60, "defence" to 125),
        abilities = listOf("Swift Swim"),
    )
}