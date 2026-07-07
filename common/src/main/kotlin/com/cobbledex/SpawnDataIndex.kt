package com.cobbledex

import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Suppress("ObjectPropertyName")
object SpawnDataIndex {

    enum class LoadState { NOT_LOADED, PARTIAL, FULLY_LOADED }

    @Volatile
    private var snapshot: CobbleDexDataSnapshot = CobbleDexDataSnapshot()

    @Volatile
    private var cachedQueriesSnapshot: CobbleDexDataSnapshot? = null

    @Volatile
    private var cachedQueries: CobbleDexDataQueries? = null

    var loadState: LoadState
        get() = snapshot.loadState
        private set(value) { snapshot = snapshot.copy(loadState = value) }

    private val dataLock = ReentrantLock()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "CobbleDex-DataLoad").apply { isDaemon = true }
    }
    private var loadFuture: Future<*>? = null

    var spawnsBySpecies: Map<String, List<SpawnInfo>>
        get() = snapshot.spawnsBySpecies
        private set(value) { snapshot = snapshot.copy(spawnsBySpecies = value) }

    var evolutionsBySpecies: Map<String, List<EvolutionInfo>>
        get() = snapshot.evolutionsBySpecies
        private set(value) { snapshot = snapshot.copy(evolutionsBySpecies = value) }

    var evolutionsToSpecies: Map<String, List<EvolutionInfo>>
        get() = snapshot.evolutionsToSpecies
        private set(value) { snapshot = snapshot.copy(evolutionsToSpecies = value) }

    var speciesInfo: Map<String, EvolutionDataLoader.SpeciesBasicInfo>
        get() = snapshot.speciesInfo
        private set(value) { snapshot = snapshot.copy(speciesInfo = value) }

    var obtainmentBySpecies: Map<String, List<ObtainmentInfo>>
        get() = snapshot.obtainmentBySpecies
        private set(value) { snapshot = snapshot.copy(obtainmentBySpecies = value) }

    var fossilsBySpecies: Map<String, List<FossilCombo>>
        get() = snapshot.fossilsBySpecies
        private set(value) { snapshot = snapshot.copy(fossilsBySpecies = value) }

    var dropsByItem: Map<String, List<String>>
        get() = snapshot.dropsByItem
        private set(value) { snapshot = snapshot.copy(dropsByItem = value) }

    var speciesByTmMove: Map<String, List<String>>
        get() = snapshot.speciesByTmMove
        private set(value) { snapshot = snapshot.copy(speciesByTmMove = value) }

    var jobRules: List<JobRule>
        get() = snapshot.jobRules
        private set(value) { snapshot = snapshot.copy(jobRules = value) }

    var ridingBySpecies: Map<String, RidingInfo>
        get() = snapshot.ridingBySpecies
        private set(value) { snapshot = snapshot.copy(ridingBySpecies = value) }

    var allSpeciesNames: List<String>
        get() = snapshot.allSpeciesNames
        private set(value) { snapshot = snapshot.copy(allSpeciesNames = value) }

    var spawnSourceTier: DataSourceTier
        get() = snapshot.spawnSourceTier
        private set(value) { snapshot = snapshot.copy(spawnSourceTier = value) }

    var evolutionSourceTier: DataSourceTier
        get() = snapshot.evolutionSourceTier
        private set(value) { snapshot = snapshot.copy(evolutionSourceTier = value) }

    var speciesInfoSourceTier: DataSourceTier
        get() = snapshot.speciesInfoSourceTier
        private set(value) { snapshot = snapshot.copy(speciesInfoSourceTier = value) }

    var obtainmentSourceTier: DataSourceTier
        get() = snapshot.obtainmentSourceTier
        private set(value) { snapshot = snapshot.copy(obtainmentSourceTier = value) }

    var fossilSourceTier: DataSourceTier
        get() = snapshot.fossilSourceTier
        private set(value) { snapshot = snapshot.copy(fossilSourceTier = value) }

    var ridingSourceTier: DataSourceTier
        get() = snapshot.ridingSourceTier
        private set(value) { snapshot = snapshot.copy(ridingSourceTier = value) }

    var dataVersion: Long
        get() = snapshot.dataVersion
        private set(value) { snapshot = snapshot.copy(dataVersion = value) }

    /** Tracks local load attempts where species exist but evolutions are empty */
    @Volatile
    private var emptyEvoRetries = 0
    private const val MAX_EMPTY_EVO_RETRIES = 5

    /** True when spawn data was received from the server via networking */
    @Volatile
    private var hasServerSync = false

    fun isFullyLoaded(): Boolean = loadState == LoadState.FULLY_LOADED

    fun hasData(): Boolean = allSpeciesNames.isNotEmpty()

    fun currentSnapshot(): CobbleDexDataSnapshot = snapshot

    fun currentQueries(): CobbleDexDataQueries {
        val current = snapshot
        val cachedSnapshot = cachedQueriesSnapshot
        val cached = cachedQueries
        if (cachedSnapshot === current && cached != null) return cached

        val queries = CobbleDexDataQueries(current)
        cachedQueriesSnapshot = current
        cachedQueries = queries
        return queries
    }

    fun ensureLoaded() {
        when (loadState) {
            LoadState.FULLY_LOADED -> return
            LoadState.PARTIAL -> {
                val count = try { PokemonSpecies.implemented.count() } catch (_: Exception) { 0 }
                if (count > 0) {
                    DebugLog.info("Runtime API now has $count species, reloading")
                    loadAll()
                }
            }
            LoadState.NOT_LOADED -> loadAll()
        }
    }

    fun ensureLoadedAsync() {
        when (loadState) {
            LoadState.FULLY_LOADED -> return
            LoadState.PARTIAL -> {
                val count = try { PokemonSpecies.implemented.count() } catch (_: Exception) { 0 }
                if (count > 0) {
                    DebugLog.info("Runtime API now has $count species, reloading async")
                    loadAllAsync()
                }
            }
            LoadState.NOT_LOADED -> loadAllAsync()
        }
    }

    fun loadAll() {
        if (!dataLock.tryLock(10, java.util.concurrent.TimeUnit.SECONDS)) {
            DebugLog.warn("Data load skipped: lock held by another thread for >10s")
            return
        }
        try {
            cancelPendingLoad()
            doLoad()
        } catch (e: Exception) {
            DebugLog.warn("Data load failed: ${e.message}")
        } finally {
            dataLock.unlock()
        }
    }

    private fun loadAllAsync() {
        val prev = loadFuture
        if (prev != null && !prev.isDone) return
        loadFuture = executor.submit {
            dataLock.lock()
            try {
                doLoad()
            } catch (e: Exception) {
                DebugLog.warn("Async data load failed: ${e.message}")
            } finally {
                dataLock.unlock()
            }
        }
    }

    private fun cancelPendingLoad() {
        loadFuture?.cancel(true)
        loadFuture = null
    }

    private fun doLoad() {
        DebugLog.reset()
        PokemonItemCache.reset()

        if (hasServerSync) {
            // Server already sent spawns, evolutions, species info, and fossils — just load supplementary data
            spawnSourceTier = DataSourceTier.SERVER_SYNC
            evolutionSourceTier = DataSourceTier.SERVER_SYNC
            speciesInfoSourceTier = DataSourceTier.SERVER_SYNC
            try {
                obtainmentBySpecies = normalizeMapKeys(ObtainmentDataLoader.loadFromAllSources(
                    SpawnDataLoader.getModRootPaths()
                ))
                obtainmentSourceTier = DataSourcePolicy.preferredSource(obtainmentBySpecies.values.flatten().map { it.source })
            } catch (e: Exception) {
                DebugLog.warn("Obtainment data load failed: ${e.message}")
                obtainmentBySpecies = emptyMap()
                obtainmentSourceTier = DataSourceTier.UNKNOWN
            }

            // Fossils from server sync; fall back to JarDataCache if sync had none
            if (fossilsBySpecies.isEmpty() && JarDataCache.hasCachedFossils()) {
                DebugLog.info("Using JarDataCache fossils (${JarDataCache.getCachedFossils().size} species)")
                fossilsBySpecies = normalizeMapKeys(JarDataCache.getCachedFossils())
                fossilSourceTier = DataSourceTier.JAR_OR_DATAPACK
            } else if (fossilsBySpecies.isNotEmpty()) {
                fossilSourceTier = DataSourceTier.SERVER_SYNC
            } else {
                fossilSourceTier = DataSourceTier.UNKNOWN
            }

            rebuildDerivedData()
            if (loadState != LoadState.FULLY_LOADED) {
                loadState = LoadState.FULLY_LOADED
            }
            dataVersion++

            DebugLog.info(
                "Load complete (server-synced, ${loadState.name}): ${allSpeciesNames.size} species " +
                "(${speciesInfo.count { it.value.nationalDexNumber > 0 }} with dex, " +
                "${spawnsBySpecies.size} with spawns, ${evolutionsBySpecies.size} with evolutions, " +
                "${obtainmentBySpecies.size} with obtainment)"
            )
            return
        }

        // --- Baseline: start with JarDataCache (loaded on game launch) ---
        // Wait briefly for cache if it's still initializing
        JarDataCache.awaitReady(5_000)

        // Try Cobblemon's runtime spawn pool (populated in singleplayer or by server)
        SpawnDataLoader.invalidateCache()
        val runtimeSpawns = normalizeMapKeys(SpawnDataLoader.loadFromRuntime())
        spawnsBySpecies = if (runtimeSpawns.isNotEmpty()) {
            spawnSourceTier = DataSourceTier.RUNTIME
            runtimeSpawns
        } else if (JarDataCache.hasCachedSpawns()) {
            DebugLog.info("Using JarDataCache spawns (${JarDataCache.getCachedSpawns().size} species)")
            spawnSourceTier = DataSourceTier.JAR_OR_DATAPACK
            normalizeMapKeys(JarDataCache.getCachedSpawns())
        } else {
            spawnSourceTier = DataSourceTier.UNKNOWN
            emptyMap()
        }

        val runtimeCount = try { PokemonSpecies.implemented.count() } catch (_: Exception) { 0 }

        if (runtimeCount > 0) {
            // Try runtime evolutions (works in singleplayer, empty on dedicated servers)
            try {
                evolutionsBySpecies = normalizeMapKeys(EvolutionDataLoader.loadFromRuntime())
                evolutionSourceTier = if (evolutionsBySpecies.isNotEmpty()) DataSourceTier.RUNTIME else DataSourceTier.UNKNOWN
            } catch (e: Exception) {
                DebugLog.warn("Runtime evolution load failed: ${e.message}")
                evolutionsBySpecies = emptyMap()
                evolutionSourceTier = DataSourceTier.UNKNOWN
            }

            // Fall back to JarDataCache evolutions if runtime is empty
            if (evolutionsBySpecies.isEmpty() && JarDataCache.hasCachedEvolutions()) {
                DebugLog.info("Using JarDataCache evolutions (${JarDataCache.getCachedEvolutions().size} species)")
                evolutionsBySpecies = normalizeMapKeys(JarDataCache.getCachedEvolutions())
                evolutionSourceTier = DataSourceTier.JAR_OR_DATAPACK
            }

            try {
                speciesInfo = normalizeMapKeys(EvolutionDataLoader.loadSpeciesBasicInfoFromRuntime())
                speciesInfoSourceTier = if (speciesInfo.isNotEmpty()) DataSourceTier.RUNTIME else DataSourceTier.UNKNOWN
            } catch (e: Exception) {
                DebugLog.warn("Runtime species info load failed: ${e.message}")
                speciesInfo = emptyMap()
                speciesInfoSourceTier = DataSourceTier.UNKNOWN
            }
        } else {
            DebugLog.warn("PokemonSpecies.implemented empty, spawn data only")
            // Still use cached evolutions even without runtime species
            if (JarDataCache.hasCachedEvolutions()) {
                evolutionsBySpecies = normalizeMapKeys(JarDataCache.getCachedEvolutions())
                evolutionSourceTier = DataSourceTier.JAR_OR_DATAPACK
            } else {
                evolutionsBySpecies = emptyMap()
                evolutionSourceTier = DataSourceTier.UNKNOWN
            }
            speciesInfo = emptyMap()
            speciesInfoSourceTier = DataSourceTier.UNKNOWN
        }

        try {
            obtainmentBySpecies = normalizeMapKeys(ObtainmentDataLoader.loadFromAllSources(
                SpawnDataLoader.getModRootPaths()
            ))
            obtainmentSourceTier = DataSourcePolicy.preferredSource(obtainmentBySpecies.values.flatten().map { it.source })
        } catch (e: Exception) {
            DebugLog.warn("Obtainment data load failed: ${e.message}")
            obtainmentBySpecies = emptyMap()
            obtainmentSourceTier = DataSourceTier.UNKNOWN
        }

        // Cobblemon's client-side Fossils.all() has empty ingredient lists due to
        // FossilRegistrySyncPacket.decodeEntry() not syncing ItemPredicates — use JarDataCache instead
        if (JarDataCache.hasCachedFossils()) {
            fossilsBySpecies = normalizeMapKeys(JarDataCache.getCachedFossils())
            fossilSourceTier = DataSourceTier.JAR_OR_DATAPACK
        } else {
            fossilsBySpecies = emptyMap()
            fossilSourceTier = DataSourceTier.UNKNOWN
        }

        // Enrich speciesInfo with JAR-cached moves when runtime API returned null
        enrichWithJarMoves()

        // Load riding data from Cobblemon runtime API
        try {
            ridingBySpecies = RidingDataLoader.loadFromRuntime()
            ridingSourceTier = if (ridingBySpecies.isNotEmpty()) DataSourceTier.RUNTIME else DataSourceTier.UNKNOWN
        } catch (e: Exception) {
            DebugLog.warn("Riding data load failed: ${e.message}")
            ridingBySpecies = emptyMap()
            ridingSourceTier = DataSourceTier.UNKNOWN
        }

        rebuildDerivedData()

        val hasEvolutions = evolutionsBySpecies.isNotEmpty()
        val hasSpawns = spawnsBySpecies.isNotEmpty()
        loadState = when {
            runtimeCount == 0 && !hasEvolutions && !hasSpawns -> LoadState.PARTIAL
            runtimeCount == 0 -> {
                // Have cached data but no runtime species yet
                LoadState.PARTIAL
            }
            !hasEvolutions && emptyEvoRetries < MAX_EMPTY_EVO_RETRIES -> {
                emptyEvoRetries++
                DebugLog.info("Species loaded ($runtimeCount) but no evolutions found (attempt $emptyEvoRetries/$MAX_EMPTY_EVO_RETRIES) — staying PARTIAL for retry")
                LoadState.PARTIAL
            }
            !hasEvolutions -> {
                DebugLog.warn("Species loaded ($runtimeCount) but evolutions still empty after $MAX_EMPTY_EVO_RETRIES retries — accepting as final state")
                LoadState.FULLY_LOADED
            }
            else -> {
                emptyEvoRetries = 0
                LoadState.FULLY_LOADED
            }
        }
        dataVersion++

        // Diagnostic: report species with spawns but no species info entry
        if (spawnsBySpecies.isNotEmpty() && speciesInfo.isNotEmpty()) {
            val orphanSpawns = spawnsBySpecies.keys.filter { it !in speciesInfo }
            if (orphanSpawns.isNotEmpty()) {
                DebugLog.info("Spawn-only species (no speciesInfo): ${orphanSpawns.take(20).joinToString(", ")}${if (orphanSpawns.size > 20) " (+${orphanSpawns.size - 20} more)" else ""}")
            }
        }

        DebugLog.info(
            "Load complete (${loadState.name}): ${allSpeciesNames.size} species " +
            "(${speciesInfo.count { it.value.nationalDexNumber > 0 }} with dex, " +
            "${spawnsBySpecies.size} with spawns, ${evolutionsBySpecies.size} with evolutions, " +
            "${obtainmentBySpecies.size} with obtainment)"
        )
    }

    /** Mark data stale on disconnect. Cached JAR data is preserved —
     *  only runtime/server data is cleared. */
    fun onDisconnect() {
        cancelPendingLoad()
        PokemonItemCache.reset()
        RecipeViewerReloader.reset()
        emptyEvoRetries = 0
        hasServerSync = false
        jobRules = emptyList()

        dataLock.withLock {
            loadState = LoadState.NOT_LOADED
        }
        DebugLog.info("Marked data stale on disconnect (${allSpeciesNames.size} species cached)")
    }

    /** Accept all data synced from the server via networking */
    fun applyServerSync(syncedSpawns: Map<String, List<SpawnInfo>>,
                        syncedEvolutions: Map<String, List<EvolutionInfo>>,
                        syncedSpeciesInfo: Map<String, EvolutionDataLoader.SpeciesBasicInfo>,
                        syncedJobRules: List<JobRule>? = null,
                        syncedFossils: Map<String, List<FossilCombo>>? = null) {
        dataLock.withLock {
            PokemonItemCache.reset()
            spawnsBySpecies = normalizeMapKeys(syncedSpawns)
            evolutionsBySpecies = normalizeMapKeys(syncedEvolutions)
            speciesInfo = normalizeMapKeys(syncedSpeciesInfo)
            jobRules = syncedJobRules ?: emptyList()
            if (syncedFossils != null) fossilsBySpecies = normalizeMapKeys(syncedFossils)
            spawnSourceTier = DataSourceTier.SERVER_SYNC
            evolutionSourceTier = DataSourceTier.SERVER_SYNC
            speciesInfoSourceTier = DataSourceTier.SERVER_SYNC
            fossilSourceTier = if (syncedFossils != null) DataSourceTier.SERVER_SYNC else fossilSourceTier
            hasServerSync = true
            rebuildDerivedData()
            dataVersion++

            loadState = if (spawnsBySpecies.isNotEmpty() || evolutionsBySpecies.isNotEmpty()) {
                LoadState.FULLY_LOADED
            } else {
                LoadState.PARTIAL
            }
        }
        val jobMsg = if (jobRules.isNotEmpty()) ", ${jobRules.size} job rules" else ""
        val totalSpawnEntries = syncedSpawns.values.sumOf { it.size }
        CobbleDexMod.LOGGER.info("[CobbleDex] Server sync received: ${syncedSpawns.size} species ($totalSpawnEntries spawn entries), " +
            "${syncedEvolutions.size} evolutions, ${syncedSpeciesInfo.size} species info$jobMsg — scheduling recipe viewer reload")
        DebugLog.info("Applied server sync: ${syncedSpawns.size} species with spawns, " +
            "${syncedEvolutions.size} with evolutions, ${syncedSpeciesInfo.size} with info$jobMsg " +
            "(loadState=${loadState.name}, dataVersion=$dataVersion)")

        RecipeViewerReloader.scheduleReload()

        if (loadState != LoadState.FULLY_LOADED) {
            ensureLoadedAsync()
        }
    }

    /** Accept job rules from Cobbleworkers' own network packet (independent of CobbleDex server sync) */
    fun applyJobRules(rules: List<JobRule>) {
        dataLock.withLock {
            jobRules = rules
            dataVersion++
        }
        DebugLog.info("Applied ${rules.size} job rules from Cobbleworkers packet")
        RecipeViewerReloader.scheduleReload()
    }

    private fun rebuildDerivedData() {
        val result = DerivedDataBuilder.rebuild(snapshot)
        snapshot = result.snapshot
        if (result.backfilledSpeciesInfoCount > 0) {
            DebugLog.info("Backfilled ${result.backfilledSpeciesInfoCount} orphan evolution keys into speciesInfo")
        }
        result.speciesEnumerationError?.let { message ->
            DebugLog.warn("Species enumeration interrupted: $message")
        }
    }

    /**
     * Fill in missing move data from JarDataCache when the runtime API
     * didn't provide egg/tutor/tm/level-up moves (common on dedicated-server clients).
     *
     * A form's own move data is checked first (formJarMoves, keyed by the same
     * form key as speciesInfo) before falling back to the base species' moves
     * (jarMoves, bare-name keyed) - Cobblemon's client-side FormData for a
     * species_additions-nested form routinely only syncs its LEVEL-UP moves,
     * silently dropping any egg/tutor/tm moves the form defines on its own
     * (confirmed via /cobbledex evo: Laser's Fakemon Pack's Fomantis Lunar
     * form defines 102 moves of its own, but Cobblemon's runtime form.moves
     * only exposed the 13 level-up ones). Falling back straight to the base
     * species' moves in that case would silently substitute the wrong
     * Pokemon's moveset instead of the form's own (missing) one.
     */
    private fun enrichWithJarMoves() {
        if (!JarDataCache.hasCachedMoves() && !JarDataCache.hasCachedFormMoves()) return
        if (speciesInfo.isEmpty()) return
        val jarMoves = JarDataCache.getCachedMoves()
        val formJarMoves = JarDataCache.getCachedFormMoves()

        val enriched = speciesInfo.toMutableMap()
        var enrichCount = 0

        for ((species, info) in enriched) {
            val baseKey = info.baseSpeciesName ?: species
            val jarData = formJarMoves[species] ?: jarMoves[baseKey] ?: continue
            val needsEnrichment = info.levelUpMoves == null || info.eggMoves == null ||
                info.tutorMoves == null || info.tmMoves == null
            if (!needsEnrichment) continue

            val resolvedLevelUp = if (info.levelUpMoves == null && jarData.levelUp.isNotEmpty()) {
                jarData.levelUp.entries.sortedBy { it.key }.mapNotNull { (level, names) ->
                    val moves = names.mapNotNull { resolveMoveByName(it) }
                    if (moves.isEmpty()) null else LevelUpMove(level, moves)
                }.ifEmpty { null }
            } else info.levelUpMoves

            val resolvedEgg = if (info.eggMoves == null && jarData.egg.isNotEmpty()) {
                jarData.egg.mapNotNull { resolveMoveByName(it) }.ifEmpty { null }
            } else info.eggMoves

            val resolvedTutor = if (info.tutorMoves == null && jarData.tutor.isNotEmpty()) {
                jarData.tutor.mapNotNull { resolveMoveByName(it) }.ifEmpty { null }
            } else info.tutorMoves

            val resolvedTm = if (info.tmMoves == null && jarData.tm.isNotEmpty()) {
                jarData.tm.mapNotNull { resolveMoveByName(it) }.ifEmpty { null }
            } else info.tmMoves

            enriched[species] = info.copy(
                levelUpMoves = resolvedLevelUp,
                eggMoves = resolvedEgg,
                tutorMoves = resolvedTutor,
                tmMoves = resolvedTm,
            )
            enrichCount++
        }

        if (enrichCount > 0) {
            speciesInfo = enriched
            DebugLog.info("Enriched $enrichCount species with JAR-cached move data")
        }
    }

    private fun resolveMoveByName(name: String): MoveDetail? {
        return try {
            val template = Moves.getByName(name) ?: return null
            MoveDetail(
                name = template.name,
                type = try { template.elementalType.name.lowercase() } catch (_: Exception) { "normal" },
                category = try { template.damageCategory.name } catch (_: Exception) { "PHYSICAL" },
                power = try { template.power.toInt() } catch (_: Exception) { 0 },
                accuracy = try { template.accuracy.toInt() } catch (_: Exception) { 0 },
                pp = try { template.pp } catch (_: Exception) { 0 },
            )
        } catch (_: Exception) { null }
    }

    private fun <T> normalizeMapKeys(map: Map<String, T>): Map<String, T> {
        val result = mutableMapOf<String, T>()
        for ((key, value) in map) {
            val normalized = SpeciesNameNormalizer.normalize(key)
            // If there's a collision, merge lists if applicable
            val existing = result[normalized]
            if (existing != null && existing is List<*> && value is List<*>) {
                @Suppress("UNCHECKED_CAST")
                result[normalized] = (existing + value) as T
            } else {
                result[normalized] = value
            }
        }
        return result
    }

    fun getSpawnsFor(species: String): List<SpawnInfo> = currentQueries().getSpawnsFor(species)

    fun getEvolutionsFrom(species: String): List<EvolutionInfo> = currentQueries().getEvolutionsFrom(species)

    fun getEvolutionsTo(species: String): List<EvolutionInfo> = currentQueries().getEvolutionsTo(species)

    fun getSpeciesInfo(species: String): EvolutionDataLoader.SpeciesBasicInfo? = currentQueries().getSpeciesInfo(species)

    fun getObtainmentFor(species: String): List<ObtainmentInfo> = currentQueries().getObtainmentFor(species)

    fun getFossilsFor(species: String): List<FossilCombo> = currentQueries().getFossilsFor(species)

    fun getSpeciesDroppingItem(itemId: String): List<String> = currentQueries().getSpeciesDroppingItem(itemId)

    fun getJobsFor(species: String): List<JobMatch> = currentQueries().getJobsFor(species)

    fun hasJobRules(): Boolean = currentQueries().hasJobRules()

    fun isForm(species: String): Boolean = currentQueries().isForm(species)

    fun materialFormDecision(species: String): MaterialFormPolicy.Decision? = currentQueries().materialFormDecision(species)

    fun shouldSurfaceSpecies(species: String): Boolean = currentQueries().shouldSurfaceSpecies(species)

    fun getFormsOf(baseSpecies: String): List<EvolutionDataLoader.SpeciesBasicInfo> = currentQueries().getFormsOf(baseSpecies)

    fun getBaseOf(formSpecies: String): String? = currentQueries().getBaseOf(formSpecies)

    fun getSpeciesWithTmMove(moveName: String): List<String> = currentQueries().getSpeciesWithTmMove(moveName)

    fun getRidingFor(species: String): RidingInfo? = currentQueries().getRidingFor(species)
}
