package com.cobbledex

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Characterisation tests for the local-file requirement reader.
 *
 * This reader used to be a hand-written branch per variant; it is now table-driven off
 * [EvolutionRequirementSpec]. These assertions pin the observable behaviour — variant name and
 * data keys — so the table cannot silently change what a requirement decodes to.
 */
class EvolutionRequirementJsonTest {

    private fun parse(json: String): EvolutionRequirement =
        JarDataCache.parseRequirementFromJson(JsonParser.parseString(json).asJsonObject)

    @Test
    fun readsIntFields() {
        val req = parse("""{"variant":"level","minLevel":16}""")
        assertEquals("level", req.variant)
        assertEquals(16, req.data["minLevel"])
    }

    @Test
    fun readsStringFields() {
        val req = parse("""{"variant":"held_item","itemCondition":"cobblemon:razor_claw"}""")
        assertEquals("held_item", req.variant)
        assertEquals("cobblemon:razor_claw", req.data["itemCondition"])
    }

    @Test
    fun readsBooleanFields() {
        val req = parse("""{"variant":"weather","isRaining":true}""")
        assertEquals("weather", req.variant)
        assertEquals(true, req.data["isRaining"])
    }

    @Test
    fun readsMultiFieldVariants() {
        val req = parse("""{"variant":"biome","biomeCondition":"#minecraft:is_forest","biomeAnticondition":"minecraft:desert"}""")
        assertEquals("biome", req.variant)
        assertEquals("#minecraft:is_forest", req.data["biomeCondition"])
        assertEquals("minecraft:desert", req.data["biomeAnticondition"])
    }

    @Test
    fun aliasesDecodeToTheCanonicalVariant() {
        assertEquals("move_set", parse("""{"variant":"has_move","move":"rollout"}""").variant)
        assertEquals("move_type", parse("""{"variant":"has_move_type","type":"fairy"}""").variant)
        assertEquals("pokemon_properties", parse("""{"variant":"properties","target":"shiny=true"}""").variant)
    }

    @Test
    fun absentFieldsAreOmittedRatherThanNulled() {
        val req = parse("""{"variant":"level"}""")
        assertEquals("level", req.variant)
        assertEquals(emptyMap(), req.data)
    }

    @Test
    fun anyUnwrapsToItsFirstPossibility() {
        val req = parse("""{"variant":"any","possibilities":[{"variant":"level","minLevel":30},{"variant":"friendship","amount":220}]}""")
        assertEquals("level", req.variant)
        assertEquals(30, req.data["minLevel"])
    }

    @Test
    fun anyWithNoPossibilitiesStaysAny() {
        assertEquals("any", parse("""{"variant":"any"}""").variant)
    }

    @Test
    fun unknownVariantsKeepTheirPrimitiveFields() {
        val req = parse("""{"variant":"future_thing","someNumber":7,"someText":"x","someFlag":false}""")
        assertEquals("future_thing", req.variant)
        assertEquals("x", req.data["someText"])
        assertEquals(false, req.data["someFlag"])
        assertEquals(7, (req.data["someNumber"] as Number).toInt())
    }

    @Test
    fun missingVariantFallsBackToUnknown() {
        assertEquals(EvolutionRequirementSpec.UNKNOWN, parse("""{"minLevel":5}""").variant)
    }
}
