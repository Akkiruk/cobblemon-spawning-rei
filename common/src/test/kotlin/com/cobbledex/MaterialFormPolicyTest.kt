package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MaterialFormPolicyTest {
    @Test
    fun textureOnlyFormDoesNotSurface() {
        val base = snapshot("vivillon")
        val form = snapshot("vivillonfancy", baseSpeciesName = "vivillon", formAspects = setOf("fancy"))

        val decision = MaterialFormPolicy.decide(base, form)

        assertFalse(decision.surface)
    }

    @Test
    fun typeDifferenceSurfacesForm() {
        val base = snapshot("wooper", primaryType = "water", secondaryType = "ground")
        val form = snapshot(
            "wooperpaldean",
            primaryType = "poison",
            secondaryType = "ground",
            baseSpeciesName = "wooper",
            formAspects = setOf("paldean")
        )

        val decision = MaterialFormPolicy.decide(base, form)

        assertTrue(decision.surface)
        assertTrue("typing" in decision.reasons)
    }

    @Test
    fun formSpecificSpawnDataSurfacesForm() {
        val base = snapshot("deerling")
        val form = snapshot("deerlingsummer", baseSpeciesName = "deerling", formAspects = setOf("summer"))

        val decision = MaterialFormPolicy.decide(
            base,
            form,
            MaterialFormPolicy.DomainSignals(hasFormSpecificSpawns = true)
        )

        assertTrue(decision.surface)
        assertTrue("form-specific spawn data" in decision.reasons)
    }

    @Test
    fun missingBaseDataKeepsFormVisibleForSafety() {
        val form = snapshot("unknownform", baseSpeciesName = "unknown", formAspects = setOf("special"))

        val decision = MaterialFormPolicy.decide(null, form)

        assertTrue(decision.surface)
    }

    private fun snapshot(
        name: String,
        primaryType: String = "normal",
        secondaryType: String? = null,
        baseSpeciesName: String? = null,
        formAspects: Set<String> = emptySet()
    ) = MaterialFormPolicy.SpeciesSnapshot(
        name = name,
        primaryType = primaryType,
        secondaryType = secondaryType,
        catchRate = 45,
        weight = 10f,
        height = 1f,
        baseStats = mapOf("hp" to 45, "attack" to 49, "defence" to 49),
        abilities = setOf("runaway"),
        baseSpeciesName = baseSpeciesName,
        formAspects = formAspects
    )
}