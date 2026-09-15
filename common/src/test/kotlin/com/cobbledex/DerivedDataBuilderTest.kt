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
    fun buildsTheSpawnBiomeCrowdFromTheSameSnapshot() {
        fun spawn(pokemon: String, weight: Float, biomes: List<String>) = SpawnInfo(
            id = "t", pokemon = pokemon, formAspects = "", bucket = "common", weight = weight,
            levelRange = "1-5", context = "grounded", biomes = biomes, timeRange = null,
            weather = SpawnWeather(), dimensions = emptyList(), structures = emptyList(),
            canSeeSky = null, minLight = null, maxLight = null, minSkyLight = null, maxSkyLight = null,
            minY = null, maxY = null, neededNearbyBlocks = emptyList(), neededBaseBlocks = emptyList(),
            moonPhase = null, presets = emptyList(), fluid = null, anticondition = null,
            weightMultipliers = emptyList(), minLureLevel = null,
        )
        val pidgey = spawn("pidgey", 10f, listOf("minecraft:forest", "minecraft:plains"))
        val snapshot = CobbleDexDataSnapshot(
            spawnsBySpecies = mapOf(
                "pidgey" to listOf(pidgey),
                "spearow" to listOf(spawn("spearow", 90f, listOf("minecraft:forest"))),
            )
        )

        val result = DerivedDataBuilder.rebuild(snapshot) { emptyList() }

        assertTrue(result.snapshot.spawnBiomeCrowd.isNotEmpty())
        assertEquals("minecraft:plains", SpawnBiomeRanking.bestBiomeForWay(pidgey, result.snapshot.spawnBiomeCrowd))
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

    // findMentionedSpecies replaced a per-species-name Regex("\\b${escape(name)}\\b") scan (2.26.13,
    // performance) with tokenize-once-and-hash-lookup. No test exercised the fusion path before this
    // change (it depends on tr()/I18n, unavailable in a plain JVM unit test), so these compare the
    // new implementation against that exact old regex logic - kept inline here as the oracle - across
    // representative fusion-description shapes, to catch any behavioral drift from the rewrite.
    private fun oldRegexBasedMatch(descText: String, speciesNames: List<String>): Set<String> =
        speciesNames.filter { name ->
            Regex("\\b${Regex.escape(name)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(descText)
        }.toSet()

    @Test
    fun findMentionedSpeciesMatchesTheOldPerNameRegexScan() {
        val speciesNames = listOf(
            "charizard", "blastoise", "venusaur", "pikachu", "eevee", "vaporeon", "porygon2",
            "necrozma", "metagross", "solgaleo", "lunala",
        )
        val speciesSet = speciesNames.toHashSet()
        val maxWords = 1

        val cases = listOf(
            "A fusion of Charizard and Blastoise, possessing traits of both.",
            "Necrozma appears to have fully absorbed Metagross.",
            "This Pokemon was created by fusing Venusaur with Pikachu and Eevee!",
            "No fusion keywords here, just Charizard flying around.",
            "FUSING SOLGALEO WITH LUNALA IN ALL CAPS.",
            "Porygon2 fused with itself somehow.",
            "",
            "fusion fusion fusion but no species names at all",
            "Charizard-Blastoise hybrid, fused via unknown means.",
        )

        for (text in cases) {
            val expected = oldRegexBasedMatch(text, speciesNames)
            val actual = DerivedDataBuilder.findMentionedSpecies(text, speciesSet, maxWords)
            assertEquals(expected, actual, "Mismatch for: \"$text\"")
        }
    }

    @Test
    fun findMentionedSpeciesRequiresWholeWordMatches() {
        val speciesSet = setOf("eevee", "vee")
        // "vee" must not match inside "eeveevee"/"vaporeon" etc - whole-word only, same as \b did.
        assertEquals(emptySet(), DerivedDataBuilder.findMentionedSpecies("eeveevee is not a real word", speciesSet, 1))
        assertEquals(setOf("eevee"), DerivedDataBuilder.findMentionedSpecies("Eevee fused with something", speciesSet, 1))
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