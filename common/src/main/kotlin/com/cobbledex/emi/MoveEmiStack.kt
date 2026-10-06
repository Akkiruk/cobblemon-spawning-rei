package com.cobbledex.emi

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dev.emi.emi.api.render.EmiTooltipComponents
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * A move as an EMI ingredient. Only used to key navigation ("show me who can learn this move") - it
 * is never shown in a slot, so it renders nothing.
 */
class MoveEmiStack private constructor(val move: String) : EmiStack() {

    /** A typed key rather than the bare string, so it can never equal another mod's string-keyed stack. */
    private data class Key(val move: String)

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

    private val key = Key(normalized)

    override fun getKey(): Any = key

    override fun getId(): ResourceLocation = id

    override fun getName(): Component = Component.literal(
        move.replace('_', ' ').replaceFirstChar { it.uppercase() }
    )

    override fun getTooltipText(): List<Component> = listOf(name)

    // EMI draws getTooltip(), not getTooltipText() - see PokemonEmiStack.getTooltip.
    override fun getTooltip(): List<ClientTooltipComponent> {
        if (isEmpty()) return emptyList()
        val list = getTooltipText().mapTo(mutableListOf()) { EmiTooltipComponents.of(it) }
        list.addAll(super.getTooltip())
        return list
    }

    override fun render(
        graphics: net.minecraft.client.gui.GuiGraphics, x: Int, y: Int, delta: Float, flags: Int,
    ) {
        // never shown
    }

    companion object {
        fun of(move: String): MoveEmiStack = MoveEmiStack(move)
    }

    /** Lets a move survive in EMI's lookup history, which only keeps stacks that have a serializer. */
    class Serializer : EmiIngredientSerializer<MoveEmiStack> {
        override fun getType(): String = TYPE

        override fun serialize(stack: MoveEmiStack): JsonElement = JsonObject().apply {
            addProperty("type", TYPE)
            addProperty("move", stack.move)
        }

        override fun deserialize(element: JsonElement): EmiIngredient {
            val move = element.takeIf { it.isJsonObject }?.asJsonObject?.get("move")
                ?.takeIf { it.isJsonPrimitive }?.asString ?: return EmiStack.EMPTY
            return of(move).takeUnless { it.isEmpty } ?: EmiStack.EMPTY
        }

        companion object {
            const val TYPE = "cobbledex_move"
        }
    }
}
