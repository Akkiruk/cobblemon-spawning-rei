package com.cobbledex

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Diagnostic logic for analyzing Pokémon data coverage.
 * Platform-specific command registration calls these methods.
 */
object DiagnosticService {
    
    fun interface MessageSender {
        fun send(message: String)
    }
    
    fun showStats(sender: MessageSender): Int {
        val index = SpawnDataIndex
        
        if (!index.hasData()) {
            sender.send(tr("cobbledex-rei-emi-jei.cmd.no_data"))
            return 0
        }
        
        val allSpecies = index.allSpeciesNames
        val withSpawns = index.spawnsBySpecies.keys
        val withEvolutions = index.evolutionsBySpecies.keys
        val withObtainment = index.obtainmentBySpecies.keys
        val withDex = index.speciesInfo.count { it.value.nationalDexNumber > 0 }
        
        sender.send(tr("cobbledex-rei-emi-jei.cmd.stats_header"))
        sender.send(tr("cobbledex-rei-emi-jei.cmd.total_species", allSpecies.size))
        sender.send(tr("cobbledex-rei-emi-jei.cmd.with_dex", withDex))
        sender.send(tr("cobbledex-rei-emi-jei.cmd.with_spawns", withSpawns.size, allSpecies.size - withSpawns.size))
        sender.send(tr("cobbledex-rei-emi-jei.cmd.with_evolutions", withEvolutions.size))
        sender.send(tr("cobbledex-rei-emi-jei.cmd.with_obtainment", withObtainment.size))
        sender.send(tr("cobbledex-rei-emi-jei.cmd.load_state", index.loadState.name))

        // Where each category's data actually came from, and whether it can be trusted for the
        // world you're in. Anything marked local-only is read from this client's files while
        // connected to a server that may not share them.
        sender.send("§7Sources §8(§a✔§8 verified / §e▲§8 local-only / §c✖§8 missing)")
        sender.send(DataAvailability.describe("Spawns", index.spawnSourceTier))
        sender.send(DataAvailability.describe("Evolutions", index.evolutionSourceTier))
        sender.send(DataAvailability.describe("Species info", index.speciesInfoSourceTier))
        sender.send(DataAvailability.describe("Obtainment", index.obtainmentSourceTier))
        sender.send(DataAvailability.describe("Fossils", index.fossilSourceTier))
        sender.send(DataAvailability.describe("Riding", index.ridingSourceTier))

        sender.send(tr("cobbledex-rei-emi-jei.cmd.dump_hint"))

        return 1
    }
    
