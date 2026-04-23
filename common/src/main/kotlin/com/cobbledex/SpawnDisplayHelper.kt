package com.cobbledex

import com.cobbledex.config.CobbleDexConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.biome.Biome

object SpawnDisplayHelper {

    data class MergedSpawn(val spawn: SpawnInfo, val formVariants: List<String>)

    data class ChainLayoutResult(
        val layout: PanelLayout,
        val pokemonSlots: List<PokemonSlotDef>,
        val itemSlots: List<ItemSlotDef>,
    )

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

    // --- Condition / location / exclusion builders ---

    fun buildConditions(spawn: SpawnInfo): List<String> {
        val list = mutableListOf<String>()
        spawn.timeRange?.let {
            val icon = when {
                it.contains("day", true) -> "\u2600 "
                it.contains("night", true) -> "\u263D "
                it.contains("dusk", true) || it.contains("dawn", true) -> "\u263C "
                else -> "\u23F0 "
            }
            list.add("$icon${formatTimeRange(it)}")
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
        val structNames = spawn.structures.map { formatStructureName(it) }.toSet()
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
            val names = spawn.neededBaseBlocks.map { formatBlockName(it) }
            val redundant = structNames.isNotEmpty() && names.all { it.lowercase().contains("structure") }
            if (!redundant) list.add(tr("cobbledex-rei-emi-jei.spawn.special.spawns_on", names.joinToString(", ")))
        }
        if (spawn.neededNearbyBlocks.isNotEmpty()) {
            val names = spawn.neededNearbyBlocks.map { formatBlockName(it) }
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
            lines.add(tr("cobbledex-rei-emi-jei.spawn.excluded.structures", anti.structures.map { formatStructureName(it) }.joinToString(", ")))
        }
        if (anti.minY != null || anti.maxY != null) {
            val r = listOfNotNull(anti.minY?.let { "Y \u2265 $it" }, anti.maxY?.let { "Y \u2264 $it" })
            lines.add(tr("cobbledex-rei-emi-jei.spawn.excluded.height", r.joinToString(", ")))
        }
        if (anti.timeRange != null) {
            lines.add(tr("cobbledex-rei-emi-jei.spawn.excluded.time", formatTimeRange(anti.timeRange)))
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
        if (maxWidth <= 0) return listOf(clipToWidth(font, text, maxWidth.coerceAtLeast(1)))
        if (font.width(text) <= maxWidth) return listOf(text)
        val items = text.split(", ")
        val lines = mutableListOf<String>()
        var current = ""
        for (item in items) {
            val clipped = if (font.width(item) > maxWidth) clipToWidth(font, item, maxWidth) else item
            val next = if (current.isEmpty()) clipped else "$current, $clipped"
            if (font.width(next) > maxWidth && current.isNotEmpty()) {
                lines.add(current)
                current = clipped
            } else {
                current = next
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        return if (lines.isEmpty()) listOf(text) else lines
    }

    fun wrapText(font: net.minecraft.client.gui.Font, text: String, maxWidth: Int): List<String> {
        if (maxWidth <= 0) return listOf(clipToWidth(font, text, maxWidth.coerceAtLeast(1)))
        if (font.width(text) <= maxWidth) return listOf(text)
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val clipped = if (font.width(word) > maxWidth) clipToWidth(font, word, maxWidth) else word
            val next = if (current.isEmpty()) clipped else "$current $clipped"
            if (font.width(next) > maxWidth && current.isNotEmpty()) {
                lines.add(current)
                current = clipped
            } else {
                current = next
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        return if (lines.isEmpty()) listOf(text) else lines
    }

    private fun computePanelWidth(vararg candidates: Int): Int {
        return (candidates.maxOrNull() ?: PanelLayout.MIN_WIDTH)
            .coerceIn(PanelLayout.MIN_WIDTH, PanelLayout.MAX_WIDTH)
    }

    private fun measureHeaderWidth(
        font: Font,
        leftText: String,
        rightText: String,
        leftX: Int = PanelLayout.PADDING + 22,
        gap: Int = 8
    ): Int {
        return leftX + font.width(leftText) + gap + font.width(rightText) + PanelLayout.PADDING
    }

    private fun drawSplitRow(
        layout: PanelLayout,
        leftX: Int,
        leftText: String,
        rightText: String,
        leftColor: Int,
        rightColor: Int,
        yPos: Int = layout.y,
        gap: Int = 8,
        minLeftWidth: Int = 52,
        minRightWidth: Int = 28
    ) {
        val rightEdge = layout.right
        if (rightText.isBlank()) {
            layout.clippedAt(leftX, yPos, leftText, (rightEdge - leftX).coerceAtLeast(1), leftColor)
            return
        }

        val maxRightWidth = (rightEdge - leftX - gap - minLeftWidth).coerceAtLeast(minRightWidth)
        val clippedRight = clipToWidth(layout.font, rightText, maxRightWidth)
        val rightWidth = layout.font.width(clippedRight)
        val leftMaxWidth = (rightEdge - leftX - gap - rightWidth).coerceAtLeast(1)

        layout.clippedAt(leftX, yPos, leftText, leftMaxWidth, leftColor)
        layout.clippedRightAt(yPos, rightText, maxRightWidth, rightColor)
    }

    private fun drawHeader(
        layout: PanelLayout,
        leftText: String,
        rightText: String,
        rightColor: Int,
        dividerY: Int,
        leftX: Int = PanelLayout.PADDING + 22,
        yPos: Int = 6,
        leftColor: Int = 0xFFFFFF,
        gap: Int = 8
    ) {
        drawSplitRow(
            layout = layout,
            leftX = leftX,
            leftText = leftText,
            rightText = rightText,
            leftColor = leftColor,
            rightColor = rightColor,
            yPos = yPos,
            gap = gap,
            minLeftWidth = 56,
            minRightWidth = 36
        )
        layout.fill(PanelLayout.PADDING, dividerY, layout.right, dividerY + 1, 0x50FFFFFF)
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

    // --- Biome tooltip builder ---

    private fun resolveBiomeTag(tagId: String): List<String> {
        val clean = tagId.removePrefix("#")
        val loc = ResourceLocation.tryParse(clean) ?: return emptyList()
        val tagKey = TagKey.create(Registries.BIOME, loc)
        val registry = Minecraft.getInstance().connection?.registryAccess()
            ?.registryOrThrow(Registries.BIOME) ?: return emptyList()
        val tag = registry.getTag(tagKey)
        if (tag.isEmpty) return emptyList()
        return tag.get().map { holder ->
            holder.unwrapKey().map { it.location().toString() }.orElse("")
        }.filter { it.isNotEmpty() }.sorted()
    }

    private fun translateBiomeId(id: String): String {
        val loc = ResourceLocation.tryParse(id) ?: return titleCase(id)
        val key = "biome.${loc.namespace}.${loc.path}"
        val translated = tr(key)
        return if (translated != key) translated else titleCase(loc.path.replace("_", " "))
    }

    private fun buildSingleBiomeTooltip(biomeId: String): List<Component> {
        val lines = mutableListOf<Component>()
        val pretty = formatBiomeName(biomeId)

        if (biomeId.startsWith("#")) {
            lines.add(Component.literal("§e§l$pretty"))
            val resolved = resolveBiomeTag(biomeId)
            if (resolved.isNotEmpty()) {
                for (id in resolved) {
                    lines.add(Component.literal("  §7${translateBiomeId(id)}"))
                }
            } else {
                lines.add(Component.literal("  §8${biomeId.removePrefix("#")}"))
            }
        } else {
            lines.add(Component.literal("§f${translateBiomeId(biomeId)}"))
            lines.add(Component.literal("§8$biomeId"))
        }

        return lines
    }

    // --- Ability / Move tooltip builders ---

    private fun abilityDescKey(name: String): String =
        "cobblemon.ability.${name.lowercase().replace(" ", "").replace("-", "").replace("_", "")}.desc"

    private fun buildAbilityTooltip(ability: String, hidden: Boolean = false): List<Component> {
        val lines = mutableListOf<Component>()
        val displayName = formatAbilityName(ability)
        val label = if (hidden) "$displayName §7(${tr("cobbledex-rei-emi-jei.info.hidden_ability")})" else displayName
        lines.add(Component.literal("§b§l$label"))
        val descKey = abilityDescKey(ability)
        val desc = tr(descKey)
        if (desc != descKey) lines.add(Component.literal("§7$desc"))
        return lines
    }

    private fun buildMoveTooltip(move: MoveDetail): List<Component> {
        val lines = mutableListOf<Component>()
        val moveKey = "cobblemon.move.${move.name}"
        val displayName = tr(moveKey).let { if (it == moveKey) titleCase(move.name) else it }
        lines.add(Component.literal("§f§l$displayName"))
        lines.add(Component.literal("§e${titleCase(move.type)} §7\u2022 §e${titleCase(move.category)}"))
        val pow = if (move.power > 0) "${move.power}" else "\u2014"
        val acc = if (move.accuracy > 0) "${move.accuracy}" else "\u2014"
        lines.add(Component.literal("§7Power: §f$pow §7| Acc: §f$acc §7| PP: §f${move.pp}"))
        val descKey = "cobblemon.move.${move.name}.desc"
        val desc = tr(descKey)
        if (desc != descKey) {
            lines.add(Component.literal(""))
            // Wrap long move descriptions into multiple tooltip lines (~40 chars each)
            val words = desc.split(" ")
            var current = ""
            for (word in words) {
                val next = if (current.isEmpty()) word else "$current $word"
                if (next.length > 40 && current.isNotEmpty()) {
                    lines.add(Component.literal("§7$current"))
                    current = word
                } else current = next
            }
            if (current.isNotEmpty()) lines.add(Component.literal("§7$current"))
        }
        return lines
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
            // Show base species for forms
            if (info.isForm && info.baseSpeciesName != null) {
                lines.add(Component.literal("§8" + tr("cobbledex-rei-emi-jei.tooltip.form_of", formatSpeciesName(info.baseSpeciesName))))
            }

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
            val maxBiomes = PanelLayout.MAX_VISIBLE_BIOMES
            val header = if (biomeNames.size > 1) tr("cobbledex-rei-emi-jei.spawn.section.biomes") else tr("cobbledex-rei-emi-jei.spawn.section.biome")
            layout.text(padding, header, 0xEEEEEE)
            layout.line()
            val visibleNames = if (biomeNames.size > maxBiomes) biomeNames.take(maxBiomes) else biomeNames
            val visibleRawIds = if (spawn.biomes.size > maxBiomes) spawn.biomes.take(maxBiomes) else spawn.biomes
            val placements = layout.wrappedItemsWithPositions(indentX, visibleNames, ", ", indentWidth, 0xDDDDDD)
            for ((placement, rawId) in placements.zip(visibleRawIds)) {
                val tooltipLines = buildSingleBiomeTooltip(rawId)
                layout.addTooltipZone(placement.x, placement.y, placement.width, PanelLayout.LINE_HEIGHT, tooltipLines)
            }
            if (biomeNames.size > maxBiomes) {
                val overflow = biomeNames.size - maxBiomes
                val moreText = "+$overflow more..."
                val moreY = layout.y
                layout.text(indentX, moreText, 0xFF999999.toInt())
                layout.line()
                val overflowTooltip = spawn.biomes.drop(maxBiomes).flatMap { rawId ->
                    buildSingleBiomeTooltip(rawId) + listOf(Component.literal(""))
                }.dropLast(1) // remove trailing blank
                layout.addTooltipZone(indentX, moreY, layout.font.width(moreText), PanelLayout.LINE_HEIGHT, overflowTooltip)
            }
            layout.gap(PanelLayout.SECTION_GAP)
        }

        val conditions = buildConditions(spawn)
        if (conditions.isNotEmpty()) {
            val maxConds = PanelLayout.MAX_VISIBLE_CONDITIONS
            layout.text(padding, tr("cobbledex-rei-emi-jei.spawn.section.conditions"), 0xEEEEEE)
            layout.line()
            val visibleConds = if (conditions.size > maxConds) conditions.take(maxConds) else conditions
            for (cond in visibleConds) {
                layout.wrapped(indentX, cond, indentWidth, 0xDDDDDD)
            }
            if (conditions.size > maxConds) {
                val overflow = conditions.size - maxConds
                val moreText = "+$overflow more..."
                val moreY = layout.y
                layout.text(indentX, moreText, 0xFF999999.toInt())
                layout.line()
                val overflowTooltip = conditions.drop(maxConds).map {
                    Component.literal(it).withStyle { s -> s.withColor(0xDDDDDD) }
                }
                layout.addTooltipZone(indentX, moreY, layout.font.width(moreText), PanelLayout.LINE_HEIGHT, overflowTooltip)
            }
            layout.gap(PanelLayout.SECTION_GAP)
        }

        val specials = buildSpecials(spawn)
        if (specials.isNotEmpty()) {
            val maxSpecials = PanelLayout.MAX_VISIBLE_SPECIALS
            layout.text(padding, tr("cobbledex-rei-emi-jei.spawn.section.location"), 0xEEEEEE)
            layout.line()
            val visibleSpecials = if (specials.size > maxSpecials) specials.take(maxSpecials) else specials
            for (s in visibleSpecials) {
                layout.wrappedCommas(indentX, s, indentWidth, 0xFFCC66)
            }
            if (specials.size > maxSpecials) {
                val overflow = specials.size - maxSpecials
                val moreText = "+$overflow more..."
                val moreY = layout.y
                layout.text(indentX, moreText, 0xFF999999.toInt())
                layout.line()
                val overflowTooltip = specials.drop(maxSpecials).map {
                    Component.literal(it).withStyle { s -> s.withColor(0xFFCC66) }
                }
                layout.addTooltipZone(indentX, moreY, layout.font.width(moreText), PanelLayout.LINE_HEIGHT, overflowTooltip)
            }
            layout.gap(PanelLayout.SECTION_GAP)
        }

        val anti = spawn.anticondition
        if (anti != null && !anti.isEmpty) {
            val exLines = buildExclusionLines(anti)
            if (exLines.isNotEmpty()) {
                val maxEx = PanelLayout.MAX_VISIBLE_EXCLUSION_LINES
                layout.text(padding, tr("cobbledex-rei-emi-jei.spawn.section.excluded"), 0xFF7777)
                layout.line()
                val visibleEx = if (exLines.size > maxEx) exLines.take(maxEx) else exLines
                for (line in visibleEx) {
                    layout.wrappedCommas(indentX, line, indentWidth, 0xEE8888)
                }
                if (exLines.size > maxEx) {
                    val overflow = exLines.size - maxEx
                    val moreText = "+$overflow more..."
                    val moreY = layout.y
                    layout.text(indentX, moreText, 0xFF999999.toInt())
                    layout.line()
                    val overflowTooltip = exLines.drop(maxEx).map {
                        Component.literal(it).withStyle { s -> s.withColor(0xEE8888) }
                    }
                    layout.addTooltipZone(indentX, moreY, layout.font.width(moreText), PanelLayout.LINE_HEIGHT, overflowTooltip)
                }
                layout.gap(PanelLayout.SECTION_GAP)
            }
        }

        if (CobbleDexConfig.get().showSpawnWeights && spawn.weightMultipliers.isNotEmpty()) {
            val maxWm = PanelLayout.MAX_VISIBLE_WEIGHT_MODS
            layout.text(padding, tr("cobbledex-rei-emi-jei.spawn.section.weight_mods"), 0xEEEEEE)
            layout.line()
            val visibleWm = if (spawn.weightMultipliers.size > maxWm) spawn.weightMultipliers.take(maxWm) else spawn.weightMultipliers
            for (wm in visibleWm) {
                val arrow: String
                val c: Int
                when {
                    wm.multiplier > 1f -> { arrow = "\u25B2"; c = 0x88DD88 }
                    wm.multiplier < 1f -> { arrow = "\u25BC"; c = 0xEE8888 }
                    else -> { arrow = "\u25CF"; c = 0xBBBBBB }
                }
                val wmText = "$arrow ${formatWeight(wm.multiplier)}x ${wm.displayConditionSummary()}"
                layout.wrapped(indentX, wmText, indentWidth, c)
            }
            if (spawn.weightMultipliers.size > maxWm) {
                val overflow = spawn.weightMultipliers.size - maxWm
                val moreText = "+$overflow more..."
                val moreY = layout.y
                layout.text(indentX, moreText, 0xFF999999.toInt())
                layout.line()
                val overflowTooltip = spawn.weightMultipliers.drop(maxWm).map { wm ->
                    val arrow = when { wm.multiplier > 1f -> "\u25B2"; wm.multiplier < 1f -> "\u25BC"; else -> "\u25CF" }
                    val color = when { wm.multiplier > 1f -> 0x88DD88; wm.multiplier < 1f -> 0xEE8888; else -> 0xBBBBBB }
                    Component.literal("$arrow ${formatWeight(wm.multiplier)}x ${wm.displayConditionSummary()}").withStyle { s -> s.withColor(color) }
                }
                layout.addTooltipZone(indentX, moreY, layout.font.width(moreText), PanelLayout.LINE_HEIGHT, overflowTooltip)
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

        for (note in obtainment.displayNotes) {
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

    // --- Evolution chain layout builder ---

    fun buildEvolutionChainLayout(chain: EvolutionChainBuilder.ChainNode): ChainLayoutResult {
        val font = Minecraft.getInstance().font
        val padding = PanelLayout.PADDING
        val headerTag = tr("category.cobbledex-rei-emi-jei.evolution")
        val chainIndent = EvolutionChainBuilder.CHAIN_INDENT
        val rows = EvolutionChainBuilder.flattenChain(chain)
        val pokemonSlots = mutableListOf<PokemonSlotDef>()
        val itemSlots = mutableListOf<ItemSlotDef>()
        val iconSize = 20
        val afterIcon = iconSize + 2

        var maxRowWidth = PanelLayout.MIN_WIDTH
        for (row in rows) {
            val indent = when (row) {
                is EvolutionChainBuilder.ChainRow.Pokemon -> row.indent * chainIndent
                is EvolutionChainBuilder.ChainRow.Arrow -> row.indent * chainIndent
                is EvolutionChainBuilder.ChainRow.Branch -> row.indent * chainIndent
            }
            val rowW = when (row) {
                is EvolutionChainBuilder.ChainRow.Pokemon ->
                    padding + 4 + indent + iconSize + 2 + font.width(row.displayName) + padding
                is EvolutionChainBuilder.ChainRow.Arrow -> {
                    val itemExtra = if (row.items.isNotEmpty()) 20 else 0
                    padding + 4 + indent + font.width("\u2193 ${row.requirement}") + itemExtra + padding
                }
                is EvolutionChainBuilder.ChainRow.Branch -> {
                    val prefixW = font.width("\u251C ")
                    val reqPart = if (row.requirement.isNotBlank()) " \u2014 ${row.requirement}" else ""
                    val itemExtra = if (row.items.isNotEmpty()) 20 else 0
                    padding + 4 + indent + prefixW + iconSize + 2 + font.width(row.displayName + reqPart) + itemExtra + padding
                }
            }
            if (rowW > maxRowWidth) maxRowWidth = rowW
        }

        val nameWidth = padding + 22 + font.width(formatSpeciesName(chain.species)) + 8 + font.width(headerTag) + padding
        val width = maxOf(maxRowWidth, nameWidth).coerceAtMost(PanelLayout.MAX_WIDTH)
        val layout = PanelLayout(width)
        val right = layout.right
        val indentX = padding + 4

        // Header
        val speciesDisplay = formatSpeciesName(chain.species)
        val tagWidth = font.width(headerTag)
        val maxNameW = width - padding - 22 - tagWidth - 8
        val clippedName = if (font.width(speciesDisplay) > maxNameW && maxNameW > 20) {
            var s = speciesDisplay
            while (font.width("$s\u2026") > maxNameW && s.length > 1) s = s.dropLast(1)
            if (s.length < speciesDisplay.length) "$s\u2026" else s
        } else speciesDisplay
        pokemonSlots.add(PokemonSlotDef(chain.species, chain.aspects, padding, 2, SlotRole.INPUT))
        layout.textAt(padding + 22, 6, clippedName, 0xFFFFFF)
        layout.textRightAt(6, headerTag, 0xDDCC99)
        layout.fill(padding, 24, right, 25, 0x50FFFFFF)
        layout.skipTo(30)

        layout.text(padding, tr("cobbledex-rei-emi-jei.evo.section.chain"), 0xEEEEEE)
        layout.line()
        layout.gap(2)

        val maxRows = PanelLayout.MAX_VISIBLE_CHAIN_ROWS
        val visibleRows = if (rows.size > maxRows) rows.take(maxRows) else rows

        for (row in visibleRows) {
            when (row) {
                is EvolutionChainBuilder.ChainRow.Pokemon -> {
                    val x = indentX + row.indent * chainIndent
                    pokemonSlots.add(PokemonSlotDef(row.species, row.aspects, x, layout.y, SlotRole.INPUT,
                        disableBackground = false, disableHighlight = false))
                    layout.textAt(x + afterIcon, layout.y + 5, row.displayName, 0xFFFFFF)
                    layout.gap(22)
                }
                is EvolutionChainBuilder.ChainRow.Arrow -> {
                    val x = indentX + row.indent * chainIndent + 4
                    val hasItems = row.items.isNotEmpty()
                    val itemSpace = if (hasItems) 20 else 0
                    val reqText = if (row.requirement.isNotBlank()) "\u2193 ${row.requirement}" else "\u2193"
                    layout.clipped(x, reqText, right - x - itemSpace, 0xFFDD88)
                    if (hasItems) {
                        for ((i, item) in row.items.withIndex()) {
                            itemSlots.add(ItemSlotDef(item.itemId, right - 18 * (row.items.size - i), layout.y, SlotRole.INPUT))
                        }
                    }
                    layout.gap(if (hasItems) 20 else 14)
                }
                is EvolutionChainBuilder.ChainRow.Branch -> {
                    val x = indentX + row.indent * chainIndent
                    val prefixW = font.width("\u251C ")
                    layout.textAt(x, layout.y + 5, "\u251C", 0x888888)
                    val slotX = x + prefixW
                    pokemonSlots.add(PokemonSlotDef(row.species, row.aspects, slotX, layout.y, SlotRole.INPUT,
                        disableBackground = false, disableHighlight = false))
                    val hasItems = row.items.isNotEmpty()
                    val itemSpace = if (hasItems) 20 else 0
                    if (row.requirement.isNotBlank()) {
                        val reqX = slotX + afterIcon + font.width(row.displayName) + 4
                        val maxReqW = right - reqX - itemSpace
                        if (maxReqW > 10) {
                            layout.clipped(reqX, "\u2014 ${row.requirement}", maxReqW, 0xFFDD88)
                        }
                    }
                    layout.textAt(slotX + afterIcon, layout.y + 5, row.displayName, 0xFFFFFF)
                    if (hasItems) {
                        for ((i, item) in row.items.withIndex()) {
                            itemSlots.add(ItemSlotDef(item.itemId, right - 18 * (row.items.size - i), layout.y + 1, SlotRole.INPUT))
                        }
                    }
                    layout.gap(22)
                }
            }
        }

        if (rows.size > maxRows) {
            val overflow = rows.size - maxRows
            val overflowSpecies = rows.drop(maxRows).mapNotNull { row ->
                when (row) {
                    is EvolutionChainBuilder.ChainRow.Pokemon -> row.displayName
                    is EvolutionChainBuilder.ChainRow.Branch -> row.displayName
                    else -> null
                }
            }.distinct()
            val moreText = "+$overflow more rows (${ overflowSpecies.size } Pokémon)..."
            val moreY = layout.y
            layout.text(indentX, moreText, 0xFF999999.toInt())
            layout.line()
            val overflowTooltip = overflowSpecies.map { name ->
                Component.literal(name).withStyle { s -> s.withColor(0xFFFFFF) }
            }
            layout.addTooltipZone(indentX, moreY, font.width(moreText), PanelLayout.LINE_HEIGHT, overflowTooltip)
        }

        val allSpecies = EvolutionChainBuilder.collectAllSpecies(chain)
        layout.gap(1)
        layout.separator(0x20FFFFFF)
        layout.gap(4)
        layout.text(padding, tr("cobbledex-rei-emi-jei.evo.chain_count", allSpecies.size), 0x888888)
        layout.gap(font.lineHeight + padding)

        return ChainLayoutResult(layout, pokemonSlots, itemSlots)
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
        val speciesDisplay = formatSpeciesName(speciesName)
        val headerText = tr("category.cobbledex-rei-emi-jei.stats")
        val bstText = tr("cobbledex-rei-emi-jei.stats.bst", bst)
        val width = computePanelWidth(
            measureHeaderWidth(Minecraft.getInstance().font, speciesDisplay, headerText),
            210
        )
        val layout = PanelLayout(width)
        val font = layout.font
        val padding = PanelLayout.PADDING
        val right = layout.right
        val lineHeight = 13

        drawHeader(layout, speciesDisplay, headerText, 0xDDCC99, dividerY = 20)

        val typeStr = buildString {
            append(formatTypeName(primaryType))
            secondaryType?.let { append(" / ${formatTypeName(it)}") }
        }

        val bstColor = when {
            bst >= 600 -> 0xFFFF5555.toInt()
            bst >= 500 -> 0xFFFFCC33.toInt()
            bst >= 400 -> 0xFF77CC55.toInt()
            else -> 0xFFBBBBBB.toInt()
        }
        drawSplitRow(layout, padding, typeStr, bstText, typeColor(primaryType), bstColor, yPos = 25)

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
            layout.wrapped(padding, evText, right - padding, 0xFF88CCFF.toInt())
        }

        return layout
    }

    private fun typeColor(type: String): Int = TYPE_COLORS[type.lowercase()] ?: 0xFFBBBBBB.toInt()

    // --- Pokédex Info layout builder ---

    fun buildPokedexInfoLayout(data: PokedexInfoRecipeData): PanelLayout {
        val speciesDisplay = formatSpeciesName(data.speciesName)
        val headerText = tr("category.cobbledex-rei-emi-jei.pokedex_info")
        val width = computePanelWidth(
            measureHeaderWidth(Minecraft.getInstance().font, speciesDisplay, headerText),
            220
        )
        val layout = PanelLayout(width)
        val padding = PanelLayout.PADDING
        val right = layout.right
        val lineHeight = PanelLayout.LINE_HEIGHT
        val indentX = padding + 4
        val indentWidth = right - indentX

        drawHeader(layout, speciesDisplay, headerText, 0xDDCC99, dividerY = 20)
        layout.skipTo(26)

        if (data.abilities.isNotEmpty() || data.hiddenAbility != null) {
            layout.text(padding, tr("cobbledex-rei-emi-jei.info.abilities"), 0xEEEEEE)
            layout.line()
            for (ability in data.abilities) {
                val abilityY = layout.y
                val abilityText = "\u2022 ${formatAbilityName(ability)}"
                val lineCount = layout.wrapped(indentX, abilityText, indentWidth, 0xFF88CCFF.toInt())
                val tooltip = buildAbilityTooltip(ability)
                if (tooltip.isNotEmpty()) {
                    layout.addTooltipZone(indentX, abilityY, right - indentX, lineHeight * lineCount, tooltip)
                }
            }
            data.hiddenAbility?.let { ha ->
                val haY = layout.y
                val haText = "\u2022 ${formatAbilityName(ha)} ${tr("cobbledex-rei-emi-jei.info.hidden_ability")}"
                val lineCount = layout.wrapped(indentX, haText, indentWidth, 0xFF66AADD.toInt())
                val tooltip = buildAbilityTooltip(ha, hidden = true)
                if (tooltip.isNotEmpty()) {
                    layout.addTooltipZone(indentX, haY, right - indentX, lineHeight * lineCount, tooltip)
                }
            }
            layout.gap(3)
        }

        if (data.eggGroups.isNotEmpty()) {
            layout.text(padding, tr("cobbledex-rei-emi-jei.info.egg_groups"), 0xEEEEEE)
            layout.line()
            val groupText = data.eggGroups.joinToString(", ") { formatEggGroupName(it) }
            layout.wrapped(indentX, groupText, indentWidth, 0xFFDDDD88.toInt())
            layout.gap(3)
        }

        layout.text(padding, tr("cobbledex-rei-emi-jei.info.physical"), 0xEEEEEE)
        layout.line()
        val heightText = "%.1fm".format(data.height / 10f)
        val weightText = "%.1fkg".format(data.weight / 10f)
        layout.wrapped(indentX, tr("cobbledex-rei-emi-jei.info.height_weight", heightText, weightText), indentWidth, 0xBBBBBB)

        layout.wrapped(indentX, tr("cobbledex-rei-emi-jei.info.catch_rate", data.catchRate), indentWidth, 0xBBBBBB)

        data.maleRatio?.let { ratio ->
            val genderText = when {
                ratio < 0 -> tr("cobbledex-rei-emi-jei.info.genderless")
                ratio == 1f -> tr("cobbledex-rei-emi-jei.info.male_only")
                ratio == 0f -> tr("cobbledex-rei-emi-jei.info.female_only")
                else -> tr("cobbledex-rei-emi-jei.info.gender_ratio", "%.0f".format(ratio * 100), "%.0f".format((1 - ratio) * 100))
            }
            layout.wrapped(indentX, genderText, indentWidth, 0xBBBBBB)
        }

        if (data.shoulderMountable) {
            layout.wrapped(indentX, tr("cobbledex-rei-emi-jei.info.shoulder"), indentWidth, 0xFF88DDAA.toInt())
        }
        layout.gap(3)

        data.source?.let { ns ->
            if (ns != "cobblemon") {
                layout.wrapped(padding, tr("cobbledex-rei-emi-jei.info.added_by", titleCase(ns)), right - padding, 0xFF888888.toInt(), shadow = false)
                layout.gap(3)
            }
        }

        data.eggCycles?.let { cycles ->
            layout.text(padding, tr("cobbledex-rei-emi-jei.info.breeding"), 0xEEEEEE)
            layout.line()
            layout.wrapped(indentX, tr("cobbledex-rei-emi-jei.info.egg_cycles", cycles, cycles * 257), indentWidth, 0xBBBBBB)
        }

        if (data.experienceGroup != null || data.baseExperienceYield != null || data.baseFriendship != null) {
            layout.gap(1)
            layout.text(padding, tr("cobbledex-rei-emi-jei.info.training"), 0xEEEEEE)
            layout.line()
            data.experienceGroup?.let { group ->
                layout.wrapped(indentX, tr("cobbledex-rei-emi-jei.info.exp_group", formatExpGroup(group)), indentWidth, 0xBBBBBB)
            }
            data.baseExperienceYield?.let { exp ->
                layout.wrapped(indentX, tr("cobbledex-rei-emi-jei.info.base_exp", exp), indentWidth, 0xBBBBBB)
            }
            data.baseFriendship?.let { friendship ->
                layout.wrapped(indentX, tr("cobbledex-rei-emi-jei.info.base_friendship", friendship), indentWidth, 0xBBBBBB)
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
        val rowY = layout.y

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

        val tooltip = buildMoveTooltip(move)
        layout.addTooltipZone(padding, rowY, right - padding, PanelLayout.LINE_HEIGHT, tooltip)
    }

    fun buildMovesLayout(data: MovesRecipeData): PanelLayout {
        val headerText = if (data.pageTotal > 1)
            tr("category.cobbledex-rei-emi-jei.moves") + " (" + tr("cobbledex-rei-emi-jei.moves.page", data.pageIndex, data.pageTotal) + ")"
        else
            tr("category.cobbledex-rei-emi-jei.moves")
        val width = computePanelWidth(
            measureHeaderWidth(Minecraft.getInstance().font, formatSpeciesName(data.speciesName), headerText),
            220
        )
        val layout = PanelLayout(width)
        val padding = PanelLayout.PADDING
        val right = layout.right

        drawHeader(layout, formatSpeciesName(data.speciesName), headerText, 0xDDCC99, dividerY = 20)
        val colHeader = tr("cobbledex-rei-emi-jei.moves.pow_acc")
        layout.clippedRightAt(22, colHeader, right - padding - 60, 0xFF888888.toInt())
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
        val padding = PanelLayout.PADDING
        val headerText = tr("category.cobbledex-rei-emi-jei.fossils")
        val width = computePanelWidth(
            measureHeaderWidth(Minecraft.getInstance().font, formatSpeciesName(data.speciesName), headerText),
            200
        )
        val layout = PanelLayout(width)
        val right = layout.right
        val lineHeight = PanelLayout.LINE_HEIGHT

        drawHeader(layout, formatSpeciesName(data.speciesName), headerText, 0xDDCC99, dividerY = 20)
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
        val headerText = tr("category.cobbledex-rei-emi-jei.type_chart")
        val width = computePanelWidth(
            measureHeaderWidth(Minecraft.getInstance().font, formatSpeciesName(data.speciesName), headerText),
            200
        )
        val layout = PanelLayout(width)
        val padding = PanelLayout.PADDING
        val right = layout.right

        drawHeader(layout, formatSpeciesName(data.speciesName), headerText, 0xDDCC99, dividerY = 20)

        val typeStr = buildString {
            append(formatTypeName(data.primaryType))
            data.secondaryType?.let { append(" / ${formatTypeName(it)}") }
        }
        layout.clippedAt(padding, 25, typeStr, right - padding, typeColor(data.primaryType))
        layout.skipTo(40)

        if (data.weaknesses.isNotEmpty()) {
            layout.text(padding, tr("cobbledex-rei-emi-jei.typechart.weak"), 0xFFFF6666.toInt())
            layout.line()
            for ((type, mult) in data.weaknesses) {
                val multText = if (mult == 4f) "\u00D74" else "\u00D72"
                val color = if (mult == 4f) 0xFFFF4444.toInt() else 0xFFFF8866.toInt()
                drawSplitRow(layout, padding + 4, "\u2022 ${formatTypeName(type)}", multText, typeColor(type), color)
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
                drawSplitRow(layout, padding + 4, "\u2022 ${formatTypeName(type)}", multText, typeColor(type), color)
                layout.line()
            }
            layout.gap(PanelLayout.SECTION_GAP)
        }

        if (data.immunities.isNotEmpty()) {
            layout.text(padding, tr("cobbledex-rei-emi-jei.typechart.immune"), 0xFF9999FF.toInt())
            layout.line()
            for (type in data.immunities) {
                drawSplitRow(layout, padding + 4, "\u2022 ${formatTypeName(type)}", "\u00D70", typeColor(type), 0xFF9999FF.toInt())
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
        val headerText = tr("category.cobbledex-rei-emi-jei.pokemon_description")
        val width = computePanelWidth(
            measureHeaderWidth(Minecraft.getInstance().font, formatSpeciesName(data.speciesName), headerText),
            200
        )
        val layout = PanelLayout(width)
        val padding = PanelLayout.PADDING
        val right = layout.right

        drawHeader(layout, formatSpeciesName(data.speciesName), headerText, 0xDDCC99, dividerY = 20)
        layout.skipTo(26)

        val descText = tr(data.description)
        if (descText != data.description && descText.isNotBlank()) {
            val maxLines = PanelLayout.MAX_DESCRIPTION_LINES
            val allLines = wrapText(layout.font, descText, right - padding * 2 - 4)
            val visibleLines = if (allLines.size > maxLines) allLines.take(maxLines) else allLines
            for (line in visibleLines) {
                layout.text(padding + 4, line, 0xFFDDDDDD.toInt())
                layout.line()
            }
            if (allLines.size > maxLines) {
                val moreText = "..."
                val moreY = layout.y
                layout.text(padding + 4, moreText, 0xFF999999.toInt())
                layout.line()
                val overflowTooltip = allLines.drop(maxLines).map {
                    Component.literal(it).withStyle { s -> s.withColor(0xDDDDDD) }
                }
                layout.addTooltipZone(padding + 4, moreY, layout.font.width(moreText), PanelLayout.LINE_HEIGHT, overflowTooltip)
            }
        }

        layout.gap(padding + 4)
        return layout
    }

    // --- Cobbleworkers Jobs layout builder ---

    private val JOB_ICON_COLORS = mapOf(
        "apricorn_harvester" to 0xFFFF9933.toInt(),
        "amethyst_harvester" to 0xFFCC88FF.toInt(),
        "berry_harvester" to 0xFFFF6688.toInt(),
        "crop_harvester" to 0xFF88CC44.toInt(),
        "mint_harvester" to 0xFF66DDAA.toInt(),
        "netherwart_harvester" to 0xFFCC3333.toInt(),
        "tumblestone_harvester" to 0xFF88AACC.toInt(),
        "crop_irrigator" to 0xFF44AAFF.toInt(),
        "lava_generator" to 0xFFFF6600.toInt(),
        "water_generator" to 0xFF3399FF.toInt(),
        "snow_generator" to 0xFFAADDFF.toInt(),
        "fuel_generator" to 0xFFFF8833.toInt(),
        "brewing_stand_fuel_generator" to 0xFFDD66FF.toInt(),
        "fishing_loot_generator" to 0xFF3388CC.toInt(),
        "pickup_looter" to 0xFFDDAA33.toInt(),
        "dive_looter" to 0xFF2277BB.toInt(),
        "ground_item_gatherer" to 0xFFBB88CC.toInt(),
        "fire_extinguisher" to 0xFF44CCFF.toInt(),
        "honey_collector" to 0xFFFFCC00.toInt(),
        "archeologist" to 0xFFCC9966.toInt(),
        "healer" to 0xFFFF88AA.toInt(),
        "scout" to 0xFF66BBFF.toInt(),
    )

    private val PRIORITY_SYMBOLS = mapOf(
        "COMBO" to "\u2726",
        "MOVE" to "\u2694",
        "SPECIES" to "\u2605",
        "TYPE" to "\u25C6",
    )

    fun buildJobLayout(speciesName: String, match: JobMatch): PanelLayout {
        val speciesDisplay = formatSpeciesName(speciesName)
        val width = computePanelWidth(
            measureHeaderWidth(Minecraft.getInstance().font, speciesDisplay, match.rule.displayName),
            220
        )
        val layout = PanelLayout(width)
        val padding = PanelLayout.PADDING
        val right = layout.right
        val rule = match.rule
        val jobColor = JOB_ICON_COLORS[rule.id] ?: 0xFFAAAAFF.toInt()
        val contentWidth = right - (padding + 8)

        drawHeader(layout, speciesDisplay, rule.displayName, jobColor, dividerY = 20)
        layout.skipTo(26)

        // Job description
        layout.wrapped(padding + 4, rule.description, right - padding - 4, 0xFFCCCCCC.toInt())
        layout.gap(6)
        layout.separator(0x30FFFFFF)
        layout.gap(4)

        // Requirements
        layout.text(padding, "Requirements", 0xFFDDAA44.toInt())
        layout.line()
        layout.gap(2)

        var hasReqs = false

        if (rule.requiredType != null && rule.requiredType != "NONE") {
            layout.wrapped(padding + 8, "\u2022 ${rule.requiredType.lowercase().replaceFirstChar { it.uppercase() }} type", contentWidth, 0xFFBBBBBB.toInt())
            hasReqs = true
        }

        if (rule.requiredMoves.isNotEmpty()) {
            val isCombo = rule.priority.equals("COMBO", ignoreCase = true)
            val label = if (isCombo) "Must know all moves:" else "Must know move:"
            layout.wrapped(padding + 8, "\u2022 $label", contentWidth, 0xFFBBBBBB.toInt())
            for (move in rule.requiredMoves) {
                layout.wrapped(padding + 16, "- ${move.replaceFirstChar { it.uppercase() }}", right - (padding + 16), 0xFFAAAA88.toInt())
            }
            hasReqs = true
        }

        if (rule.requiredAbility != null) {
            layout.wrapped(padding + 8, "\u2022 Ability: ${rule.requiredAbility.replaceFirstChar { it.uppercase() }}", contentWidth, 0xFFBBBBBB.toInt())
            hasReqs = true
        }

        if (rule.designatedSpecies.isNotEmpty()) {
            layout.wrapped(padding + 8, "\u2022 Designated species list", contentWidth, 0xFFBBBBBB.toInt())
            hasReqs = true
        }

        if (rule.hardcodedSpeciesEnabled && rule.hardcodedSpecies.isNotEmpty()) {
            val names = rule.hardcodedSpecies.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
            layout.wrapped(padding + 8, "\u2022 Special: $names", contentWidth, 0xFFBBBBBB.toInt())
            hasReqs = true
        }

        if (!hasReqs) {
            layout.wrapped(padding + 8, "\u2022 No specific requirements", contentWidth, 0xFF999999.toInt())
        }

        layout.gap(4)
        layout.separator(0x30FFFFFF)
        layout.gap(4)

        // Qualifications
        layout.wrapped(padding, "Why ${formatSpeciesName(speciesName)} Qualifies", right - padding, 0xFF77BB77.toInt())
        layout.gap(2)

        for (reason in match.reasons) {
            layout.wrapped(padding + 8, "\u2713 $reason", contentWidth, 0xFF88DD88.toInt())
        }

        layout.gap(padding)
        return layout
    }

    // --- TM learner layout builder ---

    fun buildTmLearnerLayout(data: TmLearnerRecipeData): PanelLayout {
        val pageText = if (data.pageTotal > 1) "${data.pageIndex}/${data.pageTotal}" else ""
        val width = computePanelWidth(
            measureHeaderWidth(Minecraft.getInstance().font, formatSpeciesName(data.speciesName), pageText),
            200
        )
        val layout = PanelLayout(width)
        val padding = PanelLayout.PADDING
        val right = layout.right

        drawHeader(layout, formatSpeciesName(data.speciesName), pageText, 0xFF888888.toInt(), dividerY = 20)
        layout.skipTo(24)

        // Move info
        val moveKey = "cobblemon.move.${data.moveName}"
        val displayName = tr(moveKey).let { if (it == moveKey) titleCase(data.moveName) else it }
        val detail = data.moveDetail
        if (detail != null) {
            val suffix = formatMoveSuffix(detail)
            val suffixColor = CATEGORY_ICONS[detail.category]?.second ?: 0xFFBBBBBB.toInt()
            drawSplitRow(layout, padding, displayName, suffix, typeColor(detail.type), suffixColor)
            layout.line()

            val ppText = "PP: ${detail.pp}"
            layout.text(padding + 4, ppText, 0xFFAAAAAA.toInt())
            layout.line()
        } else {
            layout.wrapped(padding, displayName, right - padding, 0xFFFFFF)
        }

        layout.gap(3)
        layout.separator()
        layout.gap(3)

        layout.text(padding, "Learn Methods", 0xFFDDCC99.toInt())
        layout.line()
        layout.gap(2)

        for (method in data.learnMethods) {
            val label = if (method.detail != null) "${method.label} (${method.detail})" else method.label
            layout.wrapped(padding + 6, "\u2726 $label", right - (padding + 6), 0xFF88DD88.toInt())
        }

        if (data.learnMethods.isEmpty()) {
            layout.wrapped(padding + 6, "\u2726 TM", right - (padding + 6), 0xFF88DD88.toInt())
        }

        layout.gap(padding)
        return layout
    }

    // --- Alternate Forms layout ---

    data class FormLayoutResult(
        val layout: PanelLayout,
        val pokemonSlots: List<PokemonSlotDef>
    )

    fun buildFormLayout(data: FormRecipeData): FormLayoutResult {
        val font = Minecraft.getInstance().font
        val padding = PanelLayout.PADDING
        val baseDisplay = formatSpeciesName(data.baseSpeciesName)
        val headerTag = tr("category.cobbledex-rei-emi-jei.forms")
        val iconSize = 20
        val afterIcon = iconSize + 2
        val lineHeight = 13

        val width = computePanelWidth(measureHeaderWidth(font, baseDisplay, headerTag), 200)
        val layout = PanelLayout(width)
        val right = layout.right
        val pokemonSlots = mutableListOf<PokemonSlotDef>()

        pokemonSlots.add(PokemonSlotDef(data.baseSpeciesName, emptySet(), padding, 2, SlotRole.INPUT))
        drawHeader(layout, baseDisplay, headerTag, 0xDDCC99, dividerY = 24)
        layout.skipTo(30)

        val formCount = tr("cobbledex-rei-emi-jei.forms.count", data.forms.size)
        layout.text(padding, formCount, 0x888888)
        layout.line()
        layout.gap(2)

        val maxForms = PanelLayout.MAX_VISIBLE_FORMS
        val visibleForms = if (data.forms.size > maxForms) data.forms.take(maxForms) else data.forms

        for (form in visibleForms) {
            // Form sprite + name
            pokemonSlots.add(PokemonSlotDef(
                form.formKey, form.formAspects, padding + 2, layout.y, SlotRole.INPUT,
                disableBackground = false, disableHighlight = false
            ))
            val nameX = padding + 2 + afterIcon
            val clippedName = clipToWidth(font, form.formDisplayName, right - nameX - 2)
            layout.textAt(nameX, layout.y + 5, clippedName, 0xFFFFFF)
            layout.gap(22)

            // Types
            val typeStr = buildString {
                append(formatTypeName(form.primaryType))
                form.secondaryType?.let { append(" / ${formatTypeName(it)}") }
            }
            layout.clipped(padding + 6, typeStr, right - padding - 6, typeColor(form.primaryType))
            layout.gap(lineHeight)

            // Abilities (compact)
            val allAbilities = buildList {
                addAll(form.abilities)
                form.hiddenAbility?.let { add("$it (H)") }
            }
            if (allAbilities.isNotEmpty()) {
                val abilityY = layout.y
                val abilityStr = allAbilities.joinToString(", ")
                layout.clipped(padding + 6, "\u2605 $abilityStr", right - padding - 6, 0xFF88CCFF.toInt())
                val tooltipLines = mutableListOf<Component>()
                for (ab in form.abilities) {
                    tooltipLines.addAll(buildAbilityTooltip(ab))
                }
                form.hiddenAbility?.let { ha ->
                    if (tooltipLines.isNotEmpty()) tooltipLines.add(Component.literal(""))
                    tooltipLines.addAll(buildAbilityTooltip(ha, hidden = true))
                }
                if (tooltipLines.isNotEmpty()) {
                    layout.addTooltipZone(padding + 6, abilityY, right - padding - 6, lineHeight, tooltipLines)
                }
                layout.gap(lineHeight)
            }

            // BST
            val bst = form.baseStatTotal
            if (bst != null) {
                val bstColor = when {
                    bst >= 600 -> 0xFFFF5555.toInt()
                    bst >= 500 -> 0xFFFFCC33.toInt()
                    bst >= 400 -> 0xFF77CC55.toInt()
                    else -> 0xFFBBBBBB.toInt()
                }
                layout.text(padding + 6, tr("cobbledex-rei-emi-jei.stats.bst", bst), bstColor)
                layout.gap(lineHeight)
            }

            layout.separator(0x20FFFFFF)
            layout.gap(4)
        }

        if (data.forms.size > maxForms) {
            val overflow = data.forms.size - maxForms
            val moreText = "+$overflow more forms..."
            val moreY = layout.y
            layout.text(padding + 4, moreText, 0xFF999999.toInt())
            layout.line()
            val overflowTooltip = data.forms.drop(maxForms).map { form ->
                Component.literal(form.formDisplayName).withStyle { s -> s.withColor(0xFFFFFF) }
            }
            layout.addTooltipZone(padding + 4, moreY, font.width(moreText), PanelLayout.LINE_HEIGHT, overflowTooltip)
        }

        layout.gap(padding)
        return FormLayoutResult(layout, pokemonSlots)
    }

    // --- Riding layout builder ---

    private val MOUNT_TYPE_COLORS = mapOf(
        "LAND" to 0xFF8B6914.toInt(),
        "AIR" to 0xFF5599DD.toInt(),
        "LIQUID" to 0xFF3388AA.toInt(),
    )

    private val RIDING_STAT_COLORS = mapOf(
        "SPEED" to 0xFFFF6655.toInt(),
        "ACCEL" to 0xFFFFAA33.toInt(),
        "SKILL" to 0xFF55CC55.toInt(),
        "JUMP" to 0xFF5599FF.toInt(),
        "STAMINA" to 0xFFCC66DD.toInt(),
    )

    fun buildRidingLayout(speciesName: String, data: RidingRecipeData): PanelLayout {
        val speciesDisplay = formatSpeciesName(speciesName)
        val headerText = tr("category.cobbledex-rei-emi-jei.riding")
        val width = computePanelWidth(
            measureHeaderWidth(Minecraft.getInstance().font, speciesDisplay, headerText),
            220
        )
        val layout = PanelLayout(width)
        val font = layout.font
        val padding = PanelLayout.PADDING
        val right = layout.right
        val mount = data.mount

        drawHeader(layout, speciesDisplay, headerText, 0xDDCC99, dividerY = 20)

        // Mount types + seats summary
        layout.skipTo(26)
        val typesStr = data.allMountTypes.joinToString(" / ")
        val seatsText = if (data.seats == 1) "1 seat" else "${data.seats} seats"
        drawSplitRow(layout, padding, typesStr, seatsText, 0xFFAAAA88.toInt(), 0xFFBBBBBB.toInt())
        layout.line()
        layout.gap(4)

        // Page indicator
        if (data.mountTotal > 1) {
            val pageText = "Mount ${data.mountIndex + 1} / ${data.mountTotal}"
            layout.text(padding, pageText, 0xFFBBBBBB.toInt())
            layout.line()
            layout.gap(4)
        }

        // Single mount entry
        val typeColor = MOUNT_TYPE_COLORS[mount.mountType.uppercase()] ?: 0xFFBBBBBB.toInt()
        val styleStr = mount.ridingStyle.replaceFirstChar { it.uppercase() }
        layout.wrapped(padding, "${mount.mountType} - $styleStr", right - padding, typeColor)
        layout.gap(4)

        val stats = listOf(
            "SPEED" to (mount.speedMin to mount.speedMax),
            "ACCEL" to (mount.accelMin to mount.accelMax),
            "SKILL" to (mount.skillMin to mount.skillMax),
            "JUMP" to (mount.jumpMin to mount.jumpMax),
            "STAMINA" to (mount.staminaMin to mount.staminaMax),
        )

        val maxLabelWidth = stats.maxOf { font.width(it.first) }
        val barX = padding + maxLabelWidth + 4
        val valueSpace = 42
        val barMaxWidth = right - barX - valueSpace
        val maxStatValue = 220

        for ((statName, range) in stats) {
            val (min, max) = range
            val color = RIDING_STAT_COLORS[statName] ?: 0xFFAAAAAA.toInt()

            layout.text(padding, statName, 0xBBBBBB)

            // Background bar
            layout.fill(barX, layout.y + 1, barX + barMaxWidth, layout.y + 9, 0x30FFFFFF)
            // Min bar (darker)
            val minBarW = ((min.toFloat() / maxStatValue) * barMaxWidth).toInt().coerceAtLeast(0)
            layout.fill(barX, layout.y + 1, barX + minBarW, layout.y + 9, color)
            // Max bar (lighter overlay)
            val maxBarW = ((max.toFloat() / maxStatValue) * barMaxWidth).toInt().coerceAtLeast(0)
            val lighterColor = (color and 0x00FFFFFF) or 0x60000000
            layout.fill(barX + minBarW, layout.y + 1, barX + maxBarW, layout.y + 9, lighterColor)

            layout.textRight("$min-$max", 0xFFFFFF)
            layout.gap(13)
        }

        layout.gap(padding)
        return layout
    }

}

