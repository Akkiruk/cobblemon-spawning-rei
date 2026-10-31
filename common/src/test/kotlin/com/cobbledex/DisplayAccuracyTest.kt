package com.cobbledex

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `tr()` returns the raw key on the test classpath, so text assertions compare keys. */
class DisplayAccuracyTest {

    private fun json(text: String) = JsonParser.parseString(text).asJsonObject

    private val herd = json(
        """
        {"id": "tauros-alpha-1", "type": "pokemon-herd", "bucket": "boss", "weight": 100.0, "levelRange": "22-100",
         "maxHerdSize": 8, "spawnablePositionType": "grounded", "presets": ["natural"],
         "condition": {"biomes": ["#cobblemon:is_grassland"]},
         "herdablePokemon": [
           {"pokemon": "tauros held_item=cobblemon:normal_gem alpha=true", "isLeader": true, "isFollower": false, "levelRange": "1-100", "maxTimes": 1, "weight": 30.0},
           {"pokemon": "tauros paldean bull_breed=blaze", "isFollower": true, "levelRange": "27-46", "maxTimes": 7, "weight": 80.0}
         ]}
        """
    )

    @Test
    fun herdsAreReadFromFiles() {
        val infos = JarDataCache.parseHerdSpawn(herd, emptyMap())

        assertEquals(2, infos.size)
        assertEquals("#cobblemon:is_grassland", infos[0].biomes.single())
        val context = assertNotNull(infos[0].herd)
        assertEquals(8, context.maxHerdSize)
        assertEquals(HerdRole.LEADER, context.members.first().role)
        assertTrue(context.members.first().isAlpha)
        assertEquals("cobblemon:normal_gem", context.members.first().heldItemId)
        assertEquals("paldean bull_breed=blaze", infos[1].formAspects)
        assertEquals("tauros-alpha-1#herd:tauros", infos[1].id)
    }

    @Test
    fun fileSpawnsKeepWhatTheRuntimeReaderKeeps() {
        val spawn = json(
            """
            {"id": "x", "pokemon": "wooper", "bucket": "common", "level": "1-5", "weight": 1,
             "condition": {"isPokeSnack": true, "isSlimeChunk": true, "maxLureLevel": 2, "rodType": "cobblemon:love_rod",
                           "bait": "cobblemon:love_sweet", "minX": 0, "minHeight": 3, "neededBaseBlocks": ["#cobblemon:natural", "minecraft:sand"]},
             "anticondition": {"neededBaseBlocks": ["minecraft:farmland"], "minLureLevel": 2, "isSlimeChunk": true},
             "drops": {"amount": 1, "entries": [{"item": "minecraft:blaze_powder", "percentage": 10.0}]}}
            """
        )

        val info = assertNotNull(JarDataCache.parseSpawnEntry(spawn, emptyMap()))

        assertEquals(true, info.isPokeSnack)
        assertEquals(true, info.isSlimeChunk)
        assertEquals(2, info.maxLureLevel)
        assertEquals("cobblemon:love_rod", info.rodType)
        assertEquals("cobblemon:love_sweet", info.bait)
        assertEquals(0, info.minX)
        assertEquals(3, info.minHeight)
        assertEquals(listOf("minecraft:sand"), info.neededBaseBlocks)
        assertEquals("minecraft:blaze_powder", info.spawnDrops.single().itemId)
        val anti = assertNotNull(info.anticondition)
        assertEquals(emptyList(), anti.neededBaseBlocks)
        assertEquals(2, anti.minLureLevel)
        assertEquals(true, anti.isSlimeChunk)
    }

    private fun multiplierOf(body: String): WeightMultiplier {
        val spawn = json("""{"pokemon": "a", "weight": 1, $body}""")
        return assertNotNull(JarDataCache.parseSpawnEntry(spawn, emptyMap())).weightMultipliers.single()
    }

    @Test
    fun aBonusNamesItsConditions() {
        val snack = multiplierOf(""""weightMultiplier": {"multiplier": 2.0, "condition": {"isPokeSnack": true}}""")
        assertEquals("poke_snack", snack.conditionParts.single().type)
        assertEquals("cobbledex-rei-emi-jei.spawn.mult.more cobbledex-rei-emi-jei.spawn.mult.poke_snack", SpawnPageModel.phraseMultiplier(snack)?.text)

        val lava = multiplierOf(""""weightMultipliers": [{"multiplier": 5.0, "condition": {"neededNearbyBlocks": ["#minecraft:lava"]}}]""")
        assertEquals(listOf("#minecraft:lava"), lava.conditionParts.single().ids)
        assertEquals(true, SpawnPageModel.phraseMultiplier(lava)?.spatial)
    }

    @Test
    fun anExcludingBonusIsNotCalledAlways() {
        val notSpooky = multiplierOf(""""weightMultiplier": {"multiplier": 2.0, "anticondition": {"biomes": ["#cobblemon:is_spooky"]}}""")

        assertEquals("not_biomes", notSpooky.conditionParts.single().type)
        assertEquals("cobbledex-rei-emi-jei.weight.not", notSpooky.displayConditionSummary())
    }

    @Test
    fun aBonusWithSeveralConditionsListsThemAll() {
        val both = multiplierOf(""""weightMultiplier": {"multiplier": 2.0, "condition": {"isRaining": true, "minLureLevel": 1, "maxLureLevel": 2}}""")

        assertEquals(listOf("rain", "lure"), both.conditionParts.map { it.type })
        assertEquals("1-2", both.conditionParts.last().text)
    }

