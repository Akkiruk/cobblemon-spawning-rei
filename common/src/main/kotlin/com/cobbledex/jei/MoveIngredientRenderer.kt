package com.cobbledex.jei

import mezz.jei.api.ingredients.IIngredientRenderer
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.item.TooltipFlag

/** Move ingredients are never shown in a slot, so this only exists to satisfy JEI registration. */
class MoveIngredientRenderer : IIngredientRenderer<MoveIngredient> {

    override fun render(graphics: GuiGraphics, ingredient: MoveIngredient) {
        // never shown
    }

    override fun getTooltip(ingredient: MoveIngredient, tooltipFlag: TooltipFlag): List<Component> =
        listOf(Component.literal(ingredient.displayName))
}
