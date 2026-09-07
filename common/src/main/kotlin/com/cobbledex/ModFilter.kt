package com.cobbledex

import com.cobbledex.platform.PlatformHelper

/**
 * Cobblemon keeps every spawn entry in the world pool even when the biome / structure / block it
 * needs comes from a mod that isn't installed - the condition just never matches. We hide those so a
 * page doesn't advertise Aether / Bumblezone / Twilight Forest spawns on a pack without them.
 */
object ModFilter {

    private val ALWAYS_PRESENT = setOf(
        "minecraft", "cobblemon", "c", "forge", "neoforge", "fabric", "common",
    )

    private val cache = HashMap<String, Boolean>()

    fun modPresent(namespace: String): Boolean {
        if (namespace.isBlank() || namespace in ALWAYS_PRESENT) return true
        return cache.getOrPut(namespace) {
            try { PlatformHelper.isModLoaded(namespace) } catch (_: Throwable) { true }
        }
    }

    /** A registry id / `#tag` that belongs to a mod that isn't loaded. */
    fun idMissing(id: String): Boolean {
        val ns = id.removePrefix("#").substringBefore(':', missingDelimiterValue = "")
        return !modPresent(ns)
    }

    fun anyMissing(ids: Iterable<String>): Boolean = ids.any { idMissing(it) }

    fun spawnReferencesMissingMod(s: SpawnInfo): Boolean {
        if (anyMissing(s.biomes) || anyMissing(s.structures) || anyMissing(s.dimensions) ||
            anyMissing(s.neededNearbyBlocks) || anyMissing(s.neededBaseBlocks)
        ) return true
        s.anticondition?.let {
            if (anyMissing(it.biomes) || anyMissing(it.structures) || anyMissing(it.dimensions)) return true
        }
        return false
    }
}
