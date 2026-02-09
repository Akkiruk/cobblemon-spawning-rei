package com.cobblemonrei

object DebugLog {

    private val loggedOnce = mutableSetOf<String>()
    private val missingModels = mutableSetOf<String>()
    private val failedSpawnParse = mutableSetOf<String>()
    private var summaryPrinted = false

    private val logger get() = CobblemonSpawningMod.LOGGER

    fun info(message: String) {
        logger.info("[CobblemonSpawningREI] $message")
    }

    fun warn(message: String) {
        logger.warn("[CobblemonSpawningREI] $message")
    }

    fun debug(message: String) {
        logger.debug("[CobblemonSpawningREI] $message")
    }

    fun once(key: String, message: () -> String) {
        if (loggedOnce.add(key)) {
            logger.debug("[CobblemonSpawningREI] ${message()}")
        }
    }

    fun trackMissingModel(species: String) {
        missingModels.add(species.lowercase())
    }

    fun trackFailedSpawn(file: String, reason: String) {
        if (failedSpawnParse.add(file)) {
            logger.debug("[CobblemonSpawningREI] Spawn parse failed: $file — $reason")
        }
    }

    fun printSummary() {
        if (summaryPrinted) return
        summaryPrinted = true

        if (missingModels.isNotEmpty()) {
            val sorted = missingModels.sorted()
            val preview = sorted.take(15).joinToString(", ")
            val suffix = if (sorted.size > 15) " ... and ${sorted.size - 15} more" else ""
            logger.info("[CobblemonSpawningREI] ${sorted.size} species hidden (no model): $preview$suffix")
            logger.debug("[CobblemonSpawningREI] Full missing model list: ${sorted.joinToString(", ")}")
        }
        if (failedSpawnParse.isNotEmpty()) {
            logger.debug("[CobblemonSpawningREI] ${failedSpawnParse.size} spawn files had parse issues")
        }
    }

    fun getMissingModelCount(): Int = missingModels.size
    fun hasMissingModel(species: String): Boolean = species.lowercase() in missingModels

    fun reset() {
        loggedOnce.clear()
        missingModels.clear()
        failedSpawnParse.clear()
        summaryPrinted = false
    }
}
