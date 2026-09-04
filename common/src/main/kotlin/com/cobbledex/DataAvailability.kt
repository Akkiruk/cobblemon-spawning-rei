package com.cobbledex

import net.minecraft.client.Minecraft

/**
 * Answers "can this page be trusted on this session?".
 *
 * Data read out of Cobblemon's registries always matches the world you are in, because Cobblemon
 * synced it. Data read from this client's files matches only when the client carries the same packs
 * as the server — always true in singleplayer, usually true on a modpack server, not guaranteed on
 * a server running packs of its own.
 *
 * Rather than let that ambiguity silently corrupt a page, categories consult this and say so.
 */
object DataAvailability {

    enum class Confidence {
        /** Straight from Cobblemon, or a table compiled into CobbleDex. Matches this world. */
        VERIFIED,

        /** Read from this client's files while connected to a server that may not share them. */
        LOCAL_ONLY,

        /** No source supplied it. */
        MISSING,
    }

    /** True in singleplayer or on a LAN world this client is hosting — local files *are* the world. */
    private fun isLocalWorld(): Boolean = try {
        Minecraft.getInstance().hasSingleplayerServer()
    } catch (_: Exception) {
        false
    }

    fun confidenceOf(tier: DataSourceTier): Confidence = when (tier) {
        DataSourceTier.COBBLEMON, DataSourceTier.BUILT_IN -> Confidence.VERIFIED
        DataSourceTier.LOCAL_FILES -> if (isLocalWorld()) Confidence.VERIFIED else Confidence.LOCAL_ONLY
        DataSourceTier.UNAVAILABLE -> Confidence.MISSING
    }

    /**
     * A short caveat to show on a page whose data came only from local files while connected to a
     * server, or null when there is nothing to warn about.
     */
    fun caveatFor(tier: DataSourceTier): String? = when (confidenceOf(tier)) {
        Confidence.LOCAL_ONLY -> tr("cobbledex-rei-emi-jei.source.local_only")
        else -> null
    }

    /** One-line description of where a category's data came from, for diagnostics. */
    fun describe(label: String, tier: DataSourceTier): String {
        val mark = when (confidenceOf(tier)) {
            Confidence.VERIFIED -> "§a✔"
            Confidence.LOCAL_ONLY -> "§e▲"
            Confidence.MISSING -> "§c✖"
        }
        return "$mark §7$label: §f${tier.displayName}"
    }
}
