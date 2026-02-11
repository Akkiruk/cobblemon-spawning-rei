package com.cobblemonrei

object SpawnDisplayHelper {

    data class MergedSpawn(val spawn: SpawnInfo, val formVariants: List<String>)

    data class EvolutionBranch(val evolution: EvolutionInfo, val branchIndex: Int, val branchTotal: Int)

    val BUCKET_COLORS = mapOf(
        "common" to 0xFF4CAF50.toInt(),
        "uncommon" to 0xFFFFC107.toInt(),
        "rare" to 0xFFFF5722.toInt(),
        "ultra-rare" to 0xFFE040FB.toInt()
    )

    val BUCKET_LABELS = mapOf(
        "common" to "Common",
        "uncommon" to "Uncommon",
        "rare" to "Rare",
        "ultra-rare" to "Ultra Rare"
    )

    private val BUCKET_ORDER = listOf("common", "uncommon", "rare", "ultra-rare")

    val PRESET_LABELS = mapOf(
        "natural" to "Natural",
        "water" to "Water",
        "lava" to "Lava",
        "urban" to "Urban",
        "wild" to "Wild",
        "foliage" to "Foliage",
        "treetop" to "Treetop",
        "derelict" to "Derelict",
        "redstone" to "Redstone",
        "ancient_city" to "Ancient City",
        "desert_pyramid" to "Desert Pyramid",
        "end_city" to "End City",
        "jungle_pyramid" to "Jungle Pyramid",
        "mansion" to "Mansion",
        "nether_fossil" to "Nether Fossil",
        "nether_structures" to "Nether Structure",
        "ocean_monument" to "Ocean Monument",
        "ocean_ruins" to "Ocean Ruins",
        "pillager_outpost" to "Pillager Outpost",
        "stronghold" to "Stronghold",
        "trail_ruins" to "Trail Ruins"
    )

    fun bucketColor(bucket: String): Int =
        BUCKET_COLORS[bucket.lowercase()] ?: 0xFFAAAAAA.toInt()

    fun bucketLabel(bucket: String): String =
        BUCKET_LABELS[bucket.lowercase()] ?: titleCase(bucket)

    fun bucketSortOrder(bucket: String): Int =
        BUCKET_ORDER.indexOf(bucket.lowercase()).let { if (it < 0) 99 else it }

    // --- Spawn merge ---