    // Bypasses all of CobbleDex's own form-loading/dedup logic and prints
    // exactly what Cobblemon's live Species API reports for one species -
    // used to debug cases where a bogus alternate-form entry (e.g. a
    // cosmetic resourcepack skin) shows up and it's unclear whether
    // Cobblemon itself is reporting it as a form or CobbleDex is
    // synthesizing it.
    fun showRawForms(speciesName: String, sender: MessageSender): Int {
        val species = try {
            PokemonSpecies.getByName(speciesName.lowercase())
        } catch (e: Exception) {
            sender.send("§cFailed to look up species: ${e.message}")
            return 0
        }
        if (species == null) {
            sender.send("§cSpecies not found in Cobblemon runtime: $speciesName")
            return 0
        }
        sender.send("§6Raw Cobblemon forms for ${species.name}:")
        val standardForm = try { species.standardForm } catch (_: Exception) { null }
        sender.send("§7standardForm: name=${standardForm?.name} aspects=${standardForm?.aspects}")
        val forms = try { species.forms } catch (e: Exception) {
            sender.send("§cFailed to access species.forms: ${e.message}")
            return 0
        }
        sender.send("§7Total forms: ${forms.size}")
        val queries = SpawnDataIndex.currentQueries()
        for (form in forms) {
            val isStandard = form === standardForm
            val included = try { EvolutionDataLoader.shouldIncludeForm(species, form, standardForm) } catch (e: Exception) { "ERROR: ${e.message}" }
            val formKey = try { EvolutionDataLoader.buildFormEntryKey(species.name.lowercase(), form, species) } catch (e: Exception) { "ERROR: ${e.message}" }
            val inSpeciesInfo = SpawnDataIndex.speciesInfo.containsKey(formKey)
            val surfaced = try { queries.shouldSurfaceSpecies(formKey) } catch (e: Exception) { "ERROR: ${e.message}" }
            sender.send("  §fname=\"${form.name}\" aspects=${form.aspects} labels=${form.labels} isStandard=$isStandard")
            sender.send("    §7shouldIncludeForm=$included formKey=$formKey inSpeciesInfo=$inSpeciesInfo surfaced=$surfaced")
        }

        sender.send("§6species.evolutions (base-level):")
        val baseEvos = try { species.evolutions } catch (e: Exception) {
            sender.send("§cFailed to access species.evolutions: ${e.message}")
            emptyList()
        }
        if (baseEvos.isEmpty()) sender.send("  §7(none)")
        for (evo in baseEvos) {
            sender.send("  §fid=${evo.id} result=${evo.result.species}/${evo.result.aspects}")
        }

        sender.send("§6Per-form evolutions:")
        for (form in forms) {
            val formEvos = try { form.evolutions } catch (_: Exception) { continue }
            if (formEvos.isEmpty()) continue
            sender.send("  §7form=\"${form.name}\" aspects=${form.aspects}:")
            for (evo in formEvos) {
                sender.send("    §fid=${evo.id} result=${evo.result.species}/${evo.result.aspects}")
            }
        }

        // The processed data RecipeBuilder/EvolutionDex actually consume -
        // includes CobbleDex's own synthesized entries (fusion edges, etc),
        // not just what Cobblemon itself reports above.
        val normalized = SpeciesNameNormalizer.normalize(speciesName)
        sender.send("§6Processed evolutionsBySpecies[$normalized] (outgoing, what RecipeBuilder sees):")
        val outgoing = SpawnDataIndex.getEvolutionsFrom(normalized)
        if (outgoing.isEmpty()) sender.send("  §7(none)")
        for (evo in outgoing) {
            sender.send("  §ffrom=${evo.fromSpecies}${evo.fromAspects} to=${evo.toSpecies}${evo.toAspects} variant=${evo.variant} id=${evo.id}")
        }
        sender.send("§6Processed evolutionsToSpecies[$normalized] (incoming):")
        val incoming = SpawnDataIndex.getEvolutionsTo(normalized)
        if (incoming.isEmpty()) sender.send("  §7(none)")
        for (evo in incoming) {
            sender.send("  §ffrom=${evo.fromSpecies}${evo.fromAspects} to=${evo.toSpecies}${evo.toAspects} variant=${evo.variant} id=${evo.id}")
        }
        return 1
    }

