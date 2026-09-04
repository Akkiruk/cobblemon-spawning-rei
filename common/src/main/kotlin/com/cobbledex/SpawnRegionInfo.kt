package com.cobbledex

/**
 * A named spawn region contributed by the optional CobbleRegions mod.
 *
 * Read client-side through [CobbleRegionsIntegration]'s reflective API call — CobbleRegions
 * exposes this to the client itself, so CobbleDex needs no packet of its own for it.
 */
data class SpawnRegionInfo(
    val id: String,
    val displayName: String,
)
