package com.cobbledex.rei.entry

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
import net.minecraft.world.item.ItemStack
import java.util.stream.Stream

class MoveEntryDefinition : EntryDefinition<MoveEntry> {

    private val renderer = MoveEntryRenderer()

    override fun getValueType(): Class<MoveEntry> = MoveEntry::class.java

    override fun getType(): EntryType<MoveEntry> = MoveEntryType.MOVE

    override fun getRenderer(): EntryRenderer<MoveEntry> = renderer

    override fun getIdentifier(entry: EntryStack<MoveEntry>, value: MoveEntry): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(
            "cobbledex-rei-emi-jei",
            "move/" + value.moveName.lowercase().replace(Regex("[^a-z0-9._-]"), "")
        )

    override fun getContainingNamespace(entry: EntryStack<MoveEntry>, value: MoveEntry): String = "cobblemon"

    override fun isEmpty(entry: EntryStack<MoveEntry>, value: MoveEntry): Boolean = value.moveName.isBlank()

    override fun copy(entry: EntryStack<MoveEntry>, value: MoveEntry): MoveEntry = value.copy()

    override fun normalize(entry: EntryStack<MoveEntry>, value: MoveEntry): MoveEntry = value

    override fun wildcard(entry: EntryStack<MoveEntry>, value: MoveEntry): MoveEntry = value

    override fun hash(entry: EntryStack<MoveEntry>, value: MoveEntry, context: ComparisonContext): Long =
        value.moveName.lowercase().hashCode().toLong()

    override fun equals(o1: MoveEntry, o2: MoveEntry, context: ComparisonContext): Boolean =
        o1.moveName.equals(o2.moveName, ignoreCase = true)

    override fun getSerializer(): EntrySerializer<MoveEntry> = object : EntrySerializer<MoveEntry> {
        override fun supportSaving() = true
        override fun supportReading() = true
        override fun save(entry: EntryStack<MoveEntry>, value: MoveEntry): CompoundTag =
            CompoundTag().apply { putString("move", value.moveName) }
        override fun read(tag: CompoundTag): MoveEntry = MoveEntry(tag.getString("move"))
    }

    override fun asFormattedText(entry: EntryStack<MoveEntry>, value: MoveEntry): Component =
        Component.literal(value.moveName)

    override fun cheatsAs(entry: EntryStack<MoveEntry>, value: MoveEntry): ItemStack? = null

    override fun getTagsFor(entry: EntryStack<MoveEntry>, value: MoveEntry): Stream<out TagKey<*>> = Stream.empty()
}
