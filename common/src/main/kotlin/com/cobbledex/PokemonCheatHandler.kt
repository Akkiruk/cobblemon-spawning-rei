package com.cobbledex

import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack

/**
 * Handles cheating in Pokémon across recipe viewers (REI/JEI/EMI).
 * When a player uses cheat mode, sends /pokegive instead of giving a cosmetic item.
 */
object PokemonCheatHandler {

    fun sendPokegiveCommand(species: String, formAspects: Set<String> = emptySet()) {
        val player = Minecraft.getInstance().player ?: return
        val cmd = buildCommand(species, formAspects)
        player.connection?.sendCommand(cmd)
    }

    private fun buildCommand(species: String, formAspects: Set<String>): String {
        val speciesName = if (species.contains(":")) species.substringAfter(":") else species
        val parts = mutableListOf("pokegive", speciesName)
        val formAspect = formAspects.firstOrNull { it != "male" && it != "female" }
        if (formAspect != null) parts.add("form=$formAspect")
        return parts.joinToString(" ")
    }

    /**
     * Checks if an ItemStack is a Cobblemon PokemonItem by checking its registered item class.
     */
    fun isPokemonItem(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        return try {
            stack.item is com.cobblemon.mod.common.item.PokemonItem
        } catch (_: Exception) {
            false
        }
    }
}
