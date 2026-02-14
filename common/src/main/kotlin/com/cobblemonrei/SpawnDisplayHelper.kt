package com.cobblemonrei

import com.cobblemonrei.config.CobblemonSpawningConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

object SpawnDisplayHelper {

    data class MergedSpawn(val spawn: SpawnInfo, val formVariants: List<String>)

    data class EvolutionBranch(val evolution: EvolutionInfo, val branchIndex: Int, val branchTotal: Int)

    val BUCKET_COLORS = mapOf(
        "common" to 0xFF4CAF50.toInt(),
        "uncommon" to 0xFFFFC107.toInt(),
        "rare" to 0xFFFF5722.toInt(),
        "ultra-rare" to 0xFFE040FB.toInt()
    )

    private val BUCKET_ORDER = listOf("common", "uncommon", "rare", "ultra-rare")

    fun bucketColor(bucket: String): Int =
        BUCKET_COLORS[bucket.lowercase()] ?: 0xFFAAAAAA.toInt()

    fun bucketLabel(bucket: String): String = when (bucket.lowercase()) {
        "common" -> tr("cobblemon-spawning-rei.bucket.common")
        "uncommon" -> tr("cobblemon-spawning-rei.bucket.uncommon")
        "rare" -> tr("cobblemon-spawning-rei.bucket.rare")
        "ultra-rare" -> tr("cobblemon-spawning-rei.bucket.ultra_rare")
        else -> titleCase(bucket)
    }

    fun bucketSortOrder(bucket: String): Int =
        BUCKET_ORDER.indexOf(bucket.lowercase()).let { if (it < 0) 99 else it }

    fun presetLabel(preset: String): String = when (preset.lowercase()) {
        "natural" -> tr("cobblemon-spawning-rei.preset.natural")
        "water" -> tr("cobblemon-spawning-rei.preset.water")
        "lava" -> tr("cobblemon-spawning-rei.preset.lava")
        "urban" -> tr("cobblemon-spawning-rei.preset.urban")
        "wild" -> tr("cobblemon-spawning-rei.preset.wild")
        "foliage" -> tr("cobblemon-spawning-rei.preset.foliage")
        "treetop" -> tr("cobblemon-spawning-rei.preset.treetop")
        "derelict" -> tr("cobblemon-spawning-rei.preset.derelict")
        "redstone" -> tr("cobblemon-spawning-rei.preset.redstone")
        "ancient_city" -> tr("cobblemon-spawning-rei.preset.ancient_city")
        "desert_pyramid" -> tr("cobblemon-spawning-rei.preset.desert_pyramid")
        "end_city" -> tr("cobblemon-spawning-rei.preset.end_city")
        "jungle_pyramid" -> tr("cobblemon-spawning-rei.preset.jungle_pyramid")
        "mansion" -> tr("cobblemon-spawning-rei.preset.mansion")
        "nether_fossil" -> tr("cobblemon-spawning-rei.preset.nether_fossil")
        "nether_structures" -> tr("cobblemon-spawning-rei.preset.nether_structures")
        "ocean_monument" -> tr("cobblemon-spawning-rei.preset.ocean_monument")
        "ocean_ruins" -> tr("cobblemon-spawning-rei.preset.ocean_ruins")
        "pillager_outpost" -> tr("cobblemon-spawning-rei.preset.pillager_outpost")
        "stronghold" -> tr("cobblemon-spawning-rei.preset.stronghold")
        "trail_ruins" -> tr("cobblemon-spawning-rei.preset.trail_ruins")
        else -> titleCase(preset)
    }

    // --- Spawn merge ---

