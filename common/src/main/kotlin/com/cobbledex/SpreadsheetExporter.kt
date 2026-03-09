package com.cobbledex

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Exports all CobbleDex data as CSV spreadsheets importable into Google Sheets.
 * Run via /spawningrei export in-game after data is loaded.
 */
object SpreadsheetExporter {

    data class ExportResult(val outputDir: Path, val files: List<String>, val speciesCount: Int)

    fun export(sender: DiagnosticService.MessageSender): Int {
        val index = SpawnDataIndex

        if (!index.hasData()) {
            sender.send(tr("cobbledex-rei-emi-jei.cmd.no_data_short"))
            return 0
        }

        try {
            sender.send(tr("cobbledex-rei-emi-jei.cmd.export_start"))
            val result = doExport(index)
            sender.send(tr("cobbledex-rei-emi-jei.cmd.export_done", result.files.size, result.speciesCount))
            sender.send("§7${result.outputDir.toAbsolutePath()}")
            for (file in result.files) {
                sender.send("§7  • $file")
            }
        } catch (e: Exception) {
            sender.send(tr("cobbledex-rei-emi-jei.cmd.export_failed", e.message ?: "unknown"))
            DebugLog.warn("Spreadsheet export failed: ${e.message}")
            return 0
        }

        return 1
    }

    private fun doExport(index: SpawnDataIndex): ExportResult {
        val gameDir = try {
            com.cobbledex.platform.PlatformHelper.getGameDir()
        } catch (_: Exception) {
            java.nio.file.Paths.get(".")
        }

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val outputDir = gameDir.resolve("cobbledex-export").resolve(timestamp)
        Files.createDirectories(outputDir)

        val files = mutableListOf<String>()

        writeSpeciesOverview(index, outputDir)?.let { files.add(it) }
        writeSpawnData(index, outputDir)?.let { files.add(it) }
        writeEvolutionData(index, outputDir)?.let { files.add(it) }
        writeItemDrops(index, outputDir)?.let { files.add(it) }
        writeMovesets(index, outputDir)?.let { files.add(it) }
        writeLevelUpMoves(index, outputDir)?.let { files.add(it) }
        writeObtainmentData(index, outputDir)?.let { files.add(it) }
        writeFossilData(index, outputDir)?.let { files.add(it) }
        writeAbilities(index, outputDir)?.let { files.add(it) }
        writeTypeChart(index, outputDir)?.let { files.add(it) }

        // Write a README so the folder is self-explanatory
        writeReadme(outputDir, files, index)

        return ExportResult(outputDir, files, index.allSpeciesNames.size)
    }

    // ──────────────────────────────────────────────────────────────────
    //  1. Species Overview — one row per species, all key stats
    // ──────────────────────────────────────────────────────────────────
    private fun writeSpeciesOverview(index: SpawnDataIndex, dir: Path): String? {
        val species = index.speciesInfo
        if (species.isEmpty()) return null

        val file = "01_Species_Overview.csv"
        val rows = mutableListOf<List<String>>()
        rows.add(listOf(
            "Name", "Dex #", "Form", "Base Species",
            "Type 1", "Type 2",
            "HP", "Attack", "Defense", "Sp.Atk", "Sp.Def", "Speed", "BST",
            "Catch Rate", "Height", "Weight",
            "Ability 1", "Ability 2", "Hidden Ability",
            "Egg Group 1", "Egg Group 2",
            "Male Ratio %", "Egg Cycles",
            "EXP Group", "Base EXP Yield", "Base Friendship",
            "EV HP", "EV Atk", "EV Def", "EV SpA", "EV SpD", "EV Spe",
            "Has Spawns", "Has Drops", "Has Evolutions",
            "Labels", "Shoulder Mountable"
        ))

        val sorted = species.entries.sortedWith(compareBy({ it.value.nationalDexNumber.let { d -> if (d > 0) d else Int.MAX_VALUE } }, { it.key }))

        for ((key, info) in sorted) {
            val stats = info.baseStats
            val evs = info.evYield
            val abilities = info.abilities ?: emptyList()
            val eggGroups = info.eggGroups ?: emptyList()

            rows.add(listOf(
                info.name,
                if (info.nationalDexNumber > 0) info.nationalDexNumber.toString() else "",
                info.formName ?: "",
                info.baseSpeciesName ?: "",
                info.primaryType,
                info.secondaryType ?: "",
                stats?.get("hp")?.toString() ?: "",
                stats?.get("attack")?.toString() ?: "",
                stats?.get("defense")?.toString() ?: "",
                stats?.get("spa")?.toString() ?: "",
                stats?.get("spd")?.toString() ?: "",
                stats?.get("spe")?.toString() ?: "",
                info.baseStatTotal?.toString() ?: "",
                info.catchRate.toString(),
                info.height.toString(),
                info.weight.toString(),
                abilities.getOrElse(0) { "" },
                abilities.getOrElse(1) { "" },
                info.hiddenAbility ?: "",
                eggGroups.getOrElse(0) { "" },
                eggGroups.getOrElse(1) { "" },
                info.maleRatio?.let { "%.1f".format(it * 100) } ?: "",
                info.eggCycles?.toString() ?: "",
                info.experienceGroup ?: "",
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
                info.labels?.joinToString("; ") ?: "",
                if (info.shoulderMountable) "Yes" else "No"
            ))
        }

        writeCsv(dir.resolve(file), rows)
        return file
    }

