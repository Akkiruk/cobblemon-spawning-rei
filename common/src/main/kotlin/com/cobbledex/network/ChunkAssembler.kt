package com.cobbledex.network

import com.cobbledex.DebugLog
import java.io.ByteArrayOutputStream
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object ChunkAssembler {

    private var activeTransferId: Long? = null
    private var expectedChunks = 0
    private val receivedChunks = mutableMapOf<Int, ByteArray>()
    private val lock = ReentrantLock()

    fun reset() {
        lock.withLock {
            activeTransferId = null
            expectedChunks = 0
            receivedChunks.clear()
        }
    }

    fun receiveChunk(payload: ChunkedSpawnSyncPayload): ByteArray? {
        return lock.withLock {
            if (activeTransferId != null && payload.transferId != activeTransferId) {
                DebugLog.warn("Chunk transfer changed from $activeTransferId to ${payload.transferId}, resetting assembler")
                reset()
            }

            if (activeTransferId == null) {
                activeTransferId = payload.transferId
            }

            if (payload.totalChunks != expectedChunks && receivedChunks.isNotEmpty()) {
                DebugLog.warn("Chunk count changed mid-transfer for ${payload.transferId}, resetting assembler")
                reset()
                activeTransferId = payload.transferId
            }

            expectedChunks = payload.totalChunks
            receivedChunks[payload.chunkIndex] = payload.data

            DebugLog.info("Received chunk ${payload.chunkIndex + 1}/${payload.totalChunks} for transfer ${payload.transferId} (${payload.data.size} bytes)")

            if (receivedChunks.size < expectedChunks) return@withLock null

            val bos = ByteArrayOutputStream()
            for (i in 0 until expectedChunks) {
                val chunk = receivedChunks[i]
                if (chunk == null) {
                    DebugLog.warn("Missing chunk $i during assembly")
                    reset()
                    return@withLock null
                }
                bos.write(chunk)
            }
            val result = bos.toByteArray()
            DebugLog.info("Assembled $expectedChunks chunks into ${result.size} bytes for transfer ${payload.transferId}")
            reset()
            result
        }
    }
}
