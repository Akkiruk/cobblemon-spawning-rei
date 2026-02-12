package com.cobblemonrei

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Diagnostic logic for analyzing Pokémon data coverage.
 * Platform-specific command registration calls these methods.
 */
object DiagnosticService {
    
    fun interface MessageSender {
        fun send(message: String)
    }
    
    fun showStats(sender: MessageSender): Int {
        val index = SpawnDataIndex
        
        if (!index.hasData()) {
            sender.send("§cNo data loaded yet. Try again in a few seconds.")
            return 0
        }
        
        val allSpecies = index.allSpeciesNames
        val withSpawns = index.spawnsBySpecies.keys
        val withEvolutions = index.evolutionsBySpecies.keys
        val withObtainment = index.obtainmentBySpecies.keys
        val withDex = index.speciesInfo.count { it.value.nationalDexNumber > 0 }
        
        sender.send("§6=== Cobblemon Spawning REI Stats ===")
        sender.send("§7Total species: §f${allSpecies.size}")
        sender.send("§7With National Dex #: §f$withDex")
        sender.send("§7With spawn data: §a${withSpawns.size} §7(§c${allSpecies.size - withSpawns.size}§7 missing)")
        sender.send("§7With evolution data: §a${withEvolutions.size}")
        sender.send("§7With obtainment data: §a${withObtainment.size}")
        sender.send("§7Data source: §f${index.dataSource.name}")
        sender.send("§7Load state: §f${index.loadState.name}")
        sender.send("§oUse /spawningrei dump for full report§r")
        
        return 1
    }
    
    fun showMissing(sender: MessageSender): Int {
        val index = SpawnDataIndex
        
        if (!index.hasData()) {
            sender.send("§cNo data loaded yet.")
            return 0
        }
        
        val withSpawns = index.spawnsBySpecies.keys
        val withObtainment = index.obtainmentBySpecies.keys
        
        val officialMissingBoth = index.speciesInfo
            .filter { it.value.nationalDexNumber in 1..1025 }
            .filter { !withSpawns.contains(it.key) && !withObtainment.contains(it.key) }
            .keys
            .sorted()
        
        sender.send("§6=== Official Pokémon Missing Spawn & Obtainment Data ===")
        
        if (officialMissingBoth.isEmpty()) {
            sender.send("§aAll official Pokémon have spawn or obtainment data!")
        } else {
            sender.send("§c${officialMissingBoth.size} Pokémon missing both spawn and obtainment:")
            val preview = officialMissingBoth.take(20).joinToString(", ")
            val suffix = if (officialMissingBoth.size > 20) " ... and ${officialMissingBoth.size - 20} more" else ""
            sender.send("§7$preview$suffix")
        }
        
        sender.send("§oUse /spawningrei dump for complete list§r")
        return 1
    }
    
    fun reloadData(sender: MessageSender): Int {
        sender.send("§6Reloading spawn data...")
        SpawnDataIndex.loadAll()
        sender.send("§aReload complete. ${SpawnDataIndex.allSpeciesNames.size} species loaded.")
        return 1
    }
    
    fun dumpDiagnostics(sender: MessageSender): Int {
        val index = SpawnDataIndex
        
        if (!index.hasData()) {
            sender.send("§cNo data loaded yet.")
            return 0
        }
        
        try {
            val report = buildDiagnosticReport()
            val outputFile = writeDiagnosticFile(report)
            
            sender.send("§aDiagnostic report written to:")
            sender.send("§7$outputFile")
            sender.send("§7Total lines: ${report.lines().size}")
        } catch (e: Exception) {
            sender.send("§cFailed to write report: ${e.message}")
            DebugLog.warn("Diagnostic dump failed: ${e.message}")
            return 0
        }
        
        return 1
    }
    
