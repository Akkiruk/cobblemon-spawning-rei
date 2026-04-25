package com.cobbledex

import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack

/**
 * Handles cheating in Pokémon across recipe viewers (REI/JEI/EMI).
 * When a player uses cheat mode, sends /pokegive instead of giving a cosmetic item.
 */
object PokemonCheatHandler {

    // Debounce to prevent double-fire (JEI calls getCheatItemStack on both simulate + execute phases)
    private var lastSpecies = ""
    private var lastFireTick = 0L

    fun sendPokegiveCommand(species: String, formAspects: Set<String> = emptySet()) {
        val player = Minecraft.getInstance().player ?: return
        val tick = player.level().gameTime
        // Skip if same species fired within 10 ticks (~500ms)
        val key = "$species|${formAspects.sorted()}"
        if (key == lastSpecies && tick - lastFireTick < 10) return
        lastSpecies = key
        lastFireTick = tick
        val cmd = buildCommand(species, formAspects)
        player.connection?.sendCommand(cmd)
    }

    private fun buildCommand(species: String, formAspects: Set<String>): String {
        val speciesName = if (species.contains(":")) species.substringAfter(":") else species
        val parts = mutableListOf("pokegive", speciesName)
        val nonGenderAspects = formAspects
            .map { it.lowercase() }
            .filter { it != "male" && it != "female" }
            .sorted()
        nonGenderAspects.firstOrNull()?.let { parts.add("form=$it") }
        nonGenderAspects.drop(1).forEach { parts.add("aspect=$it") }
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