    // Calls the exact same RecipeBuilder functions the REI/EMI Evolution tab
    // calls (EvolutionDex.buildRecipesFor), using the same form-key string a
    // Pokemon entry would carry (e.g. "fomantis_lunar"). Used to tell apart
    // "the data pipeline never produced this edge" (showRawForms would show
    // that) from "the pipeline has it right, but REI is asking for the wrong
    // key or something in RecipeBuilder's per-form lookup misses it" - this
    // command bypasses REI entirely and prints RecipeBuilder's raw output.
    fun showEvoPages(formKey: String, sender: MessageSender): Int {
        val normalized = SpeciesNameNormalizer.normalize(formKey)
        val queries = CobbleDexDataQueries(SpawnDataIndex.currentSnapshot())

        sender.send("§6buildEvolutionPagesFor($normalized) (outgoing, what the Evolution tab calls):")
        val outgoing = RecipeBuilder.buildEvolutionPagesFor(normalized)
        for (page in outgoing) {
            sender.send("  §fpage=${page.pageIndex}/${page.pageTotal} target=${page.targetSpeciesName}${page.targetAspects} methods=${page.methods.map { it.requirementText }}")
        }
        if (outgoing.isEmpty()) sender.send("  §7(empty list)")

        sender.send("§6buildEvolutionRecipesInto($normalized) (incoming, what the Evolution tab calls):")
        val incoming = RecipeBuilder.buildEvolutionRecipesInto(normalized)
        for (page in incoming) {
            sender.send("  §fsource=${page.sourceSpeciesName}${page.sourceAspects} target=${page.targetSpeciesName}${page.targetAspects} methods=${page.methods.map { it.requirementText }}")
        }
        if (incoming.isEmpty()) sender.send("  §7(empty list)")

        sender.send("§6getSpeciesInfo($normalized):")
        val info = queries.getSpeciesInfo(normalized)
        if (info == null) sender.send("  §7(null - not in speciesInfo)")
        else sender.send("  §fname=${info.name} baseSpeciesName=${info.baseSpeciesName} formAspects=${info.formAspects} isForm=${info.isForm}")

        // Direct raw map lookup, bypassing RecipeBuilder entirely - and every
        // key in the whole snapshot that starts with the same prefix, in case
        // the real stored key differs subtly from what we expect (missing
        // underscore, different casing survives normalize, etc).
        val snapshot = SpawnDataIndex.currentSnapshot()
        sender.send("§6Raw snapshot.evolutionsBySpecies[$normalized]:")
        val raw = snapshot.evolutionsBySpecies[normalized]
        if (raw == null) sender.send("  §7(key not present in map)")
        else if (raw.isEmpty()) sender.send("  §7(key present, empty list)")
        else for (evo in raw) sender.send("  §ffrom=${evo.fromSpecies}${evo.fromAspects} to=${evo.toSpecies}${evo.toAspects} variant=${evo.variant} id=${evo.id}")

        val prefix = normalized.substringBefore('_')
        val matchingKeys = snapshot.evolutionsBySpecies.keys.filter { it.startsWith(prefix) }.sorted()
        sender.send("§6All evolutionsBySpecies keys starting with '$prefix': $matchingKeys")

        // showRawForms looks up the species via PokemonSpecies.getByName, but
        // EvolutionDataLoader.loadFromRuntime() (which actually populates
        // evolutionsBySpecies) iterates PokemonSpecies.implemented instead.
        // If those two return different Species/FormData instances for the
        // same species (e.g. a stale duplicate left behind by a runtime data
        // reload), buildFormEntryKey could compute two different keys for
        // what looks like "the same" form in showRawForms vs here - this
        // replicates loadFromRuntime()'s exact lookup path to check for that.
        sender.send("§6Via PokemonSpecies.implemented (what loadFromRuntime iterates):")
        try {
            val implementedSpecies = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.implemented
                .filter { it.name.equals(prefix, ignoreCase = true) }
            sender.send("  §fmatching species instances: ${implementedSpecies.size}")
            for (sp in implementedSpecies) {
                val spEvos = try { sp.evolutions } catch (_: Exception) { emptyList() }
                sender.send("  §fspecies instance ${System.identityHashCode(sp)}: species.evolutions=${spEvos.size}")
                val forms = try { sp.forms } catch (_: Exception) { emptyList() }
                sender.send("  §f  ${forms.size} forms")
                for (form in forms) {
                    val fEvos = try { form.evolutions } catch (_: Exception) { emptyList() }
                    val computedKey = EvolutionDataLoader.buildFormEntryKey(prefix, form, sp)
                    val lvlCount = try { form.moves.levelUpMoves.values.sumOf { it.size } } catch (_: Exception) { -1 }
                    val eggCount = try { form.moves.eggMoves.size } catch (_: Exception) { -1 }
                    val tutorCount = try { form.moves.tutorMoves.size } catch (_: Exception) { -1 }
                    val tmCount = try { form.moves.tmMoves.size } catch (_: Exception) { -1 }
                    sender.send("    §fname=\"${form.name}\" aspects=${form.aspects} evolutions=${fEvos.size} computedKey=$computedKey")
                    sender.send("      §7moves: levelUp=$lvlCount egg=$eggCount tutor=$tutorCount tm=$tmCount primaryType=${try { form.primaryType } catch (_: Exception) { "?" }}")
                }
            }
        } catch (e: Exception) {
            sender.send("  §c${e.message}")
        }

        // Broader net: any TOP-LEVEL species (not form) whose own .name
        // contains our prefix - e.g. if a mod registered "Fomantis Lunar" as
        // its own separate species (not just a form/aspect of "Fomantis"),
        // its species.evolutions would get keyed by its raw (space-containing)
        // name, and SpeciesNameNormalizer strips spaces without inserting a
        // separator - "fomantis lunar" -> "fomantislunar". That would explain
        // an evolutionsBySpecies key with no underscore that showRawForms
        // (which only looks at the real "Fomantis" species's own forms) would
        // never see, since it's a completely separate species registration.
        sender.send("§6All top-level species whose name contains '$prefix' (not exact match):")
        try {
            val containing = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.implemented
                .filter { it.name.contains(prefix, ignoreCase = true) && !it.name.equals(prefix, ignoreCase = true) }
            if (containing.isEmpty()) sender.send("  §7(none found)")
            for (sp in containing) {
                val spEvos = try { sp.evolutions } catch (_: Exception) { emptyList() }
                sender.send("  §fname=\"${sp.name}\" species.evolutions=${spEvos.size} normalizedKey=${SpeciesNameNormalizer.normalize(sp.name)}")
                for (evo in spEvos) {
                    val resultSpecies = try { evo.result.species } catch (_: Exception) { null }
                    sender.send("    §fevo id=${evo.id} result.species=$resultSpecies result.aspects=${try { evo.result.aspects } catch (_: Exception) { null }}")
                }
            }
        } catch (e: Exception) {
            sender.send("  §c${e.message}")
        }

        sender.send("§6shouldSurfaceSpecies($normalized): ${queries.shouldSurfaceSpecies(normalized)}")
        return 1
    }

