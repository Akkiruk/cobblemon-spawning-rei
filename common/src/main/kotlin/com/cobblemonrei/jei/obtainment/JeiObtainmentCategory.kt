package com.cobblemonrei.jei.obtainment

import com.cobblemonrei.CobblemonSpawningMod
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
class JeiObtainmentCategory(guiHelper: IGuiHelper) : IRecipeCategory<JeiObtainmentRecipe> {

    companion object {
        val RECIPE_TYPE: RecipeType<JeiObtainmentRecipe> = RecipeType(
            ResourceLocation.fromNamespaceAndPath(CobblemonSpawningMod.MOD_ID, "jei_obtainment"),
            JeiObtainmentRecipe::class.java
        )

        private const val WIDTH = 180
        private const val HEIGHT = 150
        private const val PADDING = 6
    }

    private val background: IDrawable = guiHelper.createBlankDrawable(WIDTH, HEIGHT)
    private val icon: IDrawable = guiHelper.createDrawableItemStack(ItemStack(Items.NETHER_STAR))

    override fun getRecipeType(): RecipeType<JeiObtainmentRecipe> = RECIPE_TYPE
    override fun getTitle(): Component = Component.literal("Special Obtainment")
    override fun getBackground(): IDrawable = background
    override fun getWidth(): Int = WIDTH
    override fun getHeight(): Int = HEIGHT
    override fun getIcon(): IDrawable = icon

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: JeiObtainmentRecipe, focuses: IFocusGroup) {
        builder.addSlot(RecipeIngredientRole.OUTPUT, PADDING, 2)
            .setSlotName("pokemon")
            .addIngredient(PokemonIngredientType, PokemonIngredient(recipe.speciesName))
    }

    override fun draw(recipe: JeiObtainmentRecipe, recipeSlotsView: IRecipeSlotsView, graphics: GuiGraphics, mouseX: Double, mouseY: Double) {
        SpawnDisplayHelper.drawObtainmentDetails(
            graphics, recipe.speciesName, recipe.obtainment,
            recipe.entryIndex, recipe.entryTotal
        )
    }
}
