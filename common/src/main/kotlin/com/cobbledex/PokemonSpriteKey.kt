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
            val hash = Integer.toHexString((SPRITE_CACHE_VERSION + "|" + species + "|" + aspectPart).hashCode())
            return "pokemon/$SPRITE_CACHE_VERSION/${identifier.namespace}/${sanitizePath(identifier.path)}/$aspectPart-$hash"
        }

    val cacheFile: String
        get() = "${idPath}.png"

    companion object {
        private const val SPRITE_CACHE_VERSION = "v3"

        fun from(species: String, explicitAspects: Set<String> = emptySet()): PokemonSpriteKey {
            val resolved = PokemonIconResolver.resolve(species, explicitAspects)
            return PokemonSpriteKey(resolved.captureSpecies, resolved.captureAspects)
        }

        fun from(ref: PokemonRef): PokemonSpriteKey = from(ref.species, ref.formAspects)
    }
}