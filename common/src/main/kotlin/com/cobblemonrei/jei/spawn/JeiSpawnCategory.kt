package com.cobblemonrei.jei.spawn

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.SpawnAntiCondition
import com.cobblemonrei.SpawnDataIndex
import com.cobblemonrei.SpawnInfo
import com.cobblemonrei.config.CobblemonSpawningConfig
import com.cobblemonrei.formatBiomeName
import com.cobblemonrei.formatId
import com.cobblemonrei.jei.PokemonIngredient
import com.cobblemonrei.jei.PokemonIngredientType
import com.cobblemonrei.titleCase
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.drawable.IDrawable
import mezz.jei.api.gui.ingredient.IRecipeSlotsView
import mezz.jei.api.helpers.IGuiHelper
import mezz.jei.api.recipe.IFocusGroup
import mezz.jei.api.recipe.RecipeIngredientRole
import mezz.jei.api.recipe.RecipeType
import mezz.jei.api.recipe.category.IRecipeCategory
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

@Suppress("DEPRECATION")
class JeiSpawnCategory(guiHelper: IGuiHelper) : IRecipeCategory<JeiSpawnRecipe> {

    companion object {
        val RECIPE_TYPE: RecipeType<JeiSpawnRecipe> = RecipeType(
            ResourceLocation.fromNamespaceAndPath(CobblemonSpawningMod.MOD_ID, "jei_spawns"),
            JeiSpawnRecipe::class.java
        )

        private const val WIDTH = 180
        private const val HEIGHT = 200
        private const val PADDING = 6
        private const val LINE_HEIGHT = 11

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

        private val BUCKET_ORDER = listOf("common", "uncommon", "rare", "ultra-rare")

        fun bucketSortOrder(bucket: String): Int =
            BUCKET_ORDER.indexOf(bucket.lowercase()).let { if (it < 0) 99 else it }

        private val PRESET_LABELS = mapOf(
            "natural" to "Natural", "water" to "Water", "lava" to "Lava",
            "urban" to "Urban", "wild" to "Wild", "foliage" to "Foliage",
            "treetop" to "Treetop", "derelict" to "Derelict", "redstone" to "Redstone",
            "ancient_city" to "Ancient City", "desert_pyramid" to "Desert Pyramid",
            "end_city" to "End City", "jungle_pyramid" to "Jungle Pyramid",
            "mansion" to "Mansion", "nether_fossil" to "Nether Fossil",
            "nether_structures" to "Nether Structure", "ocean_monument" to "Ocean Monument",
            "ocean_ruins" to "Ocean Ruins", "pillager_outpost" to "Pillager Outpost",
            "stronghold" to "Stronghold", "trail_ruins" to "Trail Ruins"
        )

        fun bucketColor(bucket: String): Int = BUCKET_COLORS[bucket.lowercase()] ?: 0xFFAAAAAA.toInt()
        fun bucketLabel(bucket: String): String = BUCKET_LABELS[bucket.lowercase()] ?: titleCase(bucket)
    }

    private val background: IDrawable = guiHelper.createBlankDrawable(WIDTH, HEIGHT)
    private val icon: IDrawable = guiHelper.createDrawableItemStack(ItemStack(Items.GRASS_BLOCK))

