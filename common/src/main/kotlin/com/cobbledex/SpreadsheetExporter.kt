package com.cobbledex

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exports all CobbleDex data as a single .xlsx workbook with multiple sheet tabs.
 * Uses human-readable names as primary columns with raw IDs preserved alongside.
 * Import directly into Google Sheets (File → Import) and all tabs are preserved.
 */
object SpreadsheetExporter {

    data class ExportResult(val filePath: Path, val sheetNames: List<String>, val speciesCount: Int)

    // Per-export caches for frequently-called formatters
    private val speciesNameCache = mutableMapOf<String, String>()
    private val itemNameCache = mutableMapOf<String, String>()

    private fun cachedSpeciesName(raw: String): String =
        speciesNameCache.getOrPut(raw) { formatSpeciesName(raw) }

    private fun cachedItemName(raw: String): String =
        itemNameCache.getOrPut(raw) { SpawnDisplayHelper.resolveItemName(raw) }

    fun export(sender: DiagnosticService.MessageSender): Int {
        val index = SpawnDataIndex

        if (!index.hasData()) {
            sender.send(tr("cobbledex-rei-emi-jei.cmd.no_data_short"))
            return 0
        }

        try {
            sender.send(tr("cobbledex-rei-emi-jei.cmd.export_start"))
            val result = doExport(index)
            sender.send(tr("cobbledex-rei-emi-jei.cmd.export_done", result.sheetNames.size, result.speciesCount))
            sender.send("§7${result.filePath.toAbsolutePath()}")
            for (name in result.sheetNames) {
                sender.send("§7  • $name")
            }
        } catch (e: Exception) {
            sender.send(tr("cobbledex-rei-emi-jei.cmd.export_failed", e.message ?: "unknown"))
            DebugLog.warn("Spreadsheet export failed: ${e.message}")
            return 0
        }

        return 1
    }

    private fun doExport(index: SpawnDataIndex): ExportResult {
        speciesNameCache.clear()
        itemNameCache.clear()

        val gameDir = try {
            com.cobbledex.platform.PlatformHelper.getGameDir()
        } catch (_: Exception) {
            java.nio.file.Paths.get(".")
        }

        val exportDir = gameDir.resolve("cobbledex-export")
        Files.createDirectories(exportDir)

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val filePath = exportDir.resolve("CobbleDex_Data_$timestamp.xlsx")

        val sheets = mutableListOf<SheetData>()

        buildSpeciesOverview(index)?.let { sheets.add(it) }
        buildSpawnData(index)?.let { sheets.add(it) }
        buildEvolutionData(index)?.let { sheets.add(it) }
        buildItemDrops(index)?.let { sheets.add(it) }
        buildMovesets(index)?.let { sheets.add(it) }
        buildLevelUpMoves(index)?.let { sheets.add(it) }
        buildObtainmentData(index)?.let { sheets.add(it) }
        buildFossilData(index)?.let { sheets.add(it) }
        buildAbilities(index)?.let { sheets.add(it) }
        buildTypeChart()?.let { sheets.add(it) }

        writeXlsx(filePath, sheets)
        return ExportResult(filePath, sheets.map { it.name }, index.allSpeciesNames.size)
    }

    // ══════════════════════════════════════════════════════════════════
    //  Sheet builders — human-readable primary text + raw IDs
    // ══════════════════════════════════════════════════════════════════

    private data class SheetData(val name: String, val rows: List<List<String>>)

