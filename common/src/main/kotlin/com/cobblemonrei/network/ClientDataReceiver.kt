package com.cobblemonrei.network

import com.cobblemonrei.DebugLog
import com.cobblemonrei.SpawnDataIndex

object ClientDataReceiver {

    @Volatile
    private var receivedChunks: Array<ByteArray?> = emptyArray()

    @Volatile
    private var expectedTotal = 0

    @Volatile
    private var receivedCount = 0

    /** When true, server data matches local — ignore incoming chunks */
    @Volatile
    private var ignoreServerData = false

    fun onHashReceived(payload: SpawnSyncHashPayload) {
        val serverFingerprint = payload.fingerprint
        val localFingerprint = SpawnDataIndex.computeFingerprint()

        if (serverFingerprint == localFingerprint) {
            DebugLog.info("Server fingerprint matches local data ($serverFingerprint) — skipping sync")
            ignoreServerData = true
        } else {
            DebugLog.info("Server fingerprint differs (server=$serverFingerprint, local=$localFingerprint) — accepting sync")
            ignoreServerData = false
        }
    }

    fun onChunkReceived(payload: SpawnSyncPayload) {
        if (ignoreServerData) return

        val idx = payload.chunkIndex
        val total = payload.totalChunks

        if (total <= 0) return

        if (idx == 0 || total != expectedTotal) {
            receivedChunks = arrayOfNulls(total)
            expectedTotal = total
            receivedCount = 0
            DebugLog.debug("Receiving spawn sync data: $total chunk(s)")
        }

        if (idx < 0 || idx >= expectedTotal) {
            DebugLog.warn("Invalid chunk index $idx/$expectedTotal")
            return
        }

        if (receivedChunks[idx] == null) {
            receivedChunks[idx] = payload.data
            receivedCount++
            DebugLog.debug("Received chunk ${idx + 1}/$expectedTotal (${payload.data.size} bytes)")
        }

        if (receivedCount >= expectedTotal) {
            assembleAndApply()
        }
    }

    private fun assembleAndApply() {
        try {
            val chunks = receivedChunks.filterNotNull()
            if (chunks.size != expectedTotal) {
                DebugLog.warn("Chunk count mismatch: got ${chunks.size}, expected $expectedTotal")
                reset()
                return
            }

            val compressed = DataSerializer.reassembleChunks(chunks)
            DebugLog.info("Reassembled ${compressed.size} bytes from $expectedTotal chunk(s), decompressing...")

            val data = DataSerializer.deserialize(compressed)
            SpawnDataIndex.applyServerData(data.spawns, data.evolutions, data.speciesInfo)

            DebugLog.info("Server data applied: ${data.spawns.size} spawn species, ${data.evolutions.size} evolution species, ${data.speciesInfo.size} species info")
        } catch (e: Exception) {
            DebugLog.warn("Failed to deserialize server spawn data: ${e.message}")
        } finally {
            reset()
        }
    }

    fun reset() {
        receivedChunks = emptyArray()
        expectedTotal = 0
        receivedCount = 0
        ignoreServerData = false
    }
}
