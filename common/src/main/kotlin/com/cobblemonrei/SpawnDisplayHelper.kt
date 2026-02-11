package com.cobblemonrei

import com.cobblemonrei.config.CobblemonSpawningConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

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

    // --- Sorted spawn builder (shared across REI/JEI/EMI) ---

    data class SortedSpawnEntry(
        val spawn: SpawnInfo,
        val formVariants: List<String>,
        val bucketIndex: Int,
        val bucketTotal: Int
    )

    fun buildSortedSpawns(spawns: List<SpawnInfo>): List<SortedSpawnEntry> {
        val merged = mergeVariantSpawns(spawns)
        val sorted = merged.sortedWith(
            compareBy<MergedSpawn> { bucketSortOrder(it.spawn.bucket) }
                .thenBy { it.spawn.context }
                .thenByDescending { it.spawn.weight }
        )
        val bucketCounts = sorted.groupBy { it.spawn.bucket.lowercase() }.mapValues { it.value.size }
        val bucketIdx = mutableMapOf<String, Int>()
        return sorted.map { ms ->
            val b = ms.spawn.bucket.lowercase()
            val idx = (bucketIdx[b] ?: 0) + 1
            bucketIdx[b] = idx
            SortedSpawnEntry(ms.spawn, ms.formVariants, idx, bucketCounts[b]!!)
        }
    }

    // --- Context parts builder ---

    fun buildContextParts(spawn: SpawnInfo, mergedFormVariants: List<String>): List<String> {
        val parts = mutableListOf<String>()
        if (spawn.context != "grounded") parts.add(spawn.displayContext)
        if (spawn.presets.isNotEmpty()) {
            parts.add(spawn.presets.mapNotNull { PRESET_LABELS[it] ?: titleCase(it) }.joinToString(", "))
        }
        if (mergedFormVariants.isNotEmpty()) {
            parts.add("Forms: ${mergedFormVariants.joinToString(", ")}")
        } else if (spawn.hasFormVariant) {
            parts.add("Form: ${formatFormAspects(spawn.formAspects)}")
        }
        return parts
    }

    // --- Tooltip builder (shared across REI/JEI) ---

    fun buildPokemonTooltipLines(speciesName: String, displayName: String): List<Component> {
        val lines = mutableListOf<Component>()
        lines.add(Component.literal(displayName))
        val species = PokemonItemCache.resolveSpecies(speciesName)
        if (species != null) {
            lines.add(Component.literal("§7#${species.nationalPokedexNumber}"))
        }
        val info = SpawnDataIndex.getSpeciesInfo(speciesName)
        if (info != null) {
            val typeStr = buildString {
                append("§e")
                append(titleCase(info.primaryType))
                info.secondaryType?.let { append(" §7/ §e${titleCase(it)}") }
            }
            lines.add(Component.literal(typeStr))
            lines.add(Component.literal("§7Catch Rate: ${info.catchRate}"))
        }
        val spawns = SpawnDataIndex.getSpawnsFor(speciesName)
        if (spawns.isNotEmpty()) {
            lines.add(Component.literal("§a${spawns.size} spawn location(s)"))
        }
        return lines
    }

    // --- Spawn detail rendering (shared between JEI/EMI) ---

    fun drawSpawnDetails(
        graphics: GuiGraphics,
        speciesName: String,
        spawn: SpawnInfo,
        mergedFormVariants: List<String>,
        bucketIndex: Int,
        bucketTotal: Int,
        width: Int = 180,
        height: Int = 200,
        padding: Int = 6,
        lineHeight: Int = 11
    ) {
        val font = Minecraft.getInstance().font
        val color = bucketColor(spawn.bucket)
        val right = width - padding

        graphics.drawString(font, titleCase(speciesName), padding + 22, 6, 0xFFFFFF, false)

        graphics.drawString(font, "Lv. ${spawn.levelRange}", padding, 22, 0x0099FF, false)
        val bucketText = bucketLabel(spawn.bucket)
        val bucketWidth = font.width(bucketText)
        graphics.drawString(font, bucketText, right - bucketWidth, 22, color, false)

        graphics.fill(padding, 36, right, 37, 0x50FFFFFF)
        var y = 42

        val ctxParts = buildContextParts(spawn, mergedFormVariants)
        if (CobblemonSpawningConfig.get().showSpawnWeights && spawn.weight > 0f) {
            val wtText = "Weight: ${formatWeight(spawn.weight)}"
            val wtWidth = font.width(wtText)
            graphics.drawString(font, wtText, right - wtWidth, y, 0xBBBBBB, false)
        }
        if (ctxParts.isNotEmpty()) {
            graphics.drawString(font, clip(ctxParts.joinToString(" \u00B7 "), 30), padding + 4, y, 0xDDDDDD, false)
        }
        y += lineHeight + 4

        val biomeNames = spawn.biomes.map { formatBiomeName(it) }
        if (biomeNames.isNotEmpty()) {
            val header = if (biomeNames.size > 1) "\u2302 Biomes (any of)" else "\u2302 Biome"
            graphics.drawString(font, header, padding, y, 0xEEEEEE, false)
            y += lineHeight
            for (line in wrapText(biomeNames.joinToString(", "), 30).take(3)) {
                graphics.drawString(font, line, padding + 6, y, 0xDDDDDD, false)
                y += lineHeight
            }
            y += 3
        }

        val conditions = buildConditions(spawn)
        if (conditions.isNotEmpty()) {
            graphics.drawString(font, "\u2699 Conditions", padding, y, 0xEEEEEE, false)
            y += lineHeight
            for (cond in conditions) {
                if (y + lineHeight > height - 16) break
                graphics.drawString(font, cond, padding + 6, y, 0xDDDDDD, false)
                y += lineHeight
            }
            y += 3
        }

        val specials = buildSpecials(spawn)
        if (specials.isNotEmpty()) {
            graphics.drawString(font, "\u2605 Location", padding, y, 0xEEEEEE, false)
            y += lineHeight
            for (s in specials) {
                if (y + lineHeight > height - 16) break
                graphics.drawString(font, s, padding + 6, y, 0xFFCC66, false)
                y += lineHeight
            }
            y += 3
        }

        val anti = spawn.anticondition
        if (anti != null && !anti.isEmpty) {
            val lines = buildExclusionLines(anti)
            if (lines.isNotEmpty()) {
                graphics.drawString(font, "\u2718 Excluded", padding, y, 0xFF7777, false)
                y += lineHeight
                for (line in lines) {
                    if (y + lineHeight > height - 16) break
                    graphics.drawString(font, line, padding + 6, y, 0xEE8888, false)
                    y += lineHeight
                }
                y += 3
            }
        }

        if (CobblemonSpawningConfig.get().showSpawnWeights && spawn.weightMultipliers.isNotEmpty()) {
            graphics.drawString(font, "\u25B2 Weight Modifiers", padding, y, 0xEEEEEE, false)
            y += lineHeight
            for (wm in spawn.weightMultipliers) {
                if (y + lineHeight > height - 16) break
                val arrow: String
                val c: Int
                when {
                    wm.multiplier > 1f -> { arrow = "\u25B2"; c = 0x88DD88 }
                    wm.multiplier < 1f -> { arrow = "\u25BC"; c = 0xEE8888 }
                    else -> { arrow = "\u25CF"; c = 0xBBBBBB }
                }
                graphics.drawString(font, "$arrow ${formatWeight(wm.multiplier)}x ${clip(wm.conditionSummary, 28)}", padding + 6, y, c, false)
                y += lineHeight
            }
        }

        val footerY = height - padding - 2
        graphics.fill(padding, footerY - 4, right, footerY - 3, 0x20FFFFFF)
        val footerLeft = "${bucketLabel(spawn.bucket)} $bucketIndex/$bucketTotal"
        graphics.drawString(font, footerLeft, padding, footerY, color, false)
    }

    // --- Evolution text rendering (shared between JEI/EMI) ---

    fun drawEvolutionText(
        graphics: GuiGraphics,
        evolution: EvolutionInfo,
        branchIndex: Int,
        branchTotal: Int,
        width: Int = 180,
        slotSize: Int = 18
    ) {
        val font = Minecraft.getInstance().font
        val centerX = width / 2

        val fromName = clip(evolution.displayFromName, 16)
        val fromWidth = font.width(fromName)
        graphics.drawString(font, fromName, 20 + slotSize / 2 - fromWidth / 2, 32, 0xFFFFFF, false)

        val toName = clip(evolution.displayToName, 16)
        val toWidth = font.width(toName)
        graphics.drawString(font, toName, width - 20 - slotSize / 2 - toWidth / 2, 32, 0xFFFFFF, false)

        if (branchTotal > 1) {
            val branchText = "$branchIndex/$branchTotal"
            val bw = font.width(branchText)
            graphics.drawString(font, branchText, 20 + slotSize / 2 - bw / 2, 43, 0xBBBBBB, false)
        }

        val lines = wrapReqText(evolution.displayRequirements, 32, 3)
        var reqY = 56
        for (line in lines) {
            val lw = font.width(line)
            graphics.drawString(font, line, centerX - lw / 2, reqY, 0xFFDD88, false)
            reqY += 11
        }
    }
}
