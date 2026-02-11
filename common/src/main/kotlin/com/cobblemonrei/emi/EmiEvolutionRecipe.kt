package com.cobblemonrei.emi

import com.cobblemonrei.CobblemonSpawningMod
import com.cobblemonrei.EvolutionInfo
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.item.PokemonItem
import dev.emi.emi.api.recipe.EmiRecipe
import dev.emi.emi.api.recipe.EmiRecipeCategory
import dev.emi.emi.api.render.EmiTexture
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import dev.emi.emi.api.widget.WidgetHolder
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation

class EmiEvolutionRecipe(
    val evolution: EvolutionInfo,
    val branchIndex: Int = 0,
    val branchTotal: Int = 0
) : EmiRecipe {

    companion object {
        private const val WIDTH = 180
        private const val HEIGHT = 90
        private const val SLOT_SIZE = 18
    }

    private val fromStack: EmiStack? = try {
        val species = PokemonSpecies.getByName(evolution.fromSpecies)
        if (species != null) EmiStack.of(PokemonItem.from(species)) else null
    } catch (_: Exception) { null }

    private val toStack: EmiStack? = try {
        val species = PokemonSpecies.getByName(evolution.toSpecies)
        if (species != null) EmiStack.of(PokemonItem.from(species)) else null
    } catch (_: Exception) { null }

    override fun getCategory(): EmiRecipeCategory = CobblemonEMIPlugin.EVOLUTION_CATEGORY

    override fun getId(): ResourceLocation {
        val suffix = if (evolution.fromAspects.isNotEmpty() || evolution.toAspects.isNotEmpty()) {
            "_${(evolution.fromAspects + evolution.toAspects).hashCode().toUInt()}"
        } else ""
        return ResourceLocation.fromNamespaceAndPath(
            CobblemonSpawningMod.MOD_ID,
            "emi_evolution/${evolution.fromSpecies.lowercase()}_to_${evolution.toSpecies.lowercase()}$suffix"
        )
    }

    override fun getInputs(): List<EmiIngredient> = listOfNotNull(fromStack)
    override fun getOutputs(): List<EmiStack> = listOfNotNull(toStack)
    override fun supportsRecipeTree(): Boolean = false
    override fun getDisplayWidth(): Int = WIDTH
    override fun getDisplayHeight(): Int = HEIGHT

    override fun addWidgets(widgets: WidgetHolder) {
        fromStack?.let { widgets.addSlot(it, 20, 10) }
        toStack?.let { widgets.addSlot(it, WIDTH - 20 - SLOT_SIZE, 10).recipeContext(this) }
        widgets.addTexture(EmiTexture.EMPTY_ARROW, WIDTH / 2 - 12, 10)
        widgets.addDrawable(0, 0, WIDTH, HEIGHT) { graphics, _, _, _ ->
            drawEvolutionDetails(graphics)
        }
    }

    private fun drawEvolutionDetails(graphics: GuiGraphics) {
        val font = Minecraft.getInstance().font
        val centerX = WIDTH / 2

        val fromName = clip(evolution.displayFromName, 16)
        val fromWidth = font.width(fromName)
        graphics.drawString(font, fromName, 20 + SLOT_SIZE / 2 - fromWidth / 2, 32, 0xFFFFFF, false)

        val toName = clip(evolution.displayToName, 16)
        val toWidth = font.width(toName)
        graphics.drawString(font, toName, WIDTH - 20 - SLOT_SIZE / 2 - toWidth / 2, 32, 0xFFFFFF, false)

        if (branchTotal > 1) {
            val branchText = "$branchIndex/$branchTotal"
            val bw = font.width(branchText)
            graphics.drawString(font, branchText, 20 + SLOT_SIZE / 2 - bw / 2, 43, 0xBBBBBB, false)
        }

        val reqText = evolution.displayRequirements
        val lines = wrapReqText(reqText, 32, 3)
        var reqY = 56
        for (line in lines) {
            val lw = font.width(line)
            graphics.drawString(font, line, centerX - lw / 2, reqY, 0xFFDD88, false)
            reqY += 11
        }
    }

    private fun clip(text: String, maxLen: Int): String =
        if (text.length > maxLen) text.take(maxLen - 1) + "\u2026" else text

    private fun wrapReqText(text: String, maxChars: Int, maxLines: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val items = text.split(", ")
        val lines = mutableListOf<String>()
        var current = ""
        for (item in items) {
            val next = if (current.isEmpty()) item else "$current, $item"
            if (next.length > maxChars && current.isNotEmpty()) {
                lines.add(current)
                if (lines.size >= maxLines) {
                    val remaining = items.drop(items.indexOf(item))
                    lines[lines.lastIndex] = clip(lines.last() + ", " + remaining.joinToString(", "), maxChars)
                    return lines
                }
                current = item
            } else {
                current = next
            }
        }
        if (current.isNotEmpty()) {
            if (lines.size >= maxLines) {
                lines[lines.lastIndex] = clip(lines.last() + ", $current", maxChars)
            } else {
                lines.add(current)
            }
        }
        return lines
    }
}
