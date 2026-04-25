package com.cobbledex

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.cobbledex.platform.PlatformHelper
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

object ObtainmentDataLoader {

    private const val DATA_PATH = "special_obtainment"

    private data class ObtainmentEntryKey(
        val identityKey: String,
        val method: String,
        val description: String,
        val descriptionKey: String?,
        val items: List<String>,
        val block: String?,
        val structure: String?,
        val dimension: String?,
        val notes: List<String>,
        val noteKeys: List<String>,
        val source: String,
    )

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
        val seen = linkedSetOf<ObtainmentEntryKey>()

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
                                    parseObtainmentFile(file, result, seen, "mod")
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
            scanDatapacksDir(datapacksDir, result, seen)
        }

        // Merge bundled defaults only if LumyMon is actually installed
        if (isModLoaded("lumymon")) {
            val bundled = loadBundledDefaults()
            for ((species, infos) in bundled) {
                for (info in infos) {
                    addEntry(info, result, seen)
                }
            }
        }

        val totalEntries = result.values.sumOf { it.size }
        if (totalEntries > 0) {
            DebugLog.info("Loaded $totalEntries special obtainment entries for ${result.size} species")
        }
        return result
    }

    private fun scanDatapacksDir(
        datapacksDir: Path,
        result: MutableMap<String, MutableList<ObtainmentInfo>>,
        seen: MutableSet<ObtainmentEntryKey>
    ) {
        Files.list(datapacksDir).use { packDirs ->
            packDirs.filter { Files.isDirectory(it) }.forEach { pack ->
                val dataDir = pack.resolve("data")
                if (Files.exists(dataDir)) {
                    Files.list(dataDir).use { namespaces ->
                        namespaces.filter { Files.isDirectory(it) }.forEach { namespace ->
                            val obtainDir = namespace.resolve(DATA_PATH)
                            if (Files.exists(obtainDir) && Files.isDirectory(obtainDir)) {
                                Files.walk(obtainDir, 10).use { files ->
                                    files.filter { it.toString().endsWith(".json") }.forEach { file ->
                                        parseObtainmentFile(file, result, seen, "datapack")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Files.list(datapacksDir).use { zipPacks ->
            zipPacks.filter { it.toString().endsWith(".zip") && Files.isRegularFile(it) }.forEach { zipPath ->
                scanZipDatapack(zipPath, result, seen)
            }
        }
    }

    private fun scanZipDatapack(
        zipPath: Path,
        result: MutableMap<String, MutableList<ObtainmentInfo>>,
        seen: MutableSet<ObtainmentEntryKey>
    ) {
        try {
            ZipFile(zipPath.toFile()).use { zip ->
                val pattern = Regex("^data/([^/]+)/${DATA_PATH}/.+\\.json$")
                for (entry in zip.entries()) {
                    if (entry.isDirectory) continue
                    if (!pattern.matches(entry.name)) continue
                    try {
                        val json = zip.getInputStream(entry).use { stream ->
                            InputStreamReader(stream).use { reader ->
                                JsonParser.parseReader(reader)
                            }
                        }
                        parseJsonElement(json, result, seen, "datapack")
                    } catch (e: Exception) {
                        DebugLog.once("obtainment-zip-${zipPath.fileName}-${entry.name}") { "Failed to parse zipped obtainment entry: ${e.message}" }
                    }
                }
            }
        } catch (e: Exception) {
            DebugLog.once("obtainment-zip-root-${zipPath.fileName}") { "Failed to scan zipped datapack: ${e.message}" }
        }
    }

    private fun parseObtainmentFile(
        file: Path,
        result: MutableMap<String, MutableList<ObtainmentInfo>>,
        seen: MutableSet<ObtainmentEntryKey>,
        source: String
    ) {
        try {
            val json = Files.newInputStream(file).use { stream ->
                InputStreamReader(stream).use { reader ->
                    JsonParser.parseReader(reader)
                }
            }
            parseJsonElement(json, result, seen, source)
        } catch (e: Exception) {
            DebugLog.once("obtainment-parse-${file.fileName}") { "Failed to parse obtainment file: ${e.message}" }
        }
    }

    private fun parseJsonElement(
        json: JsonElement,
        result: MutableMap<String, MutableList<ObtainmentInfo>>,
        seen: MutableSet<ObtainmentEntryKey>,
        source: String
    ) {
        if (json.isJsonArray) {
            json.asJsonArray.forEachIndexed { index, element ->
                parseAndAddEntry(element, result, seen, source, "array[$index]")
            }
        } else if (json.isJsonObject) {
            val obj = json.asJsonObject
            if (obj.has("entries")) {
                obj.getAsJsonArray("entries").forEachIndexed { index, element ->
                    parseAndAddEntry(element, result, seen, source, "entries[$index]")
                }
            } else {
                addEntry(parseEntry(obj, source), result, seen)
            }
        }
    }

    private fun parseAndAddEntry(
        element: JsonElement,
        result: MutableMap<String, MutableList<ObtainmentInfo>>,
        seen: MutableSet<ObtainmentEntryKey>,
        source: String,
        path: String
    ) {
        try {
            addEntry(parseEntry(element.asJsonObject, source), result, seen)
        } catch (e: Exception) {
            DebugLog.once("obtainment-entry-$source-$path") {
                "Failed to parse obtainment entry at $path: ${e.message}"
            }
        }
    }

    private fun addEntry(
        info: ObtainmentInfo?,
        result: MutableMap<String, MutableList<ObtainmentInfo>>,
        seen: MutableSet<ObtainmentEntryKey>
    ) {
        if (info == null) return
        val key = ObtainmentEntryKey(
            identityKey = pokemonIdentityKey(info.pokemon, parseAspectString(info.formAspects)),
            method = info.method.lowercase(),
            description = info.description.trim(),
            descriptionKey = info.descriptionKey?.trim(),
            items = info.items.map { SpeciesNameNormalizer.normalize(it) },
            block = info.block?.trim()?.lowercase(),
            structure = info.structure?.trim()?.lowercase(),
            dimension = info.dimension?.trim()?.lowercase(),
            notes = info.notes.map(String::trim),
            noteKeys = info.noteKeys.map(String::trim),
            source = info.source,
        )
        if (!seen.add(key)) return
        result.getOrPut(info.pokemon) { mutableListOf() }.add(info)
    }

    private fun parseEntry(obj: JsonObject, source: String): ObtainmentInfo? {
        val pokemon = SpeciesNameNormalizer.normalize(obj.get("pokemon")?.asString ?: return null)
        val method = obj.get("method")?.asString ?: return null
        val description = obj.get("description")?.asString ?: ""
        val descriptionKey = obj.get("description_key")?.asString
        val items = obj.getAsJsonArray("items")?.map { it.asString } ?: emptyList()
        val block = obj.get("block")?.asString
        val structure = obj.get("structure")?.asString
        val dimension = obj.get("dimension")?.asString
        val notes = obj.getAsJsonArray("notes")?.map { it.asString } ?: emptyList()
        val noteKeys = obj.getAsJsonArray("note_keys")?.map { it.asString } ?: emptyList()
        val formAspects = parseAspectString(obj.get("form")?.asString ?: "").joinToString(" ")

        return ObtainmentInfo(
            pokemon = pokemon,
            formAspects = formAspects,
            method = method,
            description = description,
            descriptionKey = descriptionKey,
            items = items,
            block = block,
            structure = structure,
            dimension = dimension,
            notes = notes,
            noteKeys = noteKeys,
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