    private fun buildSpeciesOverview(index: SpawnDataIndex): SheetData? {
        val species = index.speciesInfo
        if (species.isEmpty()) return null

        val rows = mutableListOf<List<String>>()
        rows.add(listOf(
            "Name", "Species ID", "Dex #", "Form", "Base Species",
            "Type 1", "Type 2",
            "HP", "Attack", "Defense", "Sp. Atk", "Sp. Def", "Speed", "BST",
            "Catch Rate", "Height (m)", "Weight (kg)",
            "Ability 1", "Ability 2", "Hidden Ability",
            "Egg Group 1", "Egg Group 2",
            "Male Ratio %", "Egg Cycles",
            "EXP Group", "Base EXP Yield", "Base Friendship",
            "EV HP", "EV Atk", "EV Def", "EV SpA", "EV SpD", "EV Spe",
            "Has Spawns", "Has Drops", "Has Evolutions",
            "Labels", "Shoulder Mountable"
        ))

        val sorted = species.entries.sortedWith(
            compareBy({ it.value.nationalDexNumber.let { d -> if (d > 0) d else Int.MAX_VALUE } }, { it.key })
        )

        for ((key, info) in sorted) {
            val stats = info.baseStats
            val evs = info.evYield
            val abilities = info.abilities ?: emptyList()
            val eggGroups = info.eggGroups ?: emptyList()

            rows.add(listOf(
                cachedSpeciesName(info.name),
                key,
                if (info.nationalDexNumber > 0) info.nationalDexNumber.toString() else "",
                info.formName?.let { titleCase(it) } ?: "",
                info.baseSpeciesName?.let { cachedSpeciesName(it) } ?: "",
                formatTypeName(info.primaryType),
                info.secondaryType?.let { formatTypeName(it) } ?: "",
                stats?.get("hp")?.toString() ?: "",
                stats?.get("attack")?.toString() ?: "",
                stats?.get("defense")?.toString() ?: "",
                stats?.get("spa")?.toString() ?: "",
                stats?.get("spd")?.toString() ?: "",
                stats?.get("spe")?.toString() ?: "",
                info.baseStatTotal?.toString() ?: "",
                info.catchRate.toString(),
                "%.1f".format(info.height),
                "%.1f".format(info.weight),
                abilities.getOrElse(0) { "" }.let { if (it.isNotEmpty()) formatAbilityName(it) else "" },
                abilities.getOrElse(1) { "" }.let { if (it.isNotEmpty()) formatAbilityName(it) else "" },
                info.hiddenAbility?.let { formatAbilityName(it) } ?: "",
                eggGroups.getOrElse(0) { "" }.let { if (it.isNotEmpty()) formatEggGroupName(it) else "" },
                eggGroups.getOrElse(1) { "" }.let { if (it.isNotEmpty()) formatEggGroupName(it) else "" },
                info.maleRatio?.let { "%.1f".format(it * 100) } ?: "",
                info.eggCycles?.toString() ?: "",
                info.experienceGroup?.let { formatExpGroup(it) } ?: "",
                info.baseExperienceYield?.toString() ?: "",
                info.baseFriendship?.toString() ?: "",
                evs?.get("hp")?.toString() ?: "0",
                evs?.get("attack")?.toString() ?: "0",
                evs?.get("defense")?.toString() ?: "0",
                evs?.get("spa")?.toString() ?: "0",
                evs?.get("spd")?.toString() ?: "0",
                evs?.get("spe")?.toString() ?: "0",
                if (index.spawnsBySpecies.containsKey(key)) "Yes" else "No",
                if (info.drops?.isNotEmpty() == true) "Yes" else "No",
                if (index.evolutionsBySpecies.containsKey(key)) "Yes" else "No",
                info.labels?.joinToString("; ") { titleCase(it) } ?: "",
                if (info.shoulderMountable) "Yes" else "No"
            ))
        }

        return SheetData("Species Overview", rows)
    }