    fun showMissing(sender: MessageSender): Int {
        val index = SpawnDataIndex
        
        if (!index.hasData()) {
            sender.send(tr("cobbledex-rei-emi-jei.cmd.no_data_short"))
            return 0
        }
        
        val withSpawns = index.spawnsBySpecies.keys
        val withObtainment = index.obtainmentBySpecies.keys
        
        val indexedDexSpecies = index.speciesInfo
            .filter { it.value.nationalDexNumber > 0 }

        val missingBoth = indexedDexSpecies
            .filter { !withSpawns.contains(it.key) && !withObtainment.contains(it.key) }
            .keys
            .sorted()
        
        sender.send(tr("cobbledex-rei-emi-jei.cmd.missing_header"))
        
        if (missingBoth.isEmpty()) {
            sender.send(tr("cobbledex-rei-emi-jei.cmd.all_have_data"))
        } else {
            sender.send(tr("cobbledex-rei-emi-jei.cmd.missing_count", missingBoth.size))
            val preview = missingBoth.take(20).joinToString(", ")
            val suffix = if (missingBoth.size > 20) tr("cobbledex-rei-emi-jei.cmd.and_more", missingBoth.size - 20) else ""
            sender.send("§7$preview$suffix")
        }
        
        sender.send(tr("cobbledex-rei-emi-jei.cmd.dump_hint_complete"))
        return 1
    }
    
    fun reloadData(sender: MessageSender): Int {
        sender.send(tr("cobbledex-rei-emi-jei.cmd.reloading"))
        SpawnDataIndex.loadAll()
        RecipeViewerReloader.scheduleReload()
        sender.send(tr("cobbledex-rei-emi-jei.cmd.reload_complete", SpawnDataIndex.allSpeciesNames.size))
        return 1
    }
    
    fun dumpDiagnostics(sender: MessageSender): Int {
        val index = SpawnDataIndex
        
        if (!index.hasData()) {
            sender.send(tr("cobbledex-rei-emi-jei.cmd.no_data_short"))
            return 0
        }
        
        try {
            val report = buildDiagnosticReport()
            val outputFile = writeDiagnosticFile(report)
            
            sender.send(tr("cobbledex-rei-emi-jei.cmd.report_written"))
            sender.send("§7$outputFile")
            sender.send(tr("cobbledex-rei-emi-jei.cmd.total_lines", report.lines().size))
        } catch (e: Exception) {
            sender.send(tr("cobbledex-rei-emi-jei.cmd.report_failed", e.message ?: "unknown"))
            DebugLog.warn("Diagnostic dump failed: ${e.message}")
            return 0
        }
        
        return 1
    }
    
