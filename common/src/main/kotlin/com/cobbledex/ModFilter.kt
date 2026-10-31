package com.cobbledex

import com.cobbledex.platform.PlatformHelper
import net.minecraft.client.Minecraft
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey

/**
 * Cobblemon keeps every spawn entry in the world pool even when the biome / structure / block it
 * needs comes from something that isn't installed - the condition just never matches. We hide those
 * so a page doesn't advertise Aether / Bumblezone / Twilight Forest spawns on a pack without them.
 *
 * "Installed" means the id's namespace really exists, which is not the same as a mod with that id:
 * datapacks add namespaces with no mod behind them (the datapack builds of Terralith and Nullscape,
 * say), and a mod can ship content under another name (`cobblemon-additions` uses `bca`). On a companion server only the server's answer counts, whatever this client has.
 */
object ModFilter {

    private val ALWAYS_PRESENT = setOf(
        "minecraft", "cobblemon", "c", "forge", "neoforge", "fabric", "common",
    )

    @Volatile
    private var known: Set<String> = emptySet()

    /** Re-reads what exists. Called before each spawn load, since a world can add its own packs. */
    fun refresh() {
        ServerDataView.namespaces()?.let {
            known = it
            return
        }
        known = buildSet {
            try { addAll(PlatformHelper.getLoadedMods().keys) } catch (_: Throwable) {}
            try { addAll(JarDataCache.localDataNamespaces()) } catch (_: Throwable) {}
            try { addRegistry(BuiltInRegistries.BLOCK) } catch (_: Throwable) {}
            try { worldRegistries()?.let { addWorldRegistries(it) } } catch (_: Throwable) {}
        }
    }

    /** Hosting a world exposes every registry; a client on a server is only sent some, biomes among them. */
    private fun worldRegistries(): RegistryAccess? =
        LocalDataSource.localServer()?.registryAccess() ?: Minecraft.getInstance().connection?.registryAccess()

    private fun MutableSet<String>.addWorldRegistries(access: RegistryAccess) {
        addRegistryIfPresent(access, Registries.BIOME)
        addRegistryIfPresent(access, Registries.STRUCTURE)
        addRegistryIfPresent(access, Registries.DIMENSION_TYPE)
        addRegistryIfPresent(access, Registries.LEVEL_STEM)
    }

    private fun <T> MutableSet<String>.addRegistryIfPresent(access: RegistryAccess, key: ResourceKey<out Registry<T>>) {
        access.registry(key).ifPresent { addRegistry(it) }
    }

    private fun MutableSet<String>.addRegistry(registry: Registry<*>) {
        registry.keySet().forEach { add(it.namespace) }
        registry.tagNames.forEach { add(it.location.namespace) }
    }

    /** A registry id / `#tag` from something that isn't installed. */
    fun idMissing(id: String): Boolean {
        val ns = id.removePrefix("#").substringBefore(':', missingDelimiterValue = "")
        return ns.isNotBlank() && ns !in ALWAYS_PRESENT && ns !in known
    }

    /**
     * [s] without anything that isn't installed, or null when it can't happen at all. Biome,
     * structure, dimension and block lists are "any of", so a spawn only dies once one of them has
     * nothing left. An exclusion can never stop a spawn, so one naming something missing is just
     * dropped, and so is a weight bonus that could only apply in a missing biome.
     */
    fun withoutMissingMods(s: SpawnInfo): SpawnInfo? = withoutMissing(s, ::idMissing)

    internal fun withoutMissing(s: SpawnInfo, missing: (String) -> Boolean): SpawnInfo? {
        fun keep(ids: List<String>) = ids.filterNot(missing)
        val biomes = keep(s.biomes)
        val structures = keep(s.structures)
        val dimensions = keep(s.dimensions)
        val nearbyBlocks = keep(s.neededNearbyBlocks)
        val baseBlocks = keep(s.neededBaseBlocks)
        val required = listOf(
            s.biomes to biomes, s.structures to structures, s.dimensions to dimensions,
            s.neededNearbyBlocks to nearbyBlocks, s.neededBaseBlocks to baseBlocks,
        )
        if (required.any { (before, after) -> before.isNotEmpty() && after.isEmpty() }) return null

        val anticondition = s.anticondition?.let { anti ->
            anti.copy(
                biomes = keep(anti.biomes), structures = keep(anti.structures), dimensions = keep(anti.dimensions),
                neededNearbyBlocks = keep(anti.neededNearbyBlocks), neededBaseBlocks = keep(anti.neededBaseBlocks),
            ).takeUnless { it.isEmpty }
        }
        return s.copy(
            biomes = biomes, structures = structures, dimensions = dimensions,
            neededNearbyBlocks = nearbyBlocks, neededBaseBlocks = baseBlocks, anticondition = anticondition,
            weightMultipliers = s.weightMultipliers.mapNotNull { withoutMissing(it, ::keep) },
        )
    }

    /** A bonus needing only missing things can't apply, and an exclusion of them says nothing. */
    private fun withoutMissing(multiplier: WeightMultiplier, keep: (List<String>) -> List<String>): WeightMultiplier? {
        val parts = multiplier.conditionParts.mapNotNull { part ->
            if (part.ids.isEmpty()) return@mapNotNull part
            val ids = keep(part.ids)
            when {
                ids.isNotEmpty() -> part.copy(ids = ids)
                part.type.startsWith("not_") -> null
                else -> return null
            }
        }
        return multiplier.copy(conditionParts = parts.ifEmpty { WeightConditionParts.ALWAYS })
    }
}