    fun mergeVariantSpawns(spawns: List<SpawnInfo>): List<MergedSpawn> {
        val groups = spawns.groupBy { spawnMergeKey(it) }
        return groups.map { (_, group) ->
            val primary = group.first()
            val variants = group
                .filter { it.formAspects.isNotBlank() }
                .map {
                    it.formAspects
                        .split(" ")
                        .filter { w -> w.isNotBlank() }
                        .joinToString(", ") { w -> formatAspect(w) }
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
        val weatherData = spawn.weather
        val weatherText = weatherData.displayText
        if (weatherData.isRaining != null || weatherData.isThundering != null) {
            val icon = when {
                weatherData.isThundering == true -> "\u26A1 "
                weatherData.isRaining == true -> "\u2602 "
                weatherData.isRaining == false -> "\u2600 "
                else -> ""
            }
            list.add("$icon$weatherText")
        }
        if (spawn.canSeeSky == true) list.add(tr("cobblemon-spawning-rei.spawn.cond.open_sky"))
        if (spawn.canSeeSky == false) list.add(tr("cobblemon-spawning-rei.spawn.cond.underground"))
        if (spawn.minSkyLight != null || spawn.maxSkyLight != null) {
            val min = spawn.minSkyLight ?: 0
            val max = spawn.maxSkyLight ?: 15
            when {
                min == 0 && max <= 7 -> list.add(tr("cobblemon-spawning-rei.spawn.cond.dark", max))
                min >= 8 -> list.add(tr("cobblemon-spawning-rei.spawn.cond.bright", min))
                else -> list.add(tr("cobblemon-spawning-rei.spawn.cond.sky_light_range", min, max))
            }
        }
        if (spawn.minLight != null || spawn.maxLight != null) {
            val min = spawn.minLight ?: 0
            val max = spawn.maxLight ?: 15
            if (max == 0) list.add(tr("cobblemon-spawning-rei.spawn.cond.no_light")) else list.add(tr("cobblemon-spawning-rei.spawn.cond.light_range", min, max))
        }
        if (spawn.minY != null || spawn.maxY != null) {
            when {
                spawn.minY != null && spawn.maxY != null -> list.add(tr("cobblemon-spawning-rei.spawn.cond.y_range", spawn.minY!!, spawn.maxY!!))
                spawn.minY != null -> list.add(tr("cobblemon-spawning-rei.spawn.cond.y_min", spawn.minY!!))
                spawn.maxY != null -> list.add(tr("cobblemon-spawning-rei.spawn.cond.y_max", spawn.maxY!!))
            }
        }
        spawn.moonPhase?.let { list.add(tr("cobblemon-spawning-rei.spawn.cond.moon", titleCase(it))) }
        if (spawn.isFishing) {
            val lure = spawn.minLureLevel
            if (lure != null && lure > 0) list.add(tr("cobblemon-spawning-rei.spawn.cond.fishing_lure", lure)) else list.add(tr("cobblemon-spawning-rei.spawn.cond.fishing"))
        }
        return list
    }

    fun buildSpecials(spawn: SpawnInfo): List<String> {
        val list = mutableListOf<String>()
        val structNames = spawn.structures.map { formatId(it) }.toSet()
        if (structNames.isNotEmpty()) {
            list.add(tr("cobblemon-spawning-rei.spawn.special.structure", structNames.joinToString(", ")))
        }
        if (spawn.dimensions.isNotEmpty()) {
            list.add(tr("cobblemon-spawning-rei.spawn.special.dimension", spawn.dimensions.joinToString(", ") { formatDimension(it) }))
        }
        spawn.fluid?.let {
            val name = when {
                it.contains("water") -> tr("cobblemon-spawning-rei.fluid.water")
                it.contains("lava") -> tr("cobblemon-spawning-rei.fluid.lava")
                else -> formatId(it)
            }
            list.add(tr("cobblemon-spawning-rei.spawn.special.in_fluid", name))
        }
        if (spawn.neededBaseBlocks.isNotEmpty()) {
            val names = spawn.neededBaseBlocks.map { formatId(it) }
            val redundant = structNames.isNotEmpty() && names.all { it.lowercase().contains("structure") }
            if (!redundant) list.add(tr("cobblemon-spawning-rei.spawn.special.spawns_on", names.joinToString(", ")))
        }
        if (spawn.neededNearbyBlocks.isNotEmpty()) {
            val names = spawn.neededNearbyBlocks.map { formatId(it) }
            val redundant = structNames.isNotEmpty() && names.all { it.lowercase().contains("structure") }
            if (!redundant) list.add(tr("cobblemon-spawning-rei.spawn.special.near", names.joinToString(", ")))
        }
        return list
    }

    fun buildExclusionLines(anti: SpawnAntiCondition): List<String> {
        val lines = mutableListOf<String>()
        if (anti.biomes.isNotEmpty()) {
            lines.add(tr("cobblemon-spawning-rei.spawn.excluded.biomes", anti.biomes.map { formatBiomeName(it) }.joinToString(", ")))
        }
        if (anti.structures.isNotEmpty()) {
            lines.add(tr("cobblemon-spawning-rei.spawn.excluded.structures", anti.structures.map { formatId(it) }.joinToString(", ")))
        }
        if (anti.minY != null || anti.maxY != null) {
            val r = listOfNotNull(anti.minY?.let { "Y \u2265 $it" }, anti.maxY?.let { "Y \u2264 $it" })
            lines.add(tr("cobblemon-spawning-rei.spawn.excluded.height", r.joinToString(", ")))
        }
        return lines
    }

    // --- Formatting ---

    fun formatWeight(weight: Float): String =
        if (weight == weight.toLong().toFloat()) weight.toLong().toString() else "%.1f".format(weight)

    fun formatDimension(dim: String): String = when (dim.lowercase()) {
        "minecraft:overworld" -> tr("cobblemon-spawning-rei.dimension.overworld")
        "minecraft:the_nether" -> tr("cobblemon-spawning-rei.dimension.nether")
        "minecraft:the_end" -> tr("cobblemon-spawning-rei.dimension.the_end")
        else -> formatId(dim)
    }

    fun formatFormAspects(aspects: String): String =
        aspects.split(" ").filter { it.isNotBlank() }.joinToString(", ") { formatAspect(it) }

    // --- Text layout ---

    fun clip(text: String, maxLen: Int): String =
        if (text.length > maxLen) text.take(maxLen - 1) + "\u2026" else text

    fun clipToWidth(font: net.minecraft.client.gui.Font, text: String, maxWidth: Int): String {
        if (maxWidth <= 0) return ""
        if (font.width(text) <= maxWidth) return text
        val ellipsis = "\u2026"
        val ellipsisW = font.width(ellipsis)
        if (maxWidth <= ellipsisW) return ellipsis
        var end = text.length
        while (end > 0) {
            val sub = text.substring(0, end)
            if (font.width(sub) + ellipsisW <= maxWidth) return sub + ellipsis
            end--
        }
        return ellipsis
    }

    fun wrapToWidth(font: net.minecraft.client.gui.Font, text: String, maxWidth: Int): List<String> {
        if (font.width(text) <= maxWidth) return listOf(text)
        val items = text.split(", ")
        val lines = mutableListOf<String>()
        var current = ""
        for (item in items) {
            val next = if (current.isEmpty()) item else "$current, $item"
            if (font.width(next) > maxWidth && current.isNotEmpty()) {
                lines.add(current)
                current = item
            } else {
                current = next
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        return if (lines.isEmpty()) listOf(text) else lines
    }

    fun wrapText(font: net.minecraft.client.gui.Font, text: String, maxWidth: Int): List<String> {
        if (maxWidth <= 0) return listOf(text)
        if (font.width(text) <= maxWidth) return listOf(text)
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val next = if (current.isEmpty()) word else "$current $word"
            if (font.width(next) > maxWidth && current.isNotEmpty()) {
                lines.add(current)
                current = word
            } else {
                current = next
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        return if (lines.isEmpty()) listOf(text) else lines
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
            parts.add(spawn.presets.map { presetLabel(it) }.joinToString(", "))
        }
        if (mergedFormVariants.isNotEmpty()) {
            parts.add(tr("cobblemon-spawning-rei.spawn.forms", mergedFormVariants.joinToString(", ")))
        } else if (spawn.hasFormVariant) {
            parts.add(tr("cobblemon-spawning-rei.spawn.form", formatFormAspects(spawn.formAspects)))
        }
        return parts
    }

    // --- Tooltip builder (shared across REI/JEI) ---

    fun buildPokemonTooltipLines(speciesName: String, displayName: String): List<Component> {
        val lines = mutableListOf<Component>()
        lines.add(Component.literal(displayName))
        val species = PokemonItemCache.resolveSpecies(speciesName)
        if (species != null) {
            lines.add(Component.literal("§7" + tr("cobblemon-spawning-rei.tooltip.pokedex_number", species.nationalPokedexNumber)))
        }
        val info = SpawnDataIndex.getSpeciesInfo(speciesName)
        if (info != null) {
            val typeStr = buildString {
                append("§e")
                append(formatTypeName(info.primaryType))
                info.secondaryType?.let { append(" §7/ §e${formatTypeName(it)}") }
            }
            lines.add(Component.literal(typeStr))

            // Labels (Legendary, Mythical, etc.)
            val labelBadges = info.labels?.filter {
                it in setOf("legendary", "mythical", "ultra_beast", "paradox")
            }
            if (!labelBadges.isNullOrEmpty()) {
                val badge = labelBadges.joinToString(", ") { "§d${titleCase(it.replace("_", " "))}" }
                lines.add(Component.literal(badge))
            }

            lines.add(Component.literal("§7" + tr("cobblemon-spawning-rei.tooltip.catch_rate", info.catchRate)))

            // BST
            info.baseStatTotal?.let { bst ->
                lines.add(Component.literal("§7" + tr("cobblemon-spawning-rei.tooltip.bst", bst)))
            }

            // Abilities
            val abilityText = buildString {
                info.abilities?.let { abs ->
                    append("§b")
                    append(abs.joinToString(", "))
                }
                info.hiddenAbility?.let { ha ->
                    if (isNotEmpty()) append(" §7| ")
                    append("§3$ha §7(HA)")
                }
            }
            if (abilityText.isNotEmpty()) {
                lines.add(Component.literal(abilityText))
            }

            // Egg Groups
            info.eggGroups?.let { groups ->
                if (groups.isNotEmpty()) {
                    lines.add(Component.literal("§7" + tr("cobblemon-spawning-rei.tooltip.egg_groups", groups.joinToString(", "))))
                }
            }
        }
        val spawns = SpawnDataIndex.getSpawnsFor(speciesName)
        if (spawns.isNotEmpty()) {
            val displayCount = buildSortedSpawns(spawns).size
            lines.add(Component.literal("§a" + tr("cobblemon-spawning-rei.tooltip.spawn_count", displayCount)))
        }
        val evosFrom = SpawnDataIndex.getEvolutionsFrom(speciesName)
        val evosTo = SpawnDataIndex.getEvolutionsTo(speciesName)
        val evoCount = evosFrom.size + evosTo.size
        if (evoCount > 0) {
            lines.add(Component.literal("§6" + tr("cobblemon-spawning-rei.tooltip.evolution_count", evoCount)))
        }
        // Drop count
        val dropCount = info?.drops?.size ?: 0
        if (dropCount > 0) {
            lines.add(Component.literal("§b" + tr("cobblemon-spawning-rei.tooltip.drop_count", dropCount)))
        }
        val obtainments = SpawnDataIndex.getObtainmentFor(speciesName)
        if (obtainments.isNotEmpty()) {
            lines.add(Component.literal("§d" + tr("cobblemon-spawning-rei.tooltip.obtainment_count", obtainments.size)))
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
        val indentX = padding + 6
        val indentWidth = right - indentX

        graphics.drawString(font, formatSpeciesName(speciesName), padding + 22, 6, 0xFFFFFF, false)

        val lvText = levelText(spawn.levelRange)
        val bucketText = bucketLabel(spawn.bucket)
        val bucketWidth = font.width(bucketText)
        graphics.drawString(font, lvText, padding, 22, 0x0099FF, false)
        graphics.drawString(font, bucketText, right - bucketWidth, 22, color, false)

        graphics.fill(padding, 36, right, 37, 0x50FFFFFF)
        var y = 42

        val ctxParts = buildContextParts(spawn, mergedFormVariants)
        val showWeights = CobblemonSpawningConfig.get().showSpawnWeights && spawn.weight > 0f
        if (showWeights) {
            val wtText = weightText(spawn.weight)
            val wtWidth = font.width(wtText)
            graphics.drawString(font, wtText, right - wtWidth, y, 0xBBBBBB, false)
            if (ctxParts.isNotEmpty()) {
                val ctxMax = right - wtWidth - (padding + 4) - 6
                for (line in wrapText(font, ctxParts.joinToString(" \u00B7 "), ctxMax)) {
                    graphics.drawString(font, line, padding + 4, y, 0xDDDDDD, false)
                    y += lineHeight
                }
            } else {
                y += lineHeight
            }
        } else if (ctxParts.isNotEmpty()) {
            for (line in wrapText(font, ctxParts.joinToString(" \u00B7 "), right - padding - 4)) {
                graphics.drawString(font, line, padding + 4, y, 0xDDDDDD, false)
                y += lineHeight
            }
        } else {
            y += lineHeight
        }
        y += 4

        val biomeNames = spawn.biomes.map { formatBiomeName(it) }
        if (biomeNames.isNotEmpty()) {
            val header = if (biomeNames.size > 1) tr("cobblemon-spawning-rei.spawn.section.biomes") else tr("cobblemon-spawning-rei.spawn.section.biome")
            graphics.drawString(font, header, padding, y, 0xEEEEEE, false)
            y += lineHeight
            for (line in wrapToWidth(font, biomeNames.joinToString(", "), indentWidth)) {
                graphics.drawString(font, line, indentX, y, 0xDDDDDD, false)
                y += lineHeight
            }
            y += 3
        }

        val conditions = buildConditions(spawn)
        if (conditions.isNotEmpty()) {
            graphics.drawString(font, tr("cobblemon-spawning-rei.spawn.section.conditions"), padding, y, 0xEEEEEE, false)
            y += lineHeight
            for (cond in conditions) {
                for (line in wrapText(font, cond, indentWidth)) {
                    graphics.drawString(font, line, indentX, y, 0xDDDDDD, false)
                    y += lineHeight
                }
            }
            y += 3
        }

        val specials = buildSpecials(spawn)
        if (specials.isNotEmpty()) {
            graphics.drawString(font, tr("cobblemon-spawning-rei.spawn.section.location"), padding, y, 0xEEEEEE, false)
            y += lineHeight
            for (s in specials) {
                for (line in wrapText(font, s, indentWidth)) {
                    graphics.drawString(font, line, indentX, y, 0xFFCC66, false)
                    y += lineHeight
                }
            }
            y += 3
        }

        val anti = spawn.anticondition
        if (anti != null && !anti.isEmpty) {
            val exLines = buildExclusionLines(anti)
            if (exLines.isNotEmpty()) {
                graphics.drawString(font, tr("cobblemon-spawning-rei.spawn.section.excluded"), padding, y, 0xFF7777, false)
                y += lineHeight
                for (line in exLines) {
                    for (wrapped in wrapText(font, line, indentWidth)) {
                        graphics.drawString(font, wrapped, indentX, y, 0xEE8888, false)
                        y += lineHeight
                    }
                }
                y += 3
            }
        }

        if (CobblemonSpawningConfig.get().showSpawnWeights && spawn.weightMultipliers.isNotEmpty()) {
            graphics.drawString(font, tr("cobblemon-spawning-rei.spawn.section.weight_mods"), padding, y, 0xEEEEEE, false)
            y += lineHeight
            for (wm in spawn.weightMultipliers) {
                val arrow: String
                val c: Int
                when {
                    wm.multiplier > 1f -> { arrow = "\u25B2"; c = 0x88DD88 }
                    wm.multiplier < 1f -> { arrow = "\u25BC"; c = 0xEE8888 }
                    else -> { arrow = "\u25CF"; c = 0xBBBBBB }
                }
                val wmText = "$arrow ${formatWeight(wm.multiplier)}x ${wm.conditionSummary}"
                for (line in wrapText(font, wmText, indentWidth)) {
                    graphics.drawString(font, line, indentX, y, c, false)
                    y += lineHeight
                }
            }
        }

        y += 1
        graphics.fill(padding, y, right, y + 1, 0x20FFFFFF)
        y += 4
        val footerLeft = "${bucketLabel(spawn.bucket)} $bucketIndex/$bucketTotal"
        graphics.drawString(font, footerLeft, padding, y, color, false)
    }

    // --- Obtainment detail rendering (shared between JEI/EMI) ---

    fun drawObtainmentDetails(
        graphics: GuiGraphics,
        speciesName: String,
        obtainment: ObtainmentInfo,
        entryIndex: Int,
        entryTotal: Int,
        width: Int = 180,
        height: Int = 150,
        padding: Int = 6,
        lineHeight: Int = 11
    ) {
        val font = Minecraft.getInstance().font
        val right = width - padding
        val indentX = padding + 4
        val indentWidth = right - indentX

        val methodText = obtainment.displayMethodName
        val methodWidth = font.width(methodText)
        graphics.drawString(font, formatSpeciesName(speciesName), padding + 22, 6, 0xFFFFFF, false)
        graphics.drawString(font, methodText, right - methodWidth, 6, 0xDDCC99, false)

        graphics.fill(padding, 20, right, 21, 0x50FFFFFF)
        var y = 26

        for (line in wrapText(font, obtainment.displayDescription, indentWidth)) {
            graphics.drawString(font, line, indentX, y, 0xEEEEEE, false)
            y += lineHeight
        }
        y += 4

        if (obtainment.items.isNotEmpty()) {
            graphics.drawString(font, tr("cobblemon-spawning-rei.obtainment.required_items"), padding, y, 0xEEEEEE, false)
            y += lineHeight
            for (item in obtainment.displayItems) {
                for (line in wrapText(font, "\u2022 $item", indentWidth)) {
                    graphics.drawString(font, line, padding + 6, y, 0xFFCC66, false)
                    y += lineHeight
                }
            }
            y += 4
        }

        if (obtainment.displayBlock != null || obtainment.displayStructure != null || obtainment.displayDimension != null) {
            graphics.drawString(font, tr("cobblemon-spawning-rei.spawn.section.location"), padding, y, 0xEEEEEE, false)
            y += lineHeight
            obtainment.displayBlock?.let {
                for (line in wrapText(font, obtainmentUseText(it), indentWidth)) {
                    graphics.drawString(font, line, padding + 6, y, 0xDDDDDD, false)
                    y += lineHeight
                }
            }
            obtainment.displayStructure?.let {
                for (line in wrapText(font, obtainmentStructureText(it), indentWidth)) {
                    graphics.drawString(font, line, padding + 6, y, 0xDDDDDD, false)
                    y += lineHeight
                }
            }
            obtainment.displayDimension?.let {
                for (line in wrapText(font, obtainmentDimensionText(it), indentWidth)) {
                    graphics.drawString(font, line, padding + 6, y, 0xDDDDDD, false)
                    y += lineHeight
                }
            }
            y += 4
        }

        for (note in obtainment.notes) {
            for (line in wrapText(font, "\u2139 $note", indentWidth)) {
                graphics.drawString(font, line, indentX, y, 0xBBBBBB, false)
                y += lineHeight
            }
        }

        y += 1
        graphics.fill(padding, y, right, y + 1, 0x20FFFFFF)
        y += 4
        if (entryTotal > 1) {
            graphics.drawString(font, "$entryIndex/$entryTotal", padding, y, 0xFFAA00, false)
        }

        val srcLabel = sourceLabel(obtainment.source)
        if (srcLabel.isNotEmpty()) {
            val sw = font.width(srcLabel)
            graphics.drawString(font, srcLabel, right - sw, y, 0xBBBBBB, false)
        }
    }

    // --- Item resolution ---

    fun resolveItemStack(itemId: String): ItemStack {
        val rl = ResourceLocation.tryParse(itemId) ?: return ItemStack.EMPTY
        return BuiltInRegistries.ITEM.getOptional(rl)
            .map { ItemStack(it) }
            .orElse(ItemStack.EMPTY)
    }

    fun resolveItemName(itemId: String): String {
        val stack = resolveItemStack(itemId)
        return if (!stack.isEmpty) stack.hoverName.string else formatItemIdFallback(itemId)
    }

    private fun formatItemIdFallback(itemId: String): String {
        val name = if (itemId.contains(":")) itemId.substringAfter(":") else itemId
        return name.replace("_", " ").split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    // --- Drop detail rendering (shared between REI/JEI/EMI) ---

    fun drawDropDetails(
        graphics: GuiGraphics,
        speciesName: String,
        drops: List<DropEntryInfo>,
        width: Int = 180,
        height: Int = 150,
        padding: Int = 6,
        lineHeight: Int = 11
    ) {
        val font = Minecraft.getInstance().font
        val right = width - padding

        graphics.drawString(font, formatSpeciesName(speciesName), padding + 22, 6, 0xFFFFFF, false)

        val headerText = tr("category.cobblemon-spawning-rei.drops")
        val headerWidth = font.width(headerText)
        graphics.drawString(font, headerText, right - headerWidth, 6, 0xDDCC99, false)

        graphics.fill(padding, 20, right, 21, 0x50FFFFFF)

        val labelHeader = tr("cobblemon-spawning-rei.drops.header")
        graphics.drawString(font, labelHeader, padding, 26, 0xEEEEEE, false)

        var y = 38

        for (drop in drops) {
            val itemName = resolveItemName(drop.itemId)
            val qtyText = "\u00D7${drop.displayQuantity}"
            val pctText = drop.displayPercentage

            val nameX = padding + 22
            val rightInfo = "$pctText $qtyText"
            val rightInfoWidth = font.width(rightInfo)
            val nameMaxWidth = right - nameX - rightInfoWidth - 4

            val clippedName = clipToWidth(font, itemName, nameMaxWidth)
            graphics.drawString(font, clippedName, nameX, y + 4, 0xFFFFFF, false)
            graphics.drawString(font, rightInfo, right - rightInfoWidth, y + 4, 0xBBBBBB, false)

            y += 20
        }

        y += 2
        graphics.fill(padding, y, right, y + 1, 0x20FFFFFF)
        y += 4
        val countText = tr("cobblemon-spawning-rei.drops.count", drops.size)
        graphics.drawString(font, countText, padding, y, 0x888888, false)
    }

    // --- Evolution text rendering (shared between REI/JEI/EMI) ---

    fun drawEvolutionText(
        graphics: GuiGraphics,
        evolution: EvolutionInfo,
        branchIndex: Int,
        branchTotal: Int,
        width: Int = 180,
        height: Int = 120,
        slotSize: Int = 18,
        hasItemSlots: Boolean = false
    ) {
        val font = Minecraft.getInstance().font
        val centerX = width / 2
        val padding = 6
        val right = width - padding

        val fromCenterX = 20 + slotSize / 2
        val fromName = evolution.displayFromName
        val fromWidth = font.width(fromName)
        graphics.drawString(font, fromName, fromCenterX - fromWidth / 2, 30, 0xFFFFFF, false)

        val dirText = "\u2192"
        val dirW = font.width(dirText)
        graphics.drawString(font, dirText, centerX - dirW / 2, 30, 0x888888, false)

        val toCenterX = width - 20 - slotSize / 2
        val toName = evolution.displayToName
        val toWidth = font.width(toName)
        graphics.drawString(font, toName, toCenterX - toWidth / 2, 30, 0xFFFFFF, false)

        graphics.fill(padding, 42, right, 43, 0x40FFFFFF)

        val items = evolution.itemRequirements
        var contentY = 48

        if (hasItemSlots && items.isNotEmpty()) {
            val labelX = 30
            val labelMaxW = right - labelX
            for (item in items) {
                val stack = resolveItemStack(item.itemId)
                val name = if (!stack.isEmpty) stack.hoverName.string else formatItemIdFallback(item.itemId)
                val labelText = "${item.label} "
                val labelWidth = font.width(labelText)
                graphics.drawString(font, labelText, labelX, contentY + 4, 0xBBBBBB, false)
                val nameLines = wrapText(font, name, labelMaxW - labelWidth)
                for ((lineIdx, line) in nameLines.withIndex()) {
                    graphics.drawString(font, line, labelX + if (lineIdx == 0) labelWidth else 0, contentY + 4 + lineIdx * 12, 0xFFFFFF, false)
                }
                contentY += 20
            }
        }

        val reqText = if (hasItemSlots) evolution.textOnlyRequirements else evolution.displayRequirements
        if (reqText.isNotBlank()) {
            val reqWidth = right - padding
            for (line in wrapToWidth(font, reqText, reqWidth)) {
                val lw = font.width(line)
                graphics.drawString(font, line, centerX - lw / 2, contentY, 0xFFDD88, false)
                contentY += 12
            }
        }

        if (branchTotal > 1) {
            contentY += 2
            val branchText = evoBranchText(branchIndex, branchTotal)
            val bw = font.width(branchText)
            graphics.drawString(font, branchText, right - bw, contentY, 0x666666, false)
        }
    }
}
