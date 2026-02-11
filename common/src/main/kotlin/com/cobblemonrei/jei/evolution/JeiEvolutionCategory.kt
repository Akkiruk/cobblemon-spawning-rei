package com.cobblemonrei.jei.evolution

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.SpawnDisplayHelper.clip
import com.cobblemonrei.SpawnDisplayHelper.wrapReqText
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
        private const val HEIGHT = 90
        private const val SLOT_SIZE = 18
    }

    private val background: IDrawable = guiHelper.createBlankDrawable(WIDTH, HEIGHT)
    private val icon: IDrawable = guiHelper.createDrawableItemStack(ItemStack(Items.EXPERIENCE_BOTTLE))
    private val arrow: IDrawable = guiHelper.getRecipeArrow()

    override fun getRecipeType(): RecipeType<JeiEvolutionRecipe> = RECIPE_TYPE
    override fun getTitle(): Component = Component.literal("Cobblemon Evolution")
    override fun getBackground(): IDrawable = background
    override fun getWidth(): Int = WIDTH
    override fun getHeight(): Int = HEIGHT
    override fun getIcon(): IDrawable = icon

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: JeiEvolutionRecipe, focuses: IFocusGroup) {
        builder.addSlot(RecipeIngredientRole.INPUT, 20, 10)
            .addIngredient(PokemonIngredientType, PokemonIngredient(recipe.evolution.fromSpecies, recipe.evolution.fromAspects))

        builder.addSlot(RecipeIngredientRole.OUTPUT, WIDTH - 20 - SLOT_SIZE, 10)
            .addIngredient(PokemonIngredientType, PokemonIngredient(recipe.evolution.toSpecies, recipe.evolution.toAspects))
    }

    override fun draw(recipe: JeiEvolutionRecipe, recipeSlotsView: IRecipeSlotsView, graphics: GuiGraphics, mouseX: Double, mouseY: Double) {
        val font = net.minecraft.client.Minecraft.getInstance().font
        val evo = recipe.evolution
        val centerX = WIDTH / 2

        // Arrow
        arrow.draw(graphics, centerX - 12, 10)

        // From name
        val fromName = clip(evo.displayFromName, 16)
        val fromWidth = font.width(fromName)
        graphics.drawString(font, fromName, 20 + SLOT_SIZE / 2 - fromWidth / 2, 32, 0xFFFFFF, false)

        // To name
        val toName = clip(evo.displayToName, 16)
        val toWidth = font.width(toName)
        graphics.drawString(font, toName, WIDTH - 20 - SLOT_SIZE / 2 - toWidth / 2, 32, 0xFFFFFF, false)

        // Branch indicator
        if (recipe.branchTotal > 1) {
            val branchText = "${recipe.branchIndex}/${recipe.branchTotal}"
            val bw = font.width(branchText)
            graphics.drawString(font, branchText, 20 + SLOT_SIZE / 2 - bw / 2, 43, 0xBBBBBB, false)
        }

        // Requirements text
        val reqText = evo.displayRequirements
        val maxCharsPerLine = 32
        val lines = wrapReqText(reqText, maxCharsPerLine, 3)
        var reqY = 56
        for (line in lines) {
            val lw = font.width(line)
            graphics.drawString(font, line, centerX - lw / 2, reqY, 0xFFDD88, false)
            reqY += 11
        }
    }
}
