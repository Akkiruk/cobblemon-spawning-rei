package com.cobblemonrei.jei.spawn

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.SpawnDataIndex
import com.cobblemonrei.SpawnDisplayHelper
import com.cobblemonrei.SpawnDisplayHelper.PRESET_LABELS
import com.cobblemonrei.SpawnDisplayHelper.buildConditions
import com.cobblemonrei.SpawnDisplayHelper.buildExclusionLines
import com.cobblemonrei.SpawnDisplayHelper.buildSpecials
import com.cobblemonrei.SpawnDisplayHelper.clip
import com.cobblemonrei.SpawnDisplayHelper.formatWeight
import com.cobblemonrei.SpawnDisplayHelper.wrapText
import com.cobblemonrei.config.CobblemonSpawningConfig
import com.cobblemonrei.formatBiomeName
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

        fun bucketSortOrder(bucket: String): Int = SpawnDisplayHelper.bucketSortOrder(bucket)
        fun bucketColor(bucket: String): Int = SpawnDisplayHelper.bucketColor(bucket)
        fun bucketLabel(bucket: String): String = SpawnDisplayHelper.bucketLabel(bucket)
    }

    private val background: IDrawable = guiHelper.createBlankDrawable(WIDTH, HEIGHT)
    private val icon: IDrawable = guiHelper.createDrawableItemStack(ItemStack(Items.GRASS_BLOCK))

    override fun getRecipeType(): RecipeType<JeiSpawnRecipe> = RECIPE_TYPE
    override fun getTitle(): Component = Component.literal("Spawn Locations")
    override fun getBackground(): IDrawable = background
    override fun getWidth(): Int = WIDTH
    override fun getHeight(): Int = HEIGHT
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

}
