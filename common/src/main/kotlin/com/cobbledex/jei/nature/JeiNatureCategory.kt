package com.cobbledex.jei.nature

import com.cobbledex.CobbleDexMod
import com.cobbledex.DisplayLayout
import com.cobbledex.SpawnDisplayHelper
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.drawable.IDrawable
import mezz.jei.api.gui.ingredient.IRecipeSlotsView
import mezz.jei.api.helpers.IGuiHelper
import mezz.jei.api.recipe.IFocusGroup
import mezz.jei.api.recipe.RecipeType
import mezz.jei.api.recipe.category.IRecipeCategory
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

@Suppress("DEPRECATION")
class JeiNatureCategory(guiHelper: IGuiHelper) : IRecipeCategory<JeiNatureRecipe> {

    companion object {
        val RECIPE_TYPE: RecipeType<JeiNatureRecipe> = RecipeType(
            ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, "jei_natures"),
            JeiNatureRecipe::class.java
        )
    }

    private val background: IDrawable = guiHelper.createBlankDrawable(200, 290)
    private val icon: IDrawable = guiHelper.createDrawableItemStack(ItemStack(Items.WRITABLE_BOOK))

    override fun getRecipeType(): RecipeType<JeiNatureRecipe> = RECIPE_TYPE
    override fun getTitle(): Component = Component.translatable("category.cobbledex-rei-emi-jei.natures")
    override fun getBackground(): IDrawable = background
    override fun getWidth(): Int = DisplayLayout.getMaxNatureSize().width
    override fun getHeight(): Int = DisplayLayout.getMaxNatureSize().height
    override fun getIcon(): IDrawable = icon

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: JeiNatureRecipe, focuses: IFocusGroup) {
        // No input/output slots for the nature reference table
    }

    override fun draw(recipe: JeiNatureRecipe, recipeSlotsView: IRecipeSlotsView, graphics: GuiGraphics, mouseX: Double, mouseY: Double) {
        val size = DisplayLayout.getMaxNatureSize()
        SpawnDisplayHelper.drawNatureDetails(graphics, recipe.data, width = size.width, height = size.height)
    }
}
