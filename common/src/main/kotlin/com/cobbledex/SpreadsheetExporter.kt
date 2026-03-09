package com.cobbledex

import net.minecraft.world.item.ItemStack
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exports all CobbleDex data as a single .xlsx workbook with:
 * - Human-readable names (no raw IDs)
 * - 32x32 Pokémon/item icons embedded in every applicable sheet
 * - Styled blue headers, 13pt font, auto-fit column widths
 * - Frozen panes, auto-filter, per-sheet icon drawings
 * Import into Google Sheets (File → Import) and all tabs + icons are preserved.
 */
object SpreadsheetExporter {

    data class ExportResult(val filePath: Path, val sheetNames: List<String>, val speciesCount: Int)

    private val speciesNameCache = mutableMapOf<String, String>()
    private val itemNameCache = mutableMapOf<String, String>()

    private fun pokemon(raw: String): String =
        speciesNameCache.getOrPut(raw) { formatSpeciesName(raw) }

    private fun item(raw: String): String =
        itemNameCache.getOrPut(raw) { SpawnDisplayHelper.resolveItemName(raw) }

    private fun pct(value: Float): String {
        val formatted = if (value == value.toLong().toFloat()) value.toLong().toString() else "%.1f".format(value)
        return "$formatted%"
    }

    fun export(sender: DiagnosticService.MessageSender): Int {
        val index = SpawnDataIndex

        if (!index.hasData()) {
            sender.send(tr("cobbledex-rei-emi-jei.cmd.no_data_short"))
            return 0
        }

        try {
            sender.send(tr("cobbledex-rei-emi-jei.cmd.export_start"))
            val result = doExport(index, sender)
            sender.send(tr("cobbledex-rei-emi-jei.cmd.export_done", result.sheetNames.size, result.speciesCount))
            sender.send("§7${result.filePath.toAbsolutePath()}")
            for (name in result.sheetNames) sender.send("§7  • $name")
        } catch (e: Exception) {
            sender.send(tr("cobbledex-rei-emi-jei.cmd.export_failed", e.message ?: "unknown"))
            DebugLog.warn("Spreadsheet export failed: ${e.message}")
            e.printStackTrace()
            return 0
        } finally {
            IconCapture.cleanup()
        }

        return 1
    }

    private fun doExport(index: SpawnDataIndex, sender: DiagnosticService.MessageSender): ExportResult {
        speciesNameCache.clear()
        itemNameCache.clear()

        val gameDir = try {
            com.cobbledex.platform.PlatformHelper.getGameDir()
        } catch (_: Exception) { java.nio.file.Paths.get(".") }

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

        // Capture all needed icons
        sender.send("§7Rendering icons...")
        val iconRegistry = captureAllIcons(sheets)

        writeXlsx(filePath, sheets, iconRegistry)
        return ExportResult(filePath, sheets.map { it.name }, index.allSpeciesNames.size)
    }

    // ══════════════════════════════════════════════════════════════════
    //  Icon registry — deduplicated icon capture
    // ══════════════════════════════════════════════════════════════════

    /** Maps a cache key → PNG bytes. Built once, shared across all sheets. */
    private data class IconRegistry(
        val pngByKey: Map<String, ByteArray>,
        val mediaIndex: Map<String, Int> // key → media file index (1-based)
    )

    private fun captureAllIcons(sheets: List<SheetData>): IconRegistry {
        val keysNeeded = mutableSetOf<String>()
        for (sheet in sheets) {
            for (icon in sheet.icons) keysNeeded.add(icon.cacheKey)
        }

        val pngByKey = mutableMapOf<String, ByteArray>()
        var idx = 0
        val mediaIndex = mutableMapOf<String, Int>()
        var failures = 0

        for (key in keysNeeded.sorted()) {
            val png = captureIcon(key)
            if (png != null) {
                idx++
                pngByKey[key] = png
                mediaIndex[key] = idx
            } else {
                failures++
            }
        }

        DebugLog.info("Icon capture: ${pngByKey.size} succeeded, $failures failed out of ${keysNeeded.size} unique keys")
        return IconRegistry(pngByKey, mediaIndex)
    }