    private fun buildSpawnData(index: SpawnDataIndex): SheetData? {
        val spawns = index.spawnsBySpecies
        if (spawns.isEmpty()) return null

        val rows = mutableListOf<List<String>>()
        rows.add(listOf(
            "Pokemon", "Pokemon ID", "Form", "Bucket", "Spawn Weight",
            "Level Range", "Context",
            "Biomes", "Biome IDs",
            "Time", "Weather",
            "Dimensions", "Dimension IDs",
            "Structures", "Structure IDs",
            "Can See Sky", "Min Light", "Max Light",
            "Min Sky Light", "Max Sky Light",
            "Min Y", "Max Y",
            "Nearby Blocks", "Nearby Block IDs",
            "Base Blocks", "Base Block IDs",
            "Moon Phase", "Presets",
            "Fluid", "Fluid ID",
            "Weight Multipliers", "Min Lure Level"
        ))

        for ((_, spawnList) in spawns.entries.sortedBy { it.key }) {
            for (spawn in spawnList) {
                val weatherText = buildString {
                    spawn.weather.isRaining?.let { append(if (it) "Rain" else "No Rain") }
                    spawn.weather.isThundering?.let {
                        if (isNotEmpty()) append(", ")
                        append(if (it) "Thunder" else "No Thunder")
                    }
                }

                val multipliers = spawn.weightMultipliers.joinToString("; ") { wm ->
                    "${wm.multiplier}x ${wm.conditionSummary}".trim()
                }

                rows.add(listOf(
                    cachedSpeciesName(spawn.pokemon),
                    spawn.pokemon,
                    if (spawn.formAspects.isNotBlank()) SpawnDisplayHelper.formatFormAspects(spawn.formAspects) else "",
                    titleCase(spawn.bucket),
                    spawn.weight.toString(),
                    spawn.levelRange,
                    titleCase(spawn.context),
                    spawn.biomes.joinToString("; ") { formatBiomeName(it) },
                    spawn.biomes.joinToString("; "),
                    spawn.timeRange?.let { formatTimeRange(it) } ?: "Any",
                    weatherText.ifEmpty { "Any" },
                    spawn.dimensions.joinToString("; ") { SpawnDisplayHelper.formatDimension(it) },
                    spawn.dimensions.joinToString("; "),
                    spawn.structures.joinToString("; ") { formatStructureName(it) },
                    spawn.structures.joinToString("; "),
                    spawn.canSeeSky?.let { if (it) "Yes" else "No" } ?: "",
                    spawn.minLight?.toString() ?: "",
                    spawn.maxLight?.toString() ?: "",
                    spawn.minSkyLight?.toString() ?: "",
                    spawn.maxSkyLight?.toString() ?: "",
                    spawn.minY?.toString() ?: "",
                    spawn.maxY?.toString() ?: "",
                    spawn.neededNearbyBlocks.joinToString("; ") { formatBlockName(it) },
                    spawn.neededNearbyBlocks.joinToString("; "),
                    spawn.neededBaseBlocks.joinToString("; ") { formatBlockName(it) },
                    spawn.neededBaseBlocks.joinToString("; "),
                    spawn.moonPhase ?: "",
                    spawn.presets.joinToString("; ") { titleCase(it) },
                    spawn.fluid?.let { formatId(it) } ?: "",
                    spawn.fluid ?: "",
                    multipliers,
                    spawn.minLureLevel?.toString() ?: ""
                ))
            }
        }

        return SheetData("Spawn Data", rows)
    }

    private fun buildEvolutionData(index: SpawnDataIndex): SheetData? {
        val evolutions = index.evolutionsBySpecies
        if (evolutions.isEmpty()) return null

        val rows = mutableListOf<List<String>>()
        rows.add(listOf(
            "From", "From ID", "From Form",
            "To", "To ID", "To Form",
            "Method", "Requirements",
            "Required Item", "Required Item ID", "Consumes Held Item"
        ))

        for ((_, evoList) in evolutions.entries.sortedBy { it.key }) {
            for (evo in evoList) {
                rows.add(listOf(
                    cachedSpeciesName(evo.fromSpecies),
                    evo.fromSpecies,
                    evo.fromAspects.joinToString("; ") { formatAspect(it) },
                    cachedSpeciesName(evo.toSpecies),
                    evo.toSpecies,
                    evo.toAspects.joinToString("; ") { formatAspect(it) },
                    titleCase(evo.variant),
                    evo.requirements.joinToString("; ") { it.displayText },
                    evo.requiredContext?.let { cachedItemName(it) } ?: "",
                    evo.requiredContext ?: "",
                    if (evo.consumeHeldItem) "Yes" else "No"
                ))
            }
        }

        return SheetData("Evolutions", rows)
    }

