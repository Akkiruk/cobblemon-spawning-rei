package com.cobblemonrei.jei.stats

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.DisplayLayout
import com.cobblemonrei.SpawnDisplayHelper
import com.cobblemonrei.jei.PokemonIngredient
import com.cobblemonrei.jei.PokemonIngredientType
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
class JeiStatsCategory(guiHelper: IGuiHelper) : IRecipeCategory<JeiStatsRecipe> {

    companion object {
        val RECIPE_TYPE: RecipeType<JeiStatsRecipe> = RecipeType(
            ResourceLocation.fromNamespaceAndPath(CobblemonSpawningMod.MOD_ID, "jei_stats"),
            JeiStatsRecipe::class.java
        )

        private const val PADDING = 6
    }

    private val background: IDrawable = guiHelper.createBlankDrawable(180, 150)
    private val icon: IDrawable = guiHelper.createDrawableItemStack(ItemStack(Items.BOOK))

    override fun getRecipeType(): RecipeType<JeiStatsRecipe> = RECIPE_TYPE
    override fun getTitle(): Component = Component.translatable("category.cobblemon-spawning-rei.stats")
    override fun getBackground(): IDrawable = background
    override fun getWidth(): Int = DisplayLayout.getMaxStatsSize().width
    override fun getHeight(): Int = DisplayLayout.getMaxStatsSize().height
    override fun getIcon(): IDrawable = icon

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: JeiStatsRecipe, focuses: IFocusGroup) {
        builder.addSlot(RecipeIngredientRole.INPUT, PADDING, 2)
            .setSlotName("pokemon")
            .addIngredient(PokemonIngredientType, PokemonIngredient(recipe.speciesName))
    }

    override fun draw(recipe: JeiStatsRecipe, recipeSlotsView: IRecipeSlotsView, graphics: GuiGraphics, mouseX: Double, mouseY: Double) {
        val size = DisplayLayout.getMaxStatsSize()
        SpawnDisplayHelper.drawStatsDetails(
            graphics, recipe.speciesName, recipe.data.baseStats,
            recipe.data.baseStatTotal, recipe.data.primaryType, recipe.data.secondaryType,
            width = size.width, height = size.height
        )
    }
}
