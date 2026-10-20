package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SyncedDisplayTest {

    @Test
    fun aSyncedRequirementShowsTheTextCobblemonAlreadyRendered() {
        val requirement = EvolutionRequirement("display", mapOf("text" to "Level 30 or higher"))
        assertEquals("Level 30 or higher", requirement.displayText)
    }

    @Test
    fun aFlagFeatureVariantIsNamedByItsKeyNotByTrue() {
        assertEquals(titleCase("candle-pot"), formatAspect("candle-pot=true"))
        assertNotEquals("True", formatAspect("candle-pot=true"))
    }

    @Test
    fun aValuedFeatureVariantIsStillNamedByItsValue() {
        assertEquals(titleCase("ocean"), formatAspect("vivillon_wings=ocean"))
        assertEquals(titleCase("sunshine"), formatAspect("blossom_form=sunshine"))
    }
}