    private fun captureIcon(cacheKey: String): ByteArray? {
        val stack: ItemStack? = when {
            cacheKey.startsWith("species:") -> {
                val species = cacheKey.removePrefix("species:")
                PokemonItemCache.getItem(species)
            }
            cacheKey.startsWith("item:") -> {
                val itemId = cacheKey.removePrefix("item:")
                SpawnDisplayHelper.resolveItemStack(itemId).let { if (it.isEmpty) null else it }
            }
            else -> null
        }
        if (stack == null) {
            DebugLog.warn("Icon resolve failed — no ItemStack for key: $cacheKey")
            return null
        }
        return IconCapture.captureItemToPng(stack)
    }

    // ══════════════════════════════════════════════════════════════════
    //  Sheet data model
    // ══════════════════════════════════════════════════════════════════

    private data class CellIcon(val cacheKey: String, val row: Int, val col: Int)

    private data class SheetData(
        val name: String,
        val rows: List<List<String>>,
        val icons: List<CellIcon> = emptyList(),
        val hasIcons: Boolean = icons.isNotEmpty(),
        val iconColumns: Set<Int> = if (icons.isNotEmpty()) setOf(0) else emptySet()
    )

    // ══════════════════════════════════════════════════════════════════
    //  Sheet builders — Dex # first, no IDs, % symbols, icons
    // ══════════════════════════════════════════════════════════════════

