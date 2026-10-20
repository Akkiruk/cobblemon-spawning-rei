package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The collisions here are the real ones found in Cobblemon 1.7.3 plus a server's add-on packs: each
 * pair used to share one key, so the second form silently overwrote the first.
 */
class FormKeysTest {

    private fun key(base: String, name: String, aspects: List<String>, labels: List<String> = emptyList()) =
        FormKeys.build(base, name, labels, aspects)

    @Test
    fun aPlainRegionalFormKeepsTheBareRegionalKeyOtherDataIsFiledUnder() {
        assertEquals("marowakalolan", key("marowak", "Alola", listOf("alolan")))
        assertEquals("farfetchdgalarian", key("farfetch'd", "Galar", emptyList(), listOf("galarian_form")))
    }

    @Test
    fun aRegionalFormThatIsAlsoAMegaIsItsOwnForm() {
        assertNotEquals(
            key("marowak", "Alola", listOf("alolan")),
            key("marowak", "Alola-Mega", listOf("mega", "alolan")),
        )
        assertNotEquals(
            key("typhlosion", "Hisui", listOf("hisuian")),
            key("typhlosion", "Hisui-Mega", listOf("hisuian", "mega")),
        )
        assertNotEquals(
            key("zoroark", "Hisui", listOf("hisuian")),
            key("zoroark", "Hisui-Mega", listOf("mega", "hisuian")),
        )
    }

    @Test
    fun aRegionalFormWithAStateAspectStillOwnsTheBareKeyWhenItIsTheDefaultOne() {
        // Galarian Darmanitan's standard mode carries a "standard-mode" aspect; Zen mode must not take its key.
        val standard = key("darmanitan", "Galar", listOf("standard-mode", "galarian"))
        val zen = key("darmanitan", "Galar-Zen", listOf("galarian", "zen-mode"))
        assertEquals("darmanitangalarian", standard)
        assertNotEquals(standard, zen)
    }

    @Test
    fun taurosPaldeanBreedsAreThreeSeparateForms() {
        val keys = setOf(
            key("tauros", "Paldea-Combat", listOf("paldean")),
            key("tauros", "Paldea-Blaze", listOf("blaze-breed", "paldean")),
            key("tauros", "Paldea-Aqua", listOf("paldean", "aqua-breed")),
        )
        assertEquals(3, keys.size)
    }

    @Test
    fun nonRegionalFormsAreUnderscoreJoined() {
        assertEquals("zygarde_10", key("zygarde", "10%", listOf("10-percent")))
        assertEquals("zygarde_10c", key("zygarde", "10%-C", listOf("power-construct", "10-percent")))
        assertEquals("charizard_megax", key("charizard", "Mega-X", listOf("mega_x")))
    }

    @Test
    fun symbolOnlyFormNamesDoNotCollapseToOneKey() {
        val exclamation = key("unown", "!", listOf("character-!"))
        val question = key("unown", "?", listOf("character-?"))
        assertNotEquals(exclamation, question)
        assertEquals("unown_exclamation", exclamation)
        assertEquals("unown_question", question)
    }

    @Test
    fun aNamelessSymbolFormStillGetsAUsableKey() {
        assertEquals("unown_form", key("unown", "#", listOf("character-#")))
    }

    @Test
    fun regionBiasLooksAndBasculinsWhiteStripedKeepTheirOriginalBareKeys() {
        // 15 Cobblemon species have a "<region>-bias" form, and Basculin's White-Striped is a Hisuian form with an
        // aspect of its own. Their keys must not change, or existing favorites and links to them break.
        assertEquals("cubonealolan", key("cubone", "Alola-Bias", listOf("region-bias-alola"), listOf("alolan_form")))
        assertEquals("cyndaquilhisuian", key("cyndaquil", "Hisui-Bias", listOf("region-bias-hisui"), listOf("hisuian_form")))
        assertEquals("koffinggalarian", key("koffing", "Galar-Bias", listOf("region-bias-galar"), listOf("galarian_form")))
        assertEquals("basculinhisuian", key("basculin", "White-Striped", listOf("white-striped"), listOf("hisuian_form")))
    }
}
