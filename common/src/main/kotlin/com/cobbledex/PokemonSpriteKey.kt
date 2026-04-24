package com.cobbledex

import net.minecraft.resources.ResourceLocation

data class PokemonSpriteKey(
    override val species: String,
    override val formAspects: Set<String> = emptySet()
) : PokemonRef {

    val idPath: String
        get() {
            val aspectPart = if (formAspects.isEmpty()) {
                "base"
            } else {
                sanitizePath(formAspects.joinToString("_"))
            }
            val hash = Integer.toHexString((species + "|" + aspectPart).hashCode())
            return "pokemon/${identifier.namespace}/${sanitizePath(identifier.path)}/$aspectPart-$hash"
        }

    val cacheFile: String
        get() = "${idPath}.png"

    companion object {
        fun from(species: String, explicitAspects: Set<String> = emptySet()): PokemonSpriteKey {
            val normalizedSpecies = SpeciesNameNormalizer.normalize(species)
            val decomp = SpeciesNameNormalizer.decomposeFormSpecies(normalizedSpecies)
            val aspects = explicitAspects.ifEmpty {
                decomp.cobblemonAspects.ifEmpty {
                    SpawnDataIndex.getSpeciesInfo(species)?.formAspects ?: emptySet()
                }
            }.map { it.lowercase() }.toSortedSet()
            return PokemonSpriteKey(normalizedSpecies, aspects)
        }

        fun from(ref: PokemonRef): PokemonSpriteKey = from(ref.species, ref.formAspects)
    }
}