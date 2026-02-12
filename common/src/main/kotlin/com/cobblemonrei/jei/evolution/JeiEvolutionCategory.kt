package com.cobblemonrei.jei.evolution

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
class JeiEvolutionCategory(guiHelper: IGuiHelper) : IRecipeCategory<JeiEvolutionRecipe> {

    companion object {
        val RECIPE_TYPE: RecipeType<JeiEvolutionRecipe> = RecipeType(
            ResourceLocation.fromNamespaceAndPath(CobblemonSpawningMod.MOD_ID, "jei_evolution"),
            JeiEvolutionRecipe::class.java
        )

        private const val WIDTH = 180
        private const val HEIGHT = 120
        private const val SLOT_SIZE = 18
        private const val ITEM_START_Y = 48
        private const val ITEM_ROW_HEIGHT = 20
    }

    private val background: IDrawable = guiHelper.createBlankDrawable(WIDTH, HEIGHT)
    private val icon: IDrawable = guiHelper.createDrawableItemStack(ItemStack(Items.EXPERIENCE_BOTTLE))
    private val arrow: IDrawable = guiHelper.getRecipeArrow()

    override fun getRecipeType(): RecipeType<JeiEvolutionRecipe> = RECIPE_TYPE
    override fun getTitle(): Component = Component.literal("Cobblemon Evolution")
    override fun getBackground(): IDrawable = background
    override fun getWidth(): Int = DisplayLayout.getMaxEvolutionSize().width
    override fun getHeight(): Int = DisplayLayout.getMaxEvolutionSize().height
    override fun getIcon(): IDrawable = icon

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: JeiEvolutionRecipe, focuses: IFocusGroup) {
        val w = getWidth()
        builder.addSlot(RecipeIngredientRole.INPUT, 20, 8)
            .addIngredient(PokemonIngredientType, PokemonIngredient(recipe.evolution.fromSpecies, recipe.evolution.fromAspects))

        builder.addSlot(RecipeIngredientRole.OUTPUT, w - 20 - SLOT_SIZE, 8)
            .addIngredient(PokemonIngredientType, PokemonIngredient(recipe.evolution.toSpecies, recipe.evolution.toAspects))

        for ((i, item) in recipe.evolution.itemRequirements.withIndex()) {
            val stack = SpawnDisplayHelper.resolveItemStack(item.itemId)
            if (!stack.isEmpty) {
                builder.addSlot(RecipeIngredientRole.CATALYST, 8, ITEM_START_Y + i * ITEM_ROW_HEIGHT)
                    .addItemStack(stack)
            }
        }
    }

    override fun draw(recipe: JeiEvolutionRecipe, recipeSlotsView: IRecipeSlotsView, graphics: GuiGraphics, mouseX: Double, mouseY: Double) {
        val w = getWidth()
        val h = getHeight()
        arrow.draw(graphics, w / 2 - 12, 8)
        SpawnDisplayHelper.drawEvolutionText(graphics, recipe.evolution, recipe.branchIndex, recipe.branchTotal, width = w, height = h, hasItemSlots = recipe.evolution.itemRequirements.isNotEmpty())
    }
}
