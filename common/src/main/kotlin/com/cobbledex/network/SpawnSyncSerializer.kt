package com.cobbledex.network

import com.cobbledex.SpawnAntiCondition
import com.cobbledex.SpawnInfo
import com.cobbledex.SpawnWeather
import com.cobbledex.WeightMultiplier
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object SpawnSyncSerializer {

    private val gson: Gson = GsonBuilder().create()

    private val mapType = object : TypeToken<Map<String, List<SpawnInfo>>>() {}.type

    fun serialize(data: Map<String, List<SpawnInfo>>): ByteArray {
        val json = gson.toJson(data, mapType)
        return compress(json.toByteArray(Charsets.UTF_8))
    }

    fun deserialize(compressed: ByteArray): Map<String, List<SpawnInfo>> {
        val json = String(decompress(compressed), Charsets.UTF_8)
        return gson.fromJson(json, mapType)
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