    @Test
    fun aBonusForAMissingStructureGoesAndAnExclusionOfOneIsHarmless() {
        val missing = { id: String -> id.startsWith("nullscape:") }
        fun spawn(vararg m: WeightMultiplier) = SpawnInfo.minimal("x", "a", "common", 1f, "1-5").copy(weightMultipliers = m.toList())
        val dead = WeightMultiplier(2f, conditionParts = listOf(WeightConditionPart("structures", ids = listOf("nullscape:x"))))
        val anti = WeightMultiplier(3f, conditionParts = listOf(WeightConditionPart("not_biomes", ids = listOf("nullscape:y"))))

        val kept = assertNotNull(ModFilter.withoutMissing(spawn(dead, anti), missing)).weightMultipliers

        assertEquals(listOf(3f), kept.map { it.multiplier })
        assertEquals("always", kept.single().conditionParts.single().type)
    }

    @Test
    fun negateCarriesTheRequirementItFlips() {
        val req = JarDataCache.parseRequirementFromJson(
            json("""{"variant": "negate", "inner": {"variant": "biome", "biomeCondition": "#cobblemon:is_overworld"}}""")
        )

        assertEquals("negate", req.variant)
        assertEquals("cobbledex-rei-emi-jei.evo.not_in_biome", req.displayText)
    }

    @Test
    fun aPartyTypeRequirementIsNotShownRaw() {
        val req = EvolutionRequirement("party_member", mapOf("target" to "type=dark", "contains" to true))

        assertEquals("cobbledex-rei-emi-jei.evo.party_type", req.displayText)
    }

    private fun edge(from: Set<String> = emptySet(), to: Set<String> = emptySet(), target: String = "raichu") = EvolutionInfo(
        id = "e", fromSpecies = "pikachu", fromAspects = from, toSpecies = target, toAspects = to,
        variant = "item_interact", requirements = emptyList(), requiredContext = null, consumeHeldItem = false,
    )

    @Test
    fun cosmeticCopiesOfAnEvolutionGo() {
        val cosmetic = setOf("cosmetic_item-lava_cookie")
        val kept = EvolutionEdges.withoutCosmeticCopies(
            mapOf("pikachu" to listOf(edge(), edge(to = setOf("alolan")), edge(from = cosmetic), edge(from = cosmetic, to = setOf("alolan"))))
        )

        assertEquals(2, kept.getValue("pikachu").size)
    }

    @Test
    fun aCosmeticFormsOnlyRouteIsKept() {
        val only = edge(from = setOf("cosmetic_item-x"), target = "special")

        assertEquals(1, EvolutionEdges.withoutCosmeticCopies(mapOf("pikachu" to listOf(edge(), only))).getValue("pikachu").count { it.toSpecies == "special" })
    }

    @Test
    fun formsInheritTraitsTheyDoNotState() {
        val base = JarDataCache.JarTraitData(catchRate = 255, eggGroups = listOf("field"), eggCycles = 15, baseFriendship = 50)
        val form = JarDataCache.JarTraitData(baseExperienceYield = 51)

        val merged = form.orElse(base)

        assertEquals(255, merged.catchRate)
        assertEquals(listOf("field"), merged.eggGroups)
        assertEquals(51, merged.baseExperienceYield)
        assertEquals(form, form.orElse(null))
    }

    @Test
    fun moonPhasesAreNamed() {
        assertEquals("cobbledex-rei-emi-jei.moon.0, cobbledex-rei-emi-jei.moon.4", formatMoonPhase("0,4"))
        assertEquals("cobbledex-rei-emi-jei.moon.0, cobbledex-rei-emi-jei.moon.4", formatMoonPhase("0-0,4-4"))
        assertEquals(3, formatMoonPhase("1-3").split(", ").size)
        assertEquals("Full", formatMoonPhase("full"))
    }

    @Test
    fun rangesReadNaturally() {
        assertEquals("2-5", WeightConditionParts.rangeText(2, 5))
        assertEquals("2", WeightConditionParts.rangeText(2, 2))
        assertEquals("≥2", WeightConditionParts.rangeText(2, null))
        assertEquals("≤5", WeightConditionParts.rangeText(null, 5))
        assertNull(WeightConditionParts.rangeText(null, null))
    }

    @Test
    fun aRegionalEvolutionTagReadsAsARegion() {
        assertEquals("cobbledex-rei-emi-jei.biome.regional", formatBiomeName("#cobblemon:evolution/regional/pikachu_alolabiome"))
    }

    @Test
    fun clearWeatherAndSkyBonusesAreNamed() {
        val clear = multiplierOf(""""weightMultiplier": {"multiplier": 0.5, "condition": {"isRaining": false}}""")
        assertEquals("clear", clear.conditionParts.single().type)

        val sky = multiplierOf(""""weightMultiplier": {"multiplier": 2.0, "condition": {"canSeeSky": true, "minSkyLight": 8}}""")
        assertEquals(listOf("open_sky", "sky_light"), sky.conditionParts.map { it.type })
    }

    @Test
    fun quotedNumbersCount() {
        val lure = multiplierOf(""""weightMultiplier": {"multiplier": 2.0, "condition": {"minLureLevel": "3"}}""")

        assertEquals(3, lure.conditionParts.single().number)
    }

    @Test
    fun aRegionBiasIsNotADifferentResult() {
        assertEquals(EvolutionEdges.destination(edge()), EvolutionEdges.destination(edge(to = setOf("region-bias-hisui"))))
        assertTrue(EvolutionEdges.destination(edge()) != EvolutionEdges.destination(edge(to = setOf("alolan"))))
    }

    @Test
    fun aTickRangeCoveringTheNightReadsAsNight() {
        assertEquals("cobbledex-rei-emi-jei.spawn.time.night", formatSpawnTime("13000-23000"))
        assertEquals("Twilight", formatSpawnTime("twilight"))
        assertEquals("08:00-09:00", formatSpawnTime("2000-3000"))
    }
}
