package com.cobbledex.emi

import com.cobbledex.CobbleDexMod
import com.cobbledex.PokemonItemCache
import com.cobbledex.PokemonRef
import com.cobbledex.PokemonSpriteKey
import com.cobbledex.PokemonSpriteService
import com.cobbledex.SpawnDisplayHelper
import dev.emi.emi.EmiPort
import dev.emi.emi.api.render.EmiTooltipComponents
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import dev.emi.emi.screen.tooltip.EmiTextTooltipWrapper
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore

class PokemonEmiStack(
    override val species: String,
    override val formAspects: Set<String> = emptySet(),
) : EmiStack(), PokemonRef {

    private val spriteKey = PokemonSpriteKey.from(species, formAspects)

    override fun copy(): EmiStack {
        val copy = PokemonEmiStack(species, formAspects)
        copy.setAmount(amount)
        copy.setChance(chance)
        copy.setRemainder(getRemainder().copy())
        copy.comparison(comparison)
        return copy
    }

    override fun isEmpty(): Boolean = !PokemonSpriteService.canRender(species, formAspects)

    override fun getComponentChanges() = net.minecraft.core.component.DataComponentPatch.EMPTY

    override fun getKey(): Any = spriteKey

    override fun getId(): ResourceLocation = ResourceLocation.fromNamespaceAndPath(CobbleDexMod.MOD_ID, spriteKey.idPath)

    override fun getItemStack(): ItemStack {
        val stack = PokemonItemCache.getItem(species, formAspects) ?: return ItemStack.EMPTY
        val info = com.cobbledex.SpawnDataIndex.getSpeciesInfo(species)
        val loreLines = mutableListOf<Component>()
        val jobs = com.cobbledex.SpawnDataIndex.getJobsFor(species)
        if (jobs.isNotEmpty()) {
            loreLines.add(Component.literal(jobs.joinToString(" ") { "job:${it.rule.id} ${it.rule.displayName}" }))
        }
        if (info != null && info.isForm && info.baseSpeciesName != null) {
            loreLines.add(Component.literal(com.cobbledex.formatSpeciesName(info.baseSpeciesName)))
        }
        if (loreLines.isNotEmpty()) {
            stack.set(DataComponents.LORE, ItemLore(loreLines))
        }
        return stack
    }

    override fun render(draw: GuiGraphics, x: Int, y: Int, delta: Float, flags: Int) {
        if ((flags and EmiIngredient.RENDER_ICON) != 0) {
            PokemonSpriteService.render(draw, spriteKey, x, y, 16)
        }
    }

    override fun getTooltipText(): List<Component> = SpawnDisplayHelper.buildPokemonTooltipLines(species, displayName)

    override fun getTooltip(): List<ClientTooltipComponent> {
        val list = mutableListOf<ClientTooltipComponent>()
        val text = getTooltipText()
        if (text.isNotEmpty()) {
            list.add(EmiTextTooltipWrapper(this, EmiPort.ordered(text.first())))
            list.addAll(text.drop(1).map(EmiTooltipComponents::of))
        }
        if (amount > 1) {
            list.add(EmiTooltipComponents.getAmount(this))
        }
        list.addAll(super.getTooltip())
        return list
    }

    override fun getName(): Component = Component.literal(displayName)
}