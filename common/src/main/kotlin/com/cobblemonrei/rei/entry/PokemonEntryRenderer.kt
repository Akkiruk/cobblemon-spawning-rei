package com.cobblemonrei.rei.entry

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import me.shedaniel.math.Rectangle
import me.shedaniel.rei.api.client.entry.renderer.EntryRenderer
import me.shedaniel.rei.api.client.gui.widgets.Tooltip
import me.shedaniel.rei.api.client.gui.widgets.TooltipContext
import me.shedaniel.rei.api.common.entry.EntryStack
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import org.joml.Quaternionf

class PokemonEntryRenderer : EntryRenderer<PokemonEntry> {

    private val stateCache = HashMap<String, FloatingState>()

    companion object {
        private const val COBBLEMON_SLOT_SIZE = 25f
        private const val COBBLEMON_PRESCALE = 2.5f
        private const val COBBLEMON_PROFILE_SCALE = 4.5f
        private const val DEG_TO_RAD = Math.PI.toFloat() / 180f
    }

    override fun render(
        entry: EntryStack<PokemonEntry>,
        graphics: GuiGraphics,
        bounds: Rectangle,
        mouseX: Int,
        mouseY: Int,
        delta: Float
    ) {
        val pokemon = entry.value ?: return
        
        val species = try {
            PokemonSpecies.getByName(pokemon.species)
        } catch (_: Exception) {
            null
        }

        if (species == null) {
            renderFallbackText(graphics, bounds, pokemon.displayName)
            return
        }

        val state = stateCache.getOrPut(pokemon.species) { FloatingState() }
        val renderable = RenderablePokemon(species, pokemon.formAspects)

        val poseStack = graphics.pose()
        
        val slotSize = bounds.width.coerceAtMost(bounds.height).toFloat()
        val sizeRatio = slotSize / COBBLEMON_SLOT_SIZE
        val preScale = COBBLEMON_PRESCALE * sizeRatio
        
        val centerX = bounds.x + bounds.width / 2.0
        val centerY = bounds.y + bounds.height * 0.55

        graphics.enableScissor(bounds.x, bounds.y, bounds.maxX, bounds.maxY)
        poseStack.pushPose()
        poseStack.translate(centerX, centerY, 100.0)
        poseStack.scale(preScale, preScale, 1f)

        try {
            drawProfilePokemon(
                renderablePokemon = renderable,
                matrixStack = poseStack,
                rotation = Quaternionf().rotationXYZ(13f * DEG_TO_RAD, 35f * DEG_TO_RAD, 0f),
                poseType = PoseType.PROFILE,
                state = state,
                partialTicks = 0f,
                scale = COBBLEMON_PROFILE_SCALE
            )
        } catch (_: Exception) {
            poseStack.popPose()
            graphics.disableScissor()
            renderFallbackText(graphics, bounds, pokemon.displayName)
            return
        }

        poseStack.popPose()
        graphics.disableScissor()
    }

    private fun renderFallbackText(graphics: GuiGraphics, bounds: Rectangle, name: String) {
        val label = if (name.length > 4) name.take(3) + "." else name
        val font = net.minecraft.client.Minecraft.getInstance().font
        val textWidth = font.width(label)
        val x = bounds.centerX - textWidth / 2
        val y = bounds.centerY - font.lineHeight / 2
        graphics.drawString(font, label, x, y, 0xFFAAAAAA.toInt(), false)
    }

    override fun getTooltip(entry: EntryStack<PokemonEntry>, context: TooltipContext): Tooltip? {
        val pokemon = entry.value ?: return null
        val species = try {
            PokemonSpecies.getByName(pokemon.species)
        } catch (_: Exception) {
            null
        }

        val tooltip = Tooltip.create(Component.literal(pokemon.displayName))
        if (species != null) {
            tooltip.add(Component.literal("§7#${species.nationalPokedexNumber}"))
        }
        return tooltip
    }
}
