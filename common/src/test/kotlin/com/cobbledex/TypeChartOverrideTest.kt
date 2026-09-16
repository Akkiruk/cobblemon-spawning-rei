package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertEquals

class TypeChartOverrideTest {

    // Real shape from Project Lazuli's data/projectlazuli/mega_showdown/showdown/typecharts/fairy.js
    // (a mega_showdown-style Showdown damageTaken script - one file per defending type).
    private val fairyJs = """
        {
            damageTaken: {
                Bug: 0,
                Dark: 2,
                Dragon: 3,
                Electric: 0,
                Fairy: 0,
                Fighting: 2,
                Fire: 0,
                Flying: 0,
                Ghost: 0,
                Grass: 0,
                Ground: 0,
                Ice: 0,
                Normal: 0,
                Poison: 1,
                Psychic: 0,
                Rock: 0,
                Steel: 1,
                Stellar: 0,
                Water: 0
            }
        }
    """.trimIndent()

    @Test
    fun parsesDamageTakenCodesIntoMultipliers() {
        val overrides = JarDataCache.parseDamageTakenBlock(fairyJs)
        assertEquals(0.5f, overrides["dark"])   // code 2: not very effective
        assertEquals(0f, overrides["dragon"])   // code 3: immune
        assertEquals(2f, overrides["poison"])   // code 1: super effective
        assertEquals(2f, overrides["steel"])    // Lazuli's own deviation from vanilla (normally 1x)
        assertEquals(1f, overrides["bug"])      // code 0: normal
    }

    @Test
    fun typeChartPrefersOverrideThenFallsBackToVanilla() {
        TypeChart.applyOverrides(mapOf("fairy" to JarDataCache.parseDamageTakenBlock(fairyJs)))
        try {
            assertEquals(2f, TypeChart.effectiveness("steel", "fairy")) // overridden
            assertEquals(2f, TypeChart.effectiveness("fighting", "normal")) // untouched vanilla entry
        } finally {
            TypeChart.applyOverrides(emptyMap())
        }
    }

    // A second pack's own take on Fairy - deliberately different from Lazuli's (Steel neutral, not 2x)
    // to make a same-type collision from two sources unambiguous to detect.
    private val otherFairyJs = """
        {
            damageTaken: {
                Steel: 0,
                Dragon: 3
            }
        }
    """.trimIndent()

    @Test
    fun laterSourceWinsOnCollisionAndIsRecorded() {
        val result = mutableMapOf<String, Map<String, Float>>()
        val sourceOf = mutableMapOf<String, String>()

        JarDataCache.applyTypeChartEntry(result, sourceOf, "fairy", fairyJs, "jar:mega_showdown")
        assertEquals(2f, result["fairy"]?.get("steel"))
        assertEquals("jar:mega_showdown", sourceOf["fairy"])

        // A later-scanned source redefines the same defending type - it should win outright, not merge.
        JarDataCache.applyTypeChartEntry(result, sourceOf, "fairy", otherFairyJs, "datapack:projectlazuli")
        assertEquals(1f, result["fairy"]?.get("steel")) // code 0: neutral, unlike Lazuli's 2x
        assertEquals(2, result["fairy"]?.size) // fully replaced (otherFairyJs's 2 entries), not merged with the first source's
        assertEquals("datapack:projectlazuli", sourceOf["fairy"])
    }

    @Test
    fun distinctDefendingTypesDoNotCollide() {
        val result = mutableMapOf<String, Map<String, Float>>()
        val sourceOf = mutableMapOf<String, String>()

        JarDataCache.applyTypeChartEntry(result, sourceOf, "fairy", fairyJs, "jar:mega_showdown")
        JarDataCache.applyTypeChartEntry(result, sourceOf, "dragon", otherFairyJs, "jar:mega_showdown")

        assertEquals(2, result.size)
    }
}
