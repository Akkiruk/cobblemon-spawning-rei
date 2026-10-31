package com.cobbledex.server

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer

/**
 * Every namespace with a biome, structure, dimension or block on this server, tags included. Spawn
 * conditions can only name these, and a client can't work them out: it is never sent structures, and
 * a datapack or a server-only mod leaves nothing in its own files. Namespaces rather than mod ids so
 * players aren't handed the server's mod list.
 */
object ServerNamespaces {

    fun collect(server: MinecraftServer): Set<String> = buildSet {
        addAll(of(BuiltInRegistries.BLOCK))
        val access = server.registryAccess()
        fun <T> add(key: ResourceKey<out Registry<T>>) {
            access.registry(key).ifPresent { addAll(of(it)) }
        }
        add(Registries.BIOME)
        add(Registries.STRUCTURE)
        add(Registries.DIMENSION_TYPE)
        add(Registries.LEVEL_STEM)
    }

    private fun of(registry: Registry<*>): Set<String> = buildSet {
        registry.keySet().forEach { add(it.namespace) }
        registry.tagNames.forEach { add(it.location.namespace) }
    }
}
