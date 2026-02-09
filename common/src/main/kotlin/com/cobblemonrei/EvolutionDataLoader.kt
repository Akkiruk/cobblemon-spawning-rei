package com.cobblemonrei

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.evolution.ContextEvolution
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution
import com.cobblemon.mod.common.api.pokemon.requirement.Requirement
import net.minecraft.resources.ResourceLocation

object EvolutionDataLoader {

    fun loadFromRuntime(): Map<String, List<EvolutionInfo>> {
        val implemented = try {
            PokemonSpecies.implemented.toList()
        } catch (_: Exception) {
            return emptyMap()
        }
        if (implemented.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, MutableList<EvolutionInfo>>()
        var baseEvoCount = 0
        var formEvoCount = 0

        for (species in implemented) {
            val baseName = species.name.lowercase()

            // Base species evolutions
            for (evo in species.evolutions) {
                try {
                    val info = parseEvolution(baseName, null, evo)
                    if (info != null) {
                        result.getOrPut(baseName) { mutableListOf() }.add(info)
                        baseEvoCount++
                    }
                } catch (e: Exception) {
                    DebugLog.once("evo-parse-$baseName") { "Failed to parse base evolution: ${e.message}" }
                }
            }

            // Form-specific evolutions
            for (form in species.forms) {
                if (form.evolutions.isEmpty()) continue
                val formAspects = form.aspects.toSet()
                val formKey = buildFormKey(baseName, formAspects)

                for (evo in form.evolutions) {
                    try {
                        val info = parseEvolution(baseName, formAspects, evo)
                        if (info != null) {
                            result.getOrPut(formKey) { mutableListOf() }.add(info)
                            formEvoCount++
                        }
                    } catch (e: Exception) {
                        DebugLog.once("evo-parse-$formKey") { "Failed to parse form evolution: ${e.message}" }
                    }
                }
            }
        }

        DebugLog.info("Parsed $baseEvoCount base + $formEvoCount form evolutions")
        return result
    }

    private fun buildFormKey(baseName: String, aspects: Set<String>): String {
        return if (aspects.isEmpty()) baseName
        else "$baseName ${aspects.sorted().joinToString(" ")}"
    }

    private fun parseEvolution(fromSpecies: String, fromAspects: Set<String>?, evo: Evolution): EvolutionInfo? {
        val id = evo.id
        val resultSpecies = evo.result.species?.lowercase() ?: return null
        val resultAspects = parseResultAspects(evo.result.aspects)

        val variant = evo.javaClass.simpleName
            .replace("Evolution", "")
            .replace(Regex("([A-Z])"), "_$1")
            .lowercase()
            .trimStart('_')
            .ifEmpty { "level_up" }

        val requiredContext = extractRequiredContext(evo)

        val requirements = mutableListOf<EvolutionRequirement>()
        for (req in evo.requirements) {
            try {
                val parsed = parseRequirement(req)
                requirements.add(parsed)
            } catch (e: Exception) {
                val reqVariant = req.javaClass.simpleName.replace("Requirement", "").lowercase()
                requirements.add(EvolutionRequirement(reqVariant, emptyMap()))
            }
        }

        return EvolutionInfo(
            id = id,
            fromSpecies = fromSpecies,
            fromAspects = fromAspects ?: emptySet(),
            toSpecies = resultSpecies,
            toAspects = resultAspects,
            variant = variant,
            requirements = requirements,
            requiredContext = requiredContext,
            consumeHeldItem = evo.consumeHeldItem
        )
    }

    private fun extractRequiredContext(evo: Evolution): String? {
        if (evo !is ContextEvolution<*, *>) return null
        return try {
            val field = evo.javaClass.getDeclaredField("requiredContext")
            field.isAccessible = true
            val value = field.get(evo)
            when (value) {
                is ResourceLocation -> value.toString()
                else -> value?.toString()
            }
        } catch (_: Exception) {
            // Try superclass
            try {
                val field = evo.javaClass.superclass?.getDeclaredField("requiredContext")
                field?.isAccessible = true
                field?.get(evo)?.toString()
            } catch (_: Exception) { null }
        }
    }

    private fun parseResultAspects(aspects: Set<String>): Set<String> {
        return aspects.map { it.lowercase() }.toSet()
    }

    private fun parseRequirement(req: Requirement): EvolutionRequirement {
        val className = req.javaClass.simpleName
        val variant = className.replace("Requirement", "")
            .replace(Regex("([A-Z])"), "_$1")
            .lowercase()
            .trimStart('_')

        val data = mutableMapOf<String, Any>()

        // Use reflection to extract fields based on requirement type
        when {
            className.contains("Level") -> {
                extractField(req, "minLevel")?.let { data["minLevel"] = it }
            }
            className.contains("Friendship") -> {
                extractField(req, "amount")?.let { data["amount"] = it }
            }
            className.contains("TimeRange") -> {
                // Try multiple approaches to get TimeRange value
                val timeRange = extractField(req, "range") 
                    ?: extractField(req, "timeRange")
                    ?: extractField(req, "time")
                
                val rangeName = when (timeRange) {
                    null -> null
                    else -> {
                        // Try to extract readable name from TimeRange object
                        extractField(timeRange, "name")?.toString()?.takeIf { !it.contains("@") }
                            ?: extractField(timeRange, "displayName")?.toString()?.takeIf { !it.contains("@") }
                            ?: run {
                                // Parse from toString() or class name - look for DAY, NIGHT, etc.
                                val str = timeRange.toString().uppercase()
                                when {
                                    str.contains("DAY") && !str.contains("NIGHT") -> "day"
                                    str.contains("NIGHT") -> "night"
                                    str.contains("DUSK") -> "dusk"
                                    str.contains("DAWN") -> "dawn"
                                    str.contains("TWILIGHT") -> "twilight"
                                    str.contains("MIDNIGHT") -> "midnight"
                                    else -> {
                                        // Last resort: extract simple class name
                                        timeRange.javaClass.simpleName.lowercase()
                                            .replace("timerange", "")
                                            .ifBlank { "time" }
                                    }
                                }
                            }
                    }
                }
                rangeName?.let { data["range"] = it }
            }
            className.contains("HeldItem") -> {
                extractField(req, "itemCondition")?.let { data["itemCondition"] = it.toString() }
            }
            className.contains("MoveType") -> {
                extractField(req, "type")?.let { data["type"] = extractReadableValue(it) ?: "unknown" }
            }
            className.contains("MoveSet") -> {
                extractField(req, "move")?.let { data["move"] = extractReadableValue(it) ?: "unknown" }
            }
            className.contains("Biome") -> {
                extractField(req, "biomeCondition")?.let { data["biomeCondition"] = it.toString() }
                extractField(req, "biomeAnticondition")?.let { data["biomeAnticondition"] = it.toString() }
            }
            className.contains("Structure") -> {
                extractField(req, "structureCondition")?.let { data["structureCondition"] = it.toString() }
                extractField(req, "structureAnticondition")?.let { data["structureAnticondition"] = it.toString() }
            }
            className.contains("StatCompare") -> {
                extractField(req, "highStat")?.let { data["highStat"] = extractReadableValue(it) ?: "stat" }
                extractField(req, "lowStat")?.let { data["lowStat"] = extractReadableValue(it) ?: "stat" }
            }
            className.contains("StatEqual") -> {
                extractField(req, "statOne")?.let { data["statOne"] = extractReadableValue(it) ?: "stat" }
                extractField(req, "statTwo")?.let { data["statTwo"] = extractReadableValue(it) ?: "stat" }
            }
            className.contains("PokemonProperties") -> {
                extractField(req, "target")?.let { data["target"] = it.toString() }
            }
            className.contains("PropertyRange") -> {
                extractField(req, "range")?.let { data["range"] = it.toString() }
                extractField(req, "feature")?.let { data["feature"] = it.toString() }
            }
            className.contains("BlocksTraveled") -> {
                extractField(req, "amount")?.let { data["amount"] = it }
            }
            className.contains("UseMove") -> {
                extractField(req, "move")?.let { data["move"] = it.toString() }
                extractField(req, "amount")?.let { data["amount"] = it }
            }
            className.contains("Defeat") -> {
                extractField(req, "target")?.let { data["target"] = it.toString() }
                extractField(req, "amount")?.let { data["amount"] = it }
            }
            className.contains("Recoil") -> {
                extractField(req, "amount")?.let { data["amount"] = it }
            }
            className.contains("DamageTaken") -> {
                extractField(req, "amount")?.let { data["amount"] = it }
            }
            className.contains("BattleCriticalHits") -> {
                extractField(req, "amount")?.let { data["amount"] = it }
            }
            className.contains("PartyMember") -> {
                extractField(req, "target")?.let { data["target"] = it.toString() }
                extractField(req, "contains")?.let { data["contains"] = it }
            }
            className.contains("MoonPhase") -> {
                extractField(req, "moonPhase")?.let { data["moonPhase"] = extractReadableValue(it) ?: "moon" }
            }
            className.contains("Weather") -> {
                extractField(req, "isRaining")?.let { data["isRaining"] = it }
            }
            className.contains("Advancement") -> {
                extractField(req, "requiredAdvancement")?.let { data["requiredAdvancement"] = it.toString() }
            }
            className.contains("World") -> {
                extractField(req, "identifier")?.let { data["identifier"] = it.toString() }
            }
            className.contains("AttackDefenceRatio") -> {
                extractField(req, "ratio")?.let { data["ratio"] = extractReadableValue(it) ?: "ratio" }
            }
            className.contains("OwnerHoldsItem") -> {
                extractField(req, "itemCondition")?.let { data["itemCondition"] = it.toString() }
            }
            className.contains("Gender") -> {
                extractField(req, "gender")?.let { data["gender"] = extractReadableValue(it) ?: "unknown" }
            }
            className.contains("Nature") -> {
                extractField(req, "nature")?.let { data["nature"] = extractReadableValue(it) ?: "unknown" }
            }
            className.contains("MaxPokemonLevel") -> {
                extractField(req, "maxLevel")?.let { data["maxLevel"] = it }
            }
            className.contains("WalkingSteps") || className.contains("DamageDealt") -> {
                extractField(req, "amount")?.let { data["amount"] = it }
            }
            className.contains("Status") && !className.contains("Stat") -> {
                extractField(req, "status")?.let { data["status"] = extractReadableValue(it) ?: "status" }
            }
        }

        return EvolutionRequirement(variant, data)
    }

    private fun extractField(obj: Any, fieldName: String): Any? {
        return try {
            val field = obj.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.get(obj)
        } catch (_: NoSuchFieldException) {
            // Try parent class
            try {
                val field = obj.javaClass.superclass?.getDeclaredField(fieldName)
                field?.isAccessible = true
                field?.get(obj)
            } catch (_: Exception) { null }
        } catch (_: Exception) { null }
    }

    // Extract a readable string from an object that might be an enum, ResourceLocation, or complex object
    private fun extractReadableValue(obj: Any?): String? {
        if (obj == null) return null
        
        // If it's a simple type, just use toString
        if (obj is String) return obj.takeIf { !it.contains("@") }
        if (obj is Number) return obj.toString()
        if (obj is Boolean) return obj.toString()
        
        // Check if it's an enum
        if (obj.javaClass.isEnum) {
            return (obj as Enum<*>).name.lowercase()
        }
        
        // Try to get name field (common for named objects)
        val name = extractField(obj, "name")
        if (name is String && !name.contains("@")) return name
        
        // Try path/namespace for ResourceLocation-like objects
        val path = extractField(obj, "path")
        if (path is String && !path.contains("@")) return path
        
        // Try displayName
        val displayName = extractField(obj, "displayName")
        if (displayName != null) {
            val str = displayName.toString()
            if (!str.contains("@")) return str
        }
        
        // Try id
        val id = extractField(obj, "id")
        if (id is String && !id.contains("@")) return id
        
        // Last resort: try toString but filter out garbage
        val str = obj.toString()
        return if (str.contains("@") || str.contains(".") && str.length > 50) {
            // Extract class simple name as fallback
            obj.javaClass.simpleName.lowercase().replace("_", " ")
        } else {
            str
        }
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

    fun loadSpeciesBasicInfoFromRuntime(): Map<String, SpeciesBasicInfo> {
        val implemented = try {
            PokemonSpecies.implemented.toList()
        } catch (_: Exception) {
            return emptyMap()
        }
        if (implemented.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, SpeciesBasicInfo>()

        for (species in implemented) {
            try {
                val name = species.name.lowercase()
                result[name] = SpeciesBasicInfo(
                    name = name,
                    nationalDexNumber = species.nationalPokedexNumber,
                    primaryType = species.primaryType.name.lowercase(),
                    secondaryType = species.secondaryType?.name?.lowercase(),
                    catchRate = species.catchRate,
                    weight = species.weight,
                    height = species.height
                )
            } catch (e: Exception) {
                DebugLog.once("species-info-${species.name}") { "Failed to load species info for ${species.name}: ${e.message}" }
            }
        }

        DebugLog.info("Loaded ${result.size} species from runtime API")
        return result
    }
}
