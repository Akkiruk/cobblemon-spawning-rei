package com.cobbledex

/**
 * Where a piece of CobbleDex data came from, best first.
 *
 * The sources on a client are Cobblemon's own synced registries, files a companion server
 * sent, this client's files, and tables compiled into the mod.
 */
enum class DataSourceTier(val displayName: String, val rank: Int) {
    /**
     * Cobblemon's client-side registries - `PokemonSpecies`, `Moves`, `Abilities`, `Fossils`.
     * On a server these are exactly what Cobblemon's own `species_sync` delivered, so they match
     * the server's datapacks; in singleplayer they are the loaded datapacks directly. Always
     * authoritative.
     */
    COBBLEMON("cobblemon", 0),

    /**
     * Files the server itself sent through CobbleDex's companion channel (spawn pools, species files with
     * their tutor moves and evolutions). They are the server's own, so they are as authoritative as Cobblemon's.
     */
    SERVER_DATA("server data", 1),

    /**
     * This client's own files: mod JARs, `datapacks/`, enabled resource packs. Correct in
     * singleplayer and on any modpack where client and server carry the same packs; potentially
     * out of step with a server running packs the client does not have.
     */
    LOCAL_FILES("local files", 2),

    /** Tables compiled into CobbleDex itself (type chart, natures). Always correct. */
    BUILT_IN("built-in", 3),

    /** Nothing supplied this data. */
    UNAVAILABLE("unavailable", 4);
}

object DataSourcePolicy {
    val precedence: List<DataSourceTier> = listOf(
        DataSourceTier.COBBLEMON,
        DataSourceTier.SERVER_DATA,
        DataSourceTier.LOCAL_FILES,
        DataSourceTier.BUILT_IN,
        DataSourceTier.UNAVAILABLE
    )

    fun tierFor(source: String?): DataSourceTier {
        val normalized = source?.lowercase()?.replace('_', '-') ?: return DataSourceTier.UNAVAILABLE
        return when {
            normalized in setOf("cobblemon", "runtime", "cobblemon-runtime", "live", "sync", "synced") ->
                DataSourceTier.COBBLEMON
            normalized in setOf("server", "server-data", "companion") ->
                DataSourceTier.SERVER_DATA
            normalized in setOf("jar", "cache", "jar-cache", "mod", "datapack", "resource-pack", "local", "local-files") ->
                DataSourceTier.LOCAL_FILES
            normalized in setOf("bundled", "builtin", "built-in", "default", "fallback") ->
                DataSourceTier.BUILT_IN
            else -> DataSourceTier.UNAVAILABLE
        }
    }

    fun preferredSource(sources: Iterable<String?>): DataSourceTier =
        sources.map(::tierFor).minByOrNull { it.rank } ?: DataSourceTier.UNAVAILABLE

    fun sortByPrecedence(sources: Iterable<String?>): List<DataSourceTier> =
        sources.map(::tierFor).distinct().sortedBy { it.rank }

    fun describePrecedence(): String = precedence.joinToString(" > ") { it.displayName }
}
