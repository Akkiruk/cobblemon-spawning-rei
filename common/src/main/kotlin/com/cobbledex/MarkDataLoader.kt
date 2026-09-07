package com.cobbledex

/**
 * Reads Cobblemon's Mark registry (`Marks.all()`, 1.8.0+) by reflection so the jar still loads on
 * 1.7.x. The registry is client-synced with every field, so the runtime read is player-truth;
 * [JarDataCache] is the fallback.
 *
 * Field access is by declared field rather than getter: `Mark` exposes both `val name` and a
 * `fun getName(): Component`, so `getName()` is ambiguous to reflect.
 */
object MarkDataLoader {

    private const val MARKS_CLASS = "com.cobblemon.mod.common.api.mark.Marks"

    fun loadFromRuntime(): List<MarkInfo> {
        return try {
            val all = Class.forName(MARKS_CLASS).getMethod("all").invoke(null) as? List<*> ?: return emptyList()
            val out = all.mapNotNull { it?.let(::readMark) }
            if (out.isNotEmpty()) DebugLog.info("Loaded ${out.size} marks from Cobblemon runtime")
            out
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun readMark(mark: Any): MarkInfo? {
        return try {
            fun <T> field(name: String): T? = try {
                @Suppress("UNCHECKED_CAST")
                mark.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(mark) as? T
            } catch (_: Throwable) { null }

            val id = try {
                mark.javaClass.getMethod("getIdentifier").invoke(mark)?.toString()
            } catch (_: Throwable) { null } ?: field<Any>("identifier")?.toString() ?: return null

            val nameKey = field<String>("name") ?: return null
            MarkInfo(
                id = id,
                nameKey = nameKey,
                descriptionKey = field<String>("description") ?: "$nameKey.desc",
                titleKey = field<String>("title"),
                titleColor = field<String>("titleColour"),
                chance = (field<Number>("chance"))?.toFloat() ?: 0f,
                group = field<String>("group"),
                sortOrder = (field<Number>("sortOrder"))?.toInt() ?: 0,
                indexNumber = (field<Number>("indexNumber"))?.toInt(),
            )
        } catch (_: Throwable) {
            null
        }
    }
}
