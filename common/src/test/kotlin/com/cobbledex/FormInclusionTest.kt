package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The forms here are the real ones a server's players asked about, or were wrongly listed. */
class FormInclusionTest {

    private fun distinct(name: String, aspects: List<String>, labels: List<String> = emptyList()) =
        FormInclusion.isDistinctForm(name, aspects, labels)

    @Test
    fun formsThatShareTheirBasesStatsButHaveTheirOwnAspectAreListed() {
        assertTrue(distinct("Sunshine", listOf("sunshine-form"), listOf("gen5")))
        assertTrue(distinct("Hangry", listOf("hangry-mode"), listOf("gen8")))
    }

    @Test
    fun costumeFormsAreHiddenWhateverTheirNumbersAre() {
        assertTrue(!distinct("Cafe-Costume", listOf("cafe-costume")))
        assertFalse(distinct("costume", listOf("costume")))
        assertFalse(distinct("costume2", listOf("costume2")))
        assertFalse(distinct("costume3", listOf("costume3")))
    }

    @Test
    fun aCostumeWornOverAMegaStaysHidden() {
        assertFalse(distinct("Mega-Chef-Costume", listOf("chef-costume", "mega")))
    }

    @Test
    fun aCopyOfTheBaseLookWithNoAspectIsNotAForm() {
        assertFalse(distinct("Normal", emptyList()))
        assertFalse(distinct("", listOf("anything")))
    }

    @Test
    fun aSignificantLabelIsEnoughEvenWithoutAnAspect() {
        assertTrue(distinct("Gmax", emptyList(), listOf("gmax")))
        assertTrue(distinct("Mega", emptyList(), listOf("mega")))
    }

    @Test
    fun anAspectNamedLikeACostumeButNotOneIsStillAForm() {
        assertTrue(distinct("Costumed", listOf("costumed")))
    }
}