    private fun buildSpeciesOverview(index: SpawnDataIndex): SheetData? {
        val species = index.speciesInfo
        if (species.isEmpty()) return null

        val rows = mutableListOf<List<String>>()
        val icons = mutableListOf<CellIcon>()
        val iconCol = 0

        rows.add(listOf(
            "", "Dex #", "Name", "Form", "Base Species",
            "Type 1", "Type 2",
            "HP", "Attack", "Defense", "Sp. Atk", "Sp. Def", "Speed", "BST",
            "Catch Rate", "Height (m)", "Weight (kg)",
            "Ability 1", "Ability 2", "Hidden Ability",
            "Egg Group 1", "Egg Group 2",
            "Male Ratio", "Egg Cycles",
            "EXP Group", "Base EXP Yield", "Base Friendship",
            "EV HP", "EV Atk", "EV Def", "EV SpA", "EV SpD", "EV Spe",
            "Has Spawns", "Has Drops", "Has Evolutions",
            "Labels", "Shoulder Mountable"
        ))

        val sorted = species.entries.sortedWith(
            compareBy({ it.value.nationalDexNumber.let { d -> if (d > 0) d else Int.MAX_VALUE } }, { it.key })
        )

        for ((key, info) in sorted) {
            val rowIdx = rows.size
            val stats = info.baseStats
            val evs = info.evYield
            val abilities = info.abilities ?: emptyList()
            val eggGroups = info.eggGroups ?: emptyList()

            icons.add(CellIcon("species:${info.name}", rowIdx, iconCol))

            rows.add(listOf(
                "", // icon column
                if (info.nationalDexNumber > 0) info.nationalDexNumber.toString() else "",
                pokemon(info.name),
                info.formName?.let { titleCase(it) } ?: "",
                info.baseSpeciesName?.let { pokemon(it) } ?: "",
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
                info.maleRatio?.let { "%.1f%%".format(it * 100) } ?: "",
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

        return SheetData("Species Overview", rows, icons)
    }

    private fun buildSpawnData(index: SpawnDataIndex): SheetData? {
        val spawns = index.spawnsBySpecies
        if (spawns.isEmpty()) return null

        val rows = mutableListOf<List<String>>()
        val icons = mutableListOf<CellIcon>()

        rows.add(listOf(
            "", "Pokemon", "Form", "Bucket", "Spawn Weight",
            "Level Range", "Context",
            "Biomes", "Time", "Weather",
            "Dimensions", "Structures",
            "Can See Sky", "Min Light", "Max Light",
            "Min Sky Light", "Max Sky Light",
            "Min Y", "Max Y",
            "Nearby Blocks", "Base Blocks",
            "Moon Phase", "Presets",
            "Fluid", "Weight Multipliers", "Min Lure Level"
        ))

        for ((_, spawnList) in spawns.entries.sortedBy { it.key }) {
            for (spawn in spawnList) {
                val rowIdx = rows.size
                icons.add(CellIcon("species:${spawn.pokemon}", rowIdx, 0))

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
                    "", // icon
                    pokemon(spawn.pokemon),
                    if (spawn.formAspects.isNotBlank()) SpawnDisplayHelper.formatFormAspects(spawn.formAspects) else "",
                    titleCase(spawn.bucket),
                    spawn.weight.toString(),
                    spawn.levelRange,
                    titleCase(spawn.context),
                    spawn.biomes.joinToString("; ") { formatBiomeName(it) },
                    spawn.timeRange?.let { formatTimeRange(it) } ?: "Any",
                    weatherText.ifEmpty { "Any" },
                    spawn.dimensions.joinToString("; ") { SpawnDisplayHelper.formatDimension(it) },
                    spawn.structures.joinToString("; ") { formatStructureName(it) },
                    spawn.canSeeSky?.let { if (it) "Yes" else "No" } ?: "",
                    spawn.minLight?.toString() ?: "",
                    spawn.maxLight?.toString() ?: "",
                    spawn.minSkyLight?.toString() ?: "",
                    spawn.maxSkyLight?.toString() ?: "",
                    spawn.minY?.toString() ?: "",
                    spawn.maxY?.toString() ?: "",
                    spawn.neededNearbyBlocks.joinToString("; ") { formatBlockName(it) },
                    spawn.neededBaseBlocks.joinToString("; ") { formatBlockName(it) },
                    spawn.moonPhase ?: "",
                    spawn.presets.joinToString("; ") { titleCase(it) },
                    spawn.fluid?.let { formatId(it) } ?: "",
                    multipliers,
                    spawn.minLureLevel?.toString() ?: ""
                ))
            }
        }

        return SheetData("Spawn Data", rows, icons)
    }

    private fun buildEvolutionData(index: SpawnDataIndex): SheetData? {
        val evolutions = index.evolutionsBySpecies
        if (evolutions.isEmpty()) return null

        val rows = mutableListOf<List<String>>()
        val icons = mutableListOf<CellIcon>()

        rows.add(listOf(
            "", "From", "From Form",
            "To", "To Form",
            "Method", "Requirements",
            "Required Item", "Consumes Held Item"
        ))

        for ((_, evoList) in evolutions.entries.sortedBy { it.key }) {
            for (evo in evoList) {
                val rowIdx = rows.size
                icons.add(CellIcon("species:${evo.fromSpecies}", rowIdx, 0))

                rows.add(listOf(
                    "", // icon
                    pokemon(evo.fromSpecies),
                    evo.fromAspects.joinToString("; ") { formatAspect(it) },
                    pokemon(evo.toSpecies),
                    evo.toAspects.joinToString("; ") { formatAspect(it) },
                    titleCase(evo.variant),
                    evo.requirements.joinToString("; ") { it.displayText },
                    evo.requiredContext?.let { item(it) } ?: "",
                    if (evo.consumeHeldItem) "Yes" else "No"
                ))
            }
        }

        return SheetData("Evolutions", rows, icons)
    }

    private fun buildItemDrops(index: SpawnDataIndex): SheetData? {
        val speciesInfo = index.speciesInfo
        val hasDrops = speciesInfo.any { it.value.drops?.isNotEmpty() == true }
        if (!hasDrops) return null

        val rows = mutableListOf<List<String>>()
        val icons = mutableListOf<CellIcon>()

        rows.add(listOf(
            "", "Dex #", "Pokemon", "", "Item", "Drop Chance", "Quantity"
        ))

        val sorted = speciesInfo.entries
            .filter { it.value.drops?.isNotEmpty() == true }
            .sortedWith(compareBy({ it.value.nationalDexNumber.let { d -> if (d > 0) d else Int.MAX_VALUE } }, { it.key }))

        for ((_, info) in sorted) {
            for (drop in info.drops!!) {
                val rowIdx = rows.size
                icons.add(CellIcon("species:${info.name}", rowIdx, 0))
                icons.add(CellIcon("item:${drop.itemId}", rowIdx, 3))

                // Normalize quantity: always use "min–max" format for ranges, plain number otherwise
                val qty = if (drop.quantityRange != null) {
                    drop.quantityRange.replace("-", "–")
                } else {
                    drop.quantity.toString()
                }

                rows.add(listOf(
                    "", // species icon
                    if (info.nationalDexNumber > 0) info.nationalDexNumber.toString() else "",
                    pokemon(info.name),
                    "", // item icon
                    item(drop.itemId),
                    pct(drop.percentage),
                    qty
                ))
            }
        }

        return SheetData("Item Drops", rows, icons, iconColumns = setOf(0, 3))
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
        val icons = mutableListOf<CellIcon>()

        rows.add(listOf(
            "", "Dex #", "Pokemon", "Learn Method", "Level",
            "Move", "Type", "Category", "Power", "Accuracy", "PP"
        ))

        val sorted = speciesInfo.entries.sortedWith(
            compareBy({ it.value.nationalDexNumber.let { d -> if (d > 0) d else Int.MAX_VALUE } }, { it.key })
        )

        for ((_, info) in sorted) {
            fun addMove(method: String, level: String, move: MoveDetail) {
                val rowIdx = rows.size
                icons.add(CellIcon("species:${info.name}", rowIdx, 0))
                rows.add(listOf(
                    "", // icon
                    if (info.nationalDexNumber > 0) info.nationalDexNumber.toString() else "",
                    pokemon(info.name),
                    method, level,
                    move.name,
                    formatTypeName(move.type),
                    titleCase(move.category),
                    if (move.power > 0) move.power.toString() else "—",
                    if (move.accuracy > 0) "${move.accuracy}%" else "—",
                    move.pp.toString()
                ))
            }

            info.levelUpMoves?.forEach { lum ->
                for (move in lum.moves) addMove("Level Up", lum.level.toString(), move)
            }
            info.eggMoves?.forEach { move -> addMove("Egg", "", move) }
            info.tutorMoves?.forEach { move -> addMove("Tutor", "", move) }
            info.tmMoves?.forEach { move -> addMove("TM", "", move) }
        }

        return SheetData("All Moves", rows, icons)
    }

    private fun buildLevelUpMoves(index: SpawnDataIndex): SheetData? {
        val speciesInfo = index.speciesInfo
        val hasLevelUp = speciesInfo.any { it.value.levelUpMoves?.isNotEmpty() == true }
        if (!hasLevelUp) return null

        val rows = mutableListOf<List<String>>()
        val icons = mutableListOf<CellIcon>()

        rows.add(listOf(
            "", "Dex #", "Pokemon", "Level",
            "Move", "Type", "Category", "Power", "Accuracy", "PP"
        ))

        val sorted = speciesInfo.entries
            .filter { it.value.levelUpMoves?.isNotEmpty() == true }
            .sortedWith(compareBy({ it.value.nationalDexNumber.let { d -> if (d > 0) d else Int.MAX_VALUE } }, { it.key }))

        for ((_, info) in sorted) {
            for (lum in (info.levelUpMoves ?: continue).sortedBy { it.level }) {
                for (move in lum.moves) {
                    val rowIdx = rows.size
                    icons.add(CellIcon("species:${info.name}", rowIdx, 0))
                    rows.add(listOf(
                        "", // icon
                        if (info.nationalDexNumber > 0) info.nationalDexNumber.toString() else "",
                        pokemon(info.name),
                        lum.level.toString(),
                        move.name,
                        formatTypeName(move.type),
                        titleCase(move.category),
                        if (move.power > 0) move.power.toString() else "—",
                        if (move.accuracy > 0) "${move.accuracy}%" else "—",
                        move.pp.toString()
                    ))
                }
            }
        }

        return SheetData("Level-Up Moves", rows, icons)
    }

    private fun buildObtainmentData(index: SpawnDataIndex): SheetData? {
        val obtainment = index.obtainmentBySpecies
        if (obtainment.isEmpty()) return null

        val rows = mutableListOf<List<String>>()
        val icons = mutableListOf<CellIcon>()

        rows.add(listOf(
            "", "Pokemon", "Form", "Method", "Description",
            "Required Items", "Block", "Structure", "Dimension",
            "Notes", "Source"
        ))

        for ((_, obtainList) in obtainment.entries.sortedBy { it.key }) {
            for (info in obtainList) {
                val rowIdx = rows.size
                icons.add(CellIcon("species:${info.pokemon}", rowIdx, 0))

                rows.add(listOf(
                    "", // icon
                    pokemon(info.pokemon),
                    if (info.formAspects.isNotBlank()) SpawnDisplayHelper.formatFormAspects(info.formAspects) else "",
                    titleCase(info.method),
                    info.description,
                    info.items.joinToString("; ") { item(it) },
                    info.block?.let { formatBlockName(it) } ?: "",
                    info.structure?.let { formatStructureName(it) } ?: "",
                    info.dimension?.let { SpawnDisplayHelper.formatDimension(it) } ?: "",
                    info.notes.joinToString("; "),
                    titleCase(info.source)
                ))
            }
        }

        return SheetData("Special Obtainment", rows, icons)
    }

    private fun buildFossilData(index: SpawnDataIndex): SheetData? {
        val fossils = index.fossilsBySpecies
        if (fossils.isEmpty()) return null

        val rows = mutableListOf<List<String>>()
        val icons = mutableListOf<CellIcon>()

        rows.add(listOf("", "Pokemon", "Fossil Items", "Extra Tags"))

        for ((_, fossilList) in fossils.entries.sortedBy { it.key }) {
            for (fossil in fossilList) {
                val rowIdx = rows.size
                icons.add(CellIcon("species:${fossil.resultSpecies}", rowIdx, 0))

                rows.add(listOf(
                    "", // icon
                    pokemon(fossil.resultSpecies),
                    fossil.fossilItems.joinToString("; ") { item(it) },
                    fossil.extraTags ?: ""
                ))
            }
        }

        return SheetData("Fossils", rows, icons)
    }

    private fun buildAbilities(index: SpawnDataIndex): SheetData? {
        val speciesInfo = index.speciesInfo
        val hasAbilities = speciesInfo.any { it.value.abilities?.isNotEmpty() == true }
        if (!hasAbilities) return null

        val rows = mutableListOf<List<String>>()
        val icons = mutableListOf<CellIcon>()

        rows.add(listOf("", "Dex #", "Pokemon", "Type 1", "Type 2", "Ability 1", "Ability 2", "Hidden Ability"))

        val sorted = speciesInfo.entries
            .filter { it.value.abilities?.isNotEmpty() == true || it.value.hiddenAbility != null }
            .sortedWith(compareBy({ it.value.nationalDexNumber.let { d -> if (d > 0) d else Int.MAX_VALUE } }, { it.key }))

        for ((_, info) in sorted) {
            val rowIdx = rows.size
            icons.add(CellIcon("species:${info.name}", rowIdx, 0))

            val abilities = info.abilities ?: emptyList()
            rows.add(listOf(
                "", // icon
                if (info.nationalDexNumber > 0) info.nationalDexNumber.toString() else "",
                pokemon(info.name),
                formatTypeName(info.primaryType),
                info.secondaryType?.let { formatTypeName(it) } ?: "",
                abilities.getOrElse(0) { "" }.let { if (it.isNotEmpty()) formatAbilityName(it) else "" },
                abilities.getOrElse(1) { "" }.let { if (it.isNotEmpty()) formatAbilityName(it) else "" },
                info.hiddenAbility?.let { formatAbilityName(it) } ?: ""
            ))
        }

        return SheetData("Abilities", rows, icons)
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

        return SheetData("Type Chart", rows) // no icons
    }

    // ══════════════════════════════════════════════════════════════════
    //  XLSX writer — OOXML SpreadsheetML + DrawingML icons
    // ══════════════════════════════════════════════════════════════════

    private const val ICON_COL_WIDTH = 5.5
    private const val DATA_ROW_HEIGHT_ICON = 30.0    // rows with icons
    private const val DATA_ROW_HEIGHT_PLAIN = 18.0   // rows without icons
    private const val HEADER_ROW_HEIGHT = 24.0
    private const val EMU_PER_PX = 9525              // Excel EMU units per pixel
    private const val ICON_EMU = IconCapture.ICON_SIZE * EMU_PER_PX // 32 * 9525 = 304800
    private const val ICON_OFFSET_EMU = 19050        // 2px padding

    private fun writeXlsx(path: Path, sheets: List<SheetData>, icons: IconRegistry) {
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
            zip.addXml("[Content_Types].xml", buildContentTypes(sheets, icons))
            zip.addXml("_rels/.rels", RELS_ROOT)
            zip.addXml("xl/workbook.xml", buildWorkbook(sheets))
            zip.addXml("xl/_rels/workbook.xml.rels", buildWorkbookRels(sheets.size))
            zip.addXml("xl/styles.xml", STYLES_XML)
            zip.addXml("xl/sharedStrings.xml", buildSharedStrings(stringPool))

            // Write media images
            for ((key, png) in icons.pngByKey) {
                val idx = icons.mediaIndex[key] ?: continue
                zip.putNextEntry(ZipEntry("xl/media/image$idx.png"))
                zip.write(png)
                zip.closeEntry()
            }

            // Write sheets + drawings
            for ((i, sheet) in sheets.withIndex()) {
                val sheetNum = i + 1
                val hasDrawing = sheet.hasIcons && sheet.icons.any { icons.mediaIndex.containsKey(it.cacheKey) }

                zip.addXml("xl/worksheets/sheet$sheetNum.xml",
                    buildSheetXml(sheet, stringPool, hasDrawing))

                if (hasDrawing) {
                    zip.addXml("xl/worksheets/_rels/sheet$sheetNum.xml.rels",
                        buildSheetRels(sheetNum))
                    zip.addXml("xl/drawings/drawing$sheetNum.xml",
                        buildDrawingXml(sheet, icons))
                    zip.addXml("xl/drawings/_rels/drawing$sheetNum.xml.rels",
                        buildDrawingRels(sheet, icons))
                }
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
        // Don't treat "5%" or "12.5%" as numeric — they have a % suffix
        if (s.endsWith("%")) return false
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
            if (col in sheet.iconColumns) {
                ICON_COL_WIDTH
            } else {
                val maxLen = sheet.rows.maxOf { row -> row.getOrElse(col) { "" }.length }
                (maxLen * 1.2 + 3).coerceIn(8.0, 55.0)
            }
        }
    }

    // ── Content Types ────────────────────────────────────────────────

    private fun buildContentTypes(sheets: List<SheetData>, icons: IconRegistry): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        sb.append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        sb.append("""<Default Extension="xml" ContentType="application/xml"/>""")

        if (icons.pngByKey.isNotEmpty()) {
            sb.append("""<Default Extension="png" ContentType="image/png"/>""")
        }

        sb.append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        sb.append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        sb.append("""<Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>""")

        for ((i, sheet) in sheets.withIndex()) {
            val n = i + 1
            sb.append("""<Override PartName="/xl/worksheets/sheet$n.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
            if (sheet.hasIcons && sheet.icons.any { icons.mediaIndex.containsKey(it.cacheKey) }) {
                sb.append("""<Override PartName="/xl/drawings/drawing$n.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/>""")
            }
        }