    private fun buildItemDrops(index: SpawnDataIndex): SheetData? {
        val speciesInfo = index.speciesInfo
        val hasDrops = speciesInfo.any { it.value.drops?.isNotEmpty() == true }
        if (!hasDrops) return null

        val rows = mutableListOf<List<String>>()
        rows.add(listOf("Pokemon", "Dex #", "Item", "Item ID", "Drop Chance %", "Quantity"))

        val sorted = speciesInfo.entries
            .filter { it.value.drops?.isNotEmpty() == true }
            .sortedWith(compareBy({ it.value.nationalDexNumber.let { d -> if (d > 0) d else Int.MAX_VALUE } }, { it.key }))

        for ((_, info) in sorted) {
            for (drop in info.drops!!) {
                rows.add(listOf(
                    cachedSpeciesName(info.name),
                    if (info.nationalDexNumber > 0) info.nationalDexNumber.toString() else "",
                    cachedItemName(drop.itemId),
                    drop.itemId,
                    drop.percentage.toString(),
                    drop.quantityRange ?: drop.quantity.toString()
                ))
            }
        }

        return SheetData("Item Drops", rows)
    }

    private fun buildMovesets(index: SpawnDataIndex): SheetData? {
        val speciesInfo = index.speciesInfo
        val hasMoves = speciesInfo.any {
            val i = it.value
            i.levelUpMoves?.isNotEmpty() == true || i.eggMoves?.isNotEmpty() == true ||
                i.tutorMoves?.isNotEmpty() == true || i.tmMoves?.isNotEmpty() == true
        }
        if (!hasMoves) return null

        val rows = mutableListOf<List<String>>()
        rows.add(listOf(
            "Pokemon", "Dex #", "Learn Method", "Level",
            "Move", "Type", "Category", "Power", "Accuracy", "PP"
        ))

        val sorted = speciesInfo.entries.sortedWith(
            compareBy({ it.value.nationalDexNumber.let { d -> if (d > 0) d else Int.MAX_VALUE } }, { it.key })
        )

        for ((_, info) in sorted) {
            info.levelUpMoves?.forEach { lum ->
                for (move in lum.moves) rows.add(moveRow(info, "Level Up", lum.level.toString(), move))
            }
            info.eggMoves?.forEach { move -> rows.add(moveRow(info, "Egg", "", move)) }
            info.tutorMoves?.forEach { move -> rows.add(moveRow(info, "Tutor", "", move)) }
            info.tmMoves?.forEach { move -> rows.add(moveRow(info, "TM", "", move)) }
        }

        return SheetData("All Moves", rows)
    }

    private fun moveRow(info: EvolutionDataLoader.SpeciesBasicInfo, method: String, level: String, move: MoveDetail): List<String> {
        return listOf(
            cachedSpeciesName(info.name),
            if (info.nationalDexNumber > 0) info.nationalDexNumber.toString() else "",
            method, level,
            move.name,
            formatTypeName(move.type),
            titleCase(move.category),
            if (move.power > 0) move.power.toString() else "—",
            if (move.accuracy > 0) move.accuracy.toString() else "—",
            move.pp.toString()
        )
    }

