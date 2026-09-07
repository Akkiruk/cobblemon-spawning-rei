package com.cobbledex

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

data class ViewerParityIssue(
    val categoryId: String,
    val recipeIdPath: String,
    val message: String,
)

object ViewerParityGuard {
    private val globalCategories = setOf("natures", "marks")
    private val validatedContexts = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    fun validate(def: DexCategory, handles: List<RecipeHandle>): List<ViewerParityIssue> =
        validateHandles(def.id, handles, allowGlobalHandles = def.id in globalCategories)

    fun validateHandles(
        categoryId: String,
        handles: List<RecipeHandle>,
        allowGlobalHandles: Boolean = false,
    ): List<ViewerParityIssue> {
        val issues = mutableListOf<ViewerParityIssue>()
        val ids = mutableSetOf<String>()

        for (handle in handles) {
            val recipeId = handle.recipeIdPath
            fun issue(message: String) {
                issues.add(ViewerParityIssue(categoryId, recipeId.ifBlank { "<blank>" }, message))
            }

            if (recipeId.isBlank()) {
                issue("blank recipe id")
            } else if (!ids.add(recipeId)) {
                issue("duplicate recipe id")
            }

            val inputSpecies = handle.lookupInputSpecies()
            val outputSpecies = handle.lookupOutputSpecies()
            val hasItemKeys = handle.lookupInputItemIds().isNotEmpty() || handle.lookupOutputItemIds().isNotEmpty()
            if (!allowGlobalHandles && inputSpecies.isEmpty() && outputSpecies.isEmpty() && !hasItemKeys) {
                issue("no species lookup keys")
            }

            val width = handle.explicitWidthOrNull()
            val height = handle.explicitHeightOrNull()
            if (handle.hasExplicitSize && (width == null || height == null)) {
                issue("explicit size failed to measure")
            }
            if (width != null && (width < PanelLayout.MIN_WIDTH || width > PanelLayout.MAX_WIDTH)) {
                issue("width $width outside viewer bounds")
            }
            if (height != null && (height <= 0 || height > PanelLayout.MAX_HEIGHT)) {
                issue("height $height outside viewer bounds")
            }
        }

        return issues
    }

    fun warn(def: DexCategory, handles: List<RecipeHandle>, viewerName: String) {
        val contextKey = "$viewerName-${def.id}-${SpawnDataIndex.dataVersion}-${handles.size}"
        if (!validatedContexts.add(contextKey)) return

        val issues = validate(def, handles)
        if (issues.isEmpty()) return

        val preview = issues.take(5).joinToString("; ") { issue ->
            "${issue.categoryId}/${issue.recipeIdPath}: ${issue.message}"
        }
        val suffix = if (issues.size > 5) " ... and ${issues.size - 5} more" else ""
        DebugLog.warnOnce("viewer-parity-$viewerName-${def.id}-${issues.size}") {
            "$viewerName parity guard found ${issues.size} issue(s): $preview$suffix"
        }
    }
}