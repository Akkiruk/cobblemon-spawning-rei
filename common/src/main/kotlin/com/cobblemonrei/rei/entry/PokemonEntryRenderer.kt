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
    private val DEG_TO_RAD = (Math.PI / 180.0).toFloat()

    override fun render(
        entry: EntryStack<PokemonEntry>,
        graphics: GuiGraphics,
        bounds: Rectangle,
        mouseX: Int,
        mouseY: Int,
        delta: Float
    ) {
        val pokemon = entry.value ?: return
        val species = PokemonSpecies.getByName(pokemon.species) ?: return

        val state = stateCache.getOrPut(pokemon.species) { FloatingState() }
        val renderable = RenderablePokemon(species, pokemon.formAspects)

        val poseStack = graphics.pose()
        val centerX = bounds.centerX.toFloat()
        val centerY = bounds.y.toFloat() + bounds.height * 0.55f
        val scale = bounds.width.coerceAtMost(bounds.height) * 0.35f

        graphics.enableScissor(bounds.x, bounds.y, bounds.maxX, bounds.maxY)
        poseStack.pushPose()
        poseStack.translate(centerX.toDouble(), centerY.toDouble(), 100.0)

        try {
            drawProfilePokemon(
                renderablePokemon = renderable,
                matrixStack = poseStack,
                rotation = Quaternionf().rotationXYZ(13f * DEG_TO_RAD, 35f * DEG_TO_RAD, 0f),
                poseType = PoseType.PROFILE,
                state = state,
                partialTicks = delta,
                scale = scale
            )
        } catch (_: Exception) {
            // Species model not loaded yet
        }

        poseStack.popPose()
        graphics.disableScissor()
    }

    override fun getTooltip(entry: EntryStack<PokemonEntry>, context: TooltipContext): Tooltip? {
        val pokemon = entry.value ?: return null
        val species = PokemonSpecies.getByName(pokemon.species)

        val tooltip = Tooltip.create(Component.literal(pokemon.displayName))
        if (species != null) {
            tooltip.add(Component.literal("§7#${species.nationalPokedexNumber}"))
        }
        return tooltip
    }
}