    private fun buildDiagnosticReport(): String {
        val sb = StringBuilder()
        val index = SpawnDataIndex
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        
        sb.appendLine("=".repeat(80))
        sb.appendLine("COBBLEDEX - DIAGNOSTIC REPORT")
        sb.appendLine("Generated: $timestamp")
        sb.appendLine("=".repeat(80))
        sb.appendLine()
        
        val allSpecies = index.allSpeciesNames
        val withSpawns = index.spawnsBySpecies
        val withEvolutions = index.evolutionsBySpecies
        val withObtainment = index.obtainmentBySpecies
        val speciesInfo = index.speciesInfo
        
        sb.appendLine("SUMMARY")
        sb.appendLine("-".repeat(40))
        sb.appendLine("Total species in index: ${allSpecies.size}")
        sb.appendLine("Species with spawn data: ${withSpawns.size}")
        sb.appendLine("Species with evolution data: ${withEvolutions.size}")
        sb.appendLine("Species with obtainment data: ${withObtainment.size}")
        sb.appendLine("Species with National Dex #: ${speciesInfo.count { it.value.nationalDexNumber > 0 }}")
        sb.appendLine("Load state: ${index.loadState.name}")
        sb.appendLine()

        appendSourceDiagnostics(sb, index)
        appendMaterialFormDiagnostics(sb, index)
        
        val dexSpecies = speciesInfo.filter { it.value.nationalDexNumber > 0 }
        val noDex = allSpecies.filter { speciesInfo[it]?.nationalDexNumber == null || speciesInfo[it]?.nationalDexNumber == 0 }
        
        sb.appendLine("SPECIES CATEGORIES")
        sb.appendLine("-".repeat(40))
        sb.appendLine("Species with National Dex #: ${dexSpecies.size}")
        sb.appendLine("Species without National Dex #: ${noDex.size}")
        sb.appendLine()
        
        val dexSpeciesNames = dexSpecies.keys
        val dexWithSpawns = dexSpeciesNames.filter { withSpawns.containsKey(it) }
        val dexWithObtainment = dexSpeciesNames.filter { withObtainment.containsKey(it) }
        val dexMissingSpawns = dexSpeciesNames.filter { !withSpawns.containsKey(it) }
        val dexMissingBoth = dexSpeciesNames.filter { !withSpawns.containsKey(it) && !withObtainment.containsKey(it) }
        
        sb.appendLine("NATIONAL DEX COVERAGE")
        sb.appendLine("-".repeat(40))
        sb.appendLine("With spawn data: ${dexWithSpawns.size}/${dexSpecies.size} (${percent(dexWithSpawns.size, dexSpecies.size)})")
        sb.appendLine("With obtainment data: ${dexWithObtainment.size}/${dexSpecies.size} (${percent(dexWithObtainment.size, dexSpecies.size)})")
        sb.appendLine("Missing spawns: ${dexMissingSpawns.size}")
        sb.appendLine("Missing BOTH spawn and obtainment: ${dexMissingBoth.size}")
        sb.appendLine()
        
        sb.appendLine("=".repeat(80))
        sb.appendLine("DETAILED LISTS")
        sb.appendLine("=".repeat(80))
        sb.appendLine()
        
        sb.appendLine("DEX-INDEXED SPECIES MISSING SPAWN DATA (${dexMissingSpawns.size})")
        sb.appendLine("-".repeat(40))
        if (dexMissingSpawns.isEmpty()) {
            sb.appendLine("(none)")
        } else {
            val sorted = dexMissingSpawns.sortedBy { speciesInfo[it]?.nationalDexNumber ?: Int.MAX_VALUE }
            for (species in sorted) {
                val dex = speciesInfo[species]?.nationalDexNumber ?: 0
                val hasObtain = if (withObtainment.containsKey(species)) " [has obtainment]" else ""
                sb.appendLine("#${dex.toString().padStart(4, '0')} $species$hasObtain")
            }
        }
        sb.appendLine()
        
        sb.appendLine("DEX-INDEXED SPECIES MISSING BOTH SPAWN AND OBTAINMENT (${dexMissingBoth.size})")
        sb.appendLine("-".repeat(40))
        if (dexMissingBoth.isEmpty()) {
            sb.appendLine("(none - all dex-indexed species have at least spawn or obtainment data)")
        } else {
            val sorted = dexMissingBoth.sortedBy { speciesInfo[it]?.nationalDexNumber ?: Int.MAX_VALUE }
            for (species in sorted) {
                val dex = speciesInfo[species]?.nationalDexNumber ?: 0
                sb.appendLine("#${dex.toString().padStart(4, '0')} $species")
            }
        }
        sb.appendLine()
        
        sb.appendLine("SPECIES WITHOUT NATIONAL DEX # (${noDex.size})")
        sb.appendLine("-".repeat(40))
        val noDexSorted = noDex.sorted()
        for (species in noDexSorted.take(100)) {
            val hasSpawn = if (withSpawns.containsKey(species)) "spawn" else ""
            val hasObtain = if (withObtainment.containsKey(species)) "obtainment" else ""
            val hasEvo = if (withEvolutions.containsKey(species)) "evolution" else ""
            val dataTypes = listOf(hasSpawn, hasObtain, hasEvo).filter { it.isNotEmpty() }.joinToString(", ")
            sb.appendLine("$species: ${dataTypes.ifEmpty { "(no data)" }}")
        }
        if (noDex.size > 100) {
            sb.appendLine("... and ${noDex.size - 100} more")
        }
        sb.appendLine()
        
        sb.appendLine("=".repeat(80))
        sb.appendLine("SPAWN DATA ANALYSIS")
        sb.appendLine("=".repeat(80))
        sb.appendLine()
        
        val totalSpawnEntries = withSpawns.values.sumOf { it.size }
        sb.appendLine("Total spawn entries: $totalSpawnEntries across ${withSpawns.size} species")
        sb.appendLine()
        
        sb.appendLine("TOP 20 SPECIES BY SPAWN ENTRY COUNT")
        sb.appendLine("-".repeat(40))
        val bySpawnCount = withSpawns.entries.sortedByDescending { it.value.size }.take(20)
        for ((species, spawns) in bySpawnCount) {
            sb.appendLine("$species: ${spawns.size} spawn entries")
        }
        sb.appendLine()
        
        sb.appendLine("=".repeat(80))
        sb.appendLine("OBTAINMENT DATA ANALYSIS")
        sb.appendLine("=".repeat(80))
        sb.appendLine()
        
        val obtainMethods = mutableMapOf<String, Int>()
        for ((_, infos) in withObtainment) {
            for (info in infos) {
                obtainMethods[info.method] = (obtainMethods[info.method] ?: 0) + 1
            }
        }
        
        sb.appendLine("OBTAINMENT METHODS")
        sb.appendLine("-".repeat(40))
        for ((method, count) in obtainMethods.entries.sortedByDescending { it.value }) {
            sb.appendLine("$method: $count entries")
        }
        sb.appendLine()
        
        sb.appendLine("=".repeat(80))
        sb.appendLine("COBBLEMON RUNTIME CHECK")
        sb.appendLine("=".repeat(80))
        sb.appendLine()
        
        try {
            val runtimeSpeciesRaw = PokemonSpecies.implemented.map { it.name.lowercase() }.toSet()
            val runtimeSpeciesNormalized = runtimeSpeciesRaw.map { SpeciesNameNormalizer.normalize(it) }.toSet()
            val inIndexButNotRuntime = allSpecies.filter { !runtimeSpeciesNormalized.contains(it) }
            val inRuntimeButNotIndex = runtimeSpeciesRaw.filter { !allSpecies.contains(SpeciesNameNormalizer.normalize(it)) }
            
            sb.appendLine("Cobblemon runtime species count: ${runtimeSpeciesRaw.size}")
            sb.appendLine("In index but not runtime: ${inIndexButNotRuntime.size}")
            sb.appendLine("In runtime but not index: ${inRuntimeButNotIndex.size}")
            
            if (inIndexButNotRuntime.isNotEmpty() && inIndexButNotRuntime.size <= 50) {
                sb.appendLine()
                sb.appendLine("Species in index but not Cobblemon runtime:")
                for (s in inIndexButNotRuntime.sorted()) {
                    sb.appendLine("  $s")
                }
            }
            
            if (inRuntimeButNotIndex.isNotEmpty() && inRuntimeButNotIndex.size <= 50) {
                sb.appendLine()
                sb.appendLine("Species in Cobblemon runtime but missing from index:")
                for (s in inRuntimeButNotIndex.sorted()) {
                    sb.appendLine("  $s")
                }
            }
        } catch (e: Exception) {
            sb.appendLine("Could not access Cobblemon runtime: ${e.message}")
        }
        sb.appendLine()
        
        sb.appendLine("=".repeat(80))
        sb.appendLine("END OF REPORT")
        sb.appendLine("=".repeat(80))
        
        return sb.toString()
    }

