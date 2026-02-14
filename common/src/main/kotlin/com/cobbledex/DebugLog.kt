package com.cobbledex

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

object DebugLog {

    private val loggedOnce: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    private val missingModels: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    private val failedSpawnParse: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    @Volatile
    private var summaryPrinted = false

    private val logger get() = CobbleDexMod.LOGGER

    fun info(message: String) {
        logger.info("[CobbleDex] $message")
    }

    fun warn(message: String) {
        logger.warn("[CobbleDex] $message")
    }

    fun debug(message: String) {
        if (!isDebugEnabled()) return
        logger.debug("[CobbleDex] $message")
    }

    fun warnOnce(key: String, message: () -> String) {
        if (loggedOnce.add(key)) {
            logger.warn("[CobbleDex] ${message()}")
        }
    }

    private fun isDebugEnabled(): Boolean {
        return try {
            com.cobbledex.config.CobbleDexConfig.get().debugMode
        } catch (_: Exception) { true }
    }

    fun once(key: String, message: () -> String) {
        if (loggedOnce.add(key)) {
            logger.debug("[CobbleDex] ${message()}")
        }
    }

    fun trackMissingModel(species: String) {
        missingModels.add(species.lowercase())
    }

    fun trackFailedSpawn(file: String, reason: String) {
        if (failedSpawnParse.add(file)) {
            logger.debug("[CobbleDex] Spawn parse failed: $file — $reason")
        }
    }

    fun printSummary() {
        if (summaryPrinted) return
        summaryPrinted = true

        if (missingModels.isNotEmpty()) {
            val sorted = missingModels.sorted()
            val preview = sorted.take(15).joinToString(", ")
            val suffix = if (sorted.size > 15) " ... and ${sorted.size - 15} more" else ""
            logger.info("[CobbleDex] ${sorted.size} species hidden (no model): $preview$suffix")
            logger.debug("[CobbleDex] Full missing model list: ${sorted.joinToString(", ")}")
        }
        if (failedSpawnParse.isNotEmpty()) {
            logger.debug("[CobbleDex] ${failedSpawnParse.size} spawn files had parse issues")
        }
    }

    fun reset() {
        loggedOnce.clear()
        missingModels.clear()
        failedSpawnParse.clear()
        summaryPrinted = false
    }
}
