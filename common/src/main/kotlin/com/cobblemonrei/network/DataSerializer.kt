package com.cobblemonrei.network

import com.cobblemonrei.*
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object DataSerializer {

    const val MAX_CHUNK_SIZE = 900 * 1024 // 900KB per chunk

    private val GSON: Gson = GsonBuilder().create()

    fun serialize(
        spawns: Map<String, List<SpawnInfo>>,
        evolutions: Map<String, List<EvolutionInfo>>,
        speciesInfo: Map<String, EvolutionDataLoader.SpeciesBasicInfo>
    ): ByteArray {
        val wrapper = JsonObject()
        wrapper.add("spawns", GSON.toJsonTree(spawns))
        wrapper.add("evolutions", GSON.toJsonTree(evolutions))
        wrapper.add("speciesInfo", GSON.toJsonTree(speciesInfo))
        val json = wrapper.toString()
        return compress(json.toByteArray(Charsets.UTF_8))
    }

    data class DeserializedData(
        val spawns: Map<String, List<SpawnInfo>>,
        val evolutions: Map<String, List<EvolutionInfo>>,
        val speciesInfo: Map<String, EvolutionDataLoader.SpeciesBasicInfo>
    )

    fun deserialize(compressed: ByteArray): DeserializedData {
        val json = decompress(compressed).toString(Charsets.UTF_8)
        val wrapper = JsonParser.parseString(json).asJsonObject

        val spawnsType = object : TypeToken<Map<String, List<SpawnInfo>>>() {}.type
        val evosType = object : TypeToken<Map<String, List<EvolutionInfo>>>() {}.type
        val speciesType = object : TypeToken<Map<String, EvolutionDataLoader.SpeciesBasicInfo>>() {}.type

        val spawns: Map<String, List<SpawnInfo>> = GSON.fromJson(wrapper.get("spawns"), spawnsType) ?: emptyMap()
        val evolutions: Map<String, List<EvolutionInfo>> = GSON.fromJson(wrapper.get("evolutions"), evosType) ?: emptyMap()
        val speciesInfo: Map<String, EvolutionDataLoader.SpeciesBasicInfo> = GSON.fromJson(wrapper.get("speciesInfo"), speciesType) ?: emptyMap()

        return DeserializedData(spawns, evolutions, speciesInfo)
    }

    fun compress(data: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(data) }
        return baos.toByteArray()
    }

    fun decompress(data: ByteArray): ByteArray {
        return GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
    }

    fun splitIntoChunks(data: ByteArray, maxChunkSize: Int = MAX_CHUNK_SIZE): List<ByteArray> {
        if (data.size <= maxChunkSize) return listOf(data)
        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < data.size) {
            val end = (offset + maxChunkSize).coerceAtMost(data.size)
            chunks.add(data.copyOfRange(offset, end))
            offset = end
        }
        return chunks
    }

    fun reassembleChunks(chunks: List<ByteArray>): ByteArray {
        val total = chunks.sumOf { it.size }
        val result = ByteArray(total)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(result, offset)
            offset += chunk.size
        }
        return result
    }
}