    private fun appendSourceDiagnostics(sb: StringBuilder, index: SpawnDataIndex) {
        sb.appendLine("DATA SOURCE STATUS")
        sb.appendLine("-".repeat(40))
        sb.appendLine("Preferred order: ${DataSourcePolicy.describePrecedence()}")
        sb.appendLine("Spawns: ${index.spawnSourceTier.displayName}")
        sb.appendLine("Evolutions: ${index.evolutionSourceTier.displayName}")
        sb.appendLine("Species info: ${index.speciesInfoSourceTier.displayName}")
        sb.appendLine("Obtainment: ${index.obtainmentSourceTier.displayName}")
        sb.appendLine("Fossils: ${index.fossilSourceTier.displayName}")
        sb.appendLine("Riding: ${index.ridingSourceTier.displayName}")

        val obtainmentSourceCounts = index.obtainmentBySpecies.values
            .flatten()
            .map { DataSourcePolicy.tierFor(it.source) }
            .groupingBy { it }
            .eachCount()
        if (obtainmentSourceCounts.isNotEmpty()) {
            sb.appendLine("Obtainment source counts:")
            for ((tier, count) in obtainmentSourceCounts.entries.sortedBy { it.key.rank }) {
                sb.appendLine("  ${tier.displayName}: $count")
            }
        }
        sb.appendLine()
    }

