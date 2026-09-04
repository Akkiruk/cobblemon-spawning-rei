package com.cobbledex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Locks the requirement vocabulary that the runtime reader and the local-file reader share.
 *
 * Both readers name requirements through [EvolutionRequirementSpec]; these assertions fail if a
 * variant is added without a name, or if an alias stops resolving — the drift that previously made
 * a requirement render differently in singleplayer than on a server.
 */
class EvolutionRequirementSpecTest {

    @Test
    fun everyVariantResolvesToItself() {
        for (variant in EvolutionRequirementSpec.VARIANTS) {
            assertEquals(variant, EvolutionRequirementSpec.canonicalise(variant), "variant $variant")
            assertNotNull(EvolutionRequirementSpec.forVariant(variant), "variant $variant")
        }
    }

    @Test
    fun cobblemonRuntimeSpellingsCanonicaliseToTheJsonSchemaNames() {
        // Cobblemon's runtime class names and its JSON schema disagree on these.
        assertEquals("move_set", EvolutionRequirementSpec.canonicalise("has_move"))
        assertEquals("move_type", EvolutionRequirementSpec.canonicalise("has_move_type"))
        assertEquals("pokemon_properties", EvolutionRequirementSpec.canonicalise("properties"))
    }

    @Test
    fun unknownVariantsPassThroughUnchanged() {
        assertEquals("some_future_requirement", EvolutionRequirementSpec.canonicalise("some_future_requirement"))
        assertNull(EvolutionRequirementSpec.forVariant("some_future_requirement"))
    }

    @Test
    fun variantNamesAreUniqueAndAliasesDoNotCollide() {
        val names = EvolutionRequirementSpec.VARIANTS
        assertEquals(names.size, names.toSet().size, "duplicate canonical variant names")

        val allNames = EvolutionRequirementSpec.ALL.flatMap { listOf(it.variant) + it.jsonAliases }
        assertEquals(allNames.size, allNames.toSet().size, "an alias collides with another name")
    }

    @Test
    fun everyVariantDeclaresFieldsExceptTheWrapper() {
        for (spec in EvolutionRequirementSpec.ALL) {
            if (spec.variant == EvolutionRequirementSpec.ANY) continue
            assertTrue(spec.fields.isNotEmpty(), "${spec.variant} declares no fields")
            val keys = spec.fields.map { it.key }
            assertEquals(keys.size, keys.toSet().size, "${spec.variant} repeats a field key")
        }
    }
}
