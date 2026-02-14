package com.cobbledex.jei.typechart

import com.cobbledex.CobbleDexMod
import com.cobbledex.DisplayLayout
import com.cobbledex.SpawnDisplayHelper
import com.cobbledex.jei.PokemonIngredient
import com.cobbledex.jei.PokemonIngredientType
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
class JeiTypeChartCategory(guiHelper: IGuiHelper) : IRecipeCategory<JeiTypeChartRecipe> {

    companion object {
        val RECIPE_TYPE: RecipeType<JeiTypeChartRecipe> = RecipeType(
            ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "jei_type_chart"),
            JeiTypeChartRecipe::class.java
        )

        private const val PADDING = 6
    }

    private val background: IDrawable = guiHelper.createBlankDrawable(180, 200)
    private val icon: IDrawable = guiHelper.createDrawableItemStack(ItemStack(Items.SHIELD))

    override fun getRecipeType(): RecipeType<JeiTypeChartRecipe> = RECIPE_TYPE
    override fun getTitle(): Component = Component.translatable("category.cobbledex-rei-emi-jei.type_chart")
    override fun getBackground(): IDrawable = background
    override fun getWidth(): Int = DisplayLayout.getMaxTypeChartSize().width
    override fun getHeight(): Int = DisplayLayout.getMaxTypeChartSize().height
    override fun getIcon(): IDrawable = icon

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: JeiTypeChartRecipe, focuses: IFocusGroup) {
        builder.addSlot(RecipeIngredientRole.INPUT, PADDING, 2)
            .setSlotName("pokemon")
            .addIngredient(PokemonIngredientType, PokemonIngredient(recipe.speciesName))
    }

    override fun draw(recipe: JeiTypeChartRecipe, recipeSlotsView: IRecipeSlotsView, graphics: GuiGraphics, mouseX: Double, mouseY: Double) {
        val size = DisplayLayout.getMaxTypeChartSize()
        SpawnDisplayHelper.drawTypeChartDetails(graphics, recipe.data, width = size.width, height = size.height)
    }
}
