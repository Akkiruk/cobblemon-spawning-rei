package com.cobbledex

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MarkParseTest {

    // Real shape from Cobblemon 1.8.0 data/cobblemon/marks/mark_alpha.json
    private val alphaJson = """
        {
          "name": "cobblemon.mark.mark_alpha",
          "title": "cobblemon.mark.mark_alpha.title",
          "titleColor": "CE3A49",
          "description": "cobblemon.mark.mark_alpha.desc",
          "texture": "cobblemon:textures/gui/mark/mark_alpha.png",
          "indexNumber": 107,
          "aspects": ["alpha_eyes"]
        }
    """.trimIndent()

    @Test
    fun parsesMarkJson() {
        val mark = JarDataCache.parseMarkObject(JsonParser.parseString(alphaJson).asJsonObject)!!
        assertEquals("cobblemon:mark_alpha", mark.id)
        assertEquals("cobblemon.mark.mark_alpha", mark.nameKey)
        assertEquals("cobblemon.mark.mark_alpha.desc", mark.descriptionKey)
        assertEquals("cobblemon.mark.mark_alpha.title", mark.titleKey)
        assertEquals("CE3A49", mark.titleColor)
        assertEquals(107, mark.indexNumber)
        assertNull(mark.rarityPercent) // chance defaults to 0
    }

    @Test
    fun chanceMarkExposesRarity() {
        val json = """{ "name": "cobblemon.mark.mark_rowdy", "description": "x", "chance": 0.05 }"""
        val mark = JarDataCache.parseMarkObject(JsonParser.parseString(json).asJsonObject)!!
        assertEquals(5.0, mark.rarityPercent!!, 0.001)
    }
}
