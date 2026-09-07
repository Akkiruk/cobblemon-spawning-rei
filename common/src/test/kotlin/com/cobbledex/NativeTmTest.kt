package com.cobbledex

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeTmTest {

    // Real shape from Cobblemon 1.8.0 data/cobblemon/tms/acidspray.json
    private val acidSprayJson = """
        {
          "moveName": "acidspray",
          "obtainMethods": [ { "variant": "cobblemon:unlockable" } ],
          "type": "poison",
          "recipe": [
            { "item": "cobblemon:poison_gem", "count": 2 },
            { "item": "cobblemon:pink_mint_leaf", "count": 2 }
          ]
        }
    """.trimIndent()

    @Test
    fun parsesNativeTmJson() {
        val tm = JarDataCache.parseTmObject(JsonParser.parseString(acidSprayJson).asJsonObject)!!
        assertEquals("acidspray", tm.moveName)
        assertEquals("poison", tm.elementalType)
        assertFalse(tm.passivelyObtained)
        assertEquals(2, tm.ingredients.size)
        assertEquals(listOf("cobblemon:poison_gem"), tm.ingredients[0].itemIds)
        assertEquals(2, tm.ingredients[0].count)
    }

    @Test
    fun defaultObtainMethodMeansPassive() {
        val json = """{ "moveName": "tackle", "type": "normal", "obtainMethods": [ { "variant": "cobblemon:default" } ] }"""
        val tm = JarDataCache.parseTmObject(JsonParser.parseString(json).asJsonObject)!!
        assertTrue(tm.passivelyObtained)
        assertTrue(tm.ingredients.isEmpty())
    }

    @Test
    fun tagIngredientsAreKept() {
        val json = """{ "moveName": "x", "type": "fire", "recipe": [ { "tag": "c:gems/fire", "count": 3 } ] }"""
        val tm = JarDataCache.parseTmObject(JsonParser.parseString(json).asJsonObject)!!
        assertEquals("c:gems/fire", tm.ingredients.single().tagId)
        assertTrue(tm.ingredients.single().itemIds.isEmpty())
    }

    @Test
    fun tmItemUtilsRecognisesBothSystems() {
        assertTrue(TmItemUtils.isTmItem("cobblemon:technical_machine"))
        assertTrue(TmItemUtils.isNativeTm("cobblemon:technical_machine"))
        assertTrue(TmItemUtils.isTmItem("tmcraft:tm_flamethrower"))
        assertFalse(TmItemUtils.isNativeTm("tmcraft:tm_flamethrower"))
        assertEquals("flamethrower", TmItemUtils.extractMove("tmcraft:tm_flamethrower"))
        assertNull(TmItemUtils.extractMove("cobblemon:technical_machine"))
    }
}
