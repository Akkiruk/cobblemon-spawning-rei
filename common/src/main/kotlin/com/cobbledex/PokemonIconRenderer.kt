package com.cobbledex

import net.minecraft.client.gui.GuiGraphics

/**
 * The one place a Pokémon icon is drawn.
 *
 * REI, JEI and EMI each need to paint the same thing and previously each carried their own copy of
 * this sequence — atlas first, rendered item as fallback, failures recorded so a broken model isn't
 * retried every frame. Keeping three copies meant a fix in one viewer silently didn't reach the
 * other two.
 */
object PokemonIconRenderer {

    /** The size a vanilla item renders at; anything else has to be scaled. */
    private const val ITEM_RENDER_SIZE = 16

    /**
     * Aspects to draw this species with: the caller's explicit set when it has one, otherwise the
     * aspects implied by a form-qualified species id (e.g. `alolan_raichu`).
     */
    fun resolveAspects(species: String, formAspects: Set<String>): Set<String> =
        formAspects.ifEmpty { SpeciesNameNormalizer.decomposeFormSpecies(species).cobblemonAspects }

    /**
     * Draws [species] at [x], [y] filling [size] pixels. Returns false when nothing could be drawn,
     * which callers may use to skip decorations around an empty slot.
     */
    fun render(
        graphics: GuiGraphics,
        species: String,
        formAspects: Set<String>,
        x: Int,
        y: Int,
        size: Int = ITEM_RENDER_SIZE,
    ): Boolean {
        val aspects = resolveAspects(species, formAspects)

        if (PokemonSpriteAtlas.renderIfAvailable(graphics, species, aspects, x, y, size)) return true

        val stack = PokemonItemCache.getRenderItem(species, aspects) ?: return false
        if (stack.isEmpty) return false

        val pose = graphics.pose()
        pose.pushPose()
        try {
            // renderItem always draws 16x16, so a differently-sized slot needs a scale around the
            // slot's origin rather than a different draw call.
            if (size == ITEM_RENDER_SIZE) {
                graphics.renderItem(stack, x, y)
            } else {
                val scale = size.toFloat() / ITEM_RENDER_SIZE
                pose.translate(x.toFloat(), y.toFloat(), 0f)
                pose.scale(scale, scale, 1f)
                graphics.renderItem(stack, 0, 0)
            }
        } catch (t: Throwable) {
            PokemonItemCache.markRenderFailed(species, aspects, t)
            return false
        } finally {
            pose.popPose()
        }
        return true
    }
}
