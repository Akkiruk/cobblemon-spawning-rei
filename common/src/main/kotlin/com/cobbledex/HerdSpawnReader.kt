package com.cobbledex

import com.cobblemon.mod.common.api.pokemon.PokemonProperties

/**
 * Reads `pokemon-herd` spawn details (Cobblemon 1.8.0+) without a compile-time reference to
 * `PokemonHerdSpawnDetail` or its `Herdable` inner class, so the same jar still loads on 1.7.x where
 * those classes do not exist. Everything here is reflection over method names that are stable within
 * 1.8.x; any shape mismatch is swallowed and the detail is simply skipped.
 *
 * The caller ([SpawnDataLoader]) still owns condition / anticondition / weight-multiplier parsing —
 * those live on the shared [com.cobblemon.mod.common.api.spawning.detail.SpawnDetail] base and are
 * version-stable. This reader only pulls the herd-specific shape.
 */
object HerdSpawnReader {

    private const val HERD_CLASS = "com.cobblemon.mod.common.api.spawning.detail.PokemonHerdSpawnDetail"

    data class HerdMember(
        val species: String,
        val formAspects: String,
        val role: HerdRole,
        val heldItemId: String?,
        val ownLevelRange: String?,
    )

    data class HerdRead(
        val maxHerdSize: Int,
        val detailLevelRange: String,
        val members: List<HerdMember>,
    )

    fun isHerdDetail(detail: Any): Boolean =
        generateSequence<Class<*>>(detail.javaClass) { it.superclass }.any { it.name == HERD_CLASS }

    fun read(detail: Any): HerdRead? {
        return try {
            val cls = detail.javaClass
            val maxHerdSize = (cls.getMethod("getMaxHerdSize").invoke(detail) as? Number)?.toInt() ?: 10
            val detailLevelRange = readIntRange(cls.getMethod("getLevelRange").invoke(detail)) ?: "1-100"

            @Suppress("UNCHECKED_CAST")
            val herdables = cls.getMethod("getHerdablePokemon").invoke(detail) as? List<Any> ?: return null

            val members = herdables.mapNotNull { herdable ->
                try {
                    val hc = herdable.javaClass
                    val props = hc.getMethod("getPokemon").invoke(herdable) as? PokemonProperties ?: return@mapNotNull null
                    val rawSpecies = props.species?.lowercase() ?: return@mapNotNull null
                    val species = rawSpecies.substringAfter(':')
                    val isLeader = hc.getMethod("isLeader").invoke(herdable) as? Boolean ?: false
                    val isFollower = hc.getMethod("isFollower").invoke(herdable) as? Boolean ?: true
                    val role = when {
                        isLeader && !isFollower -> HerdRole.LEADER
                        isLeader -> HerdRole.LEADER
                        isFollower -> HerdRole.FOLLOWER
                        else -> HerdRole.ANY
                    }
                    val heldItem = runCatching { hc.getMethod("getHeldItem").invoke(herdable)?.toString() }.getOrNull()
                    val ownRange = runCatching { readIntRange(hc.getMethod("getHerdLevelRange").invoke(herdable)) }.getOrNull()

                    HerdMember(
                        species = species,
                        formAspects = herdFormAspects(props),
                        role = role,
                        heldItemId = heldItem,
                        ownLevelRange = ownRange,
                    )
                } catch (_: Throwable) {
                    null
                }
            }

            if (members.isEmpty()) null else HerdRead(maxHerdSize, detailLevelRange, members)
        } catch (_: Throwable) {
            null
        }
    }

    private fun herdFormAspects(props: PokemonProperties): String {
        val form = try { props.form } catch (_: Throwable) { null }
        val aspects = try { props.aspects?.joinToString(" ") } catch (_: Throwable) { null }
        return when {
            !form.isNullOrBlank() && !form.equals("Normal", ignoreCase = true) -> form
            !aspects.isNullOrBlank() -> aspects
            else -> ""
        }
    }

    private fun readIntRange(range: Any?): String? {
        range ?: return null
        return try {
            val first = (range.javaClass.getMethod("getFirst").invoke(range) as? Number)?.toInt()
            val last = (range.javaClass.getMethod("getLast").invoke(range) as? Number)?.toInt()
            if (first != null && last != null) "$first-$last" else null
        } catch (_: Throwable) {
            null
        }
    }
}
