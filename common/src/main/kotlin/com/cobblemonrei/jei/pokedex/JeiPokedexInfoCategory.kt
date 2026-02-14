package com.cobblemonrei.jei.pokedex

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
class JeiPokedexInfoCategory(guiHelper: IGuiHelper) : IRecipeCategory<JeiPokedexInfoRecipe> {

    companion object {
        val RECIPE_TYPE: RecipeType<JeiPokedexInfoRecipe> = RecipeType(
            ResourceLocation.fromNamespaceAndPath(CobblemonSpawningMod.MOD_ID, "jei_pokedex_info"),
            JeiPokedexInfoRecipe::class.java
        )

        private const val PADDING = 6
    }

    private val background: IDrawable = guiHelper.createBlankDrawable(180, 200)
    private val icon: IDrawable = guiHelper.createDrawableItemStack(ItemStack(Items.WRITABLE_BOOK))

    override fun getRecipeType(): RecipeType<JeiPokedexInfoRecipe> = RECIPE_TYPE
    override fun getTitle(): Component = Component.translatable("category.cobblemon-spawning-rei.pokedex_info")
    override fun getBackground(): IDrawable = background
    override fun getWidth(): Int = DisplayLayout.getMaxPokedexInfoSize().width
    override fun getHeight(): Int = DisplayLayout.getMaxPokedexInfoSize().height
    override fun getIcon(): IDrawable = icon

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: JeiPokedexInfoRecipe, focuses: IFocusGroup) {
        builder.addSlot(RecipeIngredientRole.INPUT, PADDING, 2)
            .setSlotName("pokemon")
            .addIngredient(PokemonIngredientType, PokemonIngredient(recipe.speciesName))
    }

    override fun draw(recipe: JeiPokedexInfoRecipe, recipeSlotsView: IRecipeSlotsView, graphics: GuiGraphics, mouseX: Double, mouseY: Double) {
        val size = DisplayLayout.getMaxPokedexInfoSize()
        SpawnDisplayHelper.drawPokedexInfoDetails(
            graphics, recipe.data,
            width = size.width, height = size.height
        )
    }
}
