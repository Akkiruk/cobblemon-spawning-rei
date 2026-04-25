package com.cobbledex

enum class DataSourceTier(val displayName: String, val rank: Int) {
    SERVER_SYNC("server sync", 0),
    RUNTIME("runtime", 1),
    JAR_OR_DATAPACK("jar/datapack", 2),
    BUNDLED_DEFAULT("bundled default", 3),
    UNKNOWN("unknown", 4)
}

object DataSourcePolicy {
    val precedence: List<DataSourceTier> = listOf(
        DataSourceTier.SERVER_SYNC,
        DataSourceTier.RUNTIME,
        DataSourceTier.JAR_OR_DATAPACK,
        DataSourceTier.BUNDLED_DEFAULT,
        DataSourceTier.UNKNOWN
    )

    fun tierFor(source: String?): DataSourceTier {
        val normalized = source?.lowercase()?.replace('_', '-') ?: return DataSourceTier.UNKNOWN
        return when {
            normalized in setOf("server", "server-sync", "sync", "synced") -> DataSourceTier.SERVER_SYNC
            normalized in setOf("runtime", "cobblemon-runtime", "live") -> DataSourceTier.RUNTIME
            normalized in setOf("jar", "cache", "jar-cache", "mod", "datapack", "resource-pack") -> DataSourceTier.JAR_OR_DATAPACK
            normalized in setOf("bundled", "builtin", "built-in", "default", "fallback") -> DataSourceTier.BUNDLED_DEFAULT
            else -> DataSourceTier.UNKNOWN
        }
    }

    fun preferredSource(sources: Iterable<String?>): DataSourceTier =
        sources.map(::tierFor).minByOrNull { it.rank } ?: DataSourceTier.UNKNOWN

    fun sortByPrecedence(sources: Iterable<String?>): List<DataSourceTier> =
        sources.map(::tierFor).distinct().sortedBy { it.rank }

    fun describePrecedence(): String = precedence.joinToString(" > ") { it.displayName }
}