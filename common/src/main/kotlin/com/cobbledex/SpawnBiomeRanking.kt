package com.cobbledex

/**
 * "Where does this Pokémon actually show up most" - picks the one biome, among those a spawn
 * lists, where the species has clearly better odds than the rest. Returns nothing when it's close,
 * so the panel only speaks up when it actually knows something.
 *
 * A spawn's share of the roll in a biome is roughly `weight / crowd`, where the crowd is the
 * summed weight of every spawn competing at that spot. [buildCrowd] sums that denominator once
 * across all species - one representative (heaviest) weight per species per spot, matching the
 * merged weight the pages show, so a species that ships duplicate spawn rows never inflates its
 * own competition.
 *
 * Approximations (surfaced in the panel tooltip, not corrected here): a biome **tag**
 * (`#cobblemon:is_temperate`) only competes with other spawns using that exact token, never the
 * concrete biomes it overlaps; per-position gates (needed blocks, Y range, anticonditions, weight
 * multipliers) are ignored.
 *
 * Pure and client-free: the crowd map is built in [DerivedDataBuilder.rebuild] and stored on
 * [CobbleDexDataSnapshot]; the two entry points below read it. All ranking logic lives here so
 * `keyFor` is defined exactly once.
 */
object SpawnBiomeRanking {

    /** The leader must beat the runner-up by this ratio, or the panel says nothing. */
    private const val CLEAR_MARGIN = 1.30

    /**
     * Cobblemon's default spawn-bucket proportions (`best-spawner-config.json`), stable across
     * Cobblemon 1.6-1.8. Only used by [headline] to compare biomes across rarity buckets; within a
     * single spawn every biome shares a bucket so this term cancels.
     */
    private val BUCKET_PROBABILITY = mapOf(
        "common" to 0.885,
        "uncommon" to 0.10,
        "rare" to 0.012,
        "ultra-rare" to 0.003,
    )
    private const val BUCKET_PROBABILITY_FALLBACK = 0.012

    /**
     * The biome (of those this spawn lists) with a clear odds edge, or null when it's too close to
     * call, the spawn has fewer than two rankable biomes, or there's no crowd data.
     */
    fun bestBiomeForWay(spawn: SpawnInfo, crowd: Map<String, Float>): String? {
        if (crowd.isEmpty() || spawn.weight <= 0f) return null
        val shares = biomeShares(spawn, crowd)
        if (shares.size < 2) return null
        return if (shares[0].second >= shares[1].second * CLEAR_MARGIN) shares[0].first else null
    }

    /**
     * Across all of a species' spawn ways, the single best-odds biome overall - weighs each way by
     * its rarity bucket's share of the roll. Only ways that have their own clear winner are
     * considered, so the result is always a biome that [bestBiomeForWay] also flags on some page.
     */
    fun headline(spawns: List<SpawnInfo>, crowd: Map<String, Float>): String? {
        if (crowd.isEmpty()) return null
        var bestScore = 0.0
        var best: String? = null
        for (spawn in spawns) {
            val biome = bestBiomeForWay(spawn, crowd) ?: continue
            val sum = crowd[keyFor(biome, spawn)] ?: continue
            val score = bucketProbability(spawn.bucket) * (spawn.weight / sum).toDouble()
            if (score > bestScore) {
                bestScore = score
                best = biome
            }
        }
        return best
    }

    /** `token -> summed competing weight`. One weight per species per spot (its heaviest). */
    fun buildCrowd(spawnsBySpecies: Map<String, List<SpawnInfo>>): Map<String, Float> {
        if (spawnsBySpecies.isEmpty()) return emptyMap()
        val totals = HashMap<String, Float>()
        val perSpecies = HashMap<String, Float>()
        for (list in spawnsBySpecies.values) {
            perSpecies.clear()
            for (s in list) {
                if (s.weight <= 0f) continue
                for (token in tokensOf(s)) {
                    val k = keyFor(token, s)
                    perSpecies[k] = maxOf(perSpecies[k] ?: 0f, s.weight)
                }
            }
            for ((k, w) in perSpecies) totals[k] = (totals[k] ?: 0f) + w
        }
        return totals
    }

    private fun biomeShares(spawn: SpawnInfo, crowd: Map<String, Float>): List<Pair<String, Double>> =
        tokensOf(spawn).mapNotNull { token ->
            val sum = crowd[keyFor(token, spawn)] ?: return@mapNotNull null
            if (sum <= 0f) null else token to (spawn.weight / sum).toDouble()
        }.sortedByDescending { it.second }

    /** The biome tokens the spawn panel actually shows - concrete ids and climate tags. */
    private fun tokensOf(spawn: SpawnInfo): List<String> =
        SpawnPageModel.splitBiomes(spawn.biomes).climateOrConcrete

    /** `token | bucket | context | time | light` - who competes with whom at one spot. */
    private fun keyFor(token: String, s: SpawnInfo): String = listOf(
        token,
        s.bucket.lowercase(),
        s.context.lowercase(),
        SpawnPageModel.normalizeTime(s.timeRange).name,
        SpawnPageModel.lightBucket(s.minSkyLight, s.maxSkyLight, s.minLight, s.maxLight, s.canSeeSky),
    ).joinToString("|")

    private fun bucketProbability(bucket: String): Double =
        BUCKET_PROBABILITY[bucket.lowercase()] ?: BUCKET_PROBABILITY_FALLBACK
}
