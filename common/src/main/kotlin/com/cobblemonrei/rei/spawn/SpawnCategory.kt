package com.cobblemonrei.rei.spawn

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.SpawnDataIndex
import com.cobblemonrei.SpawnInfo
import com.cobblemonrei.formatBiomeName
import com.cobblemonrei.formatId
import com.cobblemonrei.titleCase
import com.cobblemonrei.config.CobblemonSpawningConfig
import com.cobblemonrei.rei.entry.PokemonEntry
import com.cobblemonrei.rei.entry.PokemonEntryType
import me.shedaniel.math.Point
import me.shedaniel.math.Rectangle
import me.shedaniel.rei.api.client.gui.Renderer
import me.shedaniel.rei.api.client.gui.widgets.Widget
import me.shedaniel.rei.api.client.gui.widgets.Widgets
import me.shedaniel.rei.api.client.registry.display.DisplayCategory
import me.shedaniel.rei.api.common.category.CategoryIdentifier
import me.shedaniel.rei.api.common.entry.EntryStack
import me.shedaniel.rei.api.common.util.EntryStacks
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Items

class SpawnCategory : DisplayCategory<SpawnDisplay> {

    companion object {
        val ID: CategoryIdentifier<SpawnDisplay> = CategoryIdentifier.of(
            CobblemonSpawningMod.MOD_ID, "spawns"
        )

        private const val PADDING = 8
        private const val SECTION_GAP = 4
        private const val LINE_HEIGHT = 11
        private const val HEADER_HEIGHT = 40

        private val BUCKET_ORDER = listOf("common", "uncommon", "rare", "ultra-rare")

        private val BUCKET_COLORS = mapOf(
            "common" to 0xFF4CAF50.toInt(),
            "uncommon" to 0xFFFFC107.toInt(),
            "rare" to 0xFFFF5722.toInt(),
            "ultra-rare" to 0xFFE040FB.toInt()
        )

        private val BUCKET_LABELS = mapOf(
            "common" to "Common",
            "uncommon" to "Uncommon",
            "rare" to "Rare",
            "ultra-rare" to "Ultra Rare"
        )

        private val PRESET_LABELS = mapOf(
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
    }

    override fun getCategoryIdentifier(): CategoryIdentifier<out SpawnDisplay> = ID

    override fun getTitle(): Component = Component.literal("Spawn Locations")

    override fun getIcon(): Renderer = EntryStacks.of(Items.GRASS_BLOCK)

    override fun getDisplayHeight(): Int = 210

    override fun getFixedDisplaysPerPage(): Int = 1

    override fun setupDisplay(display: SpawnDisplay, bounds: Rectangle): List<Widget> {
        val widgets = mutableListOf<Widget>()
        widgets.add(Widgets.createRecipeBase(bounds))

        val left = bounds.x + PADDING
        val right = bounds.maxX - PADDING
        val contentWidth = right - left
        val spawn = display.spawn
        val color = bucketColor(spawn.bucket)

        // === Row 1: Pokemon icon + species name ===
        val pokemonStack = EntryStack.of(PokemonEntryType.POKEMON, PokemonEntry(display.speciesName))
        widgets.add(
            Widgets.createSlot(Rectangle(left, bounds.y + 3, 20, 20))
                .entries(listOf(pokemonStack))
                .markInput()
                .disableBackground()
                .disableHighlight()
        )

        val title = titleCase(display.speciesName)
        widgets.add(
            Widgets.createLabel(Point(left + 24, bounds.y + 9), Component.literal(title))
                .leftAligned().noShadow().color(0xFF333333.toInt(), 0xFFFFFFFF.toInt())
        )

        // Data source indicator (top-right, small)
        if (SpawnDataIndex.dataSource == SpawnDataIndex.DataSource.SERVER) {
            widgets.add(
                Widgets.createLabel(Point(right, bounds.y + 3), Component.literal("Server"))
                    .rightAligned().noShadow().color(0xFF44AA44.toInt(), 0xFF66DD66.toInt())
            )
        }

        // === Row 2: Level range (left) + Rarity (right) ===
        val row2Y = bounds.y + 24
        val lvText = "Lv. ${spawn.levelRange}"
        widgets.add(
            Widgets.createLabel(Point(left, row2Y), Component.literal(lvText))
                .leftAligned().noShadow().color(0xFF0099FF.toInt(), 0xFF00DDFF.toInt())
        )
        widgets.add(
            Widgets.createLabel(Point(right, row2Y), Component.literal(bucketLabel(spawn.bucket)))
                .rightAligned().color(color, color)
        )

        // Separator with breathing room
        val sepY = bounds.y + HEADER_HEIGHT + 2
        widgets.add(Widgets.createDrawableWidget { gfx, _, _, _ ->
            gfx.fill(left, sepY, right, sepY + 1, 0x50FFFFFF)
        })

        var y = sepY + SECTION_GAP + 4

        // === Context line: rarity pip + context/presets/forms + weight ===
        val pipY = y
        widgets.add(Widgets.createDrawableWidget { gfx, _, _, _ ->
            gfx.fill(left, pipY + 1, left + 3, pipY + LINE_HEIGHT - 1, color)
        })

        val ctxParts = mutableListOf<String>()
        if (spawn.context != "grounded") ctxParts.add(spawn.displayContext)
        if (spawn.presets.isNotEmpty()) {
            val tags = spawn.presets.mapNotNull { PRESET_LABELS[it] ?: titleCase(it) }
            ctxParts.add(tags.joinToString(", "))
        }
        if (display.mergedFormVariants.isNotEmpty()) {
            ctxParts.add("Forms: ${display.mergedFormVariants.joinToString(", ")}")
        } else if (spawn.hasFormVariant) {
            ctxParts.add("Form: ${formatFormAspects(spawn.formAspects)}")
        }

        // Weight: hide 0 or if config disables
        val weightLabel = when {
            !CobblemonSpawningConfig.get().showSpawnWeights -> null
            spawn.weight <= 0f -> null
            else -> "Weight: ${formatWeight(spawn.weight)}"
        }
        val weightReserved = (weightLabel?.length ?: 0) * 6 + 8
        val ctxMaxChars = ((contentWidth - 7 - weightReserved) / 5.5).toInt().coerceIn(10, 60)

        if (ctxParts.isNotEmpty()) {
            val ctxText = clip(ctxParts.joinToString(" \u00B7 "), ctxMaxChars)
            widgets.add(
                Widgets.createLabel(Point(left + 7, y), Component.literal(ctxText))
                    .leftAligned().noShadow().color(0xFF555555.toInt(), 0xFFDDDDDD.toInt())
            )
        }
        if (weightLabel != null) {
            widgets.add(
                Widgets.createLabel(Point(right, y), Component.literal(weightLabel))
                    .rightAligned().color(0xFF666666.toInt(), 0xFFBBBBBB.toInt())
            )
        }
        y += LINE_HEIGHT + SECTION_GAP

        // === Biomes ===
        val biomeNames = spawn.biomes.map { formatBiomeName(it) }
        if (biomeNames.isNotEmpty()) {
            val header = if (biomeNames.size > 1) "\u2302 Biomes (any of)" else "\u2302 Biome"
            widgets.add(
                Widgets.createLabel(Point(left, y), Component.literal(header))
                    .leftAligned().noShadow().color(0xFF333333.toInt(), 0xFFEEEEEE.toInt())
            )
            y += LINE_HEIGHT

            val biomeStr = biomeNames.joinToString(", ")
            val maxChars = ((contentWidth - 8) / 5.5).toInt().coerceIn(20, 80)
            for (line in wrapText(biomeStr, maxChars).take(4)) {
                widgets.add(
                    Widgets.createLabel(Point(left + 8, y), Component.literal(line))
                        .leftAligned().noShadow().color(0xFF404040.toInt(), 0xFFDDDDDD.toInt())
                )
                y += LINE_HEIGHT
            }
            y += SECTION_GAP
        }

        // === Conditions (time, weather, sky, light, Y-level, moon, fishing) ===
        val conditions = buildConditions(spawn)
        if (conditions.isNotEmpty()) {
            widgets.add(
                Widgets.createLabel(Point(left, y), Component.literal("\u2699 Conditions"))
                    .leftAligned().noShadow().color(0xFF333333.toInt(), 0xFFEEEEEE.toInt())
            )
            y += LINE_HEIGHT

            for (cond in conditions) {
                if (y + LINE_HEIGHT > bounds.maxY - 18) break
                widgets.add(
                    Widgets.createLabel(Point(left + 8, y), Component.literal(cond))
                        .leftAligned().noShadow().color(0xFF404040.toInt(), 0xFFDDDDDD.toInt())
                )
                y += LINE_HEIGHT
            }
            y += SECTION_GAP
        }

        // === Location (structures, dimensions, blocks, fluid) ===
        val specials = buildSpecials(spawn)
        if (specials.isNotEmpty()) {
            widgets.add(
                Widgets.createLabel(Point(left, y), Component.literal("\u2605 Location"))
                    .leftAligned().noShadow().color(0xFF333333.toInt(), 0xFFEEEEEE.toInt())
            )
            y += LINE_HEIGHT

            for (s in specials) {
                if (y + LINE_HEIGHT > bounds.maxY - 18) break
                widgets.add(
                    Widgets.createLabel(Point(left + 8, y), Component.literal(s))
                        .leftAligned().color(0xFF806020.toInt(), 0xFFFFCC66.toInt())
                )
                y += LINE_HEIGHT
            }
            y += SECTION_GAP
        }

        // === Excluded (anti-conditions) ===
        val anti = spawn.anticondition
        if (anti != null && !anti.isEmpty) {
            val lines = buildExclusionLines(anti)
            if (lines.isNotEmpty()) {
                widgets.add(
                    Widgets.createLabel(Point(left, y), Component.literal("\u2718 Excluded"))
                        .leftAligned().color(0xFFAA3333.toInt(), 0xFFFF7777.toInt())
                )
                y += LINE_HEIGHT

                for (line in lines) {
                    if (y + LINE_HEIGHT > bounds.maxY - 18) break
                    widgets.add(
                        Widgets.createLabel(Point(left + 8, y), Component.literal(line))
                            .leftAligned().color(0xFF993333.toInt(), 0xFFEE8888.toInt())
                    )
                    y += LINE_HEIGHT
                }
                y += SECTION_GAP
            }
        }

        // === Weight multipliers ===
        if (CobblemonSpawningConfig.get().showSpawnWeights && spawn.weightMultipliers.isNotEmpty()) {
            widgets.add(
                Widgets.createLabel(Point(left, y), Component.literal("\u25B2 Weight Modifiers"))
                    .leftAligned().noShadow().color(0xFF333333.toInt(), 0xFFEEEEEE.toInt())
            )
            y += LINE_HEIGHT

            for (wm in spawn.weightMultipliers) {
                if (y + LINE_HEIGHT > bounds.maxY - 18) break
                val arrow: String
                val lightColor: Int
                val darkColor: Int
                when {
                    wm.multiplier > 1f -> { arrow = "\u25B2"; lightColor = 0xFF336633.toInt(); darkColor = 0xFF88DD88.toInt() }
                    wm.multiplier < 1f -> { arrow = "\u25BC"; lightColor = 0xFF993333.toInt(); darkColor = 0xFFEE8888.toInt() }
                    else -> { arrow = "\u25CF"; lightColor = 0xFF555555.toInt(); darkColor = 0xFFBBBBBB.toInt() }
                }
                val condText = clip(wm.conditionSummary, 40)
                widgets.add(
                    Widgets.createLabel(
                        Point(left + 8, y),
                        Component.literal("$arrow ${formatWeight(wm.multiplier)}x $condText")
                    ).leftAligned().noShadow().color(lightColor, darkColor)
                )
                y += LINE_HEIGHT
            }
        }

        // === Footer: bucket page context + summary ===
        val footerY = bounds.maxY - PADDING - 2
        widgets.add(Widgets.createDrawableWidget { gfx, _, _, _ ->
            gfx.fill(left, footerY - 4, right, footerY - 3, 0x20FFFFFF)
        })

        val footerLeft = "${bucketLabel(spawn.bucket)} ${display.bucketIndex}/${display.bucketTotal}"
        widgets.add(
            Widgets.createLabel(Point(left, footerY), Component.literal(footerLeft))
                .leftAligned().color(color, color)
        )

        val summaryParts = mutableListOf<String>()
        if (spawn.presets.isNotEmpty()) {
            spawn.presets.mapNotNull { PRESET_LABELS[it] }.firstOrNull()?.let { summaryParts.add(it) }
        }
        if (spawn.structures.isNotEmpty()) {
            summaryParts.add(formatId(spawn.structures.first()))
        } else if (spawn.biomes.isNotEmpty()) {
            summaryParts.add(formatBiomeName(spawn.biomes.first()))
        }
        if (summaryParts.isNotEmpty()) {
            val maxFooterRight = ((contentWidth - footerLeft.length * 6 - 16) / 5.5).toInt().coerceIn(10, 40)
            val summaryText = clip(summaryParts.joinToString(" \u00B7 "), maxFooterRight)
            widgets.add(
                Widgets.createLabel(Point(right, footerY), Component.literal(summaryText))
                    .rightAligned().color(0xFF777777.toInt(), 0xFFBBBBBB.toInt())
            )
        }

        return widgets
    }

    // --- Condition builders ---

    private fun buildConditions(spawn: SpawnInfo): List<String> {
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

    private fun buildSpecials(spawn: SpawnInfo): List<String> {
        val list = mutableListOf<String>()

        val structNames = spawn.structures.map { formatId(it) }.toSet()

        if (structNames.isNotEmpty()) {
            val clipped = structNames.joinToString(", ").let { clip(it, 40) }
            list.add("Near structure: $clipped")
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
            // Skip if all base blocks look like structure blocks and we already show the structure
            val redundant = structNames.isNotEmpty() && names.all { it.lowercase().contains("structure") }
            if (!redundant) {
                list.add("On block: ${clip(names.joinToString(", "), 40)}")
            }
        }

        if (spawn.neededNearbyBlocks.isNotEmpty()) {
            val names = spawn.neededNearbyBlocks.map { formatId(it) }
            val redundant = structNames.isNotEmpty() && names.all { it.lowercase().contains("structure") }
            if (!redundant) {
                list.add("Near block: ${clip(names.joinToString(", "), 40)}")
            }
        }

        return list
    }

    private fun buildExclusionLines(anti: com.cobblemonrei.SpawnAntiCondition): List<String> {
        val lines = mutableListOf<String>()
        if (anti.biomes.isNotEmpty()) {
            val names = anti.biomes.map { formatBiomeName(it) }
            lines.add("Biomes: ${names.joinToString(", ")}")
        }
        if (anti.structures.isNotEmpty()) {
            lines.add("Structures: ${anti.structures.map { formatId(it) }.joinToString(", ")}")
        }
        if (anti.minY != null || anti.maxY != null) {
            val range = listOfNotNull(
                anti.minY?.let { "Y \u2265 $it" },
                anti.maxY?.let { "Y \u2264 $it" }
            )
            lines.add("Height: ${range.joinToString(", ")}")
        }
        return lines
    }

    // --- Formatting helpers ---

    private fun formatFormAspects(aspects: String): String {
        return titleCase(
            aspects.replace("region_bias=", "")
        )
    }

    private fun formatWeight(weight: Float): String =
        if (weight == weight.toLong().toFloat()) weight.toLong().toString() else "%.1f".format(weight)

    private fun formatDimension(dim: String): String = when (dim.lowercase()) {
        "minecraft:overworld" -> "Overworld"
        "minecraft:the_nether" -> "Nether"
        "minecraft:the_end" -> "The End"
        else -> formatId(dim)
    }

    private fun wrapText(text: String, maxChars: Int): List<String> {
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

    private fun clip(text: String, maxLen: Int): String =
        if (text.length > maxLen) text.take(maxLen - 1) + "\u2026" else text
}