    private fun appendMaterialFormDiagnostics(sb: StringBuilder, index: SpawnDataIndex) {
        val decisions = index.speciesInfo.keys.mapNotNull { species ->
            index.materialFormDecision(species)?.let { species to it }
        }
        val surfaced = decisions.filter { it.second.surface }
        val collapsed = decisions.filter { !it.second.surface }

        sb.appendLine("MATERIAL FORM STATUS")
        sb.appendLine("-".repeat(40))
        sb.appendLine("Total indexed forms: ${decisions.size}")
        sb.appendLine("Surfaced material forms: ${surfaced.size}")
        sb.appendLine("Collapsed cosmetic forms: ${collapsed.size}")

        val reasonCounts = surfaced
            .flatMap { it.second.reasons }
            .groupingBy { it }
            .eachCount()
        if (reasonCounts.isNotEmpty()) {
            sb.appendLine("Material reason counts:")
            for ((reason, count) in reasonCounts.entries.sortedByDescending { it.value }) {
                sb.appendLine("  $reason: $count")
            }
        }
        if (collapsed.isNotEmpty()) {
            sb.appendLine("Collapsed examples: ${collapsed.map { it.first }.sorted().take(30).joinToString(", ")}")
            if (collapsed.size > 30) sb.appendLine("... and ${collapsed.size - 30} more")
        }
        sb.appendLine()
    }
    
    private fun percent(part: Int, total: Int): String {
        if (total == 0) return "0%"
        return "%.1f%%".format(part.toDouble() / total * 100)
    }
    
    private fun writeDiagnosticFile(content: String): String {
        val gameDir = try {
            com.cobbledex.platform.PlatformHelper.getGameDir()
        } catch (e: Exception) {
            java.nio.file.Paths.get(".")
        }
        
        val debugDir = gameDir.resolve("cobbledex-debug")
        if (!Files.exists(debugDir)) {
            Files.createDirectories(debugDir)
        }
        
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val fileName = "diagnostic_$timestamp.txt"
        val outputPath = debugDir.resolve(fileName)
        
        Files.writeString(outputPath, content)
        return outputPath.toAbsolutePath().toString()
    }
}
