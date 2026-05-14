package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PageProjectionBuilderTest {
    @Test
    fun buildsUnifiedObtainmentRoutesFromSnapshotSources() {
        val evolution = EvolutionInfo(
            id = "test:omastar",
            fromSpecies = "omanyte",
            toSpecies = "omastar",
            variant = "level_up",
            requirements = listOf(EvolutionRequirement("level", mapOf("minLevel" to 40))),
            requiredContext = null,
            consumeHeldItem = false,
        )
        val snapshot = CobbleDexDataSnapshot(
            speciesInfo = mapOf(
                "omastar" to speciesInfo("omastar", nationalDexNumber = 139),
                "omanyte" to speciesInfo("omanyte", nationalDexNumber = 138),
            ),
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
            evolutionsToSpecies = mapOf("omastar" to listOf(evolution)),
            allSpeciesNames = listOf("omanyte", "omastar"),
        )

        val projection = PageProjectionBuilder.pokemon("omastar", snapshot)

        val routes = projection?.obtainmentRoutes.orEmpty()
        assertTrue(routes.any { it is ObtainmentRoute.WildSpawns })
        assertTrue(routes.any { it is ObtainmentRoute.Special && "minecraft:nether_star" in it.itemIds })
        assertTrue(routes.any { it is ObtainmentRoute.Fossil && "cobblemon:helix_fossil" in it.itemIds })
        assertTrue(routes.any { it is ObtainmentRoute.Evolution && it.evolution.fromSpecies == "omanyte" })
        assertEquals(listOf("WildSpawns", "Special", "Fossil", "Evolution"), routes.map { routeName(it) })
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

    private fun routeName(route: ObtainmentRoute): String = when (route) {
        is ObtainmentRoute.WildSpawns -> "WildSpawns"
        is ObtainmentRoute.Special -> "Special"
        is ObtainmentRoute.Fossil -> "Fossil"
        is ObtainmentRoute.Evolution -> "Evolution"
    }
}