package com.cobbledex

import com.google.gson.JsonParser
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path

object FossilDataLoader {

    fun loadFromAllSources(): Map<String, List<FossilCombo>> {
        val roots = SpawnDataLoader.findAllModRootPaths()
        DebugLog.debug("Scanning ${roots.size} mod roots for fossil data")

        val result = mutableMapOf<String, MutableList<FossilCombo>>()
        var totalFiles = 0

        for (root in roots) {
            try {
                val dataDir = root.resolve("data")
                if (!Files.exists(dataDir) || !Files.isDirectory(dataDir)) continue

                Files.list(dataDir).use { namespaces ->
                    namespaces.filter { Files.isDirectory(it) }.forEach { namespace ->
                        val fossilDir = namespace.resolve("fossils")
                        if (Files.exists(fossilDir) && Files.isDirectory(fossilDir)) {
                            Files.walk(fossilDir, 5).use { files ->
                                files.filter { it.toString().endsWith(".json") }.forEach { file ->
                                    if (parseFossilFile(file, result)) totalFiles++
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                DebugLog.once("fossil-root-${root}") { "Error scanning fossil root: ${e.message}" }
            }
        }

        // Scan local datapacks if enabled
        val scanDatapacks = com.cobbledex.config.CobbleDexConfig.get().localDatapackScan
        if (scanDatapacks) {
            val datapacksDir = SpawnDataLoader.getClientDatapacksDir()
            if (datapacksDir != null && Files.exists(datapacksDir) && Files.isDirectory(datapacksDir)) {
                scanDatapacksFossils(datapacksDir, result) { if (it) totalFiles++ }
            }
        }

        DebugLog.info("Loaded $totalFiles fossil files for ${result.size} species")
        return result
    }

    private fun parseFossilFile(file: Path, result: MutableMap<String, MutableList<FossilCombo>>): Boolean {
        return try {
            val json = Files.newInputStream(file).use { stream ->
                InputStreamReader(stream).use { reader -> JsonParser.parseReader(reader).asJsonObject }
            }

            val rawResult = json.get("result")?.asString ?: return false
            val fossilArray = json.getAsJsonArray("fossils") ?: return false

            // Parse species name and extra tags from result string
            // Format: "species_name min_perfect_ivs=2 aspect=legendary"
            val parts = rawResult.split(" ")
            val speciesName = parts[0].lowercase()
            val extraTags = if (parts.size > 1) parts.drop(1).joinToString(" ") else null

            val items = fossilArray.map { it.asString }
            if (items.isEmpty()) return false

            val combo = FossilCombo(speciesName, items, extraTags)
            result.getOrPut(speciesName) { mutableListOf() }.add(combo)
            true
        } catch (e: Exception) {
            DebugLog.once("fossil-parse-${file.fileName}") { "Failed to parse fossil file: ${e.message}" }
            false
        }
    }

    private fun scanDatapacksFossils(
        dir: Path,
        result: MutableMap<String, MutableList<FossilCombo>>,
        counter: (Boolean) -> Unit
    ) {
        try {
            Files.list(dir).use { entries ->
                entries.forEach { entry ->
                    if (Files.isDirectory(entry)) {
                        val dataDir = entry.resolve("data")
                        if (Files.exists(dataDir)) {
                            Files.list(dataDir).use { namespaces ->
                                namespaces.filter { Files.isDirectory(it) }.forEach { namespace ->
                                    val fossilDir = namespace.resolve("fossils")
                                    if (Files.exists(fossilDir) && Files.isDirectory(fossilDir)) {
                                        Files.walk(fossilDir, 5).use { files ->
                                            files.filter { it.toString().endsWith(".json") }.forEach { file ->
                                                counter(parseFossilFile(file, result))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            DebugLog.once("fossil-datapacks") { "Error scanning datapacks for fossils: ${e.message}" }
        }
    }
}
