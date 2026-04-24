package com.cobbledex.network

import com.cobbledex.EvolutionDataLoader
import com.cobbledex.EvolutionInfo
import com.cobbledex.FossilCombo
import com.cobbledex.FossilDataLoader
import com.cobbledex.JobRule
import com.cobbledex.SpawnDataLoader
import com.cobbledex.SpawnInfo
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.concurrent.withLock

data class SyncBundle(
    val spawns: Map<String, List<SpawnInfo>>,
    val evolutions: Map<String, List<EvolutionInfo>>,
    val speciesInfo: Map<String, EvolutionDataLoader.SpeciesBasicInfo>,
    val jobRules: List<JobRule>? = null,
    val fossils: Map<String, List<FossilCombo>>? = null,
)

data class PreparedSyncPayloads(
    val legacyPayload: SpawnSyncPayload,
    val chunkedPayloads: List<ChunkedSpawnSyncPayload>,
    val speciesCount: Int,
    val totalSpawnEntries: Int,
    val evolutionEntryCount: Int,
    val speciesInfoCount: Int,
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

    private const val MAX_CACHE_AGE_MS = 15_000L

    @Volatile private var cachedPreparedSync: PreparedSyncPayloads? = null
    @Volatile private var cachedAtMs = 0L
    private val lock = ReentrantLock()

    fun getOrBuild(): PreparedSyncPayloads {
        return lock.withLock {
            val now = System.currentTimeMillis()
            cachedPreparedSync?.takeIf { now - cachedAtMs <= MAX_CACHE_AGE_MS }?.let { return@withLock it }

            val bundle = SyncBundle(
                spawns = SpawnDataLoader.loadFromRuntime(),
                evolutions = EvolutionDataLoader.loadFromRuntime(),
                speciesInfo = EvolutionDataLoader.loadSpeciesBasicInfoFromRuntime(),
                fossils = FossilDataLoader.loadFromRuntime(),
            )
            val compressed = SpawnSyncSerializer.serialize(bundle)
            val prepared = PreparedSyncPayloads(
                legacyPayload = SpawnSyncPayload(compressed),
                chunkedPayloads = ChunkedSpawnSyncPayload.split(compressed),
                speciesCount = bundle.spawns.size,
                totalSpawnEntries = bundle.spawns.values.sumOf { it.size },
                evolutionEntryCount = bundle.evolutions.values.sumOf { it.size },
                speciesInfoCount = bundle.speciesInfo.size,
                fossilSpeciesCount = bundle.fossils?.size ?: 0,
                compressedSize = compressed.size,
            )
            cachedPreparedSync = prepared
            cachedAtMs = now
            prepared
        }
    }

    fun invalidate() {
        lock.withLock {
            cachedPreparedSync = null
            cachedAtMs = 0L
        }
    }
}
