package com.cobblemonrei

import com.google.gson.JsonParser
import java.io.InputStreamReader

/**
 * Localization helper. All user-facing strings go through [tr] so they
 * can be overridden via resource-pack lang files.
 *
 * On the client, delegates to Minecraft's I18n. On a dedicated server
 * (where I18n doesn't exist), falls back to the bundled en_us.json.
 */
fun tr(key: String, vararg args: Any): String = ServerSafeI18n.get(key, *args)

private object ServerSafeI18n {

    private val isClient: Boolean = try {
        Class.forName("net.minecraft.client.resources.language.I18n")
        true
    } catch (_: ClassNotFoundException) { false }
    catch (_: NoClassDefFoundError) { false }

    private val fallbackLang: Map<String, String> by lazy { loadBundledLang() }

    fun get(key: String, vararg args: Any): String {
        if (isClient) {
            return ClientI18nBridge.get(key, *args)
        }
        val template = fallbackLang[key] ?: return key
        return if (args.isEmpty()) template else String.format(template, *args)
    }

    private fun loadBundledLang(): Map<String, String> {
        return try {
            val stream = ServerSafeI18n::class.java.getResourceAsStream(
                "/assets/cobblemon-spawning-rei/lang/en_us.json"
            ) ?: return emptyMap()
            stream.use { input ->
                InputStreamReader(input, Charsets.UTF_8).use { reader ->
                    val json = JsonParser.parseReader(reader).asJsonObject
                    json.entrySet().associate { it.key to it.value.asString }
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

/**
 * Isolated bridge so I18n is only class-loaded on the client.
 * This object must NEVER be referenced from server code paths.
 */
private object ClientI18nBridge {
    fun get(key: String, vararg args: Any): String =
        net.minecraft.client.resources.language.I18n.get(key, *args)
}

// --- Shared text producers (single source of truth for measurement + rendering) ---

fun levelText(levelRange: String): String = tr("cobblemon-spawning-rei.spawn.level", levelRange)

fun weightText(weight: Float): String = tr("cobblemon-spawning-rei.spawn.weight", SpawnDisplayHelper.formatWeight(weight))

fun obtainmentUseText(block: String): String = tr("cobblemon-spawning-rei.obtainment.use", block)

fun obtainmentStructureText(structure: String): String = tr("cobblemon-spawning-rei.obtainment.structure", structure)

fun obtainmentDimensionText(dimension: String): String = tr("cobblemon-spawning-rei.obtainment.dimension", dimension)

fun evoBranchText(index: Int, total: Int): String = tr("cobblemon-spawning-rei.evo.branch", index, total)

fun sourceLabel(source: String): String = when (source) {
    "bundled" -> tr("cobblemon-spawning-rei.source.builtin")
    "datapack" -> tr("cobblemon-spawning-rei.source.datapack")
    "mod" -> tr("cobblemon-spawning-rei.source.mod")
    else -> ""
}