    private fun buildDiagnosticReport(): String {
        val sb = StringBuilder()
        val index = SpawnDataIndex
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        
        sb.appendLine("=".repeat(80))
        sb.appendLine("COBBLEMON SPAWNING REI - DIAGNOSTIC REPORT")
        sb.appendLine("Generated: $timestamp")
        sb.appendLine("=".repeat(80))
        sb.appendLine()
        
        val allSpecies = index.allSpeciesNames
        val withSpawns = index.spawnsBySpecies
        val withEvolutions = index.evolutionsBySpecies
        val withObtainment = index.obtainmentBySpecies
        val speciesInfo = index.speciesInfo
        
        sb.appendLine("SUMMARY")
        sb.appendLine("-".repeat(40))
        sb.appendLine("Total species in index: ${allSpecies.size}")
        sb.appendLine("Species with spawn data: ${withSpawns.size}")
        sb.appendLine("Species with evolution data: ${withEvolutions.size}")
        sb.appendLine("Species with obtainment data: ${withObtainment.size}")
        sb.appendLine("Species with National Dex #: ${speciesInfo.count { it.value.nationalDexNumber > 0 }}")
        sb.appendLine("Data source: ${index.dataSource.name}")
        sb.appendLine("Load state: ${index.loadState.name}")
        sb.appendLine()
        
        val officialWithDex = speciesInfo.filter { it.value.nationalDexNumber in 1..1025 }
        val extrasWithDex = speciesInfo.filter { it.value.nationalDexNumber > 1025 }
        val noDex = allSpecies.filter { speciesInfo[it]?.nationalDexNumber == null || speciesInfo[it]?.nationalDexNumber == 0 }
        
        sb.appendLine("SPECIES CATEGORIES")
        sb.appendLine("-".repeat(40))
        sb.appendLine("Official Pokémon (Dex 1-1025): ${officialWithDex.size}")
        sb.appendLine("Extended Pokémon (Dex >1025): ${extrasWithDex.size}")
        sb.appendLine("No National Dex # (forms/extras): ${noDex.size}")
        sb.appendLine()
        
        val officialNames = officialWithDex.keys
        val officialWithSpawns = officialNames.filter { withSpawns.containsKey(it) }
        val officialWithObtainment = officialNames.filter { withObtainment.containsKey(it) }
        val officialMissingSpawns = officialNames.filter { !withSpawns.containsKey(it) }
        val officialMissingBoth = officialNames.filter { !withSpawns.containsKey(it) && !withObtainment.containsKey(it) }
        
        sb.appendLine("OFFICIAL POKÉMON COVERAGE (Dex 1-1025)")
        sb.appendLine("-".repeat(40))
        sb.appendLine("With spawn data: ${officialWithSpawns.size}/${officialWithDex.size} (${percent(officialWithSpawns.size, officialWithDex.size)})")
        sb.appendLine("With obtainment data: ${officialWithObtainment.size}/${officialWithDex.size} (${percent(officialWithObtainment.size, officialWithDex.size)})")
        sb.appendLine("Missing spawns: ${officialMissingSpawns.size}")
        sb.appendLine("Missing BOTH spawn and obtainment: ${officialMissingBoth.size}")
        sb.appendLine()
        
        sb.appendLine("=".repeat(80))
        sb.appendLine("DETAILED LISTS")
        sb.appendLine("=".repeat(80))
        sb.appendLine()
        
        sb.appendLine("OFFICIAL POKÉMON MISSING SPAWN DATA (${officialMissingSpawns.size})")
        sb.appendLine("-".repeat(40))
        if (officialMissingSpawns.isEmpty()) {
            sb.appendLine("(none)")
        } else {
            val sorted = officialMissingSpawns.sortedBy { speciesInfo[it]?.nationalDexNumber ?: Int.MAX_VALUE }
            for (species in sorted) {
                val dex = speciesInfo[species]?.nationalDexNumber ?: 0
                val hasObtain = if (withObtainment.containsKey(species)) " [has obtainment]" else ""
                sb.appendLine("#${dex.toString().padStart(4, '0')} $species$hasObtain")
            }
        }
        sb.appendLine()
        
        sb.appendLine("OFFICIAL POKÉMON MISSING BOTH SPAWN AND OBTAINMENT (${officialMissingBoth.size})")
        sb.appendLine("-".repeat(40))
        if (officialMissingBoth.isEmpty()) {
            sb.appendLine("(none - all official Pokémon have at least spawn or obtainment data)")
        } else {
            val sorted = officialMissingBoth.sortedBy { speciesInfo[it]?.nationalDexNumber ?: Int.MAX_VALUE }
            for (species in sorted) {
                val dex = speciesInfo[species]?.nationalDexNumber ?: 0
                sb.appendLine("#${dex.toString().padStart(4, '0')} $species")
            }
        }
        sb.appendLine()
        
        sb.appendLine("EXTENDED POKÉMON (Dex >1025, ${extrasWithDex.size} total)")
        sb.appendLine("-".repeat(40))
        val extrasSorted = extrasWithDex.entries.sortedBy { it.value.nationalDexNumber }
        for ((species, info) in extrasSorted) {
            val hasSpawn = if (withSpawns.containsKey(species)) "spawn" else ""
            val hasObtain = if (withObtainment.containsKey(species)) "obtainment" else ""
            val hasEvo = if (withEvolutions.containsKey(species)) "evolution" else ""
            val dataTypes = listOf(hasSpawn, hasObtain, hasEvo).filter { it.isNotEmpty() }.joinToString(", ")
            sb.appendLine("#${info.nationalDexNumber} $species: ${dataTypes.ifEmpty { "(no data)" }}")
        }
        sb.appendLine()
        
        sb.appendLine("SPECIES WITHOUT NATIONAL DEX # (${noDex.size})")
        sb.appendLine("-".repeat(40))
        val noDexSorted = noDex.sorted()
        for (species in noDexSorted.take(100)) {
            val hasSpawn = if (withSpawns.containsKey(species)) "spawn" else ""
            val hasObtain = if (withObtainment.containsKey(species)) "obtainment" else ""
            val hasEvo = if (withEvolutions.containsKey(species)) "evolution" else ""
            val dataTypes = listOf(hasSpawn, hasObtain, hasEvo).filter { it.isNotEmpty() }.joinToString(", ")
            sb.appendLine("$species: ${dataTypes.ifEmpty { "(no data)" }}")
        }
        if (noDex.size > 100) {
            sb.appendLine("... and ${noDex.size - 100} more")
        }
        sb.appendLine()
        
        sb.appendLine("=".repeat(80))
        sb.appendLine("SPAWN DATA ANALYSIS")
        sb.appendLine("=".repeat(80))
        sb.appendLine()
        
        val totalSpawnEntries = withSpawns.values.sumOf { it.size }
        sb.appendLine("Total spawn entries: $totalSpawnEntries across ${withSpawns.size} species")
        sb.appendLine()
        
        sb.appendLine("TOP 20 SPECIES BY SPAWN ENTRY COUNT")
        sb.appendLine("-".repeat(40))
        val bySpawnCount = withSpawns.entries.sortedByDescending { it.value.size }.take(20)
        for ((species, spawns) in bySpawnCount) {
            sb.appendLine("$species: ${spawns.size} spawn entries")
        }
        sb.appendLine()
        
        sb.appendLine("=".repeat(80))
        sb.appendLine("OBTAINMENT DATA ANALYSIS")
        sb.appendLine("=".repeat(80))
        sb.appendLine()
        
        val obtainMethods = mutableMapOf<String, Int>()
        for ((_, infos) in withObtainment) {
            for (info in infos) {
                obtainMethods[info.method] = (obtainMethods[info.method] ?: 0) + 1
            }
        }
        
        sb.appendLine("OBTAINMENT METHODS")
        sb.appendLine("-".repeat(40))
        for ((method, count) in obtainMethods.entries.sortedByDescending { it.value }) {
            sb.appendLine("$method: $count entries")
        }
        sb.appendLine()
        
        sb.appendLine("=".repeat(80))
        sb.appendLine("COBBLEMON RUNTIME CHECK")
        sb.appendLine("=".repeat(80))
        sb.appendLine()
        
        try {
            val runtimeSpeciesRaw = PokemonSpecies.implemented.map { it.name.lowercase() }.toSet()
            val runtimeSpeciesNormalized = runtimeSpeciesRaw.map { SpeciesNameNormalizer.normalize(it) }.toSet()
            val inIndexButNotRuntime = allSpecies.filter { !runtimeSpeciesNormalized.contains(it) }
            val inRuntimeButNotIndex = runtimeSpeciesRaw.filter { !allSpecies.contains(SpeciesNameNormalizer.normalize(it)) }
            
            sb.appendLine("Cobblemon runtime species count: ${runtimeSpeciesRaw.size}")
            sb.appendLine("In index but not runtime: ${inIndexButNotRuntime.size}")
            sb.appendLine("In runtime but not index: ${inRuntimeButNotIndex.size}")
            
            if (inIndexButNotRuntime.isNotEmpty() && inIndexButNotRuntime.size <= 50) {
                sb.appendLine()
                sb.appendLine("Species in index but not Cobblemon runtime:")
                for (s in inIndexButNotRuntime.sorted()) {
                    sb.appendLine("  $s")
                }
            }
            
            if (inRuntimeButNotIndex.isNotEmpty() && inRuntimeButNotIndex.size <= 50) {
                sb.appendLine()
                sb.appendLine("Species in Cobblemon runtime but missing from index:")
                for (s in inRuntimeButNotIndex.sorted()) {
                    sb.appendLine("  $s")
                }
            }
        } catch (e: Exception) {
            sb.appendLine("Could not access Cobblemon runtime: ${e.message}")
        }
        sb.appendLine()
        
        sb.appendLine("=".repeat(80))
        sb.appendLine("END OF REPORT")
        sb.appendLine("=".repeat(80))
        
        return sb.toString()
    }
    
    private fun percent(part: Int, total: Int): String {
        if (total == 0) return "0%"
        return "%.1f%%".format(part.toDouble() / total * 100)
    }
    
    private fun writeDiagnosticFile(content: String): String {
        val gameDir = try {
            com.cobblemonrei.platform.PlatformHelper.getGameDir()
        } catch (e: Exception) {
            java.nio.file.Paths.get(".")
        }
        
        val debugDir = gameDir.resolve("spawningrei-debug")
        if (!Files.exists(debugDir)) {
            Files.createDirectories(debugDir)
        }
        
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val fileName = "diagnostic_$timestamp.txt"
        val outputPath = debugDir.resolve(fileName)
        
        Files.writeString(outputPath, content)
        return outputPath.toAbsolutePath().toString()
    }
}