    private fun buildLevelUpMoves(index: SpawnDataIndex): SheetData? {
        val speciesInfo = index.speciesInfo
        val hasLevelUp = speciesInfo.any { it.value.levelUpMoves?.isNotEmpty() == true }
        if (!hasLevelUp) return null

        val rows = mutableListOf<List<String>>()
        rows.add(listOf(
            "Pokemon", "Dex #", "Level",
            "Move", "Type", "Category", "Power", "Accuracy", "PP"
        ))

        val sorted = speciesInfo.entries
            .filter { it.value.levelUpMoves?.isNotEmpty() == true }
            .sortedWith(compareBy({ it.value.nationalDexNumber.let { d -> if (d > 0) d else Int.MAX_VALUE } }, { it.key }))

        for ((_, info) in sorted) {
            for (lum in (info.levelUpMoves ?: continue).sortedBy { it.level }) {
                for (move in lum.moves) {
                    rows.add(listOf(
                        cachedSpeciesName(info.name),
                        if (info.nationalDexNumber > 0) info.nationalDexNumber.toString() else "",
                        lum.level.toString(),
                        move.name,
                        formatTypeName(move.type),
                        titleCase(move.category),
                        if (move.power > 0) move.power.toString() else "—",
                        if (move.accuracy > 0) move.accuracy.toString() else "—",
                        move.pp.toString()
                    ))
                }
            }
        }

        return SheetData("Level-Up Moves", rows)
    }

    private fun buildObtainmentData(index: SpawnDataIndex): SheetData? {
        val obtainment = index.obtainmentBySpecies
        if (obtainment.isEmpty()) return null

        val rows = mutableListOf<List<String>>()
        rows.add(listOf(
            "Pokemon", "Pokemon ID", "Form", "Method", "Description",
            "Required Items", "Item IDs",
            "Block", "Block ID",
            "Structure", "Structure ID",
            "Dimension", "Dimension ID",
            "Notes", "Source"
        ))

        for ((_, obtainList) in obtainment.entries.sortedBy { it.key }) {
            for (info in obtainList) {
                rows.add(listOf(
                    cachedSpeciesName(info.pokemon),
                    info.pokemon,
                    if (info.formAspects.isNotBlank()) SpawnDisplayHelper.formatFormAspects(info.formAspects) else "",
                    titleCase(info.method),
                    info.description,
                    info.items.joinToString("; ") { cachedItemName(it) },
                    info.items.joinToString("; "),
                    info.block?.let { formatBlockName(it) } ?: "",
                    info.block ?: "",
                    info.structure?.let { formatStructureName(it) } ?: "",
                    info.structure ?: "",
                    info.dimension?.let { SpawnDisplayHelper.formatDimension(it) } ?: "",
                    info.dimension ?: "",
                    info.notes.joinToString("; "),
                    titleCase(info.source)
                ))
            }
        }

        return SheetData("Special Obtainment", rows)
    }

    private fun buildFossilData(index: SpawnDataIndex): SheetData? {
        val fossils = index.fossilsBySpecies
        if (fossils.isEmpty()) return null

        val rows = mutableListOf<List<String>>()
        rows.add(listOf("Pokemon", "Pokemon ID", "Fossil Items", "Fossil Item IDs", "Extra Tags"))

        for ((_, fossilList) in fossils.entries.sortedBy { it.key }) {
            for (fossil in fossilList) {
                rows.add(listOf(
                    cachedSpeciesName(fossil.resultSpecies),
                    fossil.resultSpecies,
                    fossil.fossilItems.joinToString("; ") { cachedItemName(it) },
                    fossil.fossilItems.joinToString("; "),
                    fossil.extraTags ?: ""
                ))
            }
        }

        return SheetData("Fossils", rows)
    }

    private fun buildAbilities(index: SpawnDataIndex): SheetData? {
        val speciesInfo = index.speciesInfo
        val hasAbilities = speciesInfo.any { it.value.abilities?.isNotEmpty() == true }
        if (!hasAbilities) return null

        val rows = mutableListOf<List<String>>()
        rows.add(listOf("Pokemon", "Dex #", "Type 1", "Type 2", "Ability 1", "Ability 2", "Hidden Ability"))

        val sorted = speciesInfo.entries
            .filter { it.value.abilities?.isNotEmpty() == true || it.value.hiddenAbility != null }
            .sortedWith(compareBy({ it.value.nationalDexNumber.let { d -> if (d > 0) d else Int.MAX_VALUE } }, { it.key }))

        for ((_, info) in sorted) {
            val abilities = info.abilities ?: emptyList()
            rows.add(listOf(
                cachedSpeciesName(info.name),
                if (info.nationalDexNumber > 0) info.nationalDexNumber.toString() else "",
                formatTypeName(info.primaryType),
                info.secondaryType?.let { formatTypeName(it) } ?: "",
                abilities.getOrElse(0) { "" }.let { if (it.isNotEmpty()) formatAbilityName(it) else "" },
                abilities.getOrElse(1) { "" }.let { if (it.isNotEmpty()) formatAbilityName(it) else "" },
                info.hiddenAbility?.let { formatAbilityName(it) } ?: ""
            ))
        }

        return SheetData("Abilities", rows)
    }