    // ──────────────────────────────────────────────────────────────────
    //  2. Spawn Data — one row per spawn condition
    // ──────────────────────────────────────────────────────────────────
    private fun writeSpawnData(index: SpawnDataIndex, dir: Path): String? {
        val spawns = index.spawnsBySpecies
        if (spawns.isEmpty()) return null

        val file = "02_Spawn_Data.csv"
        val rows = mutableListOf<List<String>>()
        rows.add(listOf(
            "Pokemon", "Form", "Bucket", "Spawn Weight",
            "Level Range", "Context",
            "Biomes", "Time", "Weather",
            "Dimensions", "Structures",
            "Can See Sky", "Min Light", "Max Light",
            "Min Sky Light", "Max Sky Light",
            "Min Y", "Max Y",
            "Needed Nearby Blocks", "Needed Base Blocks",
            "Moon Phase", "Presets", "Fluid",
            "Weight Multipliers", "Min Lure Level"
        ))

        val sorted = spawns.entries.sortedBy { it.key }

        for ((_, spawnList) in sorted) {
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
                    spawn.pokemon,
                    spawn.formAspects,
                    spawn.bucket,
                    spawn.weight.toString(),
                    spawn.levelRange,
                    spawn.context,
                    spawn.biomes.joinToString("; "),
                    spawn.timeRange ?: "Any",
                    weatherText.ifEmpty { "Any" },
                    spawn.dimensions.joinToString("; "),
                    spawn.structures.joinToString("; "),
                    spawn.canSeeSky?.toString() ?: "",
                    spawn.minLight?.toString() ?: "",
                    spawn.maxLight?.toString() ?: "",
                    spawn.minSkyLight?.toString() ?: "",
                    spawn.maxSkyLight?.toString() ?: "",
                    spawn.minY?.toString() ?: "",
                    spawn.maxY?.toString() ?: "",
                    spawn.neededNearbyBlocks.joinToString("; "),
                    spawn.neededBaseBlocks.joinToString("; "),
                    spawn.moonPhase ?: "",
                    spawn.presets.joinToString("; "),
                    spawn.fluid ?: "",
                    multipliers,
                    spawn.minLureLevel?.toString() ?: ""
                ))
            }
        }

        writeCsv(dir.resolve(file), rows)
        return file
    }

    // ──────────────────────────────────────────────────────────────────
    //  3. Evolution Data — one row per evolution path
    // ──────────────────────────────────────────────────────────────────
    private fun writeEvolutionData(index: SpawnDataIndex, dir: Path): String? {
        val evolutions = index.evolutionsBySpecies
        if (evolutions.isEmpty()) return null

        val file = "03_Evolutions.csv"
        val rows = mutableListOf<List<String>>()
        rows.add(listOf(
            "From Pokemon", "From Aspects", "To Pokemon", "To Aspects",
            "Method", "Requirements",
            "Required Item/Block", "Consumes Held Item"
        ))

        val sorted = evolutions.entries.sortedBy { it.key }

        for ((_, evoList) in sorted) {
            for (evo in evoList) {
                rows.add(listOf(
                    evo.fromSpecies,
                    evo.fromAspects.joinToString("; "),
                    evo.toSpecies,
                    evo.toAspects.joinToString("; "),
                    evo.variant,
                    evo.requirements.joinToString("; ") { it.displayText },
                    evo.requiredContext ?: "",
                    if (evo.consumeHeldItem) "Yes" else "No"
                ))
            }
        }

        writeCsv(dir.resolve(file), rows)
        return file
    }

    // ──────────────────────────────────────────────────────────────────
    //  4. Item Drops — one row per drop entry per species
    // ──────────────────────────────────────────────────────────────────
    private fun writeItemDrops(index: SpawnDataIndex, dir: Path): String? {
        val speciesInfo = index.speciesInfo
        val hasDrops = speciesInfo.any { it.value.drops?.isNotEmpty() == true }
        if (!hasDrops) return null

        val file = "04_Item_Drops.csv"
        val rows = mutableListOf<List<String>>()
        rows.add(listOf(
            "Pokemon", "Dex #", "Item ID", "Drop Chance %", "Quantity"
        ))

        val sorted = speciesInfo.entries
            .filter { it.value.drops?.isNotEmpty() == true }
            .sortedWith(compareBy({ it.value.nationalDexNumber.let { d -> if (d > 0) d else Int.MAX_VALUE } }, { it.key }))

        for ((_, info) in sorted) {
            for (drop in info.drops!!) {
                rows.add(listOf(
                    info.name,
                    if (info.nationalDexNumber > 0) info.nationalDexNumber.toString() else "",
                    drop.itemId,
                    drop.percentage.toString(),
                    drop.quantityRange ?: drop.quantity.toString()
                ))
            }
        }

        writeCsv(dir.resolve(file), rows)
        return file
    }

    // ──────────────────────────────────────────────────────────────────
    //  5. All Moves (flat list) — egg, tutor, TM per species
    // ──────────────────────────────────────────────────────────────────
    private fun writeMovesets(index: SpawnDataIndex, dir: Path): String? {
        val speciesInfo = index.speciesInfo
        val hasMoves = speciesInfo.any {
            val i = it.value
            i.eggMoves?.isNotEmpty() == true || i.tutorMoves?.isNotEmpty() == true || i.tmMoves?.isNotEmpty() == true
        }
        if (!hasMoves) return null

        val file = "05_Moves_All.csv"
        val rows = mutableListOf<List<String>>()
        rows.add(listOf(
            "Pokemon", "Dex #", "Learn Method", "Level",
            "Move Name", "Type", "Category", "Power", "Accuracy", "PP"
        ))

        val sorted = speciesInfo.entries
            .sortedWith(compareBy({ it.value.nationalDexNumber.let { d -> if (d > 0) d else Int.MAX_VALUE } }, { it.key }))

        for ((_, info) in sorted) {
            // Level-up moves
            info.levelUpMoves?.forEach { lum ->
                for (move in lum.moves) {
                    rows.add(moveRow(info, "Level Up", lum.level.toString(), move))
                }
            }

            // Egg moves
            info.eggMoves?.forEach { move ->
                rows.add(moveRow(info, "Egg", "", move))
            }

            // Tutor moves
            info.tutorMoves?.forEach { move ->
                rows.add(moveRow(info, "Tutor", "", move))
            }

            // TM moves
            info.tmMoves?.forEach { move ->
                rows.add(moveRow(info, "TM", "", move))
            }
        }

        writeCsv(dir.resolve(file), rows)
        return file
    }

    private fun moveRow(info: EvolutionDataLoader.SpeciesBasicInfo, method: String, level: String, move: MoveDetail): List<String> {
        return listOf(
            info.name,
            if (info.nationalDexNumber > 0) info.nationalDexNumber.toString() else "",
            method,
            level,
            move.name,
            move.type,
            move.category,
            if (move.power > 0) move.power.toString() else "—",
            if (move.accuracy > 0) move.accuracy.toString() else "—",
            move.pp.toString()
        )
    }

    // ──────────────────────────────────────────────────────────────────
    //  6. Level-Up Moves — focused view of what's learned when
    // ──────────────────────────────────────────────────────────────────
    private fun writeLevelUpMoves(index: SpawnDataIndex, dir: Path): String? {
        val speciesInfo = index.speciesInfo
        val hasLevelUp = speciesInfo.any { it.value.levelUpMoves?.isNotEmpty() == true }
        if (!hasLevelUp) return null

        val file = "06_Level_Up_Moves.csv"
        val rows = mutableListOf<List<String>>()
        rows.add(listOf(
            "Pokemon", "Dex #", "Level",
            "Move Name", "Type", "Category", "Power", "Accuracy", "PP"
        ))

        val sorted = speciesInfo.entries
            .filter { it.value.levelUpMoves?.isNotEmpty() == true }
            .sortedWith(compareBy({ it.value.nationalDexNumber.let { d -> if (d > 0) d else Int.MAX_VALUE } }, { it.key }))

        for ((_, info) in sorted) {
            val moves = info.levelUpMoves ?: continue
            for (lum in moves.sortedBy { it.level }) {
                for (move in lum.moves) {
                    rows.add(listOf(
                        info.name,
                        if (info.nationalDexNumber > 0) info.nationalDexNumber.toString() else "",
                        lum.level.toString(),
                        move.name,
                        move.type,
                        move.category,
                        if (move.power > 0) move.power.toString() else "—",
                        if (move.accuracy > 0) move.accuracy.toString() else "—",
                        move.pp.toString()
                    ))
                }
            }
        }

        writeCsv(dir.resolve(file), rows)
        return file
    }

    // ──────────────────────────────────────────────────────────────────
    //  7. Special Obtainment — legendaries, events, altars, etc.
    // ──────────────────────────────────────────────────────────────────
    private fun writeObtainmentData(index: SpawnDataIndex, dir: Path): String? {
        val obtainment = index.obtainmentBySpecies
        if (obtainment.isEmpty()) return null

        val file = "07_Special_Obtainment.csv"
        val rows = mutableListOf<List<String>>()
        rows.add(listOf(
            "Pokemon", "Form",
            "Method", "Description",
            "Required Items", "Block", "Structure", "Dimension",
            "Notes", "Source"
        ))

        val sorted = obtainment.entries.sortedBy { it.key }

        for ((_, obtainList) in sorted) {
            for (info in obtainList) {
                rows.add(listOf(
                    info.pokemon,
                    info.formAspects,
                    info.method,
                    info.description,
                    info.items.joinToString("; "),
                    info.block ?: "",
                    info.structure ?: "",
                    info.dimension ?: "",
                    info.notes.joinToString("; "),
                    info.source
                ))
            }
        }

        writeCsv(dir.resolve(file), rows)
        return file
    }

    // ──────────────────────────────────────────────────────────────────
    //  8. Fossil Data
    // ──────────────────────────────────────────────────────────────────
    private fun writeFossilData(index: SpawnDataIndex, dir: Path): String? {
        val fossils = index.fossilsBySpecies
        if (fossils.isEmpty()) return null

        val file = "08_Fossils.csv"
        val rows = mutableListOf<List<String>>()
        rows.add(listOf(
            "Pokemon", "Fossil Items", "Extra Tags"
        ))

        val sorted = fossils.entries.sortedBy { it.key }

        for ((_, fossilList) in sorted) {
            for (fossil in fossilList) {
                rows.add(listOf(
                    fossil.resultSpecies,
                    fossil.fossilItems.joinToString("; "),
                    fossil.extraTags ?: ""
                ))
            }
        }

        writeCsv(dir.resolve(file), rows)
        return file
    }

    // ──────────────────────────────────────────────────────────────────
    //  9. Abilities — species × ability mapping
    // ──────────────────────────────────────────────────────────────────
    private fun writeAbilities(index: SpawnDataIndex, dir: Path): String? {
        val speciesInfo = index.speciesInfo
        val hasAbilities = speciesInfo.any { it.value.abilities?.isNotEmpty() == true }
        if (!hasAbilities) return null

        val file = "09_Abilities.csv"
        val rows = mutableListOf<List<String>>()
        rows.add(listOf(
            "Pokemon", "Dex #", "Type 1", "Type 2",
            "Ability 1", "Ability 2", "Hidden Ability"
        ))

        val sorted = speciesInfo.entries
            .filter { it.value.abilities?.isNotEmpty() == true || it.value.hiddenAbility != null }
            .sortedWith(compareBy({ it.value.nationalDexNumber.let { d -> if (d > 0) d else Int.MAX_VALUE } }, { it.key }))

        for ((_, info) in sorted) {
            val abilities = info.abilities ?: emptyList()
            rows.add(listOf(
                info.name,
                if (info.nationalDexNumber > 0) info.nationalDexNumber.toString() else "",
                info.primaryType,
                info.secondaryType ?: "",
                abilities.getOrElse(0) { "" },
                abilities.getOrElse(1) { "" },
                info.hiddenAbility ?: ""
            ))
        }

        writeCsv(dir.resolve(file), rows)
        return file
    }

    // ──────────────────────────────────────────────────────────────────
    //  10. Type Chart — type vs type effectiveness
    // ──────────────────────────────────────────────────────────────────
    private fun writeTypeChart(index: SpawnDataIndex, dir: Path): String? {
        // Standard type chart — always available
        val types = listOf(
            "Normal", "Fire", "Water", "Electric", "Grass", "Ice",
            "Fighting", "Poison", "Ground", "Flying", "Psychic", "Bug",
            "Rock", "Ghost", "Dragon", "Dark", "Steel", "Fairy"
        )

        // effectiveness[attacker][defender] = multiplier
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

        val file = "10_Type_Chart.csv"
        val rows = mutableListOf<List<String>>()
        rows.add(listOf("Attacker \\ Defender") + types)

        for (attacker in types) {
            val row = mutableListOf(attacker)
            val matchups = chart[attacker] ?: emptyMap()
            for (defender in types) {
                val eff = matchups[defender] ?: 1.0
                row.add(when (eff) {
                    0.0 -> "0"
                    0.5 -> "0.5"
                    1.0 -> "1"
                    2.0 -> "2"
                    else -> eff.toString()
                })
            }
            rows.add(row)
        }

        writeCsv(dir.resolve(file), rows)
        return file
    }

    // ──────────────────────────────────────────────────────────────────
    //  README
    // ──────────────────────────────────────────────────────────────────
    private fun writeReadme(dir: Path, files: List<String>, index: SpawnDataIndex) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val sb = StringBuilder()
        sb.appendLine("# CobbleDex Data Export")
        sb.appendLine("Generated: $timestamp")
        sb.appendLine("Total species: ${index.allSpeciesNames.size}")
        sb.appendLine()
        sb.appendLine("## Files")
        sb.appendLine("All files are CSV format — import directly into Google Sheets, Excel, or LibreOffice Calc.")
        sb.appendLine()
        sb.appendLine("| File | Description |")
        sb.appendLine("|------|-------------|")
        for (f in files) {
            val desc = when {
                f.contains("Species_Overview") -> "Every Pokémon: stats, types, abilities, catch rate, egg groups, EVs"
                f.contains("Spawn_Data") -> "Every spawn condition: biomes, levels, time, weather, blocks, weights"
                f.contains("Evolutions") -> "Every evolution path: method, requirements, items"
                f.contains("Item_Drops") -> "Every item drop: item, chance %, quantity"
                f.contains("Moves_All") -> "Complete moveset: level-up, egg, tutor, TM moves per Pokémon"
                f.contains("Level_Up_Moves") -> "Level-up moves only: what each Pokémon learns at each level"
                f.contains("Obtainment") -> "Special obtainment: legendaries, altars, fossils, events"
                f.contains("Fossils") -> "Fossil combinations: which items create which Pokémon"
                f.contains("Abilities") -> "Ability listing: standard + hidden abilities per Pokémon"
                f.contains("Type_Chart") -> "Type effectiveness matrix: all 18 types"
                else -> ""
            }
            sb.appendLine("| $f | $desc |")
        }
        sb.appendLine()
        sb.appendLine("## How to use")
        sb.appendLine("1. Open Google Sheets → File → Import → Upload")
        sb.appendLine("2. Select any CSV file")
        sb.appendLine("3. Import location: \"Create new spreadsheet\" or \"Insert new sheet(s)\"")
        sb.appendLine("4. Separator type: Comma")
        sb.appendLine()
        sb.appendLine("To update after a Cobblemon update: run `/spawningrei export` in-game again.")

        Files.writeString(dir.resolve("README.md"), sb.toString())
    }

    // ──────────────────────────────────────────────────────────────────
    //  CSV writer — proper RFC 4180 escaping for Google Sheets compat
    // ──────────────────────────────────────────────────────────────────
    private fun writeCsv(path: Path, rows: List<List<String>>) {
        val sb = StringBuilder()
        // UTF-8 BOM for Excel/Sheets to detect encoding
        sb.append('\uFEFF')
        for (row in rows) {
            sb.appendLine(row.joinToString(",") { escapeCsvField(it) })
        }
        Files.writeString(path, sb.toString())
    }

    private fun escapeCsvField(field: String): String {
        if (field.isEmpty()) return ""
        val needsQuoting = field.contains(',') || field.contains('"') || field.contains('\n') || field.contains('\r')
        return if (needsQuoting) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }
}
