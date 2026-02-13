package com.cobblemonrei.jei.spawn

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
class JeiSpawnCategory(guiHelper: IGuiHelper) : IRecipeCategory<JeiSpawnRecipe> {

    companion object {
        val RECIPE_TYPE: RecipeType<JeiSpawnRecipe> = RecipeType(
            ResourceLocation.fromNamespaceAndPath(CobblemonSpawningMod.MOD_ID, "jei_spawns"),
            JeiSpawnRecipe::class.java
        )

        private const val WIDTH = 180
        private const val HEIGHT = 200
        private const val PADDING = 6
    }

    private val background: IDrawable = guiHelper.createBlankDrawable(WIDTH, HEIGHT)
    private val icon: IDrawable = guiHelper.createDrawableItemStack(ItemStack(Items.GRASS_BLOCK))

    override fun getRecipeType(): RecipeType<JeiSpawnRecipe> = RECIPE_TYPE
    override fun getTitle(): Component = Component.translatable("category.cobblemon-spawning-rei.spawn")
    override fun getBackground(): IDrawable = background
    override fun getWidth(): Int = DisplayLayout.getMaxSpawnSize().width
    override fun getHeight(): Int = DisplayLayout.getMaxSpawnSize().height
    override fun getIcon(): IDrawable = icon

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: JeiSpawnRecipe, focuses: IFocusGroup) {
        builder.addSlot(RecipeIngredientRole.OUTPUT, PADDING, 2)
            .setSlotName("pokemon")
            .addIngredient(PokemonIngredientType, PokemonIngredient(recipe.speciesName))
    }

    override fun draw(recipe: JeiSpawnRecipe, recipeSlotsView: IRecipeSlotsView, graphics: GuiGraphics, mouseX: Double, mouseY: Double) {
        val size = DisplayLayout.getMaxSpawnSize()
        SpawnDisplayHelper.drawSpawnDetails(
            graphics, recipe.speciesName, recipe.spawn, recipe.mergedFormVariants,
            recipe.bucketIndex, recipe.bucketTotal,
            width = size.width, height = size.height
        )
    }

}
