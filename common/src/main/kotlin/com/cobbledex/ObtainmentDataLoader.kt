package com.cobbledex

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.cobbledex.platform.PlatformHelper
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path

object ObtainmentDataLoader {

    private const val DATA_PATH = "special_obtainment"

    private val modLoadedCache = mutableMapOf<String, Boolean>()

    private fun isModLoaded(modId: String): Boolean {
        modLoadedCache[modId]?.let { return it }
        val loaded = try {
            PlatformHelper.isModLoaded(modId)
        } catch (_: Exception) {
            false
        }
        modLoadedCache[modId] = loaded
        return loaded
    }

    fun loadFromAllSources(modRoots: List<Path>): Map<String, List<ObtainmentInfo>> {
        val result = mutableMapOf<String, MutableList<ObtainmentInfo>>()
        var totalEntries = 0

        for (root in modRoots) {
            try {
                val dataDir = root.resolve("data")
                if (!Files.exists(dataDir) || !Files.isDirectory(dataDir)) continue

                Files.list(dataDir).use { namespaces ->
                    namespaces.filter { Files.isDirectory(it) }.forEach { namespace ->
                        val obtainDir = namespace.resolve(DATA_PATH)
                        if (Files.exists(obtainDir) && Files.isDirectory(obtainDir)) {
                            Files.walk(obtainDir, 10).use { files ->
                                files.filter { it.toString().endsWith(".json") }.forEach { file ->
                                    totalEntries += parseObtainmentFile(file, result, "mod")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                DebugLog.once("obtainment-root-$root") { "Obtainment scan failed: ${e.message}" }
            }
        }

        val datapacksDir = try {
            PlatformHelper.getGameDir().resolve("datapacks")
        } catch (_: Exception) { null }

        if (datapacksDir != null && Files.exists(datapacksDir) && Files.isDirectory(datapacksDir)) {
            totalEntries += scanDatapacksDir(datapacksDir, result)
        }

        // Merge bundled defaults only if LumyMon is actually installed
        if (isModLoaded("lumymon")) {
            val bundled = loadBundledDefaults()
            for ((species, infos) in bundled) {
                if (!result.containsKey(species)) {
                    result[species] = infos.toMutableList()
                    totalEntries += infos.size
                }
            }
        }

        if (totalEntries > 0) {
            DebugLog.info("Loaded $totalEntries special obtainment entries for ${result.size} species")
        }
        return result
    }

    private fun scanDatapacksDir(datapacksDir: Path, result: MutableMap<String, MutableList<ObtainmentInfo>>): Int {
        var count = 0
        Files.list(datapacksDir).use { packs ->
            packs.filter { Files.isDirectory(it) }.forEach { pack ->
                val dataDir = pack.resolve("data")
                if (Files.exists(dataDir)) {
                    Files.list(dataDir).use { namespaces ->
                        namespaces.filter { Files.isDirectory(it) }.forEach { namespace ->
                            val obtainDir = namespace.resolve(DATA_PATH)
                            if (Files.exists(obtainDir) && Files.isDirectory(obtainDir)) {
                                Files.walk(obtainDir, 10).use { files ->
                                    files.filter { it.toString().endsWith(".json") }.forEach { file ->
                                        count += parseObtainmentFile(file, result, "datapack")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return count
    }

    private fun parseObtainmentFile(file: Path, result: MutableMap<String, MutableList<ObtainmentInfo>>, source: String): Int {
        var count = 0
        try {
            val json = Files.newInputStream(file).use { stream ->
                InputStreamReader(stream).use { reader ->
                    JsonParser.parseReader(reader)
                }
            }

            if (json.isJsonArray) {
                for (element in json.asJsonArray) {
                    val info = parseEntry(element.asJsonObject, source)
                    if (info != null) {
                        result.getOrPut(info.pokemon) { mutableListOf() }.add(info)
                        count++
                    }
                }
            } else if (json.isJsonObject) {
                val obj = json.asJsonObject
                if (obj.has("entries")) {
                    for (element in obj.getAsJsonArray("entries")) {
                        val info = parseEntry(element.asJsonObject, source)
                        if (info != null) {
                            result.getOrPut(info.pokemon) { mutableListOf() }.add(info)
                            count++
                        }
                    }
                } else {
                    val info = parseEntry(obj, source)
                    if (info != null) {
                        result.getOrPut(info.pokemon) { mutableListOf() }.add(info)
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            DebugLog.once("obtainment-parse-${file.fileName}") { "Failed to parse obtainment file: ${e.message}" }
        }
        return count
    }

    private fun parseEntry(obj: JsonObject, source: String): ObtainmentInfo? {
        val pokemon = obj.get("pokemon")?.asString?.lowercase() ?: return null
        val method = obj.get("method")?.asString ?: return null
        val description = obj.get("description")?.asString ?: ""
        val items = obj.getAsJsonArray("items")?.map { it.asString } ?: emptyList()
        val block = obj.get("block")?.asString
        val structure = obj.get("structure")?.asString
        val dimension = obj.get("dimension")?.asString
        val notes = obj.getAsJsonArray("notes")?.map { it.asString } ?: emptyList()
        val formAspects = obj.get("form")?.asString ?: ""

        return ObtainmentInfo(
            pokemon = pokemon,
            formAspects = formAspects,
            method = method,
            description = description,
            items = items,
            block = block,
            structure = structure,
            dimension = dimension,
            notes = notes,
            source = source
        )
    }

    private fun loadBundledDefaults(): Map<String, List<ObtainmentInfo>> {
        val result = mutableMapOf<String, MutableList<ObtainmentInfo>>()
        for (entry in buildBundledObtainment()) {
            result.getOrPut(entry.pokemon) { mutableListOf() }.add(entry)
        }
        return result
    }

    @Suppress("LongMethod")
    private fun buildBundledObtainment(): List<ObtainmentInfo> = listOf(
            altar("articuno", "lumymon:glacier_feather", "lumymon:articuno_altar",
                "cobbledex-rei-emi-jei.bundled.articuno"),
            altar("zapdos", "lumymon:thunder_feather", "lumymon:zapdos_altar",
                "cobbledex-rei-emi-jei.bundled.zapdos"),
            altar("moltres", "lumymon:ember_feather", "lumymon:moltres_altar",
                "cobbledex-rei-emi-jei.bundled.moltres"),

            altar("lugia", "lumymon:silver_wing", "lumymon:lugia_altar",
                "cobbledex-rei-emi-jei.bundled.lugia"),
            altar("hooh", "lumymon:rainbow_wing", "lumymon:hooh_altar",
                "cobbledex-rei-emi-jei.bundled.hooh"),

            shrine("celebi", "lumymon:gs_ball", "lumymon:ilex_shrine",
                "cobbledex-rei-emi-jei.bundled.celebi"),
            shrine("mew", "lumymon:melodic_tape_vol_1", "lumymon:mew_shrine",
                "cobbledex-rei-emi-jei.bundled.mew"),
            shrine("jirachi", "lumymon:meteorite_crystal", "lumymon:jirachi_shrine",
                "cobbledex-rei-emi-jei.bundled.jirachi"),

            altar("regirock", "lumymon:pebble_relic", "lumymon:regirock_altar",
                "cobbledex-rei-emi-jei.bundled.regirock"),
            altar("regice", "lumymon:cryo_relic", "lumymon:regice_altar",
                "cobbledex-rei-emi-jei.bundled.regice"),
            altar("registeel", "lumymon:metal_relic", "lumymon:registeel_altar",
                "cobbledex-rei-emi-jei.bundled.registeel"),

            altar("regieleki", "lumymon:metal_relic", "lumymon:regieleki_altar",
                "cobbledex-rei-emi-jei.bundled.regieleki",
                noteKeys = listOf("cobbledex-rei-emi-jei.bundled.note.regi_nearby")),
            altar("regidrago", "lumymon:metal_relic", "lumymon:regidrago_altar",
                "cobbledex-rei-emi-jei.bundled.regidrago",
                noteKeys = listOf("cobbledex-rei-emi-jei.bundled.note.regi_nearby")),

            altar("latias", "lumymon:ruby_dew", "lumymon:latias_altar",
                "cobbledex-rei-emi-jei.bundled.latias"),
            altar("latios", "lumymon:sapphire_dew", "lumymon:latios_altar",
                "cobbledex-rei-emi-jei.bundled.latios"),

            altar("kyogre", "lumymon:ocean_core", "lumymon:kyogre_altar",
                "cobbledex-rei-emi-jei.bundled.kyogre"),
            altar("groudon", "lumymon:earth_core", "lumymon:groudon_altar",
                "cobbledex-rei-emi-jei.bundled.groudon"),
            altar("rayquaza", "lumymon:sky_core", "lumymon:rayquaza_altar",
                "cobbledex-rei-emi-jei.bundled.rayquaza"),

            shrine("deoxys", "lumymon:meteorite_crystal", "lumymon:deoxys_shrine",
                "cobbledex-rei-emi-jei.bundled.deoxys"),

            altar("calyrex", "lumymon:calyrex_crown", "lumymon:calyrex_statue",
                "cobbledex-rei-emi-jei.bundled.calyrex"),
            altar("glastrier", "lumymon:iceroot_carrot", "lumymon:summon_trigger",
                "cobbledex-rei-emi-jei.bundled.glastrier",
                noteKeys = listOf("cobbledex-rei-emi-jei.bundled.note.calyrex_nearby")),
            altar("spectrier", "lumymon:shaderoot_carrot", "lumymon:summon_trigger",
                "cobbledex-rei-emi-jei.bundled.spectrier",
                noteKeys = listOf("cobbledex-rei-emi-jei.bundled.note.calyrex_nearby")),

            resurrection("mewtwo", "lumymon:ancient_dna", "cobblemon:resurrection_machine",
                "cobbledex-rei-emi-jei.bundled.mewtwo"),
            resurrection("type_null", "lumymon:fossilized_helmet", "cobblemon:resurrection_machine",
                "cobbledex-rei-emi-jei.bundled.type_null"),

            transformation("lugia", "lumymon:shadow_soul_stone", null,
                "cobbledex-rei-emi-jei.bundled.lugia_shadow",
                form = "shadow",
                noteKeys = listOf("cobbledex-rei-emi-jei.bundled.note.shadow_transform"))
    )

    private fun altar(pokemon: String, item: String, block: String, descriptionKey: String,
                      noteKeys: List<String> = emptyList()) =
        ObtainmentInfo(
            pokemon = pokemon,
            method = "altar",
            description = "",
            descriptionKey = descriptionKey,
            items = listOf(item),
            block = block,
            noteKeys = noteKeys,
            source = "bundled"
        )

    private fun shrine(pokemon: String, item: String, block: String, descriptionKey: String,
                       noteKeys: List<String> = emptyList()) =
        ObtainmentInfo(
            pokemon = pokemon,
            method = "shrine",
            description = "",
            descriptionKey = descriptionKey,
            items = listOf(item),
            block = block,
            noteKeys = noteKeys,
            source = "bundled"
        )

    private fun resurrection(pokemon: String, item: String, block: String?, descriptionKey: String,
                             noteKeys: List<String> = emptyList()) =
        ObtainmentInfo(
            pokemon = pokemon,
            method = "resurrection",
            description = "",
            descriptionKey = descriptionKey,
            items = listOf(item),
            block = block,
            noteKeys = noteKeys,
            source = "bundled"
        )

    private fun transformation(pokemon: String, item: String, block: String?, descriptionKey: String,
                               form: String = "", noteKeys: List<String> = emptyList()) =
        ObtainmentInfo(
            pokemon = pokemon,
            formAspects = form,
            method = "transformation",
            description = "",
            descriptionKey = descriptionKey,
            items = listOf(item),
            block = block,
            noteKeys = noteKeys,
            source = "bundled"
        )
}