    private fun buildTypeChart(): SheetData {
        val types = listOf(
            "Normal", "Fire", "Water", "Electric", "Grass", "Ice",
            "Fighting", "Poison", "Ground", "Flying", "Psychic", "Bug",
            "Rock", "Ghost", "Dragon", "Dark", "Steel", "Fairy"
        )

        val chart = mapOf(
            "Normal"   to mapOf("Rock" to 0.5, "Ghost" to 0.0, "Steel" to 0.5),
            "Fire"     to mapOf("Fire" to 0.5, "Water" to 0.5, "Grass" to 2.0, "Ice" to 2.0, "Bug" to 2.0, "Rock" to 0.5, "Dragon" to 0.5, "Steel" to 2.0),
            "Water"    to mapOf("Fire" to 2.0, "Water" to 0.5, "Grass" to 0.5, "Ground" to 2.0, "Rock" to 2.0, "Dragon" to 0.5),
            "Electric" to mapOf("Water" to 2.0, "Electric" to 0.5, "Grass" to 0.5, "Ground" to 0.0, "Flying" to 2.0, "Dragon" to 0.5),
            "Grass"    to mapOf("Fire" to 0.5, "Water" to 2.0, "Grass" to 0.5, "Poison" to 0.5, "Ground" to 2.0, "Flying" to 0.5, "Bug" to 0.5, "Rock" to 2.0, "Dragon" to 0.5, "Steel" to 0.5),
            "Ice"      to mapOf("Fire" to 0.5, "Water" to 0.5, "Grass" to 2.0, "Ice" to 0.5, "Ground" to 2.0, "Flying" to 2.0, "Dragon" to 2.0, "Steel" to 0.5),
            "Fighting" to mapOf("Normal" to 2.0, "Ice" to 2.0, "Poison" to 0.5, "Flying" to 0.5, "Psychic" to 0.5, "Bug" to 0.5, "Rock" to 2.0, "Ghost" to 0.0, "Dark" to 2.0, "Steel" to 2.0, "Fairy" to 0.5),
            "Poison"   to mapOf("Grass" to 2.0, "Poison" to 0.5, "Ground" to 0.5, "Rock" to 0.5, "Ghost" to 0.5, "Steel" to 0.0, "Fairy" to 2.0),
            "Ground"   to mapOf("Fire" to 2.0, "Electric" to 2.0, "Grass" to 0.5, "Poison" to 2.0, "Flying" to 0.0, "Bug" to 0.5, "Rock" to 2.0, "Steel" to 2.0),
            "Flying"   to mapOf("Electric" to 0.5, "Grass" to 2.0, "Fighting" to 2.0, "Bug" to 2.0, "Rock" to 0.5, "Steel" to 0.5),
            "Psychic"  to mapOf("Fighting" to 2.0, "Poison" to 2.0, "Psychic" to 0.5, "Dark" to 0.0, "Steel" to 0.5),
            "Bug"      to mapOf("Fire" to 0.5, "Grass" to 2.0, "Fighting" to 0.5, "Poison" to 0.5, "Flying" to 0.5, "Psychic" to 2.0, "Ghost" to 0.5, "Dark" to 2.0, "Steel" to 0.5, "Fairy" to 0.5),
            "Rock"     to mapOf("Fire" to 2.0, "Ice" to 2.0, "Fighting" to 0.5, "Ground" to 0.5, "Flying" to 2.0, "Bug" to 2.0, "Steel" to 0.5),
            "Ghost"    to mapOf("Normal" to 0.0, "Psychic" to 2.0, "Ghost" to 2.0, "Dark" to 0.5),
            "Dragon"   to mapOf("Dragon" to 2.0, "Steel" to 0.5, "Fairy" to 0.0),
            "Dark"     to mapOf("Fighting" to 0.5, "Psychic" to 2.0, "Ghost" to 2.0, "Dark" to 0.5, "Fairy" to 0.5),
            "Steel"    to mapOf("Fire" to 0.5, "Water" to 0.5, "Electric" to 0.5, "Ice" to 2.0, "Rock" to 2.0, "Steel" to 0.5, "Fairy" to 2.0),
            "Fairy"    to mapOf("Fire" to 0.5, "Fighting" to 2.0, "Poison" to 0.5, "Dragon" to 2.0, "Dark" to 2.0, "Steel" to 0.5)
        )

        val rows = mutableListOf<List<String>>()
        rows.add(listOf("Attacker \\ Defender") + types)

        for (attacker in types) {
            val matchups = chart[attacker] ?: emptyMap()
            rows.add(listOf(attacker) + types.map { defender ->
                when (val eff = matchups[defender] ?: 1.0) {
                    0.0 -> "0"; 0.5 -> "0.5"; 1.0 -> "1"; 2.0 -> "2"; else -> eff.toString()
                }
            })
        }

        return SheetData("Type Chart", rows)
    }

