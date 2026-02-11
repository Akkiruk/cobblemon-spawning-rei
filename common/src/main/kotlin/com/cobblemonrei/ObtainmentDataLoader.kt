package com.cobblemonrei

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path

object ObtainmentDataLoader {

    private const val DATA_PATH = "special_obtainment"

    fun loadFromAllSources(modRoots: List<Path>, extraDatapacksDir: Path? = null): Map<String, List<ObtainmentInfo>> {
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

        if (extraDatapacksDir != null && Files.exists(extraDatapacksDir) && Files.isDirectory(extraDatapacksDir)) {
            totalEntries += scanDatapacksDir(extraDatapacksDir, result)
        }

        if (com.cobblemonrei.config.CobblemonSpawningConfig.get().localDatapackScan) {
            val datapacksDir = try {
                com.cobblemonrei.platform.PlatformHelper.getGameDir().resolve("datapacks")
            } catch (_: Exception) { null }

            if (datapacksDir != null && Files.exists(datapacksDir) && Files.isDirectory(datapacksDir)) {
                totalEntries += scanDatapacksDir(datapacksDir, result)
            }
        }

        // Merge bundled defaults for species that have NO obtainment data from any source
        val bundled = loadBundledDefaults()
        for ((species, infos) in bundled) {
            if (!result.containsKey(species)) {
                result[species] = infos.toMutableList()
                totalEntries += infos.size
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
        for (entry in BUNDLED_OBTAINMENT) {
            result.getOrPut(entry.pokemon) { mutableListOf() }.add(entry)
        }
        return result
    }

    @Suppress("LongMethod")
    private val BUNDLED_OBTAINMENT: List<ObtainmentInfo> by lazy {
        listOf(
            // LumyMon Bird Trio
            altar("articuno", "lumymon:glacier_feather", "lumymon:articuno_altar",
                "Use Glacier Feather on Articuno Altar"),
            altar("zapdos", "lumymon:thunder_feather", "lumymon:zapdos_altar",
                "Use Thunder Feather on Zapdos Altar"),
            altar("moltres", "lumymon:ember_feather", "lumymon:moltres_altar",
                "Use Ember Feather on Moltres Altar"),

            // LumyMon Tower Duo
            altar("lugia", "lumymon:silver_wing", "lumymon:lugia_altar",
                "Use Silver Wing on Lugia Altar"),
            altar("hooh", "lumymon:rainbow_wing", "lumymon:hooh_altar",
                "Use Rainbow Wing on Ho-Oh Altar"),

            // LumyMon Mythicals
            shrine("celebi", "lumymon:gs_ball", "lumymon:ilex_shrine",
                "Use GS Ball on Ilex Shrine"),
            shrine("mew", "lumymon:melodic_tape_vol_1", "lumymon:mew_shrine",
                "Use Melodic Tape Vol. 1 on Mew Shrine"),
            shrine("jirachi", "lumymon:meteorite_crystal", "lumymon:jirachi_shrine",
                "Use Meteorite Crystal on Jirachi Shrine"),

            // LumyMon Regi Trio
            altar("regirock", "lumymon:pebble_relic", "lumymon:regirock_altar",
                "Use Pebble Relic on Regirock Altar"),
            altar("regice", "lumymon:cryo_relic", "lumymon:regice_altar",
                "Use Cryo Relic on Regice Altar"),
            altar("registeel", "lumymon:metal_relic", "lumymon:registeel_altar",
                "Use Metal Relic on Registeel Altar"),

            // LumyMon Regi Extras
            altar("regieleki", "lumymon:metal_relic", "lumymon:regieleki_altar",
                "Use Metal Relic on Regieleki Altar",
                notes = listOf("Requires Regirock, Regice, Registeel nearby")),
            altar("regidrago", "lumymon:metal_relic", "lumymon:regidrago_altar",
                "Use Metal Relic on Regidrago Altar",
                notes = listOf("Requires Regirock, Regice, Registeel nearby")),

            // LumyMon Eon Duo
            altar("latias", "lumymon:ruby_dew", "lumymon:latias_altar",
                "Use Ruby Dew on Latias Altar"),
            altar("latios", "lumymon:sapphire_dew", "lumymon:latios_altar",
                "Use Sapphire Dew on Latios Altar"),

            // LumyMon Weather Trio
            altar("kyogre", "lumymon:ocean_core", "lumymon:kyogre_altar",
                "Use Ocean Core on Kyogre Altar"),
            altar("groudon", "lumymon:earth_core", "lumymon:groudon_altar",
                "Use Earth Core on Groudon Altar"),
            altar("rayquaza", "lumymon:sky_core", "lumymon:rayquaza_altar",
                "Use Sky Core on Rayquaza Altar"),

            // LumyMon Deoxys
            shrine("deoxys", "lumymon:meteorite_crystal", "lumymon:deoxys_shrine",
                "Use Meteorite Crystal on Deoxys Shrine"),

            // LumyMon Calyrex line
            altar("calyrex", "lumymon:calyrex_crown", "lumymon:calyrex_statue",
                "Use Calyrex Crown on Calyrex Statue"),
            altar("glastrier", "lumymon:iceroot_carrot", "lumymon:summon_trigger",
                "Plant Iceroot Carrot near Summon Anchor",
                notes = listOf("Requires Calyrex nearby")),
            altar("spectrier", "lumymon:shaderoot_carrot", "lumymon:summon_trigger",
                "Plant Shaderoot Carrot near Summon Anchor",
                notes = listOf("Requires Calyrex nearby")),

            // LumyMon Resurrection Machine
            resurrection("mewtwo", "lumymon:ancient_dna", "cobblemon:resurrection_machine",
                "Insert Ancient DNA + Cloning Catalyst into Resurrection Machine"),
            resurrection("type_null", "lumymon:fossilized_helmet", "cobblemon:resurrection_machine",
                "Insert Fossilized Helmet into Resurrection Machine"),

            // LumyMon Shadow Lugia
            transformation("lugia", "lumymon:shadow_soul_stone", null,
                "Use Shadow Soul Stone on Lugia",
                form = "shadow",
                notes = listOf("Transforms Lugia into Shadow Lugia"))
        )
    }

    private fun altar(pokemon: String, item: String, block: String, description: String,
                      notes: List<String> = emptyList()) =
        ObtainmentInfo(pokemon, "", "altar", description, listOf(item), block, notes = notes, source = "bundled")

    private fun shrine(pokemon: String, item: String, block: String, description: String,
                       notes: List<String> = emptyList()) =
        ObtainmentInfo(pokemon, "", "shrine", description, listOf(item), block, notes = notes, source = "bundled")

    private fun resurrection(pokemon: String, item: String, block: String?, description: String,
                             notes: List<String> = emptyList()) =
        ObtainmentInfo(pokemon, "", "resurrection", description, listOf(item), block, notes = notes, source = "bundled")

    private fun transformation(pokemon: String, item: String, block: String?, description: String,
                               form: String = "", notes: List<String> = emptyList()) =
        ObtainmentInfo(pokemon, form, "transformation", description, listOf(item), block, notes = notes, source = "bundled")
}
