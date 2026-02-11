package com.cobblemonrei.rei.spawn

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.SpawnDataIndex
import com.cobblemonrei.SpawnInfo
import com.cobblemonrei.SpawnDisplayHelper.PRESET_LABELS
import com.cobblemonrei.SpawnDisplayHelper.buildConditions
import com.cobblemonrei.SpawnDisplayHelper.buildExclusionLines
import com.cobblemonrei.SpawnDisplayHelper.buildSpecials
import com.cobblemonrei.SpawnDisplayHelper.clip
import com.cobblemonrei.SpawnDisplayHelper.formatFormAspects
import com.cobblemonrei.SpawnDisplayHelper.formatWeight
import com.cobblemonrei.SpawnDisplayHelper.wrapText
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

        fun bucketColor(bucket: String) = com.cobblemonrei.SpawnDisplayHelper.bucketColor(bucket)
        fun bucketLabel(bucket: String) = com.cobblemonrei.SpawnDisplayHelper.bucketLabel(bucket)
        fun bucketSortOrder(bucket: String) = com.cobblemonrei.SpawnDisplayHelper.bucketSortOrder(bucket)
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

}