        sb.append("</Types>")
        return sb.toString()
    }

    // ── Root rels ────────────────────────────────────────────────────

    private val RELS_ROOT = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    // ── Workbook ─────────────────────────────────────────────────────

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
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..sheetCount) {
            sb.append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        sb.append("""<Relationship Id="rId${sheetCount + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        sb.append("""<Relationship Id="rId${sheetCount + 2}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>""")
        sb.append("</Relationships>")
        return sb.toString()
    }

    // ── Styles (13pt Calibri, blue headers) ──────────────────────────

    // Style 0 = 13pt body, Style 1 = 13pt bold, Style 2 = 13pt bold white on blue
    private val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="3">
<font><sz val="13"/><name val="Calibri"/></font>
<font><b/><sz val="13"/><name val="Calibri"/></font>
<font><b/><sz val="13"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
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

    // ── Shared strings ───────────────────────────────────────────────

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

    // ── Sheet XML ────────────────────────────────────────────────────

    private fun buildSheetXml(
        sheet: SheetData,
        stringPool: Map<String, Int>,
        hasDrawing: Boolean
    ): String {
        val sb = StringBuilder()
        val numCols = sheet.rows.maxOfOrNull { it.size } ?: 0
        val widths = autoColumnWidths(sheet)
        val dataRowHt = if (sheet.hasIcons) DATA_ROW_HEIGHT_ICON else DATA_ROW_HEIGHT_PLAIN

        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")

        // Freeze header row
        sb.append("<sheetViews><sheetView workbookViewId=\"0\">")
        sb.append("""<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>""")
        sb.append("</sheetView></sheetViews>")

        // Column widths
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
            val ht = if (rowIdx == 0) HEADER_ROW_HEIGHT else dataRowHt
            sb.append("""<row r="$rowNum" ht="${"%.1f".format(ht)}" customHeight="1">""")

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

        // Auto-filter
        if (numCols > 0 && sheet.rows.size > 1) {
            val lastCol = colLetter(numCols - 1)
            sb.append("""<autoFilter ref="A1:${lastCol}${sheet.rows.size}"/>""")
        }

        // Drawing reference
        if (hasDrawing) {
            sb.append("""<drawing r:id="rId1"/>""")
        }

        sb.append("</worksheet>")
        return sb.toString()
    }

    // ── Sheet rels (links sheet → drawing) ───────────────────────────

    private fun buildSheetRels(sheetNum: Int): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing$sheetNum.xml"/>
