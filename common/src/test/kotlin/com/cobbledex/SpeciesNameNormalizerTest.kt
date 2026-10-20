package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertEquals

class SpeciesNameNormalizerTest {

    @Test
    fun typeNullMatchesItsSpawnFileSpelling() {
        // Cobblemon's display name has a colon; spawn files and the species id say "typenull".
        assertEquals("typenull", SpeciesNameNormalizer.normalize("Type: Null"))
        assertEquals("typenull", SpeciesNameNormalizer.normalize("type: null"))
        assertEquals("typenull", SpeciesNameNormalizer.normalize("typenull"))
        assertEquals("typenull", SpeciesNameNormalizer.normalize("cobblemon:typenull"))
    }

    @Test
    fun namespacePrefixesAreStillStripped() {
        assertEquals("charizard", SpeciesNameNormalizer.normalize("cobblemon:charizard"))
        assertEquals("fomantis", SpeciesNameNormalizer.normalize("lasers:Fomantis"))
    }

    @Test
    fun existingDisplayNameMappingsAreUnchanged() {
        assertEquals("mrmime", SpeciesNameNormalizer.normalize("Mr. Mime"))
        assertEquals("farfetchd", SpeciesNameNormalizer.normalize("Farfetch'd"))
    }
}
