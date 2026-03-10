package com.cobbledex

object RidingDataLoader {

    fun loadFromClasspath(): Map<String, RidingInfo> {
        val stream = RidingDataLoader::class.java.getResourceAsStream("/data/cobbledex-rei-emi-jei/riding_data.csv")
            ?: run {
                DebugLog.warn("riding_data.csv not found on classpath")
                return emptyMap()
            }

        val lines = stream.bufferedReader().use { it.readLines() }
        if (lines.size < 2) return emptyMap()

        // Group CSV rows by pokemon name, then assemble RidingInfo per species
        val rowsBySpecies = mutableMapOf<String, MutableList<List<String>>>()
        for (line in lines.drop(1)) {
            val cols = parseCsvLine(line)
            if (cols.size < 16) continue
            val pokemon = cols[0].lowercase().trim()
            if (pokemon.isBlank()) continue
            rowsBySpecies.getOrPut(pokemon) { mutableListOf() }.add(cols)
        }

        val result = mutableMapOf<String, RidingInfo>()
        for ((pokemon, rows) in rowsBySpecies) {
            val first = rows.first()
            val allMountTypes = first[1].split(",").map { it.trim() }.filter { it.isNotBlank() }
            val ridingStyles = first[2].split(",").map { it.trim() }.filter { it.isNotBlank() }
            val seats = first[3].trim().toIntOrNull() ?: 1

            val mounts = rows.mapNotNull { cols ->
                try {
                    RidingMount(
                        mountType = cols[4].trim(),
                        ridingStyle = cols[5].trim(),
                        speedMin = cols[6].trim().toInt(),
                        speedMax = cols[7].trim().toInt(),
                        accelMin = cols[8].trim().toInt(),
                        accelMax = cols[9].trim().toInt(),
                        skillMin = cols[10].trim().toInt(),
                        skillMax = cols[11].trim().toInt(),
                        jumpMin = cols[12].trim().toInt(),
                        jumpMax = cols[13].trim().toInt(),
                        staminaMin = cols[14].trim().toInt(),
                        staminaMax = cols[15].trim().toInt(),
                    )
                } catch (e: NumberFormatException) {
                    DebugLog.once("riding-parse-$pokemon") { "Bad number in riding data for $pokemon: ${e.message}" }
                    null
                }
            }

            if (mounts.isNotEmpty()) {
                result[SpeciesNameNormalizer.normalize(pokemon)] = RidingInfo(
                    pokemon = pokemon,
                    allMountTypes = allMountTypes,
                    ridingStyles = ridingStyles,
                    seats = seats,
                    mounts = mounts,
                )
            }
        }

        DebugLog.info("Loaded riding data for ${result.size} species (${result.values.sumOf { it.mounts.size }} mount entries)")
        return result
    }

    /** Simple CSV parser that handles quoted fields with commas */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        val sb = StringBuilder()
        var inQuotes = false
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuotes -> inQuotes = true
                c == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }
}
