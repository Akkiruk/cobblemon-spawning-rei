package com.cobbledex.network

import com.cobbledex.DebugLog
import java.io.ByteArrayOutputStream

object ChunkAssembler {

    private var expectedChunks = 0
    private val receivedChunks = mutableMapOf<Int, ByteArray>()

    @Synchronized
    fun reset() {
        expectedChunks = 0
        receivedChunks.clear()
    }

    @Synchronized
    fun receiveChunk(payload: ChunkedSpawnSyncPayload): ByteArray? {
        if (payload.totalChunks != expectedChunks && receivedChunks.isNotEmpty()) {
            DebugLog.warn("Chunk session mismatch, resetting assembler")
            reset()
        }

        expectedChunks = payload.totalChunks
        receivedChunks[payload.chunkIndex] = payload.data

        DebugLog.info("Received chunk ${payload.chunkIndex + 1}/${payload.totalChunks} (${payload.data.size} bytes)")

        if (receivedChunks.size < expectedChunks) return null

        val bos = ByteArrayOutputStream()
        for (i in 0 until expectedChunks) {
            val chunk = receivedChunks[i]
            if (chunk == null) {
                DebugLog.warn("Missing chunk $i during assembly")
                reset()
                return null
            }
            bos.write(chunk)
        }
        val result = bos.toByteArray()
        DebugLog.info("Assembled $expectedChunks chunks into ${result.size} bytes")
        reset()
        return result
    }
}
