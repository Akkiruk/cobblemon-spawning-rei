package com.cobbledex

import com.cobbledex.config.CobbleDexConfig
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
        "common" -> tr("cobbledex-rei-emi-jei.bucket.common")
        "uncommon" -> tr("cobbledex-rei-emi-jei.bucket.uncommon")
        "rare" -> tr("cobbledex-rei-emi-jei.bucket.rare")
        "ultra-rare" -> tr("cobbledex-rei-emi-jei.bucket.ultra_rare")
        else -> titleCase(bucket)
    }

    fun bucketSortOrder(bucket: String): Int =
        BUCKET_ORDER.indexOf(bucket.lowercase()).let { if (it < 0) 99 else it }

    fun presetLabel(preset: String): String = when (preset.lowercase()) {
        "natural" -> tr("cobbledex-rei-emi-jei.preset.natural")
        "water" -> tr("cobbledex-rei-emi-jei.preset.water")
        "lava" -> tr("cobbledex-rei-emi-jei.preset.lava")
        "urban" -> tr("cobbledex-rei-emi-jei.preset.urban")
        "wild" -> tr("cobbledex-rei-emi-jei.preset.wild")
        "foliage" -> tr("cobbledex-rei-emi-jei.preset.foliage")
        "treetop" -> tr("cobbledex-rei-emi-jei.preset.treetop")
        "derelict" -> tr("cobbledex-rei-emi-jei.preset.derelict")
        "redstone" -> tr("cobbledex-rei-emi-jei.preset.redstone")
        "ancient_city" -> tr("cobbledex-rei-emi-jei.preset.ancient_city")
        "desert_pyramid" -> tr("cobbledex-rei-emi-jei.preset.desert_pyramid")
        "end_city" -> tr("cobbledex-rei-emi-jei.preset.end_city")
        "jungle_pyramid" -> tr("cobbledex-rei-emi-jei.preset.jungle_pyramid")
        "mansion" -> tr("cobbledex-rei-emi-jei.preset.mansion")
        "nether_fossil" -> tr("cobbledex-rei-emi-jei.preset.nether_fossil")
        "nether_structures" -> tr("cobbledex-rei-emi-jei.preset.nether_structures")
        "ocean_monument" -> tr("cobbledex-rei-emi-jei.preset.ocean_monument")
        "ocean_ruins" -> tr("cobbledex-rei-emi-jei.preset.ocean_ruins")
        "pillager_outpost" -> tr("cobbledex-rei-emi-jei.preset.pillager_outpost")
        "stronghold" -> tr("cobbledex-rei-emi-jei.preset.stronghold")
        "trail_ruins" -> tr("cobbledex-rei-emi-jei.preset.trail_ruins")
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
        if (spawn.canSeeSky == true) list.add(tr("cobbledex-rei-emi-jei.spawn.cond.open_sky"))
        if (spawn.canSeeSky == false) list.add(tr("cobbledex-rei-emi-jei.spawn.cond.underground"))
        if (spawn.minSkyLight != null || spawn.maxSkyLight != null) {
            val min = spawn.minSkyLight ?: 0
            val max = spawn.maxSkyLight ?: 15
            when {
                min == 0 && max <= 7 -> list.add(tr("cobbledex-rei-emi-jei.spawn.cond.dark", max))
                min >= 8 -> list.add(tr("cobbledex-rei-emi-jei.spawn.cond.bright", min))
                else -> list.add(tr("cobbledex-rei-emi-jei.spawn.cond.sky_light_range", min, max))
            }
        }
        if (spawn.minLight != null || spawn.maxLight != null) {
            val min = spawn.minLight ?: 0
            val max = spawn.maxLight ?: 15
            if (max == 0) list.add(tr("cobbledex-rei-emi-jei.spawn.cond.no_light")) else list.add(tr("cobbledex-rei-emi-jei.spawn.cond.light_range", min, max))
        }
        if (spawn.minY != null || spawn.maxY != null) {
            when {
                spawn.minY != null && spawn.maxY != null -> list.add(tr("cobbledex-rei-emi-jei.spawn.cond.y_range", spawn.minY!!, spawn.maxY!!))
                spawn.minY != null -> list.add(tr("cobbledex-rei-emi-jei.spawn.cond.y_min", spawn.minY!!))
                spawn.maxY != null -> list.add(tr("cobbledex-rei-emi-jei.spawn.cond.y_max", spawn.maxY!!))
            }
        }
        spawn.moonPhase?.let { list.add(tr("cobbledex-rei-emi-jei.spawn.cond.moon", titleCase(it))) }
        if (spawn.isFishing) {
            val lure = spawn.minLureLevel
            if (lure != null && lure > 0) list.add(tr("cobbledex-rei-emi-jei.spawn.cond.fishing_lure", lure)) else list.add(tr("cobbledex-rei-emi-jei.spawn.cond.fishing"))
        }
        return list
    }

    fun buildSpecials(spawn: SpawnInfo): List<String> {
        val list = mutableListOf<String>()
        val structNames = spawn.structures.map { formatId(it) }.toSet()
        if (structNames.isNotEmpty()) {
            list.add(tr("cobbledex-rei-emi-jei.spawn.special.structure", structNames.joinToString(", ")))
        }
        if (spawn.dimensions.isNotEmpty()) {
            list.add(tr("cobbledex-rei-emi-jei.spawn.special.dimension", spawn.dimensions.joinToString(", ") { formatDimension(it) }))
        }
        spawn.fluid?.let {
            val name = when {
                it.contains("water") -> tr("cobbledex-rei-emi-jei.fluid.water")
                it.contains("lava") -> tr("cobbledex-rei-emi-jei.fluid.lava")
                else -> formatId(it)
            }
            list.add(tr("cobbledex-rei-emi-jei.spawn.special.in_fluid", name))
        }
        if (spawn.neededBaseBlocks.isNotEmpty()) {
            val names = spawn.neededBaseBlocks.map { formatId(it) }
            val redundant = structNames.isNotEmpty() && names.all { it.lowercase().contains("structure") }
            if (!redundant) list.add(tr("cobbledex-rei-emi-jei.spawn.special.spawns_on", names.joinToString(", ")))
        }
        if (spawn.neededNearbyBlocks.isNotEmpty()) {
            val names = spawn.neededNearbyBlocks.map { formatId(it) }
            val redundant = structNames.isNotEmpty() && names.all { it.lowercase().contains("structure") }
            if (!redundant) list.add(tr("cobbledex-rei-emi-jei.spawn.special.near", names.joinToString(", ")))
        }
        return list
    }

    fun buildExclusionLines(anti: SpawnAntiCondition): List<String> {
        val lines = mutableListOf<String>()
        if (anti.biomes.isNotEmpty()) {
            lines.add(tr("cobbledex-rei-emi-jei.spawn.excluded.biomes", anti.biomes.map { formatBiomeName(it) }.joinToString(", ")))
        }
        if (anti.structures.isNotEmpty()) {
            lines.add(tr("cobbledex-rei-emi-jei.spawn.excluded.structures", anti.structures.map { formatId(it) }.joinToString(", ")))
        }
        if (anti.minY != null || anti.maxY != null) {
            val r = listOfNotNull(anti.minY?.let { "Y \u2265 $it" }, anti.maxY?.let { "Y \u2264 $it" })
            lines.add(tr("cobbledex-rei-emi-jei.spawn.excluded.height", r.joinToString(", ")))
        }
        if (anti.timeRange != null) {
            lines.add(tr("cobbledex-rei-emi-jei.spawn.excluded.time", titleCase(anti.timeRange)))
        }
        if (anti.dimensions.isNotEmpty()) {
            lines.add(tr("cobbledex-rei-emi-jei.spawn.excluded.dimensions", anti.dimensions.map { formatDimension(it) }.joinToString(", ")))
        }
        if (anti.isThundering == true) {
            lines.add(tr("cobbledex-rei-emi-jei.spawn.excluded.weather", tr("cobbledex-rei-emi-jei.weather.thunder")))
        } else if (anti.isRaining == true) {
            lines.add(tr("cobbledex-rei-emi-jei.spawn.excluded.weather", tr("cobbledex-rei-emi-jei.weather.rain")))
        } else if (anti.isRaining == false) {
            lines.add(tr("cobbledex-rei-emi-jei.spawn.excluded.weather", tr("cobbledex-rei-emi-jei.weather.clear")))
        }
        if (anti.minLight != null || anti.maxLight != null) {
            val r = listOfNotNull(anti.minLight?.let { "\u2265 $it" }, anti.maxLight?.let { "\u2264 $it" })
            lines.add(tr("cobbledex-rei-emi-jei.spawn.excluded.light", r.joinToString(", ")))
        }
        if (anti.moonPhase != null) {
            lines.add(tr("cobbledex-rei-emi-jei.spawn.excluded.moon", titleCase(anti.moonPhase)))
        }
        return lines
    }

    // --- Formatting ---

    fun formatWeight(weight: Float): String =
        if (weight == weight.toLong().toFloat()) weight.toLong().toString() else "%.1f".format(weight)

    fun formatDimension(dim: String): String = when (dim.lowercase()) {
        "minecraft:overworld" -> tr("cobbledex-rei-emi-jei.dimension.overworld")
        "minecraft:the_nether" -> tr("cobbledex-rei-emi-jei.dimension.nether")
        "minecraft:the_end" -> tr("cobbledex-rei-emi-jei.dimension.the_end")
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
            parts.add(tr("cobbledex-rei-emi-jei.spawn.forms", mergedFormVariants.joinToString(", ")))
        } else if (spawn.hasFormVariant) {
            parts.add(tr("cobbledex-rei-emi-jei.spawn.form", formatFormAspects(spawn.formAspects)))
        }
        return parts
    }

    // --- Tooltip builder (shared across REI/JEI) ---

    fun buildPokemonTooltipLines(speciesName: String, displayName: String): List<Component> {
        val lines = mutableListOf<Component>()
        lines.add(Component.literal(displayName))
        val species = PokemonItemCache.resolveSpecies(speciesName)
        if (species != null) {
            lines.add(Component.literal("§7" + tr("cobbledex-rei-emi-jei.tooltip.pokedex_number", species.nationalPokedexNumber)))
        }
        val info = SpawnDataIndex.getSpeciesInfo(speciesName)
        if (info != null) {
            val typeStr = buildString {
                append("§e")
                append(formatTypeName(info.primaryType))
                info.secondaryType?.let { append(" §7/ §e${formatTypeName(it)}") }
            }
            lines.add(Component.literal(typeStr))

            val labelBadges = info.labels?.filter {
                it in setOf("legendary", "mythical", "ultra_beast", "paradox")
            }
            if (!labelBadges.isNullOrEmpty()) {
                val badge = labelBadges.joinToString(", ") {
                    "§d" + tr("cobbledex-rei-emi-jei.label.${it}")
                }
                lines.add(Component.literal(badge))
            }
        }

        val counts = mutableListOf<String>()
        val spawns = SpawnDataIndex.getSpawnsFor(speciesName)
        if (spawns.isNotEmpty()) counts.add("§a" + tr("cobbledex-rei-emi-jei.tooltip.spawns", buildSortedSpawns(spawns).size))
        val evosFrom = SpawnDataIndex.getEvolutionsFrom(speciesName)
        val evosTo = SpawnDataIndex.getEvolutionsTo(speciesName)
        val evoCount = evosFrom.size + evosTo.size
        if (evoCount > 0) counts.add("§6" + tr("cobbledex-rei-emi-jei.tooltip.evos", evoCount))
        val dropCount = info?.drops?.size ?: 0
        if (dropCount > 0) counts.add("§b" + tr("cobbledex-rei-emi-jei.tooltip.drops", dropCount))
        val obtainments = SpawnDataIndex.getObtainmentFor(speciesName)
        if (obtainments.isNotEmpty()) counts.add("§d" + tr("cobbledex-rei-emi-jei.tooltip.obtainment", obtainments.size))
        if (counts.isNotEmpty()) {
            lines.add(Component.literal(counts.joinToString(" §7| ")))
        }
        return lines
    }

    // --- Spawn layout builder (single source of truth for measure + render) ---

    fun buildSpawnLayout(
        speciesName: String,
        spawn: SpawnInfo,
        mergedFormVariants: List<String>,
        bucketIndex: Int,
        bucketTotal: Int
    ): PanelLayout {
        val font = Minecraft.getInstance().font
        val padding = PanelLayout.PADDING
        val lineHeight = PanelLayout.LINE_HEIGHT
        val showWeights = CobbleDexConfig.get().showSpawnWeights && spawn.weight > 0f

        val lvText = levelText(spawn.levelRange)
        val bucketText = bucketLabel(spawn.bucket)
        val ctxParts = buildContextParts(spawn, mergedFormVariants)
        val ctxText = ctxParts.joinToString(" \u00B7 ")
        val wtText = if (showWeights) weightText(spawn.weight) else ""
        val footerText = "$bucketText $bucketIndex/$bucketTotal"

        val nameWidth = PanelLayout.TEXT_START_X + font.width(formatSpeciesName(speciesName)) + padding
        val lvBucketWidth = padding + font.width(lvText) + 6 + font.width(bucketText) + padding
        val ctxRowWidth = if (ctxText.isNotEmpty() || wtText.isNotEmpty()) {
            padding + 4 + font.width(ctxText) + (if (wtText.isNotEmpty()) 6 + font.width(wtText) else 0) + padding
        } else 0
        val footerWidth = padding + font.width(footerText) + padding
        val width = maxOf(nameWidth, lvBucketWidth, ctxRowWidth, footerWidth, PanelLayout.MIN_WIDTH).coerceAtMost(PanelLayout.MAX_WIDTH)

        val layout = PanelLayout(width)
        val right = layout.right
        val indentX = PanelLayout.INDENT_X
        val indentWidth = right - indentX
        val color = bucketColor(spawn.bucket)

        layout.textAt(padding + 22, 6, formatSpeciesName(speciesName), 0xFFFFFF)
        layout.textAt(padding, 22, lvText, 0x0099FF)
        layout.textRightAt(22, bucketText, color)
        layout.fill(padding, 36, right, 37, 0x50FFFFFF)
        layout.skipTo(42)

        if (showWeights) {
            layout.textRight(wtText, 0xBBBBBB)
            if (ctxParts.isNotEmpty()) {
                val ctxMax = right - font.width(wtText) - (padding + 4) - 6
                layout.wrapped(padding + 4, ctxText, ctxMax, 0xDDDDDD)
            } else {
                layout.line()
            }
        } else if (ctxParts.isNotEmpty()) {
            layout.wrapped(padding + 4, ctxText, right - padding - 4, 0xDDDDDD)
        } else {
            layout.line()
        }
        layout.gap(4)

        val biomeNames = spawn.biomes.map { formatBiomeName(it) }
        if (biomeNames.isNotEmpty()) {
            val header = if (biomeNames.size > 1) tr("cobbledex-rei-emi-jei.spawn.section.biomes") else tr("cobbledex-rei-emi-jei.spawn.section.biome")
            layout.text(padding, header, 0xEEEEEE)
            layout.line()
            layout.wrappedCommas(indentX, biomeNames.joinToString(", "), indentWidth, 0xDDDDDD)
            layout.gap(PanelLayout.SECTION_GAP)
        }

        val conditions = buildConditions(spawn)
        if (conditions.isNotEmpty()) {
            layout.text(padding, tr("cobbledex-rei-emi-jei.spawn.section.conditions"), 0xEEEEEE)
            layout.line()
            for (cond in conditions) {
                layout.wrapped(indentX, cond, indentWidth, 0xDDDDDD)
            }
            layout.gap(PanelLayout.SECTION_GAP)
        }

        val specials = buildSpecials(spawn)
        if (specials.isNotEmpty()) {
            layout.text(padding, tr("cobbledex-rei-emi-jei.spawn.section.location"), 0xEEEEEE)
            layout.line()
            for (s in specials) {
                layout.wrapped(indentX, s, indentWidth, 0xFFCC66)
            }
            layout.gap(PanelLayout.SECTION_GAP)
        }

        val anti = spawn.anticondition
        if (anti != null && !anti.isEmpty) {
            val exLines = buildExclusionLines(anti)
            if (exLines.isNotEmpty()) {
                layout.text(padding, tr("cobbledex-rei-emi-jei.spawn.section.excluded"), 0xFF7777)
                layout.line()
                for (line in exLines) {
                    layout.wrapped(indentX, line, indentWidth, 0xEE8888)
                }
                layout.gap(PanelLayout.SECTION_GAP)
            }
        }

        if (CobbleDexConfig.get().showSpawnWeights && spawn.weightMultipliers.isNotEmpty()) {
            layout.text(padding, tr("cobbledex-rei-emi-jei.spawn.section.weight_mods"), 0xEEEEEE)
            layout.line()
            for (wm in spawn.weightMultipliers) {
                val arrow: String
                val c: Int
                when {
                    wm.multiplier > 1f -> { arrow = "\u25B2"; c = 0x88DD88 }
                    wm.multiplier < 1f -> { arrow = "\u25BC"; c = 0xEE8888 }
                    else -> { arrow = "\u25CF"; c = 0xBBBBBB }
                }
                val wmText = "$arrow ${formatWeight(wm.multiplier)}x ${wm.conditionSummary}"
                layout.wrapped(indentX, wmText, indentWidth, c)
            }
        }

        layout.gap(1)
        layout.separator(0x20FFFFFF)
        layout.gap(4)
        layout.text(padding, footerText, color)
        layout.gap(font.lineHeight + padding)

        return layout
    }

    // --- Obtainment layout builder ---

    fun buildObtainmentLayout(
        speciesName: String,
        obtainment: ObtainmentInfo,
        entryIndex: Int,
        entryTotal: Int
    ): PanelLayout {
        val font = Minecraft.getInstance().font
        val padding = PanelLayout.PADDING
        val lineHeight = PanelLayout.LINE_HEIGHT

        val methodText = obtainment.displayMethodName
        val headerWidth = PanelLayout.TEXT_START_X + font.width(formatSpeciesName(speciesName)) + 6 + font.width(methodText) + padding
        val width = maxOf(headerWidth, PanelLayout.MIN_WIDTH).coerceAtMost(PanelLayout.MAX_WIDTH)

        val layout = PanelLayout(width)
        val right = layout.right
        val indentX = padding + 4
        val indentWidth = right - indentX

        layout.textAt(padding + 22, 6, formatSpeciesName(speciesName), 0xFFFFFF)
        layout.textRightAt(6, methodText, 0xDDCC99)
        layout.fill(padding, 20, right, 21, 0x50FFFFFF)
        layout.skipTo(26)

        layout.wrapped(indentX, obtainment.displayDescription, indentWidth, 0xEEEEEE)
        layout.gap(4)

        if (obtainment.items.isNotEmpty()) {
            layout.text(padding, tr("cobbledex-rei-emi-jei.obtainment.required_items"), 0xEEEEEE)
            layout.line()
            for (item in obtainment.displayItems) {
                layout.wrapped(padding + 6, "\u2022 $item", indentWidth, 0xFFCC66)
            }
            layout.gap(4)
        }

        if (obtainment.displayBlock != null || obtainment.displayStructure != null || obtainment.displayDimension != null) {
            layout.text(padding, tr("cobbledex-rei-emi-jei.spawn.section.location"), 0xEEEEEE)
            layout.line()
            obtainment.displayBlock?.let { layout.wrapped(padding + 6, obtainmentUseText(it), indentWidth, 0xDDDDDD) }
            obtainment.displayStructure?.let { layout.wrapped(padding + 6, obtainmentStructureText(it), indentWidth, 0xDDDDDD) }
            obtainment.displayDimension?.let { layout.wrapped(padding + 6, obtainmentDimensionText(it), indentWidth, 0xDDDDDD) }
            layout.gap(4)
        }

        for (note in obtainment.notes) {
            layout.wrapped(indentX, "\u2139 $note", indentWidth, 0xBBBBBB)
        }

        layout.gap(1)
        layout.separator(0x20FFFFFF)
        layout.gap(4)
        if (entryTotal > 1) {
            layout.text(padding, "$entryIndex/$entryTotal", 0xFFAA00)
        }
        val srcLabel = sourceLabel(obtainment.source)
        if (srcLabel.isNotEmpty()) {
            layout.textRight(srcLabel, 0xBBBBBB)
        }
        layout.gap(font.lineHeight + padding)

        return layout
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

    // --- Drop layout builder ---

    fun buildDropLayout(
        speciesName: String,
        drops: List<DropEntryInfo>
    ): PanelLayout {
        val font = Minecraft.getInstance().font
        val padding = PanelLayout.PADDING

        val nameWidth = PanelLayout.TEXT_START_X + font.width(formatSpeciesName(speciesName)) + padding
        val headerTag = tr("category.cobbledex-rei-emi-jei.drops")
        val headerWidth = nameWidth + 6 + font.width(headerTag) + padding

        var maxItemRowWidth = 0
        for (drop in drops) {
            val itemName = resolveItemName(drop.itemId)
            val rightText = "${drop.displayPercentage} \u00D7${drop.displayQuantity}"
            val rowWidth = padding + 22 + font.width(itemName) + 8 + font.width(rightText) + padding
            if (rowWidth > maxItemRowWidth) maxItemRowWidth = rowWidth
        }

        val width = maxOf(headerWidth, maxItemRowWidth, PanelLayout.MIN_WIDTH).coerceAtMost(PanelLayout.MAX_WIDTH)
        val layout = PanelLayout(width)
        val right = layout.right

        layout.textAt(padding + 22, 6, formatSpeciesName(speciesName), 0xFFFFFF)
        layout.textRightAt(6, headerTag, 0xDDCC99)
        layout.fill(padding, 20, right, 21, 0x50FFFFFF)

        val labelHeader = tr("cobbledex-rei-emi-jei.drops.header")
        layout.textAt(padding, 26, labelHeader, 0xEEEEEE)
        layout.skipTo(38)

        for (drop in drops) {
            val itemName = resolveItemName(drop.itemId)
            val qtyText = "\u00D7${drop.displayQuantity}"
            val pctText = drop.displayPercentage
            val nameX = padding + 22
            val rightInfo = "$pctText $qtyText"
            val rightInfoWidth = font.width(rightInfo)
            val nameMaxWidth = right - nameX - rightInfoWidth - 4

            layout.clipped(nameX, itemName, nameMaxWidth, 0xFFFFFF)
            layout.textRightAt(layout.y, rightInfo, 0xBBBBBB)
            layout.gap(20)
        }

        layout.gap(2)
        layout.separator(0x20FFFFFF)
        layout.gap(4)
        val countText = tr("cobbledex-rei-emi-jei.drops.count", drops.size)
        layout.text(padding, countText, 0x888888)
        layout.gap(font.lineHeight + padding)

        return layout
    }

    // --- Evolution requirement lines (structured, for bullet display) ---

    fun getEvolutionRequirementLines(evo: EvolutionInfo): List<String> {
        val lines = mutableListOf<String>()

        if (evo.requiredContext != null && evo.requiredContext.isNotBlank()) {
            val rawId = evo.requiredContext.trim()
            val itemName = rawId
                .let { if (it.contains(":")) it.substringAfter(":") else it }
                .let { if (it.contains("=")) it.substringAfter("=").substringBefore(" ") else it }
                .replace("_", " ")
                .split(" ")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            when {
                evo.variant.contains("item_interact") ->
                    lines.add(tr("cobbledex-rei-emi-jei.evo.use_item_named", itemName))
                evo.variant == "trade" ->
                    lines.add(tr("cobbledex-rei-emi-jei.evo.trade_with", itemName))
                evo.variant.contains("block_click") ->
                    lines.add(tr("cobbledex-rei-emi-jei.evo.click_block", itemName))
                itemName.isNotBlank() ->
                    lines.add(tr("cobbledex-rei-emi-jei.evo.use_item_named", itemName))
            }
        }

        if (evo.variant == "trade" && evo.requiredContext == null) {
            lines.add(tr("cobbledex-rei-emi-jei.evo.trade"))
        }

        for (req in evo.requirements) {
            if (req.variant == "held_item" || req.variant == "owner_holds_item") continue
            lines.add(req.displayText)
        }

        if (lines.isEmpty() && evo.itemRequirements.isEmpty()) {
            when {
                evo.variant.contains("level") -> lines.add(tr("cobbledex-rei-emi-jei.evo.level_up"))
                evo.variant.contains("trade") -> lines.add(tr("cobbledex-rei-emi-jei.evo.trade"))
                evo.variant.contains("item") -> lines.add(tr("cobbledex-rei-emi-jei.evo.use_item"))
                else -> lines.add(tr("cobbledex-rei-emi-jei.evo.level_up"))
            }
        }

        return lines
    }

    // --- Evolution layout builder ---

    fun buildEvolutionLayout(
        evolution: EvolutionInfo,
        branchIndex: Int,
        branchTotal: Int,
        itemYRef: IntArray? = null
    ): PanelLayout {
        val font = Minecraft.getInstance().font
        val padding = PanelLayout.PADDING
        val slotSize = PanelLayout.SLOT_SIZE
        val lineHeight = PanelLayout.LINE_HEIGHT
        val headerTag = tr("category.cobbledex-rei-emi-jei.evolution")

        val reqLines = getEvolutionRequirementLines(evolution)
        val items = evolution.itemRequirements

        val fromW = font.width(evolution.displayFromName)
        val toW = font.width(evolution.displayToName)
        val arrowStr = " \u2192 "
        val arrowW = font.width(arrowStr)

        val fromCenterX = 20 + slotSize / 2
        val toCenterBase = slotSize / 2 + 20
        val nameLineWidth = fromCenterX + fromW / 2 + arrowW + toW / 2 + toCenterBase
        val tagWidth = font.width(headerTag) + padding * 2

        val maxReqWidth = if (reqLines.isNotEmpty()) {
            reqLines.maxOf { padding + 10 + font.width("\u2022 $it") + padding }
        } else 0

        val maxItemWidth = if (items.isNotEmpty()) {
            items.maxOf { item ->
                val itemName = resolveItemName(item.itemId)
                padding + 22 + font.width("${item.label} $itemName") + padding
            }
        } else 0

        val width = maxOf(nameLineWidth, tagWidth, maxReqWidth, maxItemWidth, PanelLayout.MIN_WIDTH)
            .coerceAtMost(PanelLayout.MAX_WIDTH)
        val layout = PanelLayout(width)
        val right = layout.right
        val centerX = width / 2
        val indentX = padding + 6
        val indentWidth = right - indentX
        val toCenterX = width - 20 - slotSize / 2

        // Species names below icon slots
        layout.textAt(fromCenterX - fromW / 2, 30, evolution.displayFromName, 0xFFFFFF)
        layout.textAt(centerX - arrowW / 2, 30, arrowStr, 0x888888)
        layout.textAt(toCenterX - toW / 2, 30, evolution.displayToName, 0xFFFFFF)

        layout.textRightAt(4, headerTag, 0xDDCC99)
        layout.fill(padding, 42, right, 43, 0x50FFFFFF)
        layout.skipTo(48)

        // Requirements section
        if (reqLines.isNotEmpty()) {
            layout.text(padding, tr("cobbledex-rei-emi-jei.evo.section.requirements"), 0xEEEEEE)
            layout.line()
            for (line in reqLines) {
                layout.wrapped(indentX, "\u2022 $line", indentWidth, 0xFFDD88)
            }
            layout.gap(PanelLayout.SECTION_GAP)
        }

        // Items section
        if (items.isNotEmpty()) {
            layout.text(padding, tr("cobbledex-rei-emi-jei.evo.section.items"), 0xEEEEEE)
            layout.line()
            itemYRef?.set(0, layout.y)
            for (item in items) {
                val itemName = resolveItemName(item.itemId)
                val nameX = padding + 22
                val labelText = "${item.label} "
                val labelWidth = font.width(labelText)
                layout.textAt(nameX, layout.y + 4, labelText, 0xBBBBBB)
                layout.textAt(nameX + labelWidth, layout.y + 4,
                    clipToWidth(font, itemName, right - nameX - labelWidth), 0xFFFFFF)
                layout.gap(PanelLayout.ITEM_ROW_HEIGHT)
            }
            layout.gap(PanelLayout.SECTION_GAP)
        }

        // Footer
        if (branchTotal > 1) {
            layout.gap(1)
            layout.separator(0x20FFFFFF)
            layout.gap(4)
            layout.text(padding, evoBranchText(branchIndex, branchTotal), 0x888888)
            layout.gap(font.lineHeight + padding)
        } else {
            layout.gap(padding)
        }

        return layout
    }

    // --- Stats detail rendering (shared between REI/JEI/EMI) ---

    private val STAT_NAMES = listOf("hp", "atk", "def", "spa", "spd", "spe")
    private val STAT_LABEL_KEYS = mapOf(
        "hp" to "cobbledex-rei-emi-jei.stat.hp",
        "atk" to "cobbledex-rei-emi-jei.stat.atk",
        "def" to "cobbledex-rei-emi-jei.stat.def",
        "spa" to "cobbledex-rei-emi-jei.stat.spa",
        "spd" to "cobbledex-rei-emi-jei.stat.spd",
        "spe" to "cobbledex-rei-emi-jei.stat.spe"
    )
    private fun statLabel(statId: String): String {
        val key = STAT_LABEL_KEYS[statId] ?: return statId.uppercase()
        return tr(key)
    }
    private val STAT_COLORS = mapOf(
        "hp" to 0xFFFF5555.toInt(),
        "atk" to 0xFFFF8844.toInt(),
        "def" to 0xFFFFCC33.toInt(),
        "spa" to 0xFF6699FF.toInt(),
        "spd" to 0xFF77CC55.toInt(),
        "spe" to 0xFFFF66AA.toInt()
    )

    private val TYPE_COLORS = mapOf(
        "normal" to 0xFFA8A878.toInt(), "fire" to 0xFFF08030.toInt(),
        "water" to 0xFF6890F0.toInt(), "electric" to 0xFFF8D030.toInt(),
        "grass" to 0xFF78C850.toInt(), "ice" to 0xFF98D8D8.toInt(),
        "fighting" to 0xFFC03028.toInt(), "poison" to 0xFFA040A0.toInt(),
        "ground" to 0xFFE0C068.toInt(), "flying" to 0xFFA890F0.toInt(),
        "psychic" to 0xFFF85888.toInt(), "bug" to 0xFFA8B820.toInt(),
        "rock" to 0xFFB8A038.toInt(), "ghost" to 0xFF705898.toInt(),
        "dragon" to 0xFF7038F8.toInt(), "dark" to 0xFF705848.toInt(),
        "steel" to 0xFFB8B8D0.toInt(), "fairy" to 0xFFEE99AC.toInt()
    )

    // --- Stats layout builder ---

    fun buildStatsLayout(
        speciesName: String,
        baseStats: Map<String, Int>,
        bst: Int,
        primaryType: String,
        secondaryType: String?,
        evYield: Map<String, Int>? = null
    ): PanelLayout {
        val layout = PanelLayout(200)
        val font = layout.font
        val padding = PanelLayout.PADDING
        val right = layout.right
        val lineHeight = 13

        layout.textAt(padding + 22, 6, formatSpeciesName(speciesName), 0xFFFFFF)
        val headerText = tr("category.cobbledex-rei-emi-jei.stats")
        layout.textRightAt(6, headerText, 0xDDCC99)
        layout.fill(padding, 20, right, 21, 0x50FFFFFF)

        val typeStr = buildString {
            append(formatTypeName(primaryType))
            secondaryType?.let { append(" / ${formatTypeName(it)}") }
        }
        layout.textAt(padding, 25, typeStr, typeColor(primaryType))

        val bstText = tr("cobbledex-rei-emi-jei.stats.bst", bst)
        val bstColor = when {
            bst >= 600 -> 0xFFFF5555.toInt()
            bst >= 500 -> 0xFFFFCC33.toInt()
            bst >= 400 -> 0xFF77CC55.toInt()
            else -> 0xFFBBBBBB.toInt()
        }
        layout.textRightAt(25, bstText, bstColor)

        layout.skipTo(40)
        val maxLabelWidth = STAT_NAMES.maxOf { font.width(statLabel(it)) }
        val barX = padding + maxLabelWidth + 4
        val valueSpace = 22
        val barMaxWidth = right - barX - valueSpace
        val maxStat = 255

        for (statId in STAT_NAMES) {
            val value = baseStats[statId] ?: 0
            val label = statLabel(statId)
            val color = STAT_COLORS[statId] ?: 0xFFAAAAAA.toInt()

            layout.text(padding, label, 0xBBBBBB)

            val barWidth = ((value.toFloat() / maxStat) * barMaxWidth).toInt().coerceAtLeast(1)
            layout.fill(barX, layout.y + 1, barX + barMaxWidth, layout.y + 9, 0x30FFFFFF)
            layout.fill(barX, layout.y + 1, barX + barWidth, layout.y + 9, color)

            layout.textRight(value.toString(), 0xFFFFFF)
            layout.gap(lineHeight)
        }

        if (evYield != null && evYield.isNotEmpty()) {
            layout.gap(2)
            val evParts = evYield.entries.map { "${it.value} ${statLabel(it.key)}" }
            val evText = tr("cobbledex-rei-emi-jei.stats.ev_yield", evParts.joinToString(", "))
            layout.text(padding, evText, 0xFF88CCFF.toInt())
            layout.gap(font.lineHeight)
        }

        return layout
    }

    private fun typeColor(type: String): Int = TYPE_COLORS[type.lowercase()] ?: 0xFFBBBBBB.toInt()

    // --- Pokédex Info layout builder ---

    fun buildPokedexInfoLayout(data: PokedexInfoRecipeData): PanelLayout {
        val layout = PanelLayout(200)
        val font = layout.font
        val padding = PanelLayout.PADDING
        val right = layout.right
        val lineHeight = PanelLayout.LINE_HEIGHT
        val indentX = padding + 4
        val indentWidth = right - indentX

        layout.textAt(padding + 22, 6, formatSpeciesName(data.speciesName), 0xFFFFFF)
        val headerText = tr("category.cobbledex-rei-emi-jei.pokedex_info")
        layout.textRightAt(6, headerText, 0xDDCC99)
        layout.fill(padding, 20, right, 21, 0x50FFFFFF)
        layout.skipTo(26)

        if (data.abilities.isNotEmpty() || data.hiddenAbility != null) {
            layout.text(padding, tr("cobbledex-rei-emi-jei.info.abilities"), 0xEEEEEE)
            layout.line()
            for (ability in data.abilities) {
                layout.text(indentX, "\u2022 $ability", 0xFF88CCFF.toInt())
                layout.line()
            }
            data.hiddenAbility?.let { ha ->
                layout.text(indentX, "\u2022 $ha ${tr("cobbledex-rei-emi-jei.info.hidden_ability")}", 0xFF66AADD.toInt())
                layout.line()
            }
            layout.gap(3)
        }

        if (data.eggGroups.isNotEmpty()) {
            layout.text(padding, tr("cobbledex-rei-emi-jei.info.egg_groups"), 0xEEEEEE)
            layout.line()
            val groupText = data.eggGroups.joinToString(", ") { titleCase(it.replace("-", " ")) }
            layout.wrapped(indentX, groupText, indentWidth, 0xFFDDDD88.toInt())
            layout.gap(3)
        }

        layout.text(padding, tr("cobbledex-rei-emi-jei.info.physical"), 0xEEEEEE)
        layout.line()
        val heightText = "%.1fm".format(data.height / 10f)
        val weightText = "%.1fkg".format(data.weight / 10f)
        layout.text(indentX, tr("cobbledex-rei-emi-jei.info.height_weight", heightText, weightText), 0xBBBBBB)
        layout.line()

        layout.text(indentX, tr("cobbledex-rei-emi-jei.info.catch_rate", data.catchRate), 0xBBBBBB)
        layout.line()

        data.maleRatio?.let { ratio ->
            val genderText = when {
                ratio < 0 -> tr("cobbledex-rei-emi-jei.info.genderless")
                ratio == 1f -> tr("cobbledex-rei-emi-jei.info.male_only")
                ratio == 0f -> tr("cobbledex-rei-emi-jei.info.female_only")
                else -> tr("cobbledex-rei-emi-jei.info.gender_ratio", "%.0f".format(ratio * 100), "%.0f".format((1 - ratio) * 100))
            }
            layout.text(indentX, genderText, 0xBBBBBB)
            layout.line()
        }

        if (data.shoulderMountable) {
            layout.text(indentX, tr("cobbledex-rei-emi-jei.info.shoulder"), 0xFF88DDAA.toInt())
            layout.line()
        }
        layout.gap(3)

        data.eggCycles?.let { cycles ->
            layout.text(padding, tr("cobbledex-rei-emi-jei.info.breeding"), 0xEEEEEE)
            layout.line()
            layout.text(indentX, tr("cobbledex-rei-emi-jei.info.egg_cycles", cycles, cycles * 257), 0xBBBBBB)
            layout.line()
        }

        if (data.experienceGroup != null || data.baseExperienceYield != null || data.baseFriendship != null) {
            layout.gap(1)
            layout.text(padding, tr("cobbledex-rei-emi-jei.info.training"), 0xEEEEEE)
            layout.line()
            data.experienceGroup?.let { group ->
                layout.text(indentX, tr("cobbledex-rei-emi-jei.info.exp_group", titleCase(group.replace("_", " "))), 0xBBBBBB)
                layout.line()
            }
            data.baseExperienceYield?.let { exp ->
                layout.text(indentX, tr("cobbledex-rei-emi-jei.info.base_exp", exp), 0xBBBBBB)
                layout.line()
            }
            data.baseFriendship?.let { friendship ->
                layout.text(indentX, tr("cobbledex-rei-emi-jei.info.base_friendship", friendship), 0xBBBBBB)
                layout.line()
            }
        }

        layout.gap(padding)
        return layout
    }

    // --- Moves layout builder ---

    private val CATEGORY_ICONS = mapOf(
        "physical" to Pair("\u2694", 0xFFCC6644.toInt()),
        "special" to Pair("\u25C6", 0xFF6699FF.toInt()),
        "status" to Pair("\u2726", 0xFFAAAADD.toInt())
    )

    private fun formatMoveSuffix(move: MoveDetail): String {
        val icon = CATEGORY_ICONS[move.category]?.first ?: "\u2022"
        val pow = if (move.power > 0) "${move.power}" else "\u2014"
        val acc = if (move.accuracy > 0) "${move.accuracy}" else "\u2014"
        return "$icon $pow | $acc"
    }

    private fun moveRow(layout: PanelLayout, move: MoveDetail, prefix: String? = null) {
        val padding = PanelLayout.PADDING
        val right = layout.right
        val font = layout.font

        var x = padding + 4
        if (prefix != null) {
            layout.text(x, prefix, 0xFF88CCFF.toInt())
            x += font.width(prefix) + 4
        }

        val suffix = formatMoveSuffix(move)
        val suffixColor = CATEGORY_ICONS[move.category]?.second ?: 0xFFBBBBBB.toInt()
        val suffixWidth = font.width(suffix)

        val nameMaxWidth = right - x - suffixWidth - 4
        val moveKey = "cobblemon.move.${move.name}"
        val displayName = tr(moveKey).let { if (it == moveKey) titleCase(move.name) else it }
        layout.clipped(x, displayName, nameMaxWidth, typeColor(move.type))
        layout.textRight(suffix, suffixColor)
        layout.line()
    }

    fun buildMovesLayout(data: MovesRecipeData): PanelLayout {
        val layout = PanelLayout(200)
        val padding = PanelLayout.PADDING
        val right = layout.right

        layout.textAt(padding + 22, 6, formatSpeciesName(data.speciesName), 0xFFFFFF)
        val headerText = if (data.pageTotal > 1)
            tr("category.cobbledex-rei-emi-jei.moves") + " (" + tr("cobbledex-rei-emi-jei.moves.page", data.pageIndex, data.pageTotal) + ")"
        else
            tr("category.cobbledex-rei-emi-jei.moves")
        layout.textRightAt(6, headerText, 0xDDCC99)
        layout.fill(padding, 20, right, 21, 0x50FFFFFF)
        val colHeader = tr("cobbledex-rei-emi-jei.moves.pow_acc")
        layout.textRightAt(22, colHeader, 0xFF888888.toInt())
        layout.skipTo(33)

        if (data.levelUpMoves.isNotEmpty()) {
            layout.text(padding, tr("cobbledex-rei-emi-jei.moves.levelup"), 0xEEEEEE)
            layout.line()
            for (entry in data.levelUpMoves) {
                val lvPrefix = tr("cobbledex-rei-emi-jei.moves.level_prefix", entry.level)
                for (move in entry.moves) {
                    moveRow(layout, move, lvPrefix)
                }
            }
            layout.gap(3)
        }

        if (data.eggMoves.isNotEmpty()) {
            layout.text(padding, tr("cobbledex-rei-emi-jei.moves.egg"), 0xEEEEEE)
            layout.line()
            for (move in data.eggMoves) {
                moveRow(layout, move)
            }
            layout.gap(3)
        }

        if (data.tutorMoves.isNotEmpty()) {
            layout.text(padding, tr("cobbledex-rei-emi-jei.moves.tutor"), 0xEEEEEE)
            layout.line()
            for (move in data.tutorMoves) {
                moveRow(layout, move)
            }
            layout.gap(3)
        }

        if (data.tmMoves.isNotEmpty()) {
            layout.text(padding, tr("cobbledex-rei-emi-jei.moves.tm"), 0xEEEEEE)
            layout.line()
            for (move in data.tmMoves) {
                moveRow(layout, move)
            }
        }

        layout.gap(padding)
        return layout
    }

    // --- Fossil layout builder ---

    fun buildFossilLayout(data: FossilRecipeData): PanelLayout {
        val layout = PanelLayout(200)
        val font = layout.font
        val padding = PanelLayout.PADDING
        val right = layout.right
        val lineHeight = PanelLayout.LINE_HEIGHT

        layout.textAt(padding + 22, 6, formatSpeciesName(data.speciesName), 0xFFFFFF)
        val headerText = tr("category.cobbledex-rei-emi-jei.fossils")
        layout.textRightAt(6, headerText, 0xDDCC99)
        layout.fill(padding, 20, right, 21, 0x50FFFFFF)
        layout.skipTo(26)

        layout.text(padding, tr("cobbledex-rei-emi-jei.fossils.required"), 0xEEEEEE)
        layout.gap(lineHeight + 2)

        for (itemId in data.fossilItems) {
            val itemName = resolveItemName(itemId)
            val nameX = padding + 22
            layout.textAt(padding + 4, layout.y + 4, "\u2022", 0xFF88CCFF.toInt())
            layout.clipped(nameX, itemName, right - nameX, 0xFFFFFF)
            layout.gap(20)
        }

        data.extraTags?.let { tags ->
            layout.gap(4)
            layout.separator(0x20FFFFFF)
            layout.gap(4)
            val tagParts = tags.split(" ")
            for (part in tagParts) {
                val tagText = part.replace("_", " ")
                layout.text(padding + 4, tagText, 0xFF999999.toInt())
                layout.gap(lineHeight)
            }
        }

        layout.gap(padding)
        return layout
    }

    // --- Type chart layout builder ---

    fun buildTypeChartLayout(data: TypeChartRecipeData): PanelLayout {
        val layout = PanelLayout(200)
        val padding = PanelLayout.PADDING
        val right = layout.right
        val lineHeight = PanelLayout.LINE_HEIGHT

        layout.textAt(padding + 22, 6, formatSpeciesName(data.speciesName), 0xFFFFFF)
        val headerText = tr("category.cobbledex-rei-emi-jei.type_chart")
        layout.textRightAt(6, headerText, 0xDDCC99)
        layout.fill(padding, 20, right, 21, 0x50FFFFFF)

        val typeStr = buildString {
            append(formatTypeName(data.primaryType))
            data.secondaryType?.let { append(" / ${formatTypeName(it)}") }
        }
        layout.textAt(padding, 25, typeStr, typeColor(data.primaryType))
        layout.skipTo(40)

        if (data.weaknesses.isNotEmpty()) {
            layout.text(padding, tr("cobbledex-rei-emi-jei.typechart.weak"), 0xFFFF6666.toInt())
            layout.line()
            for ((type, mult) in data.weaknesses) {
                val multText = if (mult == 4f) "\u00D74" else "\u00D72"
                val color = if (mult == 4f) 0xFFFF4444.toInt() else 0xFFFF8866.toInt()
                layout.text(padding + 4, "\u2022 ${formatTypeName(type)}", typeColor(type))
                layout.textRight(multText, color)
                layout.line()
            }
            layout.gap(PanelLayout.SECTION_GAP)
        }

        if (data.resistances.isNotEmpty()) {
            layout.text(padding, tr("cobbledex-rei-emi-jei.typechart.resist"), 0xFF66CC66.toInt())
            layout.line()
            for ((type, mult) in data.resistances) {
                val multText = if (mult == 0.25f) "\u00D7\u00BC" else "\u00D7\u00BD"
                val color = if (mult == 0.25f) 0xFF44AA44.toInt() else 0xFF88CC88.toInt()
                layout.text(padding + 4, "\u2022 ${formatTypeName(type)}", typeColor(type))
                layout.textRight(multText, color)
                layout.line()
            }
            layout.gap(PanelLayout.SECTION_GAP)
        }

        if (data.immunities.isNotEmpty()) {
            layout.text(padding, tr("cobbledex-rei-emi-jei.typechart.immune"), 0xFF9999FF.toInt())
            layout.line()
            for (type in data.immunities) {
                layout.text(padding + 4, "\u2022 ${formatTypeName(type)}", typeColor(type))
                layout.textRight("\u00D70", 0xFF9999FF.toInt())
                layout.line()
            }
        }

        layout.gap(padding)
        return layout
    }

    // --- Nature layout builder ---

    fun buildNatureLayout(data: NatureRecipeData): PanelLayout {
        val font = Minecraft.getInstance().font
        val padding = PanelLayout.PADDING
        val lineHeight = 10

        val nameColHeader = tr("cobbledex-rei-emi-jei.natures.name_col")
        val upColHeader = tr("cobbledex-rei-emi-jei.natures.up_col")
        val downColHeader = tr("cobbledex-rei-emi-jei.natures.down_col")
        val nameColWidth = maxOf(font.width(nameColHeader), NatureData.NATURES.maxOf { font.width(NatureData.natureName(it.name)) }) + 6
        val statMaxWidth = maxOf(
            font.width(upColHeader),
            font.width(downColHeader),
            NatureData.NATURES.mapNotNull { it.increasedStat?.let { s -> font.width(NatureData.statName(s)) } }.maxOrNull() ?: 0,
            NatureData.NATURES.mapNotNull { it.decreasedStat?.let { s -> font.width(NatureData.statName(s)) } }.maxOrNull() ?: 0
        ) + 6
        val tableWidth = (padding + 2) + nameColWidth + statMaxWidth * 2 + padding
        val panelWidth = maxOf(tableWidth, PanelLayout.MIN_WIDTH).coerceAtMost(PanelLayout.MAX_WIDTH)

        val layout = PanelLayout(panelWidth)
        val right = layout.right

        val headerText = tr("category.cobbledex-rei-emi-jei.natures")
        layout.textCentered(headerText, 0xDDCC99)
        layout.skipTo(18)
        layout.separator()
        layout.skipTo(23)

        val nameCol = padding + 2
        val upCol = nameCol + nameColWidth
        val downCol = upCol + statMaxWidth
        layout.textAt(nameCol, layout.y, nameColHeader, 0xEEEEEE)
        layout.textAt(upCol, layout.y, upColHeader, 0xFF88FF88.toInt())
        layout.textAt(downCol, layout.y, downColHeader, 0xFFFF8888.toInt())
        layout.gap(lineHeight + 2)

        layout.fill(padding, layout.y - 1, right, layout.y, 0x30FFFFFF)

        for (nature in data.natures) {
            val nameColor = if (nature.isNeutral) 0xFFAAAAAA.toInt() else 0xFFFFFFFF.toInt()
            layout.text(nameCol, NatureData.natureName(nature.name), nameColor)

            if (nature.isNeutral) {
                layout.textAt(upCol, layout.y, "\u2014", 0xFF777777.toInt())
                layout.textAt(downCol, layout.y, "\u2014", 0xFF777777.toInt())
            } else {
                val upName = nature.increasedStat?.let { NatureData.statName(it) } ?: ""
                val downName = nature.decreasedStat?.let { NatureData.statName(it) } ?: ""
                layout.textAt(upCol, layout.y, upName, 0xFF88FF88.toInt())
                layout.textAt(downCol, layout.y, downName, 0xFFFF8888.toInt())
            }
            layout.gap(lineHeight)
        }

        layout.gap(padding)
        return layout
    }

    // --- Pokemon description layout builder ---

    fun buildPokemonDescriptionLayout(data: PokemonDescriptionRecipeData): PanelLayout {
        val layout = PanelLayout(200)
        val padding = PanelLayout.PADDING
        val right = layout.right

        layout.textAt(padding + 22, 6, formatSpeciesName(data.speciesName), 0xFFFFFF)
        val headerText = tr("category.cobbledex-rei-emi-jei.pokemon_description")
        layout.textRightAt(6, headerText, 0xDDCC99)
        layout.fill(padding, 20, right, 21, 0x50FFFFFF)
        layout.skipTo(26)

        val descText = tr(data.description)
        if (descText != data.description && descText.isNotBlank()) {
            layout.wrapped(padding + 4, descText, right - padding - 4, 0xFFDDDDDD.toInt())
        }
        
        layout.gap(padding + 4)
        return layout
    }

}
