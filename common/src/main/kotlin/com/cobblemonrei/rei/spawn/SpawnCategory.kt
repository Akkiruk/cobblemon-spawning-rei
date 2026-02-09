package com.cobblemonrei.rei.spawn

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.SpawnInfo
import me.shedaniel.math.Point
import me.shedaniel.math.Rectangle
import me.shedaniel.rei.api.client.gui.Renderer
import me.shedaniel.rei.api.client.gui.widgets.Widget
import me.shedaniel.rei.api.client.gui.widgets.Widgets
import me.shedaniel.rei.api.client.registry.display.DisplayCategory
import me.shedaniel.rei.api.common.category.CategoryIdentifier
import me.shedaniel.rei.api.common.util.EntryStacks
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Items

class SpawnCategory : DisplayCategory<SpawnDisplay> {

    companion object {
        val ID: CategoryIdentifier<SpawnDisplay> = CategoryIdentifier.of(
            CobblemonSpawningMod.MOD_ID, "spawns"
        )
    }

    override fun getCategoryIdentifier(): CategoryIdentifier<out SpawnDisplay> = ID

    override fun getTitle(): Component = Component.literal("Cobblemon Spawns")

    override fun getIcon(): Renderer = EntryStacks.of(Items.GRASS_BLOCK)

    override fun getDisplayHeight(): Int = 160

    override fun getFixedDisplaysPerPage(): Int = 1

    override fun setupDisplay(display: SpawnDisplay, bounds: Rectangle): List<Widget> {
        val widgets = mutableListOf<Widget>()
        widgets.add(Widgets.createRecipeBase(bounds))

        val startX = bounds.x + 6
        val startY = bounds.y + 6

        // Species name header
        val speciesName = display.speciesName.replaceFirstChar { it.uppercase() }
        widgets.add(
            Widgets.createLabel(Point(bounds.centerX, startY), Component.literal(speciesName))
                .centered().noShadow().color(0xFF333333.toInt(), 0xFFDDDDDD.toInt())
        )

        // Spawn entries
        val spawns = display.spawns
        var y = startY + 14

        for ((index, spawn) in spawns.withIndex()) {
            if (y + 24 > bounds.maxY - 4) break

            // Rarity bucket colored bar
            widgets.add(Widgets.createDrawableWidget { graphics, _, _, _ ->
                graphics.fill(startX, y, startX + 3, y + 12, spawn.bucketColor)
            })

            // Bucket label
            val bucketText = "${spawn.bucket.replaceFirstChar { it.uppercase() }} (${spawn.weight})"
            widgets.add(
                Widgets.createLabel(Point(startX + 7, y + 2), Component.literal(bucketText))
                    .leftAligned().noShadow().color(0xFF555555.toInt(), 0xFFBBBBBB.toInt())
            )

            // Level range on right
            val levelText = "Lv. ${spawn.levelRange}"
            widgets.add(
                Widgets.createLabel(Point(bounds.maxX - 8, y + 2), Component.literal(levelText))
                    .rightAligned().noShadow().color(0xFF555555.toInt(), 0xFFBBBBBB.toInt())
            )

            y += 13

            // Biome info
            val biomeStr = if (spawn.formattedBiomes.isNotEmpty()) {
                spawn.formattedBiomes.joinToString(", ")
            } else {
                "Any biome"
            }
            widgets.add(
                Widgets.createLabel(Point(startX + 7, y + 2), Component.literal("  $biomeStr"))
                    .leftAligned().noShadow().color(0xFF777777.toInt(), 0xFF999999.toInt())
            )

            y += 12

            // Conditions line (time, weather, context)
            val conditions = mutableListOf<String>()
            spawn.timeRange?.let { conditions.add(it.replaceFirstChar { c -> c.uppercase() }) }
            if (spawn.weather.displayText != "Any") conditions.add(spawn.weather.displayText)
            if (spawn.context != "grounded") conditions.add(spawn.context.replaceFirstChar { it.uppercase() })
            spawn.moonPhase?.let { conditions.add("Moon: ${it.replaceFirstChar { c -> c.uppercase() }}") }
            if (spawn.canSeeSky == true) conditions.add("Sky visible")
            if (spawn.canSeeSky == false) conditions.add("Underground")

            if (conditions.isNotEmpty()) {
                widgets.add(
                    Widgets.createLabel(
                        Point(startX + 7, y + 2),
                        Component.literal("  ${conditions.joinToString(" · ")}")
                    ).leftAligned().noShadow().color(0xFF999999.toInt(), 0xFF777777.toInt())
                )
                y += 12
            }

            // Separator between entries
            if (index < spawns.size - 1) {
                widgets.add(Widgets.createDrawableWidget { graphics, _, _, _ ->
                    graphics.fill(startX + 4, y + 1, bounds.maxX - 8, y + 2, 0x20FFFFFF)
                })
                y += 5
            }
        }

        // "And X more..." if truncated
        val shown = spawns.indexOfFirst { false }.let { spawns.size } // count shown
        if (y + 24 > bounds.maxY - 4 && spawns.size > 1) {
            widgets.add(
                Widgets.createLabel(
                    Point(bounds.centerX, bounds.maxY - 12),
                    Component.literal("... and more spawn locations")
                ).centered().noShadow().color(0xFF888888.toInt(), 0xFF888888.toInt())
            )
        }

        return widgets
    }
}
