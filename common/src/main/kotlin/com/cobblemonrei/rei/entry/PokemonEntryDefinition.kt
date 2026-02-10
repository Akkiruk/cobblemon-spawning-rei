package com.cobblemonrei.rei.entry

import me.shedaniel.rei.api.client.entry.renderer.EntryRenderer
import me.shedaniel.rei.api.common.entry.EntrySerializer
import me.shedaniel.rei.api.common.entry.EntryStack
import me.shedaniel.rei.api.common.entry.comparison.ComparisonContext
import me.shedaniel.rei.api.common.entry.type.EntryDefinition
import me.shedaniel.rei.api.common.entry.type.EntryType
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import com.cobblemonrei.SpawnDataIndex
import com.cobblemonrei.titleCase
import java.util.stream.Stream

class PokemonEntryDefinition : EntryDefinition<PokemonEntry> {

    private val renderer = PokemonEntryRenderer()

    override fun getValueType(): Class<PokemonEntry> = PokemonEntry::class.java

    override fun getType(): EntryType<PokemonEntry> = PokemonEntryType.POKEMON

    override fun getRenderer(): EntryRenderer<PokemonEntry> = renderer

    override fun getIdentifier(entry: EntryStack<PokemonEntry>, value: PokemonEntry): ResourceLocation {
        return value.identifier
    }

    override fun getContainingNamespace(entry: EntryStack<PokemonEntry>, value: PokemonEntry): String {
        return "cobblemon"
    }

    override fun isEmpty(entry: EntryStack<PokemonEntry>, value: PokemonEntry): Boolean {
        return value.species.isBlank()
    }

    override fun copy(entry: EntryStack<PokemonEntry>, value: PokemonEntry): PokemonEntry {
        return value.copy()
    }

    override fun normalize(entry: EntryStack<PokemonEntry>, value: PokemonEntry): PokemonEntry {
        return value
    }

    override fun wildcard(entry: EntryStack<PokemonEntry>, value: PokemonEntry): PokemonEntry {
        return PokemonEntry(value.species)
    }

    override fun hash(entry: EntryStack<PokemonEntry>, value: PokemonEntry, context: ComparisonContext): Long {
        return if (context.isExact) {
            value.species.hashCode().toLong() * 31 + value.formAspects.hashCode().toLong()
        } else {
            value.species.hashCode().toLong()
        }
    }

    override fun equals(o1: PokemonEntry, o2: PokemonEntry, context: ComparisonContext): Boolean {
        return if (context.isExact) {
            o1.species == o2.species && o1.formAspects == o2.formAspects
        } else {
            o1.species == o2.species
        }
    }

    override fun getSerializer(): EntrySerializer<PokemonEntry> = object : EntrySerializer<PokemonEntry> {
        override fun supportSaving() = true
        override fun supportReading() = true

        override fun save(entry: EntryStack<PokemonEntry>, value: PokemonEntry): CompoundTag {
            val tag = CompoundTag()
            tag.putString("species", value.species)
            if (value.formAspects.isNotEmpty()) {
                tag.putString("aspects", value.formAspects.joinToString(","))
            }
            return tag
        }

        override fun read(tag: CompoundTag): PokemonEntry {
            val species = tag.getString("species")
            val aspects = tag.getString("aspects").let {
                if (it.isBlank()) emptySet() else it.split(",").toSet()
            }
            return PokemonEntry(species, aspects)
        }
    }

    override fun asFormattedText(entry: EntryStack<PokemonEntry>, value: PokemonEntry): Component {
        val info = SpawnDataIndex.getSpeciesInfo(value.species)
        return if (info != null) {
            val types = listOfNotNull(info.primaryType, info.secondaryType).joinToString(" ") { titleCase(it) }
            Component.literal("${value.displayName} $types")
        } else {
            Component.literal(value.displayName)
        }
    }

    override fun getTagsFor(entry: EntryStack<PokemonEntry>, value: PokemonEntry): Stream<out TagKey<*>> {
        return Stream.empty()
    }
}
