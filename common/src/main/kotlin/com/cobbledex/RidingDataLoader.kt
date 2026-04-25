package com.cobbledex

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.riding.stats.RidingStat

object RidingDataLoader {

    fun loadFromRuntime(): Map<String, RidingInfo> {
        val implemented = try {
            PokemonSpecies.implemented.toList()
        } catch (e: Exception) {
            DebugLog.warnOnce("riding-species-load") { "Failed to access PokemonSpecies.implemented: ${e.message}" }
            return emptyMap()
        }
        if (implemented.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, RidingInfo>()

        for (species in implemented) {
            val baseName = SpeciesNameNormalizer.normalize(species.name)
            val baseForm = try { species.standardForm } catch (_: Exception) { null }
            val forms = try { species.forms } catch (_: Exception) { emptyList() }

            baseForm?.let { form ->
                buildRidingInfo(baseName, form)?.let { result[baseName] = it }
            }

            for (form in forms) {
                if (!shouldIncludeForm(form, baseForm)) continue
                val formKey = buildFormEntryKey(baseName, form)
                buildRidingInfo(formKey, form)?.let { result[formKey] = it }
            }
        }

        DebugLog.info("Loaded riding data for ${result.size} species (${result.values.sumOf { it.mounts.size }} mount entries)")
        return result
    }

    /** Extracts riding style name from behaviour key path like "air/bird" -> "bird" */
    private fun extractStyleName(path: String): String? {
        val afterSlash = path.substringAfterLast('/', "")
        return afterSlash.ifBlank { null }
    }

    private fun buildRidingInfo(
        speciesKey: String,
        form: com.cobblemon.mod.common.pokemon.FormData
    ): RidingInfo? {
        val riding = try { form.riding } catch (_: Exception) { return null }
        val behaviours = riding.behaviours ?: return null
        if (behaviours.isEmpty()) return null

        val seats = riding.seats.size.coerceAtLeast(1)
        val allMountTypes = behaviours.keys.map { it.name }
        val ridingStyles = behaviours.values.mapNotNull { extractStyleName(it.key.path) }.distinct()

        val mounts = behaviours.map { (style, behaviour) ->
            val stats = behaviour.stats
            RidingMount(
                mountType = style.name,
                ridingStyle = extractStyleName(behaviour.key.path) ?: style.name.lowercase(),
                speedMin = stats[RidingStat.SPEED]?.first ?: 0,
                speedMax = stats[RidingStat.SPEED]?.last ?: 0,
                accelMin = stats[RidingStat.ACCELERATION]?.first ?: 0,
                accelMax = stats[RidingStat.ACCELERATION]?.last ?: 0,
                skillMin = stats[RidingStat.SKILL]?.first ?: 0,
                skillMax = stats[RidingStat.SKILL]?.last ?: 0,
                jumpMin = stats[RidingStat.JUMP]?.first ?: 0,
                jumpMax = stats[RidingStat.JUMP]?.last ?: 0,
                staminaMin = stats[RidingStat.STAMINA]?.first ?: 0,
                staminaMax = stats[RidingStat.STAMINA]?.last ?: 0,
            )
        }

        return RidingInfo(
            pokemon = speciesKey,
            allMountTypes = allMountTypes,
            ridingStyles = ridingStyles,
            seats = seats,
            mounts = mounts,
        )
    }

    private fun shouldIncludeForm(
        form: com.cobblemon.mod.common.pokemon.FormData,
        baseForm: com.cobblemon.mod.common.pokemon.FormData?
    ): Boolean {
        if (baseForm != null && form == baseForm) return false
        if (form.name.isBlank()) return false

        val baseRiding = try { baseForm?.riding } catch (_: Exception) { null }
        val formRiding = try { form.riding } catch (_: Exception) { null }
        if (baseRiding == null) return formRiding != null
        if (formRiding == null) return false

        val baseBehaviours = baseRiding.behaviours
        val formBehaviours = formRiding.behaviours
        if (formBehaviours.isNullOrEmpty()) return false

        return (
            baseBehaviours == null ||
            formBehaviours != baseBehaviours ||
                formRiding.seats != baseRiding.seats ||
                form.aspects.toSet() != (baseForm?.aspects?.toSet() ?: emptySet<String>()) ||
                form.labels.toSet() != (baseForm?.labels?.toSet() ?: emptySet<String>()) ||
                form.primaryType != baseForm?.primaryType ||
                form.secondaryType != baseForm?.secondaryType
            )
    }

    private fun buildFormEntryKey(baseName: String, form: com.cobblemon.mod.common.pokemon.FormData): String {
        val regionalLabel = form.labels.firstOrNull {
            it == "alolan_form" || it == "galarian_form" || it == "hisuian_form" || it == "paldean_form"
        }
        if (regionalLabel != null) {
            val suffix = when (regionalLabel) {
                "alolan_form" -> "alolan"
                "galarian_form" -> "galarian"
                "hisuian_form" -> "hisuian"
                else -> "paldean"
            }
            return "${SpeciesNameNormalizer.normalize(baseName)}$suffix"
        }

        val normalizedFormName = form.name.lowercase().replace(Regex("[^a-z0-9]"), "")
        return "${SpeciesNameNormalizer.normalize(baseName)}_$normalizedFormName"
    }
}