    // ══════════════════════════════════════════════════════════════════
    //  XLSX writer — OOXML SpreadsheetML with formatting
    // ══════════════════════════════════════════════════════════════════

    private fun writeXlsx(path: Path, sheets: List<SheetData>) {
        val stringPool = LinkedHashMap<String, Int>()
        for (sheet in sheets) {
            for (row in sheet.rows) {
                for (cell in row) {
                    if (cell.isNotEmpty() && !isNumeric(cell)) {
                        stringPool.putIfAbsent(cell, stringPool.size)
                    }
                }
            }
        }

        val buf = ByteArrayOutputStream()
        ZipOutputStream(buf).use { zip ->
            zip.addXml("[Content_Types].xml", buildContentTypes(sheets.size))
            zip.addXml("_rels/.rels", RELS_ROOT)
            zip.addXml("xl/workbook.xml", buildWorkbook(sheets))
            zip.addXml("xl/_rels/workbook.xml.rels", buildWorkbookRels(sheets.size))
            zip.addXml("xl/styles.xml", STYLES_XML)
            zip.addXml("xl/sharedStrings.xml", buildSharedStrings(stringPool))

            for ((i, sheet) in sheets.withIndex()) {
                zip.addXml("xl/worksheets/sheet${i + 1}.xml", buildSheetXml(sheet, stringPool))
            }
        }

        Files.write(path, buf.toByteArray())
    }

