package com.cobbledex.network

import com.cobbledex.EvolutionDataLoader
import com.cobbledex.EvolutionInfo
import com.cobbledex.JobRule
import com.cobbledex.SpawnInfo
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class SyncBundle(
    val spawns: Map<String, List<SpawnInfo>>,
    val evolutions: Map<String, List<EvolutionInfo>>,
    val speciesInfo: Map<String, EvolutionDataLoader.SpeciesBasicInfo>,
    val jobRules: List<JobRule>? = null,
)

object SpawnSyncSerializer {

    private val gson: Gson = GsonBuilder().create()

    private val bundleType = object : TypeToken<SyncBundle>() {}.type

    fun serialize(bundle: SyncBundle): ByteArray {
        val json = gson.toJson(bundle, bundleType)
        return compress(json.toByteArray(Charsets.UTF_8))
    }

    fun deserialize(compressed: ByteArray): SyncBundle {
        val json = String(decompress(compressed), Charsets.UTF_8)
        return gson.fromJson(json, bundleType)
    }

    private fun compress(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream(data.size / 4)
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun decompress(data: ByteArray): ByteArray {
        return GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
    }
}
