package com.cobbledex

import com.cobblemon.mod.common.api.pokemon.PokemonProperties

/**
 * Reads `pokemon-herd` spawn details (Cobblemon 1.8.0+) without a compile-time reference to
 * `PokemonHerdSpawnDetail` or its `Herdable` inner class, so the same jar still loads on 1.7.x where
 * those classes do not exist. Everything here is reflection over method names that are stable within
 * 1.8.x; any shape mismatch is swallowed and the detail is simply skipped.
 *
 * The caller ([SpawnDataLoader]) still owns condition / anticondition / weight-multiplier parsing -
 * those live on the shared [com.cobblemon.mod.common.api.spawning.detail.SpawnDetail] base and are
 * version-stable. This reader only pulls the herd-specific shape.
 */
object HerdSpawnReader {

    private const val HERD_CLASS = "com.cobblemon.mod.common.api.spawning.detail.PokemonHerdSpawnDetail"

    data class HerdMember(
        val species: String,
        val formAspects: String,
        val role: HerdRole,
        val isAlpha: Boolean,
        val heldItemId: String?,
        val levelRange: String?,
        val maxCount: Int,
        val weight: Float,
    )

    data class HerdRead(
        val maxHerdSize: Int,
        val detailLevelRange: String,
        val members: List<HerdMember>,
    )

    /**
     * One [SpawnInfo] per member of a herd, each a copy of [base] with the member's own details. Both
     * the runtime reader and the file reader end here, so a herd is described the same on a server.
     */
    fun spawnInfos(base: SpawnInfo, members: List<HerdMember>, maxHerdSize: Int): List<Pair<String, SpawnInfo>> {
        // One roster description, shared by every member's SpawnInfo, ordered leader/alpha first then
        // by roll weight so the page can render the formation without re-sorting.
        val memberRefs = members
            .map {
                HerdMemberRef(
                    species = it.species, formAspects = it.formAspects, role = it.role,
                    isAlpha = it.isAlpha, heldItemId = it.heldItemId,
                    levelRange = it.levelRange ?: base.levelRange,
                    maxCount = it.maxCount, weight = it.weight,
                )
            }
            .sortedWith(compareByDescending<HerdMemberRef> { it.role == HerdRole.LEADER }.thenByDescending { it.weight })
        val herdContext = HerdContext(memberRefs, maxHerdSize)

        return members.map { member ->
            member.species to base.copy(
                id = "${base.id.ifBlank { member.species }}#herd:${member.species}",
                pokemon = member.species,
                formAspects = member.formAspects,
                levelRange = member.levelRange ?: base.levelRange,
                herd = herdContext,
            )
        }
    }

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
                    val isLeader = runCatching { hc.getMethod("isLeader").invoke(herdable) as? Boolean }.getOrNull() ?: false
                    val isFollower = runCatching { hc.getMethod("isFollower").invoke(herdable) as? Boolean }.getOrNull() ?: true
                    val role = if (isLeader) HerdRole.LEADER else if (isFollower) HerdRole.FOLLOWER else HerdRole.ANY

                    val aspects = SpawnDataLoader.variantTokens(props)
                    val isAlpha = readIsAlpha(props) || aspects.contains("alpha", ignoreCase = true)
                    val heldItem = runCatching { hc.getMethod("getHeldItem").invoke(herdable)?.toString() }.getOrNull()
                    val ownRange = runCatching { readIntRange(hc.getMethod("getLevelRange").invoke(herdable)) }.getOrNull()
                        ?: runCatching { readIntRange(hc.getMethod("getHerdLevelRange").invoke(herdable)) }.getOrNull()
                    val maxCount = (runCatching { hc.getMethod("getMaxTimes").invoke(herdable) as? Number }.getOrNull())?.toInt() ?: 1
                    val weight = (runCatching { hc.getMethod("getWeight").invoke(herdable) as? Number }.getOrNull())?.toFloat() ?: 1f

                    HerdMember(
                        species = species,
                        formAspects = aspects,
                        role = role,
                        isAlpha = isAlpha,
                        heldItemId = heldItem,
                        levelRange = ownRange,
                        maxCount = maxCount,
                        weight = weight,
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

    /** `PokemonProperties.isAlpha` is a nullable Boolean; its Kotlin getter name varies, so try both. */
    private fun readIsAlpha(props: PokemonProperties): Boolean = try {
        props.javaClass.methods
            .firstOrNull { it.parameterCount == 0 && (it.name == "isAlpha" || it.name == "getIsAlpha" || it.name == "getAlpha") }
            ?.invoke(props) as? Boolean ?: false
    } catch (_: Throwable) {
        false
    }

    private fun readIntRange(range: Any?): String? {
        range ?: return null
        val first = Reflect.call<Number>(range, "getFirst")?.toInt()
        val last = Reflect.call<Number>(range, "getLast")?.toInt()
        return if (first != null && last != null) "$first-$last" else null
    }
}
