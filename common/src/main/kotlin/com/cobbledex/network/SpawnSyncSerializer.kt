package com.cobbledex.network

import com.cobbledex.EvolutionDataLoader
import com.cobbledex.EvolutionInfo
import com.cobbledex.FossilCombo
import com.cobbledex.FossilDataLoader
import com.cobbledex.JobRule
import com.cobbledex.ObtainmentDataLoader
import com.cobbledex.ObtainmentInfo
import com.cobbledex.RidingDataLoader
import com.cobbledex.RidingInfo
import com.cobbledex.SpawnDataLoader
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
    val obtainment: Map<String, List<ObtainmentInfo>> = emptyMap(),
    val riding: Map<String, RidingInfo> = emptyMap(),
    val jobRules: List<JobRule>? = null,
    val fossils: Map<String, List<FossilCombo>>? = null,
    val isComplete: Boolean = true,
)

data class PreparedSyncPayloads(
    val legacyPayload: SpawnSyncPayload,
    val chunkedPayloads: List<ChunkedSpawnSyncPayload>,
    val speciesCount: Int,
    val totalSpawnEntries: Int,
    val evolutionEntryCount: Int,
    val speciesInfoCount: Int,
    val obtainmentSpeciesCount: Int,
    val ridingSpeciesCount: Int,
    val fossilSpeciesCount: Int,
    val compressedSize: Int,
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

object ServerSyncPayloadFactory {
    fun getOrBuild(): PreparedSyncPayloads {
        val bundle = SyncBundle(
            spawns = SpawnDataLoader.loadFromRuntime(),
            evolutions = EvolutionDataLoader.loadFromRuntime(),
            speciesInfo = EvolutionDataLoader.loadSpeciesBasicInfoFromRuntime(),
            obtainment = ObtainmentDataLoader.loadFromAllSources(SpawnDataLoader.getModRootPaths()),
            riding = RidingDataLoader.loadFromRuntime(),
            fossils = FossilDataLoader.loadFromRuntime(),
            isComplete = true,
        )
        val compressed = SpawnSyncSerializer.serialize(bundle)
        return PreparedSyncPayloads(
            legacyPayload = SpawnSyncPayload(compressed),
            chunkedPayloads = ChunkedSpawnSyncPayload.split(compressed),
            speciesCount = bundle.spawns.size,
            totalSpawnEntries = bundle.spawns.values.sumOf { it.size },
            evolutionEntryCount = bundle.evolutions.values.sumOf { it.size },
            speciesInfoCount = bundle.speciesInfo.size,
            obtainmentSpeciesCount = bundle.obtainment.size,
            ridingSpeciesCount = bundle.riding.size,
            fossilSpeciesCount = bundle.fossils?.size ?: 0,
            compressedSize = compressed.size,
        )
    }

    fun invalidate() = Unit
}
