package com.cobbledex

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools

/**
 * Tells CobbleDex when Cobblemon's client-side data has changed, so the index rebuilds off a real
 * signal instead of a blind retry loop.
 *
 * Cobblemon gives us no client-side "data synchronised" callback: `PokemonSpecies.observable` only
 * fires on the datapack-load path (server / singleplayer), *not* on the network path — its
 * `reload(Map)` used by `SpeciesRegistrySyncPacket` swaps the registry maps and emits nothing. So
 * the only reliable signal is the registry contents themselves. This takes a cheap fingerprint of
 * them and reports transitions.
 *
 * Both reads are O(registry size) over ~1-2k entries and only run on the sample interval, which is
 * far cheaper than the rebuild it guards.
 */
object CobblemonDataSignal {

    /** Sentinel for "nothing sampled yet" — distinct from a real all-zero fingerprint. */
    private const val NO_SAMPLE = -1L

    @Volatile
    private var lastFingerprint: Long = NO_SAMPLE

    /**
     * Cobblemon's client registries as one comparable value.
     *
     * Species count moves when `species_sync` lands (and on any later Cobblemon reload); spawn-pool
     * size moves when a singleplayer/LAN world finishes loading its spawn files. On a dedicated
     * server the pool stays at 0 forever, which is correct — it is never synced.
     *
     * Species *count* alone misses a datapack change that alters an existing species without
     * adding or removing one — the commonest example being a form added to or removed from a
     * species that's already implemented. So each species' name and form count are folded into a
     * running sum too: still O(species count) with only property reads (no per-form/per-ability
     * traversal), but sensitive to far more than "did the total change". A same-species,
     * same-form-count stat-only edit still isn't caught by this — genuinely detecting that would
     * mean hashing the full data CobbleDex reads, which costs about what the rebuild itself does
     * and defeats the point of sampling cheaply once a second.
     */
    private fun fingerprint(): Long {
        val species = try { PokemonSpecies.implemented } catch (_: Exception) { return 0L }
        var structural = 0L
        var speciesCount = 0
        for (sp in species) {
            try {
                speciesCount++
                val forms = try { sp.forms.size } catch (_: Exception) { 0 }
                structural = structural * 31 + sp.name.hashCode() * 31 + forms
            } catch (_: Exception) {}
        }
        val spawnCount = try { CobblemonSpawnPools.WORLD_SPAWN_POOL.details.size } catch (_: Exception) { 0 }
        return structural + speciesCount.toLong() * 1_000_003L + spawnCount.toLong()
    }

    /** True when Cobblemon's data differs from the last sample. Records the new sample. */
    fun consumeChange(): Boolean {
        val current = fingerprint()
        if (current == lastFingerprint) return false
        val previous = lastFingerprint
        lastFingerprint = current
        DebugLog.info(
            if (previous == NO_SAMPLE) "Cobblemon data available (fingerprint $current)"
            else "Cobblemon data changed ($previous -> $current)"
        )
        return true
    }

    /** Forget the last sample so the next [consumeChange] reports a change. */
    fun reset() {
        lastFingerprint = NO_SAMPLE
    }
}
