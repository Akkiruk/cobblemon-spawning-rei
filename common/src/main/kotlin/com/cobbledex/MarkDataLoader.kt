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
            val id = Reflect.call<Any>(mark, "getIdentifier")?.toString()
                ?: Reflect.field<Any>(mark, "identifier")?.toString()
                ?: return null

            val nameKey = Reflect.field<String>(mark, "name") ?: return null
            MarkInfo(
                id = id,
                nameKey = nameKey,
                descriptionKey = Reflect.field<String>(mark, "description") ?: "$nameKey.desc",
                titleKey = Reflect.field<String>(mark, "title"),
                titleColor = Reflect.field<String>(mark, "titleColour"),
                chance = Reflect.field<Number>(mark, "chance")?.toFloat() ?: 0f,
                group = Reflect.field<String>(mark, "group"),
                sortOrder = Reflect.field<Number>(mark, "sortOrder")?.toInt() ?: 0,
                indexNumber = Reflect.field<Number>(mark, "indexNumber")?.toInt(),
            )
        } catch (_: Throwable) {
            null
        }
    }
}
