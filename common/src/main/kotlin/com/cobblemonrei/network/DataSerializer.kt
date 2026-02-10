package com.cobblemonrei.network

import com.cobblemonrei.*
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object DataSerializer {

    const val MAX_CHUNK_SIZE = 32 * 1024 // 32KB per chunk — gentle on connections

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
        val maxSize = 50 * 1024 * 1024 // 50MB
        val baos = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(data)).use { gzip ->
            val buffer = ByteArray(8192)
            var totalRead = 0
            while (true) {
                val read = gzip.read(buffer)
                if (read == -1) break
                totalRead += read
                if (totalRead > maxSize) {
                    throw IllegalStateException("Decompressed data exceeds 50MB limit")
                }
                baos.write(buffer, 0, read)
            }
        }
        return baos.toByteArray()
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

    fun computeFingerprint(
        spawns: Map<String, List<SpawnInfo>>,
        evolutions: Map<String, List<EvolutionInfo>>,
        speciesInfo: Map<String, EvolutionDataLoader.SpeciesBasicInfo>
    ): String {
        val sb = StringBuilder()
        sb.append("s:${spawns.size}:")
        sb.append(spawns.values.sumOf { it.size })
        sb.append(":e:${evolutions.size}:")
        sb.append(evolutions.values.sumOf { it.size })
        sb.append(":i:${speciesInfo.size}")
        // Include sorted keys for determinism
        for (key in spawns.keys.sorted()) {
            sb.append("|$key:${spawns[key]?.size ?: 0}")
        }
        val md = MessageDigest.getInstance("MD5")
        return md.digest(sb.toString().toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
