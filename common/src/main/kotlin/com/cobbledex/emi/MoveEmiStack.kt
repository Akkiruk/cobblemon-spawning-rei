package com.cobbledex.emi

import dev.emi.emi.api.stack.EmiStack
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * A move as an EMI ingredient. Only used to key navigation ("show me who can learn this move") — it
 * is never shown in a slot, so it renders nothing.
 */
class MoveEmiStack private constructor(val move: String) : EmiStack() {

    private val normalized = move.lowercase()
    private val id = ResourceLocation.fromNamespaceAndPath(
        "cobbledex-rei-emi-jei",
        "move/" + normalized.replace(Regex("[^a-z0-9._-]"), "")
    )

    override fun copy(): EmiStack = MoveEmiStack(move).also {
        it.setAmount(getAmount())
        it.setChance(getChance())
    }

    override fun isEmpty(): Boolean = normalized.isBlank()

    override fun getComponentChanges(): DataComponentPatch = DataComponentPatch.EMPTY

    override fun getKey(): Any = normalized

    override fun getId(): ResourceLocation = id

    override fun getName(): Component = Component.literal(
        move.replace('_', ' ').replaceFirstChar { it.uppercase() }
    )

    override fun getTooltipText(): List<Component> = listOf(name)

    override fun render(
        graphics: net.minecraft.client.gui.GuiGraphics, x: Int, y: Int, delta: Float, flags: Int,
    ) {
        // never shown
    }

    companion object {
        fun of(move: String): MoveEmiStack = MoveEmiStack(move)
    }
}