    fun mergeVariantSpawns(spawns: List<SpawnInfo>): List<MergedSpawn> {
        val groups = spawns.groupBy { spawnMergeKey(it) }
        return groups.map { (_, group) ->
            val primary = group.first()
            val variants = group
                .filter { it.formAspects.isNotBlank() }
                .map {
                    it.formAspects
                        .replace("region_bias=", "")
                        .replace("_", " ")
                        .split(" ")
                        .filter { w -> w.isNotBlank() }
                        .joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }
                }
                .distinct()
            MergedSpawn(primary, variants)
        }
    }

    private fun spawnMergeKey(s: SpawnInfo): String {
        return "${s.pokemon}|${s.bucket}|${s.weight}|${s.levelRange}|${s.context}|" +
            "${s.biomes.sorted()}|${s.timeRange}|${s.weather}|${s.dimensions.sorted()}|" +
            "${s.structures.sorted()}|${s.canSeeSky}|${s.minLight}|${s.maxLight}|" +
            "${s.minSkyLight}|${s.maxSkyLight}|${s.minY}|${s.maxY}|" +
            "${s.neededNearbyBlocks.sorted()}|${s.neededBaseBlocks.sorted()}|" +
            "${s.moonPhase}|${s.presets.sorted()}|${s.fluid}"
    }

    // --- Evolution deduplication ---

    fun deduplicateEvolutions(evolutionsBySpecies: Map<String, List<EvolutionInfo>>): List<EvolutionBranch> {
        val seen = mutableSetOf<String>()
        val allEvos = mutableListOf<EvolutionInfo>()
        for ((_, evos) in evolutionsBySpecies) {
            for (evo in evos) {
                if (evo.id in seen) continue
                seen.add(evo.id)
                allEvos.add(evo)
            }
        }
        val grouped = allEvos.groupBy { it.fromSpecies }
        return allEvos.map { evo ->
            val siblings = grouped[evo.fromSpecies] ?: listOf(evo)
            EvolutionBranch(evo, siblings.indexOf(evo) + 1, siblings.size)
        }
    }

    // --- Condition / location / exclusion builders ---

    fun buildConditions(spawn: SpawnInfo): List<String> {
        val list = mutableListOf<String>()
        spawn.timeRange?.let {
            val icon = when {
                it.contains("day", true) -> "\u2600 "
                it.contains("night", true) -> "\u263D "
                it.contains("dusk", true) || it.contains("dawn", true) -> "\u263C "
                else -> ""
            }
            list.add("$icon${titleCase(it)}")
        }
        val weather = spawn.weather.displayText
        if (weather != "Any") {
            val icon = when (weather) {
                "Thunder" -> "\u26A1 "
                "Rain" -> "\u2602 "
                "Clear" -> "\u2600 "
                else -> ""
            }
            list.add("$icon$weather")
        }
        if (spawn.canSeeSky == true) list.add("Open sky")
        if (spawn.canSeeSky == false) list.add("Underground")
        if (spawn.minSkyLight != null || spawn.maxSkyLight != null) {
            val min = spawn.minSkyLight ?: 0
            val max = spawn.maxSkyLight ?: 15
            when {
                min == 0 && max <= 7 -> list.add("Dark (sky light \u2264$max)")
                min >= 8 -> list.add("Bright (sky light \u2265$min)")
                else -> list.add("Sky light $min\u2013$max")
            }
        }
        if (spawn.minLight != null || spawn.maxLight != null) {
            val min = spawn.minLight ?: 0
            val max = spawn.maxLight ?: 15
            if (max == 0) list.add("No light") else list.add("Light $min\u2013$max")
        }
        if (spawn.minY != null || spawn.maxY != null) {
            when {
                spawn.minY != null && spawn.maxY != null -> list.add("Y: ${spawn.minY} to ${spawn.maxY}")
                spawn.minY != null -> list.add("Y \u2265 ${spawn.minY}")
                spawn.maxY != null -> list.add("Y \u2264 ${spawn.maxY}")
            }
        }
        spawn.moonPhase?.let { list.add("Moon: ${titleCase(it)}") }
        if (spawn.isFishing) {
            val lure = spawn.minLureLevel
            if (lure != null && lure > 0) list.add("Fishing (Lure $lure+)") else list.add("Fishing")
        }
        return list
    }

    fun buildSpecials(spawn: SpawnInfo, clipLen: Int = 40): List<String> {
        val list = mutableListOf<String>()
        val structNames = spawn.structures.map { formatId(it) }.toSet()
        if (structNames.isNotEmpty()) {
            list.add("Near structure: ${clip(structNames.joinToString(", "), clipLen)}")
        }
        if (spawn.dimensions.isNotEmpty()) {
            list.add("Dimension: ${spawn.dimensions.joinToString(", ") { formatDimension(it) }}")
        }
        spawn.fluid?.let {
            val name = when {
                it.contains("water") -> "Water"
                it.contains("lava") -> "Lava"
                else -> formatId(it)
            }
            list.add("In fluid: $name")
        }
        if (spawn.neededBaseBlocks.isNotEmpty()) {
            val names = spawn.neededBaseBlocks.map { formatId(it) }
            val redundant = structNames.isNotEmpty() && names.all { it.lowercase().contains("structure") }
            if (!redundant) list.add("On block: ${clip(names.joinToString(", "), clipLen)}")
        }
        if (spawn.neededNearbyBlocks.isNotEmpty()) {
            val names = spawn.neededNearbyBlocks.map { formatId(it) }
            val redundant = structNames.isNotEmpty() && names.all { it.lowercase().contains("structure") }
            if (!redundant) list.add("Near block: ${clip(names.joinToString(", "), clipLen)}")
        }
        return list
    }

    fun buildExclusionLines(anti: SpawnAntiCondition): List<String> {
        val lines = mutableListOf<String>()
        if (anti.biomes.isNotEmpty()) {
            lines.add("Biomes: ${anti.biomes.map { formatBiomeName(it) }.joinToString(", ")}")
        }
        if (anti.structures.isNotEmpty()) {
            lines.add("Structures: ${anti.structures.map { formatId(it) }.joinToString(", ")}")
        }
        if (anti.minY != null || anti.maxY != null) {
            val r = listOfNotNull(anti.minY?.let { "Y \u2265 $it" }, anti.maxY?.let { "Y \u2264 $it" })
            lines.add("Height: ${r.joinToString(", ")}")
        }
        return lines
    }

    // --- Formatting ---

    fun formatWeight(weight: Float): String =
        if (weight == weight.toLong().toFloat()) weight.toLong().toString() else "%.1f".format(weight)

    fun formatDimension(dim: String): String = when (dim.lowercase()) {
        "minecraft:overworld" -> "Overworld"
        "minecraft:the_nether" -> "Nether"
        "minecraft:the_end" -> "The End"
        else -> formatId(dim)
    }

    fun formatFormAspects(aspects: String): String =
        titleCase(aspects.replace("region_bias=", ""))

    // --- Text layout ---

    fun clip(text: String, maxLen: Int): String =
        if (text.length > maxLen) text.take(maxLen - 1) + "\u2026" else text

    fun wrapText(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val items = text.split(", ")
        val lines = mutableListOf<String>()
        var current = ""
        for (item in items) {
            val next = if (current.isEmpty()) item else "$current, $item"
            if (next.length > maxChars && current.isNotEmpty()) {
                lines.add(current)
                current = item
            } else {
                current = next
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines
    }

    fun wrapReqText(text: String, maxChars: Int, maxLines: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val items = text.split(", ")
        val lines = mutableListOf<String>()
        var current = ""
        for (item in items) {
            val next = if (current.isEmpty()) item else "$current, $item"
            if (next.length > maxChars && current.isNotEmpty()) {
                lines.add(current)
                if (lines.size >= maxLines) {
                    val remaining = items.drop(items.indexOf(item))
                    lines[lines.lastIndex] = clip(lines.last() + ", " + remaining.joinToString(", "), maxChars)
                    return lines
                }
                current = item
            } else {
                current = next
            }
        }
        if (current.isNotEmpty()) {
            if (lines.size >= maxLines) {
                lines[lines.lastIndex] = clip(lines.last() + ", $current", maxChars)
            } else {
                lines.add(current)
            }
        }
        return lines
    }
}
