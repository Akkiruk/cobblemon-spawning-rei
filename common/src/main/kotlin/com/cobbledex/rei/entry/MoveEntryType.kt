package com.cobbledex.rei.entry

import me.shedaniel.rei.api.common.entry.type.EntryType
import net.minecraft.resources.ResourceLocation

object MoveEntryType {
    val MOVE: EntryType<MoveEntry> = EntryType.deferred(
        ResourceLocation.fromNamespaceAndPath("cobbledex-rei-emi-jei", "move")
    )
}
