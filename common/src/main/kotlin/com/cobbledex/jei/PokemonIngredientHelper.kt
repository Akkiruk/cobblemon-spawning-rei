package com.cobbledex.jei

import com.cobbledex.PokemonCheatHandler
import com.cobbledex.PokemonItemCache
import com.cobbledex.SpawnDataIndex
import mezz.jei.api.ingredients.IIngredientHelper
import mezz.jei.api.ingredients.IIngredientType
import mezz.jei.api.ingredients.subtypes.UidContext
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import java.util.Optional

@Suppress("DEPRECATION")
class PokemonIngredientHelper : IIngredientHelper<PokemonIngredient> {

    override fun getIngredientType(): IIngredientType<PokemonIngredient> = PokemonIngredientType

    override fun getDisplayName(ingredient: PokemonIngredient): String = ingredient.displayName

    override fun getUniqueId(ingredient: PokemonIngredient, context: UidContext): String = ingredient.species

    override fun getResourceLocation(ingredient: PokemonIngredient): ResourceLocation = ingredient.identifier

    override fun copyIngredient(ingredient: PokemonIngredient): PokemonIngredient = ingredient.copy()

    override fun getErrorInfo(ingredient: PokemonIngredient?): String {
        return ingredient?.species ?: "null"
    }

    override fun getWildcardId(ingredient: PokemonIngredient): String = ingredient.species

    /**
     * JEI renders this natively as the mod-name tag on the tooltip/recipe page - resolved through
     * the loader's own mod registry (`getModNameForModId`), so returning a real mod id here gets
     * that mod's real display name for free, with no custom UI. Falls back to the "cobblemon" tag
     * this always showed before add-on/datapack provenance existed - for base Cobblemon content,
     * and for the rare species [SpawnDataIndex] has no provenance for at all.
     */
    override fun getDisplayModId(ingredient: PokemonIngredient): String =
        SpawnDataIndex.getSpeciesInfo(ingredient.species)?.source ?: "cobblemon"

    override fun getTagEquivalent(ingredients: MutableCollection<PokemonIngredient>): Optional<ResourceLocation> = Optional.empty()

    override fun getColors(ingredient: PokemonIngredient): Iterable<Int> = emptyList()

    override fun isValidIngredient(ingredient: PokemonIngredient): Boolean = ingredient.species.isNotBlank()

    override fun getCheatItemStack(ingredient: PokemonIngredient): ItemStack {
        PokemonCheatHandler.sendPokegiveCommand(ingredient.species, ingredient.formAspects)
        // Return the cosmetic PokemonItem so JEI considers the cheat "handled"
        return PokemonItemCache.getItem(ingredient.species, ingredient.formAspects) ?: ItemStack.EMPTY
    }
}
