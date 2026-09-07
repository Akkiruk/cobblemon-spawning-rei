package com.cobbledex

/**
 * One herd as a browsable unit (Cobblemon 1.8.0+), derived by [SpawnDataIndex] from the per-member
 * herd [SpawnInfo]s. The biome-variant copies of a herd (same roster, different biome) are merged
 * into one of these with their biomes unioned.
 *
 * [conditions] is a representative [SpawnInfo] carrying only the shared world conditions (biomes
 * unioned, everything else from the first variant) so the page can reuse the normal condition
 * formatters; its `pokemon`/`herd` fields are not meaningful here.
 */
data class HerdInfo(
    val members: List<HerdMemberRef>,
    val maxHerdSize: Int,
    val bucket: String,
    val conditions: SpawnInfo,
) {
    val hasAlpha: Boolean get() = members.any { it.isAlpha }

    /** The species that heads the herd, or the heaviest-weighted member for a leaderless herd. */
    val leader: HerdMemberRef
        get() = members.firstOrNull { it.role == HerdRole.LEADER }
            ?: members.maxByOrNull { it.weight }
            ?: members.first()

    /** True when there is a real designated leader (alpha herds); false for plain family herds. */
    val hasDesignatedLeader: Boolean get() = members.any { it.role == HerdRole.LEADER }

    val followers: List<HerdMemberRef>
        get() = (if (hasDesignatedLeader) members.filter { it.role != HerdRole.LEADER } else members)
            .sortedByDescending { it.weight }

    val id: String get() = members.map { it.species }.sorted().joinToString("-") + "-x$maxHerdSize"

    fun titleKey(): String =
        if (hasAlpha) "cobbledex-rei-emi-jei.herd.title_alpha" else "cobbledex-rei-emi-jei.herd.title"

    fun displayName(): String = tr(titleKey(), formatSpeciesName(leader.species))

    companion object {
        /** All member [SpawnInfo]s that share one roster key (across every member species and biome). */
        fun from(memberSpawns: List<SpawnInfo>): HerdInfo? {
            val ctx = memberSpawns.firstNotNullOfOrNull { it.herd } ?: return null
            if (ctx.members.isEmpty()) return null
            val biomes = memberSpawns.flatMap { it.biomes }.distinct()
            val rep = memberSpawns.first().copy(biomes = biomes)
            return HerdInfo(
                members = ctx.members,
                maxHerdSize = ctx.maxHerdSize,
                bucket = rep.bucket,
                conditions = rep,
            )
        }
    }
}