    private fun ZipOutputStream.addXml(entryName: String, content: String) {
        putNextEntry(ZipEntry(entryName))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun isNumeric(s: String): Boolean {
        if (s.isEmpty()) return false
        return s.matches(Regex("""-?\d+(\.\d+)?"""))
    }

    private fun colLetter(col: Int): String {
        var c = col
        val sb = StringBuilder()
        while (true) {
            sb.insert(0, ('A' + c % 26))
            c = c / 26 - 1
            if (c < 0) break
        }
        return sb.toString()
    }

    private fun xmlEscape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun autoColumnWidths(sheet: SheetData): List<Double> {
        val numCols = sheet.rows.maxOfOrNull { it.size } ?: return emptyList()
        return (0 until numCols).map { col ->
            val maxLen = sheet.rows.maxOf { row -> row.getOrElse(col) { "" }.length }
            (maxLen * 1.1 + 3).coerceIn(8.0, 55.0)
        }
    }

    // ── XML templates ────────────────────────────────────────────────

    private fun buildContentTypes(sheetCount: Int): String {
        val sheetOverrides = (1..sheetCount).joinToString("\n") {
            """<Override PartName="/xl/worksheets/sheet$it.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
<Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
$sheetOverrides
</Types>"""
    }

    private val RELS_ROOT = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun buildWorkbook(sheets: List<SheetData>): String {
        val sheetEntries = sheets.withIndex().joinToString("\n") { (i, s) ->
            """<sheet name="${xmlEscape(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets>
$sheetEntries
</sheets>
</workbook>"""
    }

    private fun buildWorkbookRels(sheetCount: Int): String {
        val rels = (1..sheetCount).joinToString("\n") {
            """<Relationship Id="rId$it" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$it.xml"/>"""
        }
        val stylesId = sheetCount + 1
        val ssiId = sheetCount + 2
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
$rels
<Relationship Id="rId$stylesId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
<Relationship Id="rId$ssiId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
</Relationships>"""
    }

    // Style 0 = default body, Style 1 = bold, Style 2 = bold white on blue (header)
    private val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="3">
<font><sz val="11"/><name val="Calibri"/></font>
<font><b/><sz val="11"/><name val="Calibri"/></font>
<font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
</fonts>
<fills count="3">
<fill><patternFill patternType="none"/></fill>
<fill><patternFill patternType="gray125"/></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FF4472C4"/></patternFill></fill>
</fills>
<borders count="1">
<border><left/><right/><top/><bottom/><diagonal/></border>
</borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="3">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
<xf numFmtId="0" fontId="2" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
</cellXfs>
</styleSheet>"""

    private fun buildSharedStrings(pool: LinkedHashMap<String, Int>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${pool.size}" uniqueCount="${pool.size}">""")
        for (str in pool.keys) {
            sb.append("<si><t>").append(xmlEscape(str)).append("</t></si>")
        }
        sb.append("</sst>")
        return sb.toString()
    }

    private fun buildSheetXml(sheet: SheetData, stringPool: Map<String, Int>): String {
        val sb = StringBuilder()
        val numCols = sheet.rows.maxOfOrNull { it.size } ?: 0
        val widths = autoColumnWidths(sheet)

        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")

        // Freeze header row
        sb.append("<sheetViews><sheetView workbookViewId=\"0\">")
        sb.append("""<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>""")
        sb.append("</sheetView></sheetViews>")

        // Auto-calculated column widths
        if (widths.isNotEmpty()) {
            sb.append("<cols>")
            for ((i, w) in widths.withIndex()) {
                val colNum = i + 1
                sb.append("""<col min="$colNum" max="$colNum" width="${"%.2f".format(w)}" customWidth="1"/>""")
            }
            sb.append("</cols>")
        }

        sb.append("<sheetData>")

        for ((rowIdx, row) in sheet.rows.withIndex()) {
            val rowNum = rowIdx + 1
            if (rowIdx == 0) {
                sb.append("""<row r="$rowNum" ht="20" customHeight="1">""")
            } else {
                sb.append("""<row r="$rowNum">""")
            }
            for ((colIdx, cell) in row.withIndex()) {
                val ref = "${colLetter(colIdx)}$rowNum"
                if (cell.isEmpty()) continue

                val style = if (rowIdx == 0) " s=\"2\"" else ""

                if (isNumeric(cell)) {
                    sb.append("""<c r="$ref"$style><v>$cell</v></c>""")
                } else {
                    val ssi = stringPool[cell] ?: continue
                    sb.append("""<c r="$ref" t="s"$style><v>$ssi</v></c>""")
                }
            }
            sb.append("</row>")
        }

        sb.append("</sheetData>")

        // Auto-filter dropdowns on header row
        if (numCols > 0 && sheet.rows.size > 1) {
            val lastCol = colLetter(numCols - 1)
            sb.append("""<autoFilter ref="A1:${lastCol}${sheet.rows.size}"/>""")
        }

        sb.append("</worksheet>")
        return sb.toString()
    }
}