    override fun getRecipeType(): RecipeType<JeiSpawnRecipe> = RECIPE_TYPE
    override fun getTitle(): Component = Component.literal("Spawn Locations")
    override fun getBackground(): IDrawable = background
    override fun getIcon(): IDrawable = icon

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: JeiSpawnRecipe, focuses: IFocusGroup) {
        builder.addSlot(RecipeIngredientRole.OUTPUT, PADDING, 2)
            .setSlotName("pokemon")
            .addIngredient(PokemonIngredientType, PokemonIngredient(recipe.speciesName))
    }

    override fun draw(recipe: JeiSpawnRecipe, recipeSlotsView: IRecipeSlotsView, graphics: GuiGraphics, mouseX: Double, mouseY: Double) {
        val font = net.minecraft.client.Minecraft.getInstance().font
        val spawn = recipe.spawn
        val color = bucketColor(spawn.bucket)
        val right = WIDTH - PADDING

        // Title
        val title = titleCase(recipe.speciesName)
        graphics.drawString(font, title, PADDING + 22, 6, 0xFFFFFF, false)

        // Level + rarity
        graphics.drawString(font, "Lv. ${spawn.levelRange}", PADDING, 22, 0x0099FF, false)
        val bucketText = bucketLabel(spawn.bucket)
        val bucketWidth = font.width(bucketText)
        graphics.drawString(font, bucketText, right - bucketWidth, 22, color, false)

        // Separator
        graphics.fill(PADDING, 36, right, 37, 0x50FFFFFF)

        var y = 42

        // Context line
        val ctxParts = mutableListOf<String>()
        if (spawn.context != "grounded") ctxParts.add(spawn.displayContext)
        if (spawn.presets.isNotEmpty()) {
            ctxParts.add(spawn.presets.mapNotNull { PRESET_LABELS[it] ?: titleCase(it) }.joinToString(", "))
        }
        if (recipe.mergedFormVariants.isNotEmpty()) {
            ctxParts.add("Forms: ${recipe.mergedFormVariants.joinToString(", ")}")
        } else if (spawn.hasFormVariant) {
            ctxParts.add("Form: ${titleCase(spawn.formAspects.replace("region_bias=", ""))}")
        }

        if (CobblemonSpawningConfig.get().showSpawnWeights && spawn.weight > 0f) {
            val wt = formatWeight(spawn.weight)
            val wtText = "Weight: $wt"
            val wtWidth = font.width(wtText)
            graphics.drawString(font, wtText, right - wtWidth, y, 0xBBBBBB, false)
        }
        if (ctxParts.isNotEmpty()) {
            graphics.drawString(font, clip(ctxParts.joinToString(" \u00B7 "), 30), PADDING + 4, y, 0xDDDDDD, false)
        }
        y += LINE_HEIGHT + 4

        // Biomes
        val biomeNames = spawn.biomes.map { formatBiomeName(it) }
        if (biomeNames.isNotEmpty()) {
            val header = if (biomeNames.size > 1) "\u2302 Biomes (any of)" else "\u2302 Biome"
            graphics.drawString(font, header, PADDING, y, 0xEEEEEE, false)
            y += LINE_HEIGHT
            for (line in wrapText(biomeNames.joinToString(", "), 30).take(3)) {
                graphics.drawString(font, line, PADDING + 6, y, 0xDDDDDD, false)
                y += LINE_HEIGHT
            }
            y += 3
        }

        // Conditions
        val conditions = buildConditions(spawn)
        if (conditions.isNotEmpty()) {
            graphics.drawString(font, "\u2699 Conditions", PADDING, y, 0xEEEEEE, false)
            y += LINE_HEIGHT
            for (cond in conditions) {
                if (y + LINE_HEIGHT > HEIGHT - 16) break
                graphics.drawString(font, cond, PADDING + 6, y, 0xDDDDDD, false)
                y += LINE_HEIGHT
            }
            y += 3
        }

        // Location specials
        val specials = buildSpecials(spawn)
        if (specials.isNotEmpty()) {
            graphics.drawString(font, "\u2605 Location", PADDING, y, 0xEEEEEE, false)
            y += LINE_HEIGHT
            for (s in specials) {
                if (y + LINE_HEIGHT > HEIGHT - 16) break
                graphics.drawString(font, s, PADDING + 6, y, 0xFFCC66, false)
                y += LINE_HEIGHT
            }
            y += 3
        }

        // Exclusions
        val anti = spawn.anticondition
        if (anti != null && !anti.isEmpty) {
            val lines = buildExclusionLines(anti)
            if (lines.isNotEmpty()) {
                graphics.drawString(font, "\u2718 Excluded", PADDING, y, 0xFF7777, false)
                y += LINE_HEIGHT
                for (line in lines) {
                    if (y + LINE_HEIGHT > HEIGHT - 16) break
                    graphics.drawString(font, line, PADDING + 6, y, 0xEE8888, false)
                    y += LINE_HEIGHT
                }
                y += 3
            }
        }

        // Weight multipliers
        if (CobblemonSpawningConfig.get().showSpawnWeights && spawn.weightMultipliers.isNotEmpty()) {
            graphics.drawString(font, "\u25B2 Weight Modifiers", PADDING, y, 0xEEEEEE, false)
            y += LINE_HEIGHT
            for (wm in spawn.weightMultipliers) {
                if (y + LINE_HEIGHT > HEIGHT - 16) break
                val arrow: String
                val c: Int
                when {
                    wm.multiplier > 1f -> { arrow = "\u25B2"; c = 0x88DD88 }
                    wm.multiplier < 1f -> { arrow = "\u25BC"; c = 0xEE8888 }
                    else -> { arrow = "\u25CF"; c = 0xBBBBBB }
                }
                graphics.drawString(font, "$arrow ${formatWeight(wm.multiplier)}x ${clip(wm.conditionSummary, 28)}", PADDING + 6, y, c, false)
                y += LINE_HEIGHT
            }
        }

        // Footer
        val footerY = HEIGHT - PADDING - 2
        graphics.fill(PADDING, footerY - 4, right, footerY - 3, 0x20FFFFFF)
        val footerLeft = "${bucketLabel(spawn.bucket)} ${recipe.bucketIndex}/${recipe.bucketTotal}"
        graphics.drawString(font, footerLeft, PADDING, footerY, color, false)
    }

    // --- Helpers (matching REI SpawnCategory logic) ---

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
            val icon = when (weather) { "Thunder" -> "\u26A1 "; "Rain" -> "\u2602 "; "Clear" -> "\u2600 "; else -> "" }
            list.add("$icon$weather")
        }
        if (spawn.canSeeSky == true) list.add("Open sky")
        if (spawn.canSeeSky == false) list.add("Underground")
        if (spawn.minSkyLight != null || spawn.maxSkyLight != null) {
            val min = spawn.minSkyLight ?: 0; val max = spawn.maxSkyLight ?: 15
            when {
                min == 0 && max <= 7 -> list.add("Dark (sky light \u2264$max)")
                min >= 8 -> list.add("Bright (sky light \u2265$min)")
                else -> list.add("Sky light $min\u2013$max")
            }
        }
        if (spawn.minLight != null || spawn.maxLight != null) {
            val min = spawn.minLight ?: 0; val max = spawn.maxLight ?: 15
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
        if (structNames.isNotEmpty()) list.add("Near structure: ${clip(structNames.joinToString(", "), 34)}")
        if (spawn.dimensions.isNotEmpty()) list.add("Dimension: ${spawn.dimensions.joinToString(", ") { formatDimension(it) }}")
        spawn.fluid?.let {
            val name = when { it.contains("water") -> "Water"; it.contains("lava") -> "Lava"; else -> formatId(it) }
            list.add("In fluid: $name")
        }
        if (spawn.neededBaseBlocks.isNotEmpty()) {
            val names = spawn.neededBaseBlocks.map { formatId(it) }
            val redundant = structNames.isNotEmpty() && names.all { it.lowercase().contains("structure") }
            if (!redundant) list.add("On block: ${clip(names.joinToString(", "), 34)}")
        }
        if (spawn.neededNearbyBlocks.isNotEmpty()) {
            val names = spawn.neededNearbyBlocks.map { formatId(it) }
            val redundant = structNames.isNotEmpty() && names.all { it.lowercase().contains("structure") }
            if (!redundant) list.add("Near block: ${clip(names.joinToString(", "), 34)}")
        }
        return list
    }

    private fun buildExclusionLines(anti: SpawnAntiCondition): List<String> {
        val lines = mutableListOf<String>()
        if (anti.biomes.isNotEmpty()) lines.add("Biomes: ${anti.biomes.map { formatBiomeName(it) }.joinToString(", ")}")
        if (anti.structures.isNotEmpty()) lines.add("Structures: ${anti.structures.map { formatId(it) }.joinToString(", ")}")
        if (anti.minY != null || anti.maxY != null) {
            val r = listOfNotNull(anti.minY?.let { "Y \u2265 $it" }, anti.maxY?.let { "Y \u2264 $it" })
            lines.add("Height: ${r.joinToString(", ")}")
        }
        return lines
    }

    private fun formatWeight(weight: Float): String =
        if (weight == weight.toLong().toFloat()) weight.toLong().toString() else "%.1f".format(weight)

    private fun formatDimension(dim: String): String = when (dim.lowercase()) {
        "minecraft:overworld" -> "Overworld"; "minecraft:the_nether" -> "Nether"
        "minecraft:the_end" -> "The End"; else -> formatId(dim)
    }

    private fun wrapText(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val items = text.split(", ")
        val lines = mutableListOf<String>()
        var current = ""
        for (item in items) {
            val next = if (current.isEmpty()) item else "$current, $item"
            if (next.length > maxChars && current.isNotEmpty()) { lines.add(current); current = item }
            else current = next
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines
    }

    private fun clip(text: String, maxLen: Int): String =
        if (text.length > maxLen) text.take(maxLen - 1) + "\u2026" else text
}
