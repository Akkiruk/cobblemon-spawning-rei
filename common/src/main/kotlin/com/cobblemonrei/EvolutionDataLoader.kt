package com.cobblemonrei

import com.google.gson.JsonParser
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path

object EvolutionDataLoader {

    fun loadFromCobblemonJar(): Map<String, List<EvolutionInfo>> {
        val result = mutableMapOf<String, MutableList<EvolutionInfo>>()
        val speciesPath = SpawnDataLoader.findCobblemonDataPath("species") ?: run {
            CobblemonSpawningMod.LOGGER.warn("Could not find Cobblemon species data path")
            return emptyMap()
        }

        var speciesCount = 0
        var evoCount = 0

        Files.walk(speciesPath)
            .filter { it.toString().endsWith(".json") }
            .forEach { file ->
                try {
                    val json = JsonParser.parseReader(InputStreamReader(Files.newInputStream(file))).asJsonObject
                    val name = json.get("name")?.asString?.lowercase() ?: return@forEach
                    val evolutions = json.getAsJsonArray("evolutions") ?: return@forEach
                    if (evolutions.size() == 0) return@forEach

                    speciesCount++

                    for (evoElement in evolutions) {
                        val evo = evoElement.asJsonObject
                        val info = parseEvolution(name, evo)
                        if (info != null) {
                            result.getOrPut(name) { mutableListOf() }.add(info)
                            evoCount++
                        }
                    }
                } catch (e: Exception) {
                    CobblemonSpawningMod.LOGGER.debug("Failed to parse species file ${file.fileName}: ${e.message}")
                }
            }

        CobblemonSpawningMod.LOGGER.info("Parsed $evoCount evolutions from $speciesCount species")
        return result
    }

    private fun parseEvolution(fromSpecies: String, evo: com.google.gson.JsonObject): EvolutionInfo? {
        val id = evo.get("id")?.asString ?: return null
        val resultStr = evo.get("result")?.asString ?: return null
        val toSpecies = resultStr.split(" ").first().lowercase()
        val variant = evo.get("variant")?.asString ?: "level_up"
        val requiredContext = evo.get("requiredContext")?.asString
        val consumeHeldItem = evo.get("consumeHeldItem")?.asBoolean ?: false

        val requirements = mutableListOf<EvolutionRequirement>()
        val reqArray = evo.getAsJsonArray("requirements")
        if (reqArray != null) {
            for (reqElement in reqArray) {
                val req = reqElement.asJsonObject
                val reqVariant = req.get("variant")?.asString ?: continue
                val data = mutableMapOf<String, Any>()

                for (entry in req.entrySet()) {
                    if (entry.key == "variant") continue
                    val value = entry.value
                    when {
                        value.isJsonPrimitive -> {
                            val prim = value.asJsonPrimitive
                            when {
                                prim.isNumber -> data[entry.key] = prim.asNumber
                                prim.isBoolean -> data[entry.key] = prim.asBoolean
                                else -> data[entry.key] = prim.asString
                            }
                        }
                        else -> data[entry.key] = value.toString()
                    }
                }

                requirements.add(EvolutionRequirement(reqVariant, data))
            }
        }

        return EvolutionInfo(
            id = id,
            fromSpecies = fromSpecies,
            toSpecies = toSpecies,
            variant = variant,
            requirements = requirements,
            requiredContext = requiredContext,
            consumeHeldItem = consumeHeldItem
        )
    }

    data class SpeciesBasicInfo(
        val name: String,
        val nationalDexNumber: Int,
        val primaryType: String,
        val secondaryType: String?,
        val catchRate: Int,
        val weight: Float,
        val height: Float
    )

    fun loadSpeciesBasicInfo(): Map<String, SpeciesBasicInfo> {
        val result = mutableMapOf<String, SpeciesBasicInfo>()
        val speciesPath = SpawnDataLoader.findCobblemonDataPath("species") ?: return emptyMap()

        Files.walk(speciesPath)
            .filter { it.toString().endsWith(".json") }
            .forEach { file ->
                try {
                    val json = JsonParser.parseReader(InputStreamReader(Files.newInputStream(file))).asJsonObject
                    val name = json.get("name")?.asString?.lowercase() ?: return@forEach
                    val dexNum = json.get("nationalPokedexNumber")?.asInt ?: 0
                    val primaryType = json.get("primaryType")?.asString ?: "normal"
                    val secondaryType = json.get("secondaryType")?.asString
                    val catchRate = json.get("catchRate")?.asInt ?: 45
                    val weight = json.get("weight")?.asFloat ?: 0f
                    val height = json.get("height")?.asFloat ?: 0f

                    result[name] = SpeciesBasicInfo(
                        name = name,
                        nationalDexNumber = dexNum,
                        primaryType = primaryType,
                        secondaryType = secondaryType,
                        catchRate = catchRate,
                        weight = weight,
                        height = height
                    )
                } catch (_: Exception) { }
            }

        return result
    }
}