</Relationships>"""
    }

    // ── Drawing XML (twoCellAnchor for each icon) ────────────────────

    private fun buildDrawingXml(sheet: SheetData, icons: IconRegistry): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")

        // Build local rId map for this sheet's drawing
        val localRIds = mutableMapOf<String, Int>()
        var rIdCounter = 0
        for (icon in sheet.icons) {
            if (icon.cacheKey !in localRIds && icons.mediaIndex.containsKey(icon.cacheKey)) {
                rIdCounter++
                localRIds[icon.cacheKey] = rIdCounter
            }
        }

        var picId = 0
        for (icon in sheet.icons) {
            val rId = localRIds[icon.cacheKey] ?: continue
            picId++

            // twoCellAnchor: pin image to span from (col, row) to (col+1, row+1)
            sb.append("<xdr:twoCellAnchor editAs=\"oneCell\">")
            sb.append("<xdr:from>")
            sb.append("<xdr:col>${icon.col}</xdr:col><xdr:colOff>$ICON_OFFSET_EMU</xdr:colOff>")
            sb.append("<xdr:row>${icon.row}</xdr:row><xdr:rowOff>$ICON_OFFSET_EMU</xdr:rowOff>")
            sb.append("</xdr:from>")
            sb.append("<xdr:to>")
            sb.append("<xdr:col>${icon.col}</xdr:col><xdr:colOff>${ICON_OFFSET_EMU + ICON_EMU}</xdr:colOff>")
            sb.append("<xdr:row>${icon.row + 1}</xdr:row><xdr:rowOff>0</xdr:rowOff>")
            sb.append("</xdr:to>")
            sb.append("<xdr:pic>")
            sb.append("<xdr:nvPicPr><xdr:cNvPr id=\"$picId\" name=\"img$picId\"/><xdr:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></xdr:cNvPicPr></xdr:nvPicPr>")
            sb.append("<xdr:blipFill><a:blip r:embed=\"rId$rId\"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>")
            sb.append("<xdr:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$ICON_EMU\" cy=\"$ICON_EMU\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></xdr:spPr>")
            sb.append("</xdr:pic>")
            sb.append("<xdr:clientData/>")
            sb.append("</xdr:twoCellAnchor>")
        }

        sb.append("</xdr:wsDr>")
        return sb.toString()
    }

    // ── Drawing rels (links rIds → media images) ─────────────────────

    private fun buildDrawingRels(sheet: SheetData, icons: IconRegistry): String {
        val localRIds = mutableMapOf<String, Int>()
        var rIdCounter = 0
        for (icon in sheet.icons) {
            if (icon.cacheKey !in localRIds && icons.mediaIndex.containsKey(icon.cacheKey)) {
                rIdCounter++
                localRIds[icon.cacheKey] = rIdCounter
            }
        }

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for ((key, rId) in localRIds) {
            val mediaIdx = icons.mediaIndex[key] ?: continue
            sb.append("""<Relationship Id="rId$rId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image$mediaIdx.png"/>""")
        }
        sb.append("</Relationships>")
        return sb.toString()
    }
}
