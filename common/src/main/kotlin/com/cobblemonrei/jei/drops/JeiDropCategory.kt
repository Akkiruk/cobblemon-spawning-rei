package com.cobblemonrei.jei.drops

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
class JeiDropCategory(guiHelper: IGuiHelper) : IRecipeCategory<JeiDropRecipe> {

    companion object {
        val RECIPE_TYPE: RecipeType<JeiDropRecipe> = RecipeType(
            ResourceLocation.fromNamespaceAndPath(CobblemonSpawningMod.MOD_ID, "jei_drops"),
            JeiDropRecipe::class.java
        )

        private const val PADDING = 6
    }

    private val background: IDrawable = guiHelper.createBlankDrawable(180, 150)
    private val icon: IDrawable = guiHelper.createDrawableItemStack(ItemStack(Items.DIAMOND))

    override fun getRecipeType(): RecipeType<JeiDropRecipe> = RECIPE_TYPE
    override fun getTitle(): Component = Component.translatable("category.cobblemon-spawning-rei.drops")
    override fun getBackground(): IDrawable = background
    override fun getWidth(): Int = DisplayLayout.getMaxDropSize().width
    override fun getHeight(): Int = DisplayLayout.getMaxDropSize().height
    override fun getIcon(): IDrawable = icon

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: JeiDropRecipe, focuses: IFocusGroup) {
        builder.addSlot(RecipeIngredientRole.INPUT, PADDING, 2)
            .setSlotName("pokemon")
            .addIngredient(PokemonIngredientType, PokemonIngredient(recipe.speciesName))

        var slotY = 34
        for (drop in recipe.drops) {
            val stack = SpawnDisplayHelper.resolveItemStack(drop.itemId)
            if (!stack.isEmpty) {
                builder.addSlot(RecipeIngredientRole.OUTPUT, 8, slotY)
                    .addItemStack(stack)
            }
            slotY += 20
        }
    }

    override fun draw(recipe: JeiDropRecipe, recipeSlotsView: IRecipeSlotsView, graphics: GuiGraphics, mouseX: Double, mouseY: Double) {
        val size = DisplayLayout.getMaxDropSize()
        SpawnDisplayHelper.drawDropDetails(
            graphics, recipe.speciesName, recipe.drops,
            width = size.width, height = size.height
        )
    }
}
